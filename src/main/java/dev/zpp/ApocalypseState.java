package dev.zpp;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ApocalypseState extends SavedData {
    private static final Factory<ApocalypseState> TYPE = new Factory<>(ApocalypseState::new, ApocalypseState::read, null);
    public long lastDay = -1;
    public long lastNight = -1;
    public long bloodmoonDay = -1;
    public long hordeUntil = 0;
    public long spawned = 0;
    public final Map<UUID, Long> kills = new HashMap<>();
    public static ApocalypseState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "zpp");
    }
    private static ApocalypseState read(CompoundTag nbt, HolderLookup.Provider registries) {
        var s = new ApocalypseState();
        s.lastDay = nbt.getLong("lastDay"); s.lastNight = nbt.getLong("lastNight");
        s.bloodmoonDay = nbt.getLong("bloodmoonDay"); s.hordeUntil = nbt.getLong("hordeUntil");
        s.spawned = nbt.getLong("spawned");
        var kills = nbt.getCompound("kills");
        for (String key : kills.getAllKeys()) {
            try { s.kills.put(UUID.fromString(key), kills.getLong(key)); }
            catch (IllegalArgumentException ignored) { }
        }
        return s;
    }
    @Override public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        nbt.putLong("lastDay", lastDay); nbt.putLong("lastNight", lastNight);
        nbt.putLong("bloodmoonDay", bloodmoonDay); nbt.putLong("hordeUntil", hordeUntil);
        nbt.putLong("spawned", spawned);
        var records = new CompoundTag(); kills.forEach((id, count) -> records.putLong(id.toString(), count));
        nbt.put("kills", records); return nbt;
    }
}
