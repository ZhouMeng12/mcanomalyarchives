package net.mcreator.mcanomalyarchives.potion;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;

public class SadMobEffect extends MobEffect {
	public SadMobEffect() {
		super(MobEffectCategory.HARMFUL, -16744193);
	}

	@Override
	public ParticleOptions createParticleOptions(MobEffectInstance mobEffectInstance) {
		return ParticleTypes.DRIPPING_WATER;
	}
}