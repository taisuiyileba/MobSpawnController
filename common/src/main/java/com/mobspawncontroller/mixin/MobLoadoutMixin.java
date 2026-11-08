package com.mobspawncontroller.mixin;

import com.mobspawncontroller.attribute.PendingAttributesHolder;
import com.mobspawncontroller.loadout.LoadoutPreset;
import com.mobspawncontroller.loadout.PendingLoadoutHolder;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Mob.class)
public abstract class MobLoadoutMixin implements PendingLoadoutHolder, PendingAttributesHolder {

    @Unique
    private LoadoutPreset mobspawncontroller$pendingLoadout;

    @Unique
    private boolean mobspawncontroller$loadoutApplied;

    @Unique
    private State mobspawncontroller$attributeState = State.NONE;

    @Override
    public State mobspawncontroller$getAttributeState() {
        return mobspawncontroller$attributeState;
    }

    @Override
    public void mobspawncontroller$setAttributeState(State state) {
        mobspawncontroller$attributeState = state;
    }

    @Override
    public LoadoutPreset mobspawncontroller$getPendingLoadout() {
        return mobspawncontroller$pendingLoadout;
    }

    @Override
    public void mobspawncontroller$setPendingLoadout(LoadoutPreset preset) {
        mobspawncontroller$pendingLoadout = preset;
    }

    @Override
    public boolean mobspawncontroller$hasAppliedLoadout() {
        return mobspawncontroller$loadoutApplied;
    }

    @Override
    public void mobspawncontroller$markLoadoutApplied() {
        mobspawncontroller$loadoutApplied = true;
    }
}
