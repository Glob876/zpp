package dev.zpp.integration;

import dev.zpp.*;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Mod("zpp_integration")
public final class IntegrationTestMod {
    private int assertions;
    public IntegrationTestMod() { NeoForge.EVENT_BUS.addListener(this::started); }
    private void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        assertions++;
    }
    private void started(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            run(server);
            Files.writeString(Path.of("PASSED.txt"), assertions + " assertions passed\n");
            ZppMod.LOGGER.info("ZPP INTEGRATION PASSED: {} assertions", assertions);
        } catch (Throwable failure) {
            ZppMod.LOGGER.error("ZPP INTEGRATION FAILED", failure);
            try { Files.writeString(Path.of("FAILED.txt"), failure.toString()); } catch (Exception ignored) {}
        } finally { server.halt(false); }
    }
    private void run(MinecraftServer server) throws Exception {
        var world = server.overworld();
        var source = server.createCommandSourceStack();
        var commands = server.getCommands();
        check(commands.getDispatcher().execute("zpp", source) == 1, "root command");
        check(!ZppMod.config().enabled, "default off");
        check(ZppMod.STORE.path().equals(server.getWorldPath(LevelResource.ROOT).resolve("zpp.json")), "world config path");
        check(Files.isRegularFile(ZppMod.STORE.path()), "world config exists");
        check(commands.getDispatcher().execute("zpp nightspawn 3", source) == 1 && !ZppMod.config().enabled, "edit keeps off");
        check(commands.getDispatcher().execute("zpp preset standard", source) == 1 && !ZppMod.config().enabled, "preset keeps off");
        check(commands.getDispatcher().execute("zpp toggle", source) == 1 && ZppMod.config().enabled, "toggle");
        check(commands.getDispatcher().execute("zpp dayburn True", source) == 1 && ZppMod.config().dayburn, "uppercase boolean");
        check(commands.getDispatcher().execute("zpp dayburn False", source) == 1 && !ZppMod.config().dayburn, "uppercase false");
        check(commands.getDispatcher().execute("zpp dayspawn amount 3", source) == 1 && ZppMod.config().dayAmount == 3, "nested amount");
        check(commands.getDispatcher().execute("zpp dayspawn 0", source) == 1 && ZppMod.config().dayAmount == 0, "short amount");
        try { commands.getDispatcher().execute("zpp dayburn true", source.withPermission(0)); throw new AssertionError("non-op mutation"); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { assertions++; }
        check(commands.getDispatcher().execute("zpp status", source.withPermission(0)) == 1, "public status");
        check(commands.getDispatcher().execute("zpp spawn distance 48 24", source) == 0 && ZppMod.config().minDistance == 24, "transactional edit");
        check(commands.getDispatcher().execute("zpp variants drowned 0", source) == 1, "zero variant");
        for (int i=0; i<100; i++) check(!ZombieVariants.choose(ZppMod.config(), world.getRandom()).equals("drowned"), "weight zero");
        check(commands.getDispatcher().execute("zpp variants drowned 10", source) == 1, "restore variant");
        world.setDayTime(13000);
        check(commands.getDispatcher().execute("zpp bloodmoon start", source) == 1 && ZppMod.ENGINE.bloodmoon(server), "blood moon start");
        check(commands.getDispatcher().execute("zpp bloodmoon stop", source) == 1 && !ZppMod.ENGINE.bloodmoon(server), "blood moon stop");
        world.setDayTime(1000);
        check(commands.getDispatcher().execute("zpp bloodmoon start", source) == 0, "reject day moon");
        check(commands.getDispatcher().execute("zpp horde start 60", source) == 1 && ZppMod.ENGINE.horde(server), "horde start");
        check(commands.getDispatcher().execute("zpp horde stop", source) == 1 && !ZppMod.ENGINE.horde(server), "horde stop");
        var sun = Zombie.class.getDeclaredMethod("isSunSensitive"); sun.setAccessible(true);
        var zombie = EntityType.ZOMBIE.create(world);
        check(!(Boolean)sun.invoke(zombie), "sun mixin");
        commands.getDispatcher().execute("zpp dayburn true", source);
        check((Boolean)sun.invoke(zombie), "sun restored");
        commands.getDispatcher().execute("zpp dayburn false", source);
        zombie.setBaby(true); check(!zombie.isBaby(), "baby mixin");
        commands.getDispatcher().execute("zpp babies true", source);
        zombie.setBaby(true); check(zombie.isBaby(), "baby enabled");
        commands.getDispatcher().execute("zpp babies false", source);
        world.setDayTime(30L*24000);
        ZombieVariants.apply(zombie, server);
        check(zombie.getMaxHealth() > 20, "day scaling");
        float health = zombie.getMaxHealth();
        ZombieVariants.apply(zombie, server);
        check(zombie.getMaxHealth() == health, "no scaling stack");
        zombie.setHealth(health/2);
        commands.getDispatcher().execute("zpp scaling false", source);
        ZombieVariants.apply(zombie, server);
        check(zombie.getMaxHealth() == 20 && zombie.getHealth() == 10, "remove scaling");
        commands.getDispatcher().execute("zpp scaling true", source);
        for (String type : ZppConfig.defaultWeights().keySet()) check(ZombieVariants.supported(ZombieVariants.create(type, world)), "type " + type);
        world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, server);
        server.setDifficulty(Difficulty.NORMAL, true);
        for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) world.getChunk(x,z);
        for (int x=-60;x<=60;x++) for (int z=-60;z<=60;z++) world.setBlock(new BlockPos(x,200,z),Blocks.STONE.defaultBlockState(),2);
        var player = new ServerPlayer(server, world, new GameProfile(UUID.randomUUID(), "ZppTest"), ClientInformation.createDefault());
        player.moveTo(0.5,201,0.5,0,0);
        commands.getDispatcher().execute("zpp spawn maxlight 15", source);
        for (String type : ZppConfig.defaultWeights().keySet()) {
            int count = ZppMod.ENGINE.spawn(player,type,2,true);
            check(count == 2, "actual spawn " + type + " count=" + count);
        }
        check(ZppMod.ENGINE.managedCount() == 8, "managed count");
        check(ApocalypseState.get(server).spawned == 8, "spawn stats");
        var state = ApocalypseState.get(server);
        CompoundTag saved = state.save(new CompoundTag(), world.registryAccess());
        check(saved.getLong("spawned") == 8, "persistent stats");
        commands.getDispatcher().execute("zpp babies true",source);
        commands.getDispatcher().execute("zpp babies chance 100",source);
        check(ZppMod.ENGINE.spawn(player,"zombie",2,true) == 2,"baby wave");
        int babies=0;
        for (var entity : world.getAllEntities()) if (entity instanceof Zombie z && z.isBaby()) babies++;
        check(babies==2,"baby chance");
        commands.getDispatcher().execute("zpp babies false",source);
        commands.getDispatcher().execute("zpp spawn cap 8",source);
        check(ZppMod.ENGINE.spawn(player,"zombie",2,true)==0,"nearby cap");
        commands.getDispatcher().execute("zpp spawn cap 32",source);
        commands.getDispatcher().execute("zpp spawn globalcap 8",source);
        check(ZppMod.ENGINE.spawn(player,"zombie",2,true)==0,"global cap");
        commands.getDispatcher().execute("zpp spawn globalcap 256",source);
        world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        check(ZppMod.ENGINE.spawn(player,"zombie",2,true)==0,"gamerule");
        world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,server);
        commands.getDispatcher().execute("zpp enabled false",source);
        check(ZppMod.ENGINE.spawn(player,"zombie",2,true)==0,"master switch");
        check((Boolean)sun.invoke(zombie),"disabled restores sun");
        check(commands.getDispatcher().execute("zpp variants add minecraft:squid 25",source)==1,"custom mob");
        check(commands.getDispatcher().execute("zpp preset save water",source)==1,"preset save");
        check(Files.isRegularFile(ZppMod.PRESETS.path()),"preset file");
        check(commands.getDispatcher().execute("zpp preset load water",source)==1,"preset load");
        check(ZppMod.config().variants.get("minecraft:squid")==25,"preset value restored");
        check(commands.getDispatcher().execute("zpp preset delete water",source)==1,"preset delete");
        check(ZppMod.PRESETS.get("water")==null,"preset removed");
        commands.getDispatcher().execute("zpp enabled true",source);
        check(commands.getDispatcher().execute("zpp variants add minecraft:skeleton 25",source)==1,"custom land mob");
        check(ZppMod.ENGINE.spawn(player,"minecraft:skeleton",2,true)==2,"custom mob spawn");
        var eventState=ApocalypseState.get(server); eventState.lastDay=-1; eventState.lastNight=-1;
        world.setDayTime(6L*24000+1000);
        for (int i=0;i<20;i++) ZppMod.ENGINE.tick(server);
        check(!ZppMod.ENGINE.horde(server),"no daytime horde");
        world.setDayTime(6L*24000+13000);
        for (int i=0;i<20;i++) ZppMod.ENGINE.tick(server);
        check(ZppMod.ENGINE.horde(server),"scheduled horde");
        for (int x=-60;x<=60;x++) for (int z=-60;z<=60;z++) world.setBlock(new BlockPos(x,201,z),Blocks.WATER.defaultBlockState(),2);
        check(ZppMod.ENGINE.spawn(player,"drowned",2,true)==2,"water-surface drowned");
        ZppMod.STORE.replace(new ZppConfig());
    }
}
