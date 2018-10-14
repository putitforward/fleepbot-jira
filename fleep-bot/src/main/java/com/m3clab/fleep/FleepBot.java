package com.m3clab.fleep;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.api.client.filter.LoggingFilter;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author SDNj
 */
public class FleepBot {

	private static final Logger LOGGER = LoggerFactory.getLogger(FleepBot.class);

	private JsonNodeFactory nodeFactory;

	private final String login;
	private final String password;
	private final TicketHandler ticketHandler;
	private final Pattern pattern;

	private boolean process;
	private boolean stoped;
	private String accountId;
	private int eventHorizon;
	private String ticket;
	private Session session;
	private WebResource target;

	public FleepBot(String login, String password, TicketHandler ticketHandler) {
		this.login = login;
		this.password = password;
		this.ticketHandler = ticketHandler;
		this.pattern = ticketHandler.getKeyPattern();
	}

	public void init() {
		if (target == null) {
			target = getTarget();
			login(target);
		}
	}

	public void run() {
		if (!process && !stoped) {
			try {
				listen();
			} catch (Throwable e) {
				process = false;
			}
		}
	}

	public void setStoped(boolean stoped) {
		this.stoped = stoped;
	}

	private void listen() {
		int newEventHorizon = initEventHorizon();
		do {
			eventHorizon = newEventHorizon;
			JsonNode response = read(eventHorizon, target);
			newEventHorizon = getEventHorizon(response);
			final JsonNode node = response.get("stream");
			if (node != null) {
				match(node);
			}
		} while (process = (newEventHorizon > eventHorizon && !stoped));
		if (stoped) {
			this.eventHorizon = 0;
		}
	}

	private void match(final JsonNode node) {
		Iterator<JsonNode> messages = node.iterator();
		while (messages.hasNext()) {
			JsonNode message = messages.next();

			if ("message".equals(message.get("mk_rec_type").getTextValue())
					&& !accountId.equals(message.get("account_id").getTextValue())
					&& "text".equals(message.get("mk_message_type").getTextValue())
					&& !message.get("is_edit").getBooleanValue()
					&& message.get("pin_weight") == null) {
				LOGGER.debug("message: " + message.toString());

				String user = message.get("account_id").getTextValue();
				FleepUser fleepUser = new FleepUser(user) {
					@Override
					public String getEmail() {
						return getUserEmail(this.getAccountId());
					}
				};
				String room = getConversation(message.get("conversation_id").getTextValue());

				Matcher matcher = pattern.matcher(message.get("message").getTextValue());
				while (matcher.find()) {
					String match = matcher.group();
					LOGGER.debug("match: " + match);
					try {
						TicketDetails details = null;
						try {
							details = ticketHandler.processMessage(fleepUser, match, room);
						} catch (final CreationException exception) {
							details = new TicketDetails() {
								@Override
								public String toMessage() {
									return exception.getMessage();
								}
							};
						}
						send(details, message);
					} catch (Exception ex) {
						LOGGER.error(ex.getMessage(), ex);
					}
				}
			}
		}
	}

	public String getUserEmail(String accountId) {
		ObjectNode json = getNode();
		json.put("contact_id", accountId);
		JsonNode response = post(target
				.path("contact/sync"), json);
		return response.get("email").getTextValue();
	}

	public String getConversation(String conversationId) {
		ObjectNode json = getNode();
		json.put("mk_direction", "ic_header");
		JsonNode response = post(target
				.path("conversation/sync/" + conversationId), json);
		return response.get("header").get("topic").getTextValue();
	}

	private void send(TicketDetails details, JsonNode message) {
		ObjectNode json = getNode();
		json.put("message", details.toMessage());
		JsonNode response = post(target
				.path("message/send")
				.path(message.get("conversation_id").getTextValue()), json);
	}

	private JsonNode read(int event_horizon, WebResource target) {
		ObjectNode json = getNode();
		json.put("event_horizon", event_horizon);
		json.put("wait", true);
		ArrayNode flags = json.putArray("poll_flags");
		flags.add("skip_hidden");
		flags.add("skip_rest");
		JsonNode response = post(target
				.path("account/poll"), json);
		return response;
	}

	private JsonNode post(WebResource target, ObjectNode json) {
		json.put("ticket", ticket);
		WebResource.Builder builder = target.getRequestBuilder();
		builder = builder.cookie(new NewCookie("token_id", session.getToken()));

		JsonNode response = builder
				.accept(MediaType.APPLICATION_JSON_TYPE)
				.post(JsonNode.class, json);
		return response;
	}

	private void login(WebResource target) {
		ObjectNode json = getNode();
		json.put("email", login);
		json.put("password", password);

		JsonNode response = target
				.path("account/login")
				.accept(MediaType.APPLICATION_JSON_TYPE)
				.post(JsonNode.class, json);

		ticket = response.get("ticket").getTextValue();
		accountId = response.get("account_id").getTextValue();
	}

	private WebResource getTarget() {
		ClientConfig config = new DefaultClientConfig();
		config.getClasses().add(JacksonJsonProvider.class);
		Client client = Client.create(config);
		session = new Session();
		client.addFilter(session);
		client.addFilter(new LoggingFilter(java.util.logging.Logger.getLogger(FleepBot.class.getName())));

		return client.resource("https://fleep.io/api");
	}

	private ObjectNode getNode() {
		if (nodeFactory == null) {
			nodeFactory = new ObjectMapper().getNodeFactory();
		}
		return nodeFactory.objectNode();
	}

	private int initEventHorizon() {
		if (eventHorizon == 0) {
			ObjectNode json = getNode();
			JsonNode response = post(target
					.path("account/sync"), json);
			eventHorizon = getEventHorizon(response);
		}
		return eventHorizon;
	}

	private static int getEventHorizon(JsonNode response) {
		return response.get("event_horizon").getIntValue();
	}

	private static class Session extends ClientFilter {

		private String token;

		public String getToken() {
			return token;
		}

		@Override
		public ClientResponse handle(ClientRequest cr) throws ClientHandlerException {
			cr.getHeaders().putSingle(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
			ClientResponse response = getNext().handle(cr);
			final List<NewCookie> cookies = response.getCookies();
			if (cookies != null) {
				for (NewCookie cookie : cookies) {
					if ("token_id".equals(cookie.getName())) {
						token = cookie.getValue();
					}
				}
			}
			return response;
		}
	}

}
