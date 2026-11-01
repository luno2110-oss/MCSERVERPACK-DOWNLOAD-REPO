package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.TeamService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.Teleports;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /team als Dialog in Indigo (Gildenhalle): Kopf mit Teamname, Level und Team-XP, drei Felder (Kasse, Lager, Homes)
 * mit Knöpfen (einzahlen, abheben für den Chef, Lager öffnen, Teamhome besuchen oder setzen), Mitglieder als Namens-
 * Chips (Chef mit Krone) und unten „Team verlassen“ bzw. „Team auflösen“ nach Nachfrage. Ohne Team: wie man eins
 * gründet. Ohne Pack: TeamMenu.
 */
public final class TeamScreen {

    static final int W = 390;
    static final TextColor INDIGO = TextColor.color(0xAEB0FF), CREAM = TextColor.color(0xECEAFF), LAV = TextColor.color(0x9090C8),
            GOLD = TextColor.color(0xF8D35A), GREEN = TextColor.color(0x7FE08A), RED = TextColor.color(0xFF7A7A),
            WHITE = TextColor.color(0xF4F4F4), DIM = TextColor.color(0x6060A0), DARK = TextColor.color(0x0C0C22);

    public record Member(String name, boolean leader, boolean online) {}

    /** Team: Name (null = kein Team), Level, XP-Fortschritt (0..1, -1 = höchstes Level), Mitglieder, max., Kasse, Lager-Reihen,
     *  Homes, max. Homes, Chef, Kosten fürs Gründen, Verlassen angefragt, Klicks. */
    public record View(String name, int level, double xp, List<Member> members, int max, long bank, int rows, int homes,
                       int homeMax, boolean leader, long createCost, boolean armed, Consumer<Player> deposit100,
                       Consumer<Player> deposit1000, Consumer<Player> withdraw, Consumer<Player> storage,
                       Consumer<Player> home, Consumer<Player> setHome, Consumer<Player> leave) {}

    private static final Set<UUID> ARMED = new HashSet<>();
    private final KubyPlugins plugin;

    public TeamScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        if (!plugin.uiPack().active(p)) {
            new TeamMenu(plugin).open(p);
            return;
        }
        UiDialog.show(p, canvas(view(p)), 300);
    }

    private void money(Player p, String err, String ok) {
        Msg.send(p, err == null ? ok : "<red>" + err);
        Sfx.play(plugin, p, err == null ? Sfx.Kind.COIN : Sfx.Kind.ERROR);
        open(p);
    }

    private View view(Player p) {
        TeamService ts = plugin.teams();
        UUID me = p.getUniqueId();
        TeamService.Team t = ts.of(me);
        long cost = plugin.getConfig().getLong("team.create-cost", 1000);
        if (t == null) {
            return new View(null, 0, 0, List.of(), 0, 0, 0, 0, 0, false, cost, false, null, null, null, null, null, null, null);
        }
        boolean leader = t.leader().equals(me);
        int lv = ts.level(t);
        long from = TeamService.xpFor(lv), to = TeamService.xpFor(lv + 1);
        double xp = lv >= ts.maxLevel() ? -1 : Math.min(1, (t.xp() - from) / (double) Math.max(1, to - from));
        List<Member> members = new ArrayList<>();
        for (UUID id : t.members()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            members.add(new Member(op.getName() == null ? "Unbekannt" : op.getName(), id.equals(t.leader()), op.isOnline()));
        }
        members.sort((a, b) -> Boolean.compare(b.leader(), a.leader()));
        return new View(t.name(), lv, xp, members, ts.maxMembers(t), t.bank(), ts.storageRows(t), t.homes().size(),
                ts.homeLimit(t), leader, cost, ARMED.contains(me),
                pl -> money(pl, ts.deposit(pl, 100), "<green>Eingezahlt: <gold>100$"),
                pl -> money(pl, ts.deposit(pl, 1000), "<green>Eingezahlt: <gold>1.000$"),
                !leader ? null : pl -> {
                    TeamService.Team tt = ts.of(pl.getUniqueId());
                    long amount = tt == null ? 0 : Math.min(1000, tt.bank());
                    money(pl, ts.withdraw(pl, amount), "<green>Abgehoben: <gold>" + Msg.coins(amount) + "$");
                },
                pl -> {
                    pl.closeDialog();
                    String err = ts.openStorage(pl);
                    if (err != null) Msg.send(pl, "<red>" + err);
                },
                pl -> {
                    Location loc = ts.homeOf(pl.getUniqueId());
                    if (loc == null) {
                        Msg.send(pl, "<red>Noch kein Teamhome. „Hier setzen“ setzt eins.");
                        return;
                    }
                    pl.closeDialog();
                    Teleports.delayed(plugin, pl, loc, t.name());
                },
                pl -> {
                    String err = ts.setHome(pl, "home");
                    Msg.send(pl, err == null ? "<green>Teamhome gesetzt." : "<red>" + err);
                    open(pl);
                },
                pl -> {
                    if (ARMED.add(pl.getUniqueId())) {
                        Sfx.play(plugin, pl, Sfx.Kind.CLICK);
                        open(pl);
                        return;
                    }
                    ARMED.remove(pl.getUniqueId());
                    String err = leader ? ts.disband(pl) : ts.leave(pl.getUniqueId());
                    Msg.send(pl, err == null ? "<gray>Du hast das Team " + (leader ? "aufgelöst." : "verlassen.") : "<red>" + err);
                    pl.closeDialog();
                });
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Team", INDIGO);
        c.box(0, 0, W, 54, KitData.Box.INDIGO);
        c.icon(10, 11, KitData.Icon.WHITE_BANNER, true);
        if (v.name() == null) {
            c.title(50, 12, "Team", false, INDIGO);
            c.text(50, 36, "Gemeinsam spielen, Kasse, Lager und Teamhome", LAV, false);
            int y = 60;
            c.box(0, y, W, 72, KitData.Box.INDIGO_CARD);
            c.text(12, y + 10, "Du bist in keinem Team.", CREAM, true);
            c.text(12, y + 24, "Gründen: /team create <Name>  ·  Kosten " + Msg.coins(v.createCost()) + "$", LAV, false);
            c.text(12, y + 36, "Einladen: /team invite <Spieler>  ·  annehmen: /team accept", LAV, false);
            c.text(12, y + 50, "Gemeinsam spielen bringt Team-XP: mehr Plätze, Homes und Lager.", DIM, false);
            return c;
        }
        c.text(50, 12, Canvas.fit(Canvas.lit(v.name()), true, 200), CREAM, true);
        c.text(50, 24, Canvas.caps("Level") + " " + v.level() + " · " + v.members().size() + " / " + v.max() + " " + Canvas.caps("Mitglieder"),
                LAV, false);
        if (v.xp() < 0) {
            c.text(50, 37, "Höchstes Level", GOLD, false);
        } else {
            c.bar(50, 37, 160, v.xp(), KitData.Fill.PURPLE);
            c.text(216, 37, (int) Math.round(v.xp() * 100) + " % " + Canvas.caps("Team-XP"), LAV, false);
        }
        if (v.leader()) c.textRight(378, 12, "★ " + Canvas.caps("Teamchef"), GOLD, false);

        int y = 60, fw = (W - 6) / 3, fh = 70;
        // Kasse
        field(c, 0, y, fw, fh, KitData.Icon.GOLD_INGOT, "Teamkasse", Msg.coins(v.bank()) + "$", GOLD);
        int bw = (fw - 20) / 2;
        btn(c, 8, y + 34, bw, KitData.Small.GREEN, "+100$", v.deposit100(), "100$ einzahlen");
        btn(c, 8 + bw + 4, y + 34, bw, KitData.Small.GREEN, "+1.000$", v.deposit1000(), "1.000$ einzahlen");
        if (v.withdraw() != null) btn(c, 8, y + 51, fw - 16, KitData.Small.DARK, "1.000$ abheben", v.withdraw(), "Nur der Teamchef");
        else c.text(10, y + 54, "Abheben: Teamchef", DIM, false);
        // Lager
        field(c, fw + 3, y, fw, fh, KitData.Icon.CHEST_BLOCK, "Team-Lager", v.rows() + (v.rows() == 1 ? " Reihe" : " Reihen"), CREAM);
        btn(c, fw + 3 + 8, y + 34, fw - 16, KitData.Small.GOLD, "Öffnen", v.storage(), "Lager für alle im Team");
        // Homes
        field(c, 2 * (fw + 3), y, fw, fh, KitData.Icon.RED_BED, "Teamhome", v.homes() + " / " + v.homeMax(), CREAM);
        btn(c, 2 * (fw + 3) + 8, y + 34, fw - 16, KitData.Small.GOLD, "Hinreisen", v.home(), "Zum Teamhome teleportieren");
        btn(c, 2 * (fw + 3) + 8, y + 51, fw - 16, KitData.Small.DARK, "Hier setzen", v.setHome(), "Teamhome an deiner Position setzen");
        y += fh + 8;

        // Mitglieder
        String t = Canvas.caps("Mitglieder");
        c.text(2, y, t, INDIGO, true);
        c.line(Canvas.width(t, true) + 8, y + 3, W - Canvas.width(t, true) - 10, DIM);
        y += 12;
        int x = 0;
        for (Member m : v.members()) {
            String label = Canvas.fit(Canvas.lit((m.leader() ? "★ " : "") + m.name()), false, 110);
            int w = Canvas.width(label, false) + 14;
            if (x + w > W) {
                x = 0;
                y += KitData.SMALL_H + 3;
            }
            c.smallButton(x, y, w, m.leader() ? KitData.Small.GOLD : KitData.Small.DARK, label,
                    m.leader() ? GOLD : m.online() ? GREEN : LAV, false);
            x += w + 3;
        }
        y += KitData.SMALL_H + 10;
        c.text(2, y, "Einladen: /team invite <Spieler> · entfernen (Chef): /team kick <Spieler>", DIM, false);
        y += 14;
        // Verlassen / auflösen
        String leave = v.leader() ? "Team auflösen" : "Team verlassen";
        c.button(0, y, W, v.armed() ? KitData.Button.RED : KitData.Button.RED_LINE);
        String label = v.armed() ? "Wirklich? Nochmal klicken: " + leave : leave;
        if (v.armed()) c.text((W - Canvas.width(label, true)) / 2, y + 7, label, WHITE, true);
        else c.text((W - Canvas.width(label, true)) / 2, y + 7, label, RED, true);
        c.action(0, y, W, 22, v.leave(), leave);
        return c;
    }

    private static void field(Canvas c, int x, int y, int w, int h, KitData.Icon icon, String title, String value, TextColor vc) {
        c.box(x, y, w, h, KitData.Box.INDIGO_CARD);
        c.icon(x + 7, y + 6, icon, false);
        c.text(x + 28, y + 6, Canvas.caps(title), LAV, false);
        c.text(x + 28, y + 17, Canvas.fit(value, true, w - 36), vc, true);
    }

    private static void btn(Canvas c, int x, int y, int w, KitData.Small style, String label, Consumer<Player> click, String hover) {
        TextColor col = style == KitData.Small.DARK ? CREAM : style == KitData.Small.GOLD ? GOLD : WHITE;
        c.smallButton(x, y, w, style, Canvas.fit(label, false, w - 6), col, false);
        if (click != null) c.action(x, y, w, KitData.SMALL_H, click, hover);
    }
}
