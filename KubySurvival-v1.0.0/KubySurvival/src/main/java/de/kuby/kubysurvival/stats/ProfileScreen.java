package de.kuby.kubysurvival.stats;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.PlaytimeScreen;
import de.kuby.kubyplugins.service.KitService;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.service.TeamService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.jobs.Job;
import de.kuby.kubysurvival.jobs.JobService;
import de.kuby.kubysurvival.quests.QuestService;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Profil in Survival (/stats [Spieler]) nach der Referenz „Neue-referenzen“: Kopf mit Avatar, Rang, online und Level
 * (große Ziffern), Reiter Übersicht (Level-Fortschritt, Spielzeit, Rang, Coins, Freunde, Ehe, dabei seit, Team,
 * Juwelen, Erfolge, Farmpass), Berufe (je Job Level und Fortschritt, der Hauptberuf golden, darunter Job-XP, verdiente
 * Coins und Platz in der Top 10), Quests (Quest-Rang mit Quest-Tokens, Quests, Serie, beste Serie) und Welt (abgebaut,
 * Tode, verkauft, Grundstücke mit Blöcken). Statisch aus Daten (Records) für Selbsttest und Vorschau.
 */
public final class ProfileScreen {

    public static final int W = 390, HEIGHT = 330;
    private static final TextColor BLUE = TextColor.color(0x62CAFC), GRAY = TextColor.color(0x9FA1A7),
            LIGHT = TextColor.color(0xA8A8A8), WHITE = TextColor.color(0xFCFCFC), DIM = TextColor.color(0x797D84),
            GREEN = TextColor.color(0x6DE288), ONLINE = TextColor.color(0x54FC54), GOLD = TextColor.color(0xFCD45D),
            JEWEL = TextColor.color(0x5BED79), NAV = TextColor.color(0xC0C4CB), COINS = TextColor.color(0xFCA800);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Berlin"));

    public enum Tab {
        OVERVIEW("Übersicht", KitData.Sprite.PF_TAB_OVERVIEW_ON, KitData.Sprite.PF_TAB_OVERVIEW_OFF, 69),
        JOBS("Berufe", KitData.Sprite.PF_TAB_JOBS_ON, KitData.Sprite.PF_TAB_JOBS_OFF, 68),
        QUESTS("Quests", KitData.Sprite.PF_TAB_QUESTS_ON, KitData.Sprite.PF_TAB_QUESTS_OFF, 68),
        WORLD("Welt", KitData.Sprite.PF_TAB_WORLD_ON, KitData.Sprite.PF_TAB_WORLD_OFF, 68);

        final String label;
        /** Symbol im gewählten Reiter (hell) und in den anderen (gedämpft). */
        final KitData.Sprite on, off;
        final int iconY;

        Tab(String label, KitData.Sprite on, KitData.Sprite off, int iconY) {
            this.label = label;
            this.on = on;
            this.off = off;
            this.iconY = iconY;
        }
    }

    public record Head(String name, String rank, boolean online, int level) {}

    public record Overview(int level, long xp, long need, String playtime, String since, String rank, String team,
                           long coins, long jewels, int friends, int badges, int badgesMax, String married, String farmpass) {}

    /** Ein Beruf: Name, Farbe, Level, Fortschritt 0..1, Hauptberuf. */
    public record JobCard(Job job, int level, double frac, boolean main) {}

    /** Berufe: Karten, Job-XP insgesamt, verdiente Coins, Platz in der Top 10 (0 = nicht drin). */
    public record Jobs(List<JobCard> cards, long xp, long earned, int top) {}

    public record Quests(long tokens, long quests, int streak, int best) {}

    public record World(long mined, long deaths, long sold, int claims, long used, long total) {}

    public interface Actions {
        Consumer<Player> tab(Tab tab);
    }

    // =================================================================================================================
    // Öffnen
    // =================================================================================================================

    private final KubySurvival addon;
    private final KubyPlugins core;

    public ProfileScreen(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
    }

    /** Für /stats: zeigt das Profil, wenn der Betrachter in Survival ist und das Pack hat. */
    public boolean open(Player viewer, OfflinePlayer target) {
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(viewer.getUniqueId())) || !core.uiPack().active(viewer)) return false;
        show(viewer, target, Tab.OVERVIEW);
        return true;
    }

    public void show(Player viewer, OfflinePlayer target, Tab tab) {
        UUID id = target.getUniqueId();
        JobService.Worker w = addon.jobs().worker(id);
        int level = JobService.playerLevel(w);
        Player on = target.getPlayer();
        boolean online = on != null && viewer.canSee(on);
        String rank = rank(on);
        Head head = new Head(target.getName() == null ? "Unbekannt" : target.getName(), rank, online, level);
        Actions a = t -> pl -> {
            Sfx.play(core, pl, Sfx.Kind.CLICK);
            show(pl, target, t);
        };
        Canvas c = switch (tab) {
            case OVERVIEW -> {
                long from = JobService.playerXpFor(level), to = JobService.playerXpFor(level + 1);
                TeamService.Team team = core.teams().of(id);
                UUID partner = addon.marry().partnerOf(id);
                yield overview(head, new Overview(level, w.totalXp - from, to - from,
                        PlaytimeScreen.duration(core.playtime().activeMinutes(id, ModeService.SURVIVAL)),
                        target.getFirstPlayed() > 0 ? DATE.format(Instant.ofEpochMilli(target.getFirstPlayed())) : "—",
                        rank, team == null ? "—" : team.name(), core.modes().moneyIn(id, ModeService.SURVIVAL),
                        core.jewels().balance(id), core.friends().friendsOf(id).size(), core.achievements().doneCount(id),
                        core.achievements().all().size(), partner == null ? "—" : name(partner),
                        addon.farmpass().active(id) ? "aktiv" : "keiner"), a);
            }
            case JOBS -> {
                List<JobCard> cards = new ArrayList<>();
                long xp = 0;
                for (Job j : ORDER) {
                    int jl = addon.jobs().jobLevel(w, j);
                    cards.add(new JobCard(j, jl, addon.jobs().progress(w, j) / 100.0, j == w.job));
                    xp += w.xp(j);
                }
                int top = 0;
                List<Map.Entry<UUID, Long>> best = addon.jobs().topXp(10);
                for (int i = 0; i < best.size(); i++) if (best.get(i).getKey().equals(id)) top = i + 1;
                yield jobs(head, new Jobs(cards, xp, w.earnedTotal, top), a);
            }
            case QUESTS -> {
                QuestService q = addon.quests();
                yield quests(head, new Quests(q.questTokens(id), q.total(id), q.streak(id), q.bestStreak(id)), a);
            }
            case WORLD -> {
                long used = addon.claims().used(id);
                yield world(head, new World(core.statValue("blocks_mined", id), core.statValue("deaths_survival", id),
                        core.sell().sold(id), addon.claims().claimsOf(id).size(), used, used + addon.claims().available(id)), a);
            }
        };
        UiDialog.show(viewer, c, HEIGHT);
    }

    /** Rang aus den Rechten (LuckPerms group.x oder kuby.rank.x), sonst Mitglied. */
    private String rank(Player p) {
        if (p == null) return "Mitglied";
        int r = core.kits().rankLevel(p);
        return r < 0 ? "Mitglied" : KitService.rankName(core.kits().rankOrder().get(r));
    }

    private static String name(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? "?" : op.getName();
    }

    // =================================================================================================================
    // Kopf und Reiter
    // =================================================================================================================

    private static Canvas frame(Head h, Tab tab, Actions a) {
        Canvas c = new Canvas(W).heading("Profil", BLUE);
        c.box(0, 0, W, 52, KitData.Box.PF_HEAD);
        c.sprite(8, 16, KitData.Sprite.PF_AVATAR);
        c.sprite(46, 17, KitData.Sprite.PF_RANK);
        c.text(57, 17, Canvas.fit(h.name(), true, 230), BLUE, true);
        int x = 46 + c.text(46, 30, caps(h.rank()), LIGHT, false);
        x += c.text(x, 30, " · ", GRAY, false);
        c.text(x, 30, h.online() ? "● Online" : "● Offline", h.online() ? ONLINE : DIM, false);
        String lvl = String.valueOf(h.level());
        int nx = 378 - 12 * lvl.length();
        big(c, nx, 19, lvl, GREEN);
        c.textRight(nx - 3, 27, "Level", GRAY, false);
        for (Tab t : Tab.values()) {
            int tx = 1 + 98 * t.ordinal();
            boolean on = t == tab;
            c.box(tx, 63, 94, 34, on ? KitData.Box.PF_TAB_ON : KitData.Box.PF_TAB_OFF, Canvas.Z_BUTTON);
            c.sprite(tx + 41, t.iconY, on ? t.on : t.off);
            c.text(tx + (94 - Canvas.width(Canvas.clean(t.label), true)) / 2, 84, t.label, on ? BLUE : NAV, true);
            if (!on) c.action(tx, 63, 94, 34, a.tab(t), t.label);
        }
        return c;
    }

    /** Große Ziffern (Minecraft-Schrift doppelt) mit Schatten 2 px versetzt. Gibt die Breite zurück. */
    private static int big(Canvas c, int x, int y, String text, TextColor color) {
        TextColor shadow = TextColor.color(color.red() / 4, color.green() / 4, color.blue() / 4);
        int cx = x;
        for (char ch : text.toCharArray()) {
            KitData.Sprite s = switch (ch) {
                case '0' -> KitData.Sprite.PF_BIG_0;
                case '1' -> KitData.Sprite.PF_BIG_1;
                case '2' -> KitData.Sprite.PF_BIG_2;
                case '3' -> KitData.Sprite.PF_BIG_3;
                case '4' -> KitData.Sprite.PF_BIG_4;
                case '5' -> KitData.Sprite.PF_BIG_5;
                case '6' -> KitData.Sprite.PF_BIG_6;
                case '7' -> KitData.Sprite.PF_BIG_7;
                case '8' -> KitData.Sprite.PF_BIG_8;
                case '9' -> KitData.Sprite.PF_BIG_9;
                case '.' -> KitData.Sprite.PF_BIG_DOT;
                case ',' -> KitData.Sprite.PF_BIG_COMMA;
                case '%' -> KitData.Sprite.PF_BIG_PCT;
                default -> KitData.Sprite.PF_BIG_SLASH;
            };
            c.sprite(cx + 2, y + 2, s, Canvas.Z_FILL, shadow);
            c.sprite(cx, y, s, Canvas.Z_ICON, color);
            cx += s.width + 2;
        }
        return cx - x;
    }

    /** Balken 10 hoch: leer (Enden und Mitte) und Füllung (grün oder gold) mit runden Enden. */
    private static void bar(Canvas c, int x, int y, int w, double frac, boolean gold) {
        c.sprite(x, y, KitData.Sprite.PF_BAR_L, Canvas.Z_BUTTON);
        strip(c, x + 4, y, KitData.Strip.PF_BAR, w - 8, Canvas.Z_BUTTON);
        c.sprite(x + w - 4, y, KitData.Sprite.PF_BAR_R, Canvas.Z_BUTTON);
        int fw = (int) Math.round(Math.max(0, Math.min(1, frac)) * (w - 4));
        if (fw < 4) return;
        c.sprite(x + 2, y, gold ? KitData.Sprite.PF_FILL_GOLD_L : KitData.Sprite.PF_FILL_GREEN_L);
        strip(c, x + 4, y, gold ? KitData.Strip.PF_FILL_GOLD : KitData.Strip.PF_FILL_GREEN, fw - 4, Canvas.Z_FILL);
        c.sprite(x + fw, y, gold ? KitData.Sprite.PF_FILL_GOLD_R : KitData.Sprite.PF_FILL_GREEN_R);
    }

    /** Füllung beliebiger Länge (ein Zeichen kann höchstens so breit sein wie die Füllung im Kit). */
    private static void strip(Canvas c, int x, int y, KitData.Strip s, int n, int z) {
        while (n > 0) {
            int k = Math.min(n, s.width);
            c.strip(x, y, s, k, z);
            x += k;
            n -= k;
        }
    }

    private static String n(long v) {
        return String.format(Locale.GERMANY, "%,d", v);
    }

    // =================================================================================================================
    // Übersicht
    // =================================================================================================================

    public static Canvas overview(Head h, Overview o, Actions a) {
        Canvas c = frame(h, Tab.OVERVIEW, a);
        c.box(0, 108, W, 142, KitData.Box.PF_PANEL);
        c.text(10, 116, "Auf dem Weg zu Level " + (o.level() + 1), GREEN, true);
        c.textRight(380, 116, List.of(Span.of(n(o.xp()), WHITE), Span.of(" / " + n(o.need()) + " XP", LIGHT)));
        bar(c, 10, 127, 370, o.need() <= 0 ? 1 : (double) o.xp() / o.need(), false);
        int[] ys = {149, 167, 185, 203, 221};
        row(c, 13, 144, KitData.Sprite.PF_OV_TIME, 33, ys[0], "Spielzeit", 187, List.of(Span.of(o.playtime(), WHITE)));
        row(c, 10, 161, KitData.Sprite.PF_OV_RANK_ROW, 33, ys[1], "Rang", 187, List.of(Span.of(caps(o.rank()), LIGHT)));
        row(c, 10, 180, KitData.Sprite.PF_OV_COINS, 33, ys[2], "Coins", 187, List.of(Span.of(n(o.coins()), GOLD)));
        row(c, 10, 197, KitData.Sprite.PF_OV_FRIENDS, 33, ys[3], "Freunde", 187, List.of(Span.of(n(o.friends()), WHITE)));
        row(c, 10, 217, KitData.Sprite.PF_OV_MARRIED, 33, ys[4], "Verheiratet", 187,
                List.of(Span.of(o.married(), "—".equals(o.married()) ? DIM : WHITE)));
        row(c, 204, 144, KitData.Sprite.PF_OV_SINCE, 226, ys[0], "Dabei seit", 380, List.of(Span.of(o.since(), WHITE)));
        row(c, 206, 162, KitData.Sprite.PF_OV_TEAM, 226, ys[1], "Team", 380,
                List.of(Span.of(o.team(), "—".equals(o.team()) ? DIM : WHITE)));
        row(c, 204, 179, KitData.Sprite.PF_OV_JEWELS, 226, ys[2], "Juwelen", 380, List.of(Span.of(n(o.jewels()), JEWEL)));
        row(c, 205, 198, KitData.Sprite.PF_OV_BADGES, 226, ys[3], "Abzeichen", 380,
                List.of(Span.of(String.valueOf(o.badges()), GOLD), Span.of(" / " + o.badgesMax(), GRAY)));
        row(c, 203, 215, KitData.Sprite.PF_OV_FARMPASS, 226, ys[4], "Farmpass", 380,
                List.of(Span.of(o.farmpass(), "keiner".equals(o.farmpass()) ? DIM : GREEN)));
        return c;
    }

    private static void row(Canvas c, int ix, int iy, KitData.Sprite icon, int lx, int y, String label, int right, List<Span> value) {
        c.sprite(ix, iy, icon);
        c.text(lx, y, label, GRAY, false);
        c.textRight(right, y, value);
    }

    // =================================================================================================================
    // Berufe
    // =================================================================================================================

    /** Reihenfolge der Karten (wie in der Referenz; Züchter und Verzauberer an den Plätzen von Sammler und Abenteurer). */
    public static final List<Job> ORDER = List.of(Job.GRAEBER, Job.HOLZFAELLER, Job.MINER, Job.FARMER, Job.ZUECHTER, Job.JAEGER,
            Job.VERZAUBERER, Job.ANGLER);

    private static TextColor jobColor(Job j) {
        return TextColor.color(switch (j) {
            case GRAEBER -> 0xFCA800;
            case HOLZFAELLER -> 0x00A800;
            case MINER -> 0xA8A8A8;
            case FARMER -> 0x54FC54;
            case ZUECHTER -> 0xFC5454;
            case JAEGER -> 0xFCFC54;
            case VERZAUBERER -> 0xFC54FC;
            case ANGLER -> 0x54FCFC;
        });
    }

    /** Symbol und Lage in der Karte. */
    private record JobIcon(KitData.Sprite sprite, int dx, int dy) {}

    private static JobIcon jobIcon(Job j) {
        return switch (j) {
            case GRAEBER -> new JobIcon(KitData.Sprite.PF_JOB_GRAEBER, 10, 9);
            case HOLZFAELLER -> new JobIcon(KitData.Sprite.PF_JOB_HOLZFAELLER, 9, 8);
            case MINER -> new JobIcon(KitData.Sprite.PF_JOB_MINER, 10, 9);
            case FARMER -> new JobIcon(KitData.Sprite.PF_JOB_FARMER, 9, 8);
            case ZUECHTER -> new JobIcon(KitData.Sprite.PF_JOB_ZUECHTER, 10, 8);
            case JAEGER -> new JobIcon(KitData.Sprite.PF_JOB_JAEGER, 8, 8);
            case VERZAUBERER -> new JobIcon(KitData.Sprite.PF_JOB_VERZAUBERER, 9, 8);
            case ANGLER -> new JobIcon(KitData.Sprite.PF_JOB_ANGLER, 9, 8);
        };
    }

    public static Canvas jobs(Head h, Jobs js, Actions a) {
        Canvas c = frame(h, Tab.JOBS, a);
        List<JobCard> cards = js.cards();
        for (int i = 0; i < cards.size(); i++) {
            JobCard jc = cards.get(i);
            int x = (i % 2) * 198, y = 108 + (i / 2) * 36;
            c.box(x, y, 192, 34, jc.main() ? KitData.Box.PF_JOB_MAIN : KitData.Box.PF_JOB);
            JobIcon ic = jobIcon(jc.job());
            c.sprite(x + ic.dx(), y + ic.dy(), ic.sprite());
            // Schrift in der rechten Spalte 1 px weiter links als in der linken (wie im Original)
            int tx = x + (i % 2 == 0 ? 20 : 19);
            int nx = tx + c.text(tx, y + 9, caps(jc.job().display), jobColor(jc.job()), false);
            if (jc.main()) c.text(nx, y + 9, " Haupt", GOLD, false);
            c.textRight(tx + 164, y + 9, "Level " + jc.level(), WHITE, true);
            bar(c, x + 8, y + 19, 176, jc.frac(), jc.main());
        }
        int fy = 108 + ((cards.size() + 1) / 2) * 36;
        c.box(0, fy, W, 25, KitData.Box.PF_FOOTER);
        c.text(10, fy + 8, List.of(Span.of("Insgesamt ", GRAY), Span.of(n(js.xp()), WHITE), Span.of(" Job-XP", LIGHT),
                Span.of(" · ", GRAY), Span.of(n(js.earned()), COINS), Span.of(" Coins verdient", LIGHT)));
        c.textRight(380, fy + 8, js.top() > 0 ? "Platz " + js.top() + " in der Top 10" : "Nicht in der Top 10",
                js.top() > 0 ? GOLD : DIM, false);
        return c;
    }

    // =================================================================================================================
    // Quests und Welt
    // =================================================================================================================

    public static Canvas quests(Head h, Quests q, Actions a) {
        Canvas c = frame(h, Tab.QUESTS, a);
        c.box(0, 108, W, 70, KitData.Box.PF_RANK);
        int r = QuestService.rank(q.tokens());
        List<Map.Entry<String, Long>> ranks = QuestService.RANKS;
        c.text(10, 118, upper("Quest-Rang"), TextColor.color(0xEFB16A), true);
        c.text(10, 130, ranks.get(r).getKey(), LIGHT, false);
        c.textRight(380, 118, List.of(Span.of(n(q.tokens()), WHITE), Span.of(" Quest-Tokens", LIGHT)));
        boolean last = r + 1 >= ranks.size();
        long from = ranks.get(r).getValue(), to = last ? from : ranks.get(r + 1).getValue();
        bar(c, 10, 145, 370, last ? 1 : (double) (q.tokens() - from) / Math.max(1, to - from), true);
        c.text(10, 159, last ? "Höchster Rang erreicht" : "Noch " + n(to - q.tokens()) + " Tokens bis zum nächsten Rang", GRAY, false);
        card(c, 0, 189, KitData.Box.PF_CARD_QUEST, KitData.Sprite.PF_Q_QUESTS, 15, 10, 6, "Quests",
                TextColor.color(0xEFB16A), n(q.quests()));
        card(c, 132, 189, KitData.Box.PF_CARD_STREAK, KitData.Sprite.PF_Q_STREAK, 14, 9, 6, "Serie",
                TextColor.color(0xFCB079), n(q.streak()));
        card(c, 264, 189, KitData.Box.PF_CARD_BEST, KitData.Sprite.PF_Q_BEST, 14, 9, 6, "Beste Serie", GOLD, n(q.best()));
        return c;
    }

    public static Canvas world(Head h, World w, Actions a) {
        Canvas c = frame(h, Tab.WORLD, a);
        card(c, 0, 108, KitData.Box.PF_CARD_GRAY, KitData.Sprite.PF_W_MINED, 18, 9, 4, "Abgebaut", NAV, n(w.mined()));
        card(c, 132, 108, KitData.Box.PF_CARD_RED, KitData.Sprite.PF_W_DEATHS, 18, 9, 4, "Tode", TextColor.color(0xFC7986),
                n(w.deaths()));
        card(c, 264, 108, KitData.Box.PF_CARD_GOLD, KitData.Sprite.PF_W_SOLD, 18, 9, 4, "Verkauft", GOLD, n(w.sold()));
        c.box(0, 171, W, 61, KitData.Box.PF_CLAIMS);
        c.sprite(10, 180, KitData.Sprite.PF_W_CLAIMS);
        c.text(30, 185, upper("Grundstücke"), GREEN, true);
        c.textRight(380, 185, List.of(Span.of(n(w.claims()), WHITE), Span.of(w.claims() == 1 ? " Grundstück" : " Grundstücke", LIGHT)));
        bar(c, 10, 199, 370, w.total() <= 0 ? 0 : (double) w.used() / w.total(), false);
        c.text(10, 214, n(w.used()) + " von " + n(w.total()) + " Blöcken genutzt", GRAY, false);
        c.textRight(380, 214, n(Math.max(0, w.total() - w.used())) + " frei", GREEN, false);
        return c;
    }

    /**
     * Karte 126 x 52: Symbol (in einem slot px breiten Platz, mittig) und Bezeichnung fett, zusammen mittig; darunter die
     * Zahl in großen Ziffern.
     */
    private static void card(Canvas c, int x, int y, KitData.Box box, KitData.Sprite icon, int slot, int iconDy, int gap,
                             String label, TextColor color, String value) {
        c.box(x, y, 126, 52, box);
        int s = Math.max(slot, icon.width);
        int group = s + gap + Canvas.width(Canvas.clean(label), true);
        int gx = x + (126 - group + 1) / 2;
        c.sprite(gx + (s - icon.width) / 2, y + iconDy, icon);
        c.text(gx + s + gap, y + 14, label, color, true);
        int adv = 0;
        for (char ch : value.toCharArray()) adv += ch == '.' || ch == ',' ? 4 : 12;
        big(c, x + (126 - adv) / 2 - 1, y + 27, value, WHITE);
    }

    /** Kapitälchen wie im Original: Umlaute als normale kleine Buchstaben (ä, ö, ü). */
    private static String caps(String text) {
        return Canvas.caps(text).replace('\ue0f0', 'ä').replace('\ue0f1', 'ö').replace('\ue0f2', 'ü');
    }

    private static String upper(String text) {
        return de.kuby.kubyplugins.util.I18n.ctx(text).toUpperCase(Locale.GERMAN);
    }
}
