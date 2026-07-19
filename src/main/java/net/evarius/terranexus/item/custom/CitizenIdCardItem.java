package net.evarius.terranexus.item.custom;

import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityState;
import net.evarius.terranexus.identity.IdentityScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;
import java.util.function.Consumer;

public class CitizenIdCardItem extends Item {
    public CitizenIdCardItem(Settings settings) {
        super(settings);
    }

    public static ItemStack createCard(Item item, CitizenIdentity identity) {
        ItemStack stack = new ItemStack(item);
        NbtCompound data = new NbtCompound();
        data.putString("citizen_uuid", identity.playerUuid());
        data.putString("citizen_number", identity.citizenNumber());
        data.putString("display_name", identity.firstName() + " " + identity.lastName());
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data);
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Personalausweis – " + identity.firstName() + " " + identity.lastName()).formatted(Formatting.AQUA));
        return stack;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world instanceof ServerWorld serverWorld) {
            CitizenIdentity identity = resolve(serverWorld, user.getStackInHand(hand));
            if (identity == null) {
                user.sendMessage(Text.literal("Dieser Ausweis ist ungültig oder wurde widerrufen.").formatted(Formatting.RED), false);
            } else {
                IdentityScreen.open((net.minecraft.server.network.ServerPlayerEntity) user, identity);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent,
                              Consumer<Text> textConsumer, TooltipType type) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component != null) {
            NbtCompound data = component.copyNbt();
            textConsumer.accept(Text.literal(data.getString("display_name", "Unbekannte Person")).formatted(Formatting.WHITE));
            textConsumer.accept(Text.literal(data.getString("citizen_number", "Ungültig")).formatted(Formatting.GRAY));
        }
        textConsumer.accept(Text.literal("Rechtsklick: Bürgerdaten prüfen").formatted(Formatting.DARK_GRAY));
    }

    private static CitizenIdentity resolve(ServerWorld world, ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return null;
        String uuid = component.copyNbt().getString("citizen_uuid", "");
        try {
            UUID citizenUuid = UUID.fromString(uuid);
            IdentityState state = IdentityState.get(world.getServer());
            return state.isApproved(citizenUuid) ? state.get(citizenUuid) : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static void sendIdentity(PlayerEntity player, CitizenIdentity identity) {
        player.sendMessage(Text.literal("§8§m---------------- §bBürgerinfo §8§m----------------"), false);
        player.sendMessage(Text.literal("§7Bürgernummer: §f" + identity.citizenNumber()), false);
        player.sendMessage(Text.literal("§7Name: §f" + identity.firstName() + " " + identity.lastName()), false);
        player.sendMessage(Text.literal("§7Geboren: §f" + identity.birthDate() + " in " + identity.birthPlace()), false);
        player.sendMessage(Text.literal("§7Geburtsland: §f" + identity.birthCountry()), false);
        player.sendMessage(Text.literal("§7Nationalität: §f" + identity.nationality()), false);
        player.sendMessage(Text.literal("§7Geschlecht: §f" + identity.gender()), false);
        player.sendMessage(Text.literal("§7Meldeadresse: §f" + identity.address()), false);
    }
}
