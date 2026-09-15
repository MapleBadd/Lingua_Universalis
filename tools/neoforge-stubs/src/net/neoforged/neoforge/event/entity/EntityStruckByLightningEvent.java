package net.neoforged.neoforge.event.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;

/** 桩：对齐 26.2 官方 EntityStruckByLightningEvent。 */
public class EntityStruckByLightningEvent {
    private final Entity entity;
    private final LightningBolt lightning;

    public EntityStruckByLightningEvent(Entity entity, LightningBolt lightning) {
        this.entity = entity;
        this.lightning = lightning;
    }

    public Entity getEntity() {
        return entity;
    }

    public LightningBolt getLightning() {
        return lightning;
    }
}
