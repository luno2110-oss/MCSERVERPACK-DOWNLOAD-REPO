package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Einstellungen ohne Config-Datei: Kategorien, Werte per Klick.
 * Links +, rechts -, Shift = zehnfacher Schritt. Texte werden im Chat eingegeben.
 */
public final class SettingsMenu extends GuiMenu {

    public enum Kind { INT, DOUBLE, BOOL, TEXT }

    public record Setting(String path, String label, Kind kind, double min, double max, double step, String hint) {}
    public record Category(String name, Material icon, List<Setting> settings) {}

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private static Setting i(String path, String label, double min, double max, double step, String hint) {
        return new Setting(path, label, Kind.INT, min, max, step, hint);
    }

    private static Setting d(String path, String label, double min, double max, double step, String hint) {
        return new Setting(path, label, Kind.DOUBLE, min, max, step, hint);
    }

    private static Setting b(String path, String label, String hint) {
        return new Setting(path, label, Kind.BOOL, 0, 1, 1, hint);
    }

    private static Setting t(String path, String label, String hint) {
        return new Setting(path, label, Kind.TEXT, 0, 0, 0, hint);
    }

    static final List<Category> CATEGORIES = List.of(
            new Category("Wirtschaft", Material.GOLD_INGOT, List.of(
                    d("sellmulti.step", "Verkaufs-Stufe: Zuschlag", 0, 1, 0.01, "Zuschlag pro Stufe"),
                    i("sellmulti.base-cost", "Verkaufs-Stufe: Kosten", 100, 100000, 100, "Kosten der ersten Stufe"),
                    i("sellmulti.max-level", "Verkaufs-Stufe: Maximum", 1, 50, 1, "Höchste Stufe"),
                    i("coinflip.max-bet", "Coinflip: Höchsteinsatz", 100, 1000000, 500, "Maximaler Einsatz"),
                    i("team.create-cost", "Team gründen: Kosten", 0, 1000000, 100, "$ zum Gründen"),
                    i("team.max-members", "Team: Mitglieder", 2, 20, 1, "Maximale Teamgröße"))),
            new Category("Markt & Handel", Material.CHEST, List.of(
                    i("market.tax-percent", "Markt: Steuer", 0, 50, 1, "Steuer auf Verkäufe in %"),
                    i("market.max-listings", "Markt: Angebote", 1, 100, 1, "Angebote pro Spieler"))),
            new Category("Homes & Teleport", Material.RED_BED, List.of(
                    i("homes.default-limit", "Homes: Standard", 1, 20, 1, "Homes ohne Rang"),
                    i("homes.max-limit", "Homes: Maximum", 1, 50, 1, "Höchstanzahl Homes"),
                    i("homes.teleport-delay-seconds", "Teleport-Wartezeit", 0, 30, 1, "Sekunden stillstehen"),
                    i("rtp.radius", "Zufalls-Teleport: Radius", 100, 50000, 500, "Radius um die Mitte"))),
            new Category("Daily & Tagespot", Material.CLOCK, List.of(
                    i("daily.base-reward", "Daily: Grundbelohnung", 0, 100000, 100, "$ am 1. Tag"),
                    i("daily.reward-per-day", "Daily: Steigerung", 0, 10000, 10, "Mehr$ pro Tag"),
                    i("daily.cycle-bonus", "Daily: Zyklus-Bonus", 0, 1000000, 1000, "Bonus am 30. Tag"),
                    i("dailypot.ticket-price", "Tagespot: Losspreis", 1, 100000, 50, "$ pro Los"),
                    i("dailypot.max-tickets", "Tagespot: Max. Lose", 1, 1000, 5, "Lose pro Spieler am Tag"),
                    i("dailypot.draw-hour", "Tagespot: Ziehung", 0, 23, 1, "Stunde (Berliner Zeit)"),
                    i("dailypot.tax-percent", "Tagespot: Steuer", 0, 50, 1, "Steuer in %"))),
            new Category("Kampf & Trophäen", Material.IRON_SWORD, List.of(
                    i("trophies.base", "Trophäen: Basis", 1, 500, 5, "Grundbetrag pro Kill"),
                    i("trophies.league-step", "Trophäen: Liga-Schritt", 0, 100, 1, "Zuschlag je Liga Unterschied"),
                    i("trophies.min", "Trophäen: Minimum", 0, 100, 1, "Mindestbetrag"),
                    i("trophies.max", "Trophäen: Maximum", 1, 500, 5, "Höchstbetrag"),
                    i("pvp.newbie-minutes", "PvP: Neulingsschutz", 0, 600, 5, "Minuten nach dem Beitritt"),
                    i("pvp.spawn-radius", "PvP: Spawn-Schutz", 0, 500, 5, "Radius um den Spawn"),
                    i("xp.per-kill", "XP pro Kill", 0, 10000, 5, "Basis-XP"))),
            new Category("Duelle & Turnier", Material.TOTEM_OF_UNDYING, List.of(
                    i("duel.tax-percent", "Duell: Steuer", 0, 50, 1, "Steuer auf den Pot in %"),
                    i("duel.trophies-win", "Duell: Trophäen Sieg", 0, 500, 5, "Gewinn"),
                    i("duel.trophies-loss", "Duell: Trophäen Niederlage", 0, 500, 5, "Verlust"),
                    i("tournament.reward", "Turnier: Preis", 0, 1000000, 500, "$ für den Sieger"),
                    i("tournament.max-players", "Turnier: Teilnehmer", 2, 64, 2, "Maximum"),
                    i("tournament.trophies-per-win", "Turnier: Trophäen pro Sieg", 0, 10000, 5, "Bei jedem Sieg"),
                    i("tournament.trophies-champion", "Turnier: Trophäen Titel", 0, 100000, 25, "Bonus für den Sieger"))),
            new Category("Booster & Wochen", Material.BLAZE_POWDER, List.of(
                    i("booster.start-hour", "Booster: Start", 0, 23, 1, "Stunde (Berliner Zeit)"),
                    i("booster.end-hour", "Booster: Ende", 1, 24, 1, "Stunde (24 = Mitternacht)"),
                    d("booster.multiplier", "Booster: Faktor", 1, 10, 0.1, "XP und Daily"),
                    i("weekly.jewel-reward", "Wochenpreis: Juwelen", 0, 100000, 100, "Für Baltop-Platz 1"))),
            new Category("Events & Sound", Material.NOTE_BLOCK, List.of(
                    i("quiz.interval-minutes", "Quiz: Abstand", 1, 600, 5, "Minuten (Neustart nötig)"),
                    t("keyall.command", "Universal-Schlüssel: Befehl", "Befehl mit %player%"),
                    b("sfx.enabled", "Soundeffekte", "An oder aus"))),
            new Category("Raid-Versicherung", Material.SHIELD, List.of(
                    i("insurance.premium-percent", "Prämie", 1, 100, 1, "Anteil am Wert in %"),
                    i("insurance.payout-percent", "Auszahlung", 1, 100, 1, "Anteil am Wert in %"),
                    i("insurance.days", "Laufzeit", 1, 30, 1, "Tage"),
                    i("insurance.max-chests", "Max. Kisten", 1, 20, 1, "Pro Spieler"),
                    i("insurance.min-premium", "Mindestprämie", 0, 100000, 10, "$"))),
            new Category("Lager & Shop", Material.CHEST, List.of(
                    i("enderchest.slot-xp", "Enderchest: Platz-Preis", 0, 1000000, 100, "XP pro Platz"),
                    i("enderchest.stage2-xp", "Enderchest: Stufe 2", 0, 10000000, 1000, "XP"),
                    i("enderchest.stage3-xp", "Enderchest: Stufe 3", 0, 10000000, 1000, "XP"),
                    i("shop-storage.xp-2", "Abholbox: Seite 2", 0, 1000000, 100, "XP"),
                    i("shop-storage.xp-3", "Abholbox: Seite 3", 0, 1000000, 100, "XP"),
                    i("weekend.count", "Wochenende: Angebote", 1, 10, 1, "Pro Spieler"),
                    i("weekend.reroll-jewels", "Wochenende: Neuwurf", 0, 100000, 10, "Juwelen"),
                    i("daily-shop.count", "Daily Shop: Angebote", 1, 12, 1, "Pro Tag"))),
            new Category("Marktwirtschaft", Material.GOLD_NUGGET, List.of(
                    i("economy.tax-percent", "Steuer auf Verkäufe", 0, 50, 1, "Anteil in %"),
                    i("economy.invest-step", "Investition: Standardbetrag", 100, 1000000, 100, "$"),
                    i("economy.reactivate-multiplier", "Reaktivierung: Faktor", 1, 100, 1, "x Grundpreis"),
                    i("economy.target.frequent", "Richtwert: Häufig", 1, 100000, 100, "Stück pro Woche"),
                    i("economy.target.common", "Richtwert: Gewöhnlich", 1, 100000, 50, "Stück pro Woche"),
                    i("economy.target.rare", "Richtwert: Selten", 1, 100000, 10, "Stück pro Woche"),
                    i("economy.target.very_rare", "Richtwert: Sehr selten", 1, 100000, 5, "Stück pro Woche"),
                    i("economy.target.unique", "Richtwert: Einzigartig", 1, 100000, 1, "Stück pro Woche"),
                    i("economy.min-price.frequent", "Untergrenze: Häufig", 1, 1000, 1, "$"),
                    i("economy.min-price.common", "Untergrenze: Gewöhnlich", 1, 1000, 1, "$"),
                    i("economy.min-price.rare", "Untergrenze: Selten", 1, 1000, 1, "$"),
                    i("economy.min-price.very_rare", "Untergrenze: Sehr selten", 1, 1000, 1, "$"),
                    i("economy.min-price.unique", "Untergrenze: Einzigartig", 1, 1000, 1, "$"))),
            new Category("Battlepass", Material.ENCHANTED_BOOK, List.of(
                    i("battlepass.premium-price", "Premium-Pass: Preis", 0, 100000, 50, "Juwelen"),
                    i("battlepass.xp-per-level", "Stufe: BP-XP", 10, 10000, 10, "XP pro Stufe"))),
            new Category("Totenkiste", Material.CHEST, List.of(
                    i("totenkiste.slot-xp", "Platz: XP-Preis", 0, 1000000, 50, "XP pro Platz"),
                    i("totenkiste.level2-jewels", "Stufe 2: Preis", 0, 1000000, 10, "Juwelen"),
                    i("totenkiste.level3-jewels", "Stufe 3: Preis", 0, 1000000, 10, "Juwelen"),
                    i("totenkiste.protect-min-value", "Geschützte Items: Mindestwert", 1, 500, 5, "Wert ab dem Items gesichert sind"),
                    b("totenkiste.owner-access-level3", "Stufe 3: Besitzer darf öffnen", "An oder aus"))),
            new Category("Anzeige", Material.GLOWSTONE_DUST, List.of(
                    b("display.hotbar", "Geld und XP über der Hotbar", "An oder aus"))),
            new Category("Discord", Material.NAME_TAG, List.of(
                    b("discord.require-for-daily", "Daily nur mit Discord", "Verknüpfung nötig"),
                    b("discord.require-for-duels", "Duelle nur mit Discord", "Verknüpfung nötig"),
                    b("discord.require-for-meta", "Wochen-Meta nur mit Discord", "Verknüpfung nötig"))),
            new Category("Allgemein", Material.PAPER, List.of(
                    t("prefix", "Chat-Präfix", "MiniMessage erlaubt"))));

    private final int category;

    /** category -1 = Übersicht, sonst Index in CATEGORIES. */
    public SettingsMenu(KubyPlugins plugin, int category) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Einstellungen")), category < 0 ? 5 : 6);
        this.category = category;
    }

    @Override
    protected void build(Player p) {
        if (category < 0) {
            for (int c = 0; c < CATEGORIES.size() && c < SLOTS.length; c++) {
                Category cat = CATEGORIES.get(c);
                int idx = c;
                set(SLOTS[c], Items.make(cat.icon(), "<gold>" + SmallCaps.of(cat.name()), List.of(
                        "<gray>" + cat.settings().size() + " Einstellungen",
                        "<yellow>Klick zum Öffnen")), e -> new SettingsMenu(plugin, idx).open(p));
            }
            set(40, Items.make(Material.ARROW, "<gray>Zurück zum Admin-Panel", List.of()),
                    e -> new AdminMenu(plugin).open(p));
            set(43, Items.make(Material.GOLD_BLOCK, "<gold>Preisliste bearbeiten", List.of(
                            "<gray>Verkaufspreise der Items")),
                    e -> new WorthEditorMenu(plugin).open(p));
            return;
        }

        int i = 0;
        for (Setting s : CATEGORIES.get(category).settings()) {
            if (i >= SLOTS.length) break;
            set(SLOTS[i++], item(s), e -> edit(p, s, e));
        }
        set(49, Items.make(Material.ARROW, "<gray>Zurück zur Übersicht", List.of()),
                e -> new SettingsMenu(plugin, -1).open(p));
    }

    private ItemStack item(Setting s) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Aktuell: " + valueText(s));
        lore.add("<dark_gray>" + Msg.escape(s.hint()));
        switch (s.kind()) {
            case BOOL -> lore.add("<yellow>Klick: umschalten");
            case TEXT -> lore.add("<yellow>Klick: neuen Text eingeben");
            default -> {
                lore.add("<yellow>Links: +" + format(s, s.step()) + "  Rechts: -" + format(s, s.step()));
                lore.add("<yellow>Shift: zehnfacher Schritt");
            }
        }
        return Items.make(iconFor(s.kind()), "<gold>" + Msg.escape(s.label()), lore);
    }

    private String valueText(Setting s) {
        return switch (s.kind()) {
            case BOOL -> plugin.getConfig().getBoolean(s.path()) ? "<green>an" : "<red>aus";
            case TEXT -> "<white>" + Msg.escape(plugin.getConfig().getString(s.path(), ""));
            case INT -> "<white>" + (long) plugin.getConfig().getDouble(s.path());
            case DOUBLE -> "<white>" + String.format(Locale.ROOT, "%.2f", plugin.getConfig().getDouble(s.path()));
        };
    }

    private static String format(Setting s, double v) {
        return s.kind() == Kind.INT ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    private static Material iconFor(Kind k) {
        return switch (k) {
            case BOOL -> Material.LEVER;
            case TEXT -> Material.NAME_TAG;
            case INT -> Material.IRON_NUGGET;
            case DOUBLE -> Material.REDSTONE;
        };
    }

    private void edit(Player p, Setting s, InventoryClickEvent e) {
        String path = s.path();
        switch (s.kind()) {
            case TEXT -> {
                plugin.prompts().ask(p, s.label() + " (neuer Wert):", text -> {
                    plugin.getConfig().set(path, text);
                    after(p);
                });
                return;
            }
            case BOOL -> plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path));
            case INT, DOUBLE -> {
                double step = s.step() * (e.isShiftClick() ? 10 : 1) * (e.isRightClick() ? -1 : 1);
                double v = Math.max(s.min(), Math.min(s.max(), plugin.getConfig().getDouble(path) + step));
                if (s.kind() == Kind.INT) {
                    plugin.getConfig().set(path, (int) Math.round(v));
                } else {
                    plugin.getConfig().set(path, Math.round(v * 100) / 100.0);
                }
            }
        }
        after(p);
    }

    private void after(Player p) {
        plugin.saveConfig();
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
        new SettingsMenu(plugin, category).open(p);
    }
}
