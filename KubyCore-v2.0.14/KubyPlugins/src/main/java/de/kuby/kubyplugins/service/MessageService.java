package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Private Nachrichten: /msg, /r und die Inbox für Spieler, die offline waren. */
public final class MessageService {

    private record Entry(String from, String text, long time) {}

    private final KubyPlugins plugin;
    private final Map<UUID, UUID> lastContact = new HashMap<>();
    private final Map<UUID, List<Entry>> inbox = new HashMap<>();

    public MessageService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public synchronized void send(Player from, UUID toId, String toName, String text) {
        lastContact.put(from.getUniqueId(), toId);
        lastContact.put(toId, from.getUniqueId());
        Player to = Bukkit.getPlayer(toId);
        if (to != null) {
            Msg.send(to, "<gray>[<white>" + from.getName() + " <gray>→ <white>dir<gray>] " + Msg.escape(text)); Sfx.play(plugin, to, Sfx.Kind.PING);
        } else {
            inbox.computeIfAbsent(toId, k -> new ArrayList<>())
                    .add(new Entry(from.getName(), text, System.currentTimeMillis()));
        }
        Msg.send(from, "<gray>[<white>dir <gray>→ <white>" + toName + "<gray>] " + Msg.escape(text));
    }

    public synchronized UUID lastContact(UUID id) {
        return lastContact.get(id);
    }

    /** Zeigt die Inbox an und leert sie. */
    public synchronized void showInbox(Player p) {
        List<Entry> list = inbox.remove(p.getUniqueId());
        if (list == null || list.isEmpty()) {
            Msg.send(p, "<gray>Keine verpassten Nachrichten.");
            return;
        }
        Msg.send(p, "<gold>Verpasste Nachrichten (" + list.size() + "):");
        for (Entry e : list) {
            Msg.send(p, "<gray>" + e.from() + "<dark_gray>: <white>" + Msg.escape(e.text()));
        }
    }
}
