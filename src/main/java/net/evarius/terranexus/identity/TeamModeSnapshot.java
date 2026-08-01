package net.evarius.terranexus.identity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;

import java.util.List;

public record TeamModeSnapshot(String mode,String role,long activatedAt,List<ItemStack> inventory,float health,
                               int food,float saturation,int experienceLevel,int totalExperience,float experienceProgress,
                               List<StoredStatusEffect> effects){
    public static final Codec<TeamModeSnapshot> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("mode").forGetter(TeamModeSnapshot::mode),Codec.STRING.fieldOf("role").forGetter(TeamModeSnapshot::role),
            Codec.LONG.fieldOf("activated_at").forGetter(TeamModeSnapshot::activatedAt),ItemStack.OPTIONAL_CODEC.listOf().fieldOf("inventory").forGetter(TeamModeSnapshot::inventory),
            Codec.FLOAT.fieldOf("health").forGetter(TeamModeSnapshot::health),Codec.INT.fieldOf("food").forGetter(TeamModeSnapshot::food),
            Codec.FLOAT.fieldOf("saturation").forGetter(TeamModeSnapshot::saturation),Codec.INT.fieldOf("experience_level").forGetter(TeamModeSnapshot::experienceLevel),
            Codec.INT.fieldOf("total_experience").forGetter(TeamModeSnapshot::totalExperience),Codec.FLOAT.fieldOf("experience_progress").forGetter(TeamModeSnapshot::experienceProgress),
            StoredStatusEffect.CODEC.listOf().optionalFieldOf("effects",List.of()).forGetter(TeamModeSnapshot::effects)
    ).apply(i,TeamModeSnapshot::new));
}
