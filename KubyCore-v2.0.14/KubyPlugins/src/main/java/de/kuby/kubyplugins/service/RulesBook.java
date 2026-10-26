package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Die Serverregeln aus plugins/KubyPlugins/regeln.yml (beim ersten Start aus dem Plugin kopiert): Untertitel,
 * Einleitung und die Paragraphen mit Farbe, Symbol und Punkten. Geänderte Dateien werden beim nächsten Öffnen neu gelesen.
 */
public final class RulesBook {

    /** Ein Punkt; subs sind Unterpunkte mit Strich (nur bei Aufzählungen). */
    public record Point(String text, List<String> subs) {}

    /** Ein Paragraph: Punkte in Kästen (neuer Kasten bei "---"). */
    public record Paragraph(String title, String color, int icon, boolean bullets, List<List<Point>> boxes) {}

    public record Book(String subtitle, String introTitle, String introText, String hint, List<Paragraph> paragraphs) {}

    private final KubyPlugins plugin;
    private final File file;
    private long loaded = -1;
    private Book book = new Book("", "", "", "", List.of());

    public RulesBook(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "regeln.yml");
    }

    public synchronized Book book() {
        if (!file.exists()) plugin.saveResource("regeln.yml", false);
        if (file.lastModified() != loaded) {
            loaded = file.lastModified();
            book = read(YamlConfiguration.loadConfiguration(file));
        }
        return book;
    }

    private Book read(YamlConfiguration y) {
        List<Paragraph> out = new ArrayList<>();
        int n = 0;
        for (Map<?, ?> m : y.getMapList("paragraphen")) {
            n++;
            boolean bullets = Boolean.TRUE.equals(m.get("aufzaehlung"));
            List<List<Point>> boxes = new ArrayList<>();
            List<Point> box = new ArrayList<>();
            Object pts = m.get("punkte");
            if (pts instanceof List<?> list) {
                for (Object o : list) {
                    String t = String.valueOf(o).trim();
                    if (t.equals("---")) {
                        if (!box.isEmpty()) boxes.add(box);
                        box = new ArrayList<>();
                    } else if (bullets && (t.startsWith("– ") || t.startsWith("- ")) && !box.isEmpty()) {
                        box.get(box.size() - 1).subs().add(t.substring(2).trim());
                    } else {
                        box.add(new Point(t, new ArrayList<>()));
                    }
                }
            }
            if (!box.isEmpty()) boxes.add(box);
            int icon = m.get("symbol") instanceof Number num ? num.intValue() : n;
            out.add(new Paragraph(String.valueOf(m.get("titel") == null ? "Paragraph " + n : m.get("titel")),
                    String.valueOf(m.get("farbe") == null ? "gruen" : m.get("farbe")), icon, bullets, boxes));
        }
        if (out.isEmpty()) plugin.getLogger().warning("regeln.yml: keine Paragraphen gefunden");
        return new Book(y.getString("untertitel", ""), y.getString("einleitung.titel", "So spielen wir"),
                y.getString("einleitung.text", ""), y.getString("einleitung.hinweis", ""), out);
    }
}
