package dev.ole.craftplanner.core;
import java.util.List;
public final class Pins {
    private Pins() {}
    public static long count(List<CraftGoal> goals) { return goals.stream().filter(g->g.pinned).count(); }
    public static boolean toggle(CraftGoal goal,List<CraftGoal> goals,int limit) {
        if(goal.pinned) { goal.pinned=false; return true; }
        if(count(goals)>=limit)return false;
        goal.pinned=true; return true;
    }
}
