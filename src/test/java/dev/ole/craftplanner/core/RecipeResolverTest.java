package dev.ole.craftplanner.core;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeResolverTest {
    private Recipe recipe(String out,long count,String... inputs) {
        return new Recipe(out+Arrays.toString(inputs),out,count,Arrays.stream(inputs).map(i -> new Recipe.Ingredient(List.of(i),1)).toList(),"test");
    }
    private RecipeResolver.Plan plan(List<Recipe> recipes,List<CraftGoal> goals,Map<String,Long> stock) {
        return new RecipeResolver(recipes,Map.of(),Set.of()).resolve(goals,stock);
    }
    private Map<String,Long> missing(RecipeResolver.Plan plan) {
        Map<String,Long> values=new TreeMap<>(); plan.materials().forEach(m -> values.put(m.item(),m.missing())); return values;
    }
    @Test void simpleRecipe() {
        assertEquals(Map.of("B",3L),missing(plan(List.of(recipe("A",1,"B")),List.of(new CraftGoal("A",3)),Map.of())));
    }
    @Test void multiStage() {
        assertEquals(Map.of("C",6L),missing(plan(List.of(recipe("A",1,"B","B"),recipe("B",1,"C")),List.of(new CraftGoal("A",3)),Map.of())));
    }
    @Test void outputRoundsUp() {
        assertEquals(Map.of("log",1L),missing(plan(List.of(recipe("plank",4,"log")),List.of(new CraftGoal("plank",3)),Map.of())));
    }
    @Test void repeatedSlotsAggregateBeforeRounding() {
        var p=plan(List.of(recipe("piston",1,"plank","plank","plank"),recipe("plank",4,"log")),List.of(new CraftGoal("piston",50)),Map.of());
        assertEquals(Map.of("log",38L),missing(p));
    }
    @Test void finishedTargetsReduceDemand() {
        assertEquals(Map.of("B",30L),missing(plan(List.of(recipe("A",1,"B")),List.of(new CraftGoal("A",50)),Map.of("A",20L))));
    }
    @Test void intermediateStockReducesDemand() {
        var p=plan(List.of(recipe("piston",1,"plank","plank","plank","iron"),recipe("plank",4,"log")),List.of(new CraftGoal("piston",50)),Map.of("piston",20L,"plank",40L,"iron",10L));
        assertEquals(Map.of("log",13L,"iron",20L),missing(p));
    }
    @Test void cyclesTerminate() {
        var p=assertTimeoutPreemptively(Duration.ofSeconds(2),() -> plan(List.of(recipe("A",1,"B"),recipe("B",1,"A")),List.of(new CraftGoal("A",5)),Map.of()));
        assertFalse(p.materials().isEmpty()); assertFalse(p.warnings().isEmpty());
    }
    @Test void unknownIsRaw() {
        var p=plan(List.of(),List.of(new CraftGoal("diamond",7)),Map.of("diamond",2L));
        assertEquals(Map.of("diamond",5L),missing(p)); assertEquals(2,p.materials().getFirst().owned());
    }
    @Test void inventoryIsNotDoubleCountedAcrossGoals() {
        var p=plan(List.of(recipe("A",1,"wood"),recipe("B",1,"wood")),List.of(new CraftGoal("A",10),new CraftGoal("B",10)),Map.of("wood",12L));
        assertEquals(Map.of("wood",8L),missing(p)); assertEquals(12,p.materials().getFirst().owned());
    }
    @Test void duplicateGoalTargetsReserveOnlyOnce() {
        var p=plan(List.of(recipe("A",1,"wood")),List.of(new CraftGoal("A",5),new CraftGoal("A",5)),Map.of("A",6L));
        assertEquals(5,p.goals().get(0).tree().owned()); assertEquals(1,p.goals().get(1).tree().owned());
        assertEquals(Map.of("wood",4L),missing(p));
    }
    @Test void reserveAllGoalItemsBeforeIntermediateAllocation() {
        var p=plan(List.of(recipe("A",1,"B")),List.of(new CraftGoal("A",1),new CraftGoal("B",1)),Map.of("B",1L));
        assertEquals(1,p.goals().get(1).tree().owned()); assertEquals(Map.of("B",1L),missing(p));
    }
    @Test void batchSurplusReusedAcrossGoals() {
        var p=plan(List.of(recipe("A",1,"plank"),recipe("B",1,"plank"),recipe("plank",4,"log")),List.of(new CraftGoal("A",2),new CraftGoal("B",2)),Map.of());
        assertEquals(Map.of("log",1L),missing(p));
    }
    @Test void alternativesChooseInventory() {
        var r=new Recipe("tag","A",1,List.of(new Recipe.Ingredient(List.of("oak","birch"),1)),"test");
        var p=plan(List.of(r),List.of(new CraftGoal("A",4)),Map.of("birch",3L));
        assertEquals(Map.of("birch",1L),missing(p)); assertEquals(1,p.materials().size());
    }
    @Test void storedRecipeSelection() {
        Recipe a=recipe("A",1,"B"),b=recipe("A",1,"C");
        var p=new RecipeResolver(List.of(a,b),Map.of("A",b.id()),Set.of()).resolve(List.of(new CraftGoal("A",3)),Map.of());
        assertEquals(Map.of("C",3L),missing(p));
    }
    @Test void rawOverrideStopsExpansion() {
        var p=new RecipeResolver(List.of(recipe("A",1,"B")),Map.of(),Set.of("A")).resolve(List.of(new CraftGoal("A",2)),Map.of());
        assertEquals(Map.of("A",2L),missing(p));
    }
    @Test void storageBlockCycleDoesNotMakeDustUncollectible() {
        var p=plan(List.of(recipe("dust",9,"block"),recipe("block",1,"dust","dust","dust","dust","dust","dust","dust","dust","dust")),List.of(new CraftGoal("dust",50)),Map.of());
        assertEquals(Map.of("dust",50L),missing(p));
    }
    @Test void existingStorageBlocksCanBeUnpacked() {
        var p=plan(List.of(recipe("dust",9,"block"),recipe("block",1,"dust","dust","dust","dust","dust","dust","dust","dust","dust")),List.of(new CraftGoal("dust",9)),Map.of("block",1L));
        assertTrue(p.materials().stream().allMatch(m -> m.missing()==0));
    }
    @Test void pausedGoalConsumesNoStock() {
        CraftGoal paused=new CraftGoal("A",5); paused.paused=true;
        var p=plan(List.of(),List.of(paused,new CraftGoal("A",4)),Map.of("A",4L));
        assertEquals(1,p.goals().size()); assertEquals(4,p.goals().getFirst().tree().owned());
    }
    @Test void depthLimitDoesNotOverflow() {
        List<Recipe> recipes=new ArrayList<>(); for(int i=0;i<80;i++) recipes.add(recipe("i"+i,1,"i"+(i+1)));
        var p=assertTimeoutPreemptively(Duration.ofSeconds(2),() -> plan(recipes,List.of(new CraftGoal("i0",2)),Map.of()));
        assertFalse(p.warnings().isEmpty());
    }
    @Test void hugeCountsAreBounded() {
        var r=new Recipe("big","A",1,List.of(new Recipe.Ingredient(List.of("B"),1_000_000)),"test");
        var p=plan(List.of(r),List.of(new CraftGoal("A",1_000_000_000)),Map.of());
        assertEquals(RecipeResolver.LIMIT,p.materials().getFirst().needed());
    }
}
