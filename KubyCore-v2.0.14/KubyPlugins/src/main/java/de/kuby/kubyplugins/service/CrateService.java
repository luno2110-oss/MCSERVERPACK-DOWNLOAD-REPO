package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kisten am Spawn: ein Block wird mit /keys kiste set &lt;id&gt; zur Kiste. Rechtsklick öffnet mit einem virtuellen
 * Schlüssel (KeyService) und zeigt den Gewinn als drehende Anzeige über der Kiste, Schleichen + Rechtsklick überspringt
 * die Animation, Linksklick zeigt den Inhalt mit Chancen. Kisten und Gewinne je Modus unter crates.&lt;modus&gt;.&lt;id&gt;
 * (name, key, rewards: "Gewicht;Belohnung;Anzeige"), Orte in crates.yml.
 */
public final class CrateService implements Listener {

    /** Ein Gewinn: Gewicht, Art (coins, tokens, jewels, key, cosmetic, pet, furniture, item, cmd), Wert, Menge, Name. */
    public record Reward(int weight, String kind, String value, long amount, String name) {}

    public record Crate(String mode, String id, String name, String key, List<Reward> rewards) {
        public int total() {
            int t = 0;
            for (Reward r : rewards) t += r.weight();
            return t;
        }

        public double chance(Reward r) {
            return total() == 0 ? 0 : 100.0 * r.weight() / total();
        }
    }

    private final KubyPlugins plugin;
    private final File file;
    /** Block "welt;x;y;z" -> modus:id. */
    private final Map<String, String> places = new LinkedHashMap<>();
    private final Map<String, TextDisplay> labels = new HashMap<>();
    private final Set<String> busy = new HashSet<>();
    private final Set<UUID> opening = new HashSet<>();
    /** Wer gerade wo eine Vorschau offen hat (für "Öffnen" aus der Vorschau). */
    private final Map<UUID, String> lastCrateAt = new HashMap<>();

    public CrateService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "crates.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String line : y.getStringList("orte")) {
            int cut = line.lastIndexOf(';');
            if (cut > 0) places.put(line.substring(0, cut), line.substring(cut + 1));
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::spawnLabels, 40L);
    }

    // =================================================================================================================
    // Katalog
    // =================================================================================================================

    public List<Crate> crates(String mode) {
        List<Crate> out = new ArrayList<>();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("crates." + mode);
        if (s == null) return out;
        for (String id : s.getKeys(false)) {
            Crate c = crate(mode, id);
            if (c != null) out.add(c);
        }
        return out;
    }

    public Crate crate(String mode, String id) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("crates." + mode + "." + id);
        if (c == null) return null;
        List<Reward> rewards = new ArrayList<>();
        for (String raw : c.getStringList("rewards")) {
            String[] p = raw.split(";", 3);
            if (p.length < 2) continue;
            try {
                Reward r = parse(Integer.parseInt(p[0].trim()), p[1].trim(), p.length > 2 ? p[2].trim() : null);
                if (r != null) rewards.add(r);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Ungültiger Kisten-Gewinn: " + raw);
            }
        }
        return new Crate(mode, id, c.getString("name", id), c.getString("key", id).toLowerCase(Locale.ROOT), rewards);
    }

    private Reward parse(int weight, String s, String name) {
        String[] p = s.split(":");
        String kind = p[0].toLowerCase(Locale.ROOT);
        String value = p.length > 1 ? p[1] : "";
        long amount = 1;
        try {
            switch (kind) {
                case "coins", "tokens", "jewels" -> {
                    amount = Long.parseLong(value);
                    value = "";
                }
                case "key", "item" -> amount = p.length > 2 ? Long.parseLong(p[2]) : 1;
                case "cmd" -> value = s.substring(4);
                case "cosmetic", "pet", "furniture" -> {
                }
                default -> {
                    return null;
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
        Reward r = new Reward(weight, kind, value, amount, null);
        return new Reward(weight, kind, value, amount, name != null && !name.isEmpty() ? name : autoName(r));
    }

    private String autoName(Reward r) {
        return switch (r.kind()) {
            case "coins" -> Msg.coins(r.amount()) + " Coins";
            case "tokens" -> r.amount() + (r.amount() == 1 ? " Token" : " Tokens");
            case "jewels" -> r.amount() + (r.amount() == 1 ? " Juwel" : " Juwelen");
            case "key" -> {
                KeyService.KeyType t = plugin.keys().type(r.value());
                yield (r.amount() > 1 ? r.amount() + "× " : "") + (t == null ? r.value() : t.full());
            }
            case "cosmetic" -> {
                CosmeticService.Item it = plugin.cosmetics().find(r.value());
                yield it == null ? r.value() : it.label();
            }
            case "item" -> r.amount() + "× " + r.value().toLowerCase(Locale.ROOT).replace('_', ' ');
            default -> r.value();
        };
    }

    /** Farbe nach Chance: häufig grau, selten blau, episch lila, sehr selten gold. */
    public static TextColor colorOf(double chance) {
        if (chance >= 15) return TextColor.color(0xD8D8E0);
        if (chance >= 5) return TextColor.color(0x64D8FF);
        if (chance >= 1.5) return TextColor.color(0xD08CFF);
        return TextColor.color(0xF8D35A);
    }

    // =================================================================================================================
    // Orte
    // =================================================================================================================

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private Location centerOf(String k) {
        String[] p = k.split(";");
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        return new Location(w, Integer.parseInt(p[1]) + 0.5, Integer.parseInt(p[2]), Integer.parseInt(p[3]) + 0.5);
    }

    public String set(Block b, String mode, String id) {
        if (crate(mode, id) == null) return "Diese Kiste gibt es in der Config nicht (crates." + mode + "." + id + ").";
        String k = key(b);
        places.put(k, mode + ":" + id);
        save();
        label(k);
        return null;
    }

    public boolean remove(Block b) {
        String k = key(b);
        if (places.remove(k) == null) return false;
        TextDisplay t = labels.remove(k);
        if (t != null) t.remove();
        save();
        return true;
    }

    public Map<String, String> places() {
        return new LinkedHashMap<>(places);
    }

    private Crate at(Block b) {
        if (b == null) return null;
        String v = places.get(key(b));
        if (v == null) return null;
        String[] p = v.split(":", 2);
        return crate(p[0], p[1]);
    }

    private void spawnLabels() {
        for (String k : places.keySet()) label(k);
    }

    private void label(String k) {
        TextDisplay old = labels.remove(k);
        if (old != null) old.remove();
        Location c = centerOf(k);
        String[] p = places.get(k).split(":", 2);
        Crate cr = crate(p[0], p[1]);
        if (c == null || cr == null) return;
        KeyService.KeyType t = plugin.keys().type(cr.key());
        String color = t == null ? "#F8D35A" : t.color();
        TextDisplay d = c.getWorld().spawn(c.clone().add(0, 1.55, 0), TextDisplay.class, e -> {
            e.text(Msg.parse("<" + color + "><b>" + Msg.escape(cr.name()) + "</b></" + color + ">\n<gray>Rechtsklick: öffnen\n"
                    + "<gray>Linksklick: Inhalt"));
            e.setBillboard(Display.Billboard.CENTER);
            e.setPersistent(false);
            e.setShadowed(true);
            e.setBackgroundColor(org.bukkit.Color.fromARGB(0x50, 0, 0, 0));
        });
        labels.put(k, d);
    }

    public void removeLabels() {
        labels.values().forEach(org.bukkit.entity.Entity::remove);
        labels.clear();
    }

    // =================================================================================================================
    // Klicks
    // =================================================================================================================

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Crate c = at(e.getClickedBlock());
        if (c == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        lastCrateAt.put(p.getUniqueId(), key(e.getClickedBlock()));
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            plugin.crateScreens().preview(p, c, true);
            return;
        }
        open(p, c, e.getClickedBlock(), p.isSneaking());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (at(e.getBlock()) != null) e.setCancelled(true);
    }

    /** Aus der Vorschau öffnen: nur, wenn der Spieler noch an der Kiste steht. */
    public void openFromPreview(Player p, Crate c) {
        String k = lastCrateAt.get(p.getUniqueId());
        Location at = k == null ? null : centerOf(k);
        if (at == null || at.getWorld() != p.getWorld() || at.distanceSquared(p.getLocation()) > 36) {
            Msg.send(p, "<red>Geh dafür zur Kiste am Spawn.");
            return;
        }
        open(p, c, at.getBlock(), p.isSneaking());
    }

    public void open(Player p, Crate c, Block block, boolean skip) {
        UUID id = p.getUniqueId();
        String mode = plugin.modes().modeOf(id);
        if (!mode.equals(c.mode())) {
            Msg.send(p, "<red>Diese Kiste gehört zu " + ModeService.label(c.mode()) + ".");
            return;
        }
        if (opening.contains(id)) return;
        if (c.rewards().isEmpty()) return;
        KeyService.KeyType t = plugin.keys().type(c.key());
        if (!plugin.keys().take(id, mode, c.key(), 1)) {
            Msg.send(p, "<red>Du brauchst einen " + (t == null ? c.key() : t.colored()) + "<red>. <gray>Schlüssel gibt es beim Voten, "
                    + "für Spielzeit, bei Events und im <white>/juwelenshop</white>.");
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            p.playSound(block.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
            return;
        }
        Reward win = roll(c);
        give(p, c, win);
        String k = key(block);
        if (skip || busy.contains(k)) {
            announce(p, c, win);
            return;
        }
        animate(p, c, win, block, k);
    }

    private Reward roll(Crate c) {
        int r = ThreadLocalRandom.current().nextInt(Math.max(1, c.total()));
        for (Reward w : c.rewards()) {
            r -= w.weight();
            if (r < 0) return w;
        }
        return c.rewards().get(c.rewards().size() - 1);
    }

    private void give(Player p, Crate c, Reward r) {
        UUID id = p.getUniqueId();
        switch (r.kind()) {
            case "coins" -> plugin.modes().depositIn(id, c.mode(), r.amount());
            case "jewels" -> plugin.jewels().add(id, r.amount());
            case "tokens" -> plugin.give("tokens", id, r.amount());
            case "key" -> plugin.keys().give(id, c.mode(), r.value(), (int) r.amount());
            case "cosmetic" -> {
                CosmeticService.Item it = plugin.cosmetics().find(r.value());
                if (it != null) {
                    if (plugin.cosmetics().owns(id, it)) {
                        // Doppelt: Ersatz in Juwelen
                        long back = plugin.getConfig().getLong("crates.duplicate-jewels", 10);
                        plugin.jewels().add(id, back);
                        Msg.send(p, "<gray>" + it.label() + " hast du schon, dafür gibt es <#5FE3A0>" + back + " Juwelen</#5FE3A0>.");
                    } else {
                        plugin.cosmetics().grant(id, it);
                    }
                }
            }
            case "pet" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pet give " + p.getName() + " " + r.value());
            case "furniture" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "moebel give " + p.getName() + " " + r.value());
            case "item" -> {
                Material m = Material.matchMaterial(r.value());
                if (m != null) {
                    ItemStack it = new ItemStack(m, (int) Math.min(r.amount(), m.getMaxStackSize()));
                    for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                }
            }
            case "cmd" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), r.value().replace("%player%", p.getName()));
            default -> {
            }
        }
    }

    private void announce(Player p, Crate c, Reward r) {
        double ch = c.chance(r);
        String col = colorOf(ch).asHexString();
        Msg.send(p, "<#F0A050><b>KISTE</b> <dark_gray>▶ <gray>" + Msg.escape(c.name()) + ": <" + col + "><b>" + Msg.escape(r.name())
                + "</b></" + col + "> <dark_gray>(" + String.format(Locale.GERMANY, "%.1f", ch) + " %)");
        if (ch < 1.5) {
            Bukkit.broadcast(Msg.parse("<#F0A050><b>KISTE</b> <dark_gray>▶ <white>" + p.getName() + " <gray>hat <" + col + ">"
                    + Msg.escape(r.name()) + "</" + col + "> <gray>aus der " + Msg.escape(c.name()) + " gezogen!"));
        }
        Sfx.play(plugin, p, ch < 5 ? Sfx.Kind.SUCCESS_BIG : Sfx.Kind.SUCCESS);
    }

    /** Anzeige-Item für einen Gewinn. */
    public ItemStack displayItem(Reward r) {
        Material m = switch (r.kind()) {
            case "coins" -> Material.GOLD_NUGGET;
            case "tokens" -> Material.AMETHYST_SHARD;
            case "jewels" -> Material.EMERALD;
            case "key" -> Material.TRIAL_KEY;
            case "pet" -> Material.LEAD;
            case "furniture" -> Material.OAK_STAIRS;
            case "item" -> {
                Material x = Material.matchMaterial(r.value());
                yield x == null || !x.isItem() ? Material.CHEST : x;
            }
            case "cosmetic" -> Material.PAPER;
            default -> Material.CHEST;
        };
        ItemStack it = new ItemStack(m);
        if (r.kind().equals("cosmetic")) {
            CosmeticService.Item ci = plugin.cosmetics().find(r.value());
            ItemMeta meta = it.getItemMeta();
            if (ci != null && ci.model() != null) meta.setItemModel(new NamespacedKey("kuby", "hat/" + ci.model()));
            else meta.setItemModel(NamespacedKey.minecraft("blaze_powder"));
            it.setItemMeta(meta);
        }
        return it;
    }

    private void animate(Player p, Crate c, Reward win, Block block, String k) {
        busy.add(k);
        opening.add(p.getUniqueId());
        Location top = block.getLocation().add(0.5, 1.15, 0.5);
        p.playSound(top, Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        TextDisplay label = labels.get(k);
        if (label != null) label.setTextOpacity((byte) 30);
        ItemDisplay d = top.getWorld().spawn(top, ItemDisplay.class, e -> {
            e.setItemStack(displayItem(c.rewards().get(0)));
            e.setPersistent(false);
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.6f, 0.6f, 0.6f), new Quaternionf()));
            e.setTeleportDuration(3);
        });
        int[] delays = {2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 5, 6, 8, 10};
        List<Reward> pool = c.rewards();
        int t = 0;
        for (int i = 0; i < delays.length; i++) {
            t += delays[i];
            final int step = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!d.isValid()) return;
                boolean last = step == delays.length - 1;
                Reward show = last ? win : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                d.setItemStack(displayItem(show));
                d.teleport(top.clone().add(0, step * 0.04, 0));
                top.getWorld().playSound(top, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 0.8f + step * 0.08f);
            }, t);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            double ch = c.chance(win);
            Location at = d.isValid() ? d.getLocation() : top;
            at.getWorld().spawnParticle(ch < 1.5 ? Particle.TOTEM_OF_UNDYING : Particle.HAPPY_VILLAGER, at, ch < 1.5 ? 60 : 20,
                    0.3, 0.3, 0.3, 0.2);
            at.getWorld().playSound(at, ch < 5 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            TextDisplay name = at.getWorld().spawn(at.clone().add(0, 0.55, 0), TextDisplay.class, e -> {
                e.text(Msg.parse("<" + colorOf(ch).asHexString() + "><b>" + Msg.escape(win.name())));
                e.setBillboard(Display.Billboard.CENTER);
                e.setPersistent(false);
            });
            if (p.isOnline()) announce(p, c, win);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                d.remove();
                name.remove();
                if (label != null && label.isValid()) label.setTextOpacity((byte) -1);
                busy.remove(k);
                opening.remove(p.getUniqueId());
                top.getWorld().playSound(top, Sound.BLOCK_CHEST_CLOSE, 0.8f, 1f);
            }, 50L);
        }, t + 4L);
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> lines = new ArrayList<>();
        places.forEach((k, v) -> lines.add(k + ";" + v));
        y.set("orte", lines);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("crates.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
