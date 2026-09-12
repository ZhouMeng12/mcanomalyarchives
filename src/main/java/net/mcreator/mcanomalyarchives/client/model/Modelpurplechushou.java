package net.mcreator.mcanomalyarchives.client.model;

import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.EntityModel;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

// Made with Blockbench 5.1.6
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports
public class Modelpurplechushou<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("mcanomalyarchives", "modelpurplechushou"), "main");
	public final ModelPart all;
	public final ModelPart part1;
	public final ModelPart part2;
	public final ModelPart part3;
	public final ModelPart part4;

	public Modelpurplechushou(ModelPart root) {
		this.all = root.getChild("all");
		this.part1 = this.all.getChild("part1");
		this.part2 = this.part1.getChild("part2");
		this.part3 = this.part2.getChild("part3");
		this.part4 = this.part3.getChild("part4");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();
		PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		PartDefinition part1 = all.addOrReplaceChild("part1",
				CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -24.0F, -2.0F, 3.0F, 24.0F, 3.0F, new CubeDeformation(0.0F)).texOffs(0, 27).addBox(-2.0F, -27.0F, -3.0F, 5.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)),
				PartPose.offset(-1.0F, 0.0F, 1.0F));
		PartDefinition part2 = part1.addOrReplaceChild("part2", CubeListBuilder.create().texOffs(12, 0).addBox(-2.0F, -24.0F, -1.0F, 3.0F, 24.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(1.0F, -27.0F, -1.0F));
		PartDefinition part3 = part2.addOrReplaceChild("part3",
				CubeListBuilder.create().texOffs(24, 0).addBox(-1.0F, -27.0F, -1.0F, 2.0F, 24.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(28, 26).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, -24.0F, 1.0F));
		PartDefinition part4 = part3.addOrReplaceChild("part4",
				CubeListBuilder.create().texOffs(24, 26).addBox(0.0F, -27.0F, -1.0F, 1.0F, 24.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(32, 0).addBox(-1.0F, -3.0F, -2.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, -27.0F, 1.0F));
		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int rgb) {
		all.render(poseStack, vertexConsumer, packedLight, packedOverlay, rgb);
	}
}