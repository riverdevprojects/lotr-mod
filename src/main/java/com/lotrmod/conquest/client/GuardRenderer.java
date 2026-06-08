package com.lotrmod.conquest.client;

import com.lotrmod.conquest.entity.GuardEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class GuardRenderer extends HumanoidMobRenderer<GuardEntity, HumanoidModel<GuardEntity>> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");

    public GuardRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(GuardEntity entity) {
        return TEXTURE;
    }
}
