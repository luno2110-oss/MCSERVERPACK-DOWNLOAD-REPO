package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/**
 * Info-Leiste (Survival und Skyblock, oben am Bildschirm) je Spieler: Design, Reihenfolge und Sichtbarkeit der Elemente.
 * Survival: Designs Wald, Kristall, Krone, Frost; gespeichert als bar.design, bar.order (ids mit Komma) und
 * bar.&lt;element&gt;. Skyblock hat eigene Designs (Insel, Wolke, Nether, End) und eigene Schlüssel bar.sky.design,
 * bar.sky.order und bar.sky.&lt;element&gt;. An/aus (infobar) gilt für beide. Gezeichnet wird die Leiste von den Addons
 * (SurvivalHud, SkyblockHud) mit {@link de.kuby.kubyplugins.ui.HudBar}; Job und Insel liefern sie über
 * {@link #jobSource(Function)} und {@link #islandSource(Function)}. Maße der Designs wie in tools/extract_infobar.py
 * und tools/build_infobar_sky.py.
 */
public final class InfoBar {

    /**
     * cap: Breite der Enden, up/down: Verzierung über und unter dem 18 px hohen Mittelstück, dark: helle Schrift,
     * sky: Design der Skyblock-Leiste.
     */
    public enum Design {
        WALD("wald", "Wald", 10, 2, 2, false, false), KRISTALL("kristall", "Kristall", 12, 2, 1, true, false),
        KRONE("krone", "Krone", 12, 2, 1, true, false), FROST("frost", "Frost", 12, 2, 4, true, false),
        INSEL("insel", "Insel", 12, 4, 5, true, true), WOLKE("wolke", "Wolke", 12, 4, 3, false, true),
        NETHER("nether", "Nether", 12, 7, 3, true, true), ENDE("ende", "End", 12, 7, 3, true, true);

        public final String id, label;
        public final int cap, up, down;
        public final boolean dark, sky;

        Design(String id, String label, int cap, int up, int down, boolean dark, boolean sky) {
            this.id = id;
            this.label = label;
            this.cap = cap;
            this.up = up;
            this.down = down;
            this.dark = dark;
            this.sky = sky;
        }

        /** Erstes Zeichen des Designs in der Schrift kubyplugins:hud (Mittelstücke, Enden bei +8 und +9). */
        public int base() {
            return sky ? 0xE2A0 + 0x20 * (ordinal() - INSEL.ordinal()) : 0xE200 + 0x20 * ordinal();
        }

        public static Design of(String id) {
            return of(id, false);
        }

        /** Design zur id aus der Gruppe (Survival oder Skyblock), sonst das erste der Gruppe. */
        public static Design of(String id, boolean sky) {
            for (Design d : values()) if (d.sky == sky && d.id.equals(id)) return d;
            return sky ? INSEL : WALD;
        }

        public static List<Design> group(boolean sky) {
            List<Design> out = new ArrayList<>();
            for (Design d : values()) if (d.sky == sky) out.add(d);
            return out;
        }
    }

    /** Elemente in der Standard-Reihenfolge (Survival: S, Skyblock: K, beide: B). */
    public enum Element {
        TIME("time", "Uhrzeit", "Tageszeit in der Welt", 'B'), COINS("coins", "Coins", "Dein Kontostand", 'B'),
        JEWELS("jewels", "Juwelen", "Deine Juwelen", 'B'), JOB("job", "Job", "Beruf und Level", 'S'),
        ISLAND("island", "Insel", "Level, Fortschritt und Platz", 'K'), XP("xp", "XP", "Deine Erfahrung", 'K'),
        GEN("gen", "Generator", "Aufgestellt, Plätze, Booster", 'K'),
        PLACE("place", "Ort", "Wo du gerade bist", 'B'), QUEST("quest", "Quest", "Verfolgte Quest", 'S'),
        SIDE("side", "Nebenquest", "Aktive Nebenquest", 'S');

        public final String id, label, desc;
        private final char mode;

        Element(String id, String label, String desc, char mode) {
            this.id = id;
            this.label = label;
            this.desc = desc;
            this.mode = mode;
        }

        public String key() {
            return "bar." + id;
        }

        /** Gehört zur Leiste in Skyblock (sky) bzw. Survival. */
        public boolean in(boolean sky) {
            return mode == 'B' || mode == (sky ? 'K' : 'S');
        }

        public static List<Element> group(boolean sky) {
            List<Element> out = new ArrayList<>();
            for (Element e : values()) if (e.in(sky)) out.add(e);
            return out;
        }

        /** Quest und Nebenquest stehen als eigene Balken unter der Leiste, alles andere in der Leiste. */
        public boolean row() {
            return this == QUEST || this == SIDE;
        }
    }

    /** Job in der Leiste: Name (null = kein Job), Level, Fortschritt in Prozent, verdient / Limit diese Stunde. */
    public record JobLine(String name, int level, int percent, long earned, long cap) {
        public static final JobLine NONE = new JobLine(null, 0, 0, 0, 0);
    }

    /**
     * Insel in der Skyblock-Leiste: Level, Fortschritt zum nächsten in Prozent, Platz (0 = ohne), Generatoren aufgestellt
     * und Plätze, Booster (leer = keiner). has = false: keine Insel.
     */
    public record IslandLine(boolean has, long level, int percent, int rank, int gens, int slots, String boost) {
        public static final IslandLine NONE = new IslandLine(false, 0, 0, 0, 0, 0, "");
    }

    private static volatile Function<Player, JobLine> jobs = p -> JobLine.NONE;
    private static volatile Function<Player, IslandLine> islands = p -> IslandLine.NONE;

    /** Das Skyblock-Addon meldet hier, woher die Insel kommt (für die Vorschau im Menü). */
    public static void islandSource(Function<Player, IslandLine> source) {
        islands = source == null ? p -> IslandLine.NONE : source;
    }

    public static IslandLine island(Player p) {
        try {
            IslandLine i = islands.apply(p);
            return i == null ? IslandLine.NONE : i;
        } catch (RuntimeException ex) {
            return IslandLine.NONE;
        }
    }

    /** Das Survival-Addon meldet hier, woher der Job kommt (für die Leiste und die Vorschau im Menü). */
    public static void jobSource(Function<Player, JobLine> source) {
        jobs = source == null ? p -> JobLine.NONE : source;
    }

    public static JobLine job(Player p) {
        try {
            JobLine j = jobs.apply(p);
            return j == null ? JobLine.NONE : j;
        } catch (RuntimeException ex) {
            return JobLine.NONE;
        }
    }

    private final KubyPlugins plugin;

    public InfoBar(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private PlayerSettings settings() {
        return plugin.playerSettings();
    }

    public boolean on(UUID id) {
        return settings().get(id, "infobar");
    }

    public void on(UUID id, boolean on) {
        settings().set(id, "infobar", on);
    }

    /** Skyblock-Leiste für diesen Spieler (sonst Survival). */
    public boolean sky(Player p) {
        return ModeService.SKYBLOCK.equals(plugin.modes().modeOf(p.getUniqueId()));
    }

    private static String prefix(boolean sky) {
        return sky ? "bar.sky." : "bar.";
    }

    public Design design(UUID id) {
        return design(id, false);
    }

    public Design design(UUID id, boolean sky) {
        return Design.of(settings().choice(id, prefix(sky) + "design"), sky);
    }

    public void design(UUID id, Design d) {
        settings().setChoice(id, prefix(d.sky) + "design", d.id);
    }

    public boolean visible(UUID id, Element e) {
        return visible(id, e, false);
    }

    /** Sichtbar; in Survival hören Coins und Juwelen zusätzlich auf den alten Schalter bar.money (Geld). */
    public boolean visible(UUID id, Element e, boolean sky) {
        boolean v = settings().get(id, prefix(sky) + e.id);
        if (!sky && (e == Element.COINS || e == Element.JEWELS)) v &= settings().get(id, "bar.money");
        return v;
    }

    public void toggle(UUID id, Element e) {
        toggle(id, e, false);
    }

    public void toggle(UUID id, Element e, boolean sky) {
        boolean now = !visible(id, e, sky);
        if (!sky && now && (e == Element.COINS || e == Element.JEWELS) && !settings().get(id, "bar.money")) {
            // alter Schalter „Geld“ war aus: beide wieder einzeln steuern
            settings().set(id, "bar.money", true);
            settings().set(id, Element.COINS.key(), false);
            settings().set(id, Element.JEWELS.key(), false);
        }
        settings().set(id, prefix(sky) + e.id, now);
    }

    public List<Element> order(UUID id) {
        return order(id, false);
    }

    /** Elemente der Leiste (nur die der Gruppe) in der eingestellten Reihenfolge. */
    public List<Element> order(UUID id, boolean sky) {
        List<Element> out = new ArrayList<>();
        for (String s : settings().text(id, prefix(sky) + "order").split(",")) {
            for (Element e : Element.values()) {
                if (e.in(sky) && e.id.equals(s.trim().toLowerCase(Locale.ROOT)) && !out.contains(e)) out.add(e);
            }
        }
        for (Element e : Element.group(sky)) if (!out.contains(e)) out.add(e);
        return out;
    }

    public void move(UUID id, Element e, int dir) {
        move(id, e, dir, false);
    }

    /** Verschiebt ein Element um dir (-1 hoch, +1 runter). */
    public void move(UUID id, Element e, int dir, boolean sky) {
        List<Element> o = order(id, sky);
        int i = o.indexOf(e), j = i + dir;
        if (i < 0 || j < 0 || j >= o.size()) return;
        o.set(i, o.get(j));
        o.set(j, e);
        StringBuilder b = new StringBuilder();
        for (Element x : o) b.append(b.length() == 0 ? "" : ",").append(x.id);
        boolean standard = o.equals(Element.group(sky));
        settings().setText(id, prefix(sky) + "order", standard ? "" : b.toString());
    }

    public void reset(UUID id) {
        reset(id, false);
    }

    /** Standard: erstes Design, Reihenfolge und Sichtbarkeit wie neu, Leiste an (nur die Leiste dieses Modus). */
    public void reset(UUID id, boolean sky) {
        if (sky) settings().reset(id, "bar.sky.");
        else settings().reset(id, "bar.", "bar.sky.");
        settings().set(id, "infobar", true);
    }
}
