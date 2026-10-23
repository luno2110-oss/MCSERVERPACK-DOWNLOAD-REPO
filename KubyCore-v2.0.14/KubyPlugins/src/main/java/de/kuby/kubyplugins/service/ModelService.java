package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Eigene 3D-Modelle als Blöcke in der Welt, ohne externes Plugin.
 * Ein Modell ist eine Item-Definition aus custom-items (Modell aus dem Resource Pack).
 * Es wird als Anzeige (ItemDisplay) mit unsichtbarem Trefferfeld (Interaction) gesetzt.
 * Abbauen: linksklick auf das Modell. Speicherung in models.yml, Wiederherstellung beim Laden der Chunks.
 */
public final class ModelService {

    public record Placed(String world, int x, int y, int z, String id) {
        public String key() { return world + ";" + x + ";" + y + ";" + z; }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey modelKey;
    private final NamespacedKey hitKey;
    private final List<Placed> placed = new ArrayList<>();

    public ModelService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "models.yml");
        this.modelKey = new NamespacedKey(plugin, "kp_model");
        this.hitKey = new NamespacedKey(plugin, "kp_model_hit");
        for (String raw : YamlConfiguration.loadConfiguration(file).getStringList("models")) {
            String[] p = raw.split(";", 5);
            if (p.length < 5) continue;
            try {
                placed.add(new Placed(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4]));
            } catch (NumberFormatException ignored) { }
        }
    }

    /** Setzt das Modell auf den Block, auf den der Spieler schaut. Gibt null bei Erfolg zurück. */
    public synchronized String place(Player p, String id) {
        var def = plugin.customItems().get(id);
        if (def == null) return "Unbekanntes Modell. Siehe /kp item list";
        Block target = p.getTargetBlockExact(5);
        if (target == null) return "Schau auf einen Block.";
        Block at = target.getRelative(org.bukkit.block.BlockFace.UP);
        Placed pl = new Placed(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), def.id());
        if (placed.stream().anyMatch(x -> x.key().equals(pl.key()))) return "Hier steht schon ein Modell.";
        placed.add(pl);
        spawn(pl);
        save();
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        return null;
    }

    /** Entfernt das Modell zum Trefferfeld und gibt das Item zurück. */
    public synchronized void remove(Entity hit, Player p) {
        String id = hit.getPersistentDataContainer().get(modelKey, PersistentDataType.STRING);
        if (id == null) return;
        Location l = hit.getLocation();
        Placed found = null;
        for (Placed pl : placed) {
            if (pl.world().equals(l.getWorld().getName()) && pl.x() == l.getBlockX() && pl.z() == l.getBlockZ()
                    && pl.y() == l.getBlockY()) { found = pl; break; }
        }
        if (found == null) return;
        placed.remove(found);
        removeEntities(found);
        var def = plugin.customItems().get(id);
        if (def != null) {
            var leftover = p.getInventory().addItem(plugin.customItems().create(def, 1));
            leftover.values().forEach(rest -> p.getWorld().dropItem(p.getLocation(), rest));
        }
        save();
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
    }

    /** Stellt fehlende Entitäten eines geladenen Chunks wieder her. */
    public synchronized void ensureChunk(Chunk c) {
        for (Placed pl : placed) {
            if (!pl.world().equals(c.getWorld().getName())) continue;
            if ((pl.x() >> 4) != c.getX() || (pl.z() >> 4) != c.getZ()) continue;
            Location loc = new Location(c.getWorld(), pl.x() + 0.5, pl.y(), pl.z() + 0.5);
            boolean has = false;
            for (Entity e : c.getWorld().getNearbyEntities(loc, 0.6, 1.2, 0.6)) {
                if (e.getPersistentDataContainer().has(modelKey, PersistentDataType.STRING)) { has = true; break; }
            }
            if (!has) spawn(pl);
        }
    }

    public synchronized List<Placed> all() {
        return new ArrayList<>(placed);
    }

    private void spawn(Placed pl) {
        World w = Bukkit.getWorld(pl.world());
        if (w == null) return;
        var def = plugin.customItems().get(pl.id());
        if (def == null) return;
        Location base = new Location(w, pl.x() + 0.5, pl.y(), pl.z() + 0.5);
        ItemDisplay display = w.spawn(base.clone().add(0, 0.5, 0), ItemDisplay.class, d -> {
            d.setItemStack(plugin.customItems().create(def, 1));
            d.setPersistent(true);
            d.getPersistentDataContainer().set(modelKey, PersistentDataType.STRING, pl.id());
        });
        display.setBillboard(Display.Billboard.FIXED);
        w.spawn(base, Interaction.class, i -> {
            i.setInteractionWidth(1f);
            i.setInteractionHeight(1f);
            i.setPersistent(true);
            i.getPersistentDataContainer().set(modelKey, PersistentDataType.STRING, pl.id());
            i.getPersistentDataContainer().set(hitKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void removeEntities(Placed pl) {
        World w = Bukkit.getWorld(pl.world());
        if (w == null) return;
        Location loc = new Location(w, pl.x() + 0.5, pl.y(), pl.z() + 0.5);
        for (Entity e : w.getNearbyEntities(loc, 0.8, 1.5, 0.8)) {
            if (e.getPersistentDataContainer().has(modelKey, PersistentDataType.STRING)) e.remove();
        }
    }

    private void save() {
        YamlConfiguration out = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (Placed pl : placed) list.add(pl.world() + ";" + pl.x() + ";" + pl.y() + ";" + pl.z() + ";" + pl.id());
        out.set("models", list);
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("models.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
