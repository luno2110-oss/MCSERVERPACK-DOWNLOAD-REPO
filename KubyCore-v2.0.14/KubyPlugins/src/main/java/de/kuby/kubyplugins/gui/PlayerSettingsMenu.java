package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.PlayerSettings;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** /bossbar (Inhalt der Info-Leiste); früher auch /settings (jetzt SettingsScreen). Klick schaltet an/aus. */
public final class PlayerSettingsMenu extends GuiMenu {

    private static final String[] GENERAL = {"msg", "friends", "tpa", "jump", "sounds", "scoreboard", "infobar"};
    private static final Material[] GENERAL_ICONS = {Material.PAPER, Material.PLAYER_HEAD, Material.ENDER_PEARL,
            Material.FEATHER, Material.NOTE_BLOCK, Material.OAK_SIGN, Material.WHITE_BANNER};
    private static final String[] BAR = {"infobar", "bar.money", "bar.job", "bar.place", "bar.time", "bar.quest"};
    private static final Material[] BAR_ICONS = {Material.WHITE_BANNER, Material.GOLD_NUGGET, Material.IRON_PICKAXE, Material.COMPASS,
            Material.CLOCK, Material.WRITABLE_BOOK};

    private final boolean bar;

    public PlayerSettingsMenu(KubyPlugins plugin, boolean bar) {
        super(plugin, Msg.parse(bar ? "<dark_gray>Info-Leiste" : "<dark_gray>Einstellungen"), 3);
        this.bar = bar;
    }

    @Override
    protected void build(Player p) {
        String[] keys = bar ? BAR : GENERAL;
        Material[] icons = bar ? BAR_ICONS : GENERAL_ICONS;
        int start = 10;
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            PlayerSettings.Option o = PlayerSettings.OPTIONS.get(key);
            boolean on = plugin.playerSettings().get(p.getUniqueId(), key);
            set(start + i, Items.make(icons[i], (on ? "<green>" : "<red>") + o.name(false) + ": " + (on ? "an" : "aus"),
                    List.of("<gray>" + o.desc(false), "", "<yellow>Klick zum Umschalten")), e -> {
                plugin.playerSettings().toggle(p.getUniqueId(), key);
                Sfx.play(plugin, p, Sfx.Kind.CLICK);
                open(p);
            });
        }
        if (bar) {
            set(18, Items.make(Material.COMPARATOR, "<white>Allgemeine Einstellungen", List.of("<gray>/settings", "", "<yellow>Klick")),
                    e -> plugin.settingsScreen().open(p, PlayerSettings.Tab.CHAT));
        } else {
            set(18, Items.make(Material.WHITE_BANNER, "<white>Inhalt der Info-Leiste", List.of("<gray>/bossbar", "", "<yellow>Klick")),
                    e -> new PlayerSettingsMenu(plugin, true).open(p));
        }
        addBack(p);
    }
}
