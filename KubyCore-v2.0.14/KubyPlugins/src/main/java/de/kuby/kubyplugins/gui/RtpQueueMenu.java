package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class RtpQueueMenu extends GuiMenu {

    public RtpQueueMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Duo-RTP")), 3);
    }

    @Override
    protected void build(Player p) {
        boolean queued = plugin.rtpQueue().inQueue(p.getUniqueId());
        set(4, Items.make(Material.ENDER_PEARL, "<light_purple>" + SmallCaps.of("Duo-RTP"), List.of(
                "<gray>Zwei Spieler werden zusammen an einen Zufallsort gesetzt.",
                "<gray>Dann geht es gegeneinander.",
                "<gray>Status: " + (queued ? "<yellow>wartend" : "<dark_gray>nicht in der Warteschlange"))), null);

        set(11, Items.make(Material.LIME_DYE, "<green>Beitreten", List.of(
                        "<gray>Sobald jemand anderes wartet, geht es los")),
                e -> {
                    String err = plugin.rtpQueue().join(p);
                    if (err != null) Msg.send(p, "<red>" + err);
                    open(p);
                });
        set(15, Items.make(Material.RED_DYE, "<red>Verlassen", List.of()),
                e -> {
                    String err = plugin.rtpQueue().leave(p.getUniqueId());
                    Msg.send(p, err == null ? "<gray>Aus der Warteschlange entfernt." : "<red>" + err);
                    open(p);
                });
        addBack(p);
    }
}
