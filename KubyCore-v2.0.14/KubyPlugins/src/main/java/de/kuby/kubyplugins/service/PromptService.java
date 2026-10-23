package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Eingabe per Chat für GUIs: Beträge, Namen, Texte. Die nächste Chatnachricht wird abgefangen. */
public final class PromptService {

    private final KubyPlugins plugin;
    private final Map<UUID, Consumer<String>> waiting = new ConcurrentHashMap<>();

    public PromptService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String question, Consumer<String> handler) {
        waiting.put(p.getUniqueId(), handler);
        p.closeInventory();
        Msg.send(p, "<gold>" + question + " <gray>(in den Chat schreiben, 'abbrechen' zum Stoppen)");
    }

    public boolean isWaiting(UUID id) {
        return waiting.containsKey(id);
    }

    /** Muss im Hauptthread laufen. */
    public void answer(Player p, String text) {
        Consumer<String> handler = waiting.remove(p.getUniqueId());
        if (handler == null) return;
        if (text.trim().equalsIgnoreCase("abbrechen")) {
            Msg.send(p, "<gray>Abgebrochen.");
            return;
        }
        handler.accept(text.trim());
    }
}
