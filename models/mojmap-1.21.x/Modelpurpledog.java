// Made with Blockbench 5.1.5
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

public class Modelpurpledog<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			new ResourceLocation("modid", "purpledog"), "main");
	private final ModelPart all;
	private final ModelPart body;
	private final ModelPart left_front;
	private final ModelPart right_front;
	private final ModelPart left_back;
	private final ModelPart left_back2;
	private final ModelPart tail;
	private final ModelPart head1;
	private final ModelPart up;
	private final ModelPart down;
	private final ModelPart head2;
	private final ModelPart up2;
	private final ModelPart down2;
	private final ModelPart head3;
	private final ModelPart up3;
	private final ModelPart down3;

	public Modelpurpledog(ModelPart root) {
		this.all = root.getChild("all");
		this.body = this.all.getChild("body");
		this.left_front = this.body.getChild("left_front");
		this.right_front = this.body.getChild("right_front");
		this.left_back = this.body.getChild("left_back");
		this.left_back2 = this.body.getChild("left_back2");
		this.tail = this.body.getChild("tail");
		this.head1 = this.all.getChild("head1");
		this.up = this.head1.getChild("up");
		this.down = this.head1.getChild("down");
		this.head2 = this.all.getChild("head2");
		this.up2 = this.head2.getChild("up2");
		this.down2 = this.head2.getChild("down2");
		this.head3 = this.all.getChild("head3");
		this.up3 = this.head3.getChild("up3");
		this.down3 = this.head3.getChild("down3");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(),
				PartPose.offsetAndRotation(0.0F, 13.0F, 0.0F, 0.1745F, 0.0F, 0.0F));

		PartDefinition body = all.addOrReplaceChild("body",
				CubeListBuilder.create().texOffs(24, 42)
						.addBox(4.0F, -2.0F, 12.0F, 4.0F, 6.0F, 10.0F, new CubeDeformation(0.0F)).texOffs(0, 48)
						.addBox(-6.0F, -6.0F, 0.0F, 12.0F, 8.0F, 20.0F, new CubeDeformation(0.0F)).texOffs(13, 42)
						.addBox(-8.0F, -2.0F, 12.0F, 4.0F, 6.0F, 10.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 0.0F, 6.0F, -0.5236F, 0.0F, 0.0F));

		PartDefinition left_front = body.addOrReplaceChild("left_front",
				CubeListBuilder.create().texOffs(8, 36).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 14.0F, 4.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(6.0F, -1.0F, 7.0F, -0.2182F, 0.0F, -0.48F));

		PartDefinition right_front = body
				.addOrReplaceChild("right_front",
						CubeListBuilder.create().texOffs(16, 36).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 14.0F, 4.0F,
								new CubeDeformation(0.0F)),
						PartPose.offsetAndRotation(-6.0F, -1.0F, 7.0F, 0.0F, 0.0F, 0.48F));

		PartDefinition left_back = body.addOrReplaceChild("left_back",
				CubeListBuilder.create().texOffs(40, 36).addBox(0.0F, 0.0F, -2.0F, 2.0F, 14.0F, 4.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(8.0F, 4.0F, 19.0F, -0.6318F, 0.1841F, -1.3257F));

		PartDefinition left_back2 = body.addOrReplaceChild("left_back2",
				CubeListBuilder.create().texOffs(0, 52).addBox(0.0F, 0.0F, -2.0F, 2.0F, 14.0F, 4.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-8.0F, 4.0F, 19.0F, -2.4667F, 0.1945F, -1.8078F));

		PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(8, 52).addBox(-3.0F,
				-4.0F, 26.0F, 6.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -6.0F));

		PartDefinition head1 = all.addOrReplaceChild("head1", CubeListBuilder.create(),
				PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, -0.1309F, 0.0F, 0.0F));

		PartDefinition up = head1.addOrReplaceChild("up",
				CubeListBuilder.create().texOffs(8, 12)
						.addBox(-6.0F, -8.0F, -12.0F, 12.0F, 8.0F, 12.0F, new CubeDeformation(0.0F)).texOffs(52, 4)
						.addBox(-1.0F, -6.0F, -13.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, -3.0F, 1.0F));

		PartDefinition cube_r1 = up.addOrReplaceChild("cube_r1",
				CubeListBuilder.create().texOffs(24, 1).addBox(-1.0F, -12.0F, -1.0F, 3.0F, 12.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-3.0F, -7.0F, -8.0F, 0.2597F, 0.0189F, -0.3483F));

		PartDefinition cube_r2 = up.addOrReplaceChild("cube_r2",
				CubeListBuilder.create().texOffs(16, 1).addBox(-1.0F, -7.0F, -1.0F, 3.0F, 7.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(2.0F, -7.0F, -8.0F, 0.3559F, 0.0651F, 0.252F));

		PartDefinition down = head1.addOrReplaceChild("down",
				CubeListBuilder.create().texOffs(0, 12)
						.addBox(-6.0F, 0.0F, -12.0F, 12.0F, 3.0F, 12.0F, new CubeDeformation(0.0F)).texOffs(51, 1)
						.addBox(-3.0F, -3.0F, -11.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(54, 1)
						.addBox(-1.0F, -3.0F, -11.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(57, 1)
						.addBox(1.0F, -3.0F, -11.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(60, 1)
						.addBox(3.0F, -3.0F, -11.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(57, 5)
						.addBox(-5.0F, -3.0F, -10.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(60, 5)
						.addBox(-5.0F, -3.0F, -8.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(54, 5)
						.addBox(4.0F, -3.0F, -9.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, -3.0F, 1.0F));

		PartDefinition head2 = all.addOrReplaceChild("head2", CubeListBuilder.create(),
				PartPose.offsetAndRotation(-5.2679F, -3.0F, 6.5359F, 0.0F, 0.7418F, 0.0F));

		PartDefinition up2 = head2.addOrReplaceChild("up2",
				CubeListBuilder.create().texOffs(8, 16)
						.addBox(-4.0F, -6.0F, -8.0F, 8.0F, 6.0F, 8.0F, new CubeDeformation(0.0F)).texOffs(51, 6)
						.addBox(-1.0F, -5.0F, -9.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition cube_r3 = up2.addOrReplaceChild("cube_r3",
				CubeListBuilder.create().texOffs(0, 17).addBox(0.0F, -9.0F, -1.0F, 2.0F, 9.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-2.0F, -5.0F, -4.0F, 0.2597F, 0.0189F, -0.3483F));

		PartDefinition cube_r4 = up2.addOrReplaceChild("cube_r4",
				CubeListBuilder.create().texOffs(40, 1).addBox(0.0F, -5.0F, -1.0F, 2.0F, 5.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(1.0F, -6.0F, -4.0F, 0.3559F, 0.0651F, 0.252F));

		PartDefinition down2 = head2.addOrReplaceChild("down2",
				CubeListBuilder.create().texOffs(8, 24)
						.addBox(-4.0F, 0.0F, -8.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.0F)).texOffs(51, 9)
						.addBox(-2.0F, -2.0F, -7.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(54, 9)
						.addBox(0.0F, -2.0F, -7.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(57, 9)
						.addBox(2.0F, -2.0F, -7.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(60, 9)
						.addBox(2.0F, -2.0F, -5.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(48, 13)
						.addBox(-3.0F, -2.0F, -5.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition head3 = all.addOrReplaceChild("head3", CubeListBuilder.create(),
				PartPose.offsetAndRotation(5.7321F, -3.0F, 6.5359F, 0.0F, -0.7418F, 0.0F));

		PartDefinition up3 = head3
				.addOrReplaceChild("up3",
						CubeListBuilder.create().texOffs(51, 2).addBox(-1.0F, -5.0F, -9.0F, 2.0F, 2.0F, 2.0F,
								new CubeDeformation(0.0F)),
						PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.0436F, 0.0F, 0.0F));

		PartDefinition cube_r5 = up3.addOrReplaceChild("cube_r5",
				CubeListBuilder.create().texOffs(32, 17).addBox(0.0F, -9.0F, -1.0F, 2.0F, 9.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-2.0F, -5.0F, -4.0F, 0.2597F, 0.0189F, -0.3483F));

		PartDefinition cube_r6 = up3.addOrReplaceChild("cube_r6",
				CubeListBuilder.create().texOffs(24, 17).addBox(0.0F, -5.0F, -1.0F, 2.0F, 5.0F, 1.0F,
						new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(1.0F, -6.0F, -4.0F, 0.3559F, 0.0651F, 0.252F));

		PartDefinition cube_r7 = up3
				.addOrReplaceChild("cube_r7",
						CubeListBuilder.create().texOffs(16, 24).addBox(-2.0F, -5.8255F, -8.0038F, 8.0F, 6.0F, 8.0F,
								new CubeDeformation(0.0F)),
						PartPose.offsetAndRotation(-2.0F, 0.0F, 0.0F, 0.0436F, 0.0F, 0.0F));

		PartDefinition down3 = head3.addOrReplaceChild("down3",
				CubeListBuilder.create().texOffs(40, 24)
						.addBox(-4.0F, 0.0F, -8.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.0F)).texOffs(51, 13)
						.addBox(-3.0F, -2.0F, -5.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(54, 13)
						.addBox(-3.0F, -2.0F, -7.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(57, 13)
						.addBox(-1.0F, -2.0F, -7.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(60, 13)
						.addBox(1.0F, -2.0F, -7.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(55, 7)
						.addBox(2.0F, -2.0F, -5.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
			float red, float green, float blue, float alpha) {
		all.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}

	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
	}
}