package dev.zpp;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;

/** Validated per-world settings. Commands save a candidate before replacing live settings. */
public final class ZppConfig {
    public boolean enabled = false;
    public boolean dayburn = false;
    public int dayAmount = 0;
    public int nightAmount = 2;
    public int intervalSeconds = 15;
    public int nearbyCap = 32;
    public int globalCap = 256;
    public int minDistance = 24;
    public int maxDistance = 48;
    public int attempts = 12;
    public int maxBlockLight = 7;
    public boolean openSky = true;
    public boolean babies = false;
    public int babyChance = 5;
    public boolean scaleNatural = true;
    public boolean scaling = true;
    public int scalingDays = 30;
    public double maxHealthBonus = 1.5;
    public double maxDamageBonus = 0.75;
    public double maxSpeedBonus = 0.20;
    public boolean hordes = true;
    public int hordeEveryDays = 7;
    public int hordeDurationSeconds = 120;
    public int hordeMultiplier = 3;
    public boolean bloodmoons = true;
    public int bloodmoonChance = 15;
    public int bloodmoonMultiplier = 2;
    public int graceDays = 0;
    public int deathCooldownSeconds = 60;
    public boolean announcements = true;
    public Map<String, Integer> variants = defaultWeights();

    public static Map<String, Integer> defaultWeights() {
        var result = new LinkedHashMap<String, Integer>();
        result.put("zombie", 65); result.put("drowned", 10);
        result.put("husk", 15); result.put("zombie_villager", 10);
        return result;
    }

    public void validate() {
        range("dayAmount", dayAmount, 0, 64); range("nightAmount", nightAmount, 0, 64);
        range("intervalSeconds", intervalSeconds, 2, 3600);
        range("nearbyCap", nearbyCap, 1, 256); range("globalCap", globalCap, 1, 4096);
        range("minDistance", minDistance, 8, 96); range("maxDistance", maxDistance, 9, 112);
        if (minDistance >= maxDistance) throw new IllegalArgumentException("minDistance must be less than maxDistance");
        range("attempts", attempts, 1, 32); range("maxBlockLight", maxBlockLight, 0, 15);
        range("babyChance", babyChance, 0, 100); range("scalingDays", scalingDays, 1, 3650);
        decimal("maxHealthBonus", maxHealthBonus, 0, 10); decimal("maxDamageBonus", maxDamageBonus, 0, 5);
        decimal("maxSpeedBonus", maxSpeedBonus, 0, 1);
        range("hordeEveryDays", hordeEveryDays, 1, 365); range("hordeDurationSeconds", hordeDurationSeconds, 10, 3600);
        range("hordeMultiplier", hordeMultiplier, 1, 8); range("bloodmoonChance", bloodmoonChance, 0, 100);
        range("bloodmoonMultiplier", bloodmoonMultiplier, 1, 8);
        range("graceDays", graceDays, 0, 365); range("deathCooldownSeconds", deathCooldownSeconds, 0, 3600);
        if (variants == null || variants.isEmpty())
            throw new IllegalArgumentException("variants must contain at least one mob");
        int sum = 0;
        for (var e : variants.entrySet()) {
            if (e.getValue() == null) throw new IllegalArgumentException("Missing weight: " + e.getKey());
            range(e.getKey(), e.getValue(), 0, 1000); sum += e.getValue();
            if (!e.getKey().matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid entity ID: " + e.getKey());
        }
        if (sum == 0) throw new IllegalArgumentException("At least one mob type must have a weight greater than 0");
    }
    public void validateMobTypes(ServerLevel world) {
        for (String id : variants.keySet())
            if (ZombieVariants.createMob(id, world) == null)
                throw new IllegalArgumentException("Unknown or non-mob entity ID: " + id);
    }
    private static void range(String key, int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException(key + ": allowed range is " + min + ".." + max);
    }
    private static void decimal(String key, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException(key + ": allowed range is " + min + ".." + max);
    }
}
