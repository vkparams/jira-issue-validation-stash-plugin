package com.teslamotors.stash.logchecker;

import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Pattern;

import org.eclipse.jgit.lib.Constants;

import com.atlassian.applinks.api.ApplicationLinkService;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.setting.Settings;

// ========================================================================
class LogMessageHookConfig {
	
	public static class JiraConnection {

		static final String JIRA_USE_APP_LINK_SOY_FIELD = "jiraApplicationLinkEnabled";
		static final String JIRA_BASE_URL_SOY_FIELD = "jiraBaseUrl";
		static final String JIRA_USERNAME_SOY_FIELD = "jiraUsername";
		static final String JIRA_PASSWORD_SOY_FIELD = "jiraPassword";

		final public boolean use_jira_application_link;
		public final String jira_base_url;
		public final String jira_username;
		public final String jira_password;
		public final ApplicationLinkService applicationLinkService;
		
		JiraConnection(boolean use_jira_application_link, String jira_base_url, String jira_username, String jira_password, ApplicationLinkService applicationLinkService) {
			this.use_jira_application_link = use_jira_application_link;
			this.jira_base_url = jira_base_url;
			this.jira_username = jira_username;
			this.jira_password = jira_password;
			this.applicationLinkService = applicationLinkService;
		}
	}
	
	static final String CHECK_JIRA_EXISTENCE_ENABLED_SOY_FIELD = "checkJiraExistenceEnabled";
	static final String CHECK_ISSUE_MOVE_ENABLED_SOY_FIELD = "checkForIssueMoveEnabled";
	static final String JIRA_EXEMPTION_PATTERN_SOY_FIELD = "jiraExemptionRegex";

	static final String FAIL_ON_ISSUE_MOVE_ENABLED_SOY_FIELD = "failOnIssueMoveEnabled";
	static final String LOG_MESSAGE_WATERSHED_COMMIT_SOY_FIELD = "watershedCommitLogMessageEnforcement";
	static final String PRINT_LOG_MESSAGE_ENABLED_SOY_FIELD = "printLogMessageEnabled";
	static final String ALLOW_EXEMPTION_KEYWORD_SOY_FIELD = "allowExemptionKeyword";
	static final String EXEMPT_MERGE_COMMITS_SOY_FIELD = "exemptMergeCommits";
	static final String BRANCH_NAMESPACES_SOY_FIELD = "branchNamespaces";
	
	final public Collection<String> branch_namespace_prefixes;
	final public String watershed_commit_string;
	final public Pattern exemption_regex_pattern;
	final public boolean check_for_issue_move;
	final public boolean fail_on_issue_move;
	final public boolean validate_issue_references_against_jira;
	final public boolean output_commit_message;
	final public boolean allow_exemption_keyword;
	final public boolean should_exempt_merge_commits;
	
	final public JiraConnection jira_connection;
	
	private LogMessageHookConfig(
			String watershed_commit,
			Collection<String> branch_namespace_prefixes,
			Pattern exemption_regex_pattern,
			boolean check_for_issue_move,
			boolean fail_on_issue_move,
			boolean validate_issue_references_against_jira,
			boolean output_commit_message,
			boolean allow_exemption_keyword,
			boolean should_exempt_merge_commits,
			JiraConnection jira_connection) {

		this.watershed_commit_string = watershed_commit;
		this.branch_namespace_prefixes = branch_namespace_prefixes;
		this.exemption_regex_pattern = exemption_regex_pattern;
		this.check_for_issue_move = check_for_issue_move;
		this.fail_on_issue_move = fail_on_issue_move;
		this.validate_issue_references_against_jira = validate_issue_references_against_jira;
		this.output_commit_message = output_commit_message;
		this.allow_exemption_keyword = allow_exemption_keyword;
		this.should_exempt_merge_commits = should_exempt_merge_commits;
		this.jira_connection = jira_connection;
	}

	static LogMessageHookConfig fromSettings(Settings settings, ApplicationLinkService applicationLinkService) {

		Collection<String> branch_namespace_prefixes = new HashSet<String>();
		String branch_namespaces_string = settings.getString(BRANCH_NAMESPACES_SOY_FIELD, "").trim();
		if (!branch_namespaces_string.isEmpty())
			for (String branch_prefix : branch_namespaces_string.split("\\s+"))
				branch_namespace_prefixes.add(branch_prefix);
		
		return new LogMessageHookConfig(
				settings.getString(LOG_MESSAGE_WATERSHED_COMMIT_SOY_FIELD, "").trim(),
				branch_namespace_prefixes,
				Pattern.compile(settings.getString(JIRA_EXEMPTION_PATTERN_SOY_FIELD, "#noissue\\b").trim()),
				settings.getBoolean(CHECK_ISSUE_MOVE_ENABLED_SOY_FIELD, false),
				settings.getBoolean(FAIL_ON_ISSUE_MOVE_ENABLED_SOY_FIELD, false),
				settings.getBoolean(CHECK_JIRA_EXISTENCE_ENABLED_SOY_FIELD, false),
				settings.getBoolean(PRINT_LOG_MESSAGE_ENABLED_SOY_FIELD, false),
				settings.getBoolean(ALLOW_EXEMPTION_KEYWORD_SOY_FIELD, false),
				settings.getBoolean(EXEMPT_MERGE_COMMITS_SOY_FIELD, false),
				new JiraConnection(
						settings.getBoolean(JiraConnection.JIRA_USE_APP_LINK_SOY_FIELD, false),
						settings.getString(JiraConnection.JIRA_BASE_URL_SOY_FIELD, "").trim(),
						settings.getString(JiraConnection.JIRA_USERNAME_SOY_FIELD, "").trim(),
						settings.getString(JiraConnection.JIRA_PASSWORD_SOY_FIELD, "").trim(),
						applicationLinkService
					)
			);
	}

	public boolean isRefMatchedByPrefix(RefChange ref_change) {
		
		if (this.branch_namespace_prefixes.isEmpty())
			return true;
		
		for (String branch_prefix : this.branch_namespace_prefixes) {
			String full_branch_path_prefix = Constants.R_HEADS + branch_prefix;
			if (ref_change.getRefId().startsWith(full_branch_path_prefix))
				return true;
		}
		
		return false;
	}
}