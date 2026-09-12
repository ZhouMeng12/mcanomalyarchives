package net.mcreator.mcanomalyarchives.potion;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;

public class AddicteMobEffect extends MobEffect {
	public AddicteMobEffect() {
		super(MobEffectCategory.HARMFUL, -65536);
	}

	@Override
	public ParticleOptions createParticleOptions(MobEffectInstance mobEffectInstance) {
		return ParticleTypes.HEART;
	}
}