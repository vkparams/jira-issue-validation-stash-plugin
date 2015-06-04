package com.teslamotors.stash.logchecker;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import com.atlassian.applinks.api.ApplicationLink;
import com.atlassian.applinks.api.ApplicationLinkRequest;
import com.atlassian.applinks.api.ApplicationLinkResponseHandler;
import com.atlassian.applinks.api.ApplicationLinkService;
import com.atlassian.applinks.api.application.jira.JiraApplicationType;
import com.atlassian.sal.api.net.Request;
import com.atlassian.sal.api.net.Response;
import com.atlassian.sal.api.net.ResponseException;
import com.atlassian.stash.content.Changeset;
import com.google.common.collect.Sets;
import com.teslamotors.stash.logchecker.LogMessageHookConfig.JiraConnection;

// ========================================================================
/**
 * The jira-rest-java-client (https://ecosystem.atlassian.net/wiki/display/JRJC/Home)
 * could not be loaded by the Stash classloader, for 
 * some reason, so we will have to interact with the Jira rest services at a lower level.
 * See https://docs.atlassian.com/jira/REST/5.2/
 */
public class JiraIssueUtils {

	final static String ISSUE_PATTERN_STRING = "([A-Z]+)-(\\d+)";
	public final static Pattern JIRA_ISSUE_PATTERN = Pattern.compile(ISSUE_PATTERN_STRING);
	public final static Pattern NONEXSTENT_ISSUE_ERROR_PATTERN = Pattern.compile("An issue with key '(" + ISSUE_PATTERN_STRING + ")' does not exist");

	// ========================================================================
	static JSONObject getIssuesFromApplicationLink(ApplicationLinkService applicationLinkService, String full_query_path) throws Exception {

		ApplicationLink application_link = applicationLinkService.getPrimaryApplicationLink(JiraApplicationType.class);
		if (application_link == null)
			throw new Exception("ERROR: Administrator has not yet set up Jira Application Link!");

		ApplicationLinkRequest req = application_link.createAuthenticatedRequestFactory().createRequest(Request.MethodType.GET, 
				full_query_path);

		ApplicationLinkResponseHandler<JSONObject> handler = new ApplicationLinkResponseHandler<JSONObject>() {

			@Override
			public JSONObject credentialsRequired(Response response)
					throws ResponseException {
				return null;
			}

			@Override
			public JSONObject handle(Response response)
					throws ResponseException {
				return (JSONObject) JSONValue.parse(new InputStreamReader(response.getResponseBodyAsStream()));
			}};

		return req.execute(handler);
	}
	
	// ========================================================================
	public static String getQueryString(Map<String, String> query_parameters) {
		if (query_parameters.isEmpty()) {
			return "";
		} else {
			List<NameValuePair> params = new LinkedList<NameValuePair>();
			for (Entry<String, String> pair : query_parameters.entrySet())
				params.add(new BasicNameValuePair(pair.getKey(), pair.getValue()));

			return "?" + URLEncodedUtils.format(params, "utf-8");
		}
	}

	// ========================================================================
	public static JSONObject getJiraQueryJson(JiraConnection jira_connection, String api_command, Map<String, String> query_parameters) throws Exception {

		final String query_string = getQueryString(query_parameters);
		String full_query_path = "/rest/api/latest/" + api_command + query_string;

		if (!jira_connection.use_jira_application_link) {
			HttpClient httpClient = new DefaultHttpClient();

			String query_url = jira_connection.jira_base_url + full_query_path;
			HttpGet httpGet = new HttpGet(query_url);
			httpGet.addHeader(BasicScheme.authenticate(
					new UsernamePasswordCredentials(jira_connection.jira_username, jira_connection.jira_password),
					"UTF-8", false));

			HttpResponse httpResponse = httpClient.execute(httpGet);
			HttpEntity responseEntity = httpResponse.getEntity();
			InputStream input_stream = responseEntity.getContent();
			return (JSONObject) JSONValue.parse(new InputStreamReader(input_stream));
		} else {
			return getIssuesFromApplicationLink(jira_connection.applicationLinkService, full_query_path);
		}
	}

	// ========================================================================
	static Set<String> findIssues(Changeset changeset) {

		Matcher jira_issue_matcher = JIRA_ISSUE_PATTERN.matcher(changeset.getMessage());
		Set<String> jira_issues = Sets.newHashSet();
		while (jira_issue_matcher.find())
			jira_issues.add( jira_issue_matcher.group() );
		return jira_issues;
	}
}