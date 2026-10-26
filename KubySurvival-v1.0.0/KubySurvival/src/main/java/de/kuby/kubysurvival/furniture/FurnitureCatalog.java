package de.kuby.kubysurvival.furniture;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Möbel. Ursprung = Mitte unten im Block, vorne = +Z (zum Spieler gedreht, der es aufstellt).
 *
 * <p>Zwei Arten: die alten aus Block-Anzeigen (hier im Code, Preis nach Größe; breite Stücke reichen nach +X) und die
 * Modell-Möbel aus dem Juwelenshop (moebel.yml, generiert von tools/build_furniture.py): ein ItemDisplay mit dem Modell
 * kuby:furniture/&lt;id&gt; aus dem Pack, Felder, Sitze und Licht aus dem Katalog, in Reitern wie im Shop.
 */
public final class FurnitureCatalog {

    public enum Size {
        KLEIN("klein", 25), MITTEL("mittel", 50), GROSS("groß", 100);

        public final String label;
        public final long price;

        Size(String label, long price) {
            this.label = label;
            this.price = price;
        }
    }

    /** Ein Teil: Material, Ecke (x, y, z) und Größe; glow = leuchtet (Lampenschirm). */
    public record Part(Material m, float x, float y, float z, float sx, float sy, float sz, boolean glow) {}

    /**
     * Ein Möbelstück. cells = belegte Blöcke (x, z) ab dem Ursprungsblock, seats = Sitzpunkte (x, z) mit Sitzhöhe
     * seatY, light = Höhe (in Blöcken über dem Ursprung) eines Lichtblocks oder -1, lightLevel = seine Stärke.
     * Modell-Möbel: model = Item-Modell im Pack, tab = Reiter im Shop, height = Höhe der Klickfläche; parts leer.
     */
    public record Def(String id, String name, Size size, Material icon, List<int[]> cells, float seatY, List<float[]> seats,
                      int light, int lightLevel, List<Part> parts, String tab, String model, float height, long price) {
        public boolean seat() {
            return !seats.isEmpty();
        }

        public boolean model3d() {
            return model != null;
        }

        /** Mehr als ein Block: nur in 90-Grad-Schritten drehbar. */
        public boolean large() {
            return cells.size() > 1;
        }
    }

    /** Reiter im Shop. */
    public record Tab(String id, String name, String sub) {}

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();
    private static final List<Tab> TABS = new ArrayList<>();

    private FurnitureCatalog() {
    }

    public static List<Def> all() {
        return new ArrayList<>(DEFS.values());
    }

    /** Die alten Block-Möbel (gehen ohne Pack; Truhen-Shop ohne KubyUI). */
    public static List<Def> blocks() {
        List<Def> out = new ArrayList<>();
        for (Def d : DEFS.values()) if (!d.model3d()) out.add(d);
        return out;
    }

    public static Def get(String id) {
        return id == null ? null : DEFS.get(id);
    }

    public static synchronized List<Tab> tabs() {
        return new ArrayList<>(TABS);
    }

    /** Modell-Möbel eines Reiters in der Reihenfolge des Katalogs. */
    public static List<Def> inTab(String tab) {
        List<Def> out = new ArrayList<>();
        for (Def d : DEFS.values()) if (tab.equals(d.tab())) out.add(d);
        return out;
    }

    /** Lädt die Modell-Möbel aus moebel.yml (im Plugin-Jar). */
    public static synchronized int load(Reader in) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(in);
        TABS.clear();
        DEFS.values().removeIf(Def::model3d);
        for (String raw : y.getStringList("reiter")) {
            String[] p = raw.split(";", 3);
            if (p.length == 3) TABS.add(new Tab(p[0].trim(), p[1].trim(), p[2].trim()));
        }
        ConfigurationSection s = y.getConfigurationSection("moebel");
        if (s == null) return 0;
        int n = 0;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            List<int[]> cells = new ArrayList<>();
            for (Object o : c.getList("felder", List.of())) {
                if (o instanceof List<?> l && l.size() == 2) {
                    cells.add(new int[]{((Number) l.get(0)).intValue(), ((Number) l.get(1)).intValue()});
                }
            }
            if (cells.isEmpty()) cells.add(new int[]{0, 0});
            List<float[]> seats = new ArrayList<>();
            for (Object o : c.getList("sitze", List.of())) {
                if (o instanceof List<?> l && l.size() == 2) {
                    seats.add(new float[]{((Number) l.get(0)).floatValue(), ((Number) l.get(1)).floatValue()});
                }
            }
            float height = (float) c.getDouble("hoehe", 1);
            int level = c.getInt("licht", -1);
            DEFS.put(id, new Def(id, c.getString("name", id), Size.MITTEL, Material.PAPER, cells, (float) c.getDouble("sitzhoehe"),
                    seats, level > 0 ? (height > 1.2f ? 1 : 0) : -1, Math.max(0, level), List.of(), c.getString("reiter", ""),
                    "kuby:furniture/" + id, height, c.getLong("preis", 100)));
            n++;
        }
        return n;
    }

    // ---------- Bauhilfen ----------

    private static final class B {
        final List<Part> parts = new ArrayList<>();

        B p(Material m, float x, float y, float z, float sx, float sy, float sz) {
            parts.add(new Part(m, x, y, z, sx, sy, sz, false));
            return this;
        }

        B glow(Material m, float x, float y, float z, float sx, float sy, float sz) {
            parts.add(new Part(m, x, y, z, sx, sy, sz, true));
            return this;
        }

        /** Vier Beine (Dicke t, Höhe h) an den Ecken eines Rechtecks von (x0, z0) bis (x1, z1). */
        B legs(Material m, float x0, float z0, float x1, float z1, float t, float h) {
            p(m, x0, 0, z0, t, h, t);
            p(m, x1 - t, 0, z0, t, h, t);
            p(m, x0, 0, z1 - t, t, h, t);
            p(m, x1 - t, 0, z1 - t, t, h, t);
            return this;
        }
    }

    private static void add(String id, String name, Size size, Material icon, int width, float seatY, List<float[]> seats,
                            int light, B b) {
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < width; i++) cells.add(new int[]{i, 0});
        float height = 0.5f;
        for (Part p : b.parts) height = Math.max(height, p.y() + p.sy());
        DEFS.put(id, new Def(id, name, size, icon, cells, seatY, seats, light, 13, List.copyOf(b.parts), null, null,
                Math.min(height, 1.9f), size.price));
    }

    private static List<float[]> seat(float... xz) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i + 1 < xz.length; i += 2) out.add(new float[]{xz[i], xz[i + 1]});
        return out;
    }

    private static void chair(String id, String name, Material wood, Material log, Material cushion) {
        B b = new B().legs(log, -0.24f, -0.24f, 0.24f, 0.24f, 0.07f, 0.45f)
                .p(wood, -0.26f, 0.45f, -0.26f, 0.52f, 0.07f, 0.52f)
                .p(log, -0.26f, 0.52f, -0.26f, 0.07f, 0.56f, 0.07f)
                .p(log, 0.19f, 0.52f, -0.26f, 0.07f, 0.56f, 0.07f)
                .p(wood, -0.19f, 0.70f, -0.25f, 0.38f, 0.07f, 0.05f)
                .p(wood, -0.19f, 0.92f, -0.25f, 0.38f, 0.12f, 0.05f);
        float seatY = 0.52f;
        if (cushion != null) {
            b.p(cushion, -0.22f, 0.52f, -0.20f, 0.44f, 0.045f, 0.42f).p(cushion, -0.17f, 0.62f, -0.205f, 0.34f, 0.26f, 0.04f);
            seatY = 0.565f;
        }
        add(id, name, Size.MITTEL, wood == Material.DARK_OAK_PLANKS ? Material.DARK_OAK_STAIRS : Material.OAK_STAIRS, 1, seatY,
                seat(0, 0.03f), -1, b);
    }

    static {
        // ---------- Sitzmöbel ----------
        add("hocker", "Hocker", Size.KLEIN, Material.OAK_FENCE, 1, 0.52f, seat(0, 0), -1, new B()
                .legs(Material.STRIPPED_OAK_LOG, -0.22f, -0.22f, 0.22f, 0.22f, 0.07f, 0.42f)
                .p(Material.OAK_PLANKS, -0.25f, 0.42f, -0.25f, 0.5f, 0.06f, 0.5f)
                .p(Material.RED_WOOL, -0.21f, 0.48f, -0.21f, 0.42f, 0.04f, 0.42f)
                .p(Material.STRIPPED_OAK_LOG, -0.2f, 0.16f, -0.02f, 0.4f, 0.04f, 0.04f));
        chair("stuhl_eiche", "Eichenstuhl", Material.OAK_PLANKS, Material.STRIPPED_OAK_LOG, null);
        chair("stuhl_dunkel", "Polsterstuhl", Material.DARK_OAK_PLANKS, Material.STRIPPED_DARK_OAK_LOG, Material.RED_WOOL);
        add("sessel", "Sessel", Size.MITTEL, Material.LIGHT_GRAY_WOOL, 1, 0.48f, seat(0, 0.05f), -1, new B()
                .legs(Material.DARK_OAK_PLANKS, -0.42f, -0.40f, 0.42f, 0.40f, 0.07f, 0.08f)
                .p(Material.LIGHT_GRAY_WOOL, -0.45f, 0.08f, -0.42f, 0.90f, 0.32f, 0.84f)
                .p(Material.WHITE_WOOL, -0.31f, 0.40f, -0.22f, 0.62f, 0.08f, 0.62f)
                .p(Material.LIGHT_GRAY_WOOL, -0.45f, 0.40f, -0.45f, 0.90f, 0.52f, 0.22f)
                .p(Material.LIGHT_GRAY_WOOL, -0.45f, 0.40f, -0.24f, 0.14f, 0.22f, 0.66f)
                .p(Material.LIGHT_GRAY_WOOL, 0.31f, 0.40f, -0.24f, 0.14f, 0.22f, 0.66f));
        add("sofa", "Sofa", Size.GROSS, Material.GRAY_WOOL, 2, 0.48f, seat(0.05f, 0.05f, 0.95f, 0.05f), -1, new B()
                .legs(Material.DARK_OAK_PLANKS, -0.42f, -0.40f, 1.42f, 0.40f, 0.07f, 0.08f)
                .p(Material.GRAY_WOOL, -0.45f, 0.08f, -0.42f, 1.90f, 0.32f, 0.84f)
                .p(Material.LIGHT_GRAY_WOOL, -0.31f, 0.40f, -0.22f, 0.80f, 0.08f, 0.62f)
                .p(Material.LIGHT_GRAY_WOOL, 0.51f, 0.40f, -0.22f, 0.80f, 0.08f, 0.62f)
                .p(Material.GRAY_WOOL, -0.45f, 0.40f, -0.45f, 1.90f, 0.52f, 0.22f)
                .p(Material.GRAY_WOOL, -0.45f, 0.40f, -0.24f, 0.14f, 0.22f, 0.66f)
                .p(Material.GRAY_WOOL, 1.31f, 0.40f, -0.24f, 0.14f, 0.22f, 0.66f));
        add("bank", "Holzbank", Size.GROSS, Material.SPRUCE_STAIRS, 2, 0.48f, seat(0.05f, 0.02f, 0.95f, 0.02f), -1, new B()
                .p(Material.STRIPPED_SPRUCE_LOG, -0.40f, 0, -0.22f, 0.08f, 0.42f, 0.08f)
                .p(Material.STRIPPED_SPRUCE_LOG, -0.40f, 0, 0.16f, 0.08f, 0.42f, 0.08f)
                .p(Material.STRIPPED_SPRUCE_LOG, 1.32f, 0, -0.22f, 0.08f, 0.42f, 0.08f)
                .p(Material.STRIPPED_SPRUCE_LOG, 1.32f, 0, 0.16f, 0.08f, 0.42f, 0.08f)
                .p(Material.SPRUCE_PLANKS, -0.46f, 0.42f, -0.24f, 1.92f, 0.06f, 0.50f)
                .p(Material.STRIPPED_SPRUCE_LOG, -0.40f, 0.48f, -0.28f, 0.08f, 0.50f, 0.06f)
                .p(Material.STRIPPED_SPRUCE_LOG, 1.32f, 0.48f, -0.28f, 0.08f, 0.50f, 0.06f)
                .p(Material.SPRUCE_PLANKS, -0.46f, 0.62f, -0.28f, 1.92f, 0.10f, 0.05f)
                .p(Material.SPRUCE_PLANKS, -0.46f, 0.82f, -0.28f, 1.92f, 0.10f, 0.05f));
        // ---------- Tische ----------
        add("tisch", "Tisch", Size.MITTEL, Material.OAK_PRESSURE_PLATE, 1, 0, seat(), -1, new B()
                .legs(Material.STRIPPED_OAK_LOG, -0.42f, -0.42f, 0.42f, 0.42f, 0.08f, 0.86f)
                .p(Material.OAK_PLANKS, -0.48f, 0.86f, -0.48f, 0.96f, 0.08f, 0.96f));
        add("esstisch", "Esstisch", Size.GROSS, Material.DARK_OAK_PRESSURE_PLATE, 2, 0, seat(), -1, new B()
                .legs(Material.STRIPPED_DARK_OAK_LOG, -0.42f, -0.42f, 1.42f, 0.42f, 0.09f, 0.86f)
                .p(Material.DARK_OAK_PLANKS, -0.48f, 0.86f, -0.48f, 1.96f, 0.08f, 0.96f)
                .p(Material.WHITE_CARPET, 0.20f, 0.94f, -0.34f, 0.60f, 0.01f, 0.68f));
        add("beistelltisch", "Beistelltisch", Size.KLEIN, Material.SPRUCE_PRESSURE_PLATE, 1, 0, seat(), -1, new B()
                .p(Material.SPRUCE_PLANKS, -0.2f, 0, -0.2f, 0.4f, 0.06f, 0.4f)
                .p(Material.STRIPPED_SPRUCE_LOG, -0.05f, 0.06f, -0.05f, 0.1f, 0.49f, 0.1f)
                .p(Material.SPRUCE_PLANKS, -0.3f, 0.55f, -0.3f, 0.6f, 0.06f, 0.6f));
        add("nachttisch", "Nachttisch", Size.KLEIN, Material.BARREL, 1, 0, seat(), -1, new B()
                .p(Material.SPRUCE_PLANKS, -0.30f, 0, -0.25f, 0.60f, 0.55f, 0.50f)
                .p(Material.DARK_OAK_PLANKS, -0.25f, 0.30f, 0.25f, 0.50f, 0.20f, 0.02f)
                .p(Material.DARK_OAK_PLANKS, -0.25f, 0.06f, 0.25f, 0.50f, 0.20f, 0.02f)
                .p(Material.GOLD_BLOCK, -0.03f, 0.37f, 0.27f, 0.06f, 0.05f, 0.03f)
                .p(Material.GOLD_BLOCK, -0.03f, 0.13f, 0.27f, 0.06f, 0.05f, 0.03f)
                .p(Material.SPRUCE_PLANKS, -0.32f, 0.55f, -0.27f, 0.64f, 0.05f, 0.54f));
        // ---------- Lampen ----------
        add("stehlampe", "Stehlampe", Size.MITTEL, Material.END_ROD, 1, 0, seat(), 1, new B()
                .p(Material.POLISHED_DEEPSLATE, -0.15f, 0, -0.15f, 0.30f, 0.05f, 0.30f)
                .p(Material.IRON_BLOCK, -0.025f, 0.05f, -0.025f, 0.05f, 1.30f, 0.05f)
                .glow(Material.GLOWSTONE, -0.08f, 1.22f, -0.08f, 0.16f, 0.10f, 0.16f)
                .glow(Material.WHITE_WOOL, -0.22f, 1.26f, -0.22f, 0.44f, 0.32f, 0.44f));
        add("tischlampe", "Tischlampe", Size.KLEIN, Material.LANTERN, 1, 0, seat(), 0, new B()
                .p(Material.GOLD_BLOCK, -0.1f, 0, -0.1f, 0.2f, 0.04f, 0.2f)
                .p(Material.GOLD_BLOCK, -0.015f, 0.04f, -0.015f, 0.03f, 0.26f, 0.03f)
                .glow(Material.ORANGE_WOOL, -0.15f, 0.28f, -0.15f, 0.30f, 0.20f, 0.30f));
        // ---------- Regale und Pflanzen ----------
        add("regal", "Bücherregal", Size.MITTEL, Material.BOOKSHELF, 1, 0, seat(), -1, new B()
                .p(Material.SPRUCE_PLANKS, -0.45f, 0, -0.22f, 0.90f, 1.78f, 0.03f)
                .p(Material.DARK_OAK_PLANKS, -0.48f, 0, -0.22f, 0.06f, 1.80f, 0.42f)
                .p(Material.DARK_OAK_PLANKS, 0.42f, 0, -0.22f, 0.06f, 1.80f, 0.42f)
                .p(Material.DARK_OAK_PLANKS, -0.48f, 1.74f, -0.22f, 0.96f, 0.06f, 0.42f)
                .p(Material.DARK_OAK_PLANKS, -0.42f, 0, -0.22f, 0.84f, 0.06f, 0.42f)
                .p(Material.DARK_OAK_PLANKS, -0.42f, 0.58f, -0.22f, 0.84f, 0.05f, 0.42f)
                .p(Material.DARK_OAK_PLANKS, -0.42f, 1.16f, -0.22f, 0.84f, 0.05f, 0.42f)
                .p(Material.BOOKSHELF, -0.42f, 0.06f, -0.19f, 0.84f, 0.50f, 0.34f)
                .p(Material.BOOKSHELF, -0.42f, 0.63f, -0.19f, 0.84f, 0.50f, 0.34f)
                .p(Material.BOOKSHELF, -0.42f, 1.21f, -0.19f, 0.84f, 0.50f, 0.34f));
        add("farn", "Topf-Farn", Size.KLEIN, Material.FERN, 1, 0, seat(), -1, new B()
                .p(Material.POTTED_FERN, -0.5f, 0, -0.5f, 1, 1, 1));
        add("kaktus", "Topf-Kaktus", Size.KLEIN, Material.CACTUS, 1, 0, seat(), -1, new B()
                .p(Material.POTTED_CACTUS, -0.5f, 0, -0.5f, 1, 1, 1));
        add("tulpe", "Tulpentopf", Size.KLEIN, Material.RED_TULIP, 1, 0, seat(), -1, new B()
                .p(Material.POTTED_RED_TULIP, -0.5f, 0, -0.5f, 1, 1, 1));
    }
}
