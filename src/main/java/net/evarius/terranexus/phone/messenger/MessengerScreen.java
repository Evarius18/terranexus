package net.evarius.terranexus.phone.messenger;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.management.CustomGuiService;
import net.evarius.terranexus.management.ManagementHubScreen;
import net.evarius.terranexus.phone.PhoneScreen;
import net.evarius.terranexus.phone.model.PhoneDirectoryContact;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MessengerScreen {
    private static final int[] CONTENT = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private MessengerScreen() {}

    public static void open(ServerPlayerEntity player, int page) {
        long now = System.currentTimeMillis();
        List<MessengerConversation> conversations = MessengerState.get(player.getServer()).forMember(player.getUuid(), now);
        SimpleInventory inventory = new SimpleInventory(54); Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.ECHO_SHARD, Text.translatable("gui.terranexus.phone.messenger.title"),
                Text.translatable("gui.terranexus.phone.messenger.retention", ConfigManager.phone().messengerRetentionHours));
        button(inventory, actions, 0, Items.PLAYER_HEAD, Text.translatable("gui.terranexus.phone.messenger.new_direct"), ignored -> chooseDirect(player, 0));
        button(inventory, actions, 8, Items.NAME_TAG, Text.translatable("gui.terranexus.phone.messenger.new_group"), ignored -> groupName(player));
        int pages = Math.max(1, (conversations.size() + CONTENT.length - 1) / CONTENT.length); int safe = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < CONTENT.length; i++) {
            int index = safe * CONTENT.length + i; if (index >= conversations.size()) break;
            MessengerConversation conversation = conversations.get(index);
            int unread = unread(conversation, player.getUuidAsString());
            String label = label(player.getServer(), conversation, player.getUuid());
            String detail = conversation.messages().isEmpty() ? Text.translatable("gui.terranexus.phone.messenger.empty").getString()
                    : preview(conversation.messages().getLast().body()) + " · " + remaining(conversation.messages().getLast().expiresAt(), now);
            if (unread > 0) detail = unread + " neu · " + detail;
            ManagementHubScreen.display(inventory, CONTENT[i], Items.ECHO_SHARD, label, detail);
            actions.put(CONTENT[i], ignored -> openChat(player, conversation.id(), 0));
        }
        if (safe > 0) button(inventory, actions, 45, Items.ARROW, Text.translatable("gui.terranexus.previous"), ignored -> open(player, safe - 1));
        if (safe + 1 < pages) button(inventory, actions, 53, Items.ARROW, Text.translatable("gui.terranexus.next"), ignored -> open(player, safe + 1));
        button(inventory, actions, 49, Items.BARRIER, Text.translatable("gui.terranexus.phone.apps"), ignored -> PhoneScreen.open(player));
        CustomGuiService.open(player, inventory, actions, Text.translatable("gui.terranexus.phone.messenger.title").formatted(Formatting.DARK_AQUA));
    }

    private static void chooseDirect(ServerPlayerEntity player, int page) {
        List<PhoneDirectoryContact> contacts = MessengerContactPolicy.contacts(player);
        selection(player, "gui.terranexus.phone.messenger.choose_contact", contacts, page, contact -> {
            MessengerConversation conversation = MessengerService.openDirect(player, contact.playerId(), System.currentTimeMillis());
            if (conversation == null) error(player, "gui.terranexus.phone.messenger.contact_required"); else openChat(player, conversation.id(), 0);
        }, () -> open(player, 0));
    }

    private static void groupName(ServerPlayerEntity player) {
        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        Map<Integer, java.util.function.Consumer<String>> textActions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.ECHO_SHARD,
                Text.translatable("gui.terranexus.phone.messenger.group_create"),
                Text.translatable("gui.terranexus.phone.messenger.group_create.detail"));
        ManagementHubScreen.display(inventory, 0, Items.NAME_TAG,
                Text.translatable("gui.terranexus.phone.messenger.group_name"), Text.literal("#max=48"));
        textActions.put(0, name -> {
            String normalized = name == null ? "" : name.strip().replace('\n', ' ');
            if (normalized.isBlank() || normalized.length() > 48) {
                error(player, "gui.terranexus.phone.messenger.group_invalid");
                groupName(player);
                return;
            }
            editMembers(player, null, normalized, new LinkedHashSet<>(), 0);
        });
        button(inventory, actions, 49, Items.BARRIER, Text.translatable("gui.terranexus.back"),
                ignored -> open(player, 0));
        CustomGuiService.openWithTextActions(player, inventory, actions, textActions,
                Text.translatable("gui.terranexus.phone.messenger.group_create").formatted(Formatting.DARK_AQUA));
    }

    private static void editMembers(ServerPlayerEntity player, String conversationId, String groupName, Set<UUID> selected, int page) {
        List<PhoneDirectoryContact> contacts = MessengerContactPolicy.contacts(player);
        SimpleInventory inventory = new SimpleInventory(54); Map<Integer, java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions = new HashMap<>();
        ManagementHubScreen.display(inventory, 4, Items.NAME_TAG, Text.translatable("gui.terranexus.phone.messenger.members"), Text.literal(groupName));
        int pages = Math.max(1, (contacts.size() + CONTENT.length - 1) / CONTENT.length); int safe = Math.max(0, Math.min(page, pages - 1));
        for (int i=0;i<CONTENT.length;i++) { int index=safe*CONTENT.length+i; if(index>=contacts.size())break; PhoneDirectoryContact contact=contacts.get(index); UUID id=contact.playerId();
            boolean active=selected.contains(id); ManagementHubScreen.display(inventory,CONTENT[i],active?Items.LIME_DYE:Items.GRAY_DYE,contact.name(),contact.number()+"\n"+Text.translatable(active?"gui.terranexus.phone.messenger.selected":"gui.terranexus.phone.messenger.not_selected").getString());
            actions.put(CONTENT[i],ignored->{Set<UUID> changed=new LinkedHashSet<>(selected);if(!changed.add(id))changed.remove(id);editMembers(player,conversationId,groupName,changed,safe);}); }
        if(safe>0)button(inventory,actions,45,Items.ARROW,Text.translatable("gui.terranexus.previous"),ignored->editMembers(player,conversationId,groupName,selected,safe-1));
        if(safe+1<pages)button(inventory,actions,53,Items.ARROW,Text.translatable("gui.terranexus.next"),ignored->editMembers(player,conversationId,groupName,selected,safe+1));
        button(inventory,actions,49,Items.LIME_DYE,Text.translatable("gui.terranexus.phone.messenger.save"),ignored->{
            if(conversationId==null){MessengerConversation created=MessengerService.createGroup(player,groupName,new ArrayList<>(selected),System.currentTimeMillis());if(created==null){error(player,"gui.terranexus.phone.messenger.group_invalid");open(player,0);}else openChat(player,created.id(),0);}
            else {if(!MessengerService.setGroupMembers(player,conversationId,new ArrayList<>(selected)))error(player,"gui.terranexus.phone.messenger.contact_required");openChat(player,conversationId,0);}});
        CustomGuiService.open(player,inventory,actions,Text.translatable("gui.terranexus.phone.messenger.members").formatted(Formatting.DARK_AQUA));
    }

    public static void openChat(ServerPlayerEntity player, String id, int page) {
        long now=System.currentTimeMillis(); MessengerState state=MessengerState.get(player.getServer()); MessengerConversation conversation=state.getForMember(id,player.getUuid(),now);
        if(conversation==null){open(player,0);return;} state.markRead(id,player.getUuid(),now);
        SimpleInventory inventory=new SimpleInventory(54);Map<Integer,java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();Map<Integer,java.util.function.Consumer<String>> textActions=new HashMap<>();
        ManagementHubScreen.display(inventory,4,Items.ECHO_SHARD,Text.literal(label(player.getServer(),conversation,player.getUuid())),Text.translatable("gui.terranexus.phone.messenger.retention",ConfigManager.phone().messengerRetentionHours));
        boolean maySend = MessengerContactPolicy.maySend(player, conversation);
        ManagementHubScreen.display(inventory,0,maySend?Items.FEATHER:Items.BARRIER,
                Text.translatable(maySend?"gui.terranexus.phone.messenger.send":"gui.terranexus.phone.messenger.contact_required"),
                maySend?Text.literal("#max=" + ConfigManager.phone().messengerMaximumMessageLength)
                        :Text.translatable("gui.terranexus.phone.messenger.contact_required.detail"));
        if(maySend)textActions.put(0,body->{MessengerMessage sent=MessengerService.send(player,id,body,System.currentTimeMillis());if(sent==null)error(player,"gui.terranexus.phone.messenger.invalid_message");else MessengerService.notifyRecipients(player,state.getForMember(id,player.getUuid(),System.currentTimeMillis()));openChat(player,id,0);});
        if(conversation.group()&&conversation.ownerId().equals(player.getUuidAsString()))button(inventory,actions,8,Items.COMPARATOR,Text.translatable("gui.terranexus.phone.messenger.manage_group"),ignored->{Set<UUID> selected=new LinkedHashSet<>();conversation.members().stream().filter(member->!member.equals(player.getUuidAsString())).map(UUID::fromString).forEach(selected::add);editMembers(player,id,conversation.name(),selected,0);});
        List<MessengerMessage> reversed=new ArrayList<>(conversation.messages());java.util.Collections.reverse(reversed);int pages=Math.max(1,(reversed.size()+CONTENT.length-1)/CONTENT.length);int safe=Math.max(0,Math.min(page,pages-1));
        for(int i=0;i<CONTENT.length;i++){int index=safe*CONTENT.length+i;if(index>=reversed.size())break;MessengerMessage message=reversed.get(index);String sender=message.senderId().equals(player.getUuidAsString())?Text.translatable("gui.terranexus.phone.messenger.you").getString():name(player.getServer(),UUID.fromString(message.senderId()));ManagementHubScreen.display(inventory,CONTENT[i],Items.PAPER,sender+" · "+TIME.format(Instant.ofEpochMilli(message.sentAt())),message.body()+"\n"+remaining(message.expiresAt(),now));}
        if(safe>0)button(inventory,actions,45,Items.ARROW,Text.translatable("gui.terranexus.previous"),ignored->openChat(player,id,safe-1));if(safe+1<pages)button(inventory,actions,53,Items.ARROW,Text.translatable("gui.terranexus.next"),ignored->openChat(player,id,safe+1));button(inventory,actions,49,Items.BARRIER,Text.translatable("gui.terranexus.back"),ignored->open(player,0));
        CustomGuiService.openWithTextActions(player,inventory,actions,textActions,Text.translatable("gui.terranexus.phone.messenger.chat").formatted(Formatting.DARK_AQUA));
    }

    private static void selection(ServerPlayerEntity player,String title,List<PhoneDirectoryContact> values,int page,java.util.function.Consumer<PhoneDirectoryContact> select,Runnable back){SimpleInventory inventory=new SimpleInventory(54);Map<Integer,java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions=new HashMap<>();ManagementHubScreen.display(inventory,4,Items.COMPASS,Text.translatable(title),values.isEmpty()?Text.translatable("gui.terranexus.phone.messenger.no_contacts"):Text.translatable("gui.terranexus.phone.messenger.choose_contact"));int pages=Math.max(1,(values.size()+CONTENT.length-1)/CONTENT.length);int safe=Math.max(0,Math.min(page,pages-1));for(int i=0;i<CONTENT.length;i++){int index=safe*CONTENT.length+i;if(index>=values.size())break;PhoneDirectoryContact value=values.get(index);ManagementHubScreen.display(inventory,CONTENT[i],Items.PLAYER_HEAD,value.name(),value.number());actions.put(CONTENT[i],ignored->select.accept(value));}if(safe>0)button(inventory,actions,45,Items.ARROW,Text.translatable("gui.terranexus.previous"),ignored->selection(player,title,values,safe-1,select,back));if(safe+1<pages)button(inventory,actions,53,Items.ARROW,Text.translatable("gui.terranexus.next"),ignored->selection(player,title,values,safe+1,select,back));button(inventory,actions,49,Items.BARRIER,Text.translatable("gui.terranexus.back"),ignored->back.run());CustomGuiService.open(player,inventory,actions,Text.translatable(title).formatted(Formatting.DARK_AQUA));}
    private static void button(SimpleInventory inventory,Map<Integer,java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity>> actions,int slot,net.minecraft.item.Item item,Text label,java.util.function.Consumer<net.minecraft.entity.player.PlayerEntity> action){ManagementHubScreen.display(inventory,slot,item,label,Text.empty());actions.put(slot,action);}
    private static int unread(MessengerConversation c,String player){long read=c.lastReadAt().getOrDefault(player,0L);return(int)c.messages().stream().filter(m->!m.senderId().equals(player)&&m.sentAt()>read).count();}
    private static String label(MinecraftServer server,MessengerConversation c,UUID viewer){if(c.group())return c.name();for(String member:c.members())if(!member.equals(viewer.toString()))try{return name(server,UUID.fromString(member));}catch(IllegalArgumentException ignored){}return "Unbekannt";}
    public static String name(MinecraftServer server,UUID id){CitizenIdentity identity=IdentityState.get(server).get(id);return identity==null?"Unbekannter Bürger":identity.firstName()+" "+identity.lastName();}
    private static String preview(String body){return body.length()>70?body.substring(0,67)+"…":body;}
    private static String remaining(long expires,long now){long minutes=Math.max(0,(expires-now+59999)/60000);if(minutes>=1440)return"Löschung in "+(minutes/1440)+" Tag(en)";if(minutes>=60)return"Löschung in "+(minutes/60)+" Std.";return"Löschung in "+minutes+" Min.";}
    private static void error(ServerPlayerEntity player,String key){player.sendMessage(Text.translatable(key).formatted(Formatting.RED),false);}
}
