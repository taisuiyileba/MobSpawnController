package com.mobspawncontroller.compat;

import com.mobspawncontroller.MobSpawnController;
import com.mobspawncontroller.platform.ModCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/** Optional, class-loader-safe integration with the Serene Seasons public API. */
public final class SereneSeasonsCompat {

    public static final String MOD_ID = "sereneseasons";
    /** Values exposed by Serene Seasons' /season set command. */
    public static final List<String> SEASONS = List.of(
            "early_spring", "mid_spring", "late_spring",
            "early_summer", "mid_summer", "late_summer",
            "early_autumn", "mid_autumn", "late_autumn",
            "early_winter", "mid_winter", "late_winter"
    );

    private static Api api;
    private static boolean apiLookupAttempted;
    private static boolean invocationFailureLogged;

    private SereneSeasonsCompat() {
    }

    public static boolean isAvailable() {
        return ModCompat.isModLoaded(MOD_ID);
    }

    /**
     * Applies a sub-season allow/deny list. If Serene Seasons is absent (or its API cannot be
     * queried), the condition is ignored so an optional dependency never blocks all spawning.
     */
    public static boolean isCurrentSeasonAllowed(ServerLevel level, List<String> seasons,
                                                 List<String> excludedSeasons) {
        if ((seasons.isEmpty() && excludedSeasons.isEmpty()) || !isAvailable()) {
            return true;
        }

        String current = getCurrentSeason(level);
        if (current == null) {
            return true;
        }
        return (seasons.isEmpty() || seasons.contains(current)) && !excludedSeasons.contains(current);
    }

    private static String getCurrentSeason(ServerLevel level) {
        Api resolved = getApi();
        if (resolved == null) {
            return null;
        }
        try {
            Object state = resolved.getSeasonState().invoke(null, level);
            Object season = state == null ? null : resolved.getSubSeason().invoke(state);
            return season instanceof Enum<?> value ? value.name().toLowerCase(Locale.ROOT) : null;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            if (!invocationFailureLogged) {
                invocationFailureLogged = true;
                MobSpawnController.LOGGER.warn("Failed to query the current Serene Seasons season; "
                        + "season spawn conditions will be ignored", exception);
            }
            return null;
        }
    }

    private static synchronized Api getApi() {
        if (api != null || apiLookupAttempted) {
            return api;
        }
        apiLookupAttempted = true;
        try {
            Class<?> helperClass = Class.forName("sereneseasons.api.season.SeasonHelper");
            Class<?> stateClass = Class.forName("sereneseasons.api.season.ISeasonState");
            api = new Api(helperClass.getMethod("getSeasonState", Level.class),
                    stateClass.getMethod("getSubSeason"));
        } catch (ReflectiveOperationException | LinkageError exception) {
            MobSpawnController.LOGGER.warn("Serene Seasons is loaded but its season API is unavailable; "
                    + "season spawn conditions will be ignored", exception);
        }
        return api;
    }

    private record Api(Method getSeasonState, Method getSubSeason) {
    }
}
