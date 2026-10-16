package dev.ole.craftplanner.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PlanningWorkflowTest {
    private Recipe r(String item,long output,String... in) { return new Recipe(item,item,output,Arrays.stream(in).map(s->new Recipe.Ingredient(List.of(s),1)).toList(),"craftplanner.recipe.shaped"); }
    private List<Recipe> chain() { return List.of(r("repeater",1,"stone","stone","stone","torch","torch","redstone"),r("torch",1,"stick","redstone"),r("stick",4,"plank","plank"),r("plank",4,"log"),r("stone",1,"cobble")); }
    private RecipeResolver.Plan plan(Map<String,Long> stock) { return new RecipeResolver(chain(),Map.of(),Set.of()).resolve(List.of(new CraftGoal("repeater",1)),stock); }
    private Map<String,Long> missing(RecipeResolver.Plan p) { var m=new TreeMap<String,Long>(); p.materials().stream().filter(x->x.missing()>0).forEach(x->m.put(x.item(),x.missing())); return m; }
    private Set<String> next(RecipeResolver.Plan p) { return new HashSet<>(CraftSteps.next(p).stream().map(CraftSteps.Step::item).toList()); }
    @Test void repeaterUsesNormalStoneAndAllStages() {
        assertEquals(Map.of("cobble",3L,"redstone",3L,"log",1L),missing(plan(Map.of())));
        assertEquals(Set.of("plank","stone"),next(plan(Map.of("log",1L,"cobble",3L,"redstone",3L))));
        assertFalse(CraftSteps.all(plan(Map.of())).stream().anyMatch(s->s.item().equals("smooth_stone")));
    }
    @Test void processedLogsDoNotReappearAsMissing() {
        var p=plan(Map.of("plank",4L,"cobble",3L,"redstone",3L)); assertTrue(missing(p).isEmpty()); assertEquals(Set.of("stick","stone"),next(p));
        p=plan(Map.of("stick",4L,"stone",3L,"redstone",3L)); assertTrue(missing(p).isEmpty()); assertEquals(Set.of("torch"),next(p));
        p=plan(Map.of("torch",2L,"stone",3L,"redstone",1L)); assertTrue(missing(p).isEmpty()); assertEquals(Set.of("repeater"),next(p));
    }
    @Test void RawMaterialsDoNotCompleteTarget() {
        var p=plan(Map.of("log",1L,"cobble",3L,"redstone",3L)); assertEquals(0,p.goals().getFirst().tree().owned()); assertFalse(CraftSteps.all(p).isEmpty());
        assertTrue(CraftSteps.all(plan(Map.of("repeater",1L))).isEmpty());
    }
    @Test void mixedAlternativesAcrossSourcesAreConsumedOnce() {
        var recipe=new Recipe("tag","out",1,List.of(new Recipe.Ingredient(List.of("oak","birch"),1)),"test");
        var stock=new StockTotals(Map.of("oak",2L),Map.of("birch",3L),Map.of("oak",1L));
        var p=new RecipeResolver(List.of(recipe),Map.of(),Set.of()).resolve(List.of(new CraftGoal("out",8)),stock.total());
        assertEquals(2,p.materials().stream().mapToLong(MaterialRequirement::missing).sum());
        assertEquals(6,p.materials().stream().mapToLong(MaterialRequirement::owned).sum());
    }
    @Test void exampleShulkerInventory48Of64() { var s=new StockTotals(Map.of("iron",16L),Map.of("iron",32L),Map.of()); var p=new RecipeResolver(List.of(),Map.of(),Set.of()).resolve(List.of(new CraftGoal("iron",64)),s.total()); assertEquals(48,p.materials().getFirst().owned()); assertEquals(16,p.materials().getFirst().missing()); }
    @Test void inventoryChestTransfersKeepTotalConstant() {
        assertEquals(new StockTotals(Map.of("planks",8L),Map.of(),Map.of("planks",12L)).total(),new StockTotals(Map.of("planks",20L),Map.of(),Map.of()).total());
    }
    @Test void multipleShulkersAndTransfersStaySingleCounted() {
        var a=new StockTotals(Map.of("iron",16L),Map.of("iron",64L),Map.of());
        var b=new StockTotals(Map.of("iron",48L),Map.of("iron",32L),Map.of()); assertEquals(a.total(),b.total());
    }
    @Test void cachedSnapshotCannotAffectLiveTotal() { var chest=new ChestSource("overworld","1, 2, 3",null); chest.lastStock=Map.of("iron",64L); var s=new StockTotals(Map.of("iron",16L),Map.of(),Map.of()); assertEquals(16,s.total().get("iron")); }
    @Test void doubleChestKeyIndependentOfClickedHalfAndDimension() {
        assertEquals(ChestSource.key("overworld","1, 2, 3","2, 2, 3"),ChestSource.key("overworld","2, 2, 3","1, 2, 3"));
        assertNotEquals(ChestSource.key("nether","1, 2, 3",null),ChestSource.key("overworld","1, 2, 3",null));
    }
    @Test void repeatAddingSourceReplacesSnapshot() {
        var m=new HashMap<String,ChestSource>(); var c=new ChestSource("overworld","1, 2, 3",null); m.put(c.key(),c); m.put(c.key(),c); assertEquals(1,m.size()); m.remove(c.key()); assertTrue(m.isEmpty());
    }
    @Test void pinsDoNotDeletePlansAndNeedExplicitRelease() {
        var goals=new ArrayList<CraftGoal>(); for(int i=0;i<4;i++)goals.add(new CraftGoal("item"+i,1));
        for(int i=0;i<3;i++)assertTrue(Pins.toggle(goals.get(i),goals,3));
        assertFalse(Pins.toggle(goals.get(3),goals,3)); assertEquals(4,goals.size()); assertEquals(3,Pins.count(goals));
        assertTrue(Pins.toggle(goals.get(1),goals,3)); assertTrue(Pins.toggle(goals.get(3),goals,3));
    }
    @Test void pinSubstepsDoNotCountAsExtraPins() { CraftGoal goal=new CraftGoal("repeater",1); goal.pinned=true; assertEquals(1,Pins.count(List.of(goal))); assertTrue(CraftSteps.all(plan(Map.of())).size()>3); }
}
