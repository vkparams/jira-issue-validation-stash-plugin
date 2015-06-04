package com.teslamotors.stash.logchecker;

import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.RefChangeType;

/**
 * An adapter class between {@link com.atlassian.stash.pull.PullRequest PullRequest} and {@link com.atlassian.stash.repository.RefChange RefChange}
 * 
 * Note that the semantics of "from" and "to" are
 * reversed when talking about an update to a branch
 * (as considered by the "pre-receive" hook) and
 * a merge between two branches. 
 */
class PullRequestRefChange implements RefChange {

	private final PullRequest pull_request;
	PullRequestRefChange(PullRequest pullRequest) {
		this.pull_request = pullRequest;
	}

	@Override
	public String getFromHash() {
		// XXX Note: "From" and "To" are intentionally backwards.
		return this.pull_request.getToRef().getLatestChangeset();
	}

	@Override
	public String getToHash() {
		// XXX Note: "To" and "From" are intentionally backwards.
		return this.pull_request.getFromRef().getLatestChangeset();
	}

	@Override
	public String getRefId() {
		return this.pull_request.getToRef().getId();
	}

	@Override
	public RefChangeType getType() {
		return RefChangeType.UPDATE;
	}
}