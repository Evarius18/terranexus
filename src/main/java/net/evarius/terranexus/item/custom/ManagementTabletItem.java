package net.evarius.terranexus.item.custom;

import net.evarius.terranexus.identity.CitizenIdentity;
import net.evarius.terranexus.identity.IdentityScreen;
import net.evarius.terranexus.identity.IdentityState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.evarius.terranexus.identity.AuthorityState;
import net.evarius.terranexus.management.ImmigrationScreen;
import net.evarius.terranexus.management.ManagementHubScreen;

public class ManagementTabletItem extends Item {
    public ManagementTabletItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity player) {
            AuthorityState authority = AuthorityState.get(serverWorld.getServer());
            boolean immigrationStaff = authority.has(player.getUuid(), AuthorityState.CIVIL_REGISTRAR)
                    || authority.has(player.getUuid(), AuthorityState.IMMIGRATION_OFFICER)
                    || authority.has(player.getUuid(), AuthorityState.SUPPORTER);
            if (immigrationStaff) {
                ImmigrationScreen.open(player);
            } else {
                ManagementHubScreen.open(player);
            }
        }
        return ActionResult.SUCCESS;
    }
}
