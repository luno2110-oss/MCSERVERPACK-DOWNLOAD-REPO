package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.BattlepassService;
import de.kuby.kubyplugins.service.BattlepassService.Reward;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Admin-Editor für Battlepass-Belohnungen: hinzufügen, anklicken, Shift-Klick entfernt. */
public final class BattlepassEditorMenu extends GuiMenu {

    private static final int PER_PAGE = 45;
    private final int page;

    public BattlepassEditorMenu(KubyPlugins plugin, int page) {
        super(plugin, Msg.parse("<dark_red>" + SmallCaps.of("battlepass-editor")), 6);
        this.page = Math.max(0, page);
    }

    @Override
    protected void build(Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Keine Berechtigung.");
            return;
        }
        List<Reward> all = plugin.battlepass().rewards();
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
            Reward r = all.get(start + i);
            int idx = start + i;
            set(i, Items.make(BattlepassService.iconOf(r), "<gold>Stufe " + r.level() + " <dark_gray>| "
                    + (BattlepassService.PREMIUM.equals(r.track()) ? "<light_purple>Premium" : "<green>Free"), List.of(
                    "<gray>Art: <white>" + r.type(),
                    "<gray>Wert: <white>" + r.value(),
                    "<red>Shift + Klick: entfernen")),
                    e -> {
                        if (!e.isShiftClick()) return;
                        removeAt(p, idx);
                    });
        }
        for (int i = 45; i < 54; i++) set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);

        set(45, Items.make(Material.ARROW, "<gray>Zurück zum Admin-Panel", List.of()),
                e -> new AdminMenu(plugin).open(p));
        if (page > 0) {
            set(46, Items.make(Material.ARROW, "<gray>Vorherige Seite", List.of()),
                    e -> new BattlepassEditorMenu(plugin, page - 1).open(p));
        }
        set(49, Items.make(Material.EMERALD, "<green>Belohnung hinzufügen", List.of(
                "<gray>Format: STUFE SPUR ART WERT",
                "<gray>Spur: FREE oder PREMIUM",
                "<gray>Art: COINS, XP, JEWELS, COMMAND, ITEM",
                "<gray>Beispiel: 30 PREMIUM ITEM NETHERITE_INGOT:2")),
                e -> plugin.prompts().ask(p, "Belohnung eingeben (STUFE SPUR ART WERT):", text -> {
                    if (addReward(text)) {
                        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                        Msg.send(p, "<green>Belohnung hinzugefügt.");
                    } else {
                        Sfx.play(plugin, p, Sfx.Kind.ERROR);
                        Msg.send(p, "<red>Format passt nicht, z. B.: 30 PREMIUM ITEM NETHERITE_INGOT:2");
                    }
                    new BattlepassEditorMenu(plugin, page).open(p);
                }));
        if (start + PER_PAGE < all.size()) {
            set(53, Items.make(Material.ARROW, "<gray>Nächste Seite", List.of()),
                    e -> new BattlepassEditorMenu(plugin, page + 1).open(p));
        }
    }

    private void removeAt(Player p, int index) {
        List<Reward> all = new ArrayList<>(plugin.battlepass().rewards());
        if (index < 0 || index >= all.size()) return;
        all.remove(index);
        store(all);
        Sfx.play(plugin, p, Sfx.Kind.FAIL);
        Msg.send(p, "<gray>Belohnung entfernt.");
        open(p);
    }

    private boolean addReward(String text) {
        String[] parts = text.trim().split(" +", 4);
        if (parts.length < 4) return false;
        try {
            int level = Integer.parseInt(parts[0]);
            String track = parts[1].toUpperCase(Locale.ROOT);
            String type = parts[2].toUpperCase(Locale.ROOT);
            if (level < 1 || level > BattlepassService.MAX_LEVEL) return false;
            if (!track.equals(BattlepassService.FREE) && !track.equals(BattlepassService.PREMIUM)) return false;
            if (!List.of("COINS", "XP", "JEWELS", "COMMAND", "ITEM").contains(type)) return false;
            List<Reward> all = new ArrayList<>(plugin.battlepass().rewards());
            all.add(new Reward(level, track, type, parts[3].trim()));
            store(all);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private void store(List<Reward> all) {
        List<String> lines = new ArrayList<>();
        for (Reward r : all) lines.add(r.level() + ";" + r.track() + ";" + r.type() + ";" + r.value());
        plugin.getConfig().set("battlepass.rewards", lines);
        plugin.saveConfig();
    }
}
