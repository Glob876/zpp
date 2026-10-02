package dev.zpp;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.util.WorldSavePath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class IntegrationTests implements ModInitializer {
    private int assertions;
    private void check(boolean test,String label) {
        if (!test) throw new AssertionError(label);
        assertions++;
    }
    @Override public void onInitialize() {
        if (!Boolean.getBoolean("zpp.integration")) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                run(server);
                Files.writeString(Path.of("PASSED.txt"),assertions + " assertions passed\n");
                ZppMod.LOGGER.info("ZPP INTEGRATION PASSED: {} assertions",assertions);
            } catch (Throwable failure) {
                ZppMod.LOGGER.error("ZPP INTEGRATION FAILED",failure);
                try { Files.writeString(Path.of("FAILED.txt"),failure.toString()); } catch (Exception ignored) {}
            } finally { server.stop(false); }
        });
    }
    private void run(MinecraftServer server) throws Exception {
        var world=server.getOverworld(); var source=server.getCommandSource();
        var dispatcher=server.getCommandManager().getDispatcher();
        check(dispatcher.execute("zpp",source)==1,"root command");
        check(!ZppMod.config().enabled,"master switch defaults off");
        check(ZppMod.STORE.path().equals(server.getSavePath(WorldSavePath.ROOT).resolve("zpp.json")),"config belongs to world save");
        check(Files.isRegularFile(ZppMod.STORE.path()),"config created inside world save");
        check(dispatcher.execute("zpp nightspawn 3",source)==1 && !ZppMod.config().enabled,"setting does not enable mod");
        check(dispatcher.execute("zpp preset standard",source)==1 && !ZppMod.config().enabled,"preset does not enable mod");
        check(dispatcher.execute("zpp toggle",source)==1 && ZppMod.config().enabled,"toggle enables mod");
        check(ZppMod.config().dayAmount==0,"day spawning default off");
        check(dispatcher.execute("zpp dayburn True",source)==1 && ZppMod.config().dayburn,"uppercase boolean");
        check(dispatcher.execute("zpp dayburn False",source)==1 && !ZppMod.config().dayburn,"uppercase false");
        check(dispatcher.execute("zpp dayspawn amount 3",source)==1 && ZppMod.config().dayAmount==3,"nested amount");
        check(dispatcher.execute("zpp dayspawn 0",source)==1 && ZppMod.config().dayAmount==0,"short amount");
        try { dispatcher.execute("zpp dayburn true",source.withLevel(0)); throw new AssertionError("non-OP mutation"); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { assertions++; }
        check(dispatcher.execute("zpp status",source.withLevel(0))==1,"public dashboard");
        check(dispatcher.execute("zpp spawn distance 48 24",source)==0 && ZppMod.config().minDistance==24,"invalid distance transactional");
        check(dispatcher.execute("zpp variants drowned 0",source)==1,"disable variant");
        for (int i=0;i<100;i++) check(!ZombieVariants.choose(ZppMod.config(),world.random).equals("drowned"),"zero-weight never selected");
        check(dispatcher.execute("zpp variants drowned 10",source)==1,"restore variant");
        world.setTimeOfDay(13000);
        check(dispatcher.execute("zpp bloodmoon start",source)==1 && ZppMod.ENGINE.bloodmoon(server),"start blood moon");
        check(dispatcher.execute("zpp bloodmoon stop",source)==1 && !ZppMod.ENGINE.bloodmoon(server),"stop blood moon");
        world.setTimeOfDay(1000);
        check(dispatcher.execute("zpp bloodmoon start",source)==0,"reject daytime moon");
        check(dispatcher.execute("zpp horde start 60",source)==1 && ZppMod.ENGINE.horde(server),"start horde");
        check(dispatcher.execute("zpp horde stop",source)==1 && !ZppMod.ENGINE.horde(server),"stop horde");
        var burn=ZombieEntity.class.getDeclaredMethod("burnsInDaylight"); burn.setAccessible(true);
        var zombie=EntityType.ZOMBIE.create(world);
        check(!(Boolean)burn.invoke(zombie),"sun immunity mixin applied");
        dispatcher.execute("zpp dayburn true",source);
        check((Boolean)burn.invoke(zombie),"vanilla sunlight restored");
        dispatcher.execute("zpp dayburn false",source);
        zombie.setBaby(true); check(!zombie.isBaby(),"baby mixin applied");
        dispatcher.execute("zpp babies true",source); zombie.setBaby(true); check(zombie.isBaby(),"babies allowed");
        dispatcher.execute("zpp babies false",source);
        var piglin=EntityType.ZOMBIFIED_PIGLIN.create(world); piglin.setBaby(true); check(piglin.isBaby(),"piglins untouched");
        world.setTimeOfDay(30L*24000);
        ZombieVariants.apply(zombie,server); float health=zombie.getMaxHealth();
        check(health>20,"day scaling");
        ZombieVariants.apply(zombie,server); check(zombie.getMaxHealth()==health,"scaling does not stack");
        zombie.setHealth(health/2);
        dispatcher.execute("zpp scaling false",source); ZombieVariants.apply(zombie,server);
        check(zombie.getMaxHealth()==20 && zombie.getHealth()==10,"scaling removal preserves health ratio");
        dispatcher.execute("zpp scaling true",source);
        for (String type : ZppConfig.defaultWeights().keySet()) check(ZombieVariants.supported(ZombieVariants.create(type,world)),"vanilla type "+type);
        // Build a deterministic, loaded surface for actual spawning.
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(true,server);
        server.setDifficulty(Difficulty.NORMAL,true);
        for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) world.getChunk(x,z);
        for (int x=-60;x<=60;x++) for (int z=-60;z<=60;z++) world.setBlockState(new BlockPos(x,80,z),Blocks.STONE.getDefaultState(),2);
        var player=new ServerPlayerEntity(server,world,new GameProfile(UUID.randomUUID(),"ZppTest"));
        player.refreshPositionAndAngles(0.5,81,0.5,0,0);
        dispatcher.execute("zpp spawn maxlight 15",source);
        for (String type : ZppConfig.defaultWeights().keySet()) {
            int count=ZppMod.ENGINE.spawn(player,type,2,true); check(count==2,"actual spawn "+type+" count="+count);
        }
        check(ZppMod.ENGINE.managedCount()==8,"managed count");
        check(ApocalypseState.get(server).spawned==8,"spawn stats");
        for (var entity : world.iterateEntities()) if (entity instanceof ZombieEntity z && z.getCommandTags().contains(ZombieVariants.MANAGED)) {
            check(!z.isBaby(),"spawned adult"); check(z.getMaxHealth()>20,"spawned scaled");
            check(z.squaredDistanceTo(player)>=24*24,"minimum distance");
            var saved=new NbtCompound(); z.writeNbt(saved);
            var restored=(ZombieEntity)z.getType().create(world); restored.readNbt(saved);
            ZombieVariants.apply(restored,server); check(restored.getMaxHealth()==z.getMaxHealth(),"reload no stacking");
        }
        dispatcher.execute("zpp babies true",source);
        dispatcher.execute("zpp babies chance 100",source);
        check(ZppMod.ENGINE.spawn(player,"zombie",2,true)==2,"spawn babies");
        int babies=0; int chickens=0;
        for (var entity : world.iterateEntities()) {
            if (entity instanceof ZombieEntity z && z.isBaby()) babies++;
            if (entity.getType()==EntityType.CHICKEN) chickens++;
        }
        check(babies==2,"baby chance 100"); check(chickens==0,"no uncontrolled jockey spawns");
        dispatcher.execute("zpp babies false",source);
        for (var entity : world.iterateEntities()) if (entity instanceof ZombieEntity z) check(!z.isBaby(),"live adult conversion");
        dispatcher.execute("zpp spawn cap 8",source);
        check(ZppMod.ENGINE.spawn(player,"zombie",10,true)==0,"nearby cap");
        dispatcher.execute("zpp spawn cap 32",source); dispatcher.execute("zpp spawn globalcap 8",source);
        check(ZppMod.ENGINE.spawn(player,"zombie",10,true)==0,"global cap");
        dispatcher.execute("zpp spawn globalcap 256",source);
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);
        check(ZppMod.ENGINE.spawn(player,"zombie",2,true)==0,"mob spawning gamerule");
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(true,server);
        dispatcher.execute("zpp enabled false",source);
        check(ZppMod.ENGINE.spawn(player,"zombie",2,true)==0,"master switch");
        check((Boolean)burn.invoke(zombie),"disabled restores vanilla sun");
        dispatcher.execute("zpp enabled true",source);
        // Scheduled hordes must wait for night when day spawning is disabled.
        var eventState=ApocalypseState.get(server); eventState.lastDay=-1; eventState.lastNight=-1;
        world.setTimeOfDay(6L*24000+1000);
        for (int i=0;i<20;i++) ZppMod.ENGINE.tick(server);
        check(!ZppMod.ENGINE.horde(server),"no wasted daytime horde");
        world.setTimeOfDay(6L*24000+13000);
        for (int i=0;i<20;i++) ZppMod.ENGINE.tick(server);
        check(ZppMod.ENGINE.horde(server),"scheduled night horde");
        // Water-surface drowned must satisfy open-sky at the air block above water.
        for (int x=-60;x<=60;x++) for (int z=-60;z<=60;z++) world.setBlockState(new BlockPos(x,81,z),Blocks.WATER.getDefaultState(),2);
        var previousDrowned=new java.util.HashSet<UUID>();
        for (var entity : world.iterateEntities()) if (entity.getType()==EntityType.DROWNED) previousDrowned.add(entity.getUuid());
        check(ZppMod.ENGINE.spawn(player,"drowned",2,true)==2,"drowned at water surface with open sky");
        int inWater=0;
        for (var entity : world.iterateEntities()) if (entity.getType()==EntityType.DROWNED && !previousDrowned.contains(entity.getUuid()) && entity.getBlockY()==81) inWater++;
        check(inWater==2,"drowned placed in water");
        // Clean defaults keep the test repeatable.
        ZppMod.STORE.replace(new ZppConfig());
        var state=ApocalypseState.get(server);
        var nbt=state.writeNbt(new NbtCompound());
        check(nbt.getLong("spawned")==12,"persistent stats serialize");
        check(dispatcher.execute("zpp variants add minecraft:squid 25",source)==1,"add non-zombie mob by ID");
        check(ZppMod.config().variants.get("minecraft:squid")==25,"custom weight stored");
        check(dispatcher.execute("zpp preset save water",source)==1,"save global preset");
        check(Files.isRegularFile(ZppMod.PRESETS.path()),"global preset file created");
        ZppMod.PRESETS.load();
        check(ZppMod.PRESETS.get("water").variants.get("minecraft:squid")==25,"global preset survives reload");
        check(dispatcher.execute("zpp variants add minecraft:player 1",source)==0,"reject non-mob entity ID");
        check(dispatcher.execute("zpp variants remove minecraft:squid",source)==1,"remove custom mob");
        check(!ZppMod.config().variants.containsKey("minecraft:squid"),"custom mob removed");
        check(dispatcher.execute("zpp preset load water",source)==1,"load global preset");
        check(ZppMod.config().variants.get("minecraft:squid")==25,"preset restored custom mob");
        check(!ZppMod.config().enabled,"preset restored master switch");
        check(dispatcher.execute("zpp enabled true",source)==1,"enable for custom spawn");
        check(ZppMod.ENGINE.spawn(player,"minecraft:squid",2,true)==2,"spawn configured non-zombie mob");
        check(dispatcher.execute("zpp preset delete water",source)==1,"delete global preset");
        check(ZppMod.PRESETS.get("water")==null,"deleted preset unavailable");
        ZppMod.STORE.replace(new ZppConfig());
    }
}
