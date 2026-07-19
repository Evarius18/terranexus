package net.evarius.terranexus.management;

import net.minecraft.inventory.SimpleInventory;import net.minecraft.item.Item;import net.minecraft.item.Items;import net.minecraft.screen.SimpleNamedScreenHandlerFactory;import net.minecraft.server.network.ServerPlayerEntity;import net.minecraft.text.Text;import net.minecraft.util.Formatting;
import java.util.*;import java.util.function.Consumer;

public final class SelectionMenuScreen {
    private SelectionMenuScreen() {}
    public record Option(String value,String label,String detail,Item icon){}
    public static void open(ServerPlayerEntity player,String title,List<Option> options,Consumer<String> selected,Runnable back){SimpleInventory inventory=new SimpleInventory(54);Map<Integer,Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();ManagementHubScreen.display(inventory,4,Items.COMPASS,title,"Bitte eine feste Option auswählen");int slot=10;for(Option option:options){if(slot>=45)break;while(slot%9==8)slot++;ManagementHubScreen.display(inventory,slot,option.icon(),option.label(),option.detail());actions.put(slot++,ignored->selected.accept(option.value()));}ManagementHubScreen.display(inventory,53,Items.ARROW,"Zurück","Auswahl abbrechen");actions.put(53,ignored->back.run());player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,pi,x)->new ActionMenuScreenHandler(id,pi,inventory,actions),Text.literal(title).formatted(Formatting.DARK_AQUA)));}
}
