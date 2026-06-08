package com.lotrmod.conquest.client;

import com.lotrmod.conquest.entity.FakePlayerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;

/**
 * Placeholder humanoid renderer for the fake player entity.
 * Uses the standard zombie model layers — replace with custom art later.
 */
public class FakePlayerRenderer extends HumanoidMobRenderer<FakePlayerEntity, HumanoidModel<FakePlayerEntity>> {

    public FakePlayerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }
}
