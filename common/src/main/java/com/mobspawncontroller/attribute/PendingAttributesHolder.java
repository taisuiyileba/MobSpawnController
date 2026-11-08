package com.mobspawncontroller.attribute;

/** Pending attribute overrides for a mob's spawn. */
public interface PendingAttributesHolder {

    enum State {
        NONE, PENDING, PENDING_PRESERVE_HEALTH;

        public boolean isPending() {
            return this != NONE;
        }
    }

    State mobspawncontroller$getAttributeState();

    void mobspawncontroller$setAttributeState(State state);
}
