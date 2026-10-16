package dev.ole.craftplanner.core;
import java.util.*;
/** Credits only a simultaneous carried-stock decrease and an observed chest increase. */
public final class CompletionLedger {
    private CompletionLedger() {}
    public static boolean observe(List<CraftGoal> goals,Map<String,Long> carried) {
        Map<String,Long> available=new HashMap<>(carried); boolean changed=false;
        for(var goal:goals)if(!goal.paused && !goal.archived) {
            long need=Math.max(0,goal.count-goal.delivered),used=Math.min(need,available.getOrDefault(goal.item,0L));
            available.merge(goal.item,-used,Long::sum);
            if(used>=need && !goal.achieved) { goal.achieved=true; changed=true; }
        }
        return changed;
    }
    public static boolean deposited(List<CraftGoal> goals,Map<String,Long> before,Map<String,Long> after,Map<String,Long> chestIncrease) {
        return transferred(goals,before,after,chestIncrease,"test");
    }
    public static boolean transferred(List<CraftGoal> goals,Map<String,Long> before,Map<String,Long> after,Map<String,Long> changes,String source) {
        boolean changed=false;
        Map<String,Long> withdrawn=new HashMap<>();
        changes.forEach((item,n)-> { if(n<0)withdrawn.put(item,Math.min(-n,Math.max(0,after.getOrDefault(item,0L)-before.getOrDefault(item,0L)))); });
        for(var goal:goals)if(!goal.archived) {
            long undo=Math.min(goal.deliveredByChest.getOrDefault(source,0L),withdrawn.getOrDefault(goal.item,0L));
            if(undo>0) { goal.delivered-=undo; goal.deliveredByChest.merge(source,-undo,Long::sum); withdrawn.merge(goal.item,-undo,Long::sum); changed=true; }
        }
        Map<String,Long> moved=new HashMap<>();
        changes.forEach((item,n)->moved.put(item,Math.min(Math.max(0,n),Math.max(0,before.getOrDefault(item,0L)-after.getOrDefault(item,0L)))));
        for(var goal:goals)if(goal.achieved && !goal.archived && !goal.paused) {
            long credit=Math.min(Math.max(0,goal.count-goal.delivered),moved.getOrDefault(goal.item,0L));
            if(credit<=0)continue;
            goal.delivered+=credit; goal.deliveredByChest.merge(source,credit,Long::sum); moved.merge(goal.item,-credit,Long::sum); changed=true;
            if(goal.delivered>=goal.count) { goal.archived=true; goal.pinned=false; }
        }
        return changed;
    }
}
