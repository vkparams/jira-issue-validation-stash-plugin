package com.teslamotors.stash.logchecker;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.Set;

import com.atlassian.stash.content.Changeset;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

// ========================================================================
public class ChangesetValidationSummary {

	final private Changeset changeset;
	final Set<String> issue_references;
	
	/**
	 * This is just a reference to facilitate deferred checking
	 * of whether the issues referenced by this commit message
	 * actually exist in Jira.
	 */
	private final IssueExistenceResult issue_existence_result;
	
	public boolean isExempted(LogMessageHookConfig hook_config) {
		boolean should_exempt_by_keyword = hook_config.allow_exemption_keyword && hasExemptionKeyword(hook_config);
		boolean should_exempt_by_commit_type = hook_config.should_exempt_merge_commits && isMergeCommit();
		return should_exempt_by_keyword || should_exempt_by_commit_type;
	}

	public ChangesetValidationSummary(
			Set<String> issues,
			IssueExistenceResult issue_existence_result,
			Changeset changeset) {

		this.issue_references = issues;
		this.issue_existence_result = issue_existence_result;
		this.changeset = changeset;
	}
	
	private boolean hasExemptionKeyword(LogMessageHookConfig hook_config) {
		Matcher jira_exemption_matcher = hook_config.exemption_regex_pattern.matcher(changeset.getMessage());
		return jira_exemption_matcher.find();
	}
	
	private boolean isMergeCommit() {
		return this.changeset.getParents().size() > 1;
	}

	
	public Set<String> getNonexistentIssues() {
		return Sets.intersection(Sets.difference(this.issue_references, this.issue_existence_result.moved_issue_map.keySet()), issue_existence_result.nonexistent_issues);
	}
	
	public Map<String, String> getMovedIssues() {

		Map<String, String> moved_referenced_issues = Maps.newHashMap();
		for (String referenced_issue : this.issue_references)
			if (this.issue_existence_result.moved_issue_map.containsKey(referenced_issue))
				moved_referenced_issues.put(referenced_issue, this.issue_existence_result.moved_issue_map.get(referenced_issue));

		return moved_referenced_issues;
	}
	
	
	public Collection<String> getChangesetErrors(LogMessageHookConfig hook_config) {
		Collection<String> changeset_errors = Lists.newArrayList();

		if (!isExempted(hook_config)) {
			if (this.issue_references.isEmpty()) {
				
				String message = String.format("Commit %s does not contain an issue reference or exemption in its log message.", changeset.getDisplayId());
				if (hook_config.output_commit_message)
					message += String.format(" Full message:\n%s", changeset.getMessage());

				changeset_errors.add(  message );
				
			} else {
				
				if (hook_config.fail_on_issue_move) {
					Map<String, String> moved_issues = this.getMovedIssues();
					if (!moved_issues.isEmpty()) {
						
						Collection<String> issue_move_strings = Collections2.transform(moved_issues.entrySet(), new Function<Entry<String, String>, String>() {
							@Override
							public String apply(
									Entry<String, String> input) {
								return input.getKey() + " -> " + input.getValue();
							}});
						
						changeset_errors.add( String.format("Commit %s makes reference to moved issue(s): %s", changeset.getDisplayId(), Joiner.on(", ").join(issue_move_strings) ) );
					}
				}
				
				Set<String> nonexistent_issues = this.getNonexistentIssues();
				if (!nonexistent_issues.isEmpty())
					changeset_errors.add( String.format("Commit %s makes reference to non-existent issue(s): %s", changeset.getDisplayId(), Joiner.on(',').join(nonexistent_issues) ) );
			}
		}
		return changeset_errors;
	}
}