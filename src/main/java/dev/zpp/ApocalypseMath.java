package dev.zpp;

public final class ApocalypseMath {
    private ApocalypseMath() {}
    public static long day(long timeOfDay) { return Math.max(0, Math.floorDiv(timeOfDay, 24000)) + 1; }
    public static boolean night(long timeOfDay) { long t = Math.floorMod(timeOfDay, 24000); return t >= 13000 && t < 23000; }
    public static double progression(long day, int fullAfterDays) {
        return Math.max(0, Math.min(1, (day - 1.0) / fullAfterDays));
    }
    public static int waveAmount(ZppConfig c, boolean night, boolean horde, boolean bloodmoon) {
        int amount = night ? c.nightAmount : c.dayAmount;
        if (horde) amount *= c.hordeMultiplier;
        if (bloodmoon) amount *= c.bloodmoonMultiplier;
        return Math.min(64, amount);
    }
}
