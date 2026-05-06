package com.maohi.fakeplayer.social;

import com.maohi.MaohiConfig;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 假人词库系统 (V3)
 */
public class VocabularyBank {

	// ==================== 天气与环境 ====================
	// V5.23 扩容: 16 → 50 条;加入语速/拼写/标点变体,降低 10 假人 24h 复读率
	private static final String[] RAIN = {
		"rain again", "i hate rain", "laggy rain", "why is it raining", "raining...",
		"ew rain", "so wet", "rain sucks", "can it stop raining", "brb waiting for rain to stop",
		"great more rain", "rain rain go away", "ofc its raining", "typical",
		"not again", "is it always raining here",
		"wet wet wet", "rip dry clothes", "noo my campfire", "raining on me",
		"why does it always rain here", "ugh wet socks", "any1 got an umbrella lol",
		"my dog hates the rain", "raining cats and dogs", "rain is annoying",
		"this rain tho", "smh rain", "rain delay", "rain check pls",
		"omg the rain", "fml rain", "is this monsoon season", "rain why",
		"weather: bad", "great just great", "perfect timing rain",
		"its pouring", "drizzle drizzle", "downpour incoming", "soaked",
		"my crops are happy at least", "ima wait it out", "rain stops zombies right? wait",
		"need a roof", "tarp anyone", "wishing for sun", "stuck inside",
		"low visibility", "raining sideways", "this weather is wack"
	};
	private static final String[] NIGHT = {
		"so dark", "can we sleep", "too many mobs", "night time", "dark outside",
		"where are the beds", "any beds?", "night already?", "its getting dark",
		"i need a bed", "mobs incoming", "gg night", "not looking forward to this",
		"whos got a bed", "sleep?", "its so dark", "brb hiding",
		"sun where", "wheres the day", "noctophobia activated", "monsters everywhere",
		"too spooky", "i hear zombies", "skellies are gross", "creepers love the dark",
		"need torches", "torch up boys", "lights out", "pitch black",
		"is it morning yet", "skip night pls", "anyone wanna sleep", "form a bed circle",
		"gn? lol jk", "nightmare hours", "spooky time", "dark and stormy",
		"fear mode on", "stick close", "stay together", "watch the sky",
		"sword out", "shield up", "armor check", "night patrol",
		"bandit hour", "phantom fear rising", "where is the moon", "candle anyone",
		"lantern needed", "soul sand torches plz", "lit it up"
	};
	private static final String[] FIRE = {
		"help im burning", "fireeee", "water water", "im on fire wtf", "burning",
		"ow ow ow", "ahhh fire", "someone put me out", "help", "fire!!",
		"hot hot hot", "im dying", "wtf why am i burning", "aaa",
		"no no no", "stop drop and roll lol",
		"FIRE", "ow ow ow ow", "im a torch", "rip me", "save me water",
		"my hp", "going down", "extinguish me pls", "send water bucket",
		"barbecue mode", "well done thanks", "i shouldnt have stood there",
		"i forgot fire damage", "noobish death incoming", "this is bad", "very bad",
		"frantically searching for water", "help help", "anyone got fire res",
		"potion pls", "rip armor", "rip everything", "hot stuff coming through",
		"toasty", "crispy", "extra crispy", "ahh hot",
		"why did i do that", "regret", "lava bad", "fire bad",
		"campfire mistake", "burning alive", "spicy", "ouch hot"
	};

	// ==================== 战斗与威胁 ====================
	private static final String[] CREEPER_FEAR = {
		"wtf creeper", "omg run", "creeper!!", "no no no", "get away",
		"a creeper!", "run run run", "not today", "back off", "ahhh creeper",
		"pls no", "ive had enough", "screw creepers", "nope",
		"creeper behind u", "watch out", "run!!", "oh crap",
		"CREEPER", "hisssss nope", "back back back", "outta here",
		"green nightmare", "creeper o clock", "shoo shoo", "noooo",
		"creeper warning", "CREEPER PLS", "i hate this game", "exploding incoming",
		"creep creep go away", "rip my house", "boom incoming", "duck",
		"my walls", "RIP base", "all my stuff", "pls dont blow up",
		"dont look at me", "stay away creeper", "dont breathe", "DODGE"
	};
	private static final String[] COMBAT_WIN = {
		"ez", "lmao", "get rekt", "gg", "lol", "nice",
		"too easy", "pog", "lets go", "haha", "skill issue",
		"no diff", "done", "next", "thats what u get",
		"sit down", "denied", "L", "L bozo", "go next",
		"ezpz", "absolutely", "demolished", "outclassed", "outplayed",
		"no chance", "scrub", "git gud", "free win", "thanks for the kill"
	};
	private static final String[] COMBAT_LOSE = {
		"rip", "bruh", "lmao i died", "oof", "unlucky",
		"that was close", "gg i guess", "well that sucked",
		"ow", "ill get u next time", "lag", "so unfair",
		"u got me", "good fight", "well played", "ggwp", "respect",
		"i underestimated u", "u win this round", "rematch?", "next time",
		"hax", "lag spike", "my game crashed mid swing", "totally fair", "worth"
	};

	// ==================== 社交场景 ====================
	private static final String[] GREETING = {
		"hi", "hello", "yo", "hey", "supp", "o/",
		"any1?", "whats up", "howdy", "moin", "heya",
		"hola", "sup", "wassup", "ey", "hihi",
		"hai", "henlo", "ello", "good day", "morning", "evenin",
		"yoo", "yooo", "ahoy", "salutations", "greetings",
		"hewwo", "hii", "hewo", "konnichiwa", "bonjour",
		"o7", "wave", "greetings traveler", "hey hey", "yoo whats good"
	};
	private static final String[] FAREWELL = {
		"gn", "bye", "gtg", "off now", "see ya", "cya",
		"night all", "later", "peace out", "im out", "byebye",
		"ttyl", "im going", "cya later", "take care", "night",
		"bbl", "off to bed", "irl beckons", "real life calls",
		"adios", "ciao", "sayonara", "auf wiedersehen", "logging off",
		"im done for today", "see u tomorrow", "have fun yall", "take it easy",
		"stay safe", "fly safe", "stay frosty", "g'night",
		"cu", "cyaa", "byeee", "later gators"
	};
	private static final String[] AFK_MESSAGES = {
		"brb", "afk sec", "one sec", "wait", "phone",
		"afk 1min", "give me a sec", "hold on", "be right back",
		"someone at the door", "pause", "bio", "eating brb",
		"food run", "snack break", "bathroom break", "irl emergency",
		"family stuff", "mom needs me", "dog needs walk", "cat is on me",
		"call coming in", "5min", "10min brb", "lunch time", "dinner time"
	};
	private static final String[] BACK_MESSAGES = {
		"back", "im back", "ok back", "sorry was afk", "re",
		"returned", "here again", "what did i miss", "ok im here",
		"alive again", "yo im back",
		"hi again", "ok im good", "sorry yall", "i return",
		"reporting back", "ready", "lets continue", "and im back",
		"any1 still here", "anyone alive"
	};
	private static final String[] DEATH_REACT = {
		"rip", "f", "unlucky", "oh no", "nooo", "omg",
		"bruh", "wait what?", "why", "ouch", "lmao",
		"skill issue", "RIP", "thats rough", "damn",
		"oof", "F in chat", "press F", "yikes",
		"big rip", "noooo bro", "couldve been u", "rough one",
		"fell off", "took the L", "skill issue lmao", "respawn time"
	};

	// ==================== 日常闲聊 ====================
	private static final String[] IDLE_CHAT = {
		"anyone here?", "so quiet", "what r u guys doing", "im bored",
		"this server is chill", "nice weather", "anyone wanna trade",
		"wheres the nearest village", "found diamonds lol jk",
		"just exploring", "anyone got spare iron", "whats up",
		"this is fun", "anyone wanna build", "bruh this is taking forever",
		"need food", "wheres my base again", "lol",
		"hmm", "ok", "ye", "sure", "nice", "gg", "xd",
		"ngl this is comfy", "vibing", "chillin", "minecraft hits different",
		"is anyone in vc", "discord link?", "any active builders",
		"build comp soon?", "shop opening anyone", "pvp arena where",
		"need a hug", "wholesome chungus", "i miss bedrock players",
		"i love this game", "hate quartz tho", "saw a fox today", "fox is cute",
		"cat purrs at base", "baby villager too op", "trading op", "exp grind",
		"bookshelf where", "library status", "enchant table set up?",
		"netherite hunt soon", "ancient debris vibes", "stronghold idea",
		"end raid when", "elytra plox", "shulker farm dreams",
		"need help with this redstone", "redstone broke", "pulse extender how",
		"piston door tutorial?", "anyone speedrun", "world record vibes"
	};
	private static final String[] FOUND_GOOD = {
		"yoo i found something", "nice!", "lets gooo", "omg nice",
		"pog", "no way", "finally", "worth it", "holy",
		"omg omg", "is this real", "brb screaming", "yesss",
		"lucky", "rng blessed me", "thank u rng", "hidden gem",
		"i was right", "called it", "it was here all along", "i love this game",
		"giga lucky", "best day ever", "main character moment"
	};

	// ==================== 任务关联 ====================
	private static final String[] TASK_MINING = {
		"so much stone", "found coal", "need more iron", "mining...", "my pickaxe is almost broken",
		"where are the diamonds", "this cave is huge", "so many zombies down here", "more cobble",
		"i hate gravel", "lava...", "almost died to lava", "mining level up", "back to the mines",
		"strip mining", "branch mining time", "y -54 vibes", "deep slate hard",
		"need fortune 3", "fortune 2 will do", "ancient debris??",
		"netherite incoming hopefully", "redstone again", "lapis nooo too much",
		"silk touch this", "mob spawner found", "spawner here", "abandoned mineshaft",
		"these tracks lead somewhere", "found a chest", "loot loot",
		"my hunger", "need food down here", "torches torches", "i forgot torches",
		"placeholder for stone", "mountain of cobble", "fence post mining", "ladder up"
	};
	private static final String[] TASK_WOODCUTTING = {
		"chopping trees", "need more wood", "this tree is too tall", "timber", "getting wood",
		"wheres the forest", "need saplings", "apples!", "my axe broke", "so much wood",
		"oak run", "spruce run", "birch?", "dark oak biome plz",
		"jungle wood gang", "acacia ugly tho", "mangrove who", "cherry blossom!",
		"replanting", "saplings everywhere", "leaf decay slow", "shears for leaves",
		"axe enchant pls", "efficiency 4 needed", "log farm idea", "tree farm soon"
	};
	private static final String[] TASK_EXPLORING = {
		"where am i", "this terrain is cool", "im lost", "exploring", "looking for a village",
		"nice view", "anyone want to base together?", "running around", "so far from spawn",
		"found a cool spot", "need food", "sprinting...",
		"this biome is pretty", "ravine alert", "ooh cliff", "swimming back",
		"boat is faster", "wheres my boat", "need elytra", "fireworks pls",
		"map please", "got cartographer?", "trading map for emeralds",
		"compass not pointing right", "lost in the woods", "north or south",
		"new biome unlocked", "cherry grove found", "cherry blossoms!!", "snow biome cold"
	};

	// ==================== V5.23 新增:玩家聊天匹配回复 ====================

	private static final String[] THANKS_REPLY = {
		"np", "no prob", "no worries", "anytime", "u got it",
		"gladly", "welc", "ofc", "yw", "any time my dude",
		"happy to help", "no biggie", "all good", "all gud",
		"de nada", "dw about it", ":)", "n p"
	};
	private static final String[] HELP_REPLY = {
		"on my way", "coming", "where r u", "send coords", "/tpa?",
		"wait i need to find food", "almost there", "hold on", "im low hp tho",
		"send location", "pin it", "hold position", "stay safe i come",
		"need any items", "what u need", "sec",
		"omw", "omw lol", "im out of food sry", "low on arrows",
		"need a sec to gear up"
	};
	private static final String[] QUESTION_REPLY = {
		"idk", "no clue", "i dont know", "good question", "no idea",
		"never seen one", "cant help u there", "i wish i knew",
		"maybe ask wiki", "google?", "wiki it up", "let me check",
		"i think so", "probably?", "not sure tbh", "search me",
		"could be", "maybe yeah", "doubt it", "im no expert"
	};
	private static final String[] TRADE_REPLY = {
		"what u offering", "sure", "what for what", "im listening", "details?",
		"what items", "open to trade", "rate?", "got emeralds",
		"need iron got coal", "wanna swap", "show wares",
		"buying or selling", "got nothing rn", "next time", "maybe later",
		"got diamonds in vault hmm", "let me check inv"
	};
	private static final String[] INSULT_REPLY = {
		"k", "ok bud", "rude", "wow ok", "smh",
		"calm down", "chill", "no need", "block button incoming",
		"reported", "skill issue urself", "u tho", "mirror?",
		"ouch", "ig", "okay buddy", "ratio", "mald"
	};
	private static final String[] LAUGH_REPLY = {
		"lol", "lmao", "haha", "xd", "lmfao",
		"hehe", "kek", "rofl", "im dead", "sent",
		"u funny", "thats good", "lmao for real", "fr fr",
		"absolute cinema", "cant", "stop im wheezing"
	};
	private static final String[] AFFIRM_REPLY = {
		"yeah", "yes", "yep", "yup", "ya",
		"yh", "100%", "for sure", "fr", "exactly",
		"true", "facts", "real", "no cap", "agreed",
		"correct", "this", "^", "+1"
	};
	private static final String[] NEGATE_REPLY = {
		"nah", "no", "nope", "nuh uh", "n",
		"not really", "cant", "wont", "no way",
		"absolutely not", "i disagree", "doubt", "no chance"
	};
	private static final String[] CONFUSED_REPLY = {
		"wat", "what", "huh", "?", "???",
		"come again", "say what", "didnt catch that", "hm?",
		"u wot", "?? lol", "im lost", "sry what"
	};

	// ==================== 工具方法 ====================

	private static String getRandom(String[] bank) {
		return bank[ThreadLocalRandom.current().nextInt(bank.length)];
	}

	/**
	 * V5.23: 带 per-player 近期去重的随机抽词。
	 * 从 bank 里挑一句没在 personality.recentChats 出现过的;最多重试 4 次,
	 * 仍命中重复就直接放行(避免 bank 太小导致死循环)。成功后把选词写入 recentChats
	 * (固定容量 5,FIFO 滚动)。
	 *
	 * @param personality 调用方假人(可空,空则等价于旧 getRandom)
	 * @param bank 候选台词集合
	 */
	private static String pickFresh(com.maohi.fakeplayer.Personality personality, String[] bank) {
		if (personality == null || bank.length <= 1) return getRandom(bank);
		java.util.ArrayDeque<String> recent = personality.recentChats;
		String picked = null;
		for (int attempt = 0; attempt < 4; attempt++) {
			String candidate = bank[ThreadLocalRandom.current().nextInt(bank.length)];
			// recent.contains 是 O(n),但 n<=5 可接受
			synchronized (recent) {
				if (!recent.contains(candidate)) { picked = candidate; break; }
			}
		}
		if (picked == null) picked = getRandom(bank);
		// 入队 + 滚出旧条目
		synchronized (recent) {
			recent.addLast(picked);
			while (recent.size() > 5) recent.pollFirst();
		}
		return picked;
	}

	/** 注入情绪修饰：大小写/口癖/后缀 */
	/** 注入情绪修饰：大小写/口癖/后缀
	 *  V5.23: 检测 CJK / 含非 ASCII 字母字符 → 简化情绪修饰,避免给中文塞 " lmao" 这种穿帮组合
	 */
	public static String addEmotion(String text) {
		boolean hasCJK = false;
		boolean hasAccent = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			// CJK 统一表意 + 假名 + 韩文范围
			if ((c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3040 && c <= 0x30FF) || (c >= 0xAC00 && c <= 0xD7AF)) {
				hasCJK = true; break;
			}
			// 拉丁补充(西/德/法常用变音):à é ü ñ ç 等
			if (c > 0x7F && Character.isLetter(c)) hasAccent = true;
		}
		// 中文/日文/韩文:简化处理,只允许基本变奏与少量原生表情
		if (hasCJK) {
			int style = ThreadLocalRandom.current().nextInt(100);
			if (style < 8) {
				// 中文常见后缀
				String[] zhFillers = {"...", "?", "啊", "呢", "吧", "lol", " 23333"};
				return text + zhFillers[ThreadLocalRandom.current().nextInt(zhFillers.length)];
			}
			return text;
		}
		// 拉丁带变音(西/德/法):保留小写化但避免 .toUpperCase() 破坏变音
		if (hasAccent) {
			int style = ThreadLocalRandom.current().nextInt(100);
			if (style < 25) return text.toLowerCase();
			if (ThreadLocalRandom.current().nextInt(100) < 25) {
				String[] mixFillers = {"...", " :)", " lol", " :P", " xd"};
				return text + mixFillers[ThreadLocalRandom.current().nextInt(mixFillers.length)];
			}
			return text;
		}
		// 英语原路径
		int style = ThreadLocalRandom.current().nextInt(100);
		if (style < 10) return text.toUpperCase() + "!";
		if (style < 25) return text.toLowerCase();

		if (ThreadLocalRandom.current().nextInt(100) < 35) {
			String[] fillers = {" xd", " lmao", " bruh", "...", " :)", " lol", " :P", " <3"};
			return text + fillers[ThreadLocalRandom.current().nextInt(fillers.length)];
		}
		return text;
	}

	// ==================== 公共 API（保持向后兼容） ====================

	public static String getRainComplaint() { return addEmotion(getRandom(RAIN)); }
	public static String getNightComplaint() { return addEmotion(getRandom(NIGHT)); }
	public static String getFireComplaint() { return addEmotion(getRandom(FIRE)); }
	public static String getCreeperFear() { return addEmotion(getRandom(CREEPER_FEAR)); }
	public static String getCombatWin() { return addEmotion(getRandom(COMBAT_WIN)); }
	public static String getCombatLose() { return addEmotion(getRandom(COMBAT_LOSE)); }

	// V5.23 国际化:Personality 重载先 roll 是否切母语,命中走 LanguagePack,否则走英语 + 去重
	public static String getGreeting(com.maohi.fakeplayer.Personality p) {
		if (p != null && LanguagePack.shouldSpeakNative(p)) {
			String native_ = LanguagePack.getGreeting(p);
			if (native_ != null) return addEmotion(native_);
		}
		return addEmotion(pickFresh(p, GREETING));
	}

	public static String getFarewell(com.maohi.fakeplayer.Personality p) {
		if (p != null && LanguagePack.shouldSpeakNative(p)) {
			String native_ = LanguagePack.getFarewell(p);
			if (native_ != null) return addEmotion(native_);
		}
		return addEmotion(pickFresh(p, FAREWELL));
	}

	// V5.23 新增:玩家聊天匹配回复(单参 = 全局随机,Personality 重载 = 近期去重 + 国际化)
	public static String getThanksReply() { return addEmotion(getRandom(THANKS_REPLY)); }
	public static String getThanksReply(com.maohi.fakeplayer.Personality p) {
		// 感谢类是高频短句,母语化命中率高于平均
		if (p != null && LanguagePack.shouldSpeakNative(p)) {
			String native_ = LanguagePack.getShortReply(p);
			if (native_ != null) return addEmotion(native_);
		}
		return addEmotion(pickFresh(p, THANKS_REPLY));
	}
	public static String getHelpReply() { return addEmotion(getRandom(HELP_REPLY)); }
	public static String getHelpReply(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, HELP_REPLY)); }
	public static String getQuestionReply() { return addEmotion(getRandom(QUESTION_REPLY)); }
	public static String getQuestionReply(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, QUESTION_REPLY)); }
	public static String getTradeReply() { return addEmotion(getRandom(TRADE_REPLY)); }
	public static String getTradeReply(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, TRADE_REPLY)); }
	public static String getInsultReply() { return addEmotion(getRandom(INSULT_REPLY)); }
	public static String getInsultReply(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, INSULT_REPLY)); }
	public static String getLaughReply() { return addEmotion(getRandom(LAUGH_REPLY)); }
	public static String getLaughReply(com.maohi.fakeplayer.Personality p) {
		if (p != null && LanguagePack.shouldSpeakNative(p)) {
			String native_ = LanguagePack.getLaugh(p);
			if (native_ != null) return addEmotion(native_);
		}
		return addEmotion(pickFresh(p, LAUGH_REPLY));
	}
	public static String getAffirmReply() { return addEmotion(getRandom(AFFIRM_REPLY)); }
	public static String getAffirmReply(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, AFFIRM_REPLY)); }
	public static String getNegateReply() { return addEmotion(getRandom(NEGATE_REPLY)); }
	public static String getNegateReply(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, NEGATE_REPLY)); }
	public static String getConfusedReply() { return addEmotion(getRandom(CONFUSED_REPLY)); }
	public static String getConfusedReply(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, CONFUSED_REPLY)); }

	// === V5.23: 带 per-player 近期去重的重载 ===
	// 注意:RAIN/NIGHT/FIRE/CREEPER 等长句吐槽**只走英语**,避免国际服里别人看不懂。
	// 母语只在打招呼/道别/笑/短回应这些"身份小标签"上出现(由 getGreeting/getFarewell/getLaughReply 等处理)。
	public static String getRainComplaint(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, RAIN)); }
	public static String getNightComplaint(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, NIGHT)); }
	public static String getFireComplaint(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, FIRE)); }
	public static String getCreeperFear(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, CREEPER_FEAR)); }
	public static String getCombatWin(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, COMBAT_WIN)); }
	public static String getCombatLose(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, COMBAT_LOSE)); }
	public static String getDeathReaction(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, DEATH_REACT)); }
	public static String getIdleChatFresh(com.maohi.fakeplayer.Personality p) { return addEmotion(pickFresh(p, IDLE_CHAT)); }
	public static String getGreeting() { return addEmotion(getRandom(GREETING)); }
	public static String getGreeting(String targetName) { 
		String msg = getRandom(GREETING);
		if (ThreadLocalRandom.current().nextBoolean()) return addEmotion(msg + " " + targetName);
		return addEmotion(msg);
	}
	public static String getFarewell() { return addEmotion(getRandom(FAREWELL)); }
	public static String getAFKMessage() { return addEmotion(getRandom(AFK_MESSAGES)); }
	public static String getBackMessage() { return addEmotion(getRandom(BACK_MESSAGES)); }
	public static String getDeathReaction() { return addEmotion(getRandom(DEATH_REACT)); }
	public static String getDeathReaction(String targetName) {
		String msg = getRandom(DEATH_REACT);
		if (ThreadLocalRandom.current().nextBoolean()) return addEmotion(msg + " " + targetName);
		return addEmotion(msg);
	}
	public static String getIdleChat() { return addEmotion(getRandom(IDLE_CHAT)); }
	public static String getFoundGood() { return addEmotion(getRandom(FOUND_GOOD)); }

	/**
	 * 从 MaohiConfig 已有的词库中随机选择（闲聊/打招呼/死亡反应）
	 * 优先使用 Config 词库，如果为空则 fallback 到内置词库
	 */
	public static String getConfigChat() {
		String[] msgs = MaohiConfig.getInstance().chatMessages;
		if (msgs != null && msgs.length > 0) {
			return addEmotion(msgs[ThreadLocalRandom.current().nextInt(msgs.length)]);
		}
		return getIdleChat();
	}
	public static String getConfigGreeting() {
		String[] msgs = MaohiConfig.getInstance().greetingReplies;
		if (msgs != null && msgs.length > 0) {
			return addEmotion(msgs[ThreadLocalRandom.current().nextInt(msgs.length)]);
		}
		return getGreeting();
	}
	public static String getConfigDeathReaction() {
		String[] msgs = MaohiConfig.getInstance().deathReactions;
		if (msgs != null && msgs.length > 0) {
			return addEmotion(msgs[ThreadLocalRandom.current().nextInt(msgs.length)]);
		}
		return getDeathReaction();
	}

	/**
	 * ★ P0-2 任务关联型聊天
	 * 50% 概率根据假人当前的任务说相关的话，50% 概率说通用闲聊
	 */
	public static String getChatByTask(com.maohi.fakeplayer.TaskType task) {
		if (task == null) return getConfigChat();

		if (ThreadLocalRandom.current().nextBoolean()) {
			switch (task) {
				case MINING: return addEmotion(getRandom(TASK_MINING));
				case WOODCUTTING: return addEmotion(getRandom(TASK_WOODCUTTING));
				case EXPLORING: return addEmotion(getRandom(TASK_EXPLORING));
				default: return getConfigChat();
			}
		}
		return getConfigChat();
	}

	/**
	 * V5.23: 带 per-player 去重的任务聊天。
	 * 推荐新代码用这个,旧代码沿用上面的无 Personality 版本(行为不变)。
	 */
	public static String getChatByTask(com.maohi.fakeplayer.Personality p, com.maohi.fakeplayer.TaskType task) {
		if (p == null) return getChatByTask(task);
		if (task == null) return getConfigChat();
		if (ThreadLocalRandom.current().nextBoolean()) {
			switch (task) {
				case MINING: return addEmotion(pickFresh(p, TASK_MINING));
				case WOODCUTTING: return addEmotion(pickFresh(p, TASK_WOODCUTTING));
				case EXPLORING: return addEmotion(pickFresh(p, TASK_EXPLORING));
				default: return getConfigChat();
			}
		}
		return getConfigChat();
	}
}
