package dev.zpp;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import java.util.Map;
import java.util.UUID;

public final class ZombieVariants {
    public static final String MANAGED = "zpp.spawned";
    public static final Map<String, EntityType<? extends ZombieEntity>> TYPES = Map.of(
            "zombie", EntityType.ZOMBIE, "drowned", EntityType.DROWNED,
            "husk", EntityType.HUSK, "zombie_villager", EntityType.ZOMBIE_VILLAGER);
    private static final UUID SCALE = UUID.nameUUIDFromBytes("zpp:day_scaling".getBytes());
    private ZombieVariants() {}
    public static boolean supported(ZombieEntity zombie) { return TYPES.containsValue(zombie.getType()); }
    public static String key(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid entity ID: " + raw);
        String shortName = id.getNamespace().equals("minecraft") ? id.getPath() : "";
        return TYPES.containsKey(shortName) ? shortName : id.toString();
    }
    public static EntityType<?> resolve(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) return null;
        var entry = Registries.ENTITY_TYPE.getOrEmpty(id);
        if (entry.isEmpty()) return null;
        return entry.get();
    }
    public static boolean configured(MobEntity mob, ZppConfig config) {
        return config.variants.containsKey(key(Registries.ENTITY_TYPE.getId(mob.getType()).toString()));
    }
    public static boolean water(String type) {
        var resolved = resolve(type);
        if (resolved == null) return false;
        SpawnGroup group = resolved.getSpawnGroup();
        return type.equals("drowned") || group == SpawnGroup.WATER_CREATURE || group == SpawnGroup.WATER_AMBIENT
                || group == SpawnGroup.UNDERGROUND_WATER_CREATURE || group == SpawnGroup.AXOLOTLS;
    }
    public static String choose(ZppConfig config, Random random) {
        int roll = random.nextInt(config.variants.values().stream().mapToInt(Integer::intValue).sum());
        for (var entry : config.variants.entrySet()) {
            roll -= entry.getValue(); if (roll < 0) return entry.getKey();
        }
        throw new IllegalStateException("Invalid variant weights");
    }
    public static ZombieEntity create(String type, ServerWorld world) { return TYPES.get(type).create(world); }
    public static MobEntity createMob(String type, ServerWorld world) {
        var resolved = resolve(type);
        return resolved != null && resolved.create(world) instanceof MobEntity mob ? mob : null;
    }
    public static void apply(MobEntity zombie, MinecraftServer server) {
        var c = ZppMod.config();
        boolean eligibleType = zombie instanceof ZombieEntity z && supported(z) || configured(zombie, c);
        if (!eligibleType && !hasScaling(zombie)) return;
        if (zombie instanceof ZombieEntity z && c.enabled && !c.babies && z.isBaby()) z.setBaby(false);
        boolean scale = eligibleType && c.enabled && c.scaling
                && (c.scaleNatural || zombie.getCommandTags().contains(MANAGED));
        double progress = scale ? ApocalypseMath.progression(ApocalypseMath.day(server.getOverworld().getTimeOfDay()), c.scalingDays) : 0;
        float ratio = zombie.getHealth() / zombie.getMaxHealth();
        modifier(zombie, EntityAttributes.GENERIC_MAX_HEALTH, progress * c.maxHealthBonus);
        modifier(zombie, EntityAttributes.GENERIC_ATTACK_DAMAGE, progress * c.maxDamageBonus);
        modifier(zombie, EntityAttributes.GENERIC_MOVEMENT_SPEED, progress * c.maxSpeedBonus);
        zombie.setHealth(Math.min(zombie.getMaxHealth(), ratio * zombie.getMaxHealth()));
    }
    private static boolean hasScaling(MobEntity mob) {
        return scaled(mob, EntityAttributes.GENERIC_MAX_HEALTH)
                || scaled(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE)
                || scaled(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED);
    }
    private static boolean scaled(MobEntity mob, EntityAttribute attribute) {
        var instance = mob.getAttributeInstance(attribute);
        return instance != null && instance.getModifier(SCALE) != null;
    }
    private static void modifier(MobEntity zombie, EntityAttribute attribute, double amount) {
        var instance = zombie.getAttributeInstance(attribute);
        if (instance == null) return;
        var old = instance.getModifier(SCALE);
        if (old != null && old.getValue() == amount) return;
        instance.removeModifier(SCALE);
        if (amount != 0) instance.addPersistentModifier(new EntityAttributeModifier(SCALE, "zpp.day_scaling", amount,
                EntityAttributeModifier.Operation.MULTIPLY_BASE));
    }
}
