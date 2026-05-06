package com.maohi.fakeplayer.social;

import com.maohi.fakeplayer.Personality;

import java.util.concurrent.ThreadLocalRandom;

/**
 * V5.23: 多国语言简短语料包。
 *
 * 设计理念:
 *   1. 真实 MC 国际服里 70~75% 玩家说英语,但有少数中/西/德/法玩家偶尔切母语。
 *   2. 不全替换英语 bank — 那会让该假人 100% 输出母语,反而暴露:真实非英语玩家
 *      也常用英语融入服务器。本包只覆盖 4 个最常见短句类别(GREETING/FAREWELL/
 *      LAUGH/SHORT_REPLY),其他场景(TRADE/HELP/INSULT 等长句)仍走英语,贴合真实。
 *   3. 切母语概率 ~25%,且首次问候时偏向母语(20% 概率切),日常吐槽偏向英语。
 *
 * 当前覆盖语种:
 *   en — 英语(基线,实际不走本包,直接 fallback 到 VocabularyBank)
 *   zh — 中文简体
 *   es — 西班牙语
 *   de — 德语
 *   fr — 法语
 *
 * 分布(roll 一次,贴合真实国际服比例):
 *   70% en / 8% zh / 8% es / 8% de / 6% fr
 */
public final class LanguagePack {

	private LanguagePack() {}

	/** V5.23: 母语切换概率 — 8%。
	 *  设计:大家共通语言是英语;非英语假人 92% 时间也用英语融入服务器,
	 *  只在 8% 概率切母语短句(GREETING/FAREWELL/LAUGH/THANKS),贴合"国际服里偶尔冒一句外语"。
	 *  长句吐槽(RAIN/NIGHT/CREEPER 等)一律走英语,避免让其他玩家看不懂。 */
	private static final int NATIVE_LANG_PROB_PERCENT = 8;

	// === 中文(简体)— 8% 配额 ===
	private static final String[] ZH_GREETING = {
		"你好", "嗨", "嗨嗨", "哈喽", "在吗", "有人吗", "早", "晚上好", "诶", "来啦",
		"嘿", "你们好", "大家好", "hi 各位", "新人报道"
	};
	private static final String[] ZH_FAREWELL = {
		"晚安", "拜拜", "再见", "走了", "下了", "睡了", "回头见", "明天见", "撤了", "886"
	};
	private static final String[] ZH_LAUGH = {
		"哈哈", "笑死", "lol", "哈哈哈", "23333", "草", "笑了", "哈哈哈哈"
	};
	private static final String[] ZH_SHORT = {
		"嗯", "好的", "可以", "行", "OK", "知道了", "收到", "等下", "稍等",
		"不会吧", "真的假的", "牛", "厉害", "没事", "没问题", "啊?", "什么?",
		"好惨", "可怜", "加油", "牛批"
	};
	private static final String[] ZH_RAIN = {
		"又下雨", "下雨了", "雨好大", "烦死了下雨", "什么时候停"
	};
	private static final String[] ZH_NIGHT = {
		"天黑了", "好黑", "怪物来了", "睡了睡了", "床呢"
	};

	// === 西班牙语 ===
	private static final String[] ES_GREETING = {
		"hola", "buenas", "que tal", "hey", "saludos", "hola a todos", "que onda",
		"buenos dias", "buenas tardes", "buenas noches", "qué pasa"
	};
	private static final String[] ES_FAREWELL = {
		"adios", "hasta luego", "chao", "nos vemos", "buenas noches",
		"me voy", "cuidate", "hasta mañana"
	};
	private static final String[] ES_LAUGH = {
		"jaja", "jajaja", "jeje", "lol", "jajajaja", "que risa", "xD"
	};
	private static final String[] ES_SHORT = {
		"si", "sii", "no", "vale", "ok", "claro", "exacto", "obvio",
		"genial", "que mal", "rip", "no mames", "en serio?", "que?"
	};
	private static final String[] ES_RAIN = {
		"otra vez lluvia", "que llueve", "ya no quiero lluvia", "lluvia...", "puff"
	};
	private static final String[] ES_NIGHT = {
		"esta oscuro", "ya es de noche", "monstruos!", "necesito cama", "cama por favor"
	};

	// === 德语 ===
	private static final String[] DE_GREETING = {
		"hallo", "moin", "servus", "guten tag", "hi", "hey", "tag",
		"grüß euch", "morgen", "hallöchen", "na"
	};
	private static final String[] DE_FAREWELL = {
		"tschüss", "ciao", "bis später", "gute nacht", "bin weg",
		"bis morgen", "macht's gut", "auf wiedersehen"
	};
	private static final String[] DE_LAUGH = {
		"haha", "hahaha", "xD", "lol", "kek", "lustig"
	};
	private static final String[] DE_SHORT = {
		"ja", "nein", "ok", "passt", "klar", "alles gut", "kein ding",
		"echt?", "wirklich?", "krass", "verdammt", "was?", "wie bitte"
	};
	private static final String[] DE_RAIN = {
		"schon wieder regen", "es regnet", "scheiß regen", "regen nervt", "puh"
	};
	private static final String[] DE_NIGHT = {
		"so dunkel", "monster kommen", "ich brauch ein bett", "nacht schon", "huh dunkel"
	};

	// === 法语 ===
	private static final String[] FR_GREETING = {
		"salut", "bonjour", "bonsoir", "coucou", "yo", "hey", "salut tout le monde",
		"hello", "wesh", "ça va?"
	};
	private static final String[] FR_FAREWELL = {
		"au revoir", "salut", "à plus", "bonne nuit", "je file",
		"à demain", "ciao", "bye"
	};
	private static final String[] FR_LAUGH = {
		"mdr", "lol", "ptdr", "haha", "xD", "trop drôle"
	};
	private static final String[] FR_SHORT = {
		"oui", "non", "ok", "ouais", "bien sûr", "carrément", "peut-être",
		"vraiment?", "ah bon?", "quoi?", "rip", "dommage"
	};
	private static final String[] FR_RAIN = {
		"encore la pluie", "il pleut", "fait chier la pluie", "pluie...", "ça pleut"
	};
	private static final String[] FR_NIGHT = {
		"il fait nuit", "trop sombre", "monstres!", "il me faut un lit", "lit svp"
	};

	/**
	 * 按真实国际服分布抽取一个语种代码。VirtualPlayerManager 在创建假人时调一次。
	 */
	public static String rollLanguage() {
		int r = ThreadLocalRandom.current().nextInt(100);
		if (r < 70) return "en";
		if (r < 78) return "zh";
		if (r < 86) return "es";
		if (r < 94) return "de";
		return "fr";
	}

	/**
	 * 根据假人 language + 当前消息类型,决定是否切母语。
	 * 英语假人永远走英语;非英语假人按 NATIVE_LANG_PROB_PERCENT 概率切母语。
	 */
	public static boolean shouldSpeakNative(Personality p) {
		if (p == null || p.language == null || "en".equals(p.language)) return false;
		return ThreadLocalRandom.current().nextInt(100) < NATIVE_LANG_PROB_PERCENT;
	}

	/**
	 * 取该语种的 GREETING 短句。语种不支持或英语 fallback 时返回 null,调用方应走英语 bank。
	 */
	public static String getGreeting(Personality p) {
		if (p == null) return null;
		String[] bank = greetingBank(p.language);
		if (bank == null) return null;
		return bank[ThreadLocalRandom.current().nextInt(bank.length)];
	}

	public static String getFarewell(Personality p) {
		if (p == null) return null;
		String[] bank = farewellBank(p.language);
		if (bank == null) return null;
		return bank[ThreadLocalRandom.current().nextInt(bank.length)];
	}

	public static String getLaugh(Personality p) {
		if (p == null) return null;
		String[] bank = laughBank(p.language);
		if (bank == null) return null;
		return bank[ThreadLocalRandom.current().nextInt(bank.length)];
	}

	public static String getShortReply(Personality p) {
		if (p == null) return null;
		String[] bank = shortBank(p.language);
		if (bank == null) return null;
		return bank[ThreadLocalRandom.current().nextInt(bank.length)];
	}

	public static String getRain(Personality p) {
		if (p == null) return null;
		String[] bank = rainBank(p.language);
		if (bank == null) return null;
		return bank[ThreadLocalRandom.current().nextInt(bank.length)];
	}

	public static String getNight(Personality p) {
		if (p == null) return null;
		String[] bank = nightBank(p.language);
		if (bank == null) return null;
		return bank[ThreadLocalRandom.current().nextInt(bank.length)];
	}

	private static String[] greetingBank(String lang) {
		switch (lang) {
			case "zh": return ZH_GREETING;
			case "es": return ES_GREETING;
			case "de": return DE_GREETING;
			case "fr": return FR_GREETING;
			default:   return null;
		}
	}

	private static String[] farewellBank(String lang) {
		switch (lang) {
			case "zh": return ZH_FAREWELL;
			case "es": return ES_FAREWELL;
			case "de": return DE_FAREWELL;
			case "fr": return FR_FAREWELL;
			default:   return null;
		}
	}

	private static String[] laughBank(String lang) {
		switch (lang) {
			case "zh": return ZH_LAUGH;
			case "es": return ES_LAUGH;
			case "de": return DE_LAUGH;
			case "fr": return FR_LAUGH;
			default:   return null;
		}
	}

	private static String[] shortBank(String lang) {
		switch (lang) {
			case "zh": return ZH_SHORT;
			case "es": return ES_SHORT;
			case "de": return DE_SHORT;
			case "fr": return FR_SHORT;
			default:   return null;
		}
	}

	private static String[] rainBank(String lang) {
		switch (lang) {
			case "zh": return ZH_RAIN;
			case "es": return ES_RAIN;
			case "de": return DE_RAIN;
			case "fr": return FR_RAIN;
			default:   return null;
		}
	}

	private static String[] nightBank(String lang) {
		switch (lang) {
			case "zh": return ZH_NIGHT;
			case "es": return ES_NIGHT;
			case "de": return DE_NIGHT;
			case "fr": return FR_NIGHT;
			default:   return null;
		}
	}
}
