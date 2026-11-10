package de.kuby.rematch.client.anim;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Testbefehl (nur im eigenen Client): /kanim play <name> [tempo], stop, list, reload, zeitlupe <faktor>, effekte an|aus.
 * Zum Anschauen F5 drücken (Ansicht von vorn/hinten).
 */
public final class KanimCommand {
    private KanimCommand() { }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(literal("kanim")
                .then(literal("play").then(argument("name", StringArgumentType.word())
                        .suggests((c, b) -> {
                            ClientAnims.library().names().forEach(b::suggest);
                            return b.buildFuture();
                        })
                        .executes(c -> play(c, 1.0))
                        .then(argument("tempo", DoubleArgumentType.doubleArg(0.05, 4)).executes(c -> play(c, DoubleArgumentType.getDouble(c, "tempo"))))))
                .then(literal("stop").executes(c -> {
                    ClientAnims.stop(self());
                    return 1;
                }))
                .then(literal("list").executes(c -> {
                    c.getSource().sendFeedback(Component.literal("§6Animationen: §f" + String.join(", ", ClientAnims.library().names())));
                    return 1;
                }))
                .then(literal("reload").executes(c -> {
                    int n = ClientAnims.reload();
                    c.getSource().sendFeedback(Component.literal("§a" + n + " Animationen geladen"));
                    return 1;
                }))
                .then(literal("zeitlupe").then(argument("faktor", DoubleArgumentType.doubleArg(0.02, 4)).executes(c -> {
                    ClientAnims.setTimeScale(DoubleArgumentType.getDouble(c, "faktor"));
                    c.getSource().sendFeedback(Component.literal("§7Zeitlupe: " + ClientAnims.timeScale()));
                    return 1;
                })))
                .then(literal("effekte").then(argument("an", StringArgumentType.word()).executes(c -> {
                    ClientAnims.effects = !StringArgumentType.getString(c, "an").startsWith("aus");
                    c.getSource().sendFeedback(Component.literal("§7Effekte: " + (ClientAnims.effects ? "an" : "aus")));
                    return 1;
                })))));
    }

    private static int self() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? -1 : mc.player.getId();
    }

    private static int play(CommandContext<FabricClientCommandSource> c, double speed) {
        String name = StringArgumentType.getString(c, "name");
        if (ClientAnims.library().get(name) == null) {
            c.getSource().sendError(Component.literal("Unbekannte Animation: " + name + " (/kanim list)"));
            return 0;
        }
        if (!ClientAnims.play(self(), name, speed, true)) {
            c.getSource().sendError(Component.literal("Animation läuft nicht (Priorität)"));
            return 0;
        }
        return 1;
    }
}
