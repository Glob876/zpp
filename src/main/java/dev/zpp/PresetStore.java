package dev.zpp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** Named snapshots shared by every world in this Fabric installation. */
public final class PresetStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, ZppConfig>>() {}.getType();
    private static final Set<String> BUILTINS = Set.of("casual", "standard", "hardcore");
    private final Path path;
    private Map<String, ZppConfig> presets = new LinkedHashMap<>();
    public PresetStore(Path path) { this.path = path; }
    public Path path() { return path; }
    public Set<String> names() { return Collections.unmodifiableSet(new LinkedHashSet<>(presets.keySet())); }
    public ZppConfig get(String name) {
        ZppConfig value = presets.get(name);
        return value == null ? null : GSON.fromJson(GSON.toJson(value), ZppConfig.class);
    }
    public void load() throws IOException {
        if (!Files.exists(path)) { presets = new LinkedHashMap<>(); return; }
        Map<String, ZppConfig> candidate = GSON.fromJson(Files.readString(path), TYPE);
        if (candidate == null) throw new IllegalArgumentException("Preset file is empty");
        for (var entry : candidate.entrySet()) {
            checkName(entry.getKey());
            if (entry.getValue() == null) throw new IllegalArgumentException("Empty preset: " + entry.getKey());
            entry.getValue().validate();
        }
        presets = candidate;
    }
    public void save(String name, ZppConfig config) throws IOException {
        checkName(name); config.validate();
        var candidate = new LinkedHashMap<>(presets);
        candidate.put(name, GSON.fromJson(GSON.toJson(config), ZppConfig.class));
        write(candidate);
    }
    public boolean delete(String name) throws IOException {
        checkName(name);
        if (!presets.containsKey(name)) return false;
        var candidate = new LinkedHashMap<>(presets); candidate.remove(name); write(candidate); return true;
    }
    private static void checkName(String name) {
        if (name == null || !name.matches("[a-z0-9_-]{1,32}") || BUILTINS.contains(name))
            throw new IllegalArgumentException("Use 1..32 lowercase letters, digits, _ or -; built-in names are reserved");
    }
    private void write(Map<String, ZppConfig> candidate) throws IOException {
        Path parent = path.toAbsolutePath().getParent(); Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "zpp-presets-", ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(candidate, TYPE) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
        presets = candidate;
    }
}
