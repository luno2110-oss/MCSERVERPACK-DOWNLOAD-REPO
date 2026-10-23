package de.kuby.kubysurvival.hud;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Ui;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.claim.ClaimService;
import de.kuby.kubysurvival.fishing.FishingService;
import de.kuby.kubysurvival.jobs.JobService;
import de.kuby.kubysurvival.quests.QuestService;
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
 * Survival-Leiste oben (Platz der Bossbar): drei Holzleisten mit Geld und Juwelen, aktivem Job und Ort.
 * Darunter die verfolgte Quest als grüne Bossbar.
 * Die Leisten sind Zeichen der Schrift kubyplugins:hud aus dem Pack KubyUI, der Bossbar-Balken selbst ist
 * durch das Pack unsichtbar (Farbe weiß). Solange die Leiste sichtbar ist, entfällt die Anzeige über der Hotbar.
 * Muss zu tools/build_sb_pack.py (build_hud) passen.
 */
public final class SurvivalHud implements Listener {

    private static final Key FONT = Key.key("kubyplugins", "hud");
    private static final char CAP_LEFT = '';
    private static final char CAP_RIGHT = '';
    private static final char DOT = '';
    private static final int TILE_BASE = 0xE040;              // Breiten 1, 2, 4, ..., 64
    private static final int[] TILE_WIDTHS = {64, 32, 16, 8, 4, 2, 1};
    private static final char I_COIN = '';
    private static final char I_GEM = '';
    private static final char I_ROD = '';
    private static final char I_COMPASS = '';
    private static final char I_CLOCK = '';
    private static final int ICON_ADV = 9;
    private static final int GAP = 2;                          // Abstand zwischen den Leisten

    private static final TextColor DARK = TextColor.color(0x3C2D1E);
    private static final TextColor TEAL = TextColor.color(0x2E8C8C);
    private static final TextColor GRAY = TextColor.color(0x55504A);
    private static final TextColor OLIVE = TextColor.color(0x8A8424);
    private static final TextColor GREEN = TextColor.color(0x3A9440);

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    /** Verfolgte Quest: eigene Bossbar mit grünem Balken. Unter der Leiste liegt eine leere Bossbar als Abstand. */
    private final Map<UUID, BossBar> questBars = new HashMap<>();
    private final Map<UUID, BossBar> spacers = new HashMap<>();

    public SurvivalHud(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        core.setHotbarHidden(p -> bars.containsKey(p.getUniqueId()));
        addon.getServer().getScheduler().runTaskTimer(addon, this::tick, 20L, 10L);
    }

    /** Leiste nur in Survival und nur mit geladenem Pack (sonst bleibt die Anzeige über der Hotbar). */
    private boolean wants(Player p) {
        return core.getConfig().getBoolean("survival-hud.enabled", true)
                && core.playerSettings().get(p.getUniqueId(), "infobar")
                && (core.playerSettings().get(p.getUniqueId(), "bar.money") || core.playerSettings().get(p.getUniqueId(), "bar.job")
                    || core.playerSettings().get(p.getUniqueId(), "bar.place") || core.playerSettings().get(p.getUniqueId(), "bar.time"))
                && ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))
                && core.uiPack().active(p);
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
            questBar(p, added);
        }
    }

    /** Zeigt die verfolgte Quest unter der Leiste (in Survival, auch ohne Pack). */
    private void questBar(Player p, boolean hudAdded) {
        UUID id = p.getUniqueId();
        QuestService.Track t = ModeService.SURVIVAL.equals(core.modes().modeOf(id)) && addon.quests() != null
                && core.playerSettings().get(id, "infobar") && core.playerSettings().get(id, "bar.quest")
                ? addon.quests().track(id) : null;
        BossBar quest = questBars.get(id);
        BossBar spacer = spacers.get(id);
        if (t == null) {
            if (quest != null) p.hideBossBar(questBars.remove(id));
            if (spacer != null) p.hideBossBar(spacers.remove(id));
            return;
        }
        boolean hud = bars.containsKey(id);
        // Reihenfolge: Leiste, Abstand, Quest. Kommt etwas Neues dazu, wird neu aufgebaut.
        boolean rebuild = hudAdded || quest == null || (hud != (spacer != null));
        if (rebuild) {
            if (quest != null) p.hideBossBar(quest);
            if (spacer != null) p.hideBossBar(spacer);
            spacers.remove(id);
            if (hud) {
                spacer = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                spacers.put(id, spacer);
                p.showBossBar(spacer);
            }
            if (quest == null) {
                quest = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
                questBars.put(id, quest);
            }
            p.showBossBar(quest);
        }
        long goal = Math.max(1, t.def().amount());
        float progress = Math.max(0f, Math.min(1f, (float) t.progress() / goal));
        Component name = Msg.parse("<gold>" + Msg.escape(t.def().name()) + " <dark_gray>· <white>" + Msg.escape(t.def().task())
                + " <dark_gray>· <green>" + Msg.coins(t.progress()) + "/" + Msg.coins(goal));
        if (!name.equals(quest.name())) quest.name(name);
        if (quest.progress() != progress) quest.progress(progress);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        bars.remove(e.getPlayer().getUniqueId());
        questBars.remove(e.getPlayer().getUniqueId());
        spacers.remove(e.getPlayer().getUniqueId());
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            BossBar bar = bars.remove(p.getUniqueId());
            if (bar != null) p.hideBossBar(bar);
            BossBar quest = questBars.remove(p.getUniqueId());
            if (quest != null) p.hideBossBar(quest);
            BossBar spacer = spacers.remove(p.getUniqueId());
            if (spacer != null) p.hideBossBar(spacer);
        }
    }

    // ---------- Inhalt ----------

    private Component build(Player p) {
        UUID id = p.getUniqueId();
        Strip s = new Strip();
        List<Part> money = new ArrayList<>();
        money.add(Part.icon(I_COIN));
        money.add(Part.text(number(core.economy().balance(id)), DARK));
        money.add(Part.gap(6));
        money.add(Part.icon(I_GEM));
        money.add(Part.text(number(core.jewels().balance(id)), DARK));
        if (core.playerSettings().get(id, "bar.money")) s.panel(money);

        // Aktiver Job: Level, Fortschritt, Job-Coins diese Stunde / Stundenlimit
        JobService jobs = addon.jobs();
        JobService.Worker w = jobs.worker(id);
        List<Part> job = new ArrayList<>();
        job.add(Part.icon(I_ROD));
        if (w.job == null) {
            job.add(Part.text("Kein Job", GRAY));
            job.add(Part.dot());
            job.add(Part.text("/job", TEAL));
        } else {
            job.add(Part.text(w.job.ascii, TEAL));
            job.add(Part.text(" Lv " + jobs.jobLevel(w, w.job), GRAY));
            job.add(Part.dot());
            job.add(Part.text(jobs.progress(w, w.job) + "%", OLIVE));
            job.add(Part.dot());
            job.add(Part.text(number(w.earnedHour) + "/" + number(jobs.hourlyCap(w)), GREEN));
        }
        if (core.playerSettings().get(id, "bar.job")) s.panel(job);

        List<Part> place = new ArrayList<>();
        place.add(Part.icon(I_COMPASS));
        place.add(Part.text(place(p), DARK));
        if (core.playerSettings().get(id, "bar.place")) s.panel(place);
        if (core.playerSettings().get(id, "bar.time")) {
            List<Part> time = new ArrayList<>();
            time.add(Part.icon(I_CLOCK));
            time.add(Part.text(java.time.LocalTime.now(java.time.ZoneId.of("Europe/Berlin"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), DARK));
            s.panel(time);
        }
        return s.build();
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
        for (FishingService.Zone z : addon.fishing().zones()) {
            if (z.world().equals(l.getWorld().getName())
                    && l.getBlockX() >= Math.min(z.x1(), z.x2()) && l.getBlockX() <= Math.max(z.x1(), z.x2())
                    && l.getBlockZ() >= Math.min(z.z1(), z.z2()) && l.getBlockZ() <= Math.max(z.z1(), z.z2())) {
                return ascii(z.name() == null ? z.id() : z.name());
            }
        }
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
        static Part icon(char ch) {
            return new Part(glyph(String.valueOf(ch)), ICON_ADV + 2, ICON_ADV);
        }

        static Part text(String s, TextColor color) {
            int w = Ui.width(s, false);
            return new Part(Component.text(s, color).font(FONT).shadowColor(ShadowColor.none()), w, w);
        }

        static Part gap(int px) {
            return new Part(Component.empty(), px, 0);
        }

        static Part dot() {
            return new Part(Component.empty().append(glyph(Ui.spaceChars(3))).append(glyph(String.valueOf(DOT)))
                    .append(glyph(Ui.spaceChars(3))), 9, 9);
        }
    }

    private static Component glyph(String s) {
        return Component.text(s).font(FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
    }

    /**
     * Setzt die Leisten nebeneinander. Jede Leiste: Pfosten links (Textur 12 px, Pfosten bei 5-8), Holzleiste mit
     * Cremefeld ab x = 7, Inhalt ab x = 12, Pfosten rechts. Der Cursor wird mit Leerzeichen der Schrift gesetzt,
     * die Gesamtbreite stimmt, damit Minecraft die Leiste richtig mittig setzt.
     */
    private static final class Strip {
        private final TextComponent.Builder out = Component.text();
        private int cursor;
        private int x;

        void panel(List<Part> parts) {
            int content = 0;
            for (Part p : parts) content += p.width();
            int body = content + 10;
            int x0 = x;
            // Holzleiste in Binärstücken
            int pos = x0 + 7;
            int rest = body;
            for (int i = 0; i < TILE_WIDTHS.length; i++) {
                while (rest >= TILE_WIDTHS[i]) {
                    put(pos, String.valueOf((char) (TILE_BASE + (TILE_WIDTHS.length - 1 - i))), TILE_WIDTHS[i] + 1);
                    pos += TILE_WIDTHS[i];
                    rest -= TILE_WIDTHS[i];
                }
            }
            put(x0, String.valueOf(CAP_LEFT), 13);
            put(x0 + body + 2, String.valueOf(CAP_RIGHT), 13);
            int cx = x0 + 12;
            for (Part p : parts) {
                moveTo(cx);
                out.append(p.c());
                cursor = cx + p.advance();
                cx += p.width();
            }
            x = x0 + body + 14 + GAP;
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
