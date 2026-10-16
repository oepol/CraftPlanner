package dev.ole.craftplanner.core;
import java.util.*;
public record StockTotals(Map<String,Long> direct,Map<String,Long> shulkers,Map<String,Long> openChest) {
    public StockTotals { direct=Map.copyOf(direct); shulkers=Map.copyOf(shulkers); openChest=Map.copyOf(openChest); }
    public Map<String,Long> total() {
        Map<String,Long> out=new HashMap<>();
        for(var source:List.of(direct,shulkers,openChest))source.forEach((k,v)->out.merge(k,v,(a,b)->Math.min(RecipeResolver.LIMIT,a+b)));
        return Map.copyOf(out);
    }
    public static StockTotals empty() { return new StockTotals(Map.of(),Map.of(),Map.of()); }
}
