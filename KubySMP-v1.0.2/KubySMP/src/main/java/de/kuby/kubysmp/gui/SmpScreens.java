package de.kuby.kubysmp.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.League;
import de.kuby.kubyplugins.service.StatsService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Icons;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysmp.KubySMP;
import de.kuby.kubysmp.service.RaidInsuranceService;
import de.kuby.kubysmp.service.TournamentService;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * SMP-Dialoge, jeder mit eigener Optik: Trophäen als Liga-Leiter in Frost (aktuelle Liga hervorgehoben, Belohnungen
 * abholen), Duelle als Fechthalle (Herausforderung annehmen, Spieler herausfordern), Turnier als Turnier-Banner in Gold
 * und Purpur (Status, Teilnehmer, An-/Abmelden), Raid-Versicherung als Versicherungs-Büro (Policen, kündigen nach
 * Nachfrage) und Steckbriefe als Papier-Plakate „GESUCHT“. Ohne Pack: die Truhen.
 */
public final class SmpScreens {

    static final int W = 390;
    static final TextColor FROST = TextColor.color(0xA8E8FF), ICE = TextColor.color(0xE4F6FF), MIST = TextColor.color(0x8AB0C4),
            GOLD = TextColor.color(0xF8D35A), GREEN = TextColor.color(0x7FE08A), RED = TextColor.color(0xFF7A7A),
            JEWEL = TextColor.color(0x5FE3A0), WHITE = TextColor.color(0xF4F4F4), DIM = TextColor.color(0x6A7A88),
            CREAM = TextColor.color(0xF2EEE6), STEELC = TextColor.color(0xC8D4E0), SLATE = TextColor.color(0x8C98A6),
            ROYAL = TextColor.color(0xE0B8FF), MAUVE = TextColor.color(0x9C88B8), TEAL = TextColor.color(0x6FE0D8),
            TEALDIM = TextColor.color(0x6AA8A4), INK = TextColor.color(0x3A2C18), INK_RED = TextColor.color(0x9A1A1A),
            INK_LIGHT = TextColor.color(0x7A6448);

    private static final Map<UUID, String> ARMED = new HashMap<>();
    private final KubyPlugins plugin;

    public SmpScreens(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private static String name(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? "Unbekannt" : op.getName();
    }

    static TextColor color(String mini) {
        String t = mini.replaceAll(">.*", "").replace("<", "");
        if (t.startsWith("#") && t.length() >= 7) {
            TextColor c = TextColor.fromHexString(t.substring(0, 7));
            return c == null ? WHITE : c;
        }
        NamedTextColor n = NamedTextColor.NAMES.value(t);
        return n == null ? WHITE : n;
    }

    // =================================================================================================================
    // Trophäen
    // =================================================================================================================

    /** Liga: Name, Farbe, Icon, ab Trophäen, Coins, Juwelen, erreicht, abgeholt, aktuell, Klick (abholen). */
    public record Rung(String name, TextColor color, KitData.Icon icon, int min, long coins, int jewels, boolean reached,
                       boolean claimed, boolean current, Consumer<Player> claim) {}

    public record Trophies(int trophies, String league, TextColor leagueColor, String next, int nextMin, double progress,
                           int kills, int deaths, List<Rung> rungs) {}

    public void trophies(Player p) {
        if (!plugin.uiPack().active(p)) {
            new TrophyMenu(plugin).open(p);
            return;
        }
        StatsService.Stats s = plugin.stats().get(p.getUniqueId());
        League cur = League.of(s.trophies), next = cur.next();
        List<Rung> rungs = new ArrayList<>();
        for (League l : League.values()) {
            boolean reached = s.trophies >= l.min(), claimed = s.claimed.contains(l.name());
            rungs.add(new Rung(l.label(), color(l.display()), Icons.of(l.icon(), KitData.Icon.STAR), l.min(), l.coins(), l.jewels(),
                    reached, claimed, l == cur, reached && !claimed ? pl -> {
                StatsService.Stats st = plugin.stats().get(pl.getUniqueId());
                if (!de.kuby.kubyplugins.service.ModeService.SMP.equals(plugin.modes().modeOf(pl.getUniqueId()))) {
                    Msg.send(pl, "<red>Liga-Belohnungen holst du im SMP ab.");
                } else if (st.trophies >= l.min() && !st.claimed.contains(l.name())) {
                    st.claimed.add(l.name());
                    plugin.economy().deposit(pl.getUniqueId(), l.coins());
                    if (l.jewels() > 0) plugin.jewels().add(pl.getUniqueId(), l.jewels());
                    plugin.stats().save();
                    Sfx.play(plugin, pl, Sfx.Kind.LEVEL_UP);
                    Msg.send(pl, "<green>Liga-Belohnung " + l.display() + " <green>erhalten!");
                }
                trophies(pl);
            } : null));
        }
        double prog = next == null ? 1 : Math.max(0, Math.min(1, (s.trophies - cur.min()) / (double) (next.min() - cur.min())));
        UiDialog.show(p, trophies(new Trophies(s.trophies, cur.label(), color(cur.display()), next == null ? null : next.label(),
                next == null ? 0 : next.min(), prog, s.kills, s.deaths, rungs)), 330);
    }

    public static Canvas trophies(Trophies v) {
        Canvas c = new Canvas(W).heading("Trophäen", FROST);
        c.box(0, 0, W, 54, KitData.Box.FROST);
        c.icon(10, 11, KitData.Icon.STAR, true);
        c.headline(50, 12, "Ligen", FROST);
        c.text(50, 36, "Kills " + v.kills() + " · Tode " + v.deaths(), MIST, false);
        c.textRight(378, 10, Msg.coins(v.trophies()), ICE, true);
        c.textRight(378, 21, Canvas.caps("Trophäen"), MIST, false);
        c.textRight(378, 36, v.league(), v.leagueColor(), true);
        int y = 60;
        // Fortschritt zur nächsten Liga
        c.box(0, y, W, 30, KitData.Box.FROST_CARD);
        c.text(10, y + 11, v.next() == null ? "Höchste Liga erreicht!" : "Bis " + v.next() + ":", v.next() == null ? GOLD : MIST, v.next() == null);
        if (v.next() != null) {
            c.bar(90, y + 12, 220, v.progress(), KitData.Fill.CYAN);
            c.textRight(380, y + 11, Msg.coins(v.trophies()) + " / " + Msg.coins(v.nextMin()), ICE, false);
        }
        y += 30 + 6;
        // Leiter: höchste Liga oben
        for (int i = v.rungs().size() - 1; i >= 0; i--) {
            Rung r = v.rungs().get(i);
            c.box(0, y, W, 34, r.current() ? KitData.Box.GOLD : r.reached() ? KitData.Box.FROST_CARD : KitData.Box.GRAY);
            c.icon(8, y + 9, r.icon(), false, r.reached() ? NamedTextColor.WHITE : TextColor.color(0x505050));
            String reward = Canvas.num(r.coins()) + "$" + (r.jewels() > 0 ? " + " + Canvas.num(r.jewels()) + " Juwelen" : "");
            int rw = c.textRight(270, y + 7, Canvas.fit(reward, false, 150), r.reached() ? GOLD : DIM, false);
            c.text(30, y + 7, Canvas.fit(r.name(), true, 270 - rw - 36), r.reached() ? r.color() : DIM, true);
            c.text(30, y + 19, Canvas.fit("ab " + Msg.coins(r.min()) + " Trophäen", false, 160), r.reached() ? MIST : DIM, false);
            if (r.current()) c.textRight(270, y + 19, "✔ deine Liga", GREEN, false);
            if (r.claim() != null) {
                c.smallButton(280, y + 9, 100, KitData.Small.GREEN, "Abholen!", WHITE, true);
                c.action(0, y, W, 34, r.claim(), "Belohnung abholen");
            } else {
                c.textRight(380, y + 13, r.claimed() ? "abgeholt" : r.reached() ? "–" : "✘ noch nicht", r.claimed() ? GREEN : DIM, false);
            }
            y += 34 + 3;
        }
        return c;
    }

    // =================================================================================================================
    // Duelle
    // =================================================================================================================

    public record Fighter(String name, boolean busy, Consumer<Player> challenge) {}

    public record Duels(String challenger, Consumer<Player> accept, List<Fighter> players) {}

    public void duels(Player p) {
        if (!plugin.uiPack().active(p)) {
            new DuelMenu(plugin).open(p);
            return;
        }
        var ds = KubySMP.get().duels();
        UUID from = ds.pendingChallengerOf(p.getUniqueId());
        List<Fighter> list = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) continue;
            boolean busy = ds.isDueling(other.getUniqueId());
            list.add(new Fighter(other.getName(), busy, busy ? null : pl -> {
                String err = KubySMP.get().duels().challenge(pl, other, 0);
                Msg.send(pl, err == null ? "<green>Herausforderung an <white>" + other.getName() + " <green>gesendet." : "<red>" + err);
                duels(pl);
            }));
        }
        UiDialog.show(p, duels(new Duels(from == null ? null : name(from), from == null ? null : pl -> {
            String err = KubySMP.get().duels().accept(pl);
            if (err != null) Msg.send(pl, "<red>" + err);
            de.kuby.kubyplugins.ui.UiDialog.close(pl);
        }, list)), 300);
    }

    public static Canvas duels(Duels v) {
        Canvas c = new Canvas(W).heading("Duelle", STEELC);
        c.box(0, 0, W, 54, KitData.Box.STEEL);
        c.icon(10, 11, KitData.Icon.IRON_SWORD, true);
        c.headline(50, 12, "Duelle", STEELC);
        c.text(50, 36, "Eins gegen eins · mit Einsatz: /duell <Spieler> <Betrag>", SLATE, false);
        int y = 60;
        if (v.challenger() != null) {
            c.box(0, y, W, 34, KitData.Box.RED);
            c.icon(8, y + 9, KitData.Icon.DIAMOND_SWORD, false);
            c.text(30, y + 7, Canvas.fit(Canvas.lit(v.challenger()), true, 200), WHITE, true);
            c.text(30, y + 19, "fordert dich heraus!", RED, false);
            c.smallButton(W - 100, y + 9, 92, KitData.Small.GREEN, "Annehmen", WHITE, true);
            c.action(0, y, W, 34, v.accept(), "Duell annehmen");
            y += 34 + 8;
        }
        String t = Canvas.caps("Spieler online");
        c.text(2, y, t, STEELC, true);
        c.line(Canvas.width(t, true) + 8, y + 3, W - Canvas.width(t, true) - 10, DIM);
        y += 12;
        int cols = 3, cw = (W - 6) / cols, ch = 28;
        for (int i = 0; i < v.players().size(); i++) {
            Fighter f = v.players().get(i);
            int x = (i % cols) * (cw + 3), fy = y + (i / cols) * (ch + 3);
            c.box(x, fy, cw, ch, f.busy() ? KitData.Box.GRAY : KitData.Box.STEEL_CARD);
            c.text(x + 8, fy + 6, Canvas.fit(Canvas.lit(f.name()), true, cw - 16), f.busy() ? DIM : WHITE, true);
            c.text(x + 8, fy + 17, f.busy() ? "im Duell" : "Klick: herausfordern", f.busy() ? RED : SLATE, false);
            if (f.challenge() != null) c.action(x, fy, cw, ch, f.challenge(), "Ohne Einsatz herausfordern");
        }
        if (v.players().isEmpty()) c.text(4, y, "Gerade ist niemand sonst online.", SLATE, false);
        return c;
    }

    // =================================================================================================================
    // Turnier
    // =================================================================================================================

    public record Tournament(String state, TextColor stateColor, long reward, int trophiesPerWin, List<String> entrants,
                             boolean registration, boolean joined, Consumer<Player> toggle, Consumer<Player> admin) {}

    public void tournament(Player p) {
        if (!plugin.uiPack().active(p)) {
            new TournamentMenu(plugin).open(p);
            return;
        }
        TournamentService t = KubySMP.get().tournaments();
        UUID me = p.getUniqueId();
        List<String> names = new ArrayList<>();
        for (UUID id : t.entrants()) names.add(name(id));
        TournamentService.State st = t.state();
        String state = switch (st) {
            case CLOSED -> "Kein Turnier";
            case REGISTRATION -> "Anmeldung offen";
            case RUNNING -> "Läuft · Runde " + t.round();
        };
        TextColor sc = st == TournamentService.State.REGISTRATION ? GREEN : st == TournamentService.State.RUNNING ? GOLD : DIM;
        boolean joined = t.entrants().contains(me);
        UiDialog.show(p, tournament(new Tournament(state, sc, plugin.getConfig().getLong("tournament.reward", 5000),
                plugin.getConfig().getInt("tournament.trophies-per-win", 50), names, st == TournamentService.State.REGISTRATION, joined,
                pl -> {
                    TournamentService tt = KubySMP.get().tournaments();
                    String err = tt.entrants().contains(pl.getUniqueId()) ? tt.leave(pl.getUniqueId()) : tt.join(pl);
                    Sfx.play(plugin, pl, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
                    if (err != null) Msg.send(pl, "<red>" + err);
                    tournament(pl);
                }, p.hasPermission("kuby.admin") ? pl -> {
                    de.kuby.kubyplugins.ui.UiDialog.close(pl);
                    new TournamentAdminMenu(plugin).open(pl);
                } : null)), 300);
    }

    public static Canvas tournament(Tournament v) {
        Canvas c = new Canvas(W).heading("Turnier", ROYAL);
        c.box(0, 0, W, 54, KitData.Box.ROYAL);
        c.icon(10, 11, KitData.Icon.TOTEM, true);
        c.headline(50, 12, "Turnier", ROYAL);
        c.text(50, 36, "K.-o.-Turnier · wer verliert, ist raus", MAUVE, false);
        c.textRight(378, 12, v.state(), v.stateColor(), true);
        int y = 60;
        c.box(0, y, W, 44, KitData.Box.GOLD);
        c.text(12, y + 8, Canvas.caps("Preis für den Sieger"), GOLD, false);
        String prize = Msg.coins(v.reward()) + "$";
        c.title(12, y + 20, prize, true, null);
        c.textRight(378, y + 8, Canvas.caps("je Sieg"), GOLD, false);
        c.textRight(378, y + 22, "+" + v.trophiesPerWin() + " Trophäen", CREAM, true);
        y += 44 + 6;
        if (v.registration()) {
            c.button(0, y, W, v.joined() ? KitData.Button.RED_LINE : KitData.Button.GREEN);
            String l = v.joined() ? "Abmelden" : "Jetzt anmelden";
            if (v.joined()) c.textCenter(W / 2, y + 7, l, RED, true);
            else c.textFlat(W / 2 - Canvas.width(l, true) / 2, y + 7, l, TextColor.color(0x0C1A08), true);
            c.action(0, y, W, 22, v.toggle(), l);
            y += 22 + 8;
        }
        String t = Canvas.caps("Teilnehmer") + " · " + v.entrants().size();
        c.text(2, y, t, ROYAL, true);
        c.line(Canvas.width(t, true) + 8, y + 3, W - Canvas.width(t, true) - 10, MAUVE);
        y += 12;
        int x = 0;
        for (String n : v.entrants()) {
            String label = Canvas.fit(Canvas.lit(n), false, 100);
            int w = Canvas.width(label, false) + 14;
            if (x + w > W) {
                x = 0;
                y += KitData.SMALL_H + 3;
            }
            c.smallButton(x, y, w, KitData.Small.DARK, label, CREAM, false);
            x += w + 3;
        }
        if (v.entrants().isEmpty()) c.text(2, y, "Noch niemand angemeldet.", MAUVE, false);
        y += KitData.SMALL_H + 8;
        if (v.admin() != null) {
            c.smallButton(0, y, 110, KitData.Small.DARK, "Verwaltung (Admin)", RED, false);
            c.action(0, y, 110, KitData.SMALL_H, v.admin(), "Turnier verwalten");
        }
        return c;
    }

    // =================================================================================================================
    // Raid-Versicherung
    // =================================================================================================================

    public record Policy(String where, String world, long value, long payout, long hours, boolean armed, Consumer<Player> cancel) {}

    public record Insurance(int payoutPercent, int premiumPercent, int days, List<Policy> policies) {}

    public void insurance(Player p) {
        if (!plugin.uiPack().active(p)) {
            new InsuranceMenu(plugin).open(p);
            return;
        }
        RaidInsuranceService ins = KubySMP.get().insurance();
        String armed = ARMED.get(p.getUniqueId());
        List<Policy> list = new ArrayList<>();
        for (RaidInsuranceService.Policy pol : ins.ownedBy(p.getUniqueId())) {
            String key = pol.world() + ":" + pol.x() + ":" + pol.y() + ":" + pol.z();
            long hours = Math.max(0, (pol.expires() - System.currentTimeMillis()) / 3_600_000L);
            list.add(new Policy(pol.x() + " / " + pol.y() + " / " + pol.z(), pol.world(), pol.value(), ins.payoutOf(pol), hours,
                    key.equals(armed), pl -> {
                if (!key.equals(ARMED.get(pl.getUniqueId()))) {
                    ARMED.put(pl.getUniqueId(), key);
                } else {
                    ARMED.remove(pl.getUniqueId());
                    String err = KubySMP.get().insurance().cancel(pl.getUniqueId(), pol);
                    Sfx.play(plugin, pl, err == null ? Sfx.Kind.CLICK : Sfx.Kind.ERROR);
                    Msg.send(pl, err == null ? "<gray>Versicherung gekündigt." : "<red>" + err);
                }
                insurance(pl);
            }));
        }
        UiDialog.show(p, insurance(new Insurance(ins.payoutPercent(), ins.premiumPercent(), ins.days(), list)), 300);
    }

    public static Canvas insurance(Insurance v) {
        Canvas c = new Canvas(W).heading("Raid-Versicherung", TEAL);
        c.box(0, 0, W, 54, KitData.Box.TEAL);
        c.icon(10, 11, KitData.Icon.IRON_CHESTPLATE, true);
        c.headline(50, 12, "Versicherung", TEAL);
        c.text(50, 36, "Schau auf eine Kiste und tippe /versichern", TEALDIM, false);
        int y = 60, fw = (W - 6) / 3;
        stat(c, 0, y, fw, "Auszahlung", v.payoutPercent() + " %", "des Kistenwerts");
        stat(c, fw + 3, y, fw, "Prämie", v.premiumPercent() + " %", "einmal beim Abschluss");
        stat(c, 2 * (fw + 3), y, fw, "Laufzeit", v.days() + (v.days() == 1 ? " Tag" : " Tage"), "danach neu versichern");
        y += 40 + 8;
        String t = Canvas.caps("Deine Policen") + " · " + v.policies().size();
        c.text(2, y, t, TEAL, true);
        c.line(Canvas.width(t, true) + 8, y + 3, W - Canvas.width(t, true) - 10, TEALDIM);
        y += 12;
        for (Policy pol : v.policies()) {
            c.box(0, y, W, 32, pol.armed() ? KitData.Box.RED : KitData.Box.TERMINAL_ROW);
            c.icon(8, y + 8, KitData.Icon.CHEST_BLOCK, false);
            // rechts zuerst, links auf den Rest gekürzt (lange Koordinaten und Weltnamen liefen in die Werte)
            int w1 = c.textRight(300, y + 6, "Wert " + Canvas.num(pol.value()) + "$", GOLD, false);
            int w2 = c.textRight(300, y + 18, "Auszahlung " + Canvas.num(pol.payout()) + "$", GREEN, false);
            c.text(30, y + 6, Canvas.fit("Kiste " + pol.where(), true, 300 - w1 - 36), CREAM, true);
            c.text(30, y + 18, Canvas.fit(pol.world() + " · läuft ab in " + pol.hours() + " Std.", false, 300 - w2 - 36), TEALDIM, false);
            String l = pol.armed() ? "Wirklich?" : "Kündigen";
            c.smallButton(W - 76, y + 8, 68, pol.armed() ? KitData.Small.GOLD_ON : KitData.Small.DARK, l,
                    pol.armed() ? TextColor.color(0x3A2A06) : RED, false);
            c.action(W - 76, y + 8, 68, KitData.SMALL_H, pol.cancel(), "Kündigen (ohne Erstattung)");
            y += 32 + 3;
        }
        if (v.policies().isEmpty()) c.text(4, y, "Keine versicherten Kisten.", TEALDIM, false);
        return c;
    }

    private static void stat(Canvas c, int x, int y, int w, String label, String value, String sub) {
        c.box(x, y, w, 40, KitData.Box.TERMINAL_ROW);
        c.text(x + 8, y + 6, Canvas.caps(label), TEALDIM, false);
        c.text(x + 8, y + 17, Canvas.fit(value, true, w - 14), CREAM, true);
        c.text(x + 8, y + 28, Canvas.fit(sub, false, w - 14), TEALDIM, false);
    }

    // =================================================================================================================
    // Steckbriefe
    // =================================================================================================================

    public record Wanted(String name, long bounty, String where) {}

    public void bounties(Player p) {
        if (!plugin.uiPack().active(p)) {
            new BountiesMenu(plugin).open(p);
            return;
        }
        Map<UUID, Long> all = KubySMP.get().bounties().allIn(plugin.modes().modeOf(p.getUniqueId()));
        List<Wanted> list = new ArrayList<>();
        all.entrySet().stream().sorted(Map.Entry.<UUID, Long>comparingByValue().reversed()).limit(24).forEach(e -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            Player on = op.getPlayer();
            String where = on == null ? "offline" : on.getWorld().getName() + " " + on.getLocation().getBlockX() + " / "
                    + on.getLocation().getBlockZ();
            list.add(new Wanted(op.getName() == null ? "Unbekannt" : op.getName(), e.getValue(), where));
        });
        UiDialog.show(p, bounties(list), 300);
    }

    /** Selbsttest: alle SMP-Seiten mit Grenzwerten und leer. */
    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        List<Rung> rungs = new ArrayList<>();
        for (int i = 0; i < 6; i++) rungs.add(new Rung("W".repeat(16), GOLD, KitData.Icon.DIAMOND, 999_999, 999_999_999L, 99_999, i < 3, i < 2, i == 2, i == 2 ? no : null));
        reg.accept("SMP Trophäen", () -> trophies(new Trophies(999_999, "W".repeat(16), GOLD, "W".repeat(16), 999_999, 0.5, 99_999, 99_999, rungs)));
        reg.accept("SMP Trophäen max", () -> trophies(new Trophies(5000, "Champion", GOLD, null, 0, 1, 0, 0, rungs)));
        List<Fighter> fs = new ArrayList<>();
        for (int i = 0; i < 60; i++) fs.add(new Fighter("_WWWWWWWWWWWWWWW", i % 2 == 0, i % 2 == 0 ? null : no));
        reg.accept("SMP Duelle", () -> duels(new Duels("_WWWWWWWWWWWWWWW", no, fs)));
        reg.accept("SMP Duelle leer", () -> duels(new Duels(null, null, List.of())));
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 64; i++) names.add("_WWWWWWWWWWWWWWW");
        reg.accept("SMP Turnier", () -> tournament(new Tournament("W".repeat(30), GOLD, 999_999_999_999L, 99_999, names, true, true, no, no)));
        reg.accept("SMP Turnier leer", () -> tournament(new Tournament("Kein Turnier", DIM, 0, 0, List.of(), false, false, no, null)));
        List<Policy> pol = new ArrayList<>();
        for (int i = 0; i < 20; i++) pol.add(new Policy("-29999999 / 320 / 29999999", "W".repeat(20), 999_999_999L, 999_999_999L, 99_999, i == 0, no));
        reg.accept("SMP Versicherung", () -> insurance(new Insurance(100, 100, 999, pol)));
        reg.accept("SMP Versicherung leer", () -> insurance(new Insurance(50, 10, 1, List.of())));
        List<Wanted> wl = new ArrayList<>();
        for (int i = 0; i < 24; i++) wl.add(new Wanted("_WWWWWWWWWWWWWWW", 999_999_999_999L, "W".repeat(40)));
        reg.accept("SMP Steckbriefe", () -> bounties(wl));
        reg.accept("SMP Steckbriefe leer", () -> bounties(List.of()));
    }

    public static Canvas bounties(List<Wanted> list) {
        Canvas c = new Canvas(W).heading("Steckbriefe", RED);
        c.box(0, 0, W, 54, KitData.Box.RED);
        c.icon(10, 11, KitData.Icon.SKULL_BANNER_PATTERN, true);
        c.headline(50, 12, "Gesucht", RED);
        c.text(50, 36, "Kopfgeld aussetzen: /bounty <Spieler> <Betrag>", TextColor.color(0xC08080), false);
        int y = 60, cols = 2, cw = (W - 4) / cols, ch = 66;
        for (int i = 0; i < list.size(); i++) {
            Wanted w = list.get(i);
            int x = (i % cols) * (cw + 4), wy = y + (i / cols) * (ch + 4);
            c.box(x, wy, cw, ch, KitData.Box.PAPER);
            String g = "GESUCHT";
            c.title(x + (cw - Canvas.titleWidth(g)) / 2, wy + 6, g, false, INK_RED);
            String n = Canvas.fit(Canvas.lit(w.name()), true, cw - 10);
            c.textFlat(x + (cw - Canvas.width(n, true)) / 2, wy + 28, n, INK, true);
            String b = Msg.coins(w.bounty()) + "$";
            c.textFlat(x + (cw - Canvas.width(b, true)) / 2, wy + 40, b, INK_RED, true);
            String wh = Canvas.fit(w.where(), false, cw - 10);
            c.textFlat(x + (cw - Canvas.width(wh, false)) / 2, wy + 52, wh, INK_LIGHT, false);
        }
        if (list.isEmpty()) c.text(4, y, "Keine Steckbriefe. Gerade wird niemand gesucht.", TextColor.color(0xC08080), false);
        return c;
    }
}
