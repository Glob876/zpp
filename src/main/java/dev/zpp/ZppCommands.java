package dev.zpp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.Locale;
import java.util.function.*;
import static net.minecraft.server.command.CommandManager.*;

public final class ZppCommands {
    private ZppCommands() {}
    private static int say(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal("[ZPP] " + message).formatted(Formatting.GREEN), false); return 1;
    }
    private static int change(ServerCommandSource source, Consumer<ZppConfig> edit) {
        try {
            var candidate = ZppMod.STORE.copy(); edit.accept(candidate);
            candidate.validateMobTypes(source.getServer().getOverworld()); ZppMod.STORE.replace(candidate);
            ZppMod.ENGINE.refresh(source.getServer());
            return say(source, "Settings saved. ZPP is " + (candidate.enabled ? "ON." : "OFF. Other settings will take effect after /zpp enabled true.") + " /zpp status");
        } catch (Exception ex) { source.sendError(Text.literal("[ZPP] Not saved: " + ex.getMessage())); return 0; }
    }
    private static LiteralArgumentBuilder<ServerCommandSource> toggle(String name, Predicate<ZppConfig> get, BiConsumer<ZppConfig, Boolean> set) {
        return literal(name).executes(ctx -> say(ctx.getSource(), name + " = " + get.test(ZppMod.config())))
                .then(argument("value", StringArgumentType.word()).requires(s -> s.hasPermissionLevel(2))
                        .suggests((ctx, builder) -> { builder.suggest("true"); builder.suggest("false"); return builder.buildFuture(); })
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "value").toLowerCase(Locale.ROOT);
                            if (!raw.equals("true") && !raw.equals("false"))
                                throw new SimpleCommandExceptionType(Text.literal("Expected true or false (case-insensitive)")).create();
                            return change(ctx.getSource(), c -> set.accept(c, Boolean.parseBoolean(raw)));
                        }));
    }
    private static LiteralArgumentBuilder<ServerCommandSource> number(String name, int min, int max, ToIntFunction<ZppConfig> get, ObjIntConsumer<ZppConfig> set) {
        return literal(name).executes(ctx -> say(ctx.getSource(), name + " = " + get.applyAsInt(ZppMod.config())))
                .then(argument("value", IntegerArgumentType.integer(min, max)).requires(s -> s.hasPermissionLevel(2))
                        .executes(ctx -> change(ctx.getSource(), c -> set.accept(c, IntegerArgumentType.getInteger(ctx, "value")))));
    }
    private static LiteralArgumentBuilder<ServerCommandSource> decimal(String name, double max, ToDoubleFunction<ZppConfig> get, ObjDoubleConsumer<ZppConfig> set) {
        return literal(name).executes(ctx -> say(ctx.getSource(), name + " = " + get.applyAsDouble(ZppMod.config())))
                .then(argument("value", DoubleArgumentType.doubleArg(0, max)).requires(s -> s.hasPermissionLevel(2))
                        .executes(ctx -> change(ctx.getSource(), c -> set.accept(c, DoubleArgumentType.getDouble(ctx, "value")))));
    }
    private static LiteralArgumentBuilder<ServerCommandSource> amount(String name, ToIntFunction<ZppConfig> get, ObjIntConsumer<ZppConfig> set) {
        return number(name, 0, 64, get, set).then(number("amount", 0, 64, get, set));
    }
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("zpp").executes(ctx -> status(ctx.getSource()));
        root.then(literal("help").executes(ctx -> {
            say(ctx.getSource(), "Zombie Apocalipse++ • Minecraft 1.20.1 • Fabric\n"
                    + "/zpp status | day | stats | config | reload\n"
                    + "/zpp toggle | enabled|dayburn|babies <true|false>\n"
                    + "/zpp dayspawn|nightspawn amount <0..64>\n"
                    + "/zpp variants list | add <entity_id> <weight> | remove <entity_id>\n"
                    + "/zpp spawn <type> <amount> [player] • spawn interval|cap|globalcap|distance|maxlight|opensky|grace|cooldown\n"
                    + "/zpp scaling <true|false> • scaling days|health|damage|speed|natural\n"
                    + "/zpp horde start|stop|enabled|every|duration|multiplier\n"
                    + "/zpp bloodmoon start|stop|enabled|chance|multiplier\n"
                    + "/zpp preset casual|standard|hardcore | list|save|load|delete <name>\n"
                    + "A setting without a value shows its current state. Changes require OP level 2. Use Tab completion."); return 1;
        }));
        root.then(literal("status").executes(ctx -> status(ctx.getSource())));
        root.then(toggle("enabled", c -> c.enabled, (c,v) -> c.enabled = v));
        root.then(literal("toggle").requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> change(ctx.getSource(), c -> c.enabled = !c.enabled)));
        root.then(toggle("dayburn", c -> c.dayburn, (c,v) -> c.dayburn = v));
        root.then(toggle("babies", c -> c.babies, (c,v) -> c.babies = v)
                .then(number("chance", 0, 100, c -> c.babyChance, (c,v) -> c.babyChance = v)));
        root.then(toggle("announcements", c -> c.announcements, (c,v) -> c.announcements = v));
        root.then(amount("dayspawn", c -> c.dayAmount, (c,v) -> c.dayAmount = v));
        root.then(amount("nightspawn", c -> c.nightAmount, (c,v) -> c.nightAmount = v));
        var variants = literal("variants").executes(ctx -> showVariants(ctx.getSource()));
        variants.then(literal("list").executes(ctx -> showVariants(ctx.getSource())));
        for (String type : ZppConfig.defaultWeights().keySet())
            variants.then(number(type, 0, 1000, c -> c.variants.get(type), (c,v) -> c.variants.put(type,v)));
        variants.then(literal("add").requires(s -> s.hasPermissionLevel(2))
                .then(argument("entity_id", IdentifierArgumentType.identifier())
                        .then(argument("weight", IntegerArgumentType.integer(0,1000))
                                .executes(ctx -> {
                                    String id = ZombieVariants.key(IdentifierArgumentType.getIdentifier(ctx,"entity_id").toString());
                                    if (ZombieVariants.createMob(id, ctx.getSource().getServer().getOverworld()) == null) {
                                        ctx.getSource().sendError(Text.literal("[ZPP] Entity ID is not a mob: " + id)); return 0;
                                    }
                                    return change(ctx.getSource(), c -> c.variants.put(id, IntegerArgumentType.getInteger(ctx,"weight")));
                                }))));
        variants.then(literal("remove").requires(s -> s.hasPermissionLevel(2))
                .then(argument("entity_id", IdentifierArgumentType.identifier())
                        .suggests((ctx, builder) -> {
                            ZppMod.config().variants.keySet().forEach(builder::suggest); return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String id = ZombieVariants.key(IdentifierArgumentType.getIdentifier(ctx,"entity_id").toString());
                            return change(ctx.getSource(), c -> {
                                if (c.variants.remove(id) == null) throw new IllegalArgumentException("Type is not in the wave: " + id);
                            });
                        })));
        root.then(variants);
        var spawn = literal("spawn").executes(ctx -> say(ctx.getSource(), "Wave every " + ZppMod.config().intervalSeconds
                + " seconds; distance " + ZppMod.config().minDistance + ".." + ZppMod.config().maxDistance
                + "; block light ≤ " + ZppMod.config().maxBlockLight + "; graceDays=" + ZppMod.config().graceDays));
        spawn.then(number("interval", 2, 3600, c -> c.intervalSeconds, (c,v) -> c.intervalSeconds = v));
        spawn.then(number("cap", 1, 256, c -> c.nearbyCap, (c,v) -> c.nearbyCap = v));
        spawn.then(number("globalcap", 1, 4096, c -> c.globalCap, (c,v) -> c.globalCap = v));
        spawn.then(number("attempts", 1, 32, c -> c.attempts, (c,v) -> c.attempts = v));
        spawn.then(number("maxlight", 0, 15, c -> c.maxBlockLight, (c,v) -> c.maxBlockLight = v));
        spawn.then(number("grace", 0, 365, c -> c.graceDays, (c,v) -> c.graceDays = v));
        spawn.then(number("cooldown", 0, 3600, c -> c.deathCooldownSeconds, (c,v) -> c.deathCooldownSeconds = v));
        spawn.then(toggle("opensky", c -> c.openSky, (c,v) -> c.openSky = v));
        spawn.then(literal("distance").executes(ctx -> say(ctx.getSource(), ZppMod.config().minDistance + ".." + ZppMod.config().maxDistance))
                .then(argument("min", IntegerArgumentType.integer(8, 96)).requires(s -> s.hasPermissionLevel(2))
                        .then(argument("max", IntegerArgumentType.integer(9, 112)).executes(ctx -> change(ctx.getSource(), c -> {
                            c.minDistance = IntegerArgumentType.getInteger(ctx, "min"); c.maxDistance = IntegerArgumentType.getInteger(ctx, "max");
                        })))));
        for (String type : ZppConfig.defaultWeights().keySet()) {
            spawn.then(literal(type).requires(s -> s.hasPermissionLevel(2))
                    .then(argument("amount", IntegerArgumentType.integer(1,64))
                            .executes(ctx -> manual(ctx.getSource(), ctx.getSource().getPlayerOrThrow(), type, IntegerArgumentType.getInteger(ctx,"amount")))
                            .then(argument("player", EntityArgumentType.player())
                                    .executes(ctx -> manual(ctx.getSource(), EntityArgumentType.getPlayer(ctx,"player"), type, IntegerArgumentType.getInteger(ctx,"amount"))))));
        }
        spawn.then(argument("entity_id", IdentifierArgumentType.identifier()).requires(s -> s.hasPermissionLevel(2))
                .suggests((ctx,builder) -> { ZppMod.config().variants.keySet().forEach(builder::suggest); return builder.buildFuture(); })
                .then(argument("amount", IntegerArgumentType.integer(1,64))
                        .executes(ctx -> manualMob(ctx.getSource(), ctx.getSource().getPlayerOrThrow(),
                                IdentifierArgumentType.getIdentifier(ctx,"entity_id").toString(), IntegerArgumentType.getInteger(ctx,"amount")))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> manualMob(ctx.getSource(), EntityArgumentType.getPlayer(ctx,"player"),
                                        IdentifierArgumentType.getIdentifier(ctx,"entity_id").toString(), IntegerArgumentType.getInteger(ctx,"amount"))))));
        root.then(spawn);
        root.then(toggle("scaling", c -> c.scaling, (c,v) -> c.scaling = v)
                .then(number("days",1,3650,c -> c.scalingDays,(c,v) -> c.scalingDays=v))
                .then(toggle("natural",c -> c.scaleNatural,(c,v) -> c.scaleNatural=v))
                .then(decimal("health",10,c -> c.maxHealthBonus,(c,v) -> c.maxHealthBonus=v))
                .then(decimal("damage",5,c -> c.maxDamageBonus,(c,v) -> c.maxDamageBonus=v))
                .then(decimal("speed",1,c -> c.maxSpeedBonus,(c,v) -> c.maxSpeedBonus=v)));
        root.then(literal("horde").executes(ctx -> say(ctx.getSource(), "Horde: " + ZppMod.ENGINE.horde(ctx.getSource().getServer())))
                .then(toggle("enabled",c -> c.hordes,(c,v) -> c.hordes=v))
                .then(number("every",1,365,c -> c.hordeEveryDays,(c,v) -> c.hordeEveryDays=v))
                .then(number("duration",10,3600,c -> c.hordeDurationSeconds,(c,v) -> c.hordeDurationSeconds=v))
                .then(number("multiplier",1,8,c -> c.hordeMultiplier,(c,v) -> c.hordeMultiplier=v))
                .then(literal("start").requires(s -> s.hasPermissionLevel(2))
                        .executes(ctx -> horde(ctx.getSource(), ZppMod.config().hordeDurationSeconds))
                        .then(argument("seconds", IntegerArgumentType.integer(10,3600))
                                .executes(ctx -> horde(ctx.getSource(), IntegerArgumentType.getInteger(ctx,"seconds")))))
                .then(literal("stop").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                    var s = ApocalypseState.get(ctx.getSource().getServer()); s.hordeUntil = 0; s.markDirty();
                    return say(ctx.getSource(), "Horde stopped.");
                })));
        root.then(literal("bloodmoon").executes(ctx -> say(ctx.getSource(), "Blood moon: " + ZppMod.ENGINE.bloodmoon(ctx.getSource().getServer())))
                .then(toggle("enabled",c -> c.bloodmoons,(c,v) -> c.bloodmoons=v))
                .then(number("chance",0,100,c -> c.bloodmoonChance,(c,v) -> c.bloodmoonChance=v))
                .then(number("multiplier",1,8,c -> c.bloodmoonMultiplier,(c,v) -> c.bloodmoonMultiplier=v))
                .then(literal("start").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> bloodmoon(ctx.getSource(), true)))
                .then(literal("stop").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> bloodmoon(ctx.getSource(), false))));
        root.then(literal("day").executes(ctx -> say(ctx.getSource(), "Day " + ApocalypseMath.day(ctx.getSource().getServer().getOverworld().getTimeOfDay()) + " (counting from 1)")));
        root.then(literal("stats").executes(ctx -> {
            var s = ApocalypseState.get(ctx.getSource().getServer());
            String personal = ctx.getSource().getEntity() instanceof ServerPlayerEntity p ? "; your kills: " + s.kills.getOrDefault(p.getUuid(),0L) : "";
            return say(ctx.getSource(), "Created by ZPP: " + s.spawned + "; tracked mob kills: " + s.kills.values().stream().mapToLong(Long::longValue).sum() + personal);
        }).then(argument("player", EntityArgumentType.player()).executes(ctx -> {
            var p = EntityArgumentType.getPlayer(ctx,"player");
            return say(ctx.getSource(), p.getName().getString() + ": " + ApocalypseState.get(ctx.getSource().getServer()).kills.getOrDefault(p.getUuid(),0L));
        })));
        root.then(literal("config").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> say(ctx.getSource(), ZppMod.STORE.path().toAbsolutePath().toString())));
        root.then(literal("reload").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
            try { ZppMod.STORE.load(c -> c.validateMobTypes(ctx.getSource().getServer().getOverworld())); ZppMod.ENGINE.refresh(ctx.getSource().getServer()); return say(ctx.getSource(), "Configuration reloaded." + (ZppMod.config().enabled ? "" : " ZPP is OFF; these settings will take effect after /zpp enabled true.")); }
            catch (Exception ex) { ctx.getSource().sendError(Text.literal("[ZPP] Error; previous settings remain in memory: " + ex.getMessage())); return 0; }
        }));
        var presets = literal("preset").executes(ctx -> say(ctx.getSource(), "Built-in: casual, standard, hardcore; saved: " + ZppMod.PRESETS.names()));
        presets.then(literal("list").executes(ctx -> say(ctx.getSource(), "Built-in: casual, standard, hardcore; saved: " + ZppMod.PRESETS.names())));
        presets.then(literal("save").requires(s -> s.hasPermissionLevel(2))
                .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                    String name = StringArgumentType.getString(ctx,"name");
                    try { ZppMod.PRESETS.save(name, ZppMod.STORE.copy()); return say(ctx.getSource(), "Saved global preset '" + name + "' to " + ZppMod.PRESETS.path()); }
                    catch (Exception ex) { ctx.getSource().sendError(Text.literal("[ZPP] Preset not saved: " + ex.getMessage())); return 0; }
                })));
        presets.then(literal("load").requires(s -> s.hasPermissionLevel(2))
                .then(argument("name", StringArgumentType.word())
                        .suggests((ctx,builder) -> { ZppMod.PRESETS.names().forEach(builder::suggest); return builder.buildFuture(); })
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx,"name");
                            var saved = ZppMod.PRESETS.get(name);
                            if (saved == null) { ctx.getSource().sendError(Text.literal("[ZPP] Unknown saved preset: " + name)); return 0; }
                            return change(ctx.getSource(), c -> copySettings(saved, c));
                        })));
        presets.then(literal("delete").requires(s -> s.hasPermissionLevel(2))
                .then(argument("name", StringArgumentType.word())
                        .suggests((ctx,builder) -> { ZppMod.PRESETS.names().forEach(builder::suggest); return builder.buildFuture(); })
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx,"name");
                            try { return ZppMod.PRESETS.delete(name) ? say(ctx.getSource(), "Deleted global preset '" + name + "'.") : say(ctx.getSource(), "No saved preset named '" + name + "'."); }
                            catch (Exception ex) { ctx.getSource().sendError(Text.literal("[ZPP] Preset not deleted: " + ex.getMessage())); return 0; }
                        })));
        for (String preset : new String[]{"casual","standard","hardcore"}) presets.then(literal(preset).requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> change(ctx.getSource(), c -> {
                    c.dayAmount = 0; c.minDistance=24; c.maxDistance=48; c.attempts=12; c.openSky=true;
                    c.nightAmount = preset.equals("hardcore") ? 4 : preset.equals("casual") ? 1 : 2;
                    c.intervalSeconds = preset.equals("hardcore") ? 8 : preset.equals("casual") ? 25 : 15;
                    c.nearbyCap = preset.equals("hardcore") ? 48 : preset.equals("casual") ? 16 : 32;
                    c.babies=preset.equals("hardcore"); c.babyChance=5; c.dayburn=false;
                    c.scaling=true; c.scalingDays=preset.equals("hardcore") ? 15 : preset.equals("casual") ? 60 : 30;
                    c.maxHealthBonus=1.5; c.maxDamageBonus=0.75; c.maxSpeedBonus=0.2; c.scaleNatural=true;
                    c.maxBlockLight = preset.equals("hardcore") ? 15 : 7;
                    c.graceDays = preset.equals("casual") ? 3 : 0;
                    c.hordes=!preset.equals("casual"); c.bloodmoons=!preset.equals("casual");
                    c.hordeEveryDays=7; c.hordeDurationSeconds=120; c.hordeMultiplier=3;
                    c.bloodmoonChance=15; c.bloodmoonMultiplier=2;
                })));
        root.then(presets);
        dispatcher.register(root);
    }
    private static int status(ServerCommandSource source) {
        var c = ZppMod.config(); var server = source.getServer();
        return say(source, "Zombie Apocalipse++\nDay " + ApocalypseMath.day(server.getOverworld().getTimeOfDay())
                + " • enabled=" + c.enabled + " • dayburn=" + c.dayburn + " • babies=" + c.babies
                + "\nWaves: day " + c.dayAmount + ", night " + c.nightAmount + ", interval " + c.intervalSeconds + " seconds; grace days: " + c.graceDays
                + "\nNearby cap: " + c.nearbyCap + "; ZPP loaded: " + ZppMod.ENGINE.managedCount() + "/" + c.globalCap
                + "\nHorde: " + ZppMod.ENGINE.horde(server) + "; blood moon: " + ZppMod.ENGINE.bloodmoon(server)
                + "\nTypes: " + c.variants + (c.enabled ? "" : "\nZPP is OFF. Enable it with /zpp toggle or /zpp enabled true.")
                + "\n/zpp help • Changes: OP level 2");
    }
    private static int manual(ServerCommandSource source, ServerPlayerEntity player, String type, int count) {
        int spawned = ZppMod.ENGINE.spawn(player,type,count,true);
        say(source, "Spawned " + spawned + "/" + count + " (" + type + "). If 0: check enabled, Overworld, difficulty, doMobSpawning, light, distance, and caps.");
        return spawned;
    }
    private static int showVariants(ServerCommandSource source) {
        return say(source, "Type weights: " + ZppMod.config().variants);
    }
    private static int manualMob(ServerCommandSource source, ServerPlayerEntity player, String raw, int count) {
        String type = ZombieVariants.key(raw);
        if (!ZppMod.config().variants.containsKey(type)) {
            source.sendError(Text.literal("[ZPP] Add this mob first with /zpp variants add " + type + " <weight>")); return 0;
        }
        return manual(source, player, type, count);
    }
    private static void copySettings(ZppConfig from, ZppConfig to) {
        to.enabled=from.enabled; to.dayburn=from.dayburn; to.dayAmount=from.dayAmount; to.nightAmount=from.nightAmount;
        to.intervalSeconds=from.intervalSeconds; to.nearbyCap=from.nearbyCap; to.globalCap=from.globalCap;
        to.minDistance=from.minDistance; to.maxDistance=from.maxDistance; to.attempts=from.attempts;
        to.maxBlockLight=from.maxBlockLight; to.openSky=from.openSky; to.babies=from.babies; to.babyChance=from.babyChance;
        to.scaleNatural=from.scaleNatural; to.scaling=from.scaling; to.scalingDays=from.scalingDays;
        to.maxHealthBonus=from.maxHealthBonus; to.maxDamageBonus=from.maxDamageBonus; to.maxSpeedBonus=from.maxSpeedBonus;
        to.hordes=from.hordes; to.hordeEveryDays=from.hordeEveryDays; to.hordeDurationSeconds=from.hordeDurationSeconds;
        to.hordeMultiplier=from.hordeMultiplier; to.bloodmoons=from.bloodmoons; to.bloodmoonChance=from.bloodmoonChance;
        to.bloodmoonMultiplier=from.bloodmoonMultiplier; to.graceDays=from.graceDays; to.deathCooldownSeconds=from.deathCooldownSeconds;
        to.announcements=from.announcements; to.variants=new java.util.LinkedHashMap<>(from.variants);
    }
    private static int horde(ServerCommandSource source, int seconds) {
        if (!ZppMod.config().enabled || !ZppMod.config().hordes) { source.sendError(Text.literal("Enable /zpp enabled true and /zpp horde enabled true first")); return 0; }
        var s = ApocalypseState.get(source.getServer());
        s.lastDay = ApocalypseMath.day(source.getServer().getOverworld().getTimeOfDay());
        s.hordeUntil=source.getServer().getOverworld().getTime()+seconds*20L; s.markDirty();
        ZppMod.ENGINE.announce(source.getServer(),"A horde is approaching! " + seconds + " seconds.");
        return say(source,"Horde started. It only intensifies enabled day/night waves.");
    }
    private static int bloodmoon(ServerCommandSource source, boolean start) {
        long time = source.getServer().getOverworld().getTimeOfDay();
        if (start && (!ZppMod.config().enabled || !ZppMod.config().bloodmoons || !ApocalypseMath.night(time))) {
            source.sendError(Text.literal("Requires enabled=true, bloodmoon enabled=true, and night (13000..22999).")); return 0;
        }
        var s = ApocalypseState.get(source.getServer());
        s.lastDay = ApocalypseMath.day(time); s.lastNight = ApocalypseMath.day(time);
        s.bloodmoonDay=start ? ApocalypseMath.day(time) : -1; s.markDirty();
        ZppMod.ENGINE.announce(source.getServer(), start ? "The blood moon has begun!" : "The blood moon has stopped.");
        return say(source,"Blood moon: " + start);
    }
}
