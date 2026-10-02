package dev.zpp;

import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;
import java.util.*;

public final class ApocalypseEngine {
    private final Set<UUID> managed = new HashSet<>();
    private final Map<UUID, Long> nextWave = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private long ticks;
    private int playerCursor;
    public int managedCount() { return managed.size(); }
    public void reset() { managed.clear(); nextWave.clear(); protectedUntil.clear(); ticks = 0; playerCursor = 0; }
    public void loaded(Entity entity, ServerWorld world) {
        if (entity instanceof MobEntity mob) {
            if (mob.getCommandTags().contains(ZombieVariants.MANAGED)) managed.add(mob.getUuid());
            ZombieVariants.apply(mob, world.getServer());
        }
    }
    public void unloaded(Entity entity) { managed.remove(entity.getUuid()); }
    public void died(ServerPlayerEntity player) {
        protectedUntil.put(player.getUuid(), ticks + ZppMod.config().deathCooldownSeconds * 20L);
    }
    public void disconnected(ServerPlayerEntity player) { nextWave.remove(player.getUuid()); }
    public void refresh(MinecraftServer server) {
        var c = ZppMod.config();
        var state = ApocalypseState.get(server);
        if (!c.enabled || !c.hordes) state.hordeUntil = 0;
        if (!c.enabled || !c.bloodmoons) state.bloodmoonDay = -1;
        state.markDirty();
        nextWave.clear();
        for (var world : server.getWorlds()) for (var entity : world.iterateEntities())
            if (entity instanceof MobEntity mob) ZombieVariants.apply(mob, server);
    }
    public boolean horde(MinecraftServer server) {
        return ZppMod.config().enabled && ZppMod.config().hordes && ApocalypseState.get(server).hordeUntil > server.getOverworld().getTime();
    }
    public boolean bloodmoon(MinecraftServer server) {
        return ZppMod.config().enabled && ZppMod.config().bloodmoons
                && ApocalypseMath.night(server.getOverworld().getTimeOfDay())
                && ApocalypseState.get(server).bloodmoonDay == ApocalypseMath.day(server.getOverworld().getTimeOfDay());
    }
    public void announce(MinecraftServer server, String message) {
        if (!ZppMod.config().announcements) return;
        Text title = Text.literal("[ZPP] " + message).formatted(Formatting.DARK_RED);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
            player.networkHandler.sendPacket(new TitleS2CPacket(title));
        }
    }
    public void tick(MinecraftServer server) {
        ticks++;
        var c = ZppMod.config();
        if (!c.enabled) return;
        if (ticks % 20 == 0) events(server);
        // At most one player's bounded wave is processed in a server tick.
        var players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;
        var player = players.get(Math.floorMod(playerCursor++, players.size()));
        if (!eligible(player) || ticks < protectedUntil.getOrDefault(player.getUuid(), 0L)) return;
        if (ticks < nextWave.getOrDefault(player.getUuid(), 0L)) return;
        nextWave.put(player.getUuid(), ticks + c.intervalSeconds * 20L);
        var world = player.getServerWorld();
        if (ApocalypseMath.day(world.getTimeOfDay()) <= c.graceDays) return;
        int amount = ApocalypseMath.waveAmount(c, ApocalypseMath.night(world.getTimeOfDay()), horde(server), bloodmoon(server));
        spawn(player, null, amount, false);
    }
    private void events(MinecraftServer server) {
        var c = ZppMod.config(); var world = server.getOverworld(); var s = ApocalypseState.get(server);
        long day = ApocalypseMath.day(world.getTimeOfDay());
        if (s.lastDay != day) {
            boolean first = s.lastDay == -1;
            s.lastDay = day; s.bloodmoonDay = -1;
            if (!first) announce(server, "Day " + day);
            refresh(server); s.markDirty();
        }
        if (ApocalypseMath.night(world.getTimeOfDay()) && s.lastNight != day) {
            s.lastNight = day;
            if (c.hordes && day > c.graceDays && day % c.hordeEveryDays == 0) {
                s.hordeUntil = world.getTime() + c.hordeDurationSeconds * 20L;
                announce(server, "A horde is approaching! Waves are intensified for " + c.hordeDurationSeconds + " seconds.");
            }
            if (c.bloodmoons && day > c.graceDays && world.random.nextInt(100) < c.bloodmoonChance) {
                s.bloodmoonDay = day;
                announce(server, "Blood moon! Zombie waves are intensified until dawn.");
            }
            s.markDirty();
        }
        if (s.hordeUntil != 0 && s.hordeUntil <= world.getTime()) {
            s.hordeUntil = 0; s.markDirty(); announce(server, "The horde is retreating.");
        }
        if (s.bloodmoonDay != -1 && !ApocalypseMath.night(world.getTimeOfDay())) {
            s.bloodmoonDay = -1; s.markDirty(); announce(server, "The blood moon has ended.");
        }
        protectedUntil.entrySet().removeIf(e -> e.getValue() <= ticks);
    }
    private boolean eligible(ServerPlayerEntity p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && p.getServerWorld().getRegistryKey() == World.OVERWORLD;
    }
    /** Manual waves skip day/night amounts and grace, but keep all placement and population safeguards. */
    public int spawn(ServerPlayerEntity player, String type, int requested, boolean manual) {
        var c = ZppMod.config(); var world = player.getServerWorld();
        if (!c.enabled || world.getRegistryKey() != World.OVERWORLD || !player.isAlive()
                || world.getDifficulty() == Difficulty.PEACEFUL || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) return 0;
        if (!manual && !eligible(player)) return 0;
        int nearby = world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(c.maxDistance + 16),
                mob -> mob.isAlive() && (mob.getCommandTags().contains(ZombieVariants.MANAGED)
                        || ZombieVariants.configured(mob, c))).size();
        int amount = Math.max(0, Math.min(Math.min(requested, 64), Math.min(c.nearbyCap - nearby, c.globalCap - managed.size())));
        int spawned = 0;
        for (int i = 0; i < amount; i++) {
            String chosen = type == null ? ZombieVariants.choose(c, world.random) : type;
            BlockPos pos = findPosition(player, chosen);
            if (pos == null) continue;
            var zombie = ZombieVariants.createMob(chosen, world);
            if (zombie == null) continue;
            zombie.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360, 0);
            zombie.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT,
                    zombie instanceof ZombieEntity ? new ZombieEntity.ZombieData(false, false) : null, null);
            if (zombie instanceof ZombieEntity z) z.setBaby(c.babies && world.random.nextInt(100) < c.babyChance);
            zombie.addCommandTag(ZombieVariants.MANAGED);
            if (!world.isSpaceEmpty(zombie) || !world.getWorldBorder().contains(zombie.getBoundingBox())) continue;
            ZombieVariants.apply(zombie, world.getServer());
            if (world.spawnEntity(zombie)) {
                if (!player.isCreative() && !player.isSpectator()) zombie.setTarget(player);
                spawned++;
            }
        }
        if (spawned > 0) {
            var state = ApocalypseState.get(world.getServer()); state.spawned += spawned; state.markDirty();
        }
        return spawned;
    }
    private BlockPos findPosition(ServerPlayerEntity player, String type) {
        var c = ZppMod.config(); var world = player.getServerWorld();
        for (int attempt = 0; attempt < c.attempts; attempt++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(c.minDistance * c.minDistance + world.random.nextDouble()
                    * (c.maxDistance * c.maxDistance - c.minDistance * c.minDistance));
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            // Check before querying heightmap: never load or generate a chunk to spawn a wave.
            if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;
            int surface = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = c.openSky ? surface : player.getBlockY() + world.random.nextInt(17) - 8;
            BlockPos pos = new BlockPos(x, y, z);
            if (!c.openSky) {
                for (int down = 0; down < 8 && pos.getY() > world.getBottomY() + 1
                        && world.getBlockState(pos.down()).isAir(); down++) pos = pos.down();
            }
            boolean water = ZombieVariants.water(type) && world.getFluidState(pos.down()).isIn(FluidTags.WATER);
            if (ZombieVariants.water(type) && !type.equals("drowned") && !water) continue;
            if (water && c.openSky) pos = pos.down();
            if (!valid(player, pos, water)) continue;
            return pos;
        }
        return null;
    }
    private boolean valid(ServerPlayerEntity player, BlockPos pos, boolean water) {
        var c = ZppMod.config(); var world = player.getServerWorld();
        if (pos.getY() <= world.getBottomY() || pos.getY() + 2 >= world.getTopY()) return false;
        if (!world.getWorldBorder().contains(pos) || world.getBiome(pos).matchesKey(BiomeKeys.MUSHROOM_FIELDS)) return false;
        if (c.openSky && !world.isSkyVisible(water ? pos.up() : pos)) return false;
        if (world.getLightLevel(LightType.BLOCK, pos) > c.maxBlockLight) return false;
        if (!water && (!world.getFluidState(pos).isEmpty() || !world.getFluidState(pos.up()).isEmpty()
                || !world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), net.minecraft.util.math.Direction.UP))) return false;
        if (world.getBlockState(pos.down()).isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)
                || world.getBlockState(pos.down()).isOf(net.minecraft.block.Blocks.CAMPFIRE)) return false;
        Box box = new Box(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2, pos.getX() + 0.8, pos.getY() + 1.95, pos.getZ() + 0.8);
        if (!world.isSpaceEmpty(box)) return false;
        for (var other : world.getPlayers())
            if (other.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < c.minDistance * c.minDistance) return false;
        return true;
    }
}
