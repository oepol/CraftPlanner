package dev.ole.craftplanner.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class StateLifecycleTest {
    @TempDir Path dir;
    private ConfigManager store() { return new ConfigManager(dir.resolve("config.json"),s->{}); }
    @Test void worldSwitchAndRestartKeepPlansSeparate() {
        var c=new PlannerConfig(); var p=new PlanProfiles(c); p.activate("a"); c.goals.add(new CraftGoal("minecraft:stone",3));
        c.sources.put("chest",new ChestSource("minecraft:overworld","1, 2, 3",null));
        p.activate("b"); assertTrue(c.goals.isEmpty()); assertTrue(c.sources.isEmpty()); c.goals.add(new CraftGoal("minecraft:dirt",8));
        assertTrue(store().save(p.forStorage(c))); var loaded=store().load(); var again=new PlanProfiles(loaded); assertTrue(loaded.goals.isEmpty());
        again.activate("a"); assertEquals("minecraft:stone",loaded.goals.getFirst().item); assertEquals(1,loaded.sources.size());
        again.activate("b"); assertEquals("minecraft:dirt",loaded.goals.getFirst().item); assertTrue(loaded.sources.isEmpty());
        again.activate(null); assertTrue(loaded.goals.isEmpty());
    }
    @Test void legacyPlanRequiresExplicitOneTimeImport() {
        var c=new PlannerConfig(); c.goals.add(new CraftGoal("minecraft:stone",3)); var p=new PlanProfiles(c);
        p.activate("a"); assertTrue(c.goals.isEmpty()); assertTrue(p.canImport()); p.importLegacy(); p.importLegacy(); assertEquals(1,c.goals.size());
        p.activate("b"); assertTrue(c.goals.isEmpty()); assertFalse(p.canImport());
    }
    @Test void previewSaveDoesNotPersistDraftSettingsOrDiscardGoals() {
        var c=new PlannerConfig(); var p=new PlanProfiles(c); p.activate("a"); c.goals.add(new CraftGoal("minecraft:stone",3));
        var before=c.settingsCopy(); c.opacity=.3; c.includeShulkers=false; c.pinLimit=7;
        assertTrue(store().save(p.forStorage(before))); var l=store().load(); assertEquals(.82,l.opacity); assertTrue(l.includeShulkers); assertEquals(3,l.pinLimit);
        c.copySettingsFrom(before); assertEquals(1,c.goals.size()); assertEquals(.82,c.opacity);
        c.pinLimit=5; assertTrue(store().save(p.forStorage(c))); assertEquals(5,store().load().pinLimit);
    }
    @Test void asyncWorldExitDropsLateResultsWithoutBlocking() {
        var pending=new PendingSnapshot<String>(); var old=new CompletableFuture<String>(); pending.request(old); assertTrue(pending.poll().isEmpty());
        pending.clear(); old.complete("old world"); assertTrue(pending.poll().isEmpty()); pending.request(CompletableFuture.completedFuture("new world")); assertEquals("new world",pending.poll().orElseThrow());
    }
    @Test void failedSnapshotCanBeRetried() {
        var pending=new PendingSnapshot<String>(); pending.request(CompletableFuture.failedFuture(new IllegalStateException())); assertThrows(CompletionException.class,pending::poll);
        assertFalse(pending.pending()); pending.request(CompletableFuture.completedFuture("ok")); assertEquals("ok",pending.poll().orElseThrow());
    }
    @Test void completeThenStoreRemovesTodoPermanently() {
        var c=new PlannerConfig(); var p=new PlanProfiles(c); p.activate("a"); var g=new CraftGoal("minecraft:repeater",2); g.pinned=true; c.goals.add(g);
        assertTrue(CompletionLedger.observe(c.goals,Map.of(g.item,2L)));
        assertTrue(CompletionLedger.deposited(c.goals,Map.of(g.item,2L),Map.of(),Map.of(g.item,2L)));
        assertTrue(g.archived); assertFalse(g.pinned); assertTrue(new RecipeResolver(List.of(),Map.of(),Set.of()).resolve(c.goals,Map.of()).goals().isEmpty());
        store().save(p.forStorage(c)); var l=store().load(); var loaded=new PlanProfiles(l); loaded.activate("a"); assertTrue(l.goals.getFirst().archived);
    }
    @Test void partialDepositReducesRemainingGoalWithoutMissingAgain() {
        var g=new CraftGoal("minecraft:stone",64); var list=List.of(g); CompletionLedger.observe(list,Map.of(g.item,64L));
        CompletionLedger.deposited(list,Map.of(g.item,64L),Map.of(g.item,32L),Map.of(g.item,32L));
        assertFalse(g.archived); assertEquals(32,g.delivered);
        var p=new RecipeResolver(List.of(),Map.of(),Set.of()).resolve(list,Map.of(g.item,32L)); assertEquals(0,p.materials().getFirst().missing());
    }
    @Test void deathDropOrAnotherPlayersChestDepositCannotCompleteGoal() {
        var g=new CraftGoal("minecraft:stone",2); g.achieved=true; var list=List.of(g);
        assertFalse(CompletionLedger.deposited(list,Map.of(g.item,2L),Map.of(),Map.of()));
        assertFalse(CompletionLedger.deposited(list,Map.of(g.item,2L),Map.of(g.item,2L),Map.of(g.item,2L))); assertFalse(g.archived);
    }
    @Test void identicalGoalsCannotShareDepositCredit() {
        var a=new CraftGoal("minecraft:stone",2); var b=new CraftGoal("minecraft:stone",2); a.achieved=true; b.achieved=true;
        CompletionLedger.deposited(List.of(a,b),Map.of(a.item,4L),Map.of(a.item,2L),Map.of(a.item,2L)); assertTrue(a.archived); assertFalse(b.archived); assertEquals(0,b.delivered);
    }
    @Test void neverCompletedGoalIsNotAutoArchived() {
        var g=new CraftGoal("minecraft:stone",64); assertFalse(CompletionLedger.observe(List.of(g),Map.of(g.item,32L))); assertFalse(CompletionLedger.deposited(List.of(g),Map.of(g.item,32L),Map.of(),Map.of(g.item,32L))); assertFalse(g.archived);
    }
    @Test void withdrawingAndRedepositingPartialDeliveryDoesNotDuplicateCredit() {
        var g=new CraftGoal("minecraft:stone",64); g.achieved=true; var goals=List.of(g);
        CompletionLedger.transferred(goals,Map.of(g.item,64L),Map.of(g.item,32L),Map.of(g.item,32L),"chest");
        assertEquals(32,g.delivered);
        CompletionLedger.transferred(goals,Map.of(g.item,32L),Map.of(g.item,64L),Map.of(g.item,-32L),"chest");
        assertEquals(0,g.delivered);
        CompletionLedger.transferred(goals,Map.of(g.item,64L),Map.of(g.item,32L),Map.of(g.item,32L),"chest");
        assertEquals(32,g.delivered); assertFalse(g.archived);
    }
    @Test void creditedChestItemsAreNotAvailableTwiceWhileGoalIsPartiallyStored() {
        var a=new CraftGoal("minecraft:stone",64); a.achieved=true; var b=new CraftGoal("minecraft:stone",64);
        CompletionLedger.transferred(List.of(a,b),Map.of(a.item,64L),Map.of(a.item,32L),Map.of(a.item,32L),"chest");
        var chest=ChestSource.unreserved(Map.of(a.item,32L),List.of(a,b),"chest"); assertTrue(chest.isEmpty());
        var p=new RecipeResolver(List.of(),Map.of(),Set.of()).resolve(List.of(a,b),new StockTotals(Map.of(a.item,32L),Map.of(),chest).total());
        assertEquals(64,p.materials().getFirst().missing());
    }
}
