// Save this class in your mod and generate all required imports

/**
 * Made with Blockbench 5.1.4 Exported for Minecraft version 1.19 or later with
 * Mojang mappings
 * 
 * @author Author
 */
public class quAnimation {
	public static final AnimationDefinition qumove = AnimationDefinition.Builder.withLength(0.1583F).looping()
			.addAnimation("head",
					new AnimationChannel(AnimationChannel.Targets.ROTATION,
							new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.0167F, KeyframeAnimations.degreeVec(0.0F, 7.5F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.05F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.075F, KeyframeAnimations.degreeVec(0.0F, -7.5F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.1083F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),
									AnimationChannel.Interpolations.LINEAR)))
			.addAnimation("body1",
					new AnimationChannel(AnimationChannel.Targets.ROTATION,
							new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.0333F, KeyframeAnimations.degreeVec(0.0F, 7.5F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.0667F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.0917F, KeyframeAnimations.degreeVec(0.0F, -7.5F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.125F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),
									AnimationChannel.Interpolations.LINEAR)))
			.addAnimation("body2",
					new AnimationChannel(AnimationChannel.Targets.ROTATION,
							new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.05F, KeyframeAnimations.degreeVec(0.0F, 7.5F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.075F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.1083F, KeyframeAnimations.degreeVec(0.0F, -7.5F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.1417F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),
									AnimationChannel.Interpolations.LINEAR)))
			.addAnimation("tail",
					new AnimationChannel(AnimationChannel.Targets.ROTATION,
							new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.0667F, KeyframeAnimations.degreeVec(0.0F, 7.5F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.0917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.125F, KeyframeAnimations.degreeVec(0.0F, -7.5F, 0.0F),
									AnimationChannel.Interpolations.LINEAR),
							new Keyframe(0.1583F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),
									AnimationChannel.Interpolations.LINEAR)))
			.build();
}