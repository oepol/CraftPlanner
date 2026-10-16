package dev.ole.craftplanner.core;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
public final class PlanProfiles {
    public static final class Data {
        public List<CraftGoal> goals=new ArrayList<>();
        public Map<String,String> recipes=new HashMap<>();
        public Set<String> rawItems=new HashSet<>();
        public Map<String,ChestSource> sources=new LinkedHashMap<>();
        static Data capture(PlannerConfig c) { Data d=new Data(); d.goals=c.goals; d.recipes=c.recipes; d.rawItems=c.rawItems; d.sources=c.sources; return d; }
        void apply(PlannerConfig c) { c.goals=goals; c.recipes=recipes; c.rawItems=rawItems; c.sources=sources; }
        void sanitize() { PlannerConfig c=new PlannerConfig(); apply(c); c.sanitize(); goals=c.goals; recipes=c.recipes; rawItems=c.rawItems; sources=c.sources; }
    }
    private final PlannerConfig config;
    private String active;
    public PlanProfiles(PlannerConfig c) {
        config=c;
        if(c.schema<2 && (!c.goals.isEmpty() || !c.recipes.isEmpty() || !c.rawItems.isEmpty())) c.legacyPlan=Data.capture(c);
        c.schema=2; c.profiles.entrySet().removeIf(e->e.getKey()==null || e.getValue()==null);
        c.profiles.values().forEach(Data::sanitize); if(c.legacyPlan!=null)c.legacyPlan.sanitize();
        new Data().apply(c);
    }
    public void activate(String key) { if(Objects.equals(active,key))return; active=key; (key==null ? new Data() : config.profiles.computeIfAbsent(key,k->new Data())).apply(config); }
    public boolean active() { return active!=null; }
    public boolean canImport() { return active() && config.legacyPlan!=null; }
    public void importLegacy() {
        if(!canImport())return;
        for(CraftGoal g:config.legacyPlan.goals) { g.pinned=Pins.count(config.goals)<config.pinLimit; config.goals.add(g); }
        config.legacyPlan.recipes.forEach(config.recipes::putIfAbsent); config.rawItems.addAll(config.legacyPlan.rawItems); config.legacyPlan=null; config.sanitize();
    }
    public PlannerConfig forStorage(PlannerConfig settings) { PlannerConfig c=new PlannerConfig(); c.schema=2; c.copySettingsFrom(settings); c.profiles=config.profiles; c.legacyPlan=config.legacyPlan; return c; }
    public static String key(String identity) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8))); } catch(NoSuchAlgorithmException e) { throw new AssertionError(e); } }
}
