package dev.zpp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {
    @TempDir Path dir;
    @Test void defaultsHaveNoDaySpawnsAndFourVanillaTypes() {
        var c = new ZppConfig(); c.validate();
        assertFalse(c.enabled); assertEquals(0,c.dayAmount); assertFalse(c.babies);
        assertEquals(java.util.Set.of("zombie","husk","drowned","zombie_villager"),c.variants.keySet());
    }
    @Test void writesRoundTripAndUsesDefaultsForMissingFields() throws Exception {
        var path=dir.resolve("zpp.json"); var store=new ConfigStore(path); store.load();
        var changed=store.copy(); changed.dayAmount=4; changed.variants.put("husk",0); store.replace(changed);
        var reloaded=new ConfigStore(path); reloaded.load(); assertEquals(4,reloaded.get().dayAmount);
        assertEquals(0,reloaded.get().variants.get("husk"));
        Files.writeString(path,"{\"dayAmount\":3}"); reloaded.load();
        assertEquals(3,reloaded.get().dayAmount); assertEquals(15,reloaded.get().intervalSeconds);
    }
    @Test void invalidEditCannotChangeMemoryOrDisk() throws Exception {
        var path=dir.resolve("zpp.json"); var store=new ConfigStore(path); store.load();
        String before=Files.readString(path); var bad=store.copy(); bad.minDistance=60;
        assertThrows(IllegalArgumentException.class,() -> store.replace(bad));
        assertEquals(before,Files.readString(path)); assertEquals(24,store.get().minDistance);
    }
    @Test void brokenReloadRetainsLiveSettingsAndOriginalFile() throws Exception {
        var path=dir.resolve("zpp.json"); var store=new ConfigStore(path); store.load();
        Files.writeString(path,"{\"variants\":null}");
        assertThrows(IllegalArgumentException.class,store::load); assertNotNull(store.get().variants);
        assertEquals("{\"variants\":null}",Files.readString(path));
        Files.writeString(path,"{"); assertThrows(RuntimeException.class,store::load);
        assertEquals(0,store.get().dayAmount);
    }
    @Test void rejectsZeroWeightsAndUnboundedValues() {
        var c=new ZppConfig(); c.variants.replaceAll((k,v) -> 0);
        assertThrows(IllegalArgumentException.class,c::validate);
        c.variants=ZppConfig.defaultWeights(); c.maxHealthBonus=Double.NaN;
        assertThrows(IllegalArgumentException.class,c::validate);
        c.maxHealthBonus=1; c.dayAmount=65; assertThrows(IllegalArgumentException.class,c::validate);
    }
}
