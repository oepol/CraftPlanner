import dev.ole.craftplanner.InventoryTracker;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.network.chat.Component;
import java.util.*;
public class InventoryComponentCheck {
    static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    static ItemStack box(Item item,int iron) {
        ItemStack box=new ItemStack(item);
        box.set(DataComponents.CUSTOM_NAME,Component.literal("named box"));
        box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.IRON_INGOT,iron))));
        return box;
    }
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(net.minecraft.data.registries.VanillaRegistries.createWorldLookup()).forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        Map<String,Long> direct=new HashMap<>(),shulkers=new HashMap<>();
        InventoryTracker.scan(direct,shulkers,new ItemStack(Items.IRON_INGOT,16),true);
        var white=box(Items.DYED_SHULKER_BOX.pick(DyeColor.WHITE),32); var blue=box(Items.DYED_SHULKER_BOX.pick(DyeColor.BLUE),12);
        InventoryTracker.scan(direct,shulkers,white,true); InventoryTracker.scan(direct,shulkers,blue,true);
        check(direct.get("minecraft:iron_ingot")==16,"direct stock"); check(shulkers.get("minecraft:iron_ingot")==44,"multiple named, colored shulker boxes");
        shulkers.clear(); InventoryTracker.scan(new HashMap<>(),shulkers,white,false); check(shulkers.isEmpty(),"disabled option");
        white.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.IRON_INGOT,20))));
        InventoryTracker.scan(new HashMap<>(),shulkers,white,true); check(shulkers.get("minecraft:iron_ingot")==20,"actual updated component contents");
        System.out.println("PASS: actual Minecraft 26.3 registry/bootstrap and ItemContainerContents; colored/named shulkers, updated contents, disabled option.");
    }
}
