package ca.hapke.campbinning.bot.commands;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Map.Entry;

import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import ca.hapke.campbinning.bot.AccessLevel;
import ca.hapke.campbinning.bot.commands.api.BotCommandIds;
import ca.hapke.campbinning.bot.commands.api.SlashCommand;
import ca.hapke.campbinning.bot.commands.api.SlashCommandType;
import ca.hapke.campbinning.bot.commands.inline.HideItCommand;
import ca.hapke.campbinning.bot.commands.inline.HideItMessage;
import ca.hapke.campbinning.bot.response.CommandResult;
import ca.hapke.campbinning.bot.response.TextCommandResult;
import ca.hapke.campbinning.bot.response.fragments.TextStyle;
import ca.hapke.campbinning.bot.ui.IStatus;
import ca.hapke.campbinning.bot.users.CampingUser;
import ca.hapke.campbinning.bot.util.TimeFormatter;

/**
 * @author Nathan Hapke
 */
public class StatusCommand extends AbstractCommand implements IStatus, SlashCommand {
	private static final String STATUS = "Status";
	public static final SlashCommandType SlashStatus = new SlashCommandType(STATUS, "status",
			BotCommandIds.TEXT | BotCommandIds.USE);
	private static final SlashCommandType[] SLASH_COMMANDS = new SlashCommandType[] { SlashStatus };
	private TimeFormatter tf = new TimeFormatter(2, ", ", false, false);
	private ZonedDateTime onlineTime;
	private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("LLLL d, h:mm:ss a");
	private HideItCommand hideIt;

	public StatusCommand(HideItCommand hideIt) {
		this.hideIt = hideIt;
	}

	@Override
	public void statusOffline() {
		onlineTime = null;
	}

	@Override
	public void statusOnline() {
		onlineTime = ZonedDateTime.now();
	}

	@Override
	public void statusMeProvided(CampingUser me) {
	}

	@Override
	public void connectFailed(TelegramApiRequestException e) {
		onlineTime = null;
	}

	@Override
	public CommandResult respondToSlashCommand(SlashCommandType command, Message message, Long chatId,
			CampingUser campingFromUser) {
		TextCommandResult r = new TextCommandResult(SlashStatus);
		r.add("Online Since", TextStyle.Bold);
		r.add(": ");
		if (onlineTime != null) {
			r.add(onlineTime.format(formatter));
			r.add("\nDuration", TextStyle.Bold);
			r.add(": ");
			r.add(tf.toPrettyString(onlineTime));
		} else {
			r.add("???");
		}
		r.add("\nHide It", TextStyle.Bold);

		Map<String, String> confirmedTopics = hideIt.getTopics().asMap();
		r.add("\n");
		r.add("Topics (" + confirmedTopics.size() + ") ", TextStyle.Italic);
		for (Map.Entry<String, String> e : confirmedTopics.entrySet()) {
			r.add(e.getValue());
			r.add(" ");
		}

		Map<Integer, HideItMessage> msgs = hideIt.getConfirmedMessages();
		r.add("\n");
		r.add("Messages (" + msgs.size() + ") ", TextStyle.Italic);
		for (Entry<Integer, HideItMessage> e : msgs.entrySet()) {
			r.add("\n");
			r.add(e.getKey());
			r.add(": ");
			r.add(e.getValue().getClearText());
		}
		return r;
	}

	@Override
	public SlashCommandType[] getSlashCommandsToRespondTo() {
		return SLASH_COMMANDS;
	}

	@Override
	public String getCommandName() {
		return STATUS;
	}

	@Override
	public AccessLevel accessRequired() {
		return AccessLevel.Admin;
	}
}
