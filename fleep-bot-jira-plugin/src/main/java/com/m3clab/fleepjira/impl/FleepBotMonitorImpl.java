package com.m3clab.fleepjira.impl;

import com.m3clab.fleep.CreationException;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.project.ProjectService;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
//import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.atlassian.plugin.spring.scanner.annotation.component.JiraComponent;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.scheduling.PluginScheduler;
import com.m3clab.fleep.FleepBot;
import com.m3clab.fleep.FleepUser;
import com.m3clab.fleep.TicketDetails;
import com.m3clab.fleep.TicketHandler;
import com.m3clab.fleepjira.api.ConfigEvent;
import com.m3clab.fleepjira.api.FleepBotMonitor;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.annotation.concurrent.GuardedBy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

/**
 *
 * @author SDNj
 */
@ExportAsService
@JiraComponent
public class FleepBotMonitorImpl implements FleepBotMonitor, TicketHandler, LifecycleAware, InitializingBean, DisposableBean {

	private static final String PLUGIN_KEY = "com.m3clab.fleep-bot-jira-plugin";
	private static final Logger LOGGER = LoggerFactory.getLogger(FleepBotMonitorImpl.class.getName());
	static final String KEY = FleepBotMonitorImpl.class.getCanonicalName() + ":instace";
	private static final String JOB_NAME = FleepBotMonitorImpl.class.getCanonicalName() + ":job";

//	private final ApplicationProperties applicationProperties;
	private final PluginScheduler pluginScheduler;
	private final EventPublisher eventPublisher;
//	private final UserManager jiraUserManager;
	private final JiraAuthenticationContext authenticationContext;
	private final IssueService issueService;
	private final ProjectService projectService;
	private final UserSearchService userSearchService;
	private final PluginSettingsFactory pluginSettingsFactory;

	@GuardedBy("this")
	private final Set<LifecycleEvent> lifecycleEvents = EnumSet.noneOf(LifecycleEvent.class);

	private Pattern command;

	private Date lastRun;
	private FleepBot bot;
	private Pattern pattern;

	@Autowired
	public FleepBotMonitorImpl(
			//			@ComponentImport final ApplicationProperties applicationProperties,
			@ComponentImport final PluginScheduler pluginScheduler,
			@ComponentImport final EventPublisher eventPublisher,
			//			@ComponentImport final UserManager jiraUserManager,
			@ComponentImport final JiraAuthenticationContext authenticationContext,
			@ComponentImport final IssueService issueService,
			@ComponentImport final ProjectService projectService,
			@ComponentImport final UserSearchService userSearchService,
			@ComponentImport final PluginSettingsFactory pluginSettingsFactory) {
//		this.applicationProperties = applicationProperties;
		this.pluginScheduler = pluginScheduler;
		this.eventPublisher = eventPublisher;
//		this.jiraUserManager = jiraUserManager;
		this.authenticationContext = authenticationContext;
		this.issueService = issueService;
		this.projectService = projectService;
		this.userSearchService = userSearchService;
		this.pluginSettingsFactory = pluginSettingsFactory;
	}

	@Bean
	public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	private void init() {
		PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
		String configName = Config.class.getName();
		final String login = (String) settings.get(configName + ".login");
		final String password = (String) settings.get(configName + ".password");
		final String keys = (String) settings.get(configName + ".keys");

		String exp = "@(" + keys + ")(.+)";
		command = Pattern.compile(exp, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

		exp = "(" + keys.replaceAll("\\|", "-\\\\d+)|(") + "-\\d+)|"
				+ "(@" + keys.replaceAll("\\|", "\\\\s.+)|(@") + "\\s.+)";
		pattern = Pattern.compile(exp, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

		bot = new FleepBot(login, password, this);
		LOGGER.info(String.format("login=%s keys=%s pattern=%s command=%s", login, keys, pattern.pattern(), command.pattern()));
	}

	FleepBot getFleepBot() {
		if (bot == null) {
			try {
				init();
			} catch (Throwable e) {
				LOGGER.error(e.getMessage());
			}
		}
		return bot;
	}

	@EventListener
	public void onConfigUpdated(ConfigEvent event) {
		LOGGER.info("onConfigUpdated");
		bot = null;
		unschedule();
		schedule();
	}

	private void schedule() {
		pluginScheduler.scheduleJob(JOB_NAME, FleepBotTask.class,
				new HashMap<String, Object>() {
			{
				put(KEY, FleepBotMonitorImpl.this);
			}
		}, new Date(), 15000);
		LOGGER.info("started");
	}

	@Override
	public TicketDetails processMessage(FleepUser user, String message, String room) throws Exception {
		LOGGER.info("process: " + message);

		ApplicationUser adminUser = getUserByEmail(user);

		MutableIssue issue;
		Matcher matcher = command.matcher(message);
		if (matcher.find()) {
			try {
				issue = createIssue(matcher, message, room, adminUser);
			} catch (Exception exception) {
				throw new CreationException("Error: " + exception.getMessage());
			}
		} else {
//			ApplicationUser adminUser = jiraUserManager.getUserByName(getAdmin());
			authenticationContext.setLoggedInUser(adminUser);
			IssueService.IssueResult issueResult = issueService.getIssue(adminUser, message.toUpperCase());
			issue = issueResult.getIssue();
		}
		if (issue != null) {
			String link = ComponentAccessor.getApplicationProperties().getString(APKeys.JIRA_BASEURL) + "/browse/" + issue.getKey();
			return new JiraDetails(issue, link);
		}
		throw new Exception(message + " does not exist");
	}

	private ApplicationUser getUserByEmail(FleepUser user) throws Exception {
		String email = user.getEmail();
		LOGGER.info("Search: " + email);
		Iterator<ApplicationUser> users = userSearchService.findUsersByEmail(email).iterator();
		if (!users.hasNext()) {
			LOGGER.error("User not found.");
			throw new Exception("User not found");
		}
		return users.next();
	}

	private MutableIssue createIssue(Matcher matcher, String message, String room, ApplicationUser adminUser) throws Exception {
		String projectId = matcher.group(1).toUpperCase();
		String summary = room + ": " + matcher.group(2).trim().replaceFirst("<.+", "");
		String fixVersions = summary.replaceFirst(".+fixVersions=", "");
		fixVersions = fixVersions.isEmpty() ? "16100" : fixVersions;
		String desc = message.substring(message.indexOf(summary) + summary.length()).trim();
		desc = desc.replaceFirst("^(<br/>)", "").replaceFirst("(</msg>)$", "")
				.replaceAll("<p>", "").replaceAll("</p>", "\n");
		LOGGER.info(String.format("new ticket: project=%s\nsummary: %s\ndescription: %s %s",
				projectId, summary, desc, fixVersions));

		authenticationContext.setLoggedInUser(adminUser);
		ProjectService.GetProjectResult project = projectService.getProjectByKey(projectId);
		Collection<IssueType> issueTypes = project.get().getIssueTypes();
		String issueTypeId;
		if (issueTypes != null && !issueTypes.isEmpty()) {
			issueTypeId = issueTypes.iterator().next().getId();
		} else {
			throw new Exception("Issue Types does not exist");
		}
		IssueInputParameters iip = issueService.newIssueInputParameters()
				.setProjectId(project.get().getId())
				.setIssueTypeId(issueTypeId)
				.setSummary(summary)
				.setDescription(desc)
				//.setReporterId(adminUser.getName())
				.addCustomFieldValue("fixVersions", fixVersions);
		IssueService.CreateValidationResult cvr = issueService.validateCreate(adminUser, iip);
		if (!cvr.isValid()) {
			String errors = "";
			if (cvr.getWarningCollection().hasAnyWarnings()) {
				errors += "Warnings: " + cvr.getWarningCollection().getWarnings().stream()
						.collect(Collectors.joining(";"));
			}
			if (cvr.getErrorCollection().hasAnyErrors()) {
				errors += "Errors: " + cvr.getErrorCollection().getErrorMessages().stream()
						.collect(Collectors.joining(";"));
			}
			LOGGER.error("Issue validation: " + issueTypeId + " " + errors);
			throw new Exception("Issue validation: " + errors);
		}
		IssueService.IssueResult issueResult = issueService.create(adminUser, cvr);
		return issueResult.getIssue();
	}

	@Override
	public Pattern getKeyPattern() {
		return pattern;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		LOGGER.info("afterPropertiesSet");
		registerListener();
		onLifecycleEvent(LifecycleEvent.AFTER_PROPERTIES_SET);
//		if (lifecycleManager.isApplicationSetUp()) {
//			onLifecycleEvent(LifecycleEvent.LIFECYCLE_AWARE_ON_START);
//		}
	}

	@Override
	public void onStart() {
		LOGGER.info("onStart");
		onLifecycleEvent(LifecycleEvent.LIFECYCLE_AWARE_ON_START);
	}

	@EventListener
	public void onPluginEnabled(PluginEnabledEvent event) {
		LOGGER.info("onPluginEnabled");
		if (PLUGIN_KEY.equals(event.getPlugin().getKey())) {
			onLifecycleEvent(LifecycleEvent.PLUGIN_ENABLED);
		}
	}

	@Override
	public Date getLastRun() {
		return lastRun;
	}

	public void setLastRun(Date lastRun) {
		this.lastRun = lastRun;
	}

	@Override
	public void onStop() {
		if (bot != null) {
			bot.setStoped(true);
		}
		LOGGER.info("stoped");
	}

	@Override
	public void destroy() throws Exception {
		unregisterListener();
		unschedule();
	}

	private void unregisterListener() {
		LOGGER.debug("unregisterListeners");
		eventPublisher.unregister(this);
	}

	@PreDestroy
	private void unschedule() {
		pluginScheduler.unscheduleJob(JOB_NAME);
	}

	private void registerListener() {
		LOGGER.debug("registerListeners");
		eventPublisher.register(this);
	}

	private void onLifecycleEvent(LifecycleEvent event) {
		LOGGER.debug("onLifecycleEvent: " + event);
		if (isLifecycleReady(event)) {
			LOGGER.debug("Got the last lifecycle event... Time to get started!");

			try {
				schedule();
			} catch (Exception ex) {
				LOGGER.error("Unexpected error during launch", ex);
			}
		}
	}

	synchronized private boolean isLifecycleReady(LifecycleEvent event) {
		return lifecycleEvents.add(event) && lifecycleEvents.size() == LifecycleEvent.values().length;
	}

	static enum LifecycleEvent {
		AFTER_PROPERTIES_SET,
		PLUGIN_ENABLED,
		LIFECYCLE_AWARE_ON_START
	}
}
