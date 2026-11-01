package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/**
 * Info-Leiste (Survival, oben am Bildschirm) je Spieler: Design (Wald, Kristall, Krone, Frost), Reihenfolge und
 * Sichtbarkeit der Elemente. Gespeichert in den Spieler-Einstellungen: infobar (an/aus), bar.design, bar.order (ids mit
 * Komma) und bar.&lt;element&gt;. Gezeichnet wird die Leiste vom Survival-Addon (SurvivalHud), den Job liefert es über
 * {@link #jobSource(Function)}. Maße der Designs wie in tools/extract_infobar.py.
 */
public final class InfoBar {

    /** cap: Breite der Enden, up/down: Verzierung über und unter dem 18 px hohen Mittelstück, dark: helle Schrift. */
    public enum Design {
        WALD("wald", "Wald", 10, 2, 2, false), KRISTALL("kristall", "Kristall", 12, 2, 1, true),
        KRONE("krone", "Krone", 12, 2, 1, true), FROST("frost", "Frost", 12, 2, 4, true);

        public final String id, label;
        public final int cap, up, down;
        public final boolean dark;

        Design(String id, String label, int cap, int up, int down, boolean dark) {
            this.id = id;
            this.label = label;
            this.cap = cap;
            this.up = up;
            this.down = down;
            this.dark = dark;
        }

        public static Design of(String id) {
            for (Design d : values()) if (d.id.equals(id)) return d;
            return WALD;
        }
    }

    /** Elemente in der Standard-Reihenfolge. */
    public enum Element {
        TIME("time", "Uhrzeit", "Tageszeit in der Welt"), COINS("coins", "Coins", "Dein Kontostand"),
        JEWELS("jewels", "Juwelen", "Deine Juwelen"), JOB("job", "Job", "Beruf und Level"),
        PLACE("place", "Ort", "Wo du gerade bist"), QUEST("quest", "Quest", "Verfolgte Quest"),
        SIDE("side", "Nebenquest", "Aktive Nebenquest");

        public final String id, label, desc;

        Element(String id, String label, String desc) {
            this.id = id;
            this.label = label;
            this.desc = desc;
        }

        public String key() {
            return "bar." + id;
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

    private static volatile Function<Player, JobLine> jobs = p -> JobLine.NONE;

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

    public Design design(UUID id) {
        return Design.of(settings().choice(id, "bar.design"));
    }

    public void design(UUID id, Design d) {
        settings().setChoice(id, "bar.design", d.id);
    }

    /** Sichtbar; Coins und Juwelen hören zusätzlich auf den alten Schalter bar.money (Geld). */
    public boolean visible(UUID id, Element e) {
        boolean v = settings().get(id, e.key());
        if (e == Element.COINS || e == Element.JEWELS) v &= settings().get(id, "bar.money");
        return v;
    }

    public void toggle(UUID id, Element e) {
        boolean now = !visible(id, e);
        if (now && (e == Element.COINS || e == Element.JEWELS) && !settings().get(id, "bar.money")) {
            // alter Schalter „Geld“ war aus: beide wieder einzeln steuern
            settings().set(id, "bar.money", true);
            settings().set(id, Element.COINS.key(), false);
            settings().set(id, Element.JEWELS.key(), false);
        }
        settings().set(id, e.key(), now);
    }

    public List<Element> order(UUID id) {
        List<Element> out = new ArrayList<>();
        for (String s : settings().text(id, "bar.order").split(",")) {
            for (Element e : Element.values()) {
                if (e.id.equals(s.trim().toLowerCase(Locale.ROOT)) && !out.contains(e)) out.add(e);
            }
        }
        for (Element e : Element.values()) if (!out.contains(e)) out.add(e);
        return out;
    }

    /** Verschiebt ein Element um dir (-1 hoch, +1 runter). */
    public void move(UUID id, Element e, int dir) {
        List<Element> o = order(id);
        int i = o.indexOf(e), j = i + dir;
        if (i < 0 || j < 0 || j >= o.size()) return;
        o.set(i, o.get(j));
        o.set(j, e);
        StringBuilder b = new StringBuilder();
        for (Element x : o) b.append(b.length() == 0 ? "" : ",").append(x.id);
        boolean standard = o.equals(List.of(Element.values()));
        settings().setText(id, "bar.order", standard ? "" : b.toString());
    }

    /** Standard: Design Wald, Reihenfolge und Sichtbarkeit wie neu, Leiste an. */
    public void reset(UUID id) {
        settings().reset(id, "bar.");
        settings().set(id, "infobar", true);
    }
}
