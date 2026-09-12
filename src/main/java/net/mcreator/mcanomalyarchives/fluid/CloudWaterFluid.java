package net.mcreator.mcanomalyarchives.fluid;

import net.neoforged.neoforge.fluids.BaseFlowingFluid;

import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;

import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModItems;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModFluids;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModFluidTypes;
import net.mcreator.mcanomalyarchives.init.McanomalyarchivesModBlocks;

public abstract class CloudWaterFluid extends BaseFlowingFluid {
	public static final BaseFlowingFluid.Properties PROPERTIES = new BaseFlowingFluid.Properties(() -> McanomalyarchivesModFluidTypes.CLOUD_WATER_TYPE.get(), () -> McanomalyarchivesModFluids.CLOUD_WATER.get(),
			() -> McanomalyarchivesModFluids.FLOWING_CLOUD_WATER.get()).explosionResistance(100f).bucket(() -> McanomalyarchivesModItems.CLOUD_WATER_BUCKET.get()).block(() -> (LiquidBlock) McanomalyarchivesModBlocks.CLOUD_WATER.get());

	private CloudWaterFluid() {
		super(PROPERTIES);
	}

	@Override
	public ParticleOptions getDripParticle() {
		return ParticleTypes.DRIPPING_WATER;
	}

	public static class Source extends CloudWaterFluid {
		public int getAmount(FluidState state) {
			return 8;
		}

		public boolean isSource(FluidState state) {
			return true;
		}
	}

	public static class Flowing extends CloudWaterFluid {
		protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
			super.createFluidStateDefinition(builder);
			builder.add(LEVEL);
		}

		public int getAmount(FluidState state) {
			return state.getValue(LEVEL);
		}

		public boolean isSource(FluidState state) {
			return false;
		}
	}
}