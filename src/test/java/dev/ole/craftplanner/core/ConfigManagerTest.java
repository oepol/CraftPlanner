package dev.ole.craftplanner.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
    @TempDir Path temp;
    @Test void goalsAndSettingsSurviveReload() {
        var manager=new ConfigManager(temp.resolve("craftplanner.json"),s -> {});
        var cfg=manager.load(); cfg.goals.add(new CraftGoal("minecraft:piston",50)); cfg.x=90; cfg.enabled=false; cfg.scale=1.25; cfg.recipes.put("minecraft:piston","choice");
        manager.save(cfg); var restored=manager.load();
        assertEquals(50,restored.goals.getFirst().count); assertEquals(cfg.goals.getFirst().id,restored.goals.getFirst().id);
        assertEquals(90,restored.x); assertFalse(restored.enabled); assertEquals(1.25,restored.scale); assertEquals("choice",restored.recipes.get("minecraft:piston"));
    }
    @Test void brokenJsonLoadsDefaultsAndPreservesBackup() throws Exception {
        Path file=temp.resolve("craftplanner.json"); Files.writeString(file,"{not json"); List<String> warnings=new ArrayList<>();
        var cfg=new ConfigManager(file,warnings::add).load(); assertTrue(cfg.goals.isEmpty()); assertTrue(cfg.enabled); assertEquals(1,warnings.size());
        try(var paths=Files.list(temp)) { assertEquals(2,paths.count()); }
    }
    @Test void invalidFieldsAndNullCollectionsAreSanitized() throws Exception {
        Path file=temp.resolve("craftplanner.json"); Files.writeString(file,"{\"goals\":[null,{\"item\":\"invalid\",\"count\":-5}],\"recipes\":null,\"rawItems\":null,\"scale\":100,\"opacity\":-1,\"sort\":null}");
        var cfg=new ConfigManager(file,s -> {}).load(); assertTrue(cfg.goals.isEmpty()); assertNotNull(cfg.recipes); assertNotNull(cfg.rawItems); assertEquals(2,cfg.scale); assertEquals(.15,cfg.opacity);
    }
    @Test void duplicateIdsAreRepaired() {
        var cfg=new PlannerConfig(); CraftGoal a=new CraftGoal("minecraft:stone",2),b=new CraftGoal("minecraft:stone",2); b.id=a.id; cfg.goals.add(a); cfg.goals.add(b); cfg.sanitize(); assertNotEquals(a.id,b.id);
    }
}
