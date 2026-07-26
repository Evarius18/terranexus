package net.evarius.terranexus.item.custom;

import net.evarius.terranexus.landlord.LandManagementState;
import net.evarius.terranexus.landlord.LandProperty;
import net.evarius.terranexus.landlord.LandlordState;
import net.evarius.terranexus.management.PropertyFinanceScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.UUID;
import java.util.function.Consumer;

/** RP key whose validity is derived from the current ownership or lease state. */
public final class PropertyKeyItem extends Item {
    public PropertyKeyItem(Settings settings) {
        super(settings);
    }

    public static ItemStack create(Item item, LandProperty property, UUID holder) {
        ItemStack stack = new ItemStack(item);
        NbtCompound data = new NbtCompound();
        data.putString("property_id", property.id());
        data.putString("holder_uuid", holder.toString());
        data.putString("property_name", property.name());
        data.putLong("issued_at", System.currentTimeMillis());
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Schlüssel · " + property.name()).formatted(Formatting.GOLD));
        return stack;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!(world instanceof ServerWorld) || !(user instanceof ServerPlayerEntity player))
            return ActionResult.SUCCESS;
        LandProperty property = resolve(player, player.getStackInHand(hand));
        if (property == null) {
            player.sendMessage(Text.literal("Dieser Schlüssel ist nicht mehr gültig.")
                    .formatted(Formatting.RED), false);
        } else {
            PropertyFinanceScreen.open(player, property);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              TooltipDisplayComponent displayComponent,
                              Consumer<Text> textConsumer, TooltipType type) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) {
            textConsumer.accept(Text.literal("Nicht codiert").formatted(Formatting.RED));
            return;
        }
        textConsumer.accept(Text.literal(component.copyNbt()
                .getString("property_name", "Unbekannte Immobilie")).formatted(Formatting.GRAY));
        textConsumer.accept(Text.literal("Gültigkeit wird am Server geprüft")
                .formatted(Formatting.DARK_GRAY));
    }

    private static LandProperty resolve(ServerPlayerEntity player, ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return null;
        NbtCompound data = component.copyNbt();
        if (!data.getString("holder_uuid", "").equals(player.getUuidAsString())) return null;
        LandProperty property = LandlordState.get(player.getServer())
                .get(data.getString("property_id", ""));
        if (property == null) return null;
        return property.isOwnedBy(player.getUuid())
                || LandManagementState.get(player.getServer()).isTenant(property.id(), player.getUuid())
                ? property : null;
    }
}
