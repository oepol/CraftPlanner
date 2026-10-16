package dev.ole.craftplanner.core;
import java.util.*;
/** Derived from actual allocations, never from recipe-screen clicks or theoretical surplus. */
public final class CraftSteps {
    public record Step(String item,long crafts,long readyCrafts,String kind,List<RecipeTree> inputs) {}
    private CraftSteps() {}
    public static List<Step> all(RecipeResolver.Plan plan) {
        List<Step> out=new ArrayList<>(); for(var g:plan.goals())walk(g.tree(),out); return List.copyOf(out);
    }
    private static void walk(RecipeTree n,List<Step> out) {
        n.children().forEach(c->walk(c,out));
        if(n.crafts()==0 || n.recipe()==null)return;
        long ready=n.crafts();
        for(var child:n.children()) {
            // A mixed alternative may supply part of a batch. Conservatively show ready only
            // when this allocated share is present; actual crafting is always left to Minecraft.
            long each=(child.needed()+n.crafts()-1)/n.crafts();
            ready=Math.min(ready,each==0 ? n.crafts() : child.owned()/each);
        }
        out.add(new Step(n.item(),n.crafts(),ready,n.note(),n.children()));
    }
    public static List<Step> next(RecipeResolver.Plan plan) {
        var all=all(plan); var ready=all.stream().filter(s->s.readyCrafts()>0).toList();
        return ready.isEmpty() ? all.stream().filter(s->s.inputs().stream().noneMatch(i->i.crafts()>0)).toList() : ready;
    }
}
