package com.mobspawncontroller.attribute;

/** Transient spawn state; attribute overrides are applied once after the mob's own initialization. */
public interface PendingAttributesHolder {

    enum State {
        NONE, PENDING, PENDING_PRESERVE_HEALTH, APPLIED;

        public boolean isPending() {
            return this == PENDING || this == PENDING_PRESERVE_HEALTH;
        }
    }

    State mobspawncontroller$getAttributeState();

    void mobspawncontroller$setAttributeState(State state);
}
