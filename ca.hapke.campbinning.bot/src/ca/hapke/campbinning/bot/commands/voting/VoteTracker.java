package ca.hapke.campbinning.bot.commands.voting;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage;
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.UnpinChatMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import ca.hapke.campbinning.bot.BotCommand;
import ca.hapke.campbinning.bot.CampingBotEngine;
import ca.hapke.campbinning.bot.channels.CampingChat;
import ca.hapke.campbinning.bot.channels.CampingChatManager;
import ca.hapke.campbinning.bot.commands.callback.CallbackId;
import ca.hapke.campbinning.bot.commands.inline.InlineCommandBase;
import ca.hapke.campbinning.bot.commands.response.CommandResult;
import ca.hapke.campbinning.bot.commands.response.EditTextCommandResult;
import ca.hapke.campbinning.bot.commands.response.SendResult;
import ca.hapke.campbinning.bot.commands.response.TextCommandResult;
import ca.hapke.campbinning.bot.commands.response.fragments.ResultFragment;
import ca.hapke.campbinning.bot.commands.response.fragments.TextFragment;
import ca.hapke.campbinning.bot.commands.response.fragments.TextStyle;
import ca.hapke.campbinning.bot.log.EventItem;
import ca.hapke.campbinning.bot.log.EventLogger;
import ca.hapke.campbinning.bot.users.CampingUser;
import ca.hapke.campbinning.bot.users.CampingUserMonitor;
import ca.hapke.campbinning.bot.util.TimeFormatter;

/**
 * @author Nathan Hapke
 *
 */
public abstract class VoteTracker<T> {

	public abstract float getScore();

	protected CampingBotEngine bot;
	protected final CampingUser ranter;
	protected final CampingUser activater;
	protected final Long chatId;
	protected CampingChat chat;
	protected Map<CampingUser, Integer> votes = new HashMap<>();
	protected Set<CampingUser> votesNotApplicable = new HashSet<>();
	protected boolean completed = false;
	protected NumberFormat nf;
	protected Message voteTrackingMessage;
//	protected String previousVotes;
	protected Message bannerMessage;
	protected String previousBanner;
	protected Message topicMessage;
	protected final long creationTime = System.currentTimeMillis();
	protected final long completionTime = System.currentTimeMillis() + getVotingTime();
	protected TimeFormatter formatter = new TimeFormatter(1, "", false, true);
	protected final String[] shortButtons;
	protected final String[] buttonCallbackIds;
	protected final String[] longDescriptions;
	protected final Map<Integer, T> valueMap;
	protected final boolean addNa;
	protected final int naIndex;

	protected static final TextFragment VOTING_COMPLETED = new TextFragment("Voting Completed!");
	protected static final String NOT_APPLICABLE_SHORT = "N/A";
	protected static final String NOT_APPLICABLE_LONG = "Not Applicable";
	protected final int naQuorum;
	private SendResult bannerResult;
	private SendResult voteTrackingResult;
	private TextCommandResult bannerText;

	public VoteTracker(CampingBotEngine bot, CampingUser ranter, CampingUser activater, Long chatId, Message activation,
			Message topic, int naQuorum, String command) throws TelegramApiException {
		this.ranter = ranter;
		this.activater = activater;
		this.chatId = chatId;

		this.naQuorum = naQuorum;
		this.bot = bot;
		this.chat = CampingChatManager.getInstance(bot).get(chatId);
		this.nf = NumberFormat.getInstance();

		nf.setMinimumFractionDigits(0);
		nf.setMaximumFractionDigits(1);

		List<VotingOption<T>> optionsList = new ArrayList<>();
		addNa = createOptions(optionsList);

		int count = optionsList.size() + (addNa ? 1 : 0);
		shortButtons = new String[count];
		longDescriptions = new String[count];
		buttonCallbackIds = new String[count];
		valueMap = new HashMap<Integer, T>(count);
		int updateId = topic.getMessageId();
		for (int i = 0; i < optionsList.size(); i++) {
			VotingOption<T> opt = optionsList.get(i);
			String shortStr = opt.shortButton;
			String longStr = opt.longDescription;
			T value = opt.value;

			setOption(command, updateId, i, shortStr, longStr, value);
		}

		if (addNa) {
			naIndex = count - 1;
			setOption(command, updateId, naIndex, NOT_APPLICABLE_SHORT, NOT_APPLICABLE_LONG, null);
		} else {
			naIndex = -1;
		}
		List<ResultFragment> bannerFrags = getBannerString();
		bannerText = new TextCommandResult(BotCommand.VoteTopicInitiation, bannerFrags);
		bannerText.setReplyTo(activation.getMessageId());
		bannerText.setKeyboard(getKeyboard());
		bannerResult = bannerText.send(bot, chatId);
		previousBanner = bannerResult.msg;
		bannerMessage = bannerResult.outgoingMsg;

		PinChatMessage pinBanner = new PinChatMessage(chatId, bannerMessage.getMessageId());
		pinBanner.setDisableNotification(Boolean.valueOf(true));
		bot.execute(pinBanner);

		TextCommandResult votesText = new TextCommandResult(BotCommand.VoteTopicInitiation, getVotesText(completed));
		votesText.setReplyTo(topic.getMessageId());
		voteTrackingResult = votesText.send(bot, chatId);
		voteTrackingMessage = voteTrackingResult.outgoingMsg;

		this.topicMessage = topic;
	}

	public void setOption(String command, int updateId, int i, String shortStr, String longStr, T value) {
		CallbackId id = new CallbackId(command, updateId, i);
		String callbackId = id.getId();

		shortButtons[i] = shortStr;
		longDescriptions[i] = longStr;
		buttonCallbackIds[i] = callbackId;
		valueMap.put(i, value);
	}

	/**
	 * @return add Not Applicable?
	 */
	protected abstract boolean createOptions(List<VotingOption<T>> optionsList);

	/**
	 * @return milliseconds
	 */
	protected abstract long getVotingTime();

	public EventItem react(CallbackId id, CallbackQuery callbackQuery) throws TelegramApiException {
		if (completed)
			return null;

		String callbackQueryId = callbackQuery.getId();

		String display = longDescriptions[id.getIds()[0]];

		int vote = id.getIds()[0];

		CampingUser user = CampingUserMonitor.getInstance().monitor(callbackQuery.getFrom());
		Integer previousVote = votes.get(user);
		boolean prevVoteNA = votesNotApplicable.contains(user);

		boolean voteChanged = false;
		if (previousVote == null && !prevVoteNA) {
			// first vote
			voteChanged = true;
		} else if (previousVote != null && vote != previousVote) {
			// had a non-n/a vote, and new vote is different
			voteChanged = true;
		} else if (vote == naIndex && !prevVoteNA) {
			// new vote is n/a, and wasn't before
			voteChanged = true;
		} else if (vote != naIndex && prevVoteNA) {
			// new vote is not n/a, and was before
			voteChanged = true;
		}

		if (vote == naIndex) {
			votes.remove(user);
			votesNotApplicable.add(user);
			if (votesNotApplicable.size() >= naQuorum)
				complete();
		} else {
			votes.put(user, vote);
			votesNotApplicable.remove(user);
		}
		if (voteChanged) {
			try {
				EditTextCommandResult editCmd = new EditTextCommandResult(BotCommand.Vote, voteTrackingMessage,
						getVotesText(completed));
				editCmd.send(bot, chatId);

			} catch (TelegramApiException e) {
				// HACK ignoring exceptions that come back for unchanged messages
			}
		}

		AnswerCallbackQuery answer = new AnswerCallbackQuery();
		String voteDisplayToUser;
		if (voteChanged)
			voteDisplayToUser = "Received your vote of: " + display + "!";
		else
			voteDisplayToUser = "Your vote was already: " + display + "!";
		answer.setText(voteDisplayToUser);
		answer.setCallbackQueryId(callbackQueryId);
		try {
			bot.execute(answer);
			return new EventItem(BotCommand.Vote, user, null, chat, bannerMessage.getMessageId(), display,
					topicMessage.getMessageId());
		} catch (Exception e) {
			return new EventItem(e.getLocalizedMessage());
		}
	}

	protected InlineKeyboardMarkup getKeyboard() {
		return InlineCommandBase.createKeyboard(shortButtons, buttonCallbackIds);
	}

	public void update() {
		EditTextCommandResult editCmd = new EditTextCommandResult(BotCommand.Vote, bannerMessage, getBannerString());

		if (!completed)
			editCmd.setKeyboard(getKeyboard());

		try {
			editCmd.send(bot, chatId);
		} catch (TelegramApiException e) {
			// HACK ignoring exceptions that come back for unchanged messages
		}
	}

	public void updateBannerFinished() {
		try {
			EditTextCommandResult edit = new EditTextCommandResult(BotCommand.VoteTopicComplete, bannerMessage,
					VOTING_COMPLETED);
			edit.send(bot, chatId);
		} catch (TelegramApiException e2) {
			// HACK ignoring exceptions that come back for unchanged messages
		}
	}

	public void unpinBanner() {
		try {
			Message pinnedMsg = bot.execute(new GetChat(chatId)).getPinnedMessage();
			if (pinnedMsg != null && bannerMessage.getMessageId().equals(pinnedMsg.getMessageId())) {
				UnpinChatMessage unpin = new UnpinChatMessage(chatId);
				bot.execute(unpin);
			}
		} catch (TelegramApiException e1) {
		}
	}

	public CommandResult createCompletionResult() {
		List<ResultFragment> votes = getVotesText(true);
		return new TextCommandResult(BotCommand.VoteTopicComplete, votes);
	}

	public void sendFinishedVotingMessage() {
		Integer messageId = topicMessage.getMessageId();
		EventLogger logger = EventLogger.getInstance();

		try {
			CommandResult completionMsg = createCompletionResult();
			completionMsg.setReplyTo(messageId);
			SendResult result = completionMsg.sendInternal(bot, chatId);

			logger.add(new EventItem(BotCommand.VoteTopicComplete, ranter, result.outgoingMsg.getDate(), chat,
					result.outgoingMsg.getMessageId(), result.outgoingMsg.getText(), messageId));
		} catch (TelegramApiException e) {
			logger.add(new EventItem(e.getLocalizedMessage()));
		}
	}

	public void complete() {
		if (completed)
			return;

		completed = true;
		updateBannerFinished();
		unpinBanner();

		sendFinishedVotingMessage();
	}

	public List<ResultFragment> getBannerString() {
		List<ResultFragment> sb = new ArrayList<>();
		String bannerTitle = getBannerTitle();
		sb.add(new TextFragment(bannerTitle));

		sb.add(new TextFragment(" ("));
		sb.add(new TextFragment(formatter.toPrettyString(completionTime)));
		sb.add(new TextFragment(" left)"));
		for (int i = 0; i < shortButtons.length && i < longDescriptions.length; i++) {
			String shorter = shortButtons[i];
			String longer = longDescriptions[i];

			sb.add(new TextFragment("\n"));
			sb.add(new TextFragment(shorter, TextStyle.Bold));
			sb.add(new TextFragment(": "));
			sb.add(new TextFragment(longer));
		}
		return sb;
	}

	public abstract String getBannerTitle();

	protected List<ResultFragment> getVotesText(boolean completed) {
		List<ResultFragment> output = new ArrayList<>();

		int notApplicable = votesNotApplicable.size();
		int naturalVotes = votes.size();
		int count = naturalVotes + notApplicable;
		float score = getScore();
		String scoreStr;
		if (naturalVotes > 0) {
			scoreStr = nf.format(score);
			String scoreSuffix = getScoreSuffix();
			if (scoreSuffix != null && scoreSuffix.length() > 0)
				scoreStr = scoreStr + scoreSuffix;
		} else {
			scoreStr = "(n/a)";
		}

		addVotesTextPrefix(completed, output);

		if (shouldShowVotesInCategories()) {
			int[] votes = new int[shortButtons.length];
			for (int vote : this.votes.values()) {
				votes[vote]++;
			}
			if (addNa) {
				votes[naIndex] = votesNotApplicable.size();
			}

			for (int i = 0; i < shortButtons.length; i++) {
				String txt = shortButtons[i];
				output.add(new TextFragment("\n"));
				output.add(new TextFragment(txt, TextStyle.Bold));
				output.add(new TextFragment(": "));
				output.add(new TextFragment(Integer.toString(votes[i])));
			}
		}

		output.add(new TextFragment("\n--------\nTotal Votes: "));
		output.add(new TextFragment(Integer.toString(count), TextStyle.Bold));

		output.add(new TextFragment("\n"));
		if (completed)
			output.add(new TextFragment("Final"));
		else
			output.add(new TextFragment("Current"));
		output.add(new TextFragment(" Score: "));
		output.add(new TextFragment(scoreStr, TextStyle.Bold));
		addVotesTextSuffix(output, completed, score);

		return output;
	}

	protected abstract boolean shouldShowVotesInCategories();

	public void addVotesTextPrefix(boolean completed, List<ResultFragment> sb) {
		if (completed)
			sb.add(VOTING_COMPLETED);
	}

	public void addVotesTextSuffix(List<ResultFragment> sb, boolean completed, float score) {
	}

	protected abstract String getScoreSuffix();

	public CampingUser getRanter() {
		return ranter;
	}

	public CampingUser getActivater() {
		return activater;
	}

	public Message getBanner() {
		return bannerMessage;
	}

	public long getCreationTime() {
		return creationTime;
	}

	public long getCompletionTime() {
		return completionTime;
	}

	public boolean isCompleted() {
		return completed;
	}

	public TextCommandResult getBannerText() {
		return bannerText;
	}

}