package dev.ole.craftplanner.core;

import java.util.*;

public final class PlannerConfig {
    public int schema = 1;
    public Map<String,PlanProfiles.Data> profiles=new HashMap<>();
    public PlanProfiles.Data legacyPlan;
    public Map<String,ChestSource> sources=new LinkedHashMap<>();
    public boolean includeShulkers=true;
    public int pinLimit=3;
    public List<CraftGoal> goals = new ArrayList<>();
    public Map<String,String> recipes = new HashMap<>();
    public Set<String> rawItems = new HashSet<>();
    public boolean enabled = true, hideCompleteMaterials = false, showMaterials = true, notifyComplete = true;
    public int x = 14, y = 18, width = 224, maxHeight = 340;
    public double scale = 1, opacity = .82;
    public String sort = "missing";
    public PlannerConfig settingsCopy() { PlannerConfig c=new PlannerConfig(); c.copySettingsFrom(this); return c; }
    public void copySettingsFrom(PlannerConfig c) {
        enabled=c.enabled; hideCompleteMaterials=c.hideCompleteMaterials; showMaterials=c.showMaterials; notifyComplete=c.notifyComplete;
        x=c.x; y=c.y; width=c.width; maxHeight=c.maxHeight; scale=c.scale; opacity=c.opacity; sort=c.sort; includeShulkers=c.includeShulkers; pinLimit=c.pinLimit;
    }
    public void sanitize() {
        if(profiles==null)profiles=new HashMap<>();
        if(sources==null)sources=new LinkedHashMap<>();
        sources.entrySet().removeIf(e->e.getKey()==null || e.getValue()==null || !e.getValue().valid());
        sources.values().forEach(ChestSource::sanitize);
        pinLimit=Math.clamp(pinLimit,1,12);
        if (goals == null) goals = new ArrayList<>();
        if (recipes == null) recipes = new HashMap<>();
        if (rawItems == null) rawItems = new HashSet<>();
        Set<String> ids = new HashSet<>();
        goals.removeIf(g -> g == null || g.item == null || !g.item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || g.count <= 0);
        for (CraftGoal goal : goals) {
            if (goal.id == null || !ids.add(goal.id)) { goal.id = UUID.randomUUID().toString(); ids.add(goal.id); }
            goal.count = Math.min(1_000_000,goal.count);
            goal.delivered=Math.clamp(goal.delivered,0,goal.count);
            if(goal.deliveredByChest==null)goal.deliveredByChest=new HashMap<>();
            goal.deliveredByChest.entrySet().removeIf(e->e.getKey()==null || e.getValue()==null || e.getValue()<=0);
            if(goal.archived)goal.pinned=false;
        }
        recipes.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        rawItems.remove(null);
        x = Math.clamp(x,0,10000); y = Math.clamp(y,0,10000);
        width = Math.clamp(width,180,480); maxHeight = Math.clamp(maxHeight,100,900);
        scale = Double.isFinite(scale) ? Math.clamp(scale,.6,2) : 1;
        opacity = Double.isFinite(opacity) ? Math.clamp(opacity,.15,1) : .82;
        if (!Set.of("missing","name","progress").contains(sort == null ? "" : sort)) sort="missing";
    }
}
