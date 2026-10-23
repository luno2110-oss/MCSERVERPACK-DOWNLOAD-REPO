package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.StatsService;
import de.kuby.kubyplugins.service.TeamService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.LongConsumer;

/** Details und Aktionen für einen einzelnen Spieler. */
public final class AdminPlayerMenu extends AdminBase {

    private final UUID target;
    private final String name;

    public AdminPlayerMenu(KubyPlugins plugin, UUID target, String name) {
        super(plugin, "Spieler " + name, 4);
        this.target = target;
        this.name = name;
    }

    @Override
    protected void build(Player p) {
        StatsService.Stats s = plugin.stats().get(target);
        TeamService.Team team = plugin.teams().of(target);
        OfflinePlayer op = Bukkit.getOfflinePlayer(target);
        Player online = op.getPlayer();

        set(4, Items.head(op, "<gold>" + name, List.of(
                "<gray>Geld: <gold>" + Msg.coins(plugin.economy().balance(target)),
                "<gray>Juwelen: <aqua>" + Msg.coins(plugin.jewels().balance(target)),
                "<gray>Trophäen: <white>" + s.trophies + " <dark_gray>| <gray>Kills: <white>" + s.kills
                        + " <dark_gray>| <gray>Tode: <white>" + s.deaths,
                "<gray>Team: <white>" + (team == null ? "keins" : team.name()),
                "<gray>Status: " + (online != null ? "<green>online" : "<dark_gray>offline"))), null);

        set(10, Items.make(Material.GOLD_INGOT, "<green>$ +1000", List.of()),
                e -> { plugin.economy().deposit(target, 1000); done(p, "<green>+1000$"); });
        set(11, Items.make(Material.IRON_INGOT, "<red>$ -1000", List.of()),
                e -> done(p, plugin.economy().withdraw(target, 1000) ? "<green>-1000$" : "<red>Nicht genug$."));
        set(12, Items.make(Material.GOLD_BLOCK, "<gold>$ setzen", List.of("<gray>Betrag im Chat eingeben")),
                e -> ask(p, "Neuer Kontostand:", v -> plugin.economy().set(target, v)));

        set(14, Items.make(Material.EMERALD, "<aqua>Juwelen +100", List.of()),
                e -> { plugin.jewels().add(target, 100); done(p, "<green>+100 Juwelen"); });
        set(15, Items.make(Material.REDSTONE, "<red>Juwelen -100", List.of()),
                e -> done(p, plugin.jewels().take(target, 100) ? "<green>-100 Juwelen" : "<red>Nicht genug Juwelen."));
        set(16, Items.make(Material.DIAMOND, "<aqua>Juwelen hinzufügen", List.of("<gray>Betrag im Chat eingeben")),
                e -> ask(p, "Wie viele Juwelen hinzufügen?", v -> plugin.jewels().add(target, v)));

        set(19, Items.make(Material.BARRIER, "<red>Stats zurücksetzen", List.of(
                "<gray>Kills, Tode und Trophäen auf 0")),
                e -> {
                    s.kills = 0;
                    s.deaths = 0;
                    s.trophies = 0;
                    plugin.stats().save();
                    done(p, "<gray>Stats zurückgesetzt.");
                });
        set(20, Items.make(Material.GOLDEN_APPLE, "<gold>Trophäen setzen", List.of("<gray>Betrag im Chat eingeben")),
                e -> ask(p, "Neue Trophäenzahl:", v -> s.trophies = (int) Math.max(0, v)));

        set(22, Items.make(Material.ENDER_PEARL, "<light_purple>Zu ihm teleportieren", List.of()),
                e -> {
                    if (online == null) { done(p, "<red>Spieler ist offline."); return; }
                    p.teleportAsync(online.getLocation());
                    done(p, "<gray>Teleportiert.");
                });
        set(23, Items.make(Material.COMPASS, "<light_purple>Zu mir holen", List.of()),
                e -> {
                    if (online == null) { done(p, "<red>Spieler ist offline."); return; }
                    online.teleportAsync(p.getLocation());
                    done(p, "<gray>Spieler geholt.");
                });
        set(25, Items.make(Material.SKELETON_SKULL, "<red>Kopfgeld entfernen", List.of()),
                e -> {
                    long amount = plugin.pvp().claimBounty(target);
                    done(p, amount > 0 ? "<gray>Kopfgeld entfernt (" + Msg.coins(amount) + "$ verfallen)."
                            : "<gray>Kein Kopfgeld vorhanden.");
                });

        set(28, Items.make(Material.WHITE_BANNER, "<gold>Team verlassen", List.of()),
                e -> {
                    String err = plugin.teams().leave(target);
                    done(p, err == null ? "<gray>Team verlassen." : "<red>" + err);
                });
        set(29, Items.make(Material.CHEST, "<yellow>Abholbox leeren", List.of("<gray>Items werden gelöscht")),
                e -> {
                    plugin.abholbox().takeAll(target);
                    done(p, "<gray>Abholbox geleert.");
                });
        set(21, Items.make(Material.EXPERIENCE_BOTTLE, "<green>XP geben", List.of("<gray>Betrag im Chat eingeben")),
                e -> ask(p, "Wie viel XP geben?", v -> plugin.stats().get(target).xp += v));
        set(24, Items.make(Material.GLASS_BOTTLE, "<red>XP nehmen", List.of("<gray>Betrag im Chat eingeben")),
                e -> ask(p, "Wie viel XP nehmen?", v -> {
                    var st = plugin.stats().get(target);
                    st.xp = Math.max(0, st.xp - v);
                }));
        set(33, Items.make(Material.EXPERIENCE_BOTTLE, "<aqua>XP setzen", List.of("<gray>Neuer XP-Stand im Chat eingeben")),
                e -> ask(p, "Neuer XP-Stand:", v -> plugin.stats().get(target).xp = Math.max(0, v)));
        adminBack(p);
    }

    /** Fragt eine Zahl im Chat ab und führt die Aktion aus. */
    private void ask(Player p, String question, LongConsumer action) {
        plugin.prompts().ask(p, question, text -> {
            try {
                action.accept(Long.parseLong(text));
                Msg.send(p, "<green>Gespeichert.");
            } catch (NumberFormatException ex) {
                Msg.send(p, "<red>Keine gültige Zahl.");
            }
            open(p);
        });
    }
}
