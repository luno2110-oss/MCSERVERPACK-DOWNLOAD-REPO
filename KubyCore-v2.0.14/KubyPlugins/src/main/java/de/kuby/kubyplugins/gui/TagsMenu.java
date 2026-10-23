package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.TagService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class TagsMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public TagsMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Titel")), 4);
    }

    @Override
    protected void build(Player p) {
        UUID id = p.getUniqueId();
        String current = plugin.tags().activeId(id);

        set(4, Items.make(Material.NAME_TAG, "<gray>Kein Titel", List.of("<gray>Klick: Titel ablegen")),
                e -> {
                    plugin.tags().setActive(id, null);
                    done(p, "<gray>Titel abgelegt.");
                });

        List<TagService.Tag> tags = plugin.tags().all();
        for (int i = 0; i < tags.size() && i < SLOTS.length; i++) {
            TagService.Tag t = tags.get(i);
            boolean owned = plugin.tags().has(id, t.id());
            boolean selected = t.id().equals(current);
            Material icon = !owned ? Material.BARRIER : selected ? Material.ENCHANTED_BOOK : Material.NAME_TAG;
            String status = selected ? "<green>Aktiv" : owned ? "<gray>Klick: aktivieren" : "<red>Nicht freigeschaltet";
            set(SLOTS[i], Items.make(icon, t.display(), List.of(status)), e -> {
                if (!owned) {
                    Msg.send(p, "<red>Dieser Titel ist noch nicht freigeschaltet.");
                    return;
                }
                String err = plugin.tags().setActive(id, t.id());
                Sfx.play(plugin, p, err == null ? Sfx.Kind.PING : Sfx.Kind.ERROR);
                done(p, err == null ? "<green>Titel aktiviert." : "<red>" + err);
            });
        }
        addBack(p);
    }

    private void done(Player p, String message) {
        Msg.send(p, message);
        open(p);
    }
}
