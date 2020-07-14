package ca.hapke.campbinning.bot.commands.inline;

import java.util.List;

import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;

import ca.hapke.campbinning.bot.BotCommand;
import ca.hapke.campbinning.bot.CampingBotEngine;
import ca.hapke.campbinning.bot.CommandType;
import ca.hapke.campbinning.bot.commands.SpellGenerator;
import ca.hapke.campbinning.bot.commands.SpellResult;
import ca.hapke.campbinning.bot.commands.callback.CallbackId;
import ca.hapke.campbinning.bot.commands.response.MessageProcessor;
import ca.hapke.campbinning.bot.commands.response.fragments.ResultFragment;
import ca.hapke.campbinning.bot.log.EventItem;
import ca.hapke.campbinning.bot.users.CampingUser;

/**
 * @author Nathan Hapke
 *
 */
public class SpellInlineCommand extends InlineCommandBase {

	private static final String INLINE_SPELL = "spell";
	private SpellGenerator spellGen;

	public SpellInlineCommand(SpellGenerator spellGen) {
		this.spellGen = spellGen;
	}

	@Override
	public String getCommandName() {
		return INLINE_SPELL;
	}

	@Override
	public EventItem chosenInlineQuery(Update update, CallbackId id, CampingUser campingFromUser, String resultText) {
		int[] ids = id.getIds();

		int targetUserId = ids[0];
		boolean success = ids[1] > 0;

		CampingUser targetUser = userMonitor.getUser(targetUserId);

		CommandType cmd = success ? BotCommand.Spell : BotCommand.SpellDipshit;

		EventItem event = new EventItem(cmd, campingFromUser, null, null, id.getUpdateId(), resultText,
				targetUser.getCampingId());
		return event;
	}

	@Override
	public InlineQueryResult[] provideInlineQuery(Update update, String input, int updateId,
			MessageProcessor processor) {
		String[] words = input.split(" ");
		if (words.length == 0)
			return null;

		List<ResultFragment> outputSpell;
		CampingUser targetUser = userMonitor.getUser(words[0]);
		SpellResult spellResult = spellGen.createSpell(null, targetUser);
		outputSpell = spellResult.provideInlineResult();
		String targetFirst;
		if (targetUser != null) {
			targetFirst = targetUser.getFirstname();
		} else {
			targetFirst = CampingUser.UNKNOWN_TARGET;
		}

		int targetId = -1;

		if (targetUser != null) {
			targetId = targetUser.getTelegramId();
		}

		InputTextMessageContent mcSpell = new InputTextMessageContent();
		mcSpell.setDisableWebPagePreview(true);
		String spell = processor.process(outputSpell);
		mcSpell.setMessageText(spell);
		mcSpell.setParseMode(CampingBotEngine.MARKDOWN);

		InlineQueryResultArticle articleSpell = new InlineQueryResultArticle();
		articleSpell.setTitle("spell on " + targetFirst);
		CallbackId fullId = createQueryId(updateId, targetId, spellResult.isSuccess() ? 1 : 0);
		articleSpell.setId(fullId.getId());
		articleSpell.setInputMessageContent(mcSpell);
		return new InlineQueryResult[] { articleSpell };
	}

}
