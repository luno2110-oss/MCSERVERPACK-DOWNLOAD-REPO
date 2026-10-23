package de.kuby.kubysurvival.quests;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Locs;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nebenquests bei Figuren (Dorfbewohner ohne KI). Jede Figur hat mehrere Aufgaben nacheinander. Man hat immer
 * höchstens eine laufende Nebenquest. Die Belohnung gibt es bei der Figur (bei talk: bei der Zielfigur).
 * Die Wegweisung zeigt in der Aktionsleiste einen Pfeil zur richtigen Figur.
 * Definitionen in sidequests.yml, Spielerdaten und Standorte in sidequestdata.yml.
 */
public final class SideQuestService implements Listener {

    /** Eine Figur mit ihren Aufgaben. */
    public record Figure(String id, String name, String profession, List<QuestService.Def> steps, List<String> lines) {}

    /** Laufende Nebenquest eines Spielers. */
    static final class Run {
        String figure;
        int step;
        long progress;
    }

    static final class Data {
        Run run;
        final Map<String, Integer> finished = new HashMap<>();
        boolean nav = true;
        /** Figur, zu der die Wegweisung ohne laufende Quest zeigen soll (im Menü gewählt). */
        String navTarget;
    }

    private static final String[] ARROWS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final NamespacedKey tag;
    private final File defFile;
    private final File dataFile;
    private final Map<String, Figure> figures = new LinkedHashMap<>();
    private final Map<String, Location> locations = new HashMap<>();
    private final Map<UUID, Data> players = new HashMap<>();

    public SideQuestService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.tag = new NamespacedKey(addon, "side_figure");
        this.defFile = new File(addon.getDataFolder(), "sidequests.yml");
        this.dataFile = new File(addon.getDataFolder(), "sidequestdata.yml");
        if (!defFile.exists()) addon.saveResource("sidequests.yml", false);
        loadDefs();
        loadData();
        addon.getServer().getScheduler().runTaskTimer(addon, this::navTick, 40L, 10L);
    }

    // ---------- Definitionen ----------

    public synchronized void loadDefs() {
        figures.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(defFile);
        ConfigurationSection fs = yml.getConfigurationSection("figures");
        if (fs == null) return;
        for (String id : fs.getKeys(false)) {
            List<QuestService.Def> steps = new ArrayList<>();
            List<String> lines = new ArrayList<>();
            int i = 0;
            for (String raw : fs.getStringList(id + ".quests")) {
                String[] p = raw.split(";");
                try {
                    String[] type = p[3].trim().split(":", 2);
                    steps.add(new QuestService.Def(id + "_" + i++, p[0].trim(), p[2].trim(), type[0].toLowerCase(),
                            type.length > 1 ? type[1].trim() : null, Long.parseLong(p[4].trim()), Long.parseLong(p[5].trim()),
                            Long.parseLong(p[6].trim()), Long.parseLong(p[7].trim())));
                    lines.add(p[1].trim());
                } catch (RuntimeException e) {
                    addon.getLogger().warning("sidequests.yml: Aufgabe ungültig (" + id + "): " + raw);
                }
            }
            figures.put(id, new Figure(id, fs.getString(id + ".name", id), fs.getString(id + ".beruf", "none"), steps, lines));
        }
    }

    public synchronized List<Figure> figures() {
        return new ArrayList<>(figures.values());
    }

    public synchronized Location location(String id) {
        Location l = locations.get(id);
        return l == null ? null : l.clone();
    }

    // ---------- Spielerdaten ----------

    synchronized Data data(UUID id) {
        return players.computeIfAbsent(id, k -> new Data());
    }

    public synchronized int finished(UUID id, String figure) {
        return data(id).finished.getOrDefault(figure, 0);
    }

    /** Laufende Aufgabe oder null. */
    public synchronized QuestService.Def running(UUID id) {
        Run r = data(id).run;
        if (r == null) return null;
        Figure f = figures.get(r.figure);
        if (f == null || r.step >= f.steps().size()) {
            data(id).run = null;
            return null;
        }
        return f.steps().get(r.step);
    }

    public synchronized Figure runningFigure(UUID id) {
        Run r = data(id).run;
        return r == null ? null : figures.get(r.figure);
    }

    public synchronized long runningProgress(UUID id) {
        Run r = data(id).run;
        return r == null ? 0 : r.progress;
    }

    public synchronized boolean nav(UUID id) {
        return data(id).nav;
    }

    public synchronized boolean toggleNav(UUID id) {
        Data d = data(id);
        d.nav = !d.nav;
        return d.nav;
    }

    public synchronized void setNavTarget(UUID id, String figure) {
        Data d = data(id);
        d.navTarget = figure;
        d.nav = true;
    }

    public synchronized boolean cancel(UUID id) {
        Data d = data(id);
        if (d.run == null) return false;
        d.run = null;
        return true;
    }

    // ---------- Fortschritt ----------

    /** Wird von QuestService.progress mit aufgerufen (nur in Survival). */
    public synchronized void progress(Player p, String type, Object subject, long amount) {
        Data d = data(p.getUniqueId());
        QuestService.Def def = running(p.getUniqueId());
        if (def == null || d.run.progress >= def.amount() || def.type().equals("bring") || def.type().equals("talk")) return;
        if (!QuestService.matches(def, type, subject)) return;
        d.run.progress = Math.min(def.amount(), d.run.progress + amount);
        if (d.run.progress >= def.amount()) {
            Figure f = figures.get(d.run.figure);
            Msg.send(p, "<green><b>Nebenquest geschafft:</b> <white>" + Msg.escape(def.name()) + " <gray>– geh zurück zu <white>"
                    + Msg.escape(f.name()) + "<gray>.");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
        }
    }

    // ---------- Figuren ----------

    public boolean isFigure(Entity e) {
        return e.getPersistentDataContainer().has(tag, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        String id = e.getRightClicked().getPersistentDataContainer().get(tag, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        talk(e.getPlayer(), id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (isFigure(e.getEntity())) e.setCancelled(true);
    }

    /** Spieler spricht eine Figur an. */
    public synchronized void talk(Player p, String figureId) {
        Figure f = figures.get(figureId);
        if (f == null) return;
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return;
        UUID id = p.getUniqueId();
        Data d = data(id);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.8f, 1f);
        QuestService.Def def = running(id);

        // Botengang zu dieser Figur?
        if (def != null && def.type().equals("talk") && figureId.equalsIgnoreCase(def.arg())) {
            Figure giver = figures.get(d.run.figure);
            say(p, f, "Oh, eine Nachricht von " + (giver == null ? "einem Freund" : giver.name()) + "? Vielen Dank, dass du extra gekommen bist!");
            complete(p, d, giver == null ? f : giver, def);
            return;
        }
        if (def != null && !d.run.figure.equals(figureId)) {
            Figure other = figures.get(d.run.figure);
            say(p, f, "Du hilfst gerade schon " + (other == null ? "jemand anderem" : other.name()) + ". Komm danach wieder!");
            Msg.send(p, "<gray>Abbrechen: <click:run_command:'/sidequests cancel'><white>/sidequests cancel</click>");
            return;
        }
        if (def != null) {
            if (def.type().equals("bring")) deliver(p, d, def);
            if (d.run.progress >= def.amount()) {
                say(p, f, "Großartig, genau so! Hier, das hast du dir verdient.");
                complete(p, d, f, def);
            } else {
                say(p, f, "Wie läuft's? " + def.task() + ".");
                Msg.send(p, "<gray>Stand: <white>" + Msg.coins(d.run.progress) + "/" + Msg.coins(def.amount()));
            }
            return;
        }
        int step = d.finished.getOrDefault(figureId, 0);
        if (step >= f.steps().size()) {
            say(p, f, "Du hast mir so sehr geholfen. Danke für alles!");
            return;
        }
        QuestService.Def next = f.steps().get(step);
        say(p, f, f.lines().get(step));
        Msg.send(p, "<gold>Nebenquest: <white>" + Msg.escape(next.name()) + " <dark_gray>(" + (step + 1) + "/" + f.steps().size() + ")");
        Msg.send(p, "<gray>Aufgabe: <white>" + Msg.escape(next.task()) + " <gray>· Belohnung: "
                + QuestService.rewardText(next.coins(), next.tokens(), next.xp()));
        Msg.send(p, "<click:run_command:'/sidequests annehmen " + figureId + "'><green>[Annehmen]</click>  <gray>oder einfach weitergehen");
    }

    /** Nimmt die nächste Aufgabe einer Figur an (nur in deren Nähe). */
    public synchronized String accept(Player p, String figureId) {
        Figure f = figures.get(figureId);
        if (f == null) return "Diese Figur gibt es nicht.";
        UUID id = p.getUniqueId();
        Data d = data(id);
        if (running(id) != null) return "Du hast schon eine laufende Nebenquest. Abbrechen mit /sidequests cancel.";
        Location l = locations.get(figureId);
        if (l == null || l.getWorld() != p.getWorld() || l.distanceSquared(p.getLocation()) > 100) {
            return "Sprich dafür direkt mit " + f.name() + ".";
        }
        int step = d.finished.getOrDefault(figureId, 0);
        if (step >= f.steps().size()) return f.name() + " hat keine Aufgaben mehr für dich.";
        Run r = new Run();
        r.figure = figureId;
        r.step = step;
        d.run = r;
        d.navTarget = null;
        QuestService.Def def = f.steps().get(step);
        Msg.send(p, "<green>Angenommen: <white>" + Msg.escape(def.name()) + " <gray>– " + Msg.escape(def.task()));
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
        return null;
    }

    private void deliver(Player p, Data d, QuestService.Def def) {
        Material m = def.arg() == null ? null : Material.matchMaterial(def.arg());
        if (m == null) return;
        long need = def.amount() - d.run.progress;
        int have = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && it.getType() == m) have += it.getAmount();
        int give = (int) Math.min(need, have);
        if (give <= 0) return;
        p.getInventory().removeItem(new ItemStack(m, give));
        d.run.progress += give;
        Msg.send(p, "<gray>Abgegeben: <white>" + give + "x " + nice(m));
    }

    private void complete(Player p, Data d, Figure giver, QuestService.Def def) {
        UUID id = p.getUniqueId();
        d.run = null;
        d.finished.merge(giver.id(), 1, Integer::sum);
        if (def.coins() > 0) core.economy().deposit(id, def.coins());
        if (def.tokens() > 0) addon.jobs().addTokens(id, def.tokens());
        if (def.xp() > 0) addon.jobs().addPlayerXp(id, def.xp());
        Msg.send(p, "<green>Belohnung: " + QuestService.rewardText(def.coins(), def.tokens(), def.xp()));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.3f);
        int left = giver.steps().size() - d.finished.get(giver.id());
        if (left > 0) {
            Msg.send(p, "<gray>" + Msg.escape(giver.name()) + " hat noch " + left + (left == 1 ? " Aufgabe" : " Aufgaben") + " für dich.");
        } else {
            Msg.send(p, "<gold>Alle Aufgaben von " + Msg.escape(giver.name()) + " geschafft!");
        }
    }

    private void say(Player p, Figure f, String text) {
        p.sendMessage(Msg.parse("<gold>" + Msg.escape(f.name()) + "<dark_gray>: <white>" + Msg.escape(text)));
    }

    private static String nice(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------- Wegweisung ----------

    /** Ziel der Wegweisung: Figur zum Abgeben/Sprechen, sonst die im Menü gewählte oder die nächste mit Aufgabe. */
    synchronized String navFigure(Player p) {
        UUID id = p.getUniqueId();
        Data d = data(id);
        QuestService.Def def = running(id);
        if (def != null) {
            if (def.type().equals("talk")) return def.arg();
            if (def.type().equals("bring") || d.run.progress >= def.amount()) return d.run.figure;
            return null;
        }
        if (d.navTarget != null && figures.containsKey(d.navTarget)) return d.navTarget;
        String best = null;
        double dist = Double.MAX_VALUE;
        for (Figure f : figures.values()) {
            Location l = locations.get(f.id());
            if (l == null || l.getWorld() != p.getWorld() || d.finished.getOrDefault(f.id(), 0) >= f.steps().size()) continue;
            double ds = l.distanceSquared(p.getLocation());
            if (ds < dist) {
                dist = ds;
                best = f.id();
            }
        }
        return best;
    }

    private void navTick() {
        for (Player p : addon.getServer().getOnlinePlayers()) {
            if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) continue;
            Component bar;
            synchronized (this) {
                Data d = players.get(p.getUniqueId());
                if (d == null || !d.nav) continue;
                String target = navFigure(p);
                Location l = target == null ? null : locations.get(target);
                Figure f = target == null ? null : figures.get(target);
                if (l == null || f == null || l.getWorld() != p.getWorld()) continue;
                // Ohne laufende Quest nur zeigen, wenn eine Figur im Menü gewählt wurde.
                if (d.run == null && d.navTarget == null) continue;
                double dist = l.distance(p.getLocation());
                if (dist < 4) continue;
                double dx = l.getX() - p.getLocation().getX();
                double dz = l.getZ() - p.getLocation().getZ();
                double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
                double rel = ((targetYaw - p.getLocation().getYaw()) % 360 + 360) % 360;
                String arrow = ARROWS[(int) Math.round(rel / 45.0) % 8];
                bar = Msg.parse("<gold>" + arrow + " <white>" + Msg.escape(f.name()) + " <gray>" + Math.round(dist) + " m");
            }
            p.sendActionBar(bar);
        }
    }

    // ---------- Admin ----------

    /** /kp figur setzen|entfernen|liste */
    public synchronized void admin(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase() : "";
        String id = args.length > 2 ? args[2].toLowerCase() : null;
        switch (sub) {
            case "setzen", "set" -> {
                Figure f = id == null ? null : figures.get(id);
                if (f == null) {
                    Msg.send(p, "<red>Nutzung: /kp figur setzen <id> <gray>(" + String.join(", ", figures.keySet()) + ")");
                    return;
                }
                removeEntities(id);
                p.getWorld().spawn(p.getLocation(), Villager.class, v -> {
                    v.setAI(false);
                    v.setInvulnerable(true);
                    v.setSilent(true);
                    v.setCollidable(false);
                    v.setPersistent(true);
                    v.setRemoveWhenFarAway(false);
                    var prof = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(f.profession().toLowerCase()));
                    if (prof != null) v.setProfession(prof);
                    v.setVillagerLevel(5);
                    v.customName(Msg.parse("<gold><b>" + Msg.escape(f.name()) + "</b>"));
                    v.setCustomNameVisible(true);
                    v.getPersistentDataContainer().set(tag, PersistentDataType.STRING, id);
                });
                locations.put(id, p.getLocation());
                save();
                Msg.send(p, "<green>" + f.name() + " steht jetzt hier.");
            }
            case "entfernen", "remove" -> {
                if (id == null || !locations.containsKey(id)) {
                    Msg.send(p, "<red>Nutzung: /kp figur entfernen <id>");
                    return;
                }
                int n = removeEntities(id);
                locations.remove(id);
                save();
                Msg.send(p, "<green>Figur entfernt (" + n + ").");
            }
            case "liste", "list" -> {
                for (Figure f : figures.values()) {
                    Location l = locations.get(f.id());
                    Msg.send(p, "<gold>" + f.id() + " <white>" + f.name() + " <gray>" + f.steps().size() + " Aufgaben · "
                            + (l == null ? "<red>nicht aufgestellt" : "<white>" + l.getWorld().getName() + " " + l.getBlockX() + " "
                            + l.getBlockY() + " " + l.getBlockZ()));
                }
            }
            case "reload" -> {
                loadDefs();
                Msg.send(p, "<green>sidequests.yml neu geladen.");
            }
            default -> Msg.send(p, "<white>/kp figur setzen <id> <gray>Figur an deiner Position\n<white>/kp figur entfernen <id>\n"
                    + "<white>/kp figur liste <gray>· <white>/kp figur reload");
        }
    }

    private int removeEntities(String id) {
        Location l = locations.get(id);
        if (l == null || l.getWorld() == null) return 0;
        int n = 0;
        for (Villager v : l.getWorld().getNearbyEntitiesByType(Villager.class, l, 8)) {
            if (id.equals(v.getPersistentDataContainer().get(tag, PersistentDataType.STRING))) {
                v.remove();
                n++;
            }
        }
        return n;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Daten bleiben, nur die Menü-Auswahl der Wegweisung wird vergessen.
        Data d = players.get(e.getPlayer().getUniqueId());
        if (d != null) d.navTarget = null;
    }

    // ---------- Speichern ----------

    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection ls = yml.getConfigurationSection("locations");
        if (ls != null) {
            for (String id : ls.getKeys(false)) {
                Location l = Locs.parse(ls.getString(id, ""));
                if (l != null) locations.put(id, l);
            }
        }
        ConfigurationSection ps = yml.getConfigurationSection("players");
        if (ps == null) return;
        for (String key : ps.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection s = ps.getConfigurationSection(key);
            if (s == null) continue;
            Data d = new Data();
            d.nav = s.getBoolean("nav", true);
            if (s.isString("run.figure")) {
                Run r = new Run();
                r.figure = s.getString("run.figure");
                r.step = s.getInt("run.step");
                r.progress = s.getLong("run.progress");
                d.run = r;
            }
            ConfigurationSection fin = s.getConfigurationSection("finished");
            if (fin != null) for (String f : fin.getKeys(false)) d.finished.put(f, fin.getInt(f));
            players.put(id, d);
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<String, Location> e : locations.entrySet()) out.set("locations." + e.getKey(), Locs.serialize(e.getValue()));
        for (Map.Entry<UUID, Data> e : players.entrySet()) {
            Data d = e.getValue();
            String b = "players." + e.getKey() + ".";
            out.set(b + "nav", d.nav);
            if (d.run != null) {
                out.set(b + "run.figure", d.run.figure);
                out.set(b + "run.step", d.run.step);
                out.set(b + "run.progress", d.run.progress);
            }
            for (Map.Entry<String, Integer> f : d.finished.entrySet()) out.set(b + "finished." + f.getKey(), f.getValue());
        }
        try {
            addon.getDataFolder().mkdirs();
            out.save(dataFile);
        } catch (IOException ex) {
            addon.getLogger().warning("sidequestdata.yml: " + ex.getMessage());
        }
    }
}
