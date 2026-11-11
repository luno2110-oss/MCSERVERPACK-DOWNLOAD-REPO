package de.kuby.kubysurvival.quests;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.quests.QuestScreen.Area;
import de.kuby.kubysurvival.quests.QuestScreen.Btn;
import de.kuby.kubysurvival.quests.QuestScreen.Card;
import de.kuby.kubysurvival.quests.QuestScreen.Icon;
import de.kuby.kubysurvival.quests.QuestScreen.Kind;
import de.kuby.kubysurvival.quests.QuestScreen.Reward;
import de.kuby.kubysurvival.quests.QuestScreen.Style;
import de.kuby.kubysurvival.quests.QuestService.Def;
import de.kuby.kubysurvival.quests.QuestService.Mile;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /quest mit Grafik-Pack: alle Quest-Seiten als Dialog (QuestScreen) mit den Daten aus QuestService und
 * SideQuestService. Ohne Pack (oder nach /quest gui) bleibt das Truhen-Menü.
 */
public final class QuestDialogs {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final TextColor LABEL = TextColor.color(0xFCCF79);

    private final KubySurvival addon;
    private final KubyPlugins core;
    /** Spieler, die lieber das Truhen-Menü wollen (/quest gui). */
    private final Set<UUID> chest = new HashSet<>();
    private final Map<UUID, Integer> pages = new HashMap<>();

    public QuestDialogs(KubySurvival addon) {
        this.addon = addon;
        this.core = addon.core();
    }

    private final QuestScreen.Actions actions = new QuestScreen.Actions() {
        public Consumer<Player> area(Area a) {
            return p -> {
                pages.remove(p.getUniqueId());
                if (a == Area.SIDE) sides(p);
                else list(p, a, -1);
            };
        }

        public Consumer<Player> overview() {
            return QuestDialogs.this::overview;
        }

        public Consumer<Player> page(int page) {
            return p -> {
                pages.put(p.getUniqueId(), page);
                reopen(p);
            };
        }

        public Consumer<Player> track() {
            return p -> {
                QuestService.Track t = q(p.getUniqueId()).track(p.getUniqueId());
                if (t != null && t.key().startsWith("n:")) {
                    QuestDialogs.this.navToRunning(p);
                } else {
                    QuestDialogs.this.overview(p);
                }
            };
        }

        public Consumer<Player> ranking() {
            return p -> QuestDialogs.this.ranking(p, true);
        }

        public Consumer<Player> box() {
            return QuestDialogs.this::box;
        }

        public Consumer<Player> streak() {
            return QuestDialogs.this::streak;
        }

        public Consumer<Player> help() {
            return p -> QuestDialogs.this.help(p, 0);
        }
    };

    /** Zuletzt offene Liste je Spieler (für Blättern). */
    private final Map<UUID, Area> open = new HashMap<>();

    /** Quests des Modus, in dem der Spieler gerade ist (Lobby: Survival). */
    private QuestService q(UUID id) {
        QuestService q = addon.quests(id);
        return q != null ? q : addon.quests();
    }

    /** Events und Nebenquests gibt es nur in Survival. */
    private boolean survival(UUID id) {
        return q(id).survivalMode();
    }

    /** /quest: Dialog, wenn das Pack geladen ist und der Spieler nicht das Truhen-Menü gewählt hat. */
    public boolean open(Player p) {
        if (!usable(p)) return false;
        overview(p);
        return true;
    }

    public boolean usable(Player p) {
        return core.uiPack().active(p) && !chest.contains(p.getUniqueId());
    }

    /** /quest gui: zwischen Dialog und Truhen-Menü umschalten. Gibt true zurück, wenn jetzt das Truhen-Menü gilt. */
    public boolean toggleChest(Player p) {
        if (!chest.remove(p.getUniqueId())) {
            chest.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    private void reopen(Player p) {
        Area a = open.get(p.getUniqueId());
        if (a == Area.SIDE) sides(p);
        else if (a != null) list(p, a, pages.getOrDefault(p.getUniqueId(), 0));
        else overview(p);
    }

    private void err(Player p, String e) {
        if (e != null) Msg.send(p, "<red>" + Msg.escape(e));
    }

    // =================================================================================================================
    // Übersicht
    // =================================================================================================================

    public void overview(Player p) {
        UUID id = p.getUniqueId();
        QuestService q = q(id);
        q.checkStats(p);
        open.remove(id);
        long tokens = q.questTokens(id);
        int r = QuestService.rank(tokens);
        List<Map.Entry<String, Long>> ranks = QuestService.RANKS;
        String next = r + 1 < ranks.size() ? ranks.get(r + 1).getKey() : null;
        double prog = next == null ? 1 : (double) (tokens - ranks.get(r).getValue())
                / Math.max(1, ranks.get(r + 1).getValue() - ranks.get(r).getValue());
        QuestScreen.Head head = new QuestScreen.Head(ranks.get(r).getKey(), next, prog, q.streak(id), tokens);

        QuestScreen.Tracked tracked = null;
        QuestService.Track t = q.track(id);
        if (t != null) {
            String area = switch (t.key().substring(0, 2)) {
                case "d:" -> "Täglich";
                case "w:" -> "Wöchentlich";
                case "t:" -> "Team";
                case "x:" -> "Geheim";
                case "n:" -> "Nebenquest";
                case "e:" -> ev(id) != null && ev(id).active() != null ? ev(id).active().name() : "Event";
                default -> "Hauptquests";
            };
            Def d = t.def();
            boolean side = t.key().startsWith("n:");
            tracked = new QuestScreen.Tracked(area, d.name(), d.task(), d.coins(), 0, d.tokens(),
                    (double) t.progress() / Math.max(1, d.amount()), Msg.coins(t.progress()) + "/" + Msg.coins(d.amount()),
                    side ? "Weg zeigen" : null, side ? "Am NPC" : null);
        }

        List<QuestScreen.Tile> tiles = new ArrayList<>();
        tiles.add(eventTile(id));
        List<Def> dailies = q.dailies(id);
        tiles.add(new QuestScreen.Tile(Area.DAILY, "Täglich", Canvas.caps("Neu in") + " " + until(false),
                claimedCount(id, "d:", dailies), dailies.size()));
        List<Def> weeklies = q.weeklies(id);
        tiles.add(new QuestScreen.Tile(Area.WEEKLY, "Wöchentlich", Canvas.caps("Neu in") + " " + until(true),
                claimedCount(id, "w:", weeklies), weeklies.size()));
        int si = q.storyIndex(id);
        tiles.add(new QuestScreen.Tile(Area.MAIN, "Hauptquests", si < q.storySize()
                ? Canvas.caps("Quest") + " " + (si + 1) : Canvas.caps("Alle geschafft"), si, q.storySize()));
        int[] mile = milestoneCount(id);
        tiles.add(new QuestScreen.Tile(Area.MILE, "Meilensteine", Canvas.caps("Nächster") + " " + nextMilestonePercent(id) + " %",
                mile[0], mile[1]));
        Def td = q.teamDef(id);
        int teamPct = td == null ? 0 : (int) Math.min(100, q.progressOf(id, "t:" + td.id()) * 100 / Math.max(1, td.amount()));
        tiles.add(new QuestScreen.Tile(Area.TEAM, "Team", td == null ? Canvas.caps("Kein Team") : Canvas.caps(td.name()), teamPct, -1));
        if (survival(id)) {
            int[] side = sideCount(id);
            tiles.add(new QuestScreen.Tile(Area.SIDE, "Nebenquests", side[2] + " " + Canvas.caps("frei"), side[0], side[1]));
        } else {
            // SMP und Skyblock haben keine Nebenquest-Figuren: hier stehen die geheimen Quests des Modus.
            int found = q.secretsFound(id).size(), all = q.secretCount();
            tiles.add(new QuestScreen.Tile(Area.SIDE, "Geheim", found + "/" + all + " " + Canvas.caps("gefunden"), found, all));
        }
        java.util.Set<Area> alerts = alerts(id);
        tiles.replaceAll(tl -> tl.alerted(alerts.contains(tl.area())));
        UiDialog.show(p, QuestScreen.overview(new QuestScreen.Overview(head, tracked, tiles), actions), QuestScreen.HEIGHT);
    }

    /** Bereiche mit abholbarer Belohnung (rotes Ausrufezeichen). */
    public java.util.Set<Area> alerts(UUID id) {
        java.util.Set<Area> out = java.util.EnumSet.noneOf(Area.class);
        for (String k : q(id).claimable(id)) {
            switch (k.substring(0, 2)) {
                case "d:" -> out.add(Area.DAILY);
                case "w:" -> out.add(Area.WEEKLY);
                case "t:" -> out.add(Area.TEAM);
                case "m:" -> out.add(Area.MILE);
                default -> out.add(Area.MAIN);
            }
        }
        EventService es = ev(id);
        if (es != null && es.active() != null) {
            int acc = es.accepted(id);
            if (acc >= 0 && es.state(id, acc) == EventService.State.DONE) out.add(Area.EVENT);
        }
        return out;
    }

    private int claimedCount(UUID id, String prefix, List<Def> defs) {
        int n = 0;
        for (Def d : defs) if (q(id).isClaimed(id, prefix + d.id())) n++;
        return n;
    }

    /** Zeit bis zum nächsten Tag (bzw. Montag) um 00:00, kurz („23min“, „5h“, „3d“). */
    static String until(boolean week) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime next = week ? now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay()
                : now.toLocalDate().plusDays(1).atStartOfDay();
        Duration d = Duration.between(now, next);
        if (d.toDays() >= 1) return d.toDays() + "d";
        if (d.toHours() >= 1) return d.toHours() + "h";
        return Math.max(1, d.toMinutes()) + "min";
    }

    // =================================================================================================================
    // Listen
    // =================================================================================================================

    private List<QuestScreen.Tab> tabs(UUID id) {
        QuestService q = q(id);
        List<Def> dl = q.dailies(id), wl = q.weeklies(id);
        int[] mile = milestoneCount(id);
        Def td = q.teamDef(id);
        int teamPct = td == null ? 0 : (int) Math.min(100, q.progressOf(id, "t:" + td.id()) * 100 / Math.max(1, td.amount()));
        EventService.Event e = ev(id) == null ? null : ev(id).active();
        java.util.Set<Area> al = alerts(id);
        List<QuestScreen.Tab> list = new ArrayList<>(List.of(e == null ? new QuestScreen.Tab(Area.EVENT, "Event", "0/0")
                        : new QuestScreen.Tab(Area.EVENT, e.name(), ev(id).claimedCount(id) + "/" + e.chapters().size(),
                        QuestScreen.Theme.of(e.theme())),
                new QuestScreen.Tab(Area.DAILY, "Täglich", claimedCount(id, "d:", dl) + "/" + dl.size()),
                new QuestScreen.Tab(Area.WEEKLY, "Wöchentlich", claimedCount(id, "w:", wl) + "/" + wl.size()),
                new QuestScreen.Tab(Area.MAIN, "Hauptquests", q.storyIndex(id) + "/" + q.storySize()),
                new QuestScreen.Tab(Area.MILE, "Meilensteine", mile[0] + "/" + mile[1]),
                new QuestScreen.Tab(Area.TEAM, "Team", teamPct + " %")));
        list.replaceAll(tb -> tb.alerted(al.contains(tb.area())));
        return list;
    }

    /** Eine Tages-, Wochen- oder Team-Quest als Karte: Verfolgen, Abholen oder abgeholt. */
    private Card taskCard(UUID id, String key, Def d, String label, TextColor labelColor) {
        QuestService q = q(id);
        long prog = Math.min(q.progressOf(id, key), d.amount());
        boolean done = q.isDone(id, key), claimed = q.isClaimed(id, key);
        boolean tracked = key.equals(q.trackedKey(id));
        int pct = (int) (prog * 100 / Math.max(1, d.amount()));
        Btn b;
        String text, sub;
        Consumer<Player> click;
        if (claimed) {
            b = Btn.LINE;
            text = "Abgeholt";
            sub = "✔";
            click = null;
        } else if (done) {
            b = Btn.GOLD;
            text = "Abholen";
            sub = "100 %";
            click = p -> {
                err(p, q.claim(p, key));
                reopen(p);
            };
        } else {
            b = Btn.LINE;
            text = tracked ? "Verfolgt" : "Verfolgen";
            sub = pct + " %";
            click = p -> {
                q.setTracked(p.getUniqueId(), tracked ? null : key);
                reopen(p);
            };
        }
        return new Card(Style.BROWN, label, labelColor, null, Icon.TASK, d.name(), d.task(), rewards(d.coins(), 0, d.tokens()),
                (double) prog / Math.max(1, d.amount()), false, Msg.coins(prog) + "/" + Msg.coins(d.amount()), b, text, sub, null, click);
    }

    private static List<Reward> rewards(long coins, long claims, long tokens) {
        List<Reward> out = new ArrayList<>();
        if (coins > 0) out.add(Reward.of(Kind.COINS, coins));
        if (claims > 0) out.add(Reward.of(Kind.CLAIMS, claims));
        if (tokens > 0) out.add(Reward.of(Kind.TOKENS, tokens));
        return out;
    }

    /** Liste eines Bereichs; page < 0: Seite mit der aktuellen Quest (Hauptquests) bzw. 0. */
    public void list(Player p, Area a, int page) {
        UUID id = p.getUniqueId();
        QuestService q = q(id);
        q.checkStats(p);
        open.put(id, a);
        List<Card> all = new ArrayList<>();
        String title = a.label, subtitle = "", count;
        String infoLabel = null, info = null;
        QuestScreen.Bonus bonus = null;
        QuestScreen.Shop shop = null;
        int perPage = 3;
        switch (a) {
            case DAILY, WEEKLY -> {
                boolean daily = a == Area.DAILY;
                List<Def> defs = daily ? q.dailies(id) : q.weeklies(id);
                for (Def d : defs) {
                    all.add(taskCard(id, (daily ? "d:" : "w:") + d.id(), d, daily ? "Alltag" : null, LABEL));
                }
                int c = claimedCount(id, daily ? "d:" : "w:", defs);
                count = c + "/" + defs.size();
                subtitle = "Neue Aufträge in " + until(!daily);
                long[] b = q.setBonus(daily);
                if (b != null && !defs.isEmpty()) {
                    bonus = new QuestScreen.Bonus(c, defs.size(), until(!daily), rewards(b[0], 0, b[1]));
                    perPage = daily ? 2 : 3;
                }
            }
            case MAIN -> {
                int si = q.storyIndex(id), size = q.storySize();
                for (int i = 0; i < size; i++) {
                    Def d = q.storyAt(i);
                    String key = "s:" + d.id();
                    if (i < si) {
                        all.add(new Card(Style.BROWN, null, null, null, Icon.TASK, d.name(), d.task(), rewards(d.coins(), 0, d.tokens()),
                                1.0, false, Canvas.caps("Erledigt"), Btn.NONE, null, null, null, null));
                    } else if (i == si) {
                        long prog = Math.min(q.progressOf(id, key), d.amount());
                        boolean done = q.isDone(id, key);
                        all.add(new Card(Style.GOLD, null, null, null, Icon.TRACK, d.name(), d.task(), rewards(d.coins(), 0, d.tokens()),
                                (double) prog / Math.max(1, d.amount()), false, Msg.coins(prog) + "/" + Msg.coins(d.amount()),
                                Btn.GOLD, done ? "Abholen" : "Verfolgen", Canvas.caps("Quest") + " " + (i + 1),
                                null, done ? pl -> {
                                    err(pl, q.claim(pl, key));
                                    reopen(pl);
                                } : pl -> {
                                    q.setTracked(pl.getUniqueId(), key);
                                    reopen(pl);
                                }));
                    } else {
                        all.add(new Card(Style.GRAY, null, null, null, Icon.LOCK, d.name(), d.task(), rewards(d.coins(), 0, d.tokens()),
                                null, false, null, Btn.LOCKED, null, null, "Erst Quest " + i, null));
                    }
                }
                count = si + "/" + size;
                subtitle = si < size ? "Quest " + (si + 1) + " · " + si + "/" + size + " Quests" : "Alle Hauptquests geschafft";
                if (page < 0) page = Math.min(si, Math.max(0, size - 1)) / 3;
            }
            case MILE -> {
                for (Mile m : q.milestones()) all.add(mileCard(p, m));
                int[] mc = milestoneCount(id);
                count = mc[0] + "/" + mc[1];
                subtitle = Canvas.caps("Nächster") + " " + nextMilestonePercent(id) + " %";
            }
            case TEAM -> {
                Def td = q.teamDef(id);
                if (td == null) {
                    infoLabel = "Kein Team";
                    info = "Team-Aufgaben laufen nur mit einem Survival-Team.";
                    count = "0 %";
                } else {
                    all.add(taskCard(id, "t:" + td.id(), td, null, LABEL));
                    count = (int) Math.min(100, q.progressOf(id, "t:" + td.id()) * 100 / Math.max(1, td.amount())) + " %";
                }
                subtitle = "Neue Aufgabe in " + until(true);
            }
            default -> {
                EventService.Event e = ev(id) == null ? null : ev(id).active();
                if (e == null) {
                    infoLabel = "Kein Event";
                    info = "Gerade läuft kein Event. Schau später wieder vorbei.";
                    count = "0/0";
                    subtitle = "Gerade kein Event";
                    title = "Event";
                } else {
                    QuestScreen.Theme th = QuestScreen.Theme.of(e.theme());
                    for (EventService.Chapter c : e.chapters()) all.add(chapterCard(id, e, c, th));
                    int done = ev(id).claimedCount(id);
                    count = done + "/" + e.chapters().size();
                    title = e.name();
                    subtitle = done + " von " + e.chapters().size() + " Kapiteln · " + ev(id).endText(e);
                    shop = new QuestScreen.Shop(e.currency(), ev(id).candy(id), this::shop, th, e.shopName());
                    if (page < 0) page = Math.max(0, ev(id).current(id)) / 3;
                }
            }
        }
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        if (page < 0) page = 0;
        page = Math.min(page, pages - 1);
        pages_put(id, page);
        List<Card> cards = all.subList(page * perPage, Math.min(all.size(), (page + 1) * perPage));
        UiDialog.show(p, QuestScreen.list(new QuestScreen.ListView(a, title, subtitle, count, tabs(id), new ArrayList<>(cards),
                infoLabel, info, page, pages, bonus, shop), actions), QuestScreen.HEIGHT);
    }

    // =================================================================================================================
    // Event
    // =================================================================================================================

    /** Events gibt es nur in Survival (in SMP und Skyblock: null). */
    private EventService ev(UUID id) {
        return survival(id) ? addon.events() : null;
    }

    private QuestScreen.Tile eventTile(UUID id) {
        EventService.Event e = ev(id) == null ? null : ev(id).active();
        if (e == null) return new QuestScreen.Tile(Area.EVENT, "Event", Canvas.caps("Gerade keins"), 0, 0);
        int cur = ev(id).current(id);
        return new QuestScreen.Tile(Area.EVENT, e.name(), cur < 0 ? Canvas.caps("Alle geschafft") : Canvas.caps("Kapitel") + " " + (cur + 1),
                ev(id).claimedCount(id), e.chapters().size(), QuestScreen.Theme.of(e.theme()).head());
    }

    private static List<Reward> eventRewards(EventService.Chapter c, QuestScreen.Theme th) {
        List<Reward> out = new ArrayList<>();
        if (c.coins() > 0) out.add(Reward.of(Kind.COINS, c.coins()));
        if (c.candy() > 0) out.add(Reward.currency(c.candy(), th));
        if (c.tokens() > 0) out.add(Reward.of(Kind.TOKENS, c.tokens()));
        return out;
    }

    /** Kapitel als Karte: offen (Annehmen), angenommen (Ziele, Verfolgen), geschafft (Abholen), abgeholt oder gesperrt. */
    private Card chapterCard(UUID id, EventService.Event e, EventService.Chapter c, QuestScreen.Theme th) {
        EventService es = ev(id);
        int i = c.index(), n = c.goals().size();
        EventService.State st = es.state(id, i);
        String ziele = Canvas.caps("Ziele");
        String kap = Canvas.caps("Kapitel") + " " + (i + 1);
        return switch (st) {
            case OPEN -> new Card(Style.GOLD, null, null, null, Icon.QUEST, c.name(), c.text(), eventRewards(c, th), 0.0, false,
                    "0/" + n + " " + ziele, Btn.GOLD, "Annehmen", kap, null, p -> {
                err(p, es.accept(p, i));
                reopen(p);
            });
            case ACCEPTED, DONE -> {
                StringBuilder desc = new StringBuilder(c.text());
                double sum = 0;
                for (int g = 0; g < n; g++) {
                    EventService.Goal goal = c.goals().get(g);
                    long have = es.goalProgress(id, i, g);
                    sum += (double) have / Math.max(1, goal.amount());
                    desc.append("\n").append(have >= goal.amount() ? "✔ " : "▸ ").append(goal.text());
                    if (have < goal.amount()) desc.append(" · ").append(Msg.coins(have)).append("/").append(Msg.coins(goal.amount()));
                }
                int done = es.goalsDone(id, i);
                boolean tracked = ("e:" + i).equals(q(id).trackedKey(id));
                Consumer<Player> click = st == EventService.State.DONE ? p -> {
                    err(p, es.claim(p, i));
                    reopen(p);
                } : p -> {
                    q(p.getUniqueId()).setTracked(p.getUniqueId(), tracked ? null : "e:" + i);
                    reopen(p);
                };
                yield new Card(Style.GOLD, null, null, null, Icon.TRACK, c.name(), desc.toString(), eventRewards(c, th), n == 0 ? 1.0 : sum / n,
                        false, done + "/" + n + " " + ziele, st == EventService.State.DONE ? Btn.GOLD : Btn.LINE,
                        st == EventService.State.DONE ? "Abholen" : tracked ? "Verfolgt" : "Verfolgen", kap, null, click);
            }
            case CLAIMED -> new Card(Style.BROWN, null, null, null, Icon.TASK, c.name(), c.text(), eventRewards(c, th), 1.0, false,
                    Canvas.caps("Erledigt"), Btn.NONE, null, null, null, null);
            default -> new Card(Style.GRAY, null, null, null, Icon.LOCK, c.name(), c.text(), eventRewards(c, th), null, false, null,
                    Btn.LOCKED, null, null, es.lockText(id, i), null);
        };
    }

    /** Shop des laufenden Events (Halloween: Candy-Shop). */
    public void shop(Player p) {
        EventService es = ev(p.getUniqueId());
        EventService.Event e = es == null ? null : es.active();
        if (e == null) {
            list(p, Area.EVENT, 0);
            return;
        }
        UUID id = p.getUniqueId();
        long candy = es.candy(id);
        List<QuestScreen.ShopOffer> offers = new ArrayList<>();
        for (EventService.Offer o : e.shop()) {
            boolean owned = o.once() && es.owned(id, o);
            offers.add(new QuestScreen.ShopOffer(offerIcon(o), o.name(), o.price(), owned, candy >= o.price(), owned ? null : pl -> {
                err(pl, es.buy(pl, o));
                shop(pl);
            }, offerItem(o)));
        }
        UiDialog.show(p, QuestScreen.eventShop(new QuestScreen.ShopView(e.name(), e.currency(), candy, offers,
                        QuestScreen.Theme.of(e.theme()), e.shopName()),
                pl -> list(pl, Area.EVENT, -1)), QuestScreen.HEIGHT);
    }

    /** Gegenstände im Event-Shop als echtes Item-Bild (ItemIcons), sonst null (dann das Kit-Icon). */
    private static Material offerItem(EventService.Offer o) {
        return switch (o.kind()) {
            case "TITLE", "COSMETIC", "KEY", "COINS", "TOKENS" -> null;
            default -> Material.matchMaterial(o.value());
        };
    }

    private static KitData.Icon offerIcon(EventService.Offer o) {
        return switch (o.kind()) {
            case "TITLE" -> KitData.Icon.NAME_TAG;
            case "COSMETIC" -> hatIcon(o.value());
            case "KEY" -> KitData.Icon.TRIAL_KEY;
            case "COINS" -> KitData.Icon.GOLD_INGOT;
            case "TOKENS" -> KitData.Icon.EXPERIENCE;
            default -> de.kuby.kubyplugins.ui.Icons.of(Material.matchMaterial(o.value()), KitData.Icon.PAPER);
        };
    }

    /** Hut-Kosmetik (hut_<name>) mit ihrem Bild, sonst Amethyst. */
    private static KitData.Icon hatIcon(String cosmetic) {
        String v = cosmetic.toLowerCase(java.util.Locale.ROOT);
        if (v.startsWith("hut_")) {
            try {
                return KitData.Icon.valueOf("HAT_" + v.substring(4).toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // kein Bild für diesen Hut
            }
        }
        return KitData.Icon.AMETHYST;
    }

    private void pages_put(UUID id, int page) {
        pages.put(id, page);
    }

    // =================================================================================================================
    // Meilensteine
    // =================================================================================================================

    /** [erreichte Stufen, Stufen gesamt] über alle Meilensteine. */
    private int[] milestoneCount(UUID id) {
        int done = 0, all = 0;
        for (Mile m : q(id).milestones()) {
            all += m.tiers().length;
            done += Math.min(m.tiers().length, q(id).nextTier(id, m) - 1);
        }
        return new int[]{done, all};
    }

    /** Fortschritt des Meilensteins, der am nächsten an seiner nächsten Stufe ist (Prozent). */
    private int nextMilestonePercent(UUID id) {
        int best = 0;
        for (Mile m : q(id).milestones()) {
            int t = q(id).nextTier(id, m);
            if (t > m.tiers().length) continue;
            long cur = q(id).stat(m.stat(), id), goal = m.tiers()[t - 1];
            best = Math.max(best, (int) Math.min(100, cur * 100 / Math.max(1, goal)));
        }
        return best;
    }

    private static String mileTask(Mile m, long goal) {
        String n = Msg.coins(goal);
        return switch (m.stat()) {
            case "player_level" -> "Erreiche Level " + n + ".";
            case "job_level" -> "Erreiche Job-Level " + n + ".";
            case "fish_caught" -> "Angle " + n + " Fische.";
            case "playtime" -> "Spiele " + Msg.coins(goal / 60) + " Stunden. AFK zählt nicht.";
            case "quests_done" -> "Schließe " + n + " Quests ab.";
            case "quest_streak_best" -> "Halte einen Streak von " + n + " Tagen.";
            case "claims" -> goal == 1 ? "Besitze 1 Grundstück." : "Besitze " + n + " Grundstücke.";
            case "kills" -> "Besiege " + n + " Spieler.";
            case "trophies" -> "Sammle " + n + " Trophäen.";
            case "smp_xp" -> "Sammle " + n + " XP im SMP.";
            case "island_level" -> "Bringe deine Insel auf Level " + n + ".";
            case "island_points" -> "Erreiche " + n + " Insel-Punkte.";
            case "island_members" -> "Habe " + n + " Leute auf deiner Insel.";
            case "generators" -> goal == 1 ? "Stelle 1 Generator auf." : "Stelle " + n + " Generatoren auf.";
            default -> "Erreiche " + n + ".";
        };
    }

    private Card mileCard(Player p, Mile m) {
        UUID id = p.getUniqueId();
        QuestService q = q(id);
        int tier = q.nextTier(id, m);
        boolean all = tier > m.tiers().length;
        int shown = Math.min(tier, m.tiers().length);
        long goal = m.tiers()[shown - 1], cur = q.stat(m.stat(), id);
        boolean done = !all && cur >= goal;
        int pct = (int) Math.min(100, cur * 100 / Math.max(1, goal));
        String key = "m:" + m.id() + ":" + tier;
        String count = m.stat().equals("playtime") ? Msg.coins(Math.min(cur, goal) / 60) + "/" + Msg.coins(goal / 60) + "h"
                : Msg.coins(Math.min(cur, goal)) + "/" + Msg.coins(goal);
        Btn b = all ? Btn.NONE : done ? Btn.GOLD : Btn.LINE;
        return new Card(Style.BROWN, null, null, null, Icon.TASK, m.name() + " " + QuestService.roman(shown), mileTask(m, goal),
                rewards(m.coins() * shown, 0, m.tokens() * shown), all ? 1.0 : Math.min(1.0, (double) cur / Math.max(1, goal)), true,
                count, b, done ? "Abholen" : "Verfolgen", all ? null : pct + " %", all ? "Alle Stufen erreicht" : null,
                done ? pl -> {
                    err(pl, q.claim(pl, key));
                    reopen(pl);
                } : null);
    }

    // =================================================================================================================
    // Nebenquests
    // =================================================================================================================

    private static final Map<String, KitData.Sprite> FACES = Map.of("leonie", KitData.Sprite.QU_FACE_LEONIE,
            "frieda", KitData.Sprite.QU_FACE_FRIEDA, "mortis", KitData.Sprite.QU_FACE_MORTIS, "lilia", KitData.Sprite.QU_FACE_LILIA,
            "konrad", KitData.Sprite.QU_FACE_KONRAD, "hermann", KitData.Sprite.QU_FACE_HERMANN, "rudolf", KitData.Sprite.QU_FACE_RUDOLF,
            "mia", KitData.Sprite.QU_FACE_MIA, "ralf", KitData.Sprite.QU_FACE_RALF);

    private static KitData.Sprite face(SideQuestService.Figure f) {
        String first = f.name().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, KitData.Sprite> e : FACES.entrySet()) if (first.contains(e.getKey())) return e.getValue();
        return null;
    }

    static String job(String profession) {
        return switch (profession == null ? "" : profession.toLowerCase(Locale.ROOT)) {
            case "farmer" -> "Farmer";
            case "fisherman" -> "Fischer";
            case "weaponsmith", "toolsmith" -> "Schmied";
            case "armorer" -> "Wächter";
            case "cleric" -> "Priester";
            case "librarian" -> "Bibliothekar";
            case "cartographer" -> "Kartograf";
            case "leatherworker" -> "Holzfäller";
            case "butcher" -> "Metzger";
            case "mason" -> "Steinmetz";
            case "shepherd" -> "Schäfer";
            case "fletcher" -> "Pfeilmacher";
            default -> "Dorfbewohner";
        };
    }

    /** [geschafft, gesamt, Figuren mit offener Aufgabe] */
    private int[] sideCount(UUID id) {
        SideQuestService s = addon.sideQuests();
        if (s == null) return new int[]{0, 0, 0};
        int done = 0, all = 0, free = 0;
        for (SideQuestService.Figure f : s.figures()) {
            int fin = s.finished(id, f.id());
            done += fin;
            all += f.steps().size();
            if (fin < f.steps().size()) free++;
        }
        return new int[]{done, all, free};
    }

    public void sides(Player p) {
        UUID id = p.getUniqueId();
        if (!survival(id)) {
            secrets(p);
            return;
        }
        open.put(id, Area.SIDE);
        SideQuestService s = addon.sideQuests();
        List<SideQuestService.Figure> figs = s == null ? List.of() : s.figures();
        int page = pages.getOrDefault(id, 0), pagesN = Math.max(1, (figs.size() + 8) / 9);
        page = Math.max(0, Math.min(page, pagesN - 1));
        List<QuestScreen.Npc> npcs = new ArrayList<>();
        for (int i = page * 9; i < Math.min(figs.size(), page * 9 + 9); i++) {
            SideQuestService.Figure f = figs.get(i);
            int fin = s.finished(id, f.id());
            boolean openQ = fin < f.steps().size();
            String quest = openQ ? f.steps().get(fin).name() : "Alles erledigt";
            npcs.add(new QuestScreen.Npc(f.name(), face(f), job(f.profession()), quest, fin, f.steps().size(), openQ,
                    pl -> side(pl, f.id())));
        }
        int[] c = sideCount(id);
        UiDialog.show(p, QuestScreen.sides(new QuestScreen.SideView(c[0], c[1], npcs, page, pagesN), actions), QuestScreen.HEIGHT);
    }

    /** SMP und Skyblock: gefundene geheime Quests im Chat, der Rest bleibt verborgen. */
    private void secrets(Player p) {
        UUID id = p.getUniqueId();
        QuestService q = q(id);
        List<Def> found = q.secretsFound(id);
        Msg.send(p, "<light_purple><b>Geheime Quests</b> <gray>(" + found.size() + "/" + q.secretCount() + " gefunden)");
        for (Def d : found) {
            String key = "x:" + d.id();
            String state = q.isClaimed(id, key) ? "<green>✔" : "<click:run_command:'/quest abholen'><gold>[Abholen]</click>";
            Msg.send(p, " " + state + " <white>" + Msg.escape(d.name()) + " <gray>– " + Msg.escape(d.task()));
        }
        int hidden = q.secretCount() - found.size();
        if (hidden > 0) Msg.send(p, " <dark_gray>" + hidden + " weitere warten noch darauf, entdeckt zu werden …");
        overview(p);
    }

    public void side(Player p, String figureId) {
        UUID id = p.getUniqueId();
        SideQuestService s = addon.sideQuests();
        if (s == null) return;
        SideQuestService.Figure f = null;
        for (SideQuestService.Figure x : s.figures()) if (x.id().equals(figureId)) f = x;
        if (f == null) {
            sides(p);
            return;
        }
        int fin = s.finished(id, f.id());
        boolean openQ = fin < f.steps().size();
        SideQuestService.Figure running = s.runningFigure(id);
        boolean mine = running != null && running.id().equals(f.id());
        Def d = openQ ? f.steps().get(fin) : f.steps().get(f.steps().size() - 1);
        String quote = fin < f.lines().size() ? "„" + f.lines().get(Math.min(fin, f.lines().size() - 1)) + "“" : d.task();
        List<QuestScreen.Goal> goals = new ArrayList<>();
        long have = mine ? s.runningProgress(id) : 0;
        if ("bring".equals(d.type()) && d.arg() != null) {
            Material mat = Material.matchMaterial(d.arg());
            if (mat != null) {
                have = 0;
                for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() == mat) have += it.getAmount();
            }
            goals.add(new QuestScreen.Goal(icon(mat), d.task(), Math.min(have, d.amount()), d.amount()));
        } else {
            goals.add(new QuestScreen.Goal(null, d.task(), Math.min(have, d.amount()), d.amount()));
        }
        String status = !openQ ? "Alles erledigt" : mine ? "Läuft" : "Verfügbar";
        String button = !openQ ? null : mine ? "Weg zeigen" : "Annehmen & verfolgen";
        String fid = f.id(), fname = f.name();
        Consumer<Player> click = !openQ ? null : mine ? pl -> {
            s.setNavTarget(pl.getUniqueId(), fid);
            Msg.send(pl, "<green>Der Weg zu " + Msg.escape(fname) + " wird angezeigt.");
            pl.closeInventory();
        } : pl -> {
            String e = s.accept(pl, fid);
            if (e != null && e.startsWith("Sprich dafür direkt")) {
                s.setNavTarget(pl.getUniqueId(), fid);
                Msg.send(pl, "<yellow>Geh zu " + Msg.escape(fname) + ", um die Quest anzunehmen. Der Weg wird angezeigt.");
            } else {
                err(pl, e);
            }
            side(pl, fid);
        };
        UiDialog.show(p, QuestScreen.side(new QuestScreen.SideDetail(f.name(), face(f), status, openQ && !mine, job(f.profession()),
                fin, f.steps().size(), Math.min(fin + 1, f.steps().size()), null, d.name(), quote, goals, d.coins(), d.tokens(),
                button, click), this::sides), QuestScreen.HEIGHT);
    }

    private static KitData.Icon icon(Material m) {
        if (m == null) return null;
        try {
            return KitData.Icon.valueOf(m.name());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void navToRunning(Player p) {
        SideQuestService s = addon.sideQuests();
        SideQuestService.Figure f = s == null ? null : s.runningFigure(p.getUniqueId());
        if (f == null) {
            overview(p);
            return;
        }
        s.setNavTarget(p.getUniqueId(), f.id());
        Msg.send(p, "<green>Der Weg zu " + Msg.escape(f.name()) + " wird angezeigt.");
        p.closeInventory();
    }

    // =================================================================================================================
    // Streak, Abholbox, Hilfe, Rangliste
    // =================================================================================================================

    private List<QuestScreen.Stage> stages(UUID id) {
        int best = q(id).bestStreak(id);
        List<QuestScreen.Stage> out = new ArrayList<>();
        q(id).streakRewards().entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(4)
                .forEach(e -> out.add(new QuestScreen.Stage(e.getKey(), 0, e.getValue(), best >= e.getKey())));
        return out;
    }

    public void streak(Player p) {
        UUID id = p.getUniqueId();
        int days = q(id).streak(id);
        List<QuestScreen.Stage> st = stages(id);
        int nextIn = -1;
        for (QuestScreen.Stage s : st) if (s.days() > days) {
            nextIn = s.days() - days;
            break;
        }
        UiDialog.show(p, QuestScreen.streak(new QuestScreen.StreakView(days, q(id).bestStreak(id), null, st, nextIn),
                this::overview), QuestScreen.HEIGHT);
    }

    public void box(Player p) {
        int free = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack it = p.getInventory().getStorageContents()[i];
            if (it == null || it.getType().isAir()) free++;
        }
        UiDialog.show(p, QuestScreen.box(new QuestScreen.BoxView(List.of(), free, null), this::overview), QuestScreen.HEIGHT);
    }

    public void help(Player p, int page) {
        List<Map.Entry<String, Long>> ranks = QuestService.RANKS;
        StringBuilder sb = new StringBuilder(ranks.get(0).getKey());
        for (int i = 1; i < ranks.size(); i++) {
            sb.append(i == ranks.size() - 1 ? " und " : ", ").append(ranks.get(i).getKey()).append(" ab ").append(ranks.get(i).getValue());
        }
        sb.append('.');
        String main = q(p.getUniqueId()).storySize() + " Hauptquests, der Reihe nach. Die nächste kommt, wenn du die letzte abgeholt hast.";
        long[] db = q(p.getUniqueId()).setBonus(true), wb = q(p.getUniqueId()).setBonus(false);
        String bonus = (db == null ? "Tagesaufträge: kein Bonus" : "Alle Tagesaufträge: " + Msg.coins(db[0]) + " Coins und " + db[1] + " Tokens")
                + "\n" + (wb == null ? "Wochenaufträge: kein Bonus" : "Alle Wochenaufträge: " + Msg.coins(wb[0]) + " Coins und " + wb[1] + " Tokens");
        UiDialog.show(p, QuestScreen.help(page, QuestScreen.helpPage(page, sb.toString(), stages(p.getUniqueId()), main, bonus),
                this::overview, i -> pl -> help(pl, i)), QuestScreen.HEIGHT);
    }

    public void ranking(Player p, boolean month) {
        UUID id = p.getUniqueId();
        QuestService q = q(id);
        List<Map.Entry<UUID, Long>> all = q.topTokens(month, 0);
        List<QuestScreen.Entry> top = new ArrayList<>();
        int place = 0;
        for (int i = 0; i < all.size(); i++) {
            Map.Entry<UUID, Long> e = all.get(i);
            if (e.getKey().equals(id)) place = i + 1;
            if (i < 10) {
                String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
                top.add(new QuestScreen.Entry(i + 1, name == null ? "?" : name, e.getValue()));
            }
        }
        long mine = month ? q.monthTokens(id) : q.questTokens(id);
        String hint = null;
        if (place == 0 || place > 10) {
            long tenth = top.size() >= 10 ? top.get(9).tokens() : 0;
            hint = top.size() < 10 ? "Hol dir Tokens mit Quests, dann stehst du hier." : "Noch " + Msg.coins(Math.max(1, tenth - mine + 1))
                    + " Tokens bis Platz 10";
        }
        String monthName = LocalDateTime.now(ZONE).getMonth().getDisplayName(TextStyle.FULL, Locale.GERMAN);
        UiDialog.show(p, QuestScreen.ranking(new QuestScreen.RankView(month, monthName, mine, place, top, hint),
                this::overview, pl -> ranking(pl, true), pl -> ranking(pl, false)), QuestScreen.HEIGHT);
    }
}
