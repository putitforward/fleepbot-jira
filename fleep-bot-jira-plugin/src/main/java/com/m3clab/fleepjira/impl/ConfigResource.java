package com.m3clab.fleepjira.impl;

import com.m3clab.fleepjira.api.ConfigEvent;
import com.atlassian.event.api.EventPublisher;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import javax.inject.Inject;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.atlassian.sal.api.user.UserManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author SDNj
 */
@Path("/")
public class ConfigResource {

	private final static Logger LOGGER = LoggerFactory.getLogger(ConfigResource.class);

	private final UserManager userManager;
	private final PluginSettingsFactory pluginSettingsFactory;
	private final TransactionTemplate transactionTemplate;
	private final EventPublisher eventPublisher;

	@Inject
	public ConfigResource(
			@ComponentImport UserManager userManager,
			@ComponentImport PluginSettingsFactory pluginSettingsFactory,
			@ComponentImport TransactionTemplate transactionTemplate,
			@ComponentImport EventPublisher eventPublisher) {
		this.userManager = userManager;
		this.pluginSettingsFactory = pluginSettingsFactory;
		this.transactionTemplate = transactionTemplate;
		this.eventPublisher = eventPublisher;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response get(@Context HttpServletRequest request) {
		String username = userManager.getRemoteUsername(request);
		if (username == null || !userManager.isSystemAdmin(username)) {
			return Response.status(Status.UNAUTHORIZED).build();
		}

		return Response.ok(transactionTemplate.execute(new TransactionCallback() {
			@Override
			public Object doInTransaction() {
				PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
				Config config = new Config();
				config.setLogin((String) settings.get(Config.class.getName() + ".login"));
				config.setPassword((String) settings.get(Config.class.getName() + ".password"));
				config.setKeys((String) settings.get(Config.class.getName() + ".keys"));
				return config;
			}
		})).build();
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	public Response put(final Config config, @Context HttpServletRequest request) {
		String username = userManager.getRemoteUsername(request);
		if (username == null || !userManager.isSystemAdmin(username)) {
			return Response.status(Status.UNAUTHORIZED).build();
		}

		Object result = transactionTemplate.execute(new TransactionCallback() {
			@Override
			public Object doInTransaction() {
				PluginSettings pluginSettings = pluginSettingsFactory.createGlobalSettings();
				pluginSettings.put(Config.class.getName() + ".login", config.getLogin());
				pluginSettings.put(Config.class.getName() + ".password", config.getPassword());
				pluginSettings.put(Config.class.getName() + ".keys", config.getKeys());
				LOGGER.info("Saved: " + config);
				return config;
			}
		});
		if (result != null) {
			eventPublisher.publish(new ConfigEvent());
		}
		return Response.noContent().build();
	}

}
