package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Fängt Chat-Eingaben für Admin-Prompts ab und prüft Quiz-Antworten. */
public final class QuizListener implements Listener {

    private final KubyPlugins plugin;

    public QuizListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        Player player = e.getPlayer();
        if (plugin.prompts().isWaiting(player.getUniqueId())) {
            e.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.prompts().answer(player, text));
            return;
        }
        String tag = plugin.tags().activeDisplay(player.getUniqueId());
        if (tag != null) {
            e.renderer((source, displayName, message, viewer) ->
                    Msg.parse("<gray>[" + tag + "<gray>] ").append(displayName)
                            .append(Component.text(": ")).append(message));
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.quiz().onChat(player, text));
    }
}
