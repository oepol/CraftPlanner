package dev.ole.craftplanner;
import dev.ole.craftplanner.core.StockTotals;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import java.util.*;
public final class InventoryTracker {
    private StockTotals sources=StockTotals.empty();
    private Map<String,Long> counts=Map.of();
    public boolean update(Minecraft mc,boolean includeShulkers,Map<String,Long> chest) {
        Map<String,Long> direct=new HashMap<>(),shulkers=new HashMap<>();
        if(mc.player!=null) {
            for(ItemStack stack:mc.player.getInventory().getNonEquipmentItems())scan(direct,shulkers,stack,includeShulkers);
            scan(direct,shulkers,mc.player.getOffhandItem(),includeShulkers);
            scan(direct,shulkers,mc.player.containerMenu.getCarried(),includeShulkers);
        }
        StockTotals next=new StockTotals(direct,shulkers,chest);
        if(next.equals(sources))return false;
        sources=next; counts=next.total(); return true;
    }
    public static void add(Map<String,Long> out,ItemStack stack) {
        if(!stack.isEmpty())out.merge(ItemNames.id(stack.getItem()),(long)stack.getCount(),Long::sum);
    }
    public static void scan(Map<String,Long> direct,Map<String,Long> contents,ItemStack stack,boolean include) {
        add(direct,stack);
        if(include && stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock) {
            var container=stack.get(DataComponents.CONTAINER);
            // Real component contents, independent of color/name. No recursive container traversal.
            if(container!=null)container.nonEmptyItemCopyStream().forEach(item->add(contents,item));
        }
    }
    public StockTotals sources() { return sources; }
    public Map<String,Long> counts() { return counts; }
    public void clear() { counts=Map.of(); sources=StockTotals.empty(); }
}
