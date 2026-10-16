package dev.ole.craftplanner.core;

import java.util.*;

/** Pure planner: never changes game inventory and never reuses allocated stock. */
public final class RecipeResolver {
    public static final long LIMIT = 1_000_000_000_000L;
    private static final int MAX_DEPTH = 32, MAX_NODES = 20000;
    private final Map<String, List<Recipe>> recipes;
    private final Map<String, String> preferences;
    private final Set<String> rawOverrides;
    private Map<String, Long> stock, surplus;
    private final Map<String, long[]> materials = new TreeMap<>();
    private int visited, estimates;

    public record GoalPlan(CraftGoal goal, RecipeTree tree) {}
    public record Plan(List<GoalPlan> goals, List<MaterialRequirement> materials, List<String> warnings) {
        public static Plan empty() { return new Plan(List.of(), List.of(), List.of()); }
    }
    private final Set<String> warnings = new LinkedHashSet<>();

    public RecipeResolver(Collection<Recipe> entries, Map<String,String> preferences, Set<String> rawOverrides) {
        recipes = new HashMap<>();
        this.preferences = Map.copyOf(preferences);
        this.rawOverrides = Set.copyOf(rawOverrides);
        for (Recipe r : entries) {
            if (r.outputCount() > 0 && !r.inputs().isEmpty() && r.inputs().stream().allMatch(i -> i.count() > 0 && !i.alternatives().isEmpty()))
                recipes.computeIfAbsent(r.output(), k -> new ArrayList<>()).add(r);
        }
        recipes.values().forEach(list -> list.sort(Comparator.comparingInt(RecipeResolver::defaultRank).thenComparing(Recipe::id)));
    }

    public Plan resolve(List<CraftGoal> goals, Map<String,Long> inventory) {
        stock = new HashMap<>();
        inventory.forEach((id,n) -> { if (n > 0) stock.put(id, Math.min(n, LIMIT)); });
        surplus = new HashMap<>(); materials.clear(); warnings.clear(); visited = estimates = 0;
        List<GoalPlan> plans = new ArrayList<>();
        // Reserve all finished goal items before using any remaining stock as ingredients.
        Map<String,Long> reserved = new HashMap<>();
        for (CraftGoal goal : goals) if (!goal.paused && !goal.archived) reserved.put(goal.id, take(stock,goal.item,Math.max(0,goal.count-goal.delivered)));
        for (CraftGoal goal : goals) if (!goal.paused && !goal.archived)
            plans.add(new GoalPlan(goal, expand(goal.item, Math.max(0,goal.count-goal.delivered), new LinkedHashSet<>(), 0, reserved.get(goal.id))));
        List<MaterialRequirement> leaves = materials.entrySet().stream()
            .map(e -> new MaterialRequirement(e.getKey(),e.getValue()[0],e.getValue()[1])).toList();
        return new Plan(List.copyOf(plans), leaves, List.copyOf(warnings));
    }

    private RecipeTree expand(String item,long amount,Set<String> path,int depth,Long reserved) {
        amount = Math.min(LIMIT, Math.max(0,amount));
        long owned = reserved == null ? take(stock,item,amount) : reserved;
        long extra = take(surplus,item,amount-owned), remaining = amount-owned-extra;
        boolean capped = depth >= MAX_DEPTH || ++visited > MAX_NODES;
        Recipe chosen = null;
        if (!capped && !path.contains(item) && !rawOverrides.contains(item)) {
            Set<String> next = new LinkedHashSet<>(path); next.add(item);
            chosen = chooseRecipe(item,next);
        }
        if (chosen == null || remaining == 0) {
            boolean blockedCycle = remaining>0 && chosen==null && !rawOverrides.contains(item)
                && recipes.getOrDefault(item,List.of()).stream().anyMatch(r -> !isUnpackingLoop(r));
            String note = capped ? "craftplanner.warning.limit" : path.contains(item) || blockedCycle ? "craftplanner.warning.cycle" : "";
            if (!note.isEmpty()) warnings.add(note);
            // A fully owned intermediate does not create imaginary raw requirements.
            if (chosen == null) {
                long[] row = materials.computeIfAbsent(item,k -> new long[2]);
                row[0] = add(row[0], amount-extra); row[1] = add(row[1], owned);
            }
            return new RecipeTree(item,amount,owned,extra,0,null,note,List.of());
        }
        long crafts = (remaining + chosen.outputCount()-1)/chosen.outputCount();
        Set<String> next = new LinkedHashSet<>(path); next.add(item);
        // Coalesce repeated slots before rounding downstream batches (three planks != three logs).
        Map<String,Long> inputs = new LinkedHashMap<>();
        Map<String,Long> selectionStock = new HashMap<>(stock);
        for (Recipe.Ingredient ingredient : chosen.inputs()) {
            long demand = multiply(crafts,ingredient.count());
            for(String alt:ingredient.alternatives().stream().sorted(Comparator.<String>comparingLong(id -> -selectionStock.getOrDefault(id,0L)).thenComparing(id->id)).toList()) {
                long used=take(selectionStock,alt,demand);
                if(used>0)inputs.merge(alt,used,RecipeResolver::add);
                demand-=used; if(demand==0)break;
            }
            if(demand>0) { String alternative=chooseIngredient(ingredient,next,selectionStock); inputs.merge(alternative,demand,RecipeResolver::add); }
        }
        List<RecipeTree> children = new ArrayList<>();
        for (var input : inputs.entrySet()) children.add(expand(input.getKey(),input.getValue(),next,depth+1,null));
        surplus.merge(item, Math.max(0,multiply(crafts,chosen.outputCount())-remaining), RecipeResolver::add);
        return new RecipeTree(item,amount,owned,extra,crafts,chosen.id(),chosen.kind(),List.copyOf(children));
    }

    private static int defaultRank(Recipe r) {
        return r.inputs().stream().flatMap(i -> i.alternatives().stream())
            .mapToInt(id -> id.startsWith("minecraft:raw_") ? 0 : id.startsWith("minecraft:oak_") ? 1 : 2).min().orElse(2);
    }

    private Recipe chooseRecipe(String item,Set<String> path) {
        Recipe best = null; double bestCost = Double.POSITIVE_INFINITY;
        for (Recipe r : recipes.getOrDefault(item,List.of())) {
            if (isUnpackingLoop(r)) continue;
            double cost = recipeCost(r,path,0);
            if (!Double.isFinite(cost)) continue;
            if (r.id().equals(preferences.get(item))) return r;
            if (cost < bestCost) { bestCost = cost; best = r; }
        }
        return best;
    }

    private double recipeCost(Recipe recipe,Set<String> path,int depth) {
        if (depth > 12 || ++estimates > 150000) return Double.POSITIVE_INFINITY;
        double total = 0.02;
        for (Recipe.Ingredient ingredient : recipe.inputs()) {
            double best = Double.POSITIVE_INFINITY;
            for (String alt : ingredient.alternatives()) best = Math.min(best,estimate(alt,path,depth+1));
            total += best * ingredient.count();
        }
        return total/recipe.outputCount();
    }
    private double estimate(String item,Set<String> path,int depth) {
        if (stock.getOrDefault(item,0L) > 0 || surplus.getOrDefault(item,0L) > 0) return 0.001;
        if (path.contains(item)) return Double.POSITIVE_INFINITY;
        if (rawOverrides.contains(item) || !recipes.containsKey(item)) return 1;
        Set<String> next = new LinkedHashSet<>(path); next.add(item);
        double best = Double.POSITIVE_INFINITY;
        for (Recipe r : recipes.get(item)) if (!isUnpackingLoop(r)) best = Math.min(best,recipeCost(r,next,depth));
        // If every path cycles, this item is a collection boundary, not an invented craft.
        return Double.isFinite(best) ? best : recipes.get(item).stream().allMatch(this::isUnpackingLoop) ? 1 : 1.05;
    }
    /** Do not turn collectible resources into storage blocks solely to unpack them again. */
    private boolean isUnpackingLoop(Recipe recipe) {
        if (recipe.inputs().size()!=1 || recipe.outputCount()<=recipe.inputs().getFirst().count()) return false;
        for (String input:recipe.inputs().getFirst().alternatives()) {
            if (stock.getOrDefault(input,0L)>0) return false;
            boolean reverse=recipes.getOrDefault(input,List.of()).stream().anyMatch(r ->
                r.inputs().stream().allMatch(i -> i.alternatives().size()==1 && i.alternatives().contains(recipe.output())));
            if (!reverse) return false;
        }
        return true;
    }
    private String chooseIngredient(Recipe.Ingredient ingredient,Set<String> path,Map<String,Long> available) {
        return ingredient.alternatives().stream().min(Comparator
            .<String>comparingLong(id -> -available.getOrDefault(id,0L))
            .thenComparingDouble(id -> estimate(id,path,0))
            .thenComparingInt(id -> id.startsWith("minecraft:oak_") ? 0 : id.startsWith("minecraft:raw_") ? 1 : 2)
            .thenComparing(id -> id)).orElseThrow();
    }
    private static long take(Map<String,Long> pool,String id,long count) {
        long have=pool.getOrDefault(id,0L), used=Math.min(Math.max(count,0),have);
        if (used > 0) pool.put(id,have-used);
        return used;
    }
    private static long add(long a,long b) { return a >= LIMIT-b ? LIMIT : a+b; }
    private static long multiply(long a,long b) { return a > LIMIT/b ? LIMIT : a*b; }
}
