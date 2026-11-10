package de.kuby.kubyskyblock.hud;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.InfoBar;
import de.kuby.kubyplugins.service.InfoBar.Design;
import de.kuby.kubyplugins.service.InfoBar.Element;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.HudBar;
import de.kuby.kubyplugins.ui.HudBar.Part;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.IslandService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
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
import java.util.function.Predicate;

/**
 * Insel-Leiste oben in Skyblock (Platz der Bossbar), wie die Survival-Leiste, aber mit eigenen Rahmen (Insel, Wolke,
 * Nether, End) und eigenen Elementen: Geld (Coins und Juwelen in einer Leiste), Insel (Level, Fortschritt zum nächsten
 * Level, Platz), XP, Generatoren (aufgestellt / Plätze, Booster), Ort und Uhrzeit. Reihenfolge, Sichtbarkeit und Design
 * stellt der Spieler in /bossbar ein (Schlüssel bar.sky.*). Solange die Leiste sichtbar ist, entfällt die Geld-Anzeige
 * über der Hotbar. Gezeichnet mit {@link HudBar} (Zeichen aus tools/build_infobar_sky.py, build_sb_pack.py).
 */
public final class SkyblockHud implements Listener {

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public SkyblockHud(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        core.setHotbarHidden(p -> bars.containsKey(p.getUniqueId()));
        InfoBar.islandSource(this::islandLine);
        addon.getServer().getScheduler().runTaskTimer(addon, this::tick, 20L, 10L);
    }

    private boolean skyblock(Player p) {
        return ModeService.SKYBLOCK.equals(core.modes().modeOf(p.getUniqueId()));
    }

    /** Leiste nur in Skyblock und nur mit geladenem Pack. */
    private boolean wants(Player p) {
        UUID id = p.getUniqueId();
        if (de.kuby.kubyplugins.cinema.Cinema.busy(id)) return false;
        if (!addon.getConfig().getBoolean("leiste.aktiv", true) || !core.infoBar().on(id) || !skyblock(p)
                || !core.uiPack().active(p)) return false;
        for (Element e : Element.group(true)) if (core.infoBar().visible(id, e, true)) return true;
        return false;
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            BossBar bar = bars.get(id);
            if (!wants(p)) {
                if (bar != null) {
                    p.hideBossBar(bar);
                    bars.remove(id);
                }
                continue;
            }
            Component title;
            try {
                title = build(p);
            } catch (RuntimeException ex) {
                continue;
            }
            if (bar == null) {
                bar = BossBar.bossBar(title, 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                bars.put(id, bar);
                p.showBossBar(bar);
            } else if (!title.equals(bar.name())) {
                bar.name(title);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        bars.remove(e.getPlayer().getUniqueId());
    }

    public void shutdown() {
        InfoBar.islandSource(null);
        for (Player p : Bukkit.getOnlinePlayers()) {
            BossBar bar = bars.remove(p.getUniqueId());
            if (bar != null) p.hideBossBar(bar);
        }
    }

    // ---------- Werte ----------

    private Component build(Player p) {
        UUID id = p.getUniqueId();
        InfoBar ib = core.infoBar();
        return title(ib.design(id, true), ib.order(id, true), e -> ib.visible(id, e, true), core.economy().balance(id),
                core.jewels().balance(id), islandLine(p), core.stats().get(id).xp, place(p), worldTime(p));
    }

    /** Insel des Spielers für die Leiste und die Vorschau in /bossbar. */
    public InfoBar.IslandLine islandLine(Player p) {
        IslandService is = addon.islands();
        IslandService.Island i = is == null ? null : is.islandOf(p.getUniqueId());
        if (i == null) return InfoBar.IslandLine.NONE;
        long per = is.perLevel();
        int percent = (int) Math.min(99, Math.max(0, i.points) % per * 100 / per);
        var gens = addon.generators();
        int count = gens == null ? 0 : gens.count(i.slot);
        int slots = gens == null ? 0 : gens.slots(i);
        String boost = "";
        double[] b = gens == null ? null : gens.boost(i.slot);
        if (b != null) {
            long sec = Math.max(0, ((long) b[1] - System.currentTimeMillis()) / 1000);
            String left = sec >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
                    : String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
            boost = "x" + de.kuby.kubyskyblock.service.GeneratorService.fmt(b[0]) + " " + left;
        }
        return new InfoBar.IslandLine(true, is.levelOf(i), percent, is.rank(i), count, slots, boost);
    }

    /** Ort: eigene oder fremde Insel, Spawn, Stufe (Nether, End), Biom-Insel, sonst Himmel. */
    private String place(Player p) {
        Location l = p.getLocation();
        if (addon.stages() != null) {
            var st = addon.stages().stageOf(l.getWorld());
            if (st != null) return st.label + "-Stufe";
        }
        Location spawn = core.modes().spawnOf(ModeService.SKYBLOCK);
        int r = addon.getConfig().getInt("leiste.spawn-radius", 48);
        if (spawn != null && spawn.getWorld() == l.getWorld() && spawn.distanceSquared(l) <= (double) r * r) return "Spawn";
        IslandService is = addon.islands();
        IslandService.Island i = is == null ? null : is.islandAt(l);
        if (i != null) {
            if (i.isMember(p.getUniqueId())) return "Deine Insel";
            String owner = i.owner == null ? null : Bukkit.getOfflinePlayer(i.owner).getName();
            return owner == null ? "Fremde Insel" : "Insel " + owner;
        }
        if (addon.biomes() != null && addon.biomes().at(l) != null) return "Biom-Insel";
        return "Himmel";
    }

    private static String worldTime(Player p) {
        long t = p.getWorld().getTime();
        long h = (t / 1000 + 6) % 24, m = (t % 1000) * 60 / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", h, m);
    }

    // ---------- Aufbau ----------

    /** Titel der Leiste aus fertigen Werten (auch für die Vorschau ohne Server). */
    public static Component title(Design d, List<Element> order, Predicate<Element> visible, long coins, long jewels,
                                  InfoBar.IslandLine isl, long xp, String place, String time) {
        TextColor value = HudBar.color(d, 0x2D3846, 0xFCFCFC), gray = HudBar.color(d, 0x5E6B80, 0xA8A8A8);
        HudBar.Strip s = new HudBar.Strip(d);
        List<Part> panel = null;
        Element last = null;
        for (Element e : order) {
            if (!e.in(true) || !visible.test(e)) continue;
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
                    panel.add(Part.icon(HudBar.I_COIN, 10, 4));
                    panel.add(Part.text(HudBar.number(coins), value, d));
                }
                case JEWELS -> {
                    panel.add(Part.icon(HudBar.I_GEM, 8, 5));
                    panel.add(Part.text(HudBar.number(jewels), value, d));
                }
                case ISLAND -> {
                    panel.add(Part.icon(HudBar.I_ISLAND, 8, 4));
                    if (!isl.has()) {
                        panel.add(Part.text("Keine Insel", gray, d));
                        panel.addAll(Part.dot(d));
                        panel.add(Part.text("/insel", HudBar.color(d, 0x2E7D32, 0x7CE36C), d));
                    } else {
                        panel.add(Part.text("Lv ", gray, d));
                        panel.add(Part.text(HudBar.number(isl.level()), value, d));
                        panel.addAll(Part.dot(d));
                        panel.add(Part.text(isl.percent() + "%", HudBar.color(d, 0x8A6D00, 0xFCFC54), d));
                        if (isl.rank() > 0) {
                            panel.addAll(Part.dot(d));
                            panel.add(Part.text("#" + isl.rank(), HudBar.color(d, 0xA0522D, 0xFFB45A), d));
                        }
                    }
                }
                case XP -> {
                    panel.add(Part.icon(HudBar.I_XP, 8, 4));
                    panel.add(Part.text(HudBar.number(xp), HudBar.color(d, 0x3D7A12, 0x9CF05A), d));
                }
                case GEN -> {
                    panel.add(Part.icon(HudBar.I_GEN, 8, 4));
                    if (!isl.has()) {
                        panel.add(Part.text("-", gray, d));
                    } else {
                        boolean full = isl.slots() > 0 && isl.gens() >= isl.slots();
                        panel.add(Part.text(String.valueOf(isl.gens()), full ? HudBar.color(d, 0xB03A2E, 0xFF7A6A) : value, d));
                        panel.add(Part.text("/" + isl.slots(), gray, d));
                        if (!isl.boost().isEmpty()) {
                            panel.addAll(Part.dot(d));
                            panel.add(Part.text(HudBar.ascii(isl.boost()), HudBar.color(d, 0x7B3FB0, 0xD49CFF), d));
                        }
                    }
                }
                case PLACE -> {
                    panel.add(Part.icon(HudBar.I_COMPASS, 8, 4));
                    panel.add(Part.text(HudBar.ascii(place), value, d));
                }
                case TIME -> {
                    panel.add(Part.icon(HudBar.I_CLOCK, 8, 4));
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
}
