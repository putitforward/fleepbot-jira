package ut.com.m3clab.fleep;

import com.m3clab.fleep.FleepBot;
import com.m3clab.fleep.FleepUser;
import com.m3clab.fleep.TicketDetails;
import com.m3clab.fleep.TicketHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author SDNj
 */
public class FleepBotTest {

	private static final Logger LOGGER = Logger.getLogger(FleepBot.class.getName());

	public FleepBotTest() {
	}

	@BeforeClass
	public static void setUpClass() {
	}

	@AfterClass
	public static void tearDownClass() {
	}

	@Before
	public void setUp() {
	}

	@After
	public void tearDown() {
	}

	@Test
	public void test() {
		TicketHandler handler = new TicketHandlerImpl();
		FleepBot bot = new FleepBot("sdnjava@fleep.io", System.getProperty("pass"), handler);
		bot.init();

		int i = 11;

		do {
			bot.run();
			try {
				Thread.sleep(10000);
			} catch (InterruptedException ex) {
			}
		} while (--i > 0);
	}

	@Test
	public void testTicketHandler() throws Exception {
		TicketHandler ticketHandler = new TicketHandlerImpl();

		handle(ticketHandler, "afafsaf");
		handle(ticketHandler, "BOS-1");
		handle(ticketHandler, "BOS lnklkkn WF-3 klnlnln PIF-jjj99");
		handle(ticketHandler, "pif-3");
		handle(ticketHandler, "@bos lknln pif-4 mkl\nddddddddddd");
		handle(ticketHandler, "<msg><p>@PIF ssssssssssss sssssssssssssss<br/>dddddddddd<br/>oooooooooooo oooooooo</p></msg>");
	}

	private void handle(TicketHandler ticketHandler, String message) throws Exception {
		Pattern pattern = ticketHandler.getKeyPattern();
		Matcher matcher = pattern.matcher(message);
		while (matcher.find()) {
			String match = matcher.group();
			LOGGER.log(Level.INFO, "match: {0}", match);
			TicketDetails details = ticketHandler.processMessage(new TestFleepUser("user1"), match, "Room1");
			LOGGER.log(Level.INFO, "result: {0}", details.toMessage());
		}
	}

	private static class TestFleepUser extends FleepUser {

		public TestFleepUser(String accountId) {
			super(accountId);
		}

		@Override
		public String getEmail() {
			return super.getAccountId();
		}
	}

	private class TicketHandlerImpl implements TicketHandler {

		Pattern command = Pattern.compile("@(BOS|PIF|UTEST)(.+)",
				Pattern.CASE_INSENSITIVE);
		Pattern pattern = Pattern.compile("(BOS-\\d+)|(@BOS .+)|(@PIF .+)|(PIF-\\d+)|(@UTEST .+)|(UTEST-\\d+)",
				Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

		public TicketHandlerImpl() {
		}

		@Override
		public TicketDetails processMessage(final FleepUser user, final String message, final String room) {
			return new TicketDetails() {
				@Override
				public String toMessage() {
					Matcher matcher = command.matcher(message);
					if (matcher.find()) {
						String project = matcher.group(1).toUpperCase();
						String summary = room + ": " + matcher.group(2).trim().replaceFirst("<.+", "");
						String desc = message.substring(message.indexOf(summary) + summary.length()).trim();
						desc = desc.replaceFirst("^(<br/>)", "<p>").replaceFirst("(</msg>)$", "");

						LOGGER.log(Level.INFO, "new ticket: project={0}\nsummary: {1}\ndescription: {2}",
								new Object[]{project, summary, desc});

						return project + "-XXX " + summary + " " + user.getEmail();
					} else {
						return "*"+message.toUpperCase() + "*\nsummary status";
					}
				}
			};
		}

		@Override
		public Pattern getKeyPattern() {
			return pattern;
		}
	}
}
