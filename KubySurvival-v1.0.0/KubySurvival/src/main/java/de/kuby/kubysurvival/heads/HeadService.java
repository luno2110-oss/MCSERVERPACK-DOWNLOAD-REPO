package de.kuby.kubysurvival.heads;

import com.destroystokyo.paper.profile.ProfileProperty;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Kopf-Datenbank /heads in Survival: Deko-Köpfe nach Kategorien (heads.yml), Suche nach Namen und der Kopf jedes
 * Spielers. Jeder Kopf kostet heads.price Coins. Admins erweitern die Datenbank mit /kp heads add (Kopf in der Hand).
 */
public final class HeadService implements CommandExecutor, TabCompleter {

    /** Ein Kopf: Anzeigename und Quelle (Spielername, "textur:..." oder "item:MATERIAL"). */
    public record Head(String name, String source) {}

    public record Category(String id, String name, List<Head> heads) {}

    static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<String, Category> categories = new LinkedHashMap<>();

    public HeadService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "heads.yml");
        if (!file.exists()) addon.saveResource("heads.yml", false);
        load();
    }

    public long price() {
        return core.getConfig().getLong("heads.price", 1_000);
    }

    public synchronized List<Category> categories() {
        return new ArrayList<>(categories.values());
    }

    /** Köpfe, deren Name den Suchbegriff enthält; passt er auf einen Spielernamen, kommt dessen Kopf dazu. */
    public synchronized List<Head> search(String term) {
        String t = term.toLowerCase(Locale.ROOT);
        List<Head> out = new ArrayList<>();
        for (Category c : categories.values()) {
            for (Head h : c.heads()) if (h.name().toLowerCase(Locale.ROOT).contains(t)) out.add(h);
        }
        if (PLAYER_NAME.matcher(term).matches()) out.add(0, new Head("Kopf von " + term, term));
        return out;
    }

    /** Fertiger Kopf als Item (für Menü und Kauf). */
    public static ItemStack item(Head h, List<String> lore) {
        ItemStack item;
        String src = h.source();
        if (src.startsWith("item:")) {
            Material m = Material.matchMaterial(src.substring(5));
            item = new ItemStack(m == null ? Material.PLAYER_HEAD : m);
        } else {
            item = new ItemStack(Material.PLAYER_HEAD);
        }
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.item("<white>" + Msg.escape(h.name())));
        meta.lore(lore.stream().map(Msg::item).toList());
        item.setItemMeta(meta);
        if (src.startsWith("textur:")) {
            String tex = src.substring(7);
            item.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile()
                    .uuid(UUID.nameUUIDFromBytes(tex.getBytes(StandardCharsets.UTF_8)))
                    .addProperty(new ProfileProperty("textures", tex)).build());
        } else if (!src.startsWith("item:")) {
            // Nur der Name: der Client lädt die Haut selbst (dynamisches Profil)
            item.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile().name(src).build());
        }
        return item;
    }

    /** Kauf: Coins abziehen, Kopf ins Inventar (voll: vor die Füße). Null bei Erfolg. */
    public String buy(Player p, Head h) {
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return "Köpfe gibt es nur in Survival.";
        if (!core.economy().withdraw(p.getUniqueId(), price())) {
            return "Ein Kopf kostet " + Msg.coins(price()) + " Coins, so viele hast du nicht.";
        }
        ItemStack item = item(h, List.of("<gray>Deko-Kopf"));
        p.getInventory().addItem(item).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
        return null;
    }

    // =================================================================================================================
    // Befehle
    // =================================================================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) {
            Msg.send(p, "<red>Köpfe gibt es nur in Survival.");
            return true;
        }
        if (args.length == 0) {
            new HeadMenu(this, core, 0, null, 0).open(p);
        } else {
            String term = String.join(" ", args).trim();
            if (term.length() < 2) {
                Msg.send(p, "<red>Bitte mindestens 2 Zeichen suchen.");
                return true;
            }
            new HeadMenu(this, core, -1, term, 0).open(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String t = args[0].toLowerCase(Locale.ROOT);
            for (Player o : Bukkit.getOnlinePlayers()) if (o.getName().toLowerCase(Locale.ROOT).startsWith(t)) out.add(o.getName());
        }
        return out;
    }

    /** /kp heads add &lt;kategorie&gt; &lt;Name&gt; (Kopf in der Hand) und /kp heads remove &lt;Name&gt;. */
    public void admin(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("add") && args.length >= 4) {
            String cat = args[2].toLowerCase(Locale.ROOT);
            String name = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
            ItemStack hand = p.getInventory().getItemInMainHand();
            ResolvableProfile prof = hand.getType() == Material.PLAYER_HEAD ? hand.getData(DataComponentTypes.PROFILE) : null;
            String source = null;
            if (prof != null) {
                for (ProfileProperty pp : prof.properties()) if (pp.getName().equals("textures")) source = "textur:" + pp.getValue();
                if (source == null && prof.name() != null) source = prof.name();
            }
            if (source == null) {
                Msg.send(p, "<red>Nimm einen Spielerkopf mit Haut in die Hand.");
                return;
            }
            synchronized (this) {
                Category c = categories.computeIfAbsent(cat, k -> new Category(k, k, new ArrayList<>()));
                c.heads().add(new Head(name, source));
                save();
            }
            Msg.send(p, "<green>Kopf <white>" + Msg.escape(name) + " <green>in <white>" + cat + " <green>aufgenommen.");
        } else if (sub.equals("remove") && args.length >= 3) {
            String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            int n = 0;
            synchronized (this) {
                for (Category c : categories.values()) {
                    int before = c.heads().size();
                    c.heads().removeIf(h -> h.name().equalsIgnoreCase(name));
                    n += before - c.heads().size();
                }
                save();
            }
            Msg.send(p, "<gray>" + n + " Kopf/Köpfe entfernt.");
        } else {
            Msg.send(p, "<white>/kp heads add <kategorie> <Name> <gray>Kopf in der Hand aufnehmen · <white>/kp heads remove <Name>");
        }
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private synchronized void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (y.getConfigurationSection("kategorien") == null) {
            var in = addon.getResource("heads.yml");
            if (in != null) y = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        ConfigurationSection s = y.getConfigurationSection("kategorien");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            List<Head> heads = new ArrayList<>();
            for (String raw : s.getStringList(id + ".koepfe")) {
                String[] p = raw.split(";", 2);
                if (p.length == 2 && !p[0].isBlank() && !p[1].isBlank()) heads.add(new Head(p[0].trim(), p[1].trim()));
            }
            categories.put(id, new Category(id, s.getString(id + ".name", id), heads));
        }
    }

    private synchronized void save() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        y.set("kategorien", null);
        for (Category c : categories.values()) {
            y.set("kategorien." + c.id() + ".name", c.name());
            List<String> list = new ArrayList<>();
            for (Head h : c.heads()) list.add(h.name() + ";" + h.source());
            y.set("kategorien." + c.id() + ".koepfe", list);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("heads.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
