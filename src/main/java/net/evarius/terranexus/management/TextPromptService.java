package net.evarius.terranexus.management;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Consumer;

/** Shared custom text-entry surface replacing Vanilla anvil menus. */
public final class TextPromptService {
    private TextPromptService() {}

    public static void open(ServerPlayerEntity player, String title, Consumer<String> submit) {
        open(player, title, "", submit, () -> {});
    }

    public static void open(ServerPlayerEntity player, String title, String initialValue,
                            Consumer<String> submit, Runnable cancel) {
        CustomSearchService.open(player, title, "Eingabe", initialValue, 0, 80, submit, cancel);
    }
}
