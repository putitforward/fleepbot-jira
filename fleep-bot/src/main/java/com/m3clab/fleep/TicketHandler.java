package com.m3clab.fleep;

import java.util.regex.Pattern;

/**
 *
 * @author SDNj
 */
public interface TicketHandler {

	public TicketDetails processMessage(FleepUser user, String message, String room) throws Exception;

	public Pattern getKeyPattern();

}
