package com.teslamotors.stash.logchecker;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.server.ApplicationPropertiesService;
import com.google.common.collect.Sets;

// ========================================================================
class WatershedCommitFilter {
	
	final org.eclipse.jgit.lib.Repository git_repository;
	final RevWalk rw;
	final Collection<RevCommit> parsed_watershed_commits;
	
	private WatershedCommitFilter(
			org.eclipse.jgit.lib.Repository git_repository,
			RevWalk rw,
			Collection<RevCommit> parsed_watershed_commits) {
		this.git_repository = git_repository;
		this.rw = rw;
		this.parsed_watershed_commits = parsed_watershed_commits;
	}
	
	public static WatershedCommitFilter create(
			ApplicationPropertiesService applicationPropertiesService,
			Repository stash_repository,
			LogMessageHookConfig hook_config) throws Exception {

		File repo_dir = applicationPropertiesService.getRepositoryDir(stash_repository);
		FileRepositoryBuilder builder = new FileRepositoryBuilder();

		org.eclipse.jgit.lib.Repository git_repository = builder.setGitDir(repo_dir).build();
		RevWalk rw = new RevWalk(git_repository);

		// A space character is not a valid element within a branch name.
		// Therefore, it is safe to use them as delimiters.
		// See https://www.kernel.org/pub/software/scm/git/docs/git-check-ref-format.html
		Collection<RevCommit> parsed_watershed_commits = Sets.newHashSet();
		if (!hook_config.watershed_commit_string.isEmpty()) {
			for (String ref : hook_config.watershed_commit_string.split("\\s+")) {

				ObjectId resolved_commit = git_repository.resolve(ref);
				if (resolved_commit == null)
					throw new Exception("Could not resolve commit: " + ref);

				parsed_watershed_commits.add(rw.parseCommit(resolved_commit));
			}
		}
		
		return new WatershedCommitFilter(git_repository, rw, parsed_watershed_commits);
	}
	
	/**
	 * Applies the hook if the "before" commit of the branch contains at least one
	 * of the declared "watershed commits" as an ancestor, or if there are no
	 * declared watershed commits.
	 */
	public boolean isPastWatershed(RefChange ref_change) throws MissingObjectException, IncorrectObjectTypeException, IOException {
		
		if (parsed_watershed_commits.isEmpty())
			return true;

		for (RevCommit watershed : parsed_watershed_commits) {
			RevCommit current_branch_head_commit = this.rw.parseCommit(this.git_repository.resolve(ref_change.getFromHash()));
			if (this.rw.isMergedInto(watershed, current_branch_head_commit))
				return true;
		}
		
		return false;
	}
	
	public void cleanup() {
		this.rw.dispose();
		this.git_repository.close();
	}
}