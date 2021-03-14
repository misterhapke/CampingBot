package ca.hapke.campingbot.response.fragments;

import ca.hapke.campingbot.processors.MessageProcessor;
import ca.hapke.campingbot.users.CampingUser;

/**
 * If Target is not in the channel, it won't display on the client. Not my fault.
 * 
 * @author Nathan Hapke
 */
public class MentionFragment extends ResultFragment {
	private CampingUser target;
	private String prefix, suffix;
	private MentionDisplay displayMode;

	public MentionFragment(CampingUser target) {
		this(target, MentionDisplay.Nickname, CaseChoice.Normal, TextStyle.Normal, null, null);
	}

	public MentionFragment(CampingUser target, TextStyle textStyle) {
		this(target, MentionDisplay.Nickname, CaseChoice.Normal, textStyle, null, null);
	}

	public MentionFragment(CampingUser target, CaseChoice caseChoice) {
		this(target, MentionDisplay.Nickname, caseChoice, TextStyle.Normal, null, null);
	}

	public MentionFragment(CampingUser target, MentionDisplay displayMode, CaseChoice caseChoice) {
		this(target, displayMode, caseChoice, TextStyle.Normal, null, null);
	}

	public MentionFragment(CampingUser target, MentionDisplay displayMode, CaseChoice caseChoice, TextStyle textStyle,
			String prefix, String suffix) {
		super(caseChoice, textStyle);
		this.target = target;
		this.displayMode = displayMode;
		this.prefix = prefix;
		this.suffix = suffix;
	}

	@Override
	public String getValue(MessageProcessor processor, boolean useMarkupV2) {
		String display = getDisplayText();
		if (prefix != null)
			display = prefix + display;
		if (suffix != null)
			display = display + suffix;

		display = casify(display);
		display = processor.processString(display, useMarkupV2);
		display = markup(display);

		if (useMarkupV2) {
			int telegramId = target.getTelegramId();
			return "[" + display + "](tg://user?id=" + telegramId + ")";
		} else {
			return display;
		}
	}

	public String getDisplayText() {
		// TODO make display a field so that transform can work
		String display;
		switch (displayMode) {
		case Nickname:
			display = target.getDisplayName();
			break;
		case Username:
			display = target.getUsername();
			break;
		case First:
		default:
			display = target.getFirstOrUserName();
			break;

		}
		return display;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Mention[");
		builder.append(target.getUsername());
		builder.append(" => ");
		builder.append(getDisplayText());
		builder.append("]");
		return builder.toString();
	}

	@Override
	public ResultFragment transform(MessageProcessor proc, boolean useMarkupV2) {
		return this;
	}

}
