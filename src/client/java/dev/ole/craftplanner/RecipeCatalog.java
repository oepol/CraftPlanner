package dev.ole.craftplanner;

import dev.ole.craftplanner.core.Recipe;
import dev.ole.craftplanner.core.PendingSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.*;
import net.minecraft.util.context.ContextMap;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Uses actual recipe displays synchronized by vanilla; never ships a second recipe database. */
public final class RecipeCatalog {
    private List<Recipe> entries=List.of();
    private Object collections, access, level;
    private boolean dirty=true;
    private static final java.util.concurrent.atomic.AtomicBoolean WARNED=new java.util.concurrent.atomic.AtomicBoolean();
    private Object localServer,localManager;
    private final PendingSnapshot<Snapshot> pending=new PendingSnapshot<>();
    private record Snapshot(Object manager,List<Recipe> recipes) {}
    public List<Recipe> entries() { return entries; }
    public void invalidate() { dirty=true; pending.clear(); }
    public void clear() { pending.clear(); localServer=null; localManager=null; entries=List.of(); collections=null; access=null; level=null; dirty=true; }
    public boolean refresh(Minecraft mc) {
        if (mc.player==null || mc.level==null || mc.getConnection()==null) return false;
        if(mc.getSingleplayerServer()!=null)return refreshLocal(mc);
        Object current=mc.player.getRecipeBook().getCollections();
        var recipeAccess=mc.getConnection().recipes();
        if (!dirty && current==collections && access==recipeAccess && level==mc.level) return false;
        collections=current; access=recipeAccess; level=mc.level; dirty=false;
        Map<String,Recipe> found=new LinkedHashMap<>();
        ContextMap context=SlotDisplayContext.fromLevel(mc.level);
        if (recipeAccess instanceof RecipeManager manager) {
            for (var holder:manager.getRecipes()) for (var display:holder.value().display())
                safeRead(display,context,found);
        } else {
            for (var collection:mc.player.getRecipeBook().getCollections())
                for (var entry:collection.getRecipes()) safeRead(entry.display(),context,found);
        }
        for (var stone:recipeAccess.stonecutterRecipes().entries()) {
            safeRead(new StonecutterRecipeDisplay(stone.input().display(),stone.recipe().optionDisplay(),SlotDisplay.Empty.INSTANCE),context,found);
        }
        entries=List.copyOf(found.values());
        CraftPlannerClient.LOG.info("CraftPlanner: {} bekannte Rezeptvarianten indexiert",entries.size());
        return true;
    }
    private boolean refreshLocal(Minecraft mc) {
        var server=mc.getSingleplayerServer();
        if(server!=localServer) { clear(); localServer=server; }
        boolean changed=false;
        try {
            var ready=pending.poll();
            if(ready.isPresent()) { var snapshot=ready.get(); localManager=snapshot.manager(); if(snapshot.recipes()!=null) { entries=snapshot.recipes(); changed=true; } }
        } catch(java.util.concurrent.CompletionException | java.util.concurrent.CancellationException e) {
            dirty=true; if(WARNED.compareAndSet(false,true))CraftPlannerClient.LOG.warn("Could not read integrated-server recipes",e);
        }
        if(!pending.pending() && !server.isStopped()) {
            Object previous=localManager; boolean force=dirty; dirty=false;
            pending.request(server.submit(() -> {
                var manager=server.getRecipeManager();
                if(!force && manager==previous)return new Snapshot(manager,null);
                Map<String,Recipe> found=new LinkedHashMap<>();
                ContextMap context=SlotDisplayContext.fromLevel(server.overworld());
                for(var holder:manager.getRecipes())for(var display:holder.value().display())safeRead(display,context,found);
                return new Snapshot(manager,List.copyOf(found.values()));
            }));
        }
        return changed;
    }
    private static String kindKey(String kind) { return switch(kind) {
        case "Werkbank" -> "craftplanner.recipe.shaped";
        case "Crafting" -> "craftplanner.recipe.shapeless";
        case "Ofen / Hochofen (ohne Brennstoff)" -> "craftplanner.recipe.furnace";
        case "Steinsäge" -> "craftplanner.recipe.stonecutter";
        default -> "craftplanner.recipe.smithing";
    }; }
    private static void safeRead(RecipeDisplay display,ContextMap context,Map<String,Recipe> found) {
        try { read(display,context,found); }
        catch (RuntimeException e) {
            if(WARNED.compareAndSet(false,true))CraftPlannerClient.LOG.warn("Skipped unsupported recipe display",e);
        }
    }
    private static void read(RecipeDisplay display,ContextMap context,Map<String,Recipe> found) {
        List<SlotDisplay> slots; String kind;
        if (display instanceof ShapedCraftingRecipeDisplay d) { slots=d.ingredients(); kind="Werkbank"; }
        else if (display instanceof ShapelessCraftingRecipeDisplay d) { slots=d.ingredients(); kind="Crafting"; }
        else if (display instanceof FurnaceRecipeDisplay d) { slots=List.of(d.ingredient()); kind="Ofen / Hochofen (ohne Brennstoff)"; }
        else if (display instanceof StonecutterRecipeDisplay d) { slots=List.of(d.input()); kind="Steinsäge"; }
        else if (display instanceof SmithingRecipeDisplay d) { slots=List.of(d.template(),d.base(),d.addition()); kind="Schmiedetisch"; }
        else return;
        List<Recipe.Ingredient> inputs=new ArrayList<>();
        for (SlotDisplay slot:slots) {
            if (slot instanceof SlotDisplay.Empty) continue;
            List<ItemStack> alternatives=slot.resolveForStacks(context).stream().filter(s -> !s.isEmpty()).toList();
            if (alternatives.isEmpty() || alternatives.stream().anyMatch(s -> !s.getComponentsPatch().isEmpty())) return; // Unknown ingredients must not be silently omitted.
            inputs.add(new Recipe.Ingredient(alternatives.stream().map(s -> ItemNames.id(s.getItem())).toList(),1));
        }
        if (inputs.isEmpty()) return;
        // Component-dependent outputs cannot safely be treated as interchangeable item IDs.
        List<ItemStack> outputs=display.result().resolveForStacks(context);
        if (outputs.size()!=1 || outputs.getFirst().isEmpty() || !outputs.getFirst().getComponentsPatch().isEmpty()) return;
        ItemStack output=outputs.getFirst();
        String out=ItemNames.id(output.getItem());
        String signature=kind+"|"+out+"|"+output.getCount()+"|"+inputs;
        String id="display:"+UUID.nameUUIDFromBytes(signature.getBytes(StandardCharsets.UTF_8));
        found.putIfAbsent(id,new Recipe(id,out,output.getCount(),inputs,kindKey(kind)));
    }
}
