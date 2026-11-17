package com.mobspawncontroller.verification;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mobspawncontroller.attribute.MobAttributeDiscovery;
import com.mobspawncontroller.client.MobListFilter;
import com.mobspawncontroller.command.MobSpawnManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/** Opt-in real-world regression checks; excluded from normal builds. */
public final class MobListVerification {
    private static final JsonArray CHECKS = new JsonArray();
    private static final AtomicInteger CREATIONS = new AtomicInteger();
    private static final AtomicInteger FAILURES = new AtomicInteger();
    private static final AtomicInteger NULLS = new AtomicInteger();
    private static EntityType<ProbeZombie> custom;
    private static EntityType<Entity> broken;
    private static EntityType<Entity> empty;
    private static EntityType<Entity> linkage;

    // Register alongside mods, before the game's entity registry is frozen.
    public static void registerTypes(BiConsumer<String, EntityType<?>> register) {
        custom = EntityType.Builder.<ProbeZombie>of((type, world) -> {
            CREATIONS.incrementAndGet();
            return new ProbeZombie(world);
        }, MobCategory.MISC).noSave().build("msc_mob_list_probe");
        register.accept("mob_list_probe", custom);
        broken = EntityType.Builder.<Entity>of((type, world) -> {
            FAILURES.incrementAndGet();
            throw new IllegalStateException("Expected regression-test constructor failure");
        }, MobCategory.CREATURE).noSave().build("msc_broken_probe");
        register.accept("broken_probe", broken);
        empty = EntityType.Builder.<Entity>of((type, world) -> {
            NULLS.incrementAndGet();
            return null;
        }, MobCategory.CREATURE).noSave().build("msc_null_probe");
        register.accept("null_probe", empty);
        linkage = EntityType.Builder.<Entity>of((type, world) -> {
            throw new NoClassDefFoundError("Expected regression-test linkage failure");
        }, MobCategory.MISC).noSave().build("msc_linkage_probe");
        register.accept("linkage_probe", linkage);
    }

    public static void run(MinecraftServer server) {
        JsonObject report = new JsonObject();
        try {
            ServerLevel level = server.overworld();
            if (Boolean.getBoolean("msc.verify.audit_mixins")) {
                org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
                check("all configured mixin targets audit successfully", true);
            }
            Map<EntityType<?>, Entity> icons = new HashMap<>();
            MobListFilter filter = new MobListFilter(icons);
            long entitiesBefore = entityCount(level);
            JsonArray visible = new JsonArray();
            for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
                if (filter.isMob(entry.getValue(), level)) {
                    visible.add(entry.getKey().location().toString());
                }
            }
            report.add("visible_mobs", visible);
            check("enumeration does not spawn entities", entityCount(level) == entitiesBefore);

            for (EntityType<?> type : new EntityType<?>[]{
                    EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM, EntityType.VILLAGER,
                    EntityType.ZOMBIE, EntityType.COW, EntityType.WANDERING_TRADER}) {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                check(id + " is visible", filter.isMob(type, level));
                Entity cached = icons.get(type);
                check(id + " reuses the icon instance",
                        filter.isMob(type, level) && icons.get(type) == cached && cached != null);
            }
            for (EntityType<?> type : new EntityType<?>[]{
                    EntityType.ARMOR_STAND, EntityType.PLAYER, EntityType.ITEM, EntityType.ARROW,
                    EntityType.BOAT, EntityType.MINECART, EntityType.INTERACTION}) {
                check(BuiltInRegistries.ENTITY_TYPE.getKey(type) + " is excluded",
                        !filter.isMob(type, level) && !icons.containsKey(type));
            }

            check("custom MISC subclass is accepted without an ID list", filter.isMob(custom, level));
            check("custom mob is only constructed once", filter.isMob(custom, level) && CREATIONS.get() == 1);

            check("constructor failure is isolated and cached",
                    !filter.isMob(broken, level) && !filter.isMob(broken, level) && FAILURES.get() == 1);
            check("null factory is isolated and cached",
                    !filter.isMob(empty, level) && !filter.isMob(empty, level) && NULLS.get() == 1);
            check("linkage failure does not abort enumeration", !filter.isMob(linkage, level));
            check("valid mobs remain visible after failures", filter.isMob(EntityType.IRON_GOLEM, level));

            filter.clear();
            check("closing clears preview instances", icons.isEmpty());
            check("missing world does not create or permanently reject a mob",
                    !filter.isMob(custom, null) && CREATIONS.get() == 1 && filter.isMob(custom, level));
            check("closing clears positive cache", CREATIONS.get() == 2);
            check("closing clears negative cache", !filter.isMob(empty, level) && NULLS.get() == 2);
            check("probes also stay out of the world", entityCount(level) == entitiesBefore);

            MobSpawnManager.clearAll();
            level.getChunkAt(new BlockPos(0, 4, 0));
            for (EntityType<?> type : new EntityType<?>[]{
                    EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM, EntityType.VILLAGER}) {
                var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                var health = BuiltInRegistries.ATTRIBUTE.getKey(Attributes.MAX_HEALTH.value());
                var armor = BuiltInRegistries.ATTRIBUTE.getKey(Attributes.ARMOR.value());
                var controls = MobAttributeDiscovery.discover(level, id);
                check(id + " exposes health and armor controls",
                        controls.stream().anyMatch(control -> control.id().equals(health))
                                && controls.stream().anyMatch(control -> control.id().equals(armor)));
                MobSpawnManager.setAttributeOverrides(id, Map.of(health, 80.0, armor, 9.0));
                var mob = (net.minecraft.world.entity.Mob) type.create(level);
                mob.setPos(0.5, 4, 0.5);
                mob.setNoAi(true);
                check(id + " joins the world", level.addFreshEntity(mob));
                level.tickNonPassenger(mob);
                check(id + " receives health=80 and armor=9",
                        Math.abs(mob.getMaxHealth() - 80) < 0.001
                                && Math.abs(mob.getAttributeBaseValue(Attributes.ARMOR) - 9) < 0.001);
                mob.discard();
            }
            report.addProperty("passed", true);
        } catch (Throwable error) {
            report.addProperty("passed", false);
            report.addProperty("error", error.toString());
            error.printStackTrace();
        } finally {
            MobSpawnManager.clearAll();
            report.add("checks", CHECKS);
            try {
                Files.writeString(Path.of("mob-list-results.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
            System.out.println("MSC_MOB_LIST_VERIFICATION " + report.get("passed"));
            server.halt(false);
        }
    }

    private static long entityCount(ServerLevel level) {
        long count = 0;
        for (Entity ignored : level.getAllEntities()) count++;
        return count;
    }

    private static void check(String name, boolean passed) {
        JsonObject check = new JsonObject();
        check.addProperty("name", name);
        check.addProperty("passed", passed);
        CHECKS.add(check);
        if (!passed) throw new AssertionError(name);
    }

    private static final class ProbeZombie extends Zombie {
        ProbeZombie(Level level) {
            super(EntityType.ZOMBIE, level);
        }
    }
}
