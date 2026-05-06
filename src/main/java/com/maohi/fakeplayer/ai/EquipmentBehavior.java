package com.maohi.fakeplayer.ai;

import com.maohi.fakeplayer.TaskType;
import com.maohi.fakeplayer.network.PacketHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 装备 / 工具切换行为
 * 从原 SurvivalMechanics 拆分(V5.20)
 */
public final class EquipmentBehavior {

	private EquipmentBehavior() {} // 工具类

	/**
	 * V3.1: 根据当前任务自动切换工具
	 * V3.3: 走真实发包切换槽位
	 */
	public static void autoSwitchTool(ServerPlayerEntity player, TaskType currentTask) {
		if (ThreadLocalRandom.current().nextInt(20) != 0) return;

		PlayerInventory inv = player.getInventory();
		switch (currentTask) {
			case WOODCUTTING:
				for (int i = 0; i < 9; i++) {
					if (inv.getStack(i).isOf(Items.WOODEN_AXE) || inv.getStack(i).isOf(Items.STONE_AXE)
						|| inv.getStack(i).isOf(Items.IRON_AXE) || inv.getStack(i).isOf(Items.DIAMOND_AXE)) {
						// ★ V3.3: 发真实切槽包
						PacketHelper.setSelectedSlot(player, i);
						return;
					}
				}
				break;
			case MINING:
				for (int i = 0; i < 9; i++) {
					if (inv.getStack(i).isOf(Items.WOODEN_PICKAXE) || inv.getStack(i).isOf(Items.STONE_PICKAXE)
						|| inv.getStack(i).isOf(Items.IRON_PICKAXE) || inv.getStack(i).isOf(Items.DIAMOND_PICKAXE)) {
						// ★ V3.3: 发真实切槽包
						PacketHelper.setSelectedSlot(player, i);
						return;
					}
				}
				break;
			default:
				break;
		}
	}

	/** 自动装备背包中防御值更高的护甲 */
	public static void autoEquipArmor(ServerPlayerEntity player) {
		// V4.1 限制触发频率，避免每个 tick 都扫描背包
		if (ThreadLocalRandom.current().nextInt(100) > 5) return;

		PlayerInventory inv = player.getInventory();
		// V5.23 修复:原实现用 inv.setStack(armorSlotIndex, ...) 直接写槽位 36~39,
		// 不会触发 EquipmentTracker 的 dirty 标记 → 客户端/其他玩家看不到护甲变化,
		// 反作弊也读不到 ARMOR 属性更新。改用 equipStack(EquipmentSlot, ItemStack),
		// 走完整原版装备链路(发 EntityEquipmentUpdateS2CPacket)。
		net.minecraft.entity.EquipmentSlot[] slots = {
			net.minecraft.entity.EquipmentSlot.FEET,
			net.minecraft.entity.EquipmentSlot.LEGS,
			net.minecraft.entity.EquipmentSlot.CHEST,
			net.minecraft.entity.EquipmentSlot.HEAD
		};
		for (net.minecraft.entity.EquipmentSlot slot : slots) {
			ItemStack equipped = player.getEquippedStack(slot);
			int equippedDef = getArmorDefense(equipped);
			for (int i = 0; i < 36; i++) {
				ItemStack candidate = inv.getStack(i);
				if (candidate.isEmpty() || !isArmorForEquipmentSlot(candidate, slot)) continue;
				if (getArmorDefense(candidate) > equippedDef) {
					// 走原版 equipStack:更新 EquipmentTracker + 广播 EntityEquipmentUpdateS2CPacket
					player.equipStack(slot, candidate.copy());
					inv.setStack(i, equipped); // 旧护甲回到原槽位(空也无所谓)
					equipped = player.getEquippedStack(slot);
					equippedDef = getArmorDefense(equipped);
				}
			}
		}
	}

	private static int getArmorDefense(ItemStack stack) {
		if (stack.isEmpty()) return 0;
		var def = stack.getComponents().get(net.minecraft.component.DataComponentTypes.ATTRIBUTE_MODIFIERS);
		if (def == null) return 0;
		int total = 0;
		for (var entry : def.modifiers()) {
			if (entry.attribute().value() == net.minecraft.entity.attribute.EntityAttributes.ARMOR) {
				total += (int) entry.modifier().value();
			}
		}
		return total;
	}

	private static boolean isArmorForEquipmentSlot(ItemStack stack, net.minecraft.entity.EquipmentSlot slot) {
		if (stack.isEmpty()) return false;
		// 优先用 EQUIPPABLE 组件判断(1.21.1 官方机制),失败回退到名称后缀
		var eq = stack.get(net.minecraft.component.DataComponentTypes.EQUIPPABLE);
		if (eq != null) return eq.slot() == slot;
		String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
		return switch (slot) {
			case FEET -> id.endsWith("_boots");
			case LEGS -> id.endsWith("_leggings");
			case CHEST -> id.endsWith("_chestplate");
			case HEAD -> id.endsWith("_helmet");
			default -> false;
		};
	}
}
