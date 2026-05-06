package com.maohi.fakeplayer.social;

import com.maohi.fakeplayer.Personality;

import java.util.regex.Pattern;

/**
 * V5.23: 玩家聊天意图分类器 + 语义匹配回复。
 *
 * 旧 SocialEngine.onChatMessage 只用一条正则 .*(hi|hello|yo|hey).* 抓打招呼,
 * 其他玩家发言("thanks"/"help"/"diamond?"/"want to trade"...)假人一律不响应,
 * 真人画像极易识破。
 *
 * 新版按 8 种意图分类(顺序敏感:从最具体到最泛化):
 *   GREETING / THANKS / INSULT / HELP / TRADE / QUESTION / LAUGH / AFFIRM_OR_NEGATE
 * 每种意图对应 VocabularyBank 一组 reply bank,自带 per-player 近期去重。
 *
 * 设计原则:
 *   1. 不是 NLP — 用 word-boundary 正则做关键词匹配,O(N) 命中即返回。
 *   2. 不响应假人对假人(避免回声室),只响应真玩家发言(SocialEngine 调用前已过滤)。
 *   3. 命中后仍按调用方原有冷却节流(NEARBY_GREET_COOLDOWN)。
 *   4. 漏匹配回退 null,SocialEngine 选择不回复 — 比硬塞一句通用 GREETING 更像真人
 *      (真人也不会每条聊天都回)。
 */
public final class ChatResponder {

	private ChatResponder() {}

	/** 意图分类(顺序即优先级:GREETING 优先于 QUESTION,避免 "hi any1?" 走问句路径) */
	public enum Intent {
		GREETING, THANKS, INSULT, HELP, TRADE, LAUGH, QUESTION, AFFIRM, NEGATE, NONE
	}

	// === word-boundary 正则,避免 "this" 匹配到 "hi" ===
	private static final Pattern P_GREETING = Pattern.compile(
		"\\b(hi|hii+|hey|heya|yo+|hello|hewwo|sup|supp|wassup|whats?\\s*up|howdy|moin|hola|ahoy|o7|henlo)\\b");
	private static final Pattern P_THANKS = Pattern.compile(
		"\\b(thanks?|thx|thnx|tysm|tyvm|ty|gracias|merci|cheers|appreciate)\\b");
	private static final Pattern P_INSULT = Pattern.compile(
		"\\b(noob|trash|garbage|loser|bad|sucks?|dumb|stupid|idiot|skill\\s*issue|lol\\s*u\\s*bad|rekt\\s*u)\\b");
	private static final Pattern P_HELP = Pattern.compile(
		"\\b(help|sos|save\\s*me|need\\s*help|pls\\s*help|come\\s*here|come\\s*help|emergency|low\\s*hp|dying)\\b");
	private static final Pattern P_TRADE = Pattern.compile(
		"\\b(trade|trading|sell|selling|buy|buying|wts|wtb|wtt|swap|exchange)\\b");
	private static final Pattern P_LAUGH = Pattern.compile(
		"\\b(lol+|lmao+|lmfao+|rofl|kek|x+d+|haha+|hehe+)\\b");
	private static final Pattern P_QUESTION = Pattern.compile(
		"\\b(where|when|what|why|how|who|anyone|any1|does\\s*any|got\\s*any)\\b|\\?+");
	private static final Pattern P_AFFIRM = Pattern.compile(
		"\\b(yes|yeah|yep|yup|ya|yh|true|correct|agreed?|fr|facts|right|exactly)\\b");
	private static final Pattern P_NEGATE = Pattern.compile(
		"\\b(no|nope|nah|not\\s*really|cant|wont|never|disagree)\\b");

	/**
	 * 给定玩家原话,返回最先命中的意图。
	 * 输入会被 toLowerCase 后剥离纯标点。
	 */
	public static Intent classify(String content) {
		if (content == null || content.isEmpty()) return Intent.NONE;
		String s = content.toLowerCase();
		// 顺序敏感:寒暄/感谢/挑衅 优先于 求助/疑问 — 真人也是先听问候
		if (P_GREETING.matcher(s).find()) return Intent.GREETING;
		if (P_THANKS.matcher(s).find()) return Intent.THANKS;
		if (P_INSULT.matcher(s).find()) return Intent.INSULT;
		if (P_HELP.matcher(s).find()) return Intent.HELP;
		if (P_TRADE.matcher(s).find()) return Intent.TRADE;
		if (P_LAUGH.matcher(s).find()) return Intent.LAUGH;
		if (P_QUESTION.matcher(s).find()) return Intent.QUESTION;
		if (P_AFFIRM.matcher(s).find()) return Intent.AFFIRM;
		if (P_NEGATE.matcher(s).find()) return Intent.NEGATE;
		return Intent.NONE;
	}

	/**
	 * 根据意图生成回复;走 VocabularyBank 的 Personality 重载,自带 per-player 5 条去重。
	 *
	 * @param intent     classify() 的结果
	 * @param senderName 玩家名,用于 GREETING 偶尔带名(认识则更亲切)
	 * @param p          回复者 Personality(去重)
	 * @return 回复字符串;意图 NONE 或不应回复时返回 null
	 */
	public static String respond(Intent intent, String senderName, Personality p) {
		if (intent == null || intent == Intent.NONE) return null;
		switch (intent) {
			case GREETING:
				// GREETING 优先走带名版本(认识此人时),否则普通版
				if (senderName != null && p != null && p.knownRealPlayers.contains(senderName)
					&& java.util.concurrent.ThreadLocalRandom.current().nextBoolean()) {
					return VocabularyBank.getGreeting(senderName);
				}
				return VocabularyBank.getGreeting();
			case THANKS:   return VocabularyBank.getThanksReply(p);
			case INSULT:   return VocabularyBank.getInsultReply(p);
			case HELP:     return VocabularyBank.getHelpReply(p);
			case TRADE:    return VocabularyBank.getTradeReply(p);
			case LAUGH:    return VocabularyBank.getLaughReply(p);
			case QUESTION: return VocabularyBank.getQuestionReply(p);
			case AFFIRM:   return VocabularyBank.getAffirmReply(p);
			case NEGATE:   return VocabularyBank.getNegateReply(p);
			default:       return null;
		}
	}

	/**
	 * V5.23: 真人也不会每条都回。返回 true 表示这次该假人愿意接话。
	 * 不同意图回复概率不同:GREETING/QUESTION 较高,LAUGH/AFFIRM 较低。
	 */
	public static boolean shouldEngage(Intent intent) {
		if (intent == null) return false;
		java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
		switch (intent) {
			case GREETING: return rng.nextInt(100) < 70; // 寒暄基本要回
			case THANKS:   return rng.nextInt(100) < 55;
			case HELP:     return rng.nextInt(100) < 65; // 求助大概率响应
			case QUESTION: return rng.nextInt(100) < 50;
			case TRADE:    return rng.nextInt(100) < 45;
			case INSULT:   return rng.nextInt(100) < 35; // 真人也常常无视
			case LAUGH:    return rng.nextInt(100) < 30; // 不是每个 lol 都跟
			case AFFIRM:
			case NEGATE:   return rng.nextInt(100) < 25;
			default:       return false;
		}
	}
}
