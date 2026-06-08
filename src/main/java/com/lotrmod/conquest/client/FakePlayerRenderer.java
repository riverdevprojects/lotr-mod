package com.lotrmod.conquest.client;

import com.lotrmod.conquest.entity.FakePlayerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Placeholder humanoid renderer for the fake player entity.
 * Uses the zombie model + texture — replace with custom art later.
 */
public class FakePlayerRenderer extends HumanoidMobRenderer<FakePlayerEntity, HumanoidModel<FakePlayerEntity>> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");

    public FakePlayerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(FakePlayerEntity entity) {
        return TEXTURE;
    }
}
