package com.teslamotors.stash.logchecker;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import com.atlassian.applinks.api.ApplicationLinkService;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

// ========================================================================
/**
 * This is the only class that does any network interaction with Jira.
 */
public class IssueExistenceResult {
	public final Set<String> nonexistent_issues = Sets.newHashSet();
	public final Map<String, String> moved_issue_map = Maps.newHashMap();
	
	@SuppressWarnings("unchecked")
	public void populateIssueMovesAndNonexistence(ApplicationLinkService applicationLinkService, final Collection<String> issues, LogMessageHookConfig config) throws Exception {
		
		/**
		 * This variable is not consumed.
		 */
		Set<String> validated_issue_references = Sets.newHashSet();
		
		@SuppressWarnings("serial")
		JSONObject bulk_query_json_object = JiraIssueUtils.getJiraQueryJson(config.jira_connection, "search", new HashMap<String, String>() {{
			put("fields", "summary");
			put("jql", "issue in (" + Joiner.on(',').join(issues) + ")");
		}});
		
		if (bulk_query_json_object.containsKey("errorMessages")) {
			
			Collection<String> first_query_error_messages = (Collection<String>) bulk_query_json_object.get("errorMessages");
			
			Collection<String> unaccounted_error_messages = Lists.newArrayList();
			for (String error_message : first_query_error_messages) {
				Matcher m = JiraIssueUtils.NONEXSTENT_ISSUE_ERROR_PATTERN.matcher(error_message);
				if (m.find())
					this.nonexistent_issues.add(m.group(1));
				else
					unaccounted_error_messages.add(error_message);
			}
			
			if (!unaccounted_error_messages.isEmpty())
				throw new Exception(Joiner.on('\n').join(unaccounted_error_messages));

			
			// Find moved issues
			// This might be slow, because it performs one Jira query per issue.
			if (config.check_for_issue_move) {
				for (String nonexistent_issue : this.nonexistent_issues) {
					
					JSONObject moved_query_json_object = JiraIssueUtils.getJiraQueryJson(config.jira_connection, "issue/" + nonexistent_issue, new HashMap<String, String>());
					if (moved_query_json_object.containsKey("errorMessages")) {
	
						Collection<String> moved_query_error_messages = (Collection<String>) moved_query_json_object.get("errorMessages");
						for (String message : moved_query_error_messages) {
							if ("Issue Does Not Exist".equalsIgnoreCase(message)) {
								// Issue does not exist and was not moved.
							}
						}
						
					} else {
						this.moved_issue_map.put(nonexistent_issue, (String) moved_query_json_object.get("key"));
					}
				}
			}
			
		} else {

			JSONArray issues_array = (JSONArray) bulk_query_json_object.get("issues");
			for (Object foo : issues_array) {
				JSONObject issue_object = (JSONObject) foo;
				String issue_key = (String) issue_object.get("key");
				validated_issue_references.add(issue_key);
			}
		}
	}
}