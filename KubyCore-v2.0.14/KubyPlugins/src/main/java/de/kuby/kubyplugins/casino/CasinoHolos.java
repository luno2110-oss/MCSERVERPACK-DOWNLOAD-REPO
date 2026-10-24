package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hologramme im Casino wie im Clip: Jackpot über dem Podest (mit Ziehung, Teilnehmern, letztem Gewinner und der
 * persönlichen Zeile "Du bist dabei ✓"), die Ranking-Tafel an der Wand und das "CASINO"-Hologramm am Eingang.
 * Admins stellen sie mit /casino holo &lt;jackpot|ranking|eingang&gt; auf.
 */
public final class CasinoHolos implements Listener {

    public static final List<String> KINDS = List.of("jackpot", "ranking", "eingang");
    private static final Key HOLO = Key.key("kuby", "holo");
    private static final Key TITLE = Key.key("kuby", "title");
    private static final Key CHIPS = Key.key("kuby", "k/i-5");
    private static final TextColor GRAY = TextColor.color(0x9A9AA4);
    private static final TextColor LIGHT = TextColor.color(0xE8E4DC);
    private static final TextColor YELLOW = TextColor.color(0xF6E23A);
    private static final TextColor ORANGE = TextColor.color(0xF2A02A);
    private static final TextColor GREEN = TextColor.color(0x5AE878);
    private static final int[] JACKPOT_LOGO = {0xF0303E, 0xF2503A, 0xF47034, 0xF6902E, 0xF8B028, 0xFACC24, 0xFCDC20};
    public static final int[] CASINO_LOGO = {0xF23A6A, 0xF24A4A, 0xF46A3A, 0xF68A2A, 0xF8B020, 0xFAD020};
    private static final int[] RANK_LOGO = {0xF8B028, 0xF8B028, 0xF6A02A, 0xF6A02A, 0xF4902C, 0xF4902C, 0xF4902C,
            0xF2802E, 0xF2802E, 0xF2802E, 0xF07030, 0xF07030, 0xF07030, 0xF07030};

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final Map<String, String> places = new LinkedHashMap<>();
    private final Map<String, TextDisplay> shown = new HashMap<>();
    private TextDisplay inLine;
    private TextDisplay outLine;
    private final Map<UUID, Boolean> viewer = new HashMap<>();
    private final Map<UUID, Long> greeted = new HashMap<>();
    private int ticks;

    public CasinoHolos(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "casino-hologramme.yml");
        this.tag = new NamespacedKey(plugin, "casino_holo");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : KINDS) {
            String v = yml.getString(k);
            if (v != null) places.put(k, v);
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, String> e : places.entrySet()) yml.set(e.getKey(), e.getValue());
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("casino-hologramme.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private static Location parse(String raw) {
        if (raw == null) return null;
        String[] p = raw.split(";");
        World w = Bukkit.getWorld(p[0]);
        if (w == null || p.length < 5) return null;
        try {
            Location l = new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
            l.setYaw(Float.parseFloat(p[4]));
            return l;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // =================================================================================================================
    // Aufbau
    // =================================================================================================================

    public void respawnAll() {
        for (String k : KINDS) build(k);
    }

    private void remove(String kind) {
        Location at = parse(places.get(kind));
        shown.remove(kind);
        if (kind.equals("jackpot")) {
            inLine = null;
            outLine = null;
            viewer.clear();
        }
        if (at == null || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return;
        for (Entity e : at.getWorld().getNearbyEntities(at, 4, 6, 4)) {
            if (kind.equals(e.getPersistentDataContainer().get(tag, PersistentDataType.STRING))) e.remove();
        }
    }

    private TextDisplay spawn(String kind, Location at, Component text, float scale, boolean billboard, float yaw) {
        return at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(billboard ? Display.Billboard.VERTICAL : Display.Billboard.FIXED);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setLineWidth(400);
            d.setPersistent(false);
            Quaternionf q = billboard ? new Quaternionf() : new Quaternionf().rotateY((float) Math.toRadians(-yaw));
            d.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(scale, scale, scale), new Quaternionf()));
            d.getPersistentDataContainer().set(tag, PersistentDataType.STRING, kind);
        });
    }

    private void build(String kind) {
        remove(kind);
        Location at = parse(places.get(kind));
        if (at == null || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return;
        float yaw = at.getYaw();
        Location base = at.clone();
        base.setYaw(0);
        switch (kind) {
            case "jackpot" -> {
                spawn(kind, base.clone().add(0, 3.05, 0), jackpotTitle(), 0.62f, true, 0);
                shown.put("jackpot", spawn(kind, base.clone().add(0, 2.62, 0), Component.empty(), 1.35f, true, 0));
                shown.put("jackpot_body", spawn(kind, base.clone().add(0, 1.3, 0), Component.empty(), 0.8f, true, 0));
                inLine = spawn(kind, base.clone().add(0, 0.98, 0), Component.text("✦ ", TextColor.color(0xF23A5A))
                        .append(Component.text("Du bist dabei ✓", GREEN).decorate(TextDecoration.BOLD))
                        .append(Component.text(" ✦", YELLOW)), 0.95f, true, 0);
                outLine = spawn(kind, base.clone().add(0, 0.98, 0), Component.text("Spiel eine Runde, dann bist du dabei", GRAY),
                        0.8f, true, 0);
                inLine.setVisibleByDefault(false);
                outLine.setVisibleByDefault(false);
                viewer.clear();
                updateJackpot();
            }
            case "ranking" -> {
                shown.put("ranking", spawn(kind, base, Component.empty(), 0.62f, false, yaw));
                TextDisplay d = shown.get("ranking");
                d.setBackgroundColor(Color.fromARGB(70, 0, 0, 0));
                updateRanking();
            }
            case "eingang" -> {
                spawn(kind, base.clone().add(0, 3.0, 0), CrashScreen.logo("CASINO", CASINO_LOGO), 1.4f, true, 0);
                spawn(kind, base.clone().add(0, 0.9, 0), entranceBody(), 1.0f, true, 0);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        for (String k : KINDS) {
            Location at = parse(places.get(k));
            if (at == null || !at.getWorld().equals(e.getWorld())) continue;
            if ((at.getBlockX() >> 4) != e.getChunk().getX() || (at.getBlockZ() >> 4) != e.getChunk().getZ()) continue;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> build(k), 5L);
        }
    }

    // =================================================================================================================
    // Inhalte
    // =================================================================================================================

    private static Component icon(KitData.SmallIcon i) {
        return Component.text(String.valueOf(i.c)).font(HOLO).color(NamedTextColor.WHITE);
    }

    /** Vanilla-Item als Zeilen-Icon (Zeichen aus kuby:k/i0 bzw. kuby:holo). */
    private static Component item(char c) {
        return Component.text(String.valueOf(c)).font(HOLO).color(NamedTextColor.WHITE);
    }

    private static Component dot() {
        return Component.text(" · ", NamedTextColor.DARK_GRAY);
    }

    public static Component jackpotTitle() {
        TextComponent.Builder b = Component.text();
        b.append(Component.text(String.valueOf(KitData.SmallIcon.C_PINK.c)).font(CHIPS).color(NamedTextColor.WHITE));
        b.append(Component.text(de.kuby.kubyplugins.ui.Canvas.titleChars(" ", false)).font(TITLE));
        b.append(CrashScreen.logo("JACKPOT", JACKPOT_LOGO));
        b.append(Component.text(de.kuby.kubyplugins.ui.Canvas.titleChars(" ", false)).font(TITLE));
        b.append(Component.text(String.valueOf(KitData.SmallIcon.C_PINK.c)).font(CHIPS).color(NamedTextColor.WHITE));
        return b.build();
    }

    private void updateJackpot() {
        TextDisplay amount = shown.get("jackpot");
        TextDisplay body = shown.get("jackpot_body");
        if (amount == null || !amount.isValid() || body == null || !body.isValid()) return;
        amount.text(jackpotAmount(plugin.casino().jackpot()));
        var draw = plugin.jackpotDraw();
        body.text(draw == null ? jackpotBody(null, null, 0, null, 0)
                : jackpotBody(draw.nextDrawLong(), draw.timeLeft(), draw.participantCount(), draw.lastWinner(), draw.lastAmount()));
    }

    public static Component jackpotAmount(long chips) {
        return Component.text(CasinoText.chips(chips), YELLOW).decorate(TextDecoration.BOLD);
    }

    public static Component jackpotBody(String next, String left, int players, String winner, long amount) {
        TextComponent.Builder b = Component.text();
        b.append(Component.text("Ein Topf, ein Gewinner", GRAY)).append(Component.newline());
        b.append(icon(KitData.SmallIcon.C_WHITE)).append(dot()).append(Component.text("Wächst mit jedem Einsatz im Casino", LIGHT))
                .append(Component.newline());
        b.append(icon(KitData.SmallIcon.C_PINK)).append(dot())
                .append(Component.text("Jeder, der diese Woche gespielt hat, ist dabei", LIGHT)).append(Component.newline());
        if (next != null) {
            b.append(item(KitData.Icon.CLOCK.c16)).append(dot()).append(Component.text("Ziehung: ", LIGHT))
                    .append(Component.text(next, YELLOW)).append(dot())
                    .append(Component.text("noch " + left, LIGHT)).append(Component.newline());
            b.append(icon(KitData.SmallIcon.S_STAR)).append(dot())
                    .append(Component.text(CasinoText.num(players), NamedTextColor.WHITE))
                    .append(Component.text(players == 1 ? " Spieler ist schon dabei" : " Spieler sind schon dabei", LIGHT));
            if (winner != null) {
                b.append(Component.newline()).append(item(KitData.Icon.TOTEM.c16)).append(dot()).append(Component.text("Letzter Gewinner: ", GRAY))
                        .append(Component.text(winner, NamedTextColor.WHITE)).append(Component.text("  "))
                        .append(Component.text(CasinoText.chips(amount), YELLOW));
            }
        }
        return b.build();
    }

    /** Persönliche Zeile: jeder sieht "Du bist dabei ✓" oder den Hinweis, je nachdem ob er diese Woche gespielt hat. */
    private void updateViewers() {
        if (inLine == null || !inLine.isValid() || outLine == null || !outLine.isValid()) return;
        Location at = inLine.getLocation();
        var draw = plugin.jackpotDraw();
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) > 64 * 64) continue;
            boolean in = draw != null && draw.isParticipant(p.getUniqueId());
            Boolean was = viewer.get(p.getUniqueId());
            if (was != null && was == in) continue;
            viewer.put(p.getUniqueId(), in);
            if (in) {
                p.showEntity(plugin, inLine);
                p.hideEntity(plugin, outLine);
            } else {
                p.showEntity(plugin, outLine);
                p.hideEntity(plugin, inLine);
            }
        }
        // Wer nah vorbeigeht, liest einmal die Erklärung (wie im Clip)
        long now = System.currentTimeMillis();
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) > 6 * 6) continue;
            Long last = greeted.get(p.getUniqueId());
            if (last != null && now - last < 30 * 60_000L) continue;
            greeted.put(p.getUniqueId(), now);
            p.sendMessage(Msg.parse("<#F2B829><b>JACKPOT</b></#F2B829> <dark_gray>▶</dark_gray> <white>Ein Teil von jedem Einsatz wandert in den "
                    + "<yellow>Tresor</yellow>. Wer ihn knackt, nimmt alles mit."));
        }
    }

    /** Spieler nach dem Wiedereinloggen neu einordnen. */
    public void forget(Player p) {
        viewer.remove(p.getUniqueId());
    }

    private void updateRanking() {
        TextDisplay d = shown.get("ranking");
        if (d == null || !d.isValid()) return;
        List<String> names = new java.util.ArrayList<>();
        List<Long> values = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Long> e : plugin.casinoLog().top(5)) {
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            names.add(name == null ? "?" : name);
            values.add(e.getValue());
        }
        d.text(rankingText(names, values));
    }

    public static Component rankingText(List<String> names, List<Long> values) {
        TextComponent.Builder b = Component.text();
        b.append(Component.text(String.valueOf(KitData.SmallIcon.C_PINK.c)).font(HOLO).color(NamedTextColor.WHITE)).append(Component.text(" "));
        b.append(Component.text(de.kuby.kubyplugins.util.SmallCaps.of("casino ranking"), ORANGE).decorate(TextDecoration.BOLD));
        b.append(Component.text(" ")).append(Component.text(String.valueOf(KitData.SmallIcon.C_PINK.c)).font(HOLO).color(NamedTextColor.WHITE));
        b.append(Component.newline()).append(Component.text("Top Casino-Spieler", GRAY)).append(Component.newline());
        TextColor[] medal = {TextColor.color(0xFFC83A), TextColor.color(0xC8CCD8), TextColor.color(0xE0884A), GRAY, GRAY};
        for (int i = 0; i < 5; i++) {
            b.append(Component.newline());
            if (i >= names.size()) {
                b.append(Component.text("#" + (i + 1) + " —", GRAY));
                b.append(Component.newline()).append(Component.text(" ", GRAY));
                continue;
            }
            if (i < 3) b.append(Component.text("● ", medal[i]));
            b.append(Component.text("#" + (i + 1) + " ", medal[i]).decorate(TextDecoration.BOLD))
                    .append(Component.text(names.get(i), NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));
            long v = values.get(i);
            b.append(Component.newline()).append(Component.text((v >= 0 ? "+" : "") + CasinoText.num(v) + " Coins",
                    v >= 0 ? GREEN : TextColor.color(0xFF5A5A)));
        }
        b.append(Component.newline()).append(Component.newline()).append(Component.text("Mehr Statistiken: ", GRAY))
                .append(Component.text("/casino stats", YELLOW));
        return b.build();
    }

    public static Component entranceBody() {
        TextComponent.Builder b = Component.text();
        b.append(Component.text("Willkommen! schön, dass du da bist", TextColor.color(0xB8B8C0))).append(Component.newline());
        b.append(Component.newline());
        b.append(icon(KitData.SmallIcon.S_COIN)).append(Component.text(" Tausche deine ", LIGHT)).append(Component.text("Coins", ORANGE))
                .append(Component.text(" gegen ", LIGHT)).append(Component.text("Chips", YELLOW)).append(Component.newline());
        b.append(icon(KitData.SmallIcon.C_PINK)).append(Component.text(" Mit Chips spielst du an allen Tischen", LIGHT)).append(Component.newline());
        b.append(Component.text("Deine Gewinne tauschst du jederzeit zurück", LIGHT)).append(Component.newline());
        b.append(Component.newline());
        b.append(Component.text("✦ ", TextColor.color(0xF23A5A))).append(Component.text("Viel Spaß und viel Glück", LIGHT))
                .append(Component.text(" ✦", YELLOW));
        return b.build();
    }

    private void tick() {
        ticks++;
        updateJackpot();
        updateViewers();
        if (ticks % 60 == 0) updateRanking();
    }

    // =================================================================================================================
    // Befehl
    // =================================================================================================================

    /** /casino holo &lt;jackpot|ranking|eingang&gt; [entfernen] */
    public void command(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins.");
            return;
        }
        String kind = args.length > 1 ? args[1].toLowerCase() : "";
        if (!KINDS.contains(kind)) {
            Msg.send(p, "<red>Nutzung: /casino holo <jackpot|ranking|eingang> [entfernen]");
            return;
        }
        if (args.length > 2 && args[2].equalsIgnoreCase("entfernen")) {
            remove(kind);
            places.remove(kind);
            save();
            Msg.send(p, "<green>Hologramm entfernt.");
            return;
        }
        Location at;
        if (kind.equals("ranking")) {
            RayTraceResult hit = p.rayTraceBlocks(8);
            if (hit == null || hit.getHitBlockFace() == null || Math.abs(hit.getHitBlockFace().getDirection().getY()) > 0.5) {
                Msg.send(p, "<red>Schau auf die Wand, an die die Tafel soll (Mitte der Tafel).");
                return;
            }
            Vector n = hit.getHitBlockFace().getDirection();
            at = hit.getHitPosition().add(n.clone().multiply(0.05)).toLocation(p.getWorld());
            at.setYaw((float) Math.toDegrees(Math.atan2(-n.getX(), n.getZ())));
            // Tafel hängt mit ihrer Mitte am Blickpunkt: Text wächst nach oben, also etwas tiefer ansetzen
            at.add(0, -1.0, 0);
        } else {
            at = p.getLocation().clone();
        }
        remove(kind);
        places.put(kind, at.getWorld().getName() + ";" + at.getX() + ";" + at.getY() + ";" + at.getZ() + ";" + at.getYaw());
        save();
        build(kind);
        Msg.send(p, "<green>Hologramm „" + kind + "“ aufgestellt. Entfernen: /casino holo " + kind + " entfernen");
    }

    public void shutdown() {
        for (String k : KINDS) remove(k);
    }
}
