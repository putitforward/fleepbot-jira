package com.m3clab.fleepjira.impl;

import com.atlassian.plugin.spring.scanner.annotation.component.JiraComponent;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.m3clab.fleepjira.api.FleepBotPluginComponent;
import javax.inject.Named;
import org.springframework.beans.factory.annotation.Autowired;

@ExportAsService ({FleepBotPluginComponent.class})
@JiraComponent
@Named("fleepBotPluginComponent")
public class FleepBotPluginComponentImpl implements FleepBotPluginComponent {

	private final ApplicationProperties applicationProperties;

	@Autowired
	public FleepBotPluginComponentImpl(@ComponentImport final ApplicationProperties applicationProperties) {
		this.applicationProperties = applicationProperties;
	}

	@Override
	public String getName() {
		if (null != applicationProperties) {
			return "myComponent:" + applicationProperties.getDisplayName();
		}

		return "myComponent";
	}
}
