package com.maohi.fakeplayer.social;

import com.maohi.fakeplayer.network.FakeClientConnection;
import com.maohi.fakeplayer.TimingConstants;
import com.maohi.fakeplayer.VirtualPlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 假人社交引擎 (V5.12 终极 RootCause 修复版)
 * 1. 彻底弃用异步队列，防止服务器卡顿时任务积压导致“并喷重复”。
 * 2. 引入 [V12] 唯一识别标签，用于排查是否是旧代码在作祟。
 * 3. 强制同步加锁，一人一票制。
 */
public class SocialEngine {
    private final VirtualPlayerManager manager;
    private final ReentrantLock chatLock = new ReentrantLock();
    private static final org.slf4j.Logger CHAT_LOGGER = org.slf4j.LoggerFactory.getLogger("MaohiChat");
    
    private long nextAvailableChatTime = 0;

    public SocialEngine(VirtualPlayerManager manager) {
        this.manager = manager;
    }

    public void onChatMessage(ServerPlayerEntity sender, String content) {
        // 过滤假人消息
        if (sender.networkHandler.connection instanceof FakeClientConnection || manager.isVirtualPlayer(sender.getUuid())) return;
        
        if (content.toLowerCase().matches(".*(hi|hello|yo|hey).*")) {
            long now = System.currentTimeMillis();
            if (now < nextAvailableChatTime) return;

            for (UUID id : manager.getOnlinePlayerUuids()) {
                ServerPlayerEntity p = manager.getServer().getPlayerManager().getPlayer(id);
                VirtualPlayerManager.Personality personality = manager.getPersonality(id);
                
                if (p != null && personality != null && !personality.farewellSaid && p.squaredDistanceTo(sender) < 225 
                    && now - personality.lastCommandTime > TimingConstants.NEARBY_GREET_COOLDOWN) {
                    
                    String resp = VocabularyBank.getGreeting(sender.getName().getString());
                    // 立即同步发送，杜绝积压
                    sendImmediateChat(id, resp);
                    personality.lastCommandTime = now;
                    nextAvailableChatTime = now + 15000L; // 全局冷却 15 秒
                    break;
                }
            }
        }
    }

    public void onPlayerDeathNearby(ServerPlayerEntity victim) {
        long now = System.currentTimeMillis();
        if (now < nextAvailableChatTime) return;

        for (UUID id : manager.getOnlinePlayerUuids()) {
            ServerPlayerEntity p = manager.getServer().getPlayerManager().getPlayer(id);
            VirtualPlayerManager.Personality personality = manager.getPersonality(id);
            
            if (p != null && p.squaredDistanceTo(victim) < 100 && ThreadLocalRandom.current().nextInt(100) < 30) {
                if (personality != null && !personality.farewellSaid && now - personality.lastCommandTime > TimingConstants.FAREWELL_LOCK_DURATION) {
                    String reaction = VocabularyBank.getDeathReaction(victim.getName().getString());
                    sendImmediateChat(id, reaction);
                    personality.lastCommandTime = now;
                    nextAvailableChatTime = now + 10000L;
                    break;
                }
            }
        }
    }

    public void onVictimDeath(UUID victim) {
        if (manager.isLoggingOut(victim)) return;
        if (ThreadLocalRandom.current().nextInt(100) < 70) {
            sendImmediateChat(victim, VocabularyBank.getCombatLose());
        }
    }

    /**
     * 核心发送出口：强制同步、强制打标、强制名字前缀
     */
    public void sendImmediateChat(UUID uuid, String message) {
        chatLock.lock();
        try {
            if (message == null || message.trim().isEmpty()) return;

            // RootCause 2 修复：加固名字回退
            String name = manager.getVirtualPlayerName(uuid);
            if (name == null || name.isEmpty() || name.isBlank()) {
                ServerPlayerEntity p = manager.getServer().getPlayerManager().getPlayer(uuid);
                if (p != null) name = p.getName().getString();
            }
            if (name == null || name.isEmpty()) {
                name = "Player_" + uuid.toString().substring(0, 4);
            }

            // [V12] 是自证清白的标签，如果日志里没这个，说明是旧 JAR 包在发消息！
            String formatted = "[V12] <" + name + "> " + message.trim();
            
            // 确保在主线程广播
            manager.getServer().execute(() -> {
                manager.getServer().getPlayerManager().broadcast(Text.literal(formatted), false);
                CHAT_LOGGER.info(formatted);
            });
        } finally {
            chatLock.unlock();
        }
    }

    /**
     * 保持 tick 方法为空，防止与主循环逻辑冲突
     */
    public void tick(long nowMs) {}

    public boolean isGlobalChatAvailable() {
        return System.currentTimeMillis() >= nextAvailableChatTime;
    }
}
