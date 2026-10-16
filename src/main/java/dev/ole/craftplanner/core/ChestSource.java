package dev.ole.craftplanner.core;
import java.util.*;
/** Persisted observations are never added to live inventory totals. */
public final class ChestSource {
    public String dimension,position,partner;
    public Map<String,Long> lastStock=new TreeMap<>();
    public long observedAt;
    public ChestSource(String dimension,String position,String partner) { this.dimension=dimension; this.position=position; this.partner=partner; }
    public String key() { return key(dimension,position,partner); }
    public static String key(String dimension,String a,String b) {
        if (b==null || a.equals(b)) return dimension+"|"+a;
        return dimension+"|"+(a.compareTo(b)<0 ? a+"|"+b : b+"|"+a);
    }
    public static Map<String,Long> unreserved(Map<String,Long> stock,List<CraftGoal> goals,String sourceKey) {
        Map<String,Long> available=new HashMap<>(stock);
        for(var goal:goals)if(!goal.archived) {
            long stored=goal.deliveredByChest.getOrDefault(sourceKey,0L);
            if(stored>0)available.computeIfPresent(goal.item,(k,v)->Math.max(0,v-stored));
        }
        available.values().removeIf(n->n<=0); return Map.copyOf(available);
    }
    public boolean valid() { return dimension!=null && position!=null; }
    public void sanitize() {
        if(lastStock==null) lastStock=new TreeMap<>();
        lastStock.entrySet().removeIf(e -> e.getKey()==null || e.getValue()==null || e.getValue()<=0);
        lastStock.replaceAll((k,v)->Math.min(v,RecipeResolver.LIMIT));
    }
}
