package dev.ole.craftplanner.core;

import java.util.List;

/** Each ingredient is one occupied input slot, with alternative item IDs. */
public record Recipe(String id, String output, long outputCount, List<Ingredient> inputs, String kind) {
    public record Ingredient(List<String> alternatives, long count) {
        public Ingredient { alternatives = alternatives.stream().distinct().sorted().toList(); }
    }
    public Recipe { inputs = List.copyOf(inputs); }
}
