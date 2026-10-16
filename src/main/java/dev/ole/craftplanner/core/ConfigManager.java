package dev.ole.craftplanner.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;

public final class ConfigManager {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private final Consumer<String> log;
    private boolean warned;
    public ConfigManager(Path path,Consumer<String> log) { this.path=path; this.log=log; }
    public PlannerConfig load() {
        if (!Files.exists(path)) return new PlannerConfig();
        try {
            PlannerConfig cfg=JSON.fromJson(Files.readString(path),PlannerConfig.class);
            if (cfg==null) throw new IllegalArgumentException("Leere Konfiguration");
            cfg.sanitize(); return cfg;
        } catch (Exception e) {
            warn("Ungültige Config, Standardwerte werden geladen: "+e.getMessage());
            try { Files.copy(path,path.resolveSibling(path.getFileName()+".broken-"+System.currentTimeMillis()),StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException backup) { warn("Config-Sicherung fehlgeschlagen: "+backup.getMessage()); }
            return new PlannerConfig();
        }
    }
    public boolean save(PlannerConfig cfg) {
        try {
            cfg.sanitize(); Files.createDirectories(path.toAbsolutePath().getParent());
            Path tmp=path.resolveSibling(path.getFileName()+".tmp");
            Files.writeString(tmp,JSON.toJson(cfg),StandardCharsets.UTF_8);
            try { Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING); }
            warned=false; return true;
        } catch (Exception e) { warn("Config konnte nicht gespeichert werden: "+e.getMessage()); return false; }
    }
    private void warn(String message) { if (!warned) { log.accept(message); warned=true; } }
}
