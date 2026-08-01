package net.evarius.terranexus;

import net.evarius.terranexus.economy.CashDenomination;
import net.evarius.terranexus.phone.messenger.MessengerConversation;
import net.evarius.terranexus.phone.messenger.MessengerMessage;
import net.evarius.terranexus.phone.messenger.MessengerState;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandlordState;
import net.evarius.terranexus.landlord.PropertySubareaState;
import net.evarius.terranexus.election.ElectionState;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

public final class EconomyMessengerPolicyTest {
    public static void main(String[] args) {
        verifyAtmDenominations();
        verifyMessengerRetentionAndMembership();
        verifyLandDetachAndExclusion();
        verifyLandResurveyAndBuildingSubarea();
        verifyElectionLifecycle();
    }

    private static void verifyLandDetachAndExclusion() {
        UUID owner=UUID.randomUUID();String world="minecraft:overworld";
        LandlordState state=new LandlordState();
        LandProperty parent=LandlordState.polygon("Hauptgrundstück",owner,world,List.of(
                new BlockPos(0,0,0),new BlockPos(10,0,0),new BlockPos(10,0,10),new BlockPos(0,0,10)));
        require(state.add(parent),"Parent property was not added");
        List<BlockPos> cut=List.of(new BlockPos(2,0,2),new BlockPos(4,0,2),new BlockPos(4,0,4),new BlockPos(2,0,4));
        LandProperty detached=state.detachArea(parent.id(),"Wohnung 1",cut);
        require(detached!=null,"Contained freeform area was not detached");
        require(!state.get(parent.id()).contains(world,3,0,3),"Parent still owns detached columns");
        require(state.at(world,new BlockPos(3,0,3)).id().equals(detached.id()),"Detached property does not resolve at its blocks");
        require(state.get(parent.id()).contains(world,8,0,8),"Unaffected parent columns were lost");
    }

    private static void verifyLandResurveyAndBuildingSubarea() {
        UUID owner=UUID.randomUUID();String world="minecraft:overworld";
        LandlordState state=new LandlordState();List<BlockPos> outline=List.of(new BlockPos(0,0,0),new BlockPos(10,0,0),new BlockPos(10,0,10),new BlockPos(0,0,10));
        LandProperty parent=LandlordState.polygon("Wilderness",owner,world,outline);require(state.add(parent),"Resurvey parent not added");
        List<BlockPos> cut=List.of(new BlockPos(2,0,2),new BlockPos(4,0,2),new BlockPos(4,0,4),new BlockPos(2,0,4));
        LandProperty child=state.detachArea(parent.id(),"Wildnis",cut);require(child!=null,"Resurvey child not detached");
        LandlordState.ReshapeResult result=state.reshape(LandlordState.editedPolygon(state.get(parent.id()),outline),ignored->true);
        require(result.success()&&result.absorbedPropertyIds().contains(child.id()),"Safe detached area was not reabsorbed");
        require(state.get(child.id())==null&&state.get(parent.id()).name().equals("Wilderness")&&state.get(parent.id()).contains(world,3,0,3),"Resurvey left stale child data or changed the parent name");
        PropertySubareaState subareas=new PropertySubareaState();var unit=subareas.create(state.get(parent.id()),"Wohnung 1","Wohnung",cut,owner);
        require(unit!=null&&subareas.assignBuilding(unit.id(),"building-1")&&subareas.forBuilding("building-1").size()==1,"Internal unit was not linked to its apartment building");
    }

    private static void verifyElectionLifecycle() {
        ElectionState state=new ElectionState();UUID actor=UUID.randomUUID();
        require(state.create(actor,"mayor-test","mayor","Bürgermeisterwahl","city-1","mayor")!=null,"Election draft was not created");
        require(state.addOption(actor,"mayor-test","Kandidat A",UUID.randomUUID().toString()),"First election option missing");
        require(state.addOption(actor,"mayor-test","Kandidat B",UUID.randomUUID().toString()),"Second election option missing");
        require(state.open(actor,"mayor-test",60)&&state.active(System.currentTimeMillis()).size()==1,"Election was not opened");
    }

    private static void verifyAtmDenominations() {
        List<CashDenomination> banknotes = CashDenomination.descending().stream().filter(value -> !value.coin()).toList();
        require(!banknotes.isEmpty() && banknotes.getLast() == CashDenomination.NEXUS_5,
                "ATM banknotes must start at five Nexus");
        require(banknotes.stream().noneMatch(CashDenomination::coin), "ATM denomination list contains coins");
        long remaining = 88_500L;
        int count = 0;
        for (CashDenomination denomination : banknotes) {
            count += (int) (remaining / denomination.cents());
            remaining %= denomination.cents();
        }
        require(remaining == 0 && count == 7, "Greedy ATM breakdown is not the optimal Euro-style combination");
    }

    private static void verifyMessengerRetentionAndMembership() {
        MessengerState state = new MessengerState();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        MessengerConversation direct = state.direct(first, second, 1_000L);
        require(direct != null && direct.members().size() == 2, "Direct conversation was not created");
        MessengerMessage message = state.send(direct.id(), first, "offline", 2_000L);
        require(message != null && state.unread(second, 2_001L) == 1, "Offline message was not retained as unread");
        state.purgeExpired(message.expiresAt());
        require(state.getForMember(direct.id(), second, message.expiresAt()).messages().isEmpty(), "Expired message was not deleted");
        MessengerConversation group = state.createGroup(first, "Einsatz", List.of(second, third), 3_000L);
        require(group != null && group.members().size() == 3, "Group conversation was not created");
        state.removeCitizen(first);
        MessengerConversation transferred = state.getForMember(group.id(), second, 3_001L);
        require(transferred != null && transferred.ownerId().equals(second.toString()), "Group ownership was not safely transferred");
        require(state.getForMember(direct.id(), second, 3_001L) == null, "Departed citizen direct chat was not removed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
