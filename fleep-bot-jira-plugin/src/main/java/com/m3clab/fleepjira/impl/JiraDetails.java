package com.m3clab.fleepjira.impl;

import com.atlassian.jira.issue.MutableIssue;
import com.m3clab.fleep.TicketDetails;

/**
 *
 * @author SDNj
 */
public class JiraDetails implements TicketDetails {

	private final MutableIssue issue;
	private final String link;


	JiraDetails(MutableIssue issue, String link) {
		this.issue = issue;
		this.link = link;
	}

	@Override
	public String toMessage() {
		return String.format("*%s* %s [%s]\n%s", issue.getKey(), issue.getSummary(), issue.getStatus().getName(), link);
	}

}
