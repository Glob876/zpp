package dev.zpp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private ZppConfig config = new ZppConfig();
    public ConfigStore(Path path) { this.path = path; }
    public ZppConfig get() { return config; }
    public Path path() { return path; }
    public ZppConfig copy() { return GSON.fromJson(GSON.toJson(config), ZppConfig.class); }
    public void load() throws IOException { load(c -> {}); }
    public void load(Consumer<ZppConfig> extraValidation) throws IOException {
        if (!Files.exists(path)) { replace(new ZppConfig()); return; }
        ZppConfig candidate = GSON.fromJson(Files.readString(path), ZppConfig.class);
        if (candidate == null) throw new IllegalArgumentException("Configuration is empty");
        candidate.validate();
        extraValidation.accept(candidate);
        config = candidate;
    }
    public void replace(ZppConfig candidate) throws IOException {
        candidate.validate();
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "zpp-", ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(candidate) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
        config = candidate;
    }
}
