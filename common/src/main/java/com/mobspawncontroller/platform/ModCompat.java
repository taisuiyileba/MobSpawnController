package com.mobspawncontroller.platform;

import java.util.Objects;
import java.util.function.Predicate;

/** Loader-neutral access to optional mod availability. */
public final class ModCompat {

    private static Predicate<String> modLoaded = modId -> false;

    private ModCompat() {
    }

    public static void setModLoadedChecker(Predicate<String> checker) {
        modLoaded = Objects.requireNonNull(checker);
    }

    public static boolean isModLoaded(String modId) {
        return modLoaded.test(modId);
    }
}
