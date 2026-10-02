package dev.zpp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZppMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("zpp");
    public static ConfigStore STORE;
    public static PresetStore PRESETS;
    public static final ApocalypseEngine ENGINE = new ApocalypseEngine();
    private static final ZppConfig DEFAULT_CONFIG = new ZppConfig();
    public static ZppConfig config() { return STORE == null ? DEFAULT_CONFIG : STORE.get(); }
    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> ZppCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ENGINE.reset();
            STORE = new ConfigStore(server.getSavePath(WorldSavePath.ROOT).resolve("zpp.json"));
            PRESETS = new PresetStore(FabricLoader.getInstance().getConfigDir().resolve("zpp-presets.json"));
            try { STORE.load(c -> c.validateMobTypes(server.getOverworld())); PRESETS.load(); }
            catch (Exception e) { throw new IllegalStateException("Invalid ZPP config: " + STORE.path() + ". Fix the file; it has not been overwritten.", e); }
            LOGGER.info("Zombie Apocalipse++ loaded; config: {}", STORE.path());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { ENGINE.reset(); STORE = null; PRESETS = null; });
        ServerTickEvents.END_SERVER_TICK.register(ENGINE::tick);
        ServerEntityEvents.ENTITY_LOAD.register(ENGINE::loaded);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> ENGINE.unloaded(entity));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ENGINE.disconnected(handler.player));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damage) -> {
            if (!config().enabled) return;
            if (entity instanceof ServerPlayerEntity player) ENGINE.died(player);
            if (entity instanceof MobEntity mob && (mob.getCommandTags().contains(ZombieVariants.MANAGED)
                    || ZombieVariants.configured(mob, config()))
                    && damage.getAttacker() instanceof ServerPlayerEntity player) {
                var state = ApocalypseState.get(player.getServer());
                long kills = state.kills.merge(player.getUuid(), 1L, Long::sum); state.markDirty();
                if (kills == 100 || kills == 500 || kills == 1000 || kills % 5000 == 0)
                    player.sendMessage(Text.literal("[ZPP] Survival milestone: " + kills + " mobs killed!"), false);
            }
        });
    }
}
