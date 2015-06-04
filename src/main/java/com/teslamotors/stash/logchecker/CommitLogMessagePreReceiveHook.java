package com.teslamotors.stash.logchecker;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nonnull;

import org.eclipse.jgit.lib.Constants;

import com.atlassian.applinks.api.ApplicationLinkService;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.content.ChangesetsBetweenRequest;
import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.hook.repository.RepositoryMergeRequestCheck;
import com.atlassian.stash.hook.repository.RepositoryMergeRequestCheckContext;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.RefChangeType;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.server.ApplicationPropertiesService;
import com.atlassian.stash.util.Page;
import com.atlassian.stash.util.PageRequest;
import com.atlassian.stash.util.PageUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class CommitLogMessagePreReceiveHook implements PreReceiveRepositoryHook, RepositoryMergeRequestCheck {

	private final CommitService commitService;
	private final ApplicationPropertiesService applicationPropertiesService;
	private final ApplicationLinkService applicationLinkService;

	public CommitLogMessagePreReceiveHook(
			ApplicationPropertiesService applicationPropertiesService,
            CommitService commitService,
			ApplicationLinkService applicationLinkService) {
		this.commitService = commitService;
		this.applicationPropertiesService = applicationPropertiesService;
		this.applicationLinkService = applicationLinkService;
	}

	// ========================================================================
	/**
	 * Aggregates changes from paged REST interface
	 * TODO Test this function for result sets that span multiple pages!
	 */
	static Collection<Changeset> getChangesetsForRefchange(
			Repository repository,
			RefChange ref_change,
            CommitService commitService) {

		Collection<Changeset> changesets_for_refchange = Lists.newArrayList(); 

		ChangesetsBetweenRequest request = new ChangesetsBetweenRequest.Builder(repository)
		.exclude(ref_change.getFromHash())
		.include(ref_change.getToHash())
		.build();

		PageRequest page_request = PageUtils.newRequest(0, 25);
//		PageRequest page_request = PageUtils.newRequest(0, 1);	// XXX TEST ME

		Page<Changeset> page = null;
		while (page == null || !page.getIsLastPage()) {

			page = commitService.getChangesetsBetween(request, page_request);
			for (Changeset changeset : page.getValues())
				changesets_for_refchange.add(changeset);

			page_request = page.getNextPageRequest();
		}

		return changesets_for_refchange;
	}

	// ========================================================================
	static Map<Changeset, ChangesetValidationSummary> enforceIssueReferencesOnAllRefs(
			Map<RefChange, Iterable<Changeset>> changesets_by_refchange,
			LogMessageHookConfig hook_config,
            CommitService commitService,
			ApplicationLinkService applicationLinkService) throws Exception {

		// XXX For performance reasons, this object is NOT immediately populated.
		IssueExistenceResult issue_existence_result = new IssueExistenceResult();

		Map<Changeset, ChangesetValidationSummary> issue_references_by_changeset_id = Maps.newHashMap();
		for (Entry<RefChange, Iterable<Changeset>> entry : changesets_by_refchange.entrySet()) {
			Iterable<Changeset> changesets = entry.getValue();
			for (Changeset changeset : changesets) {
				Set<String> issues = JiraIssueUtils.findIssues(changeset);
				
				ChangesetValidationSummary summary = new ChangesetValidationSummary(
						issues,
						issue_existence_result,
						changeset);

				issue_references_by_changeset_id.put(changeset, summary);
			}
		}

		// We do all issue references in one batch for speed
		Set<String> all_issue_references = Sets.newHashSet();
		for (ChangesetValidationSummary summary : issue_references_by_changeset_id.values())
			all_issue_references.addAll(summary.issue_references);

		if (!all_issue_references.isEmpty() && hook_config.validate_issue_references_against_jira) {
			// XXX This throws an exception of any of the issue references do not exist in Jira
			// XXX We discard this reference here, because we have already stowed a reference
			// to this object in each ChangesetValidationSummary.
			issue_existence_result.populateIssueMovesAndNonexistence(
					applicationLinkService,
					all_issue_references,
					hook_config);
		}

		return issue_references_by_changeset_id;
	}

	// ========================================================================
	public static Collection<String> checkRefsForRejection(
			Collection<RefChange> ref_changes,
			WatershedCommitFilter watershed_commit_filter,
			LogMessageHookConfig hook_config,
			Repository repository,
            CommitService commitService,
			ApplicationLinkService applicationLinkService) {

		// Here we can ignore certain refs
		Map<RefChange, Iterable<Changeset>> changesets_by_refchange = Maps.newHashMap(); 
		for (RefChange ref_change : ref_changes) {

			if (RefChangeType.UPDATE != ref_change.getType())
				continue;

			try {
				if (!watershed_commit_filter.isPastWatershed(ref_change))
					continue;
			} catch (IOException e) {
				e.printStackTrace();
				return Collections.singleton(e.getMessage());
			}
			
			if (!hook_config.isRefMatchedByPrefix(ref_change))
				continue;

			Collection<Changeset> changesets = getChangesetsForRefchange(
					repository,
					ref_change,
                    commitService);

			changesets_by_refchange.put(ref_change, changesets);
		}

		List<String> hook_response_error_lines = Lists.newArrayList();
		try {
			Map<Changeset, ChangesetValidationSummary> validation_summary_by_changeset = enforceIssueReferencesOnAllRefs(
					changesets_by_refchange,
					hook_config,
                    commitService,
					applicationLinkService);

			for (Entry<RefChange, Iterable<Changeset>> refchange_entry : changesets_by_refchange.entrySet()) {

				String shortened_branch_name = refchange_entry.getKey().getRefId().substring(Constants.R_HEADS.length());
				List<String> aggregated_changeset_errors_for_branch = Lists.newArrayList();
				for (Changeset changeset : refchange_entry.getValue()) {
					ChangesetValidationSummary summary = validation_summary_by_changeset.get(changeset);
					aggregated_changeset_errors_for_branch.addAll(summary.getChangesetErrors(hook_config));
				}
			
				if (!aggregated_changeset_errors_for_branch.isEmpty()) {
					hook_response_error_lines.add( String.format("Error(s) in branch \"%s\":", shortened_branch_name) );
					for (String changeset_error : aggregated_changeset_errors_for_branch)
						hook_response_error_lines.add( '\t' + changeset_error );
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			hook_response_error_lines.add( e.getMessage() );
		}
		
		return hook_response_error_lines;
	}

	// ========================================================================
	@Override
	public boolean onReceive(@Nonnull RepositoryHookContext repo_context,
			@Nonnull Collection<RefChange> ref_changes, @Nonnull HookResponse hook_response) {

		LogMessageHookConfig hook_config = LogMessageHookConfig.fromSettings(repo_context.getSettings(), this.applicationLinkService);

		WatershedCommitFilter watershed_commit_filter;
		try {
			watershed_commit_filter = WatershedCommitFilter.create(this.applicationPropertiesService, repo_context.getRepository(), hook_config);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		Collection<String> error_lines = checkRefsForRejection(ref_changes,
				watershed_commit_filter,
				hook_config,
				repo_context.getRepository(),
				this.commitService,
				this.applicationLinkService);

		for (String error_line : error_lines)
			hook_response.err().println(error_line);
		
		return error_lines.isEmpty();
	}
	

	@Override
    public void check(RepositoryMergeRequestCheckContext context) {
    	
		LogMessageHookConfig hook_config = LogMessageHookConfig.fromSettings(context.getSettings(), this.applicationLinkService);
		Repository repo = context.getMergeRequest().getPullRequest().getToRef().getRepository();
		
		WatershedCommitFilter watershed_commit_filter;
		try {
			watershed_commit_filter = WatershedCommitFilter.create(this.applicationPropertiesService,
					repo,
					hook_config);
			
		} catch (Exception e) {
			e.printStackTrace();
			context.getMergeRequest().veto(String.format("Error in merge hook \"%s\"", this.getClass().getSimpleName()), e.getMessage());
			return;
		}
    	
        RefChange ref_change = new PullRequestRefChange( context.getMergeRequest().getPullRequest() );
        Collection<String> error_lines = CommitLogMessagePreReceiveHook.checkRefsForRejection(
				Collections.singleton(ref_change),
				watershed_commit_filter,
				hook_config,
				repo,
				this.commitService,
				this.applicationLinkService);

		if (!error_lines.isEmpty())
            context.getMergeRequest().veto(String.format("Rejected by merge hook \"%s\"", this.getClass().getSimpleName()), Joiner.on('\n').join(error_lines));
    }
}