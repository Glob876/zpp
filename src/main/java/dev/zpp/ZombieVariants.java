package dev.zpp;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import java.util.Map;

public final class ZombieVariants {
    public static final String MANAGED = "zpp.spawned";
    public static final Map<String, EntityType<? extends Zombie>> TYPES = Map.of(
            "zombie", EntityType.ZOMBIE, "drowned", EntityType.DROWNED,
            "husk", EntityType.HUSK, "zombie_villager", EntityType.ZOMBIE_VILLAGER);
    private static final ResourceLocation SCALE = ResourceLocation.fromNamespaceAndPath("zpp", "day_scaling");
    private ZombieVariants() {}
    public static boolean supported(Zombie zombie) { return TYPES.containsValue(zombie.getType()); }
    public static String key(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid entity ID: " + raw);
        String shortName = id.getNamespace().equals("minecraft") ? id.getPath() : "";
        return TYPES.containsKey(shortName) ? shortName : id.toString();
    }
    public static EntityType<?> resolve(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) return null;
        var entry = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (entry.isEmpty()) return null;
        return entry.get();
    }
    public static boolean configured(Mob mob, ZppConfig config) {
        return config.variants.containsKey(key(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString()));
    }
    public static boolean water(String type) {
        var resolved = resolve(type);
        if (resolved == null) return false;
        MobCategory group = resolved.getCategory();
        return type.equals("drowned") || group == MobCategory.WATER_CREATURE || group == MobCategory.WATER_AMBIENT
                || group == MobCategory.UNDERGROUND_WATER_CREATURE || group == MobCategory.AXOLOTLS;
    }
    public static String choose(ZppConfig config, RandomSource random) {
        int roll = random.nextInt(config.variants.values().stream().mapToInt(Integer::intValue).sum());
        for (var entry : config.variants.entrySet()) {
            roll -= entry.getValue(); if (roll < 0) return entry.getKey();
        }
        throw new IllegalStateException("Invalid variant weights");
    }
    public static Zombie create(String type, ServerLevel world) { return TYPES.get(type).create(world); }
    public static Mob createMob(String type, ServerLevel world) {
        var resolved = resolve(type);
        return resolved != null && resolved.create(world) instanceof Mob mob ? mob : null;
    }
    public static void apply(Mob zombie, MinecraftServer server) {
        var c = ZppMod.config();
        boolean eligibleType = zombie instanceof Zombie z && supported(z) || configured(zombie, c);
        if (!eligibleType && !hasScaling(zombie)) return;
        if (zombie instanceof Zombie z && c.enabled && !c.babies && z.isBaby()) z.setBaby(false);
        boolean scale = eligibleType && c.enabled && c.scaling
                && (c.scaleNatural || zombie.getTags().contains(MANAGED));
        double progress = scale ? ApocalypseMath.progression(ApocalypseMath.day(server.overworld().getDayTime()), c.scalingDays) : 0;
        float ratio = zombie.getHealth() / zombie.getMaxHealth();
        modifier(zombie, Attributes.MAX_HEALTH, progress * c.maxHealthBonus);
        modifier(zombie, Attributes.ATTACK_DAMAGE, progress * c.maxDamageBonus);
        modifier(zombie, Attributes.MOVEMENT_SPEED, progress * c.maxSpeedBonus);
        zombie.setHealth(Math.min(zombie.getMaxHealth(), ratio * zombie.getMaxHealth()));
    }
    private static boolean hasScaling(Mob mob) {
        return scaled(mob, Attributes.MAX_HEALTH)
                || scaled(mob, Attributes.ATTACK_DAMAGE)
                || scaled(mob, Attributes.MOVEMENT_SPEED);
    }
    private static boolean scaled(Mob mob, Holder<Attribute> attribute) {
        var instance = mob.getAttribute(attribute);
        return instance != null && instance.getModifier(SCALE) != null;
    }
    private static void modifier(Mob zombie, Holder<Attribute> attribute, double amount) {
        var instance = zombie.getAttribute(attribute);
        if (instance == null) return;
        var old = instance.getModifier(SCALE);
        if (old != null && old.amount() == amount) return;
        instance.removeModifier(SCALE);
        if (amount != 0) instance.addPermanentModifier(new AttributeModifier(SCALE, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }
}
