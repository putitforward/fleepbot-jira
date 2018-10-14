package com.m3clab.fleepjira.impl;

import com.m3clab.fleep.FleepBot;
import com.atlassian.sal.api.scheduling.PluginJob;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author SDNj
 */
public class FleepBotTask implements PluginJob {

	private static final Logger LOGGER = LoggerFactory.getLogger(FleepBotTask.class);

	/**
	 * Executes this job.
	 *
	 * @param jobDataMap any data the job needs to execute. Changes to this data will be remembered between executions.
	 */
	@Override
	public void execute(Map<String, Object> jobDataMap) {
		LOGGER.info("execute");
		final FleepBotMonitorImpl monitor = (FleepBotMonitorImpl) jobDataMap.get(FleepBotMonitorImpl.KEY);
		assert monitor != null;
		LOGGER.info("run");
		try {
			final FleepBot bot = monitor.getFleepBot();
			if (bot != null) {
				bot.init();
				bot.run();
			}
			monitor.setLastRun(new Date());
		} catch (Exception e) {
			LOGGER.error("Error: " + e.getMessage(), e);
		}
	}

}
