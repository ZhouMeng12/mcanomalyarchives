package net.mcreator.mcanomalyarchives.mixin;

import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * 修复 MCreator 重复注册 cornPoppySad 游戏规则的崩溃。
 *
 * MCreator 生成的 McanomalyarchivesModGameRules 每次构建都会被还原，
 * 且 NeoForge 的并行事件分发会导致 FMLCommonSetupEvent 被多次触发，
 * 使 GameRules.register() 因重复键抛出 IllegalStateException。
 *
 * 此 Mixin 拦截重复异常，静默返回已创建的 Key。
 */
@Mixin(GameRules.class)
public class GameRulesMixin {

	@Inject(
		method = "register(Ljava/lang/String;Lnet/minecraft/world/level/GameRules$Category;Lnet/minecraft/world/level/GameRules$Type;)Lnet/minecraft/world/level/GameRules$Key;",
		at = @At(
			value = "INVOKE",
			target = "Ljava/lang/IllegalStateException;<init>(Ljava/lang/String;)V"
		),
		cancellable = true,
		locals = LocalCapture.CAPTURE_FAILHARD
	)
	private static <T extends GameRules.Value<T>> void onDuplicateRegistration(
			String name, GameRules.Category category, GameRules.Type<T> type,
			CallbackInfoReturnable<GameRules.Key<T>> cir, GameRules.Key<T> key) {
		// 静默返回已创建的 key，而不是抛出 Duplicate game rule 异常
		cir.setReturnValue(key);
	}
}
