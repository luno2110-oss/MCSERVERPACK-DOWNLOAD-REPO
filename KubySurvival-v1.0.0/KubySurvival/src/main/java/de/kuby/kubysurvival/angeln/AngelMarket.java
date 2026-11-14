package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fisch-Markt (/angeln markt): Spieler bieten extrem seltene Fische (ab angeln.minispiel.extrem-ab, also mit „1 zu N“
 * am Item) für Juwelen an. Der Verkäufer bekommt den Preis abzüglich markt.gebuehr-prozent (5 %), auch offline. Höchstens
 * markt.max-angebote (5) je Spieler, Preis 1 bis markt.max-preis Juwelen. Angebote liegen in angelmarkt.yml.
 */
public final class AngelMarket {

    public record Listing(String id, UUID seller, String sellerName, ItemStack item, long price, long created) {}

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private int nextId;

    public AngelMarket(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "angelmarkt.yml");
        load();
    }

    private long cfg(String key, long def) {
        return core.getConfig().getLong("angeln.markt." + key, def);
    }

    /** Nur extrem seltene Fische (mit „1 zu N“ ab der Extrem-Grenze). */
    public boolean allowed(ItemStack it) {
        AngelItems.FishItem f = AngelItems.read(it);
        return f != null && f.odds() >= core.getConfig().getDouble("angeln.minispiel.extrem-ab", 250_000);
    }

    public synchronized List<Listing> all() {
        List<Listing> l = new ArrayList<>(listings.values());
        l.sort(Comparator.comparingLong(Listing::created).reversed());
        return l;
    }

    public synchronized long countOf(UUID id) {
        return listings.values().stream().filter(x -> x.seller().equals(id)).count();
    }

    /** Fisch aus der Hand anbieten; null bei Erfolg. */
    public synchronized String offer(Player p, long price) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!allowed(hand)) return "Nimm einen extrem seltenen Fisch (mit „1 zu …“) in die Hand.";
        if (price < 1 || price > cfg("max-preis", 1_000_000)) return "Preis: 1 bis " + AngelText.num(cfg("max-preis", 1_000_000)) + " Juwelen.";
        if (countOf(p.getUniqueId()) >= cfg("max-angebote", 5)) return "Du hast schon " + cfg("max-angebote", 5) + " Angebote.";
        ItemStack one = hand.clone();
        one.setAmount(1);
        hand.setAmount(hand.getAmount() - 1);
        p.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        String id = String.valueOf(++nextId);
        listings.put(id, new Listing(id, p.getUniqueId(), p.getName(), one, price, System.currentTimeMillis()));
        save();
        return null;
    }

    /** Kaufen: Juwelen an den Verkäufer (minus Gebühr), Fisch an den Käufer. */
    public synchronized String buy(Player p, String id) {
        Listing l = listings.get(id);
        if (l == null) return "Das Angebot gibt es nicht mehr.";
        if (l.seller().equals(p.getUniqueId())) return "Das ist dein eigenes Angebot.";
        if (!core.jewels().take(p.getUniqueId(), l.price())) return "Dafür fehlen dir Juwelen (" + AngelText.num(l.price()) + ").";
        listings.remove(id);
        long fee = Math.round(l.price() * cfg("gebuehr-prozent", 5) / 100.0);
        core.jewels().add(l.seller(), l.price() - fee);
        for (ItemStack left : p.getInventory().addItem(l.item().clone()).values()) p.getWorld().dropItem(p.getLocation(), left);
        save();
        Player s = Bukkit.getPlayer(l.seller());
        if (s != null) {
            Msg.send(s, "<aqua><b>FISCH-MARKT</b> <dark_gray>▶ <white>" + Msg.escape(p.getName()) + " <gray>hat deinen Fisch gekauft: <green>+"
                    + AngelText.num(l.price() - fee) + " Juwelen");
        }
        return null;
    }

    /** Eigenes Angebot zurücknehmen. */
    public synchronized String cancel(Player p, String id) {
        Listing l = listings.get(id);
        if (l == null) return "Das Angebot gibt es nicht mehr.";
        if (!l.seller().equals(p.getUniqueId()) && !p.hasPermission("kuby.admin")) return "Das ist nicht dein Angebot.";
        listings.remove(id);
        for (ItemStack left : p.getInventory().addItem(l.item().clone()).values()) p.getWorld().dropItem(p.getLocation(), left);
        save();
        return null;
    }

    private synchronized void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getInt("naechste", 0);
        ConfigurationSection s = y.getConfigurationSection("angebote");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection e = s.getConfigurationSection(id);
            if (e == null) continue;
            try {
                ItemStack it = ItemStack.deserializeBytes(Base64.getDecoder().decode(e.getString("item", "")));
                listings.put(id, new Listing(id, UUID.fromString(e.getString("verkaeufer")), e.getString("name", "?"), it,
                        e.getLong("preis"), e.getLong("zeit")));
            } catch (RuntimeException ex) {
                addon.getLogger().warning("angelmarkt.yml: Angebot " + id + " unlesbar: " + ex.getMessage());
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("naechste", nextId);
        for (Listing l : listings.values()) {
            String b = "angebote." + l.id() + ".";
            y.set(b + "verkaeufer", l.seller().toString());
            y.set(b + "name", l.sellerName());
            y.set(b + "preis", l.price());
            y.set(b + "zeit", l.created());
            y.set(b + "item", Base64.getEncoder().encodeToString(l.item().serializeAsBytes()));
        }
        try {
            addon.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("angelmarkt.yml: " + e.getMessage());
        }
    }
}
