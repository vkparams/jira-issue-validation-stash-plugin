# Tickets, Please!

A plugin for Atlassian Stash® that enforces the presence of JIRA® issue references in commit messages.

## Features

* Verifies existence of issues in Jira (catches typos and moved issues)
* Fast; makes a minimal number of Jira API calls (checks all Jira references in a single bulk query)
* Uses Application Link for minimal setup
* Selectively apply the hook to branches based on ancestry (aka "watershed commits") and branch namespace
* Provides for a special "exemption" keyword in commit messages (via regex)
* Optionally exempt merge commits
* Includes both a pre-receive (`PreReceiveRepositoryHook`) and merge-check (`RepositoryMergeRequestCheck`) hook

## Explanation of "watershed commits"

Sometimes an organization will adopt a new convention for code style, banned repository content, commit messages, etc.
which they may wish to enforce via repository hooks. A naive repository hook might attempt to enforce the
standard repository-wide, on all future commits to all branches.

However, given that there may be many outstanding branches created at a time before the convention was adopted, we
do not wish to force developers of those branches to redundantly apply the changes that bring their branch
up to specification.

Likewise, we may not want to apply the standard universally to all branches in the repository; we may have long-lived
(but ultimately terminal) "release branches" for which we wish to minimize unnecessary change (especially of a cosmetic
nature). Such branches shall be "grandfathered" in to the new era of repository conventions.

We shall not attempt to retroactively apply the standard, but only enforce it going forward on branches that
have been brought up to the standard in a *watershed commit*. We may configure repository hooks to only
consider branches that contain this watershed commit in their ancestry.

## Discussion

Here is an Atlassian Answers post on this subject: [How can I implement a policy in Stash to prevent check-in without an issue linked in the commit?](https://answers.atlassian.com/questions/148310/how-can-i-implement-a-policy-in-stash-to-prevent-check-in-without-an-issue-linked-in-the-commit)

Within that post is another implementation of this idea as a Stash hook on Bitbucket: [stash-enforce-message-hook-plugin](https://bitbucket.org/cofarrell/stash-enforce-message-hook-plugin)

## Test cases

1. In the configuration
    * Enable the hook
    * Leave the watershed field blank
    * Enable the "Check Jira for issue existence" setting
    * Check the "Allow exemptions" setting
    * Put "#noissue\b" in the "Exemption pattern" field
1. Push commit with no issue reference
    * Expected result: **failure**
1. Amend that commit to include "#noissue" in the commit message, and push again
    * Expected result: **success**
1. Create a new commit with a syntactically valid (but non-existent in Jira) issue reference, and push
    * Expected result: **failure**
1. Disable the "Check Jira for issue existence" setting
    * Expected result: **success**
