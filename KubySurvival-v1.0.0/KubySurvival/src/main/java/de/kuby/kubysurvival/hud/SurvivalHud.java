package de.kuby.kubysurvival.hud;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.InfoBar;
import de.kuby.kubyplugins.service.InfoBar.Design;
import de.kuby.kubyplugins.service.InfoBar.Element;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Ui;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.claim.ClaimService;
import de.kuby.kubysurvival.angeln.AngelService;
import de.kuby.kubysurvival.jobs.JobService;
import de.kuby.kubysurvival.quests.QuestService;
import de.kuby.kubysurvival.quests.SideQuestService;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Survival-Leiste oben (Platz der Bossbar) nach der Referenz „Neue-referenzen“: Leisten im gewählten Design (Wald,
 * Kristall, Krone, Frost) mit Uhrzeit, Geld (Coins und Juwelen nebeneinander in einer Leiste), Job und Ort in der
 * Reihenfolge aus /bossbar. Verfolgte Quest und Nebenquest stehen als eigene Balken darunter (ebenfalls in dieser
 * Reihenfolge). Die Leisten sind Zeichen der Schrift kubyplugins:hud aus dem Pack KubyUI, der Bossbar-Balken selbst ist
 * durch das Pack unsichtbar (Farbe weiß). Solange die Leiste sichtbar ist, entfällt die Anzeige über der Hotbar.
 * Muss zu tools/build_sb_pack.py (build_infobar) passen; die Vorschau im Menü (InfoBarScreen) nutzt dieselben Maße.
 */
public final class SurvivalHud implements Listener {

    private static final Key FONT = Key.key("kubyplugins", "hud");
    /** Je Design: Mittelstück 1, 2, 4, ..., 64 px ab base, Enden bei base + 8 und base + 9 (siehe build_infobar). */
    private static final int IB_BASE = 0xE200, IB_STEP = 0x20;
    private static final int[] TILE_WIDTHS = {64, 32, 16, 8, 4, 2, 1};
    private static final char I_COIN = '', I_GEM = '', I_ROD = '', DOT = '';
    /** Kompass und Uhr aus dem Scoreboard (8 x 8, 1 px über der Schrift). */
    private static final char I_COMPASS = '', I_CLOCK = '';
    private static final int GAP = 4;                          // Abstand zwischen den Leisten
    private static final TextColor DOT_COLOR = TextColor.color(0x545454);

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    /** Balken unter der Leiste (Quest, Nebenquest) in der Reihenfolge, in der sie gezeigt werden. */
    private final Map<UUID, List<Row>> rows = new HashMap<>();
    /** Unter der Leiste eine leere Bossbar als Abstand zu den Balken. */
    private final Map<UUID, BossBar> spacers = new HashMap<>();

    private record Row(Element element, BossBar bar) {}

    public SurvivalHud(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        core.setHotbarHidden(p -> bars.containsKey(p.getUniqueId()));
        InfoBar.jobSource(this::jobLine);
        addon.getServer().getScheduler().runTaskTimer(addon, this::tick, 20L, 10L);
    }

    private InfoBar.JobLine jobLine(Player p) {
        JobService jobs = addon.jobs();
        JobService.Worker w = jobs.worker(p.getUniqueId());
        if (w.job == null) return InfoBar.JobLine.NONE;
        return new InfoBar.JobLine(w.job.display, jobs.jobLevel(w, w.job), jobs.progress(w, w.job), w.earnedHour,
                jobs.hourlyCap(w));
    }

    private boolean survival(Player p) {
        return ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()));
    }

    /** Leiste nur in Survival und nur mit geladenem Pack (sonst bleibt die Anzeige über der Hotbar). */
    private boolean wants(Player p) {
        UUID id = p.getUniqueId();
        if (!core.getConfig().getBoolean("survival-hud.enabled", true) || !core.infoBar().on(id) || !survival(p)
                || !core.uiPack().active(p)) return false;
        for (Element e : Element.values()) if (!e.row() && core.infoBar().visible(id, e)) return true;
        return false;
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            BossBar bar = bars.get(id);
            boolean added = false;
            if (!wants(p)) {
                if (bar != null) {
                    p.hideBossBar(bar);
                    bars.remove(id);
                    added = true;
                }
            } else {
                Component title = build(p);
                if (bar == null) {
                    bar = BossBar.bossBar(title, 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                    bars.put(id, bar);
                    p.showBossBar(bar);
                    added = true;
                } else if (!title.equals(bar.name())) {
                    bar.name(title);
                }
            }
            rows(p, added);
        }
    }

    // ---------- Balken unter der Leiste ----------

    /** Inhalt eines Balkens: Titel, Fortschritt 0..1, Farbe; null = nichts anzuzeigen. */
    private record RowContent(Component name, float progress, BossBar.Color color) {}

    private RowContent content(Player p, Element e) {
        UUID id = p.getUniqueId();
        if (e == Element.QUEST) {
            QuestService.Track t = addon.quests() == null ? null : addon.quests().track(id);
            if (t == null) return null;
            long goal = Math.max(1, t.def().amount());
            return new RowContent(Msg.parse("<gold>" + Msg.escape(t.def().name()) + " <dark_gray>· <white>" + Msg.escape(t.def().task())
                    + " <dark_gray>· <green>" + Msg.coins(t.progress()) + "/" + Msg.coins(goal)),
                    Math.max(0f, Math.min(1f, (float) t.progress() / goal)), BossBar.Color.GREEN);
        }
        SideQuestService side = addon.sideQuests();
        QuestService.Def d = side == null ? null : side.running(id);
        if (d == null) return null;
        SideQuestService.Figure f = side.runningFigure(id);
        long goal = Math.max(1, d.amount());
        long have = side.runningProgress(id);
        return new RowContent(Msg.parse("<light_purple>" + Msg.escape(f == null ? d.name() : f.name()) + " <dark_gray>· <white>"
                + Msg.escape(d.task()) + " <dark_gray>· <green>" + Msg.coins(have) + "/" + Msg.coins(goal)),
                Math.max(0f, Math.min(1f, (float) have / goal)), BossBar.Color.PURPLE);
    }

    /** Zeigt Quest und Nebenquest unter der Leiste (in Survival, auch ohne Pack), in der eingestellten Reihenfolge. */
    private void rows(Player p, boolean hudChanged) {
        UUID id = p.getUniqueId();
        List<Element> want = new ArrayList<>();
        List<RowContent> contents = new ArrayList<>();
        if (survival(p) && core.infoBar().on(id)) {
            for (Element e : core.infoBar().order(id)) {
                if (!e.row() || !core.infoBar().visible(id, e)) continue;
                RowContent c = content(p, e);
                if (c == null) continue;
                want.add(e);
                contents.add(c);
            }
        }
        List<Row> have = rows.getOrDefault(id, List.of());
        boolean hud = bars.containsKey(id);
        List<Element> haveEls = new ArrayList<>();
        for (Row r : have) haveEls.add(r.element());
        // Reihenfolge: Leiste, Abstand, Balken. Ändert sich etwas daran, wird neu aufgebaut.
        boolean rebuild = hudChanged || !haveEls.equals(want) || (hud && !want.isEmpty()) != spacers.containsKey(id);
        if (rebuild) {
            for (Row r : have) p.hideBossBar(r.bar());
            BossBar spacer = spacers.remove(id);
            if (spacer != null) p.hideBossBar(spacer);
            List<Row> fresh = new ArrayList<>();
            if (hud && !want.isEmpty()) {
                spacer = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                spacers.put(id, spacer);
                p.showBossBar(spacer);
            }
            for (int i = 0; i < want.size(); i++) {
                RowContent c = contents.get(i);
                BossBar b = BossBar.bossBar(c.name(), c.progress(), c.color(), BossBar.Overlay.NOTCHED_10);
                fresh.add(new Row(want.get(i), b));
                p.showBossBar(b);
            }
            if (fresh.isEmpty()) rows.remove(id);
            else rows.put(id, fresh);
            return;
        }
        for (int i = 0; i < have.size(); i++) {
            BossBar b = have.get(i).bar();
            RowContent c = contents.get(i);
            if (!c.name().equals(b.name())) b.name(c.name());
            if (b.progress() != c.progress()) b.progress(c.progress());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        bars.remove(id);
        rows.remove(id);
        spacers.remove(id);
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            BossBar bar = bars.remove(id);
            if (bar != null) p.hideBossBar(bar);
            for (Row r : rows.getOrDefault(id, List.of())) p.hideBossBar(r.bar());
            rows.remove(id);
            BossBar spacer = spacers.remove(id);
            if (spacer != null) p.hideBossBar(spacer);
        }
    }

    // ---------- Inhalt ----------

    private Component build(Player p) {
        UUID id = p.getUniqueId();
        InfoBar ib = core.infoBar();
        return title(ib.design(id), ib.order(id), e -> ib.visible(id, e), core.economy().balance(id),
                core.jewels().balance(id), jobLine(p), place(p), worldTime(p));
    }

    /** Titel der Leiste aus fertigen Werten (auch für die Vorschau ohne Server). */
    public static Component title(Design d, List<Element> order, java.util.function.Predicate<Element> visible, long coins,
                                  long jewels, InfoBar.JobLine j, String place, String time) {
        TextColor value = color(d, 0x493326, 0xFCFCFC), gray = color(d, 0x717171, 0xA8A8A8);
        Strip s = new Strip(d);
        List<Part> panel = null;
        Element last = null;
        for (Element e : order) {
            if (e.row() || !visible.test(e)) continue;
            // Coins und Juwelen direkt hintereinander teilen sich eine Leiste
            boolean join = panel != null && (e == Element.JEWELS && last == Element.COINS || e == Element.COINS && last == Element.JEWELS);
            if (!join) {
                if (panel != null) s.panel(panel);
                panel = new ArrayList<>();
            } else {
                panel.add(Part.gap(8));
            }
            switch (e) {
                case COINS -> {
                    panel.add(Part.icon(I_COIN, 10, 4));
                    panel.add(Part.text(number(coins), value, d));
                }
                case JEWELS -> {
                    panel.add(Part.icon(I_GEM, 8, 5));
                    panel.add(Part.text(number(jewels), value, d));
                }
                case JOB -> {
                    TextColor name = color(d, 0x2E8D8D, 0x54FCFC);
                    panel.add(Part.icon(I_ROD, 10, 4));
                    if (j.name() == null) {
                        panel.add(Part.text("Kein Job", gray, d));
                        panel.addAll(Part.dot(d));
                        panel.add(Part.text("/job", name, d));
                    } else {
                        panel.add(Part.text(ascii(j.name()), name, d));
                        panel.add(Part.text(" Lv ", gray, d));
                        panel.add(Part.text(String.valueOf(j.level()), value, d));
                        panel.addAll(Part.dot(d));
                        panel.add(Part.text(j.percent() + "%", color(d, 0x7B7B28, 0xFCFC54), d));
                        panel.addAll(Part.dot(d));
                        panel.add(Part.text(number(j.earned()), color(d, 0x339C33, 0x54FC54), d));
                        panel.add(Part.text("/" + number(j.cap()), gray, d));
                    }
                }
                case PLACE -> {
                    panel.add(Part.icon(I_COMPASS, 8, 4));
                    panel.add(Part.text(place, value, d));
                }
                case TIME -> {
                    panel.add(Part.icon(I_CLOCK, 8, 4));
                    panel.add(Part.text(time, value, d));
                }
                default -> {
                }
            }
            last = e;
        }
        if (panel != null) s.panel(panel);
        return s.build();
    }

    private static TextColor color(Design d, int wald, int dark) {
        return TextColor.color(d.dark ? dark : wald);
    }

    /** Tageszeit in der Welt als HH:MM (Tick 0 = 06:00). */
    private static String worldTime(Player p) {
        long t = p.getWorld().getTime();
        long h = (t / 1000 + 6) % 24, m = (t % 1000) * 60 / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", h, m);
    }

    /** Ort: Spawn, eigenes oder fremdes Grundstück, Angelzone oder Wildnis. */
    private String place(Player p) {
        Location l = p.getLocation();
        Location spawn = core.modes().spawnOf(ModeService.SURVIVAL);
        int r = core.getConfig().getInt("survival-hud.spawn-radius", 80);
        if (spawn != null && spawn.getWorld() == l.getWorld() && spawn.distanceSquared(l) <= (double) r * r) {
            return "Spawn";
        }
        if (addon.farm() != null && addon.farm().isFarm(l.getWorld())) return "Farmwelt";
        ClaimService.Claim c = addon.claims().at(l);
        if (c != null) {
            if (c.owner().equals(p.getUniqueId())) return "Dein Grundstueck";
            String name = Bukkit.getOfflinePlayer(c.owner()).getName();
            return name == null ? "Grundstueck" : "Grundstueck " + name;
        }
        AngelService.Zone z = addon.angeln().zoneAt(l);
        if (z != null) return "Angel-Zone";
        return "Wildnis";
    }

    private static String number(long v) {
        return String.format(Locale.GERMANY, "%,d", v);
    }

    /** Die Leisten-Schrift kennt nur ASCII. */
    private static String ascii(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("Ä", "Ae")
                .replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss").toCharArray()) {
            b.append(ch >= 0x21 && ch <= 0x7E || ch == ' ' ? ch : '?');
        }
        return b.toString();
    }

    // ---------- Aufbau aus Zeichen ----------

    /** Ein Stück Inhalt: Platz im Layout und tatsächlicher Vorschub der Zeichen. */
    private record Part(Component c, int width, int advance) {
        /** Grafik width px breit, danach gap px Abstand. */
        static Part icon(char ch, int width, int gap) {
            return new Part(glyph(String.valueOf(ch)), width + gap, width + 1);
        }

        /** Text: im Wald dunkel ohne Schatten, sonst hell mit Schatten (wie Minecraft-Schrift). */
        static Part text(String s, TextColor color, Design d) {
            int w = Ui.width(s, false);
            Component c = Component.text(s, color).font(FONT);
            return new Part(d.dark ? c : c.shadowColor(ShadowColor.none()), w, w);
        }

        static Part gap(int px) {
            return new Part(Component.empty(), px, 0);
        }

        /** „ · “: Leerzeichen, Punkt (1 px, dunkelgrau), Leerzeichen. */
        static List<Part> dot(Design d) {
            Component c = Component.text(String.valueOf(DOT), DOT_COLOR).font(FONT);
            return List.of(gap(4), new Part(d.dark ? c : c.shadowColor(ShadowColor.none()), 2, 2), gap(4));
        }
    }

    private static Component glyph(String s) {
        return Component.text(s).font(FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
    }

    /**
     * Setzt die Leisten nebeneinander: linkes Ende (d.cap breit), Mittelstück in Binärstücken, rechtes Ende; der
     * Inhalt beginnt 1 px nach dem linken Ende und endet am rechten. Der Cursor wird mit Leerzeichen der Schrift gesetzt,
     * die Gesamtbreite stimmt, damit Minecraft die Leiste richtig mittig setzt.
     */
    private static final class Strip {
        private final TextComponent.Builder out = Component.text();
        private final Design design;
        private final int base;
        private int cursor;
        private int x;

        Strip(Design d) {
            this.design = d;
            this.base = IB_BASE + IB_STEP * d.ordinal();
        }

        void panel(List<Part> parts) {
            int content = 0;
            for (Part p : parts) content += p.width();
            int body = content + 1;
            int cap = design.cap;
            int x0 = x;
            int pos = x0 + cap;
            int rest = body;
            for (int i = 0; i < TILE_WIDTHS.length; i++) {
                while (rest >= TILE_WIDTHS[i]) {
                    put(pos, String.valueOf((char) (base + (TILE_WIDTHS.length - 1 - i))), TILE_WIDTHS[i] + 1);
                    pos += TILE_WIDTHS[i];
                    rest -= TILE_WIDTHS[i];
                }
            }
            put(x0, String.valueOf((char) (base + 8)), cap + 1);
            put(x0 + cap + body, String.valueOf((char) (base + 9)), cap + 1);
            int cx = x0 + cap + 1;
            for (Part p : parts) {
                moveTo(cx);
                out.append(p.c());
                cursor = cx + p.advance();
                cx += p.width();
            }
            x = x0 + 2 * cap + body + GAP;
        }

        private void put(int at, String ch, int advance) {
            moveTo(at);
            out.append(glyph(ch));
            cursor = at + advance;
        }

        private void moveTo(int at) {
            if (at != cursor) out.append(glyph(Ui.spaceChars(at - cursor)));
            cursor = at;
        }

        Component build() {
            moveTo(x - GAP);
            return out.build();
        }
    }
}
