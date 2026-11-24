package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import io.papermc.paper.entity.LookAnchor;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Leute im Casino (v2.07, Wunsch des Besitzers: „stell dir vor, du arbeitest an einem GTA-Titel und musst die NPCs
 * machen. Ich liebe Tiefe in den NPCs“): frei aufstellbare Figuren mit Tätigkeiten. Gruppen unterhalten sich mit
 * Sprechblasen und Gesten, Gäste trinken, telefonieren, bestellen an der Bar, warten auf Freunde, sitzen auf Bänken
 * oder am Boden, lehnen an der Wand, schauen beim Spielen zu und jubeln mit, Pechvögel sitzen verzweifelt herum und
 * betteln um Chips, Security steht Wache oder läuft Streife.
 *
 * <p>Alle Figuren (auch das Personal aus {@link CasinoNpc}) blinzeln, schauen Spieler in der Nähe an und sagen beim
 * Rechtsklick etwas Eigenes. Skins kommen aus dem Figuren-Baukasten (tools/build_people.py, Liste people.txt). Die
 * Figuren sind dauerhaft: Tätigkeit, Variante und Platz stehen in der Figur selbst.
 */
public final class People implements Placeable, Listener {

    /** Tätigkeit einer Figur. */
    public enum Act {
        GRUPPE("gruppe", "Gruppe", "Unterhalten sich, lachen, gestikulieren"),
        TRINKEN("trinken", "Trinkt", "Glas in der Hand, nippt, prostet zu"),
        TELEFON("telefon", "Telefoniert", "Handy am Ohr, geht dabei auf und ab"),
        BESTELLEN("bestellen", "Bestellt", "An die Bar stellen, Blick zur Theke"),
        WARTEN("warten", "Wartet", "Schaut auf die Uhr, hält Ausschau nach Freunden"),
        BANK("bank", "Sitzt", "Auf die Bank oder den Hocker stellen"),
        BODEN("boden", "Sitzt am Boden", "Sitzt auf dem Boden, Blick wie du"),
        WAND("wand", "Lehnt an der Wand", "Rücken zur Wand stellen"),
        VERZWEIFELT("verzweifelt", "Verzweifelt", "Hat alles verspielt, bettelt um Chips"),
        ZUSCHAUER("zuschauer", "Schaut zu", "Neben einen Tisch stellen: fiebert mit, jubelt"),
        SECURITY("security", "Security", "Steht Wache, funkt, hat alles im Blick"),
        STREIFE("streife", "Streife", "Läuft in Blickrichtung auf und ab (Länge änderbar)");

        public final String id, title, desc;

        Act(String id, String title, String desc) {
            this.id = id;
            this.title = title;
            this.desc = desc;
        }

        public static Act of(String id) {
            for (Act a : values()) if (a.id.equalsIgnoreCase(id)) return a;
            return null;
        }

        boolean sits() {
            return this == BANK || this == BODEN || this == VERZWEIFELT;
        }

        boolean guard() {
            return this == SECURITY || this == STREIFE;
        }
    }

    /** Eine Figur aus dem Baukasten. */
    public record Variant(String id, String name, String group, boolean slim, String hat) {
        public String skin() {
            return "entity/people/" + id;
        }
    }

    private static final Random RND = new Random();
    private static final TextColor BUBBLE = TextColor.color(0xF4F0E8), RADIO = TextColor.color(0x9AB4C8), CHEER = TextColor.color(0xF6D45A);

    private final KubyPlugins plugin;
    /** Tätigkeit der Figur (Leute) bzw. Skin-Pfad (alle animierten Figuren). */
    private final NamespacedKey actKey, varKey, skinKey, slimKey, anchorKey, groupKey, lenKey, seatKey, bubbleKey;
    private final Map<String, Variant> variants = new LinkedHashMap<>();
    private final Map<UUID, Npc> npcs = new HashMap<>();
    private long tick;
    private final java.util.Set<String> warned = new java.util.HashSet<>();

    public People(KubyPlugins plugin) {
        this.plugin = plugin;
        actKey = new NamespacedKey(plugin, "people_act");
        varKey = new NamespacedKey(plugin, "people_var");
        skinKey = new NamespacedKey(plugin, "people_skin");
        slimKey = new NamespacedKey(plugin, "people_slim");
        anchorKey = new NamespacedKey(plugin, "people_anchor");
        groupKey = new NamespacedKey(plugin, "people_group");
        lenKey = new NamespacedKey(plugin, "people_len");
        seatKey = new NamespacedKey(plugin, "people_seat");
        bubbleKey = new NamespacedKey(plugin, "people_bubble");
        loadVariants();
    }

    private void loadVariants() {
        try (InputStream in = People.class.getResourceAsStream("/people.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split(";");
                if (p.length < 4) continue;
                variants.put(p[0], new Variant(p[0], p[1], p[2], p[3].equals("1"), p.length > 4 && !p[4].equals("-") ? p[4] : null));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("people.txt nicht lesbar: " + e.getMessage());
        }
    }

    public Variant variant(String id) {
        return id == null ? null : variants.get(id.toLowerCase(Locale.ROOT));
    }

    /** Varianten einer Gruppe (security, gast, kasse, dealer, croupier, chipshop, bar, losbude). */
    public List<Variant> group(String group) {
        List<Variant> out = new ArrayList<>();
        for (Variant v : variants.values()) if (v.group().equals(group)) out.add(v);
        return out;
    }

    public java.util.Collection<Variant> variants() {
        return variants.values();
    }

    // =================================================================================================================
    // Aufstellen
    // =================================================================================================================

    /** Passende Varianten für eine Tätigkeit: Security für Wache/Streife, Pechvögel bevorzugt für Verzweifelte. */
    List<Variant> pool(Act act) {
        if (act.guard()) return group("security");
        List<Variant> gast = group("gast");
        if (act == Act.VERZWEIFELT) {
            List<Variant> sad = new ArrayList<>();
            for (Variant v : gast) if (v.id().equals("gast_11") || v.id().equals("gast_12")) sad.add(v);
            return sad.isEmpty() ? gast : sad;
        }
        if (act == Act.GRUPPE || act == Act.TRINKEN || act == Act.BESTELLEN) {
            gast.removeIf(v -> v.id().equals("gast_11") || v.id().equals("gast_12"));
        }
        return gast;
    }

    private Variant pick(Act act, String wanted, List<String> taken) {
        Variant v = find(wanted);
        if (v != null) return v;
        List<Variant> pool = new ArrayList<>(pool(act));
        pool.removeIf(x -> taken.contains(x.id()));
        if (pool.isEmpty()) pool = pool(act);
        return pool.isEmpty() ? null : pool.get(RND.nextInt(pool.size()));
    }

    /** Variante nach Kennung (gast_3) oder Name (Viktor). */
    public Variant find(String wanted) {
        if (wanted == null || wanted.isBlank()) return null;
        Variant v = variant(wanted);
        if (v != null) return v;
        for (Variant x : variants.values()) if (x.name().equalsIgnoreCase(wanted)) return x;
        return null;
    }

    /**
     * Stellt Figuren mit dieser Tätigkeit an den Platz des Admins. Gruppe: count Figuren im Kreis, Blick zur Mitte.
     * Sitzen: Hüfte auf Höhe der Füße des Admins (auf der Bank bzw. auf dem Boden stehen).
     */
    public List<Entity> spawn(Act act, Location at, String wanted, int count) {
        List<Entity> out = new ArrayList<>();
        Location base = at.clone();
        base.setPitch(0);
        if (act == Act.GRUPPE) {
            int n = Math.max(2, Math.min(5, count <= 0 ? 3 : count));
            String group = UUID.randomUUID().toString().substring(0, 8);
            List<String> taken = new ArrayList<>();
            double r = n == 2 ? 0.65 : 0.85;
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians(base.getYaw()) + i * 2 * Math.PI / n;
                Location l = base.clone().add(-Math.sin(a) * r, 0, Math.cos(a) * r);
                l.setYaw(Placeable.norm((float) Math.toDegrees(Math.atan2(-(base.getX() - l.getX()), base.getZ() - l.getZ()))));
                Variant v = pick(act, i == 0 ? wanted : null, taken);
                if (v == null) break;
                taken.add(v.id());
                Mannequin m = make(l, v, act, group, 0);
                if (RND.nextInt(3) == 0) hold(m, RND.nextBoolean() ? "sekt" : "cocktail", null);
                out.add(m);
            }
            return out;
        }
        Variant v = pick(act, wanted, List.of());
        if (v == null) return out;
        out.add(make(base, v, act, null, act == Act.STREIFE ? 6 : act == Act.TELEFON ? 2.5 : 0));
        return out;
    }

    private Mannequin make(Location l, Variant v, Act act, String group, double len) {
        Mannequin m = mannequin(l, v.name(), v.skin(), v.slim());
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(actKey, PersistentDataType.STRING, act.id);
        pdc.set(varKey, PersistentDataType.STRING, v.id());
        pdc.set(anchorKey, PersistentDataType.STRING, anchor(l));
        if (group != null) pdc.set(groupKey, PersistentDataType.STRING, group);
        if (len > 0) pdc.set(lenKey, PersistentDataType.DOUBLE, len);
        if (v.hat() != null) m.getEquipment().setHelmet(model(v.hat()));
        if (act.sits()) seat(m, l);
        if (act == Act.TRINKEN) hold(m, new String[]{"sekt", "cocktail", "whisky", "bier"}[RND.nextInt(4)], null);
        register(m);
        return m;
    }

    /**
     * Figur mit Skin aus dem Pack (kuby:&lt;skin&gt;), ohne KI, unverwundbar, dauerhaft. Auch für das Personal
     * (Kasse, Croupier, Losbude …): die Markierung des Skins macht sie zu animierten Figuren (Blinzeln, Blicke).
     */
    public Mannequin mannequin(Location loc, String name, String skin, boolean slim) {
        return loc.getWorld().spawn(loc, Mannequin.class, m -> {
            m.setProfile(profile(name, skin, slim));
            m.setImmovable(true);
            m.setDescription(null);
            m.setAI(false);
            m.setGravity(false);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setPersistent(true);
            m.setRemoveWhenFarAway(false);
            m.setCustomNameVisible(false);
            m.getPersistentDataContainer().set(skinKey, PersistentDataType.STRING, skin);
            m.getPersistentDataContainer().set(slimKey, PersistentDataType.BYTE, (byte) (slim ? 1 : 0));
        });
    }

    private static io.papermc.paper.datacomponent.item.ResolvableProfile profile(String name, String skin, boolean slim) {
        return io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile()
                .name(name.replaceAll("[^A-Za-z0-9_]", ""))
                .skinPatch(sp -> sp.body(Key.key("kuby", skin)).model(slim ? PlayerTextures.SkinModel.SLIM : PlayerTextures.SkinModel.CLASSIC))
                .build();
    }

    /** Sitz: unsichtbarer Marker, Hüfte auf Höhe von l (Marker 0,10 darunter, wie an den Blackjack-Hockern). */
    private void seat(Mannequin m, Location l) {
        Location s = l.clone().add(0, -0.10, 0);
        ArmorStand a = l.getWorld().spawn(s, ArmorStand.class, e -> {
            e.setInvisible(true);
            e.setMarker(true);
            e.setGravity(false);
            e.setSmall(true);
            e.setInvulnerable(true);
            e.setPersistent(true);
            e.getPersistentDataContainer().set(seatKey, PersistentDataType.STRING, m.getUniqueId().toString());
        });
        a.addPassenger(m);
    }

    private ArmorStand seatOf(Mannequin m) {
        return m.getVehicle() instanceof ArmorStand a && a.getPersistentDataContainer().has(seatKey, PersistentDataType.STRING) ? a : null;
    }

    private static String anchor(Location l) {
        return String.format(Locale.ROOT, "%s;%.3f;%.3f;%.3f;%.1f", l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw());
    }

    private Location anchor(String raw, World fallback) {
        if (raw == null) return null;
        String[] p = raw.split(";");
        try {
            World w = plugin.getServer().getWorld(p[0]);
            Location l = new Location(w == null ? fallback : w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
            l.setYaw(Float.parseFloat(p[4]));
            return l;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // =================================================================================================================
    // Hand, Sprechblasen
    // =================================================================================================================

    /** Requisit (kuby:people/&lt;kind&gt;) als Item; mit Haltung „benutzbar“ (Arm-Pose), ohne Ton und Partikel. */
    public static ItemStack model(String kind) {
        return prop(kind, null);
    }

    static ItemStack prop(String kind, ItemUseAnimation use) {
        ItemStack it = ItemStack.of(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("kuby", "people/" + kind));
        if (use != null) {
            it.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().consumeSeconds(3600).animation(use)
                    .hasConsumeParticles(false).sound(Key.key("minecraft", "intentionally_empty")).build());
        }
        return it;
    }

    /** Requisit in die rechte Hand; use = Arm-Pose (null = nur halten). */
    static void hold(Mannequin m, String kind, ItemUseAnimation use) {
        m.clearActiveItem();
        m.getEquipment().setItemInMainHand(kind == null ? null : prop(kind, use));
        if (use != null) m.startUsingItem(EquipmentSlot.HAND);
    }

    /** Linke Hand (unsichtbares Requisit) als Geste: Uhr ansehen, Ohrhörer, Hand vors Gesicht. */
    static void offhand(Mannequin m, ItemUseAnimation use) {
        m.clearActiveItem();
        m.getEquipment().setItemInOffHand(use == null ? null : prop("leer", use));
        if (use != null) m.startUsingItem(EquipmentSlot.OFF_HAND);
    }

    static String heldKind(Mannequin m) {
        ItemStack it = m.getEquipment().getItemInMainHand();
        if (it == null || it.isEmpty() || !it.hasData(DataComponentTypes.ITEM_MODEL)) return null;
        Key k = it.getData(DataComponentTypes.ITEM_MODEL);
        return k == null ? null : k.value().replace("people/", "");
    }

    /** Sprechblase über dem Kopf (fährt mit der Figur mit), verschwindet nach ticks. */
    void say(Npc n, Mannequin m, String text, TextColor color, int ticks, boolean italic) {
        if (text == null || !playerNear(m.getLocation(), 14)) return;
        clearBubble(n, m);
        Component c = Component.text(text, color);
        if (italic) c = c.decorate(TextDecoration.ITALIC);
        Component fc = c;
        TextDisplay d = m.getWorld().spawn(m.getLocation().add(0, 2.1, 0), TextDisplay.class, e -> {
            e.text(fc);
            e.setBillboard(Display.Billboard.CENTER);
            e.setBackgroundColor(Color.fromARGB(0xA8, 0x16, 0x12, 0x1E));
            e.setShadowed(false);
            e.setLineWidth(150);
            e.setPersistent(false);
            e.setSeeThrough(false);
            e.setTransformation(new Transformation(new Vector3f(0, 0.32f, 0), new AxisAngle4f(), new Vector3f(0.5f, 0.5f, 0.5f),
                    new AxisAngle4f()));
            e.getPersistentDataContainer().set(bubbleKey, PersistentDataType.BYTE, (byte) 1);
        });
        m.addPassenger(d);
        n.bubble = d.getUniqueId();
        n.bubbleEnd = tick + ticks;
    }

    void say(Npc n, Mannequin m, String text) {
        say(n, m, text, BUBBLE, 70, false);
    }

    private void clearBubble(Npc n, Mannequin m) {
        if (n.bubble != null) {
            Entity old = plugin.getServer().getEntity(n.bubble);
            if (old != null) old.remove();
            n.bubble = null;
        }
        for (Entity p : m.getPassengers()) if (p.getPersistentDataContainer().has(bubbleKey, PersistentDataType.BYTE)) p.remove();
    }

    private boolean playerNear(Location l, double r) {
        if (l.getWorld() == null) return false;
        for (Player p : l.getWorld().getPlayers()) if (p.getLocation().distanceSquared(l) < r * r) return true;
        return false;
    }

    private Player nearest(Location l, double r) {
        Player best = null;
        double bd = r * r;
        if (l.getWorld() == null) return null;
        for (Player p : l.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(l);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // =================================================================================================================
    // Texte
    // =================================================================================================================

    static String any(String... s) {
        return s[RND.nextInt(s.length)];
    }

    /** Gesprächsrunden der Gruppen: Zeile i spricht Mitglied i mod n. */
    static final String[][] TALKS = {
            {"Und dann setzt er alles auf die 17.", "Nein!", "Doch! Und die Kugel fällt …", "… auf die 17?", "Auf die 18. Ich hab geweint."},
            {"Habt ihr den Jackpot gesehen?", "Der wird heute geknackt, ich spür das.", "Das sagst du jede Woche.", "Und irgendwann stimmt's!"},
            {"Noch eine Runde Blackjack?", "Nur, wenn du diesmal nicht bei 19 ziehst.", "Das war Taktik!", "Das war Panik."},
            {"Kennt ihr den Croupier am roten Tisch?", "Der lächelt nie.", "Doch, einmal. Als ich verloren hab.", "Haha!"},
            {"Ich hör auf, solange ich vorne bin.", "Wie viel bist du vorne?", "Gerechnet ab dem letzten Gewinn …", "Also hinten."},
            {"Die Musik heute ist richtig gut.", "Die Jukebox hat neue Lieder!", "Ich wünsch mir gleich eins.", "Bitte nicht wieder das Gleiche."},
            {"Auf uns!", "Auf den Jackpot!", "Und aufs Heimkommen mit Hemd an.", "Prost!"},
            {"Siehst du den Typen an der Wand?", "Der steht da seit Stunden.", "Wartet wohl auf jemanden.", "Oder auf ein Wunder."},
            {"Ich hab ein System für Roulette.", "Oh nein.", "Rot, Rot, Schwarz, dann alles auf Grün.", "Und das klappt?", "Theoretisch."},
            {"Morgen früh Arbeit …", "Dann geh doch.", "Nach dem nächsten Gewinn.", "Also nie."},
    };

    static final String[] LAUGH = {"Haha!", "Hahaha!", "Hör auf!", "Nicht dein Ernst!", "Ich kann nicht mehr!"};

    static String click(Act act, Variant v) {
        String own = v == null ? null : PERSONAL.get(v.id());
        if (own != null && RND.nextInt(3) == 0) return own;
        return switch (act) {
            case GRUPPE -> any("Wir sind gleich wieder am Tisch.", "Setz dich doch dazu!", "Pssst, der Dealer hört mit.");
            case TRINKEN -> any("Prost! Auf dein Glück.", "Der Barkeeper mixt hier die besten Drinks.", "Nur ein Glas. Oder zwei.");
            case TELEFON -> any("Moment … nein, nicht du. Ich telefonier gerade.", "Ja, Schatz, ich bin gleich zu Hause!", "Ich ruf zurück!");
            case BESTELLEN -> any("Ich warte schon ewig auf meinen Drink.", "Weißt du, ob es hier Oliven gibt?", "Zwei Martini, bitte!");
            case WARTEN -> any("Hast du meine Freunde gesehen? Drei Leute, laut, gut gelaunt.", "Die wollten um acht hier sein.",
                    "Ich warte hier seit einer Stunde.");
            case BANK -> any("Kurze Pause. Die Beine wollen nicht mehr.", "Von hier sieht man alle Tische.", "Hier sitz ich jeden Abend.");
            case BODEN -> any("Ist doch gemütlich hier unten.", "Ich sammle nur Kraft für die nächste Runde.", "Lass mich, ich denk nach.");
            case WAND -> any("Ich beobachte nur.", "Ich spiel nicht, ich schau nur.", "Ich hab da so ein Gefühl bei Tisch zwei.");
            case VERZWEIFELT -> any("Alles weg … die ganze Miete.", "Hast du vielleicht ein paar Chips übrig?",
                    "Rot. Es war doch so sicher Rot.", "Nur noch eine Runde, dann hol ich alles zurück …", "Sag meiner Frau nichts.");
            case ZUSCHAUER -> any("Der da drüben hat einen Lauf!", "Ich spiel nicht, ich fiebere nur mit.", "Gleich passiert was, ich spür's.");
            case SECURITY, STREIFE -> any("Alles in Ordnung hier?", "Bitte nicht rennen im Casino.", "Viel Glück heute Abend.",
                    "Ich hab dich im Blick. Freundlich gemeint.");
        };
    }

    /** Eigene Sätze je Figur: Hintergrund, der beim Ansprechen durchscheint. */
    static final Map<String, String> PERSONAL = Map.ofEntries(
            Map.entry("sec_1", "Zwölf Jahre Türsteher. Ich erkenn Ärger, bevor er reinkommt."),
            Map.entry("sec_2", "Ich war mal Boxer. Jetzt pass ich auf, dass hier keiner boxt."),
            Map.entry("sec_3", "Meine Tochter hat Geburtstag. Ich hab heute trotzdem Schicht."),
            Map.entry("sec_4", "Unterschätz mich ruhig. Machen alle. Einmal."),
            Map.entry("sec_5", "Ich hab jede Kamera hier im Kopf. Jede."),
            Map.entry("gast_1", "Mein drittes Casino heute Abend. Das erste hab ich gekauft."),
            Map.entry("gast_2", "Ich spiele nie. Ich gewinne nur."),
            Map.entry("gast_3", "Ich bin nur wegen der Musik hier. Und wegen Blackjack."),
            Map.entry("gast_4", "Mein Mann denkt, ich bin im Theater. Das hier ist besser."),
            Map.entry("gast_5", "Samt ist zurück. Sag's weiter."),
            Map.entry("gast_6", "Heute ist mein Glückstag. Das hab ich im Gefühl."),
            Map.entry("gast_7", "Ich hab mich nur verlaufen. Seit drei Stunden."),
            Map.entry("gast_8", "Ich zähl keine Karten. Ich merk sie mir nur."),
            Map.entry("gast_9", "Damals, als die Chips noch aus Ton waren …"),
            Map.entry("gast_10", "Ich bin Journalistin. Ich schreib über dieses Casino. Vielleicht."),
            Map.entry("gast_11", "Ich hatte ein Haus. Jetzt hab ich eine Geschichte."),
            Map.entry("gast_12", "Ich hör auf. Ab morgen. Wirklich."));

    // =================================================================================================================
    // Laufzeit: Zustand je Figur, Takt
    // =================================================================================================================

    /** Laufzeit-Zustand einer animierten Figur. */
    static final class Npc {
        final UUID id;
        Act act;               // null = Personal (Rolle aus CasinoNpc)
        CasinoNpc.Role role;
        Variant variant;
        String skin;
        boolean slim;
        String name;
        Location anchor;
        String group;
        double len;
        long nextBlink, blinkEnd = -1, next, phaseEnd, bubbleEnd, nextGreet, nextLook;
        int phase, line;
        int script = -1;
        UUID bubble;
        double pos;
        int dir = 1;
        Location look;
        long lookUntil;

        Npc(UUID id) {
            this.id = id;
        }
    }

    public void start() {
        for (World w : plugin.getServer().getWorlds()) for (Entity e : w.getEntities()) register(e);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) register(en);
    }

    /** Animierte Figur aufnehmen (Leute und Personal mit Skin-Markierung). */
    public void register(Entity e) {
        if (!(e instanceof Mannequin m)) {
            // verwaiste Sprechblasen nach einem Neustart weg
            if (e instanceof TextDisplay && e.getPersistentDataContainer().has(bubbleKey, PersistentDataType.BYTE)) e.remove();
            // alter Dorfbewohner-Barkeeper (vor v2.07): durch die neue Figur ersetzen
            if (e instanceof org.bukkit.entity.Villager v && plugin.casinoVip() != null && plugin.casinoVip().isBarkeeper(v)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!v.isValid()) return;
                    Location at = v.getLocation();
                    v.remove();
                    plugin.casinoVip().barkeeperAt(at, null);
                });
            }
            return;
        }
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        String skin = pdc.get(skinKey, PersistentDataType.STRING);
        if (skin == null) {
            // Personal von vor v2.07: Standard-Aussehen der Rolle übernehmen (Losbude und Chip-Shop bekommen ihre Figur)
            if (plugin.casinoNpc() != null && plugin.casinoNpc().roleOf(m) != null) plugin.casinoNpc().upgrade(m);
            return;
        }
        Npc n = npcs.computeIfAbsent(m.getUniqueId(), Npc::new);
        n.skin = skin;
        n.slim = pdc.getOrDefault(slimKey, PersistentDataType.BYTE, (byte) 0) == 1;
        n.act = Act.of(pdc.getOrDefault(actKey, PersistentDataType.STRING, ""));
        n.role = plugin.casinoNpc() == null ? null : plugin.casinoNpc().roleOf(m);
        n.variant = variant(pdc.get(varKey, PersistentDataType.STRING));
        n.name = n.variant != null ? n.variant.name() : n.role != null ? n.role.title : "Gast";
        n.anchor = anchor(pdc.get(anchorKey, PersistentDataType.STRING), m.getWorld());
        if (n.anchor == null) n.anchor = m.getLocation();
        n.group = pdc.get(groupKey, PersistentDataType.STRING);
        n.len = pdc.getOrDefault(lenKey, PersistentDataType.DOUBLE, 0.0);
        n.nextBlink = tick + 20 + RND.nextInt(80);
        n.next = tick + 20 + RND.nextInt(100);
        // Sitzende wieder auf ihren Sitz (falls der Marker fehlt)
        if (n.act != null && n.act.sits() && m.getVehicle() == null) {
            m.teleport(n.anchor);
            seat(m, n.anchor);
        }
    }

    private void tick() {
        tick++;
        java.util.Iterator<Map.Entry<UUID, Npc>> it = npcs.entrySet().iterator();
        Map<String, List<Npc>> groups = new HashMap<>();
        while (it.hasNext()) {
            Npc n = it.next().getValue();
            Entity e = plugin.getServer().getEntity(n.id);
            if (!(e instanceof Mannequin m) || !m.isValid()) {
                if (e == null || !e.isValid()) it.remove();
                continue;
            }
            if (n.group != null) groups.computeIfAbsent(n.group, k -> new ArrayList<>()).add(n);
            if (!playerNear(m.getLocation(), 40)) continue;
            try {
                blink(n, m);
                if (n.bubble != null && tick >= n.bubbleEnd) clearBubble(n, m);
                if (n.act != null) act(n, m);
                else staff(n, m);
                gaze(n, m);
            } catch (RuntimeException ex) {
                if (warned.add(n.name + ex.getClass().getSimpleName())) {
                    plugin.getLogger().warning("[Leute] Figur " + n.name + " (" + (n.act == null ? "Personal" : n.act.id) + "): " + ex);
                }
            }
        }
        if (tick % 5 == 0) for (List<Npc> g : groups.values()) talk(g);
    }

    /** Blinzeln: alle 3 bis 6 Sekunden für 3 Takte die Fassung mit geschlossenen Augen, manchmal doppelt. */
    private void blink(Npc n, Mannequin m) {
        if (n.blinkEnd >= 0 && tick >= n.blinkEnd) {
            m.setProfile(profile(n.name, n.skin, n.slim));
            n.blinkEnd = -1;
            n.nextBlink = tick + (RND.nextInt(6) == 0 ? 6 : 60 + RND.nextInt(70));
        } else if (n.blinkEnd < 0 && tick >= n.nextBlink) {
            m.setProfile(profile(n.name, n.skin + "_b", n.slim));
            n.blinkEnd = tick + 3;
        }
    }

    /**
     * Blick: Spieler in der Nähe werden angesehen (Kopf, der Körper bleibt in seiner Richtung), sonst schweift der Blick
     * umher. Wache: langsames Absuchen des Raums.
     */
    private void gaze(Npc n, Mannequin m) {
        if (tick % 4 != 0) return;
        float body = (n.act == Act.STREIFE || n.act == Act.TELEFON) && n.dir < 0 ? Placeable.norm(n.anchor.getYaw() + 180) : n.anchor.getYaw();
        Location eye = m.getEyeLocation();
        Location target = null;
        if (n.look != null && tick < n.lookUntil) target = n.look;
        else {
            Player p = nearest(m.getLocation(), n.act == Act.VERZWEIFELT ? 3.5 : 5);
            if (p != null && n.act != Act.STREIFE && facing(body, eye, p.getEyeLocation(), 100)) target = p.getEyeLocation();
        }
        if (target == null) {
            if (tick >= n.nextLook) {
                float sweep = n.act == Act.SECURITY ? 55 : 35;
                float yaw = body + (RND.nextFloat() * 2 - 1) * sweep;
                float pitch = n.act == Act.VERZWEIFELT ? 45 + RND.nextInt(15) : n.act == Act.BODEN ? 10 : RND.nextInt(12) - 4;
                Vector dir = new Location(m.getWorld(), 0, 0, 0, yaw, pitch).getDirection().multiply(4);
                n.look = eye.clone().add(dir);
                n.lookUntil = tick + 50 + RND.nextInt(70);
                n.nextLook = n.lookUntil;
                target = n.look;
            } else {
                return;
            }
        }
        m.lookAt(target.getX(), target.getY(), target.getZ(), LookAnchor.EYES);
        m.setBodyYaw(body);
        ArmorStand seat = seatOf(m);
        if (seat != null && Math.abs(Placeable.norm(seat.getLocation().getYaw() - body)) > 1) seat.setRotation(body, 0);
    }

    private static boolean facing(float yaw, Location from, Location to, float within) {
        double a = Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
        return Math.abs(Placeable.norm((float) (a - yaw))) <= within;
    }

    private void lookAt(Npc n, Location l, int ticks) {
        n.look = l == null ? null : l.clone();
        n.lookUntil = tick + ticks;
    }

    /** Spieler-Begrüßung beim Näherkommen (selten, je Figur mit Pause). */
    private boolean greet(Npc n, Mannequin m, String... lines) {
        if (tick < n.nextGreet) return false;
        Player p = nearest(m.getLocation(), 3.2);
        if (p == null) return false;
        n.nextGreet = tick + 20 * 45 + RND.nextInt(20 * 40);
        lookAt(n, p.getEyeLocation(), 60);
        say(n, m, any(lines));
        return true;
    }

    // ---------- Tätigkeiten ----------

    private void act(Npc n, Mannequin m) {
        switch (n.act) {
            case GRUPPE -> {
                // Gesten kommen aus talk(); dazwischen ab und zu ein Schluck
                if (tick >= n.next && heldKind(m) != null && !"leer".equals(heldKind(m))) {
                    n.next = tick + 200 + RND.nextInt(300);
                    sip(m, heldKind(m));
                }
                endUse(n, m);
            }
            case TRINKEN -> drink(n, m);
            case TELEFON -> phone(n, m);
            case BESTELLEN -> order(n, m);
            case WARTEN -> waiting(n, m);
            case BANK, BODEN -> sit(n, m);
            case WAND -> lean(n, m);
            case VERZWEIFELT -> despair(n, m);
            case ZUSCHAUER -> watch(n, m);
            case SECURITY -> guard(n, m);
            case STREIFE -> patrol(n, m);
        }
    }

    /** Arm-Pose nach Ablauf beenden (phaseEnd), Requisit bleibt in der Hand. */
    private void endUse(Npc n, Mannequin m) {
        if (n.phaseEnd > 0 && tick >= n.phaseEnd) {
            n.phaseEnd = 0;
            String k = heldKind(m);
            m.clearActiveItem();
            m.getEquipment().setItemInOffHand(null);
            if (k != null) m.getEquipment().setItemInMainHand(k.equals("leer") ? null : prop(k, null));
        }
    }

    /** Schluck: Glas zum Mund (Horn-Pose), nach 1,5 s wieder runter. */
    private void sip(Mannequin m, String glass) {
        hold(m, glass, ItemUseAnimation.TOOT_HORN);
        Npc n = npcs.get(m.getUniqueId());
        if (n != null) n.phaseEnd = tick + 30;
    }

    private void drink(Npc n, Mannequin m) {
        endUse(n, m);
        if (heldKind(m) == null) hold(m, "cocktail", null);
        if (greet(n, m, "Prost!", "Auf dein Glück!", "Schöner Abend, oder?")) {
            m.swingMainHand();
            return;
        }
        if (tick < n.next) return;
        n.next = tick + 120 + RND.nextInt(260);
        int r = RND.nextInt(10);
        if (r < 6) sip(m, heldKind(m));
        else if (r < 8) say(n, m, any("Hm, gut gemixt.", "Noch einer, dann spiel ich.", "*nippt*", "Der Abend ist jung."));
        else {
            m.swingMainHand();           // prostet in den Raum
            say(n, m, any("Prost!", "Auf den Jackpot!", "Cheers!"));
        }
    }

    /** Telefonat: Handy am Ohr, geht ein paar Schritte auf und ab, redet in Häppchen, legt irgendwann auf. */
    private void phone(Npc n, Mannequin m) {
        if (n.phase == 0) {
            if (tick < n.next) {
                if (heldKind(m) == null) hold(m, "handy", null);
                return;
            }
            n.phase = 1;
            n.phaseEnd = 0;
            hold(m, "handy", ItemUseAnimation.SPYGLASS);
            say(n, m, any("*klingel* … Ja?", "Hallo? Hörst du mich?", "Ja, ich bin's."));
            n.next = tick + 400 + RND.nextInt(500);
            n.line = 0;
        }
        // auf und ab gehen
        walk(n, m, 0.045, n.len <= 0 ? 2.5 : n.len, 40);
        if (tick % 90 == 0) {
            String[] lines = {"Ja … ja … nein!", "Wie, die Karte ist gesperrt?", "Ich bin gleich zu Hause, Schatz.", "Sag Mama, ich bin im Theater.",
                    "Nein, ich hab nicht gespielt. Fast nicht.", "Haha, nein! Erzähl!", "Warte, hier ist's so laut …", "Kannst du mir was leihen?",
                    "Der Deal steht. Morgen um neun.", "Ich hab gewonnen! Also … ein bisschen."};
            say(n, m, lines[(n.line++ + n.id.hashCode() & 0xFFFF) % lines.length], BUBBLE, 70, true);
            if (RND.nextInt(3) == 0) m.swingOffHand();
        }
        if (tick >= n.next) {
            say(n, m, any("Okay. Tschüss. Ja. Tschüss.", "Ich ruf dich zurück.", "*legt auf*"));
            hold(m, "handy", null);
            n.phase = 0;
            n.next = tick + 200 + RND.nextInt(400);
        }
    }

    /** Bestellen an der Bar: winken, warten (mit den Fingern trommeln), Getränk kommt, trinken, Glas abstellen. */
    private void order(Npc n, Mannequin m) {
        endUse(n, m);
        if (tick < n.next) return;
        switch (n.phase) {
            case 0 -> { // Hand heben
                hold(m, "leer", ItemUseAnimation.TRIDENT);
                n.phaseEnd = tick + 25;
                say(n, m, any("Entschuldigung! Hallo!", "Zwei Martini, bitte!", "Einen Whisky, ohne Eis.", "Ein Bier, bitte!",
                        "Haben Sie Champagner?"));
                n.phase = 1;
                n.next = tick + 60;
            }
            case 1 -> { // warten, trommeln
                hold(m, "leer", ItemUseAnimation.BRUSH);
                n.phaseEnd = tick + 60;
                if (RND.nextInt(2) == 0) say(n, m, any("*trommelt auf die Theke*", "Dauert's noch lange?", "Ich glaub, er hat mich gesehen."),
                        BUBBLE, 60, true);
                n.phase = RND.nextInt(3) == 0 ? 1 : 2;
                n.next = tick + 80 + RND.nextInt(60);
            }
            case 2 -> { // Getränk kommt
                String g = new String[]{"sekt", "cocktail", "whisky", "bier"}[RND.nextInt(4)];
                hold(m, g, null);
                m.swingMainHand();
                say(n, m, any("Danke!", "Perfekt.", "Stimmt so!"));
                n.phase = 3;
                n.line = 0;
                n.next = tick + 60;
            }
            case 3 -> { // trinken
                if (n.line++ < 4 + RND.nextInt(3)) {
                    sip(m, heldKind(m) == null ? "cocktail" : heldKind(m));
                    n.next = tick + 140 + RND.nextInt(120);
                } else {
                    hold(m, null, null);
                    n.phase = 0;
                    n.next = tick + 200 + RND.nextInt(200);
                }
            }
            default -> n.phase = 0;
        }
    }

    /** Warten auf Freunde: Blick zum Eingang, auf die Uhr schauen, seufzen; irgendwann kommen sie (Winken, Freude). */
    private void waiting(Npc n, Mannequin m) {
        endUse(n, m);
        if (greet(n, m, "Bist du … nein, sorry, verwechselt.", "Hast du drei Leute gesehen? Laut, gut gelaunt?")) return;
        if (tick < n.next) return;
        n.next = tick + 120 + RND.nextInt(200);
        int r = RND.nextInt(10);
        if (r < 3) {                           // Uhr ansehen: linker Arm hoch vors Gesicht
            offhand(m, ItemUseAnimation.BLOCK);
            n.phaseEnd = tick + 40;
            if (RND.nextBoolean()) say(n, m, any("Schon halb neun …", "Wo bleiben die denn?", "*seufzt*"), BUBBLE, 60, true);
        } else if (r < 5) {                    // aufs Handy schauen
            hold(m, "handy", ItemUseAnimation.BLOCK);
            n.phaseEnd = tick + 70;
            if (RND.nextBoolean()) say(n, m, any("„Bin gleich da“. Klar.", "Keine Nachricht …", "Gelesen. Keine Antwort."), BUBBLE, 70, true);
        } else if (r < 8) {                    // Ausschau halten
            float yaw = n.anchor.getYaw() + (RND.nextBoolean() ? 60 : -60);
            lookAt(n, m.getEyeLocation().add(new Location(m.getWorld(), 0, 0, 0, yaw, -5).getDirection().multiply(6)), 60);
        } else {                               // Freunde kommen (selten)
            m.swingMainHand();
            m.swingOffHand();
            say(n, m, any("Da seid ihr ja! Endlich!", "Hey! Hier drüben!", "Na endlich!"), CHEER, 70, false);
            n.next = tick + 600 + RND.nextInt(600);
        }
    }

    private void sit(Npc n, Mannequin m) {
        endUse(n, m);
        if (greet(n, m, "Na, auch eine Pause?", "Guten Abend.", "Setz dich ruhig.")) return;
        if (tick < n.next) return;
        n.next = tick + 200 + RND.nextInt(300);
        int r = RND.nextInt(10);
        if (r < 3) {
            hold(m, "handy", ItemUseAnimation.BLOCK);
            n.phaseEnd = tick + 100;
        } else if (r < 5 && n.act == Act.BANK) {
            say(n, m, any("Was für ein Abend.", "*gähnt*", "Gleich geht's weiter.", "Meine Füße …"), BUBBLE, 60, true);
        } else if (r < 6) {
            m.swingMainHand();                 // streckt sich
        }
    }

    /** An der Wand lehnen: Arme verschränkt (Block-Pose mit leerer Hand), Blick in den Raum, ab und zu das Handy. */
    private void lean(Npc n, Mannequin m) {
        endUse(n, m);
        if (n.phaseEnd == 0 && tick % 20 == 0 && m.getActiveItem().isEmpty()) {
            hold(m, "leer", ItemUseAnimation.BLOCK);
        }
        if (greet(n, m, "Hm.", "Abend.", "Na?")) return;
        if (tick < n.next) return;
        n.next = tick + 300 + RND.nextInt(400);
        if (RND.nextInt(3) == 0) {
            hold(m, "handy", ItemUseAnimation.BLOCK);
            n.phaseEnd = tick + 120;
        } else if (RND.nextInt(3) == 0) {
            say(n, m, any("*pfeift leise*", "Tisch zwei hat einen Lauf.", "Mal sehen, wer heute geht."), BUBBLE, 60, true);
        }
    }

    /**
     * Verzweifelt: sitzt am Boden, Kopf gesenkt, Hand vors Gesicht, Tränen, murmelt. Spieler in der Nähe werden
     * angebettelt; nach einem großen Gewinn nebenan: „Gib mir was ab!“.
     */
    private void despair(Npc n, Mannequin m) {
        endUse(n, m);
        if (tick % 70 == 0 && RND.nextBoolean()) {
            m.getWorld().spawnParticle(Particle.FALLING_WATER, m.getEyeLocation().add(0, -0.1, 0), 2, 0.12, 0.02, 0.12, 0);
        }
        Player p = nearest(m.getLocation(), 3.5);
        if (p != null && tick >= n.nextGreet) {
            n.nextGreet = tick + 20 * 30 + RND.nextInt(20 * 30);
            lookAt(n, p.getEyeLocation(), 80);
            hold(m, "leer", ItemUseAnimation.TRIDENT);     // Hand ausstrecken
            n.phaseEnd = tick + 30;
            say(n, m, any("Hey … hast du ein paar Chips übrig?", "Bitte … nur für eine Runde.", "Ich zahl's zurück, ehrlich!",
                    "Du siehst aus wie jemand, der Glück hat …"));
            return;
        }
        if (tick < n.next) return;
        n.next = tick + 160 + RND.nextInt(240);
        int r = RND.nextInt(10);
        if (r < 4) {
            offhand(m, ItemUseAnimation.SPYGLASS);         // Hand vors Gesicht
            n.phaseEnd = tick + 80;
            say(n, m, any("*schluchzt*", "Warum hab ich nicht aufgehört …", "Die ganze Miete …"), RADIO, 70, true);
        } else if (r < 7) {
            say(n, m, any("Rot. Es war doch so sicher Rot.", "Nur noch eine Runde, dann hol ich alles zurück.", "Alles weg …",
                    "Wie sag ich das zu Hause?", "Ich hatte so ein gutes Gefühl …"), RADIO, 80, true);
        } else {
            m.setPose(Pose.SNEAKING, true);                // krümmt sich zusammen
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (m.isValid()) m.setPose(Pose.STANDING, false);
            }, 40L);
        }
    }

    /** Zuschauer: fiebert mit (Kommentare), jubelt bei Gewinnen nebenan (onRound). */
    private void watch(Npc n, Mannequin m) {
        endUse(n, m);
        if (tick < n.next) return;
        n.next = tick + 160 + RND.nextInt(260);
        Player p = nearest(m.getLocation(), 7);
        if (p != null) lookAt(n, p.getLocation().add(0, 1.0, 0), 100);   // schaut auf die Hände/den Tisch des Spielers
        if (RND.nextInt(3) == 0) {
            say(n, m, any("Komm schon …", "Ui, knapp!", "Ich hätt' anders gesetzt.", "Jetzt aber!", "Das wird was!"), BUBBLE, 60, false);
            if (RND.nextBoolean()) m.swingMainHand();
        }
    }

    /** Wache: Hände vor dem Körper, sucht den Raum ab, greift ans Ohr und funkt. */
    private void guard(Npc n, Mannequin m) {
        endUse(n, m);
        if (n.phaseEnd == 0 && tick % 20 == 0 && m.getActiveItem().isEmpty()) hold(m, "leer", ItemUseAnimation.BLOCK);
        Player p = nearest(m.getLocation(), 6);
        if (p != null && p.isSprinting() && tick >= n.nextGreet) {
            n.nextGreet = tick + 20 * 20;
            lookAt(n, p.getEyeLocation(), 60);
            say(n, m, any("Hey! Nicht rennen!", "Langsam, Freund.", "Ruhig. Hier wird nicht gerannt."));
            return;
        }
        if (greet(n, m, "Guten Abend.", "Willkommen im Casino.", "Viel Glück heute.")) return;
        if (tick < n.next) return;
        n.next = tick + 400 + RND.nextInt(500);
        if (RND.nextInt(3) < 2) {
            offhand(m, ItemUseAnimation.SPYGLASS);         // Hand ans Ohr
            n.phaseEnd = tick + 60;
            say(n, m, "‹Funk› " + any("Alles ruhig am Eingang.", "Verstanden. Bin auf Position.", "Tisch drei im Auge behalten.",
                    "Negativ. Kein Problem hier.", "Kollege an der Bar, bitte melden.", "Jackpot-Bereich ist sauber."), RADIO, 70, true);
        }
    }

    /** Streife: geht in Blickrichtung des Ankers len Blöcke hin und zurück, bleibt an den Enden stehen und schaut sich um. */
    private void patrol(Npc n, Mannequin m) {
        endUse(n, m);
        walk(n, m, 0.075, n.len <= 0 ? 6 : n.len, 70);
        if (tick >= n.next && n.phase == 1) {
            n.next = tick + 600 + RND.nextInt(600);
            if (RND.nextBoolean()) {
                offhand(m, ItemUseAnimation.SPYGLASS);
                n.phaseEnd = tick + 50;
                say(n, m, "‹Funk› " + any("Runde zwei, alles sauber.", "Bin auf dem Weg zur Bar.", "Keine Auffälligkeiten."), RADIO, 60, true);
            }
        }
        if (greet(n, m, "Abend.", "Alles gut?")) return;
    }

    /**
     * Auf und ab gehen (Position pro Takt, die Beine bewegen sich im Spiel von selbst): an den Enden pause Takte
     * stehen bleiben (phase 1), dann umdrehen.
     */
    private void walk(Npc n, Mannequin m, double speed, double len, int pause) {
        if (n.phase == 1 && n.act == Act.STREIFE) {
            if (tick < n.phaseEnd) return;
            n.phase = 0;
            n.dir = -n.dir;
        }
        if (n.act == Act.TELEFON && n.phaseEnd > tick) return;
        if (n.dir == 0) n.dir = 1;
        n.pos += speed * n.dir;
        if (n.pos >= len || n.pos <= 0) {
            n.pos = Math.max(0, Math.min(len, n.pos));
            if (n.act == Act.STREIFE) {
                n.phase = 1;
                n.phaseEnd = tick + pause + RND.nextInt(pause);
                lookAt(n, null, 0);
                n.nextLook = tick;
            } else {
                n.dir = -n.dir;
                n.phaseEnd = tick + pause;         // kurz stehen bleiben (Telefon)
            }
        }
        Vector fwd = new Location(m.getWorld(), 0, 0, 0, n.anchor.getYaw(), 0).getDirection();
        Location to = n.anchor.clone().add(fwd.multiply(n.pos));
        to.setYaw(n.dir > 0 ? n.anchor.getYaw() : Placeable.norm(n.anchor.getYaw() + 180));
        to.setPitch(m.getLocation().getPitch());
        m.teleport(to, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        m.setBodyYaw(to.getYaw());
    }

    /** Gruppen-Gespräch: der Sprecher redet und gestikuliert, die anderen schauen ihn an, manchmal lachen alle. */
    private void talk(List<Npc> g) {
        if (g.size() < 2) return;
        Npc lead = g.get(0);
        for (Npc n : g) if (n.id.compareTo(lead.id) < 0) lead = n;
        if (tick < lead.next) return;
        List<Mannequin> ms = new ArrayList<>();
        g.sort((a, b) -> a.id.compareTo(b.id));
        for (Npc n : g) if (plugin.getServer().getEntity(n.id) instanceof Mannequin m) ms.add(m);
        if (ms.size() < 2 || !playerNear(ms.get(0).getLocation(), 24)) {
            lead.next = tick + 100;
            return;
        }
        if (lead.script < 0 || lead.line >= TALKS[lead.script].length) {
            if (lead.script >= 0) {            // Pointe: alle lachen
                for (int i = 0; i < ms.size(); i++) {
                    Npc n = g.get(i);
                    Mannequin m = ms.get(i);
                    if (RND.nextInt(3) > 0) {
                        say(n, m, any(LAUGH), CHEER, 50, false);
                        m.setPose(Pose.SNEAKING, true);
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (m.isValid()) m.setPose(Pose.STANDING, false);
                        }, 12L);
                    }
                }
                lead.script = -1;
                lead.line = 0;
                lead.next = tick + 300 + RND.nextInt(400);
                return;
            }
            lead.script = RND.nextInt(TALKS.length);
            lead.line = 0;
        }
        int speaker = lead.line % ms.size();
        Npc sn = g.get(speaker);
        Mannequin sm = ms.get(speaker);
        say(sn, sm, TALKS[lead.script][lead.line], BUBBLE, 65, false);
        if (RND.nextInt(3) > 0) {
            if (RND.nextBoolean()) sm.swingMainHand();
            else sm.swingOffHand();
        }
        for (int i = 0; i < ms.size(); i++) {
            if (i == speaker) {
                lookAt(g.get(i), ms.get((i + 1) % ms.size()).getEyeLocation(), 70);
            } else {
                lookAt(g.get(i), sm.getEyeLocation(), 70);
            }
        }
        lead.line++;
        lead.next = tick + 60 + TALKS[lead.script][Math.max(0, lead.line - 1)].length() * 2;
    }

    // ---------- Personal (Kasse, Croupier, Losbude, Chip-Shop, Mia, Barkeeper) ----------

    private void staff(Npc n, Mannequin m) {
        endUse(n, m);
        CasinoNpc.Role r = n.role;
        if (r == null) {      // Barkeeper
            if (greet(n, m, "Was darf's sein?", "Guten Abend! Etwas zu trinken?", "Für VIPs geht die erste Runde aufs Haus.")) {
                m.swingMainHand();
                return;
            }
            if (tick >= n.next) {
                n.next = tick + 200 + RND.nextInt(300);
                hold(m, "leer", ItemUseAnimation.BRUSH);     // poliert ein Glas
                n.phaseEnd = tick + 80;
            }
            return;
        }
        switch (r) {
            case KASSE -> {
                if (greet(n, m, "Willkommen! Chips gefällig?", "Guten Abend. Wie viele Chips dürfen's sein?",
                        "Hallo! Coins rein, Chips raus.")) {
                    m.swingMainHand();
                    return;
                }
                if (tick >= n.next) {                      // zählt Scheine (Pinsel-Bewegung), legt sie ab
                    n.next = tick + 160 + RND.nextInt(240);
                    hold(m, "geld", ItemUseAnimation.BRUSH);
                    n.phaseEnd = tick + 70 + RND.nextInt(40);
                    if (RND.nextInt(4) == 0) say(n, m, any("… achtzig, neunzig, hundert.", "*zählt*", "Stimmt auf den Chip genau."), BUBBLE, 60, true);
                }
            }
            case LOSBUDE -> {
                if (heldKind(m) == null) hold(m, "lose", null);
                if (greet(n, m, "Lose, Lose! Heute schon Glück gehabt?", "Komm ran, jedes Los ein Treffer … fast!", "Na, ein Los?")) {
                    hold(m, "lose", ItemUseAnimation.TRIDENT);   // Lose hochhalten
                    n.phaseEnd = tick + 30;
                    return;
                }
                if (tick >= n.next) {
                    n.next = tick + 300 + RND.nextInt(300);
                    if (playerNear(m.getLocation(), 10)) {
                        hold(m, "lose", ItemUseAnimation.TRIDENT);
                        n.phaseEnd = tick + 30;
                        say(n, m, any("Lose! Frische Lose!", "Hauptgewinn noch nicht gezogen!", "Wer wagt, gewinnt!",
                                "Drei Felder, ein Glückstreffer!"), CHEER, 70, false);
                    }
                }
            }
            case CHIPSHOP -> {
                if (greet(n, m, "Schau dich ruhig um.", "Neue Hüte sind da!", "Für Chips gibt's hier das Schönste.")) m.swingMainHand();
            }
            case CROUPIER -> {
                if (greet(n, m, "Faites vos jeux!", "Bonsoir. Ein Platz ist noch frei.")) m.swingMainHand();
            }
            case MIA -> {
                if (tick >= n.next) {
                    n.next = tick + 40 + RND.nextInt(40);
                    if (RND.nextBoolean()) m.swingMainHand();    // wippt zur Musik
                    else m.swingOffHand();
                }
            }
            default -> {
            }
        }
    }

    /** Runde am Tisch entschieden (CasinoPlay): Zuschauer in der Nähe jubeln oder leiden mit, Pechvögel betteln. */
    public void onRound(UUID player, boolean won, boolean big) {
        Player p = plugin.getServer().getPlayer(player);
        if (p == null) return;
        for (Npc n : npcs.values()) {
            if (n.act == null || n.anchor == null || n.anchor.getWorld() != p.getWorld()) continue;
            double d = n.anchor.distanceSquared(p.getLocation());
            if (!(plugin.getServer().getEntity(n.id) instanceof Mannequin m)) continue;
            if (n.act == Act.ZUSCHAUER && d < 81) {
                lookAt(n, p.getEyeLocation(), 80);
                if (won) {
                    hold(m, "leer", ItemUseAnimation.TRIDENT);   // Arm hoch
                    n.phaseEnd = tick + 30;
                    m.swingOffHand();
                    say(n, m, big ? any("WAHNSINN!", "Jackpot-Stimmung!", "Ich wusste es!") : any("Jaaa!", "Na also!", "Glückwunsch!"), CHEER, 60, false);
                } else if (RND.nextBoolean()) {
                    say(n, m, any("Ohh …", "Knapp daneben.", "Nächstes Mal!"), BUBBLE, 50, false);
                }
            } else if (big && won && d < 64 && (n.act == Act.GRUPPE || n.act == Act.TRINKEN || n.act == Act.BANK || n.act == Act.WAND)) {
                lookAt(n, p.getEyeLocation(), 60);
                if (RND.nextInt(3) == 0) say(n, m, any("Wow!", "Nicht schlecht!", "Hast du das gesehen?"), BUBBLE, 50, false);
            } else if (big && won && d < 100 && n.act == Act.VERZWEIFELT) {
                lookAt(n, p.getEyeLocation(), 80);
                say(n, m, any("Gib mir was ab!", "Nur ein paar Chips … bitte!", "Warum immer die anderen …"), RADIO, 70, true);
            }
        }
    }

    // =================================================================================================================
    // Rechtsklick, Schutz
    // =================================================================================================================

    public boolean isPerson(Entity e) {
        return e.getPersistentDataContainer().has(actKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!isPerson(e.getRightClicked()) || !(e.getRightClicked() instanceof Mannequin m)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Npc n = npcs.get(m.getUniqueId());
        if (n == null) {
            register(m);
            n = npcs.get(m.getUniqueId());
            if (n == null) return;
        }
        Player p = e.getPlayer();
        lookAt(n, p.getEyeLocation(), 80);
        say(n, m, click(n.act, n.variant), BUBBLE, 90, false);
        if (n.act != null && !n.act.sits() && RND.nextBoolean()) m.swingMainHand();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (isPerson(e.getEntity()) || e.getEntity().getPersistentDataContainer().has(seatKey, PersistentDataType.STRING)) e.setCancelled(true);
    }

    // =================================================================================================================
    // Befehl, Casino-Admin
    // =================================================================================================================

    /** /casino leute &lt;tätigkeit&gt; [variante|anzahl] | entfernen | varianten */
    public void command(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("entfernen")) {
            Entity near = null;
            double best = 25;
            for (Entity e : p.getNearbyEntities(5, 3, 5)) {
                if (!isPerson(e)) continue;
                double d = e.getLocation().distanceSquared(p.getLocation());
                if (d < best) {
                    best = d;
                    near = e;
                }
            }
            if (near == null) {
                Msg.send(p, "<red>Keine Figur im Umkreis von 5 Blöcken.");
                return;
            }
            remove(near.getUniqueId().toString());
            Msg.send(p, "<green>Figur entfernt.");
            return;
        }
        if (sub.equals("varianten")) {
            StringBuilder b = new StringBuilder();
            for (Variant v : variants.values()) b.append(b.isEmpty() ? "" : ", ").append(v.id()).append(" (").append(v.name()).append(")");
            Msg.send(p, "<gray>Figuren: <white>" + b);
            return;
        }
        Act act = Act.of(sub);
        if (act == null) {
            StringBuilder ids = new StringBuilder();
            for (Act a : Act.values()) ids.append(ids.isEmpty() ? "" : "|").append(a.id);
            Msg.send(p, "<red>Nutzung: /casino leute <" + ids + "> [Variante|Anzahl] | entfernen | varianten");
            return;
        }
        String extra = args.length > 2 ? args[2] : null;
        int count = 0;
        if (extra != null && extra.matches("\\d")) {
            count = Integer.parseInt(extra);
            extra = null;
        }
        if (extra != null && find(extra) == null) {
            Msg.send(p, "<red>Unbekannte Variante „" + extra + "“. Liste: /casino leute varianten");
            return;
        }
        List<Entity> made = spawn(act, p.getLocation(), extra, count);
        if (made.isEmpty()) {
            Msg.send(p, "<red>Keine Figur verfügbar (Grafik-Pack-Liste people.txt fehlt?).");
            return;
        }
        StringBuilder names = new StringBuilder();
        for (Entity e : made) {
            Npc n = npcs.get(e.getUniqueId());
            names.append(names.isEmpty() ? "" : ", ").append(n == null ? "?" : n.name);
        }
        Msg.send(p, "<green>" + act.title + ": " + names + ". Ändern im Casino-Admin (Leute), entfernen: /casino leute entfernen.");
    }

    public List<String> tab(String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 2) {
            for (Act a : Act.values()) out.add(a.id);
            out.add("entfernen");
            out.add("varianten");
        } else if (args.length == 3 && Act.of(args[1]) != null) {
            Act a = Act.of(args[1]);
            if (a == Act.GRUPPE) out.addAll(List.of("2", "3", "4", "5"));
            for (Variant v : pool(a)) out.add(v.name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    @Override
    public List<Piece> pieces() {
        List<Piece> out = new ArrayList<>();
        for (World w : plugin.getServer().getWorlds()) {
            for (Mannequin m : w.getEntitiesByClass(Mannequin.class)) {
                Act a = Act.of(m.getPersistentDataContainer().getOrDefault(actKey, PersistentDataType.STRING, ""));
                if (a == null) continue;
                Variant v = variant(m.getPersistentDataContainer().get(varKey, PersistentDataType.STRING));
                Location at = anchor(m.getPersistentDataContainer().get(anchorKey, PersistentDataType.STRING), w);
                if (at == null) at = m.getLocation();
                double len = a == Act.STREIFE || a == Act.TELEFON
                        ? m.getPersistentDataContainer().getOrDefault(lenKey, PersistentDataType.DOUBLE, a == Act.STREIFE ? 6.0 : 2.5) : Double.NaN;
                out.add(new Piece(m.getUniqueId().toString(), (v == null ? "Gast" : v.name()) + " · " + a.title, at, at.getYaw(), len,
                        m.getPersistentDataContainer().has(groupKey, PersistentDataType.STRING) ? "in einer Gruppe" : a.desc, false));
            }
        }
        out.sort(java.util.Comparator.comparing(Piece::name));
        return out;
    }

    private Mannequin byId(String id) {
        try {
            return plugin.getServer().getEntity(UUID.fromString(id)) instanceof Mannequin m && isPerson(m) ? m : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String move(String id, double dx, double dy, double dz, float dyaw) {
        Mannequin m = byId(id);
        if (m == null) return "Figur nicht gefunden (Chunk nicht geladen?).";
        Npc n = npcs.get(m.getUniqueId());
        Location a = anchor(m.getPersistentDataContainer().get(anchorKey, PersistentDataType.STRING), m.getWorld());
        if (a == null) a = m.getLocation();
        a.add(dx, dy, dz);
        a.setYaw(Placeable.norm(a.getYaw() + dyaw));
        m.getPersistentDataContainer().set(anchorKey, PersistentDataType.STRING, anchor(a));
        ArmorStand seat = seatOf(m);
        if (seat != null) {
            seat.removePassenger(m);
            seat.remove();
        }
        m.teleport(a, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        m.setBodyYaw(a.getYaw());
        if (n != null) {
            n.anchor = a;
            n.pos = 0;
            n.dir = 1;
        }
        Act act = Act.of(m.getPersistentDataContainer().getOrDefault(actKey, PersistentDataType.STRING, ""));
        if (act != null && act.sits()) seat(m, a);
        return null;
    }

    @Override
    public String resize(String id, double delta) {
        Mannequin m = byId(id);
        if (m == null) return "Figur nicht gefunden.";
        Act act = Act.of(m.getPersistentDataContainer().getOrDefault(actKey, PersistentDataType.STRING, ""));
        if (act != Act.STREIFE && act != Act.TELEFON) return "Nur Streife und Telefonierende haben eine Weglänge.";
        double len = m.getPersistentDataContainer().getOrDefault(lenKey, PersistentDataType.DOUBLE, act == Act.STREIFE ? 6.0 : 2.5);
        len = Math.max(1, Math.min(30, len + delta));
        m.getPersistentDataContainer().set(lenKey, PersistentDataType.DOUBLE, len);
        Npc n = npcs.get(m.getUniqueId());
        if (n != null) n.len = len;
        return null;
    }

    @Override
    public double sizeStep() {
        return 1;
    }

    @Override
    public String remove(String id) {
        Mannequin m = byId(id);
        if (m == null) return "Figur nicht gefunden (Chunk nicht geladen?).";
        Npc n = npcs.remove(m.getUniqueId());
        if (n != null) clearBubble(n, m);
        ArmorStand seat = seatOf(m);
        if (seat != null) seat.remove();
        for (Entity p : m.getPassengers()) p.remove();
        m.remove();
        return null;
    }

    @Override
    public String variant(String id, int dir) {
        Mannequin m = byId(id);
        if (m == null) return "Figur nicht gefunden.";
        Act act = Act.of(m.getPersistentDataContainer().getOrDefault(actKey, PersistentDataType.STRING, ""));
        List<Variant> pool = act == null ? group("gast") : act.guard() ? group("security") : group("gast");
        if (pool.isEmpty()) return "Keine Varianten.";
        String cur = m.getPersistentDataContainer().get(varKey, PersistentDataType.STRING);
        int i = 0;
        for (int k = 0; k < pool.size(); k++) if (pool.get(k).id().equals(cur)) i = k;
        Variant v = pool.get(Math.floorMod(i + dir, pool.size()));
        m.getPersistentDataContainer().set(varKey, PersistentDataType.STRING, v.id());
        m.getPersistentDataContainer().set(skinKey, PersistentDataType.STRING, v.skin());
        m.getPersistentDataContainer().set(slimKey, PersistentDataType.BYTE, (byte) (v.slim() ? 1 : 0));
        m.setProfile(profile(v.name(), v.skin(), v.slim()));
        m.getEquipment().setHelmet(v.hat() == null ? null : model(v.hat()));
        register(m);
        return null;
    }

    @Override
    public String variantName(String id) {
        Mannequin m = byId(id);
        if (m == null) return null;
        Variant v = variant(m.getPersistentDataContainer().get(varKey, PersistentDataType.STRING));
        return v == null ? "?" : v.name();
    }

    /** Personal-Figur (CasinoNpc, Barkeeper): Variante wechseln, Skin-Pfad mitführen. */
    public void restyle(Mannequin m, String name, String skin, boolean slim, String hat) {
        m.getPersistentDataContainer().set(skinKey, PersistentDataType.STRING, skin);
        m.getPersistentDataContainer().set(slimKey, PersistentDataType.BYTE, (byte) (slim ? 1 : 0));
        m.setProfile(profile(name, skin, slim));
        m.getEquipment().setHelmet(hat == null ? null : model(hat));
        register(m);
    }

    public int count() {
        return npcs.size();
    }

    /** Selbsttest: Varianten-Liste da, jede Tätigkeit hat einen Pool und Klick-Sätze. */
    public List<String> selfTest() {
        List<String> err = new ArrayList<>();
        if (variants.size() < 20) err.add("people.txt: nur " + variants.size() + " Figuren");
        if (group("security").size() < 3) err.add("weniger als 3 Security-Varianten");
        if (group("gast").size() < 8) err.add("weniger als 8 Gäste");
        for (Act a : Act.values()) {
            if (pool(a).isEmpty()) err.add("kein Pool für " + a.id);
            if (click(a, null) == null) err.add("kein Satz für " + a.id);
        }
        return err;
    }
}
