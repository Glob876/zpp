package dev.zpp;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.Mob;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import java.util.*;

public final class ApocalypseEngine {
    private final Set<UUID> managed = new HashSet<>();
    private final Map<UUID, Long> nextWave = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private long ticks;
    private int playerCursor;
    public int managedCount() { return managed.size(); }
    public void reset() { managed.clear(); nextWave.clear(); protectedUntil.clear(); ticks = 0; playerCursor = 0; }
    public void loaded(Entity entity, ServerLevel world) {
        if (entity instanceof Mob mob) {
            if (mob.getTags().contains(ZombieVariants.MANAGED)) managed.add(mob.getUUID());
            ZombieVariants.apply(mob, world.getServer());
        }
    }
    public void unloaded(Entity entity) { managed.remove(entity.getUUID()); }
    public void died(ServerPlayer player) {
        protectedUntil.put(player.getUUID(), ticks + ZppMod.config().deathCooldownSeconds * 20L);
    }
    public void disconnected(ServerPlayer player) { nextWave.remove(player.getUUID()); }
    public void refresh(MinecraftServer server) {
        var c = ZppMod.config();
        var state = ApocalypseState.get(server);
        if (!c.enabled || !c.hordes) state.hordeUntil = 0;
        if (!c.enabled || !c.bloodmoons) state.bloodmoonDay = -1;
        state.setDirty();
        nextWave.clear();
        for (var world : server.getAllLevels()) for (var entity : world.getAllEntities())
            if (entity instanceof Mob mob) ZombieVariants.apply(mob, server);
    }
    public boolean horde(MinecraftServer server) {
        return ZppMod.config().enabled && ZppMod.config().hordes && ApocalypseState.get(server).hordeUntil > server.overworld().getGameTime();
    }
    public boolean bloodmoon(MinecraftServer server) {
        return ZppMod.config().enabled && ZppMod.config().bloodmoons
                && ApocalypseMath.night(server.overworld().getDayTime())
                && ApocalypseState.get(server).bloodmoonDay == ApocalypseMath.day(server.overworld().getDayTime());
    }
    public void announce(MinecraftServer server, String message) {
        if (!ZppMod.config().announcements) return;
        Component title = Component.literal("[ZPP] " + message).withStyle(ChatFormatting.DARK_RED);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }
    public void tick(MinecraftServer server) {
        ticks++;
        var c = ZppMod.config();
        if (!c.enabled) return;
        if (ticks % 20 == 0) events(server);
        // At most one player's bounded wave is processed in a server tick.
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        var player = players.get(Math.floorMod(playerCursor++, players.size()));
        if (!eligible(player) || ticks < protectedUntil.getOrDefault(player.getUUID(), 0L)) return;
        if (ticks < nextWave.getOrDefault(player.getUUID(), 0L)) return;
        nextWave.put(player.getUUID(), ticks + c.intervalSeconds * 20L);
        var world = player.serverLevel();
        if (ApocalypseMath.day(world.getDayTime()) <= c.graceDays) return;
        int amount = ApocalypseMath.waveAmount(c, ApocalypseMath.night(world.getDayTime()), horde(server), bloodmoon(server));
        spawn(player, null, amount, false);
    }
    private void events(MinecraftServer server) {
        var c = ZppMod.config(); var world = server.overworld(); var s = ApocalypseState.get(server);
        long day = ApocalypseMath.day(world.getDayTime());
        if (s.lastDay != day) {
            boolean first = s.lastDay == -1;
            s.lastDay = day; s.bloodmoonDay = -1;
            if (!first) announce(server, "Day " + day);
            refresh(server); s.setDirty();
        }
        if (ApocalypseMath.night(world.getDayTime()) && s.lastNight != day) {
            s.lastNight = day;
            if (c.hordes && day > c.graceDays && day % c.hordeEveryDays == 0) {
                s.hordeUntil = world.getGameTime() + c.hordeDurationSeconds * 20L;
                announce(server, "A horde is approaching! Waves are intensified for " + c.hordeDurationSeconds + " seconds.");
            }
            if (c.bloodmoons && day > c.graceDays && world.getRandom().nextInt(100) < c.bloodmoonChance) {
                s.bloodmoonDay = day;
                announce(server, "Blood moon! Zombie waves are intensified until dawn.");
            }
            s.setDirty();
        }
        if (s.hordeUntil != 0 && s.hordeUntil <= world.getGameTime()) {
            s.hordeUntil = 0; s.setDirty(); announce(server, "The horde is retreating.");
        }
        if (s.bloodmoonDay != -1 && !ApocalypseMath.night(world.getDayTime())) {
            s.bloodmoonDay = -1; s.setDirty(); announce(server, "The blood moon has ended.");
        }
        protectedUntil.entrySet().removeIf(e -> e.getValue() <= ticks);
    }
    private boolean eligible(ServerPlayer p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && p.serverLevel().dimension() == Level.OVERWORLD;
    }
    /** Manual waves skip day/night amounts and grace, but keep all placement and population safeguards. */
    public int spawn(ServerPlayer player, String type, int requested, boolean manual) {
        var c = ZppMod.config(); var world = player.serverLevel();
        if (!c.enabled || world.dimension() != Level.OVERWORLD || !player.isAlive()
                || world.getDifficulty() == Difficulty.PEACEFUL || !world.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return 0;
        if (!manual && !eligible(player)) return 0;
        int nearby = world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(c.maxDistance + 16),
                mob -> mob.isAlive() && (mob.getTags().contains(ZombieVariants.MANAGED)
                        || ZombieVariants.configured(mob, c))).size();
        int amount = Math.max(0, Math.min(Math.min(requested, 64), Math.min(c.nearbyCap - nearby, c.globalCap - managed.size())));
        int spawned = 0;
        for (int i = 0; i < amount; i++) {
            String chosen = type == null ? ZombieVariants.choose(c, world.getRandom()) : type;
            BlockPos pos = findPosition(player, chosen);
            if (pos == null) continue;
            var zombie = ZombieVariants.createMob(chosen, world);
            if (zombie == null) continue;
            zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.getRandom().nextFloat() * 360, 0);
            zombie.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), MobSpawnType.EVENT,
                    zombie instanceof Zombie ? new Zombie.ZombieGroupData(false, false) : null);
            if (zombie instanceof Zombie z) z.setBaby(c.babies && world.getRandom().nextInt(100) < c.babyChance);
            zombie.addTag(ZombieVariants.MANAGED);
            if (!world.noCollision(zombie) || !world.getWorldBorder().isWithinBounds(zombie.getBoundingBox())) continue;
            ZombieVariants.apply(zombie, world.getServer());
            if (world.addFreshEntity(zombie)) {
                if (!player.isCreative() && !player.isSpectator()) zombie.setTarget(player);
                spawned++;
            }
        }
        if (spawned > 0) {
            var state = ApocalypseState.get(world.getServer()); state.spawned += spawned; state.setDirty();
        }
        return spawned;
    }
    private BlockPos findPosition(ServerPlayer player, String type) {
        var c = ZppMod.config(); var world = player.serverLevel();
        for (int attempt = 0; attempt < c.attempts; attempt++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(c.minDistance * c.minDistance + world.getRandom().nextDouble()
                    * (c.maxDistance * c.maxDistance - c.minDistance * c.minDistance));
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            // Check before querying heightmap: never load or generate a chunk to spawn a wave.
            if (!world.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
            int surface = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = c.openSky ? surface : player.getBlockY() + world.getRandom().nextInt(17) - 8;
            BlockPos pos = new BlockPos(x, y, z);
            if (!c.openSky) {
                for (int down = 0; down < 8 && pos.getY() > world.getMinBuildHeight() + 1
                        && world.getBlockState(pos.below()).isAir(); down++) pos = pos.below();
            }
            boolean water = ZombieVariants.water(type) && world.getFluidState(pos.below()).is(FluidTags.WATER);
            if (ZombieVariants.water(type) && !type.equals("drowned") && !water) continue;
            if (water && c.openSky) pos = pos.below();
            if (!valid(player, pos, water)) continue;
            return pos;
        }
        return null;
    }
    private boolean valid(ServerPlayer player, BlockPos pos, boolean water) {
        var c = ZppMod.config(); var world = player.serverLevel();
        if (pos.getY() <= world.getMinBuildHeight() || pos.getY() + 2 >= world.getHeight()) return false;
        if (!world.getWorldBorder().isWithinBounds(pos) || world.getBiome(pos).is(Biomes.MUSHROOM_FIELDS)) return false;
        if (c.openSky && !world.canSeeSky(water ? pos.above() : pos)) return false;
        if (world.getBrightness(LightLayer.BLOCK, pos) > c.maxBlockLight) return false;
        if (!water && (!world.getFluidState(pos).isEmpty() || !world.getFluidState(pos.above()).isEmpty()
                || !world.getBlockState(pos.below()).isFaceSturdy(world, pos.below(), net.minecraft.core.Direction.UP))) return false;
        if (world.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || world.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)) return false;
        AABB box = new AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2, pos.getX() + 0.8, pos.getY() + 1.95, pos.getZ() + 0.8);
        if (!world.noCollision(box)) return false;
        for (var other : world.players())
            if (other.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < c.minDistance * c.minDistance) return false;
        return true;
    }
}
