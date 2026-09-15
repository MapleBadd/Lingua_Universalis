package net.neoforged.neoforge.client.event;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** 桩：签名对齐 26.2 官方（源码核实）。 */
public class EntityRenderersEvent {
    public static class RegisterRenderers {
        public <T extends Entity> void registerEntityRenderer(
                EntityType<? extends T> entityType, EntityRendererProvider<T> provider) {
        }
    }
}
