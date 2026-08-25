// Made with Blockbench 5.1.6
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

public class Modelpurplephasefour<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			new ResourceLocation("modid", "purplephasefour"), "main");
	private final ModelPart Waist;
	private final ModelPart Head;
	private final ModelPart Body;
	private final ModelPart Right_Arm;
	private final ModelPart Left_Arm;
	private final ModelPart Right_Leg;
	private final ModelPart Left_Leg;
	private final ModelPart all;
	private final ModelPart part1;
	private final ModelPart part2;
	private final ModelPart part3;
	private final ModelPart part4;

	public Modelpurplephasefour(ModelPart root) {
		this.Waist = root.getChild("Waist");
		this.Head = this.Waist.getChild("Head");
		this.Body = this.Waist.getChild("Body");
		this.Right_Arm = this.Waist.getChild("Right_Arm");
		this.Left_Arm = this.Waist.getChild("Left_Arm");
		this.Right_Leg = root.getChild("Right_Leg");
		this.Left_Leg = root.getChild("Left_Leg");
		this.all = root.getChild("all");
		this.part1 = this.all.getChild("part1");
		this.part2 = this.part1.getChild("part2");
		this.part3 = this.part2.getChild("part3");
		this.part4 = this.part3.getChild("part4");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition Waist = partdefinition.addOrReplaceChild("Waist", CubeListBuilder.create(),
				PartPose.offset(0.0F, 12.0F, 0.0F));

		PartDefinition Head = Waist.addOrReplaceChild("Head",
				CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-4.0F, -23.0F, 0.0F, 0.0F, 0.0F, -1.4835F));

		PartDefinition Body = Waist.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F,
				-11.0F, -2.0F, 8.0F, 23.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 0.0F));

		PartDefinition Right_Arm = Waist.addOrReplaceChild("Right_Arm",
				CubeListBuilder.create().texOffs(43, 18).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 24.0F, 2.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-5.0F, -22.0F, 0.0F, -0.6109F, 0.0F, 0.0F));

		PartDefinition Left_Arm = Waist.addOrReplaceChild("Left_Arm", CubeListBuilder.create(),
				PartPose.offsetAndRotation(5.0F, -21.0F, 0.0F, 0.0F, 0.0F, -0.2182F));

		PartDefinition Left_Arm_r1 = Left_Arm.addOrReplaceChild("Left_Arm_r1",
				CubeListBuilder.create().texOffs(32, 48).addBox(4.0F, -1.0F, -2.0F, 3.0F, 12.0F, 4.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-5.0F, -1.0F, 0.0F, -0.1309F, 0.0F, 0.0F));

		PartDefinition Right_Leg = partdefinition.addOrReplaceChild("Right_Leg", CubeListBuilder.create().texOffs(0, 16)
				.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 28.0F, 4.0F, new CubeDeformation(0.0F)),
				PartPose.offset(-1.9F, 12.0F, 0.0F));

		PartDefinition Left_Leg = partdefinition.addOrReplaceChild("Left_Leg",
				CubeListBuilder.create().texOffs(19, 50).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(1.9F, 12.0F, 0.0F, -0.3054F, 0.0F, 0.0F));

		PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(),
				PartPose.offsetAndRotation(0.0F, 3.0F, 3.0F, 0.0F, 0.0F, -1.2654F));

		PartDefinition part1 = all.addOrReplaceChild("part1",
				CubeListBuilder.create().texOffs(20, 0)
						.addBox(-1.0F, -24.0F, -2.0F, 3.0F, 24.0F, 3.0F, new CubeDeformation(0.0F)).texOffs(20, 27)
						.addBox(-2.0F, -27.0F, -3.0F, 5.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)),
				PartPose.offset(-1.0F, 0.0F, 1.0F));

		PartDefinition part2 = part1.addOrReplaceChild("part2",
				CubeListBuilder.create().texOffs(32, 0).addBox(-2.0F, -24.0F, -1.0F, 3.0F, 24.0F, 3.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(1.0F, -27.0F, -1.0F, 0.1705F, -0.0376F, 1.3058F));

		PartDefinition part3 = part2.addOrReplaceChild("part3",
				CubeListBuilder.create().texOffs(44, 0)
						.addBox(-1.0F, -27.0F, -1.0F, 2.0F, 24.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(48, 26)
						.addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -24.0F, 1.0F, 0.4531F, -0.0934F, 0.9094F));

		PartDefinition part4 = part3.addOrReplaceChild("part4",
				CubeListBuilder.create().texOffs(44, 26)
						.addBox(0.0F, -27.0F, -1.0F, 1.0F, 24.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(52, 0)
						.addBox(-1.0F, -3.0F, -2.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -27.0F, 1.0F, 0.4897F, 0.1831F, 1.8716F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
			float red, float green, float blue, float alpha) {
		Waist.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		Right_Leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		Left_Leg.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		all.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}