package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.evarius.terranexus.economy.EconomyState;
import net.evarius.terranexus.config.TerraNexusConfig;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.*;

public class LandManagementState extends PersistentState {
    private static final Codec<LandManagementState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, LandAccess.CODEC).optionalFieldOf("access", Map.of()).forGetter(state -> state.access),
            Codec.unboundedMap(Codec.STRING, LandSaleOffer.CODEC).optionalFieldOf("sales", Map.of()).forGetter(state -> state.sales),
            Codec.unboundedMap(Codec.STRING, LandLease.CODEC).optionalFieldOf("leases", Map.of()).forGetter(state -> state.leases),
            Codec.unboundedMap(Codec.STRING, AdministrativeArea.CODEC).optionalFieldOf("areas", Map.of()).forGetter(state -> state.areas),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("property_areas", Map.of()).forGetter(state -> state.propertyAreas),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("addresses", Map.of()).forGetter(state -> state.addresses)
    ).apply(instance, LandManagementState::new));
    private static final PersistentStateType<LandManagementState> TYPE = new PersistentStateType<>("terranexus_land_management", LandManagementState::new, CODEC, DataFixTypes.LEVEL);
    private final Map<String, LandAccess> access; private final Map<String, LandSaleOffer> sales; private final Map<String, LandLease> leases;
    private final Map<String, AdministrativeArea> areas; private final Map<String, String> propertyAreas;private final Map<String,String> addresses;
    public LandManagementState() { this(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),new HashMap<>()); }
    private LandManagementState(Map<String, LandAccess> access, Map<String, LandSaleOffer> sales, Map<String, LandLease> leases, Map<String, AdministrativeArea> areas, Map<String, String> propertyAreas,Map<String,String> addresses) { this.access=new HashMap<>(access);this.sales=new HashMap<>(sales);this.leases=new HashMap<>(leases);this.areas=new HashMap<>(areas);this.propertyAreas=new HashMap<>(propertyAreas);this.addresses=new HashMap<>(addresses); }
    public static LandManagementState get(MinecraftServer server) { return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE); }
    public LandAccess access(String propertyId) { return access.getOrDefault(propertyId, new LandAccess(propertyId, Map.of(), Map.of())); }
    public void setAccess(LandAccess value) { access.put(value.propertyId(), value); markDirty(); }
    public LandSaleOffer sale(String propertyId) { return sales.get(propertyId); }
    public Collection<LandSaleOffer> sales() { return List.copyOf(sales.values()); }
    public void offerSale(LandSaleOffer offer) { sales.put(offer.propertyId(), offer); markDirty(); }
    public void cancelSale(String propertyId) { if(sales.remove(propertyId)!=null)markDirty(); }
    public LandLease lease(String propertyId) { return leases.get(propertyId); }
    public Collection<LandLease> leases() { return List.copyOf(leases.values()); }
    public void setLease(LandLease lease) { leases.put(lease.propertyId(), lease); markDirty(); }
    public void endLease(String propertyId) { if(leases.remove(propertyId)!=null)markDirty(); }
    public boolean isTenant(String propertyId, UUID player) { LandLease lease=leases.get(propertyId);return lease!=null&&lease.active()&&lease.tenantId().equals(player.toString()); }
    public AdministrativeArea createArea(String name,String type,String parent,String ownerType,String ownerId){String id=UUID.randomUUID().toString();AdministrativeArea area=new AdministrativeArea(id,name,type,parent,ownerType,ownerId);areas.put(id,area);markDirty();return area;}
    public List<AdministrativeArea> areas(){return areas.values().stream().sorted(Comparator.comparing(AdministrativeArea::type).thenComparing(AdministrativeArea::name)).toList();}
    public AdministrativeArea area(String id){return areas.get(id);}
    public String propertyArea(String propertyId){return propertyAreas.getOrDefault(propertyId,"");}
    public void assignArea(String propertyId,String areaId){if(areaId.isBlank())propertyAreas.remove(propertyId);else propertyAreas.put(propertyId,areaId);markDirty();}
    public String address(String propertyId){return addresses.getOrDefault(propertyId,"Nicht eingetragen");}public void setAddress(String propertyId,String address){if(address.isBlank())addresses.remove(propertyId);else addresses.put(propertyId,address);markDirty();}
    public void removePropertyData(String propertyId){access.remove(propertyId);sales.remove(propertyId);leases.remove(propertyId);propertyAreas.remove(propertyId);addresses.remove(propertyId);markDirty();}
    public static long periodMillis(int days){return (long)Math.max(1,days)*TerraNexusConfig.get().rentDayDurationMinutes*60_000L;}
    public void processRents(MinecraftServer server,long now){EconomyState economy=EconomyState.get(server);boolean changed=false;for(LandLease lease:new ArrayList<>(leases.values())){if(!lease.active()||lease.nextDueAt()>now)continue;UUID tenantId;try{tenantId=UUID.fromString(lease.tenantId());}catch(IllegalArgumentException ignored){leases.remove(lease.propertyId());changed=true;continue;}boolean paid=economy.transfer(EconomyState.playerAccount(tenantId),lease.landlordAccount(),lease.rent());int missed=paid?0:lease.missedPayments()+1;if(missed>=3){leases.remove(lease.propertyId());var online=server.getPlayerManager().getPlayer(tenantId);if(online!=null)online.sendMessage(net.minecraft.text.Text.literal("Mietvertrag nach drei offenen Zahlungen beendet."),false);}else{long next=now+periodMillis(lease.periodDays());leases.put(lease.propertyId(),new LandLease(lease.propertyId(),lease.landlordAccount(),lease.tenantId(),lease.rent(),lease.deposit(),lease.periodDays(),next,missed,true));}changed=true;}if(changed)markDirty();}
}
