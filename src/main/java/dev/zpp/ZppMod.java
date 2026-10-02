package dev.zpp;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("zpp")
public final class ZppMod {
    public static final Logger LOGGER = LoggerFactory.getLogger("zpp");
    public static ConfigStore STORE;
    public static PresetStore PRESETS;
    public static final ApocalypseEngine ENGINE = new ApocalypseEngine();
    private static final ZppConfig DEFAULT_CONFIG = new ZppConfig();
    public static ZppConfig config() { return STORE == null ? DEFAULT_CONFIG : STORE.get(); }

    public ZppMod() {
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::starting);
        NeoForge.EVENT_BUS.addListener(this::stopped);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::loaded);
        NeoForge.EVENT_BUS.addListener(this::unloaded);
        NeoForge.EVENT_BUS.addListener(this::disconnected);
        NeoForge.EVENT_BUS.addListener(this::death);
    }
    private void commands(RegisterCommandsEvent event) { ZppCommands.register(event.getDispatcher()); }
    private void starting(ServerStartingEvent event) {
        var server = event.getServer();
        ENGINE.reset();
        STORE = new ConfigStore(server.getWorldPath(LevelResource.ROOT).resolve("zpp.json"));
        PRESETS = new PresetStore(FMLPaths.CONFIGDIR.get().resolve("zpp-presets.json"));
        try { STORE.load(c -> c.validateMobTypes(server.overworld())); PRESETS.load(); }
        catch (Exception e) { throw new IllegalStateException("Invalid ZPP config: " + STORE.path() + ". Fix the file; it has not been overwritten.", e); }
        ENGINE.refresh(server);
        LOGGER.info("Zombie Apocalipse++ loaded; config: {}", STORE.path());
    }
    private void stopped(ServerStoppedEvent event) { ENGINE.reset(); STORE = null; PRESETS = null; }
    private void tick(ServerTickEvent.Post event) { ENGINE.tick(event.getServer()); }
    private void loaded(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) ENGINE.loaded(event.getEntity(), level);
    }
    private void unloaded(EntityLeaveLevelEvent event) { ENGINE.unloaded(event.getEntity()); }
    private void disconnected(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ENGINE.disconnected(player);
    }
    private void death(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !config().enabled) return;
        if (event.getEntity() instanceof ServerPlayer player) ENGINE.died(player);
        if (event.getEntity() instanceof Mob mob && (mob.getTags().contains(ZombieVariants.MANAGED)
                || ZombieVariants.configured(mob, config()))
                && event.getSource().getEntity() instanceof ServerPlayer player) {
            var state = ApocalypseState.get(player.getServer());
            long kills = state.kills.merge(player.getUUID(), 1L, Long::sum); state.setDirty();
            if (kills == 100 || kills == 500 || kills == 1000 || kills % 5000 == 0)
                player.sendSystemMessage(Component.literal("[ZPP] Survival milestone: " + kills + " mobs killed!"));
        }
    }
}
