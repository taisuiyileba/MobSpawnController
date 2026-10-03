package com.mobspawncontroller.loadout;

/** Implemented on {@link net.minecraft.world.entity.Mob} by a mixin to carry the preset chosen at spawn time. */
public interface PendingLoadoutHolder {

    LoadoutPreset mobspawncontroller$getPendingLoadout();

    void mobspawncontroller$setPendingLoadout(LoadoutPreset preset);
}
