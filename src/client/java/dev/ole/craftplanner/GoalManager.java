package dev.ole.craftplanner;

import dev.ole.craftplanner.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class GoalManager {
    private final CraftPlannerClient app;
    private RecipeResolver.Plan plan = RecipeResolver.Plan.empty();
    private List<MaterialRequirement> materialRows = List.of();
    private RecipeResolver.Plan pinned=RecipeResolver.Plan.empty();
    public RecipeResolver.Plan pinnedPlan() { return pinned; }
    public List<MaterialRequirement> gathering() {
        Comparator<MaterialRequirement> order=switch(app.config.sort) {
            case "name" -> Comparator.comparing(m->ItemNames.name(m.item()),String.CASE_INSENSITIVE_ORDER);
            case "progress" -> Comparator.comparingDouble(m->m.needed()==0?1:(double)m.owned()/m.needed());
            default -> Comparator.comparingLong(MaterialRequirement::missing).reversed();
        };
        return pinned.materials().stream().filter(m->m.missing()>0).sorted(order.thenComparing(MaterialRequirement::item)).toList();
    }
    public boolean observe(StockTotals before,StockTotals after,Map<String,Long> deposit) {
        var a=new StockTotals(before.direct(),before.shulkers(),Map.of()).total();
        var b=new StockTotals(after.direct(),after.shulkers(),Map.of()).total();
        boolean change=CompletionLedger.transferred(app.config.goals,a,b,deposit,app.sources.openedKey());
        change|=CompletionLedger.observe(app.config.goals,b);
        if(change)changed(); return change;
    }
    public void togglePin(CraftGoal goal) {
        if(!Pins.toggle(goal,app.config.goals,app.config.pinLimit)) {
            if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.sendOverlayMessage(Lang.text("craftplanner.pin_full"));
        } else { if(goal.pinned && goal.archived) { goal.archived=false; goal.achieved=false; goal.delivered=0; goal.deliveredByChest.clear(); } changed(); }
    }
    private final Set<String> completed=new HashSet<>();
    private boolean dirty=true;
    public GoalManager(CraftPlannerClient app) { this.app=app; }
    public RecipeResolver.Plan plan() { return plan; }
    public List<MaterialRequirement> rows() { return materialRows; }
    public void changed() { dirty=true; app.save(); }
    public void invalidate() { dirty=true; }
    public void clear() { plan=RecipeResolver.Plan.empty(); pinned=RecipeResolver.Plan.empty(); materialRows=List.of(); completed.clear(); dirty=true; }
    public void recalculate() {
        if (!dirty) return;
        dirty=false;
        plan=new RecipeResolver(app.catalog.entries(),app.config.recipes,app.config.rawItems)
            .resolve(app.config.goals.stream().sorted(Comparator.comparing((CraftGoal g)->!g.pinned)).toList(),app.inventory.counts());
        pinned=new RecipeResolver(app.catalog.entries(),app.config.recipes,app.config.rawItems).resolve(app.config.goals.stream().filter(g->g.pinned).toList(),app.inventory.counts());
        Comparator<MaterialRequirement> order=switch(app.config.sort) {
            case "name" -> Comparator.comparing(m -> ItemNames.name(m.item()),String.CASE_INSENSITIVE_ORDER);
            case "progress" -> Comparator.comparingDouble(m -> m.needed()==0 ? 1 : (double)m.owned()/m.needed());
            default -> Comparator.comparingLong(MaterialRequirement::missing).reversed();
        };
        materialRows=plan.materials().stream().filter(m -> !app.config.hideCompleteMaterials || m.missing()>0)
            .sorted(order.thenComparing(MaterialRequirement::item)).toList();
        Set<String> now=new HashSet<>();
        for (var gp:plan.goals()) if (gp.tree().owned()+gp.goal().delivered>=gp.goal().count) {
            now.add(gp.goal().id);
            if (!completed.contains(gp.goal().id) && app.config.notifyComplete && Minecraft.getInstance().player!=null)
                Minecraft.getInstance().player.sendOverlayMessage(Component.literal(Lang.tr("craftplanner.complete",gp.goal().count,ItemNames.name(gp.goal().item))));
        }
        completed.clear(); completed.addAll(now);
    }
    public void add(String item,long count) { CraftGoal goal=new CraftGoal(item,Math.clamp(count,1,1_000_000)); app.config.goals.add(goal); if(Pins.count(app.config.goals)<app.config.pinLimit)goal.pinned=true; else if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.sendOverlayMessage(Lang.text("craftplanner.pin_full")); changed(); }
    public void remove(CraftGoal goal) { app.config.goals.remove(goal); changed(); }
    public void clearCompleted() { app.config.goals.removeIf(g -> completed.contains(g.id) || g.archived); changed(); }
    public long owned(CraftGoal goal) { return goal.delivered+plan.goals().stream().filter(g -> g.goal().id.equals(goal.id)).mapToLong(g -> g.tree().owned()).findFirst().orElse(0); }
}
