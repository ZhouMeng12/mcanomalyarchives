// Made with Blockbench 5.1.6
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

public class Modelstrangetreefoot<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			new ResourceLocation("modid", "strangetreefoot"), "main");
	private final ModelPart whole;
	private final ModelPart leg1;
	private final ModelPart leg12;
	private final ModelPart leg2;
	private final ModelPart leg3;
	private final ModelPart leg4;
	private final ModelPart leg5;

	public Modelstrangetreefoot(ModelPart root) {
		this.whole = root.getChild("whole");
		this.leg1 = this.whole.getChild("leg1");
		this.leg12 = this.leg1.getChild("leg12");
		this.leg2 = this.whole.getChild("leg2");
		this.leg3 = this.leg2.getChild("leg3");
		this.leg4 = this.whole.getChild("leg4");
		this.leg5 = this.leg4.getChild("leg5");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition whole = partdefinition.addOrReplaceChild("whole", CubeListBuilder.create(),
				PartPose.offset(0.0F, 8.0F, 0.0F));

		PartDefinition leg1 = whole.addOrReplaceChild("leg1", CubeListBuilder.create().texOffs(0, 106).addBox(-3.0F,
				0.0F, -3.0F, 6.0F, 16.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition leg12 = leg1.addOrReplaceChild("leg12", CubeListBuilder.create().texOffs(24, 108).addBox(-2.0F,
				0.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition leg2 = whole.addOrReplaceChild("leg2", CubeListBuilder.create().texOffs(24, 108).addBox(-2.0F,
				0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(5.0F, 16.0F, -6.0F));

		PartDefinition leg3 = leg2.addOrReplaceChild("leg3", CubeListBuilder.create().texOffs(28, 110).addBox(-1.0F,
				0.0F, -1.0F, 2.0F, 14.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 12.0F, 0.0F));

		PartDefinition leg4 = whole.addOrReplaceChild("leg4", CubeListBuilder.create().texOffs(24, 108).addBox(-2.0F,
				0.0F, -2.0F, 4.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-6.0F, 16.0F, 6.0F));

		PartDefinition leg5 = leg4.addOrReplaceChild("leg5", CubeListBuilder.create().texOffs(28, 110).addBox(-1.0F,
				0.0F, -1.0F, 2.0F, 12.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 10.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
			float red, float green, float blue, float alpha) {
		whole.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}