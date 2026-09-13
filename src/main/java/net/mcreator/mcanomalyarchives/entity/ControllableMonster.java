package net.mcreator.mcanomalyarchives.entity;

import net.mcreator.mcanomalyarchives.dialogue.DialogueDatabase;
import net.mcreator.mcanomalyarchives.dialogue.DialogueLine;
import net.mcreator.mcanomalyarchives.dialogue.DialogueManager;
import net.mcreator.mcanomalyarchives.effect.GiftEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class ControllableMonster extends Monster {

	public static final int STATE_STAY = 0;
	public static final int STATE_WANDER = 1;
	public static final int STATE_FOLLOW = 2;

	private static final EntityDataAccessor<Integer> MOB_STATE =
			SynchedEntityData.defineId(ControllableMonster.class, EntityDataSerializers.INT);

	// 子类重写此方法提供自己的礼物映射
	protected Map<Item, GiftEffect> getGiftEffects() {
		return Collections.emptyMap();
	}

	@Nullable
	private UUID followTargetUUID;

	protected ControllableMonster(EntityType<? extends Monster> type, Level world) {
		super(type, world);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MOB_STATE, STATE_WANDER);
	}

	@Override
	public void checkDespawn() {
		if (this.level().getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		// 被玩家设置为跟随的可控怪永不自动消失
		if (getFollowTargetUUID() != null) {
			return;
		}
		super.checkDespawn();
	}

	public int getMobState() {
		return this.entityData.get(MOB_STATE);
	}

	public void setMobState(int state) {
		this.entityData.set(MOB_STATE, state);
	}

	@Nullable
	public UUID getFollowTargetUUID() {
		return followTargetUUID;
	}

	public void setFollowTargetUUID(@Nullable UUID uuid) {
		this.followTargetUUID = uuid;
	}

	protected boolean canInteract() {
		return true;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.level().isClientSide() || hand != InteractionHand.MAIN_HAND) {
			return super.mobInteract(player, hand);
		}

		if (!player.isShiftKeyDown()) {
			// 非Shift：手持物品 → 赠送礼物；空手 → 打招呼对话
			ItemStack heldItem = player.getItemInHand(hand);
			if (!heldItem.isEmpty()) {
				// 斯万 + 手里拿着书 → 见闻录剧情（拿书换《见闻录》，见 codex/CodexOriginStory）
				if (this instanceof net.mcreator.mcanomalyarchives.entity.SvanEntity
						&& player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
						&& net.mcreator.mcanomalyarchives.codex.CodexOriginStory.isBlankBook(heldItem)) {
					if (net.mcreator.mcanomalyarchives.codex.CodexOriginStory.tryStart(serverPlayer, this)) {
						return InteractionResult.SUCCESS;
					}
				}
				return tryGiveGift(player, heldItem);
			}
			if (canInteract()) {
				List<DialogueLine> greetings = DialogueDatabase.greetingsFor(this);
				if (!greetings.isEmpty()) {
					DialogueLine line = greetings.get(this.getRandom().nextInt(greetings.size()));
					DialogueManager.speak((ServerLevel) this.level(), this, line);
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		}

		if (!canInteract()) {
			return InteractionResult.PASS;
		}

		this.setFollowTargetUUID(player.getUUID());

		int current = getMobState();
		int next = (current + 1) % 3;
		setMobState(next);

		String msg = switch (next) {
			case STATE_STAY -> "§e◉ §f原地停留";
			case STATE_WANDER -> "§a↻ §f四处闲逛";
			case STATE_FOLLOW -> "§b→ §f跟随玩家";
			default -> "§f未知状态";
		};
		player.displayClientMessage(Component.literal(msg), true);
		this.level().playSound(null, this.blockPosition(), SoundEvents.VILLAGER_YES, this.getSoundSource(), 1.0f, 1.0f);

		return InteractionResult.SUCCESS;
	}

	/**
	 * 处理赠送物品逻辑：消耗物品、查找并触发效果、播放粒子。
	 */
	private InteractionResult tryGiveGift(Player player, ItemStack heldItem) {
		Item giftItem = heldItem.getItem();
		Map<Item, GiftEffect> effects = getGiftEffects();
		GiftEffect effect = effects.get(giftItem);

		// 没有注册效果的物品不触发
		if (effect == null) {
			return InteractionResult.PASS;
		}

		// 消耗物品（创造模式不消耗）
		if (!player.getAbilities().instabuild) {
			heldItem.shrink(1);
		}

		// 触发效果
		effect.apply(player, this, heldItem);

		// 送礼气泡（台词集中在 DialogueDatabase）
		List<DialogueLine> giftLines = DialogueDatabase.giftLinesFor(this, giftItem);
		if (!giftLines.isEmpty()) {
			DialogueLine line = giftLines.get(this.getRandom().nextInt(giftLines.size()));
			DialogueManager.speak((ServerLevel) this.level(), this, line);
		}

		// 视觉反馈：爱心粒子
		if (this.level() instanceof ServerLevel serverLevel) {
			for (int i = 0; i < 8; i++) {
				double x = this.getX() + (this.getRandom().nextDouble() - 0.5) * 0.8;
				double y = this.getEyeY() + 0.3 + this.getRandom().nextDouble() * 0.6;
				double z = this.getZ() + (this.getRandom().nextDouble() - 0.5) * 0.8;
				serverLevel.sendParticles(ParticleTypes.HEART, x, y, z, 1, 0, 0.15, 0, 0.05);
			}
		}
		this.level().playSound(null, this.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
			this.getSoundSource(), 0.5f, 1.5f);

		return InteractionResult.SUCCESS;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("MobState", this.getMobState());
		if (followTargetUUID != null) {
			tag.putLong("FollowTargetMost", followTargetUUID.getMostSignificantBits());
			tag.putLong("FollowTargetLeast", followTargetUUID.getLeastSignificantBits());
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.setMobState(tag.contains("MobState") ? tag.getInt("MobState") : STATE_WANDER);
		if (tag.contains("FollowTargetMost") && tag.contains("FollowTargetLeast")) {
			this.followTargetUUID = new UUID(tag.getLong("FollowTargetMost"), tag.getLong("FollowTargetLeast"));
		} else {
			this.followTargetUUID = null;
		}
	}
}
