package com.teslamotors.stash.logchecker;

import com.atlassian.applinks.api.ApplicationLinkService;
import com.atlassian.stash.server.ApplicationPropertiesService;
import com.atlassian.stash.setting.RepositorySettingsValidator;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.setting.SettingsValidationErrors;

public class SettingsValidator implements RepositorySettingsValidator {

	private final ApplicationPropertiesService applicationPropertiesService;
	private final ApplicationLinkService applicationLinkService;

	public SettingsValidator(
			ApplicationPropertiesService applicationPropertiesService,
			ApplicationLinkService applicationLinkService) {
		this.applicationPropertiesService = applicationPropertiesService;
		this.applicationLinkService = applicationLinkService;
	}
	
	// ========================================================================
	@Override
	public void validate(Settings settings, SettingsValidationErrors validation_errors,
			com.atlassian.stash.repository.Repository stash_repo) {

		LogMessageHookConfig hook_config = LogMessageHookConfig.fromSettings(settings, this.applicationLinkService);

		if (!hook_config.jira_connection.use_jira_application_link) {
			if (hook_config.jira_connection.jira_base_url.isEmpty() )
				validation_errors.addFieldError(LogMessageHookConfig.JiraConnection.JIRA_BASE_URL_SOY_FIELD, "Host must not be empty.");
			
			if (hook_config.jira_connection.jira_username.isEmpty() )
				validation_errors.addFieldError(LogMessageHookConfig.JiraConnection.JIRA_USERNAME_SOY_FIELD, "Username must not be empty.");
			
			if (hook_config.jira_connection.jira_password.isEmpty() )
				validation_errors.addFieldError(LogMessageHookConfig.JiraConnection.JIRA_PASSWORD_SOY_FIELD, "Password must not be empty.");
		}
		
		if (hook_config.allow_exemption_keyword)
			if (hook_config.exemption_regex_pattern.pattern().isEmpty())
				validation_errors.addFieldError(LogMessageHookConfig.JIRA_EXEMPTION_PATTERN_SOY_FIELD, "ERROR: An empty pattern will exempt all commits!");
		
		try {

			WatershedCommitFilter.create(
					this.applicationPropertiesService,
					stash_repo,
					hook_config);

		} catch (Exception e) {

			validation_errors.addFieldError(LogMessageHookConfig.LOG_MESSAGE_WATERSHED_COMMIT_SOY_FIELD, e.getMessage());
		}
	}
}
