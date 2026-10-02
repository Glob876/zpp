package dev.zpp;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ApocalypseState extends PersistentState {
    public long lastDay = -1;
    public long lastNight = -1;
    public long bloodmoonDay = -1;
    public long hordeUntil = 0;
    public long spawned = 0;
    public final Map<UUID, Long> kills = new HashMap<>();
    public static ApocalypseState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(ApocalypseState::read, ApocalypseState::new, "zpp");
    }
    private static ApocalypseState read(NbtCompound nbt) {
        var s = new ApocalypseState();
        s.lastDay = nbt.getLong("lastDay"); s.lastNight = nbt.getLong("lastNight");
        s.bloodmoonDay = nbt.getLong("bloodmoonDay"); s.hordeUntil = nbt.getLong("hordeUntil");
        s.spawned = nbt.getLong("spawned");
        var kills = nbt.getCompound("kills");
        for (String key : kills.getKeys()) {
            try { s.kills.put(UUID.fromString(key), kills.getLong(key)); }
            catch (IllegalArgumentException ignored) { /* Ignore corrupt UUIDs, retain other records. */ }
        }
        return s;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLong("lastDay", lastDay); nbt.putLong("lastNight", lastNight);
        nbt.putLong("bloodmoonDay", bloodmoonDay); nbt.putLong("hordeUntil", hordeUntil);
        nbt.putLong("spawned", spawned);
        var records = new NbtCompound(); kills.forEach((id, count) -> records.putLong(id.toString(), count));
        nbt.put("kills", records); return nbt;
    }
}
