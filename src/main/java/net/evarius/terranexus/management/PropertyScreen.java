package net.evarius.terranexus.management;
import net.evarius.terranexus.landlord.*;
import net.minecraft.inventory.SimpleInventory;import net.minecraft.item.Items;import net.minecraft.screen.SimpleNamedScreenHandlerFactory;import net.minecraft.server.network.ServerPlayerEntity;import net.minecraft.text.Text;import net.minecraft.util.Formatting;import net.minecraft.util.math.BlockPos;
import java.util.*;
public final class PropertyScreen {
 private PropertyScreen(){}
 public static void open(ServerPlayerEntity p){
  LandlordState state=LandlordState.get(p.getServer());SimpleInventory inv=new SimpleInventory(54);Map<Integer,java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();String dim=p.getWorld().getRegistryKey().getValue().toString();
  ManagementHubScreen.display(inv,4,Items.FILLED_MAP,"Grundstücksverwaltung","Position: "+p.getBlockX()+", "+p.getBlockY()+", "+p.getBlockZ());ManagementHubScreen.display(inv,8,Items.ARROW,"Zurück","Verwaltungsübersicht");actions.put(8,x->ManagementHubScreen.open(p));
  for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){int slot=(dz+1)*3+(dx+1),cx=(p.getBlockX()>>4)+dx,cz=(p.getBlockZ()>>4)+dz;LandProperty at=state.at(dim,new BlockPos(cx*16+8,p.getBlockY(),cz*16+8));ManagementHubScreen.display(inv,slot,at==null?Items.LIME_STAINED_GLASS_PANE:Items.RED_STAINED_GLASS_PANE,"Chunk "+cx+" / "+cz,at==null?"Frei":"Belegt: "+at.name());}
  ManagementHubScreen.display(inv,19,Items.GRASS_BLOCK,"Aktuellen Chunk beanspruchen","16×16-Blöcke");actions.put(19,x->name(p,n->finish(p,state,LandlordState.chunk(n,p.getUuid(),dim,p.getBlockX()>>4,p.getBlockZ()>>4))));
  ManagementHubScreen.display(inv,21,Items.WOODEN_AXE,"Quader: Punkt 1 setzen","Aktuelle Blockposition");actions.put(21,x->{PropertyDrafts.POS1.put(p.getUuid(),p.getBlockPos());open(p);});
  ManagementHubScreen.display(inv,22,Items.IRON_AXE,"Quader: Punkt 2 setzen","Aktuelle Blockposition");actions.put(22,x->{PropertyDrafts.POS2.put(p.getUuid(),p.getBlockPos());open(p);});
  ManagementHubScreen.display(inv,23,Items.EMERALD,"Quader abschließen","Benötigt Punkt 1 und 2");actions.put(23,x->{BlockPos a=PropertyDrafts.POS1.get(p.getUuid()),b=PropertyDrafts.POS2.get(p.getUuid());if(a!=null&&b!=null)name(p,n->finish(p,state,LandlordState.cuboid(n,p.getUuid(),dim,a,b)));});
  int count=PropertyDrafts.POLYGONS.getOrDefault(p.getUuid(),List.of()).size();ManagementHubScreen.display(inv,25,Items.GOLDEN_HOE,"Polygonpunkt hinzufügen","Aktuelle Position · bisher "+count);actions.put(25,x->{PropertyDrafts.POLYGONS.computeIfAbsent(p.getUuid(),k->new ArrayList<>()).add(p.getBlockPos());open(p);});
  ManagementHubScreen.display(inv,26,Items.EMERALD,"Polygon abschließen","Mindestens 3 Punkte · schräge Grenzen");actions.put(26,x->{List<BlockPos> pts=PropertyDrafts.POLYGONS.get(p.getUuid());if(pts!=null&&pts.size()>=3)name(p,n->finish(p,state,LandlordState.polygon(n,p.getUuid(),dim,pts)));});
  int slot=36;for(LandProperty prop:state.owned(p.getUuid())){if(slot>=54)break;ManagementHubScreen.display(inv,slot++,Items.PAPER,prop.name(),prop.regionType()+" · "+prop.minX()+","+prop.minZ()+" bis "+prop.maxX()+","+prop.maxZ());}
  p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,pi,x)->new ActionMenuScreenHandler(id,pi,inv,actions),Text.literal("TerraNexus Grundstücke").formatted(Formatting.DARK_GREEN)));
 }
 private static void name(ServerPlayerEntity p,java.util.function.Consumer<String> done){p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,pi,x)->new TextInputScreenHandler(id,pi,done),Text.literal("Grundstücksname")));}
 private static void finish(ServerPlayerEntity p,LandlordState s,LandProperty prop){if(s.add(prop)){PropertyDrafts.POS1.remove(p.getUuid());PropertyDrafts.POS2.remove(p.getUuid());PropertyDrafts.POLYGONS.remove(p.getUuid());p.sendMessage(Text.literal("Grundstück „"+prop.name()+"“ wurde angelegt.").formatted(Formatting.GREEN),false);}else p.sendMessage(Text.literal("Dieser Bereich überschneidet sich mit einem bestehenden Grundstück.").formatted(Formatting.RED),false);open(p);}
}
