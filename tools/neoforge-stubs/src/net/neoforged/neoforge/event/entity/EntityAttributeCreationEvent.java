package net.neoforged.neoforge.event.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

/** 桩：签名对齐 26.2 官方（源码核实）。 */
public class EntityAttributeCreationEvent {
    public void put(EntityType<? extends LivingEntity> entity, AttributeSupplier map) {
    }
}
