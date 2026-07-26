package net.evarius.terranexus.item.custom;

import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.institution.Institution;
import net.evarius.terranexus.institution.InstitutionState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/** Institution-bound, owner-bound credential for physical time clock terminals. */
public final class EmployeeChipItem extends Item {
    public EmployeeChipItem(Settings settings) {
        super(settings);
    }

    public static ItemStack create(Item item, Institution institution, CitizenIdentity employee,
                                   ServerPlayerEntity issuer) {
        ItemStack stack = new ItemStack(item);
        NbtCompound data = new NbtCompound();
        data.putString("employee_uuid", employee.playerUuid());
        data.putString("institution_id", institution.id());
        data.putString("institution_name", institution.name());
        data.putString("employee_name", employee.firstName() + " " + employee.lastName());
        data.putString("issued_by", issuer.getUuidAsString());
        data.putLong("issued_at", System.currentTimeMillis());
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(
                "Dienstausweis · " + institution.name()).formatted(Formatting.AQUA));
        return stack;
    }

    public static String resolveInstitution(ServerPlayerEntity player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return null;
        NbtCompound data = component.copyNbt();
        String employeeId = data.getString("employee_uuid", "");
        String institutionId = data.getString("institution_id", "");
        Institution institution = InstitutionState.get(player.getServer()).get(institutionId);
        if (!employeeId.equals(player.getUuidAsString()) || institution == null
                || !institution.employees().containsKey(employeeId)) return null;
        return institutionId;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              TooltipDisplayComponent displayComponent,
                              Consumer<Text> textConsumer, TooltipType type) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) {
            textConsumer.accept(Text.literal("Nicht personalisiert").formatted(Formatting.RED));
            return;
        }
        NbtCompound data = component.copyNbt();
        textConsumer.accept(Text.literal(data.getString("employee_name", "Unbekannt"))
                .formatted(Formatting.WHITE));
        textConsumer.accept(Text.literal(data.getString("institution_name", "Unbekannte Institution"))
                .formatted(Formatting.GRAY));
        textConsumer.accept(Text.literal("An einer Stempeluhr verwenden")
                .formatted(Formatting.DARK_GRAY));
    }
}
