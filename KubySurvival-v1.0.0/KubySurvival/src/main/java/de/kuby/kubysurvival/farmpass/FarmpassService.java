package de.kuby.kubysurvival.farmpass;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.service.RegionService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.claim.ClaimService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Farmpass: Monats-Pass aus dem Store. Der Store ruft "/farmpass give <Spieler> [Tage]" auf (Konsole oder Admin).
 * Vorteile, solange er läuft: Fliegen am Spawn, in Fly-Regionen (Casino) und auf eigenen und Freundes-Grundstücken,
 * Nebenjob, keine Markt- und Casino-Steuer, +2 Homes, mehr Markt-Plätze, /werkbank überall, Wochenkiste,
 * 4 Pass-Haustiere und das grüne Blatt in Tab und Chat. Pro bezahltem Monat gibt es 250 Claim-Blöcke und
 * ein Haustier zum Behalten (beides für immer).
 */
public final class FarmpassService implements Listener {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    public static final String LEAF = "<green>☘</green> ";

    static final class Data {
        long until;
        int months;
        final List<String> pets = new ArrayList<>();
        String weekly = "";
        boolean fly = true;
    }

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<UUID, Data> players = new HashMap<>();
    /** Spieler, denen der Pass gerade das Fliegen erlaubt. */
    private final Set<UUID> granted = new HashSet<>();
    /** Nach dem Wegnehmen des Fliegens: kein Fallschaden bis zur Landung. */
    private final Map<UUID, Long> fallSafe = new HashMap<>();

    public FarmpassService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "farmpass.yml");
        load();
        core.registerPerk("farmpass", this::active);
        core.registerNamePrefix(id -> active(id) ? LEAF : "");
        addon.getServer().getScheduler().runTaskTimer(addon, this::flyTick, 40L, 10L);
        addon.getServer().getScheduler().runTaskTimer(addon, () -> {
            for (Player p : addon.getServer().getOnlinePlayers()) core.refreshTabName(p);
        }, 100L, 1200L);
    }

    synchronized Data data(UUID id) {
        return players.computeIfAbsent(id, k -> new Data());
    }

    public synchronized boolean active(UUID id) {
        Data d = players.get(id);
        return d != null && d.until > System.currentTimeMillis();
    }

    public synchronized long until(UUID id) {
        return data(id).until;
    }

    public synchronized int months(UUID id) {
        return data(id).months;
    }

    // ---------- Vergabe ----------

    /** Pass verlängern (Store). Jeder angefangene 30-Tage-Block zählt als bezahlter Monat. */
    public synchronized void give(UUID id, int days) {
        Data d = data(id);
        long start = Math.max(System.currentTimeMillis(), d.until);
        d.until = start + days * 86_400_000L;
        int months = Math.max(1, (days + 29) / 30);
        d.months += months;
        long blocks = core.getConfig().getLong("farmpass.claim-blocks-per-month", 250) * months;
        if (blocks > 0) addon.claims().addBlocks(id, blocks);
        save();
        Player p = addon.getServer().getPlayer(id);
        if (p != null) {
            core.refreshTabName(p);
            Msg.send(p, "<green><b>Danke für deinen Farmpass!</b> <gray>Er läuft " + days + " Tage länger. <green>+" + blocks
                    + " Claim-Blöcke<gray>, dazu ein Monats-Haustier: <white>/farmpass pet");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
    }

    public synchronized void take(UUID id) {
        data(id).until = 0;
        save();
        Player p = addon.getServer().getPlayer(id);
        if (p != null) core.refreshTabName(p);
    }

    // ---------- Fliegen ----------

    public synchronized boolean toggleFly(UUID id) {
        Data d = data(id);
        d.fly = !d.fly;
        save();
        return d.fly;
    }

    public synchronized boolean flyOn(UUID id) {
        return data(id).fly;
    }

    /** Darf der Spieler hier mit dem Pass fliegen? */
    boolean flyZone(Player p) {
        Location l = p.getLocation();
        if (addon.farm() != null && addon.farm().isFarm(l.getWorld())) return false;
        Location spawn = core.modes().spawnOf(ModeService.SURVIVAL);
        int r = core.getConfig().getInt("survival-hud.spawn-radius", 80);
        if (spawn != null && spawn.getWorld() == l.getWorld() && spawn.distanceSquared(l) <= (double) r * r) return true;
        for (String id : core.getConfig().getStringList("farmpass.fly-regions")) {
            RegionService.Region reg = core.regions().find(id);
            if (reg != null && reg.contains(l)) return true;
        }
        ClaimService.Claim c = addon.claims().at(l);
        return c != null && addon.claims().isTrusted(c.owner(), p.getUniqueId());
    }

    private void flyTick() {
        long now = System.currentTimeMillis();
        for (Player p : addon.getServer().getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                granted.remove(id);
                continue;
            }
            boolean may = ModeService.SURVIVAL.equals(core.modes().modeOf(id)) && active(id) && flyOn(id) && flyZone(p);
            if (may && !granted.contains(id)) {
                // Nur selbst vergebenes Fliegen verwalten (Admin-/fly bleibt unberührt).
                if (!p.getAllowFlight()) {
                    p.setAllowFlight(true);
                    granted.add(id);
                    p.sendActionBar(Msg.parse("<green>☘ Fliegen erlaubt <gray>(Doppelsprung)"));
                }
            } else if (!may && granted.remove(id)) {
                if (p.isFlying()) fallSafe.put(id, now + 15_000L);
                p.setFlying(false);
                p.setAllowFlight(false);
                p.sendActionBar(Msg.parse("<yellow>Hier kannst du nicht fliegen."));
            }
            Long safe = fallSafe.get(id);
            if (safe != null && (now > safe || p.isOnGround())) {
                if (p.isOnGround()) addon.getServer().getScheduler().runTaskLater(addon, () -> fallSafe.remove(id), 5L);
                else fallSafe.remove(id);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p && fallSafe.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            fallSafe.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (granted.remove(id)) {
            e.getPlayer().setFlying(false);
            e.getPlayer().setAllowFlight(false);
        }
        fallSafe.remove(id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        addon.getServer().getScheduler().runTaskLater(addon, () -> {
            if (!p.isOnline()) return;
            core.refreshTabName(p);
            Data d;
            synchronized (this) {
                d = players.get(p.getUniqueId());
            }
            if (d != null && d.until > 0 && d.until < System.currentTimeMillis()
                    && d.until > System.currentTimeMillis() - 3 * 86_400_000L) {
                Msg.send(p, "<yellow>Dein Farmpass ist abgelaufen. <gray>Verlängern: <white>/farmpass");
            }
        }, 40L);
    }

    // ---------- Wochenkiste ----------

    static String week() {
        LocalDate d = LocalDate.now(ZONE);
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    public synchronized boolean weeklyReady(UUID id) {
        return active(id) && !week().equals(data(id).weekly);
    }

    /** Wochenkiste abholen. Null bei Erfolg, sonst eine Meldung. */
    public synchronized String claimWeekly(Player p) {
        UUID id = p.getUniqueId();
        if (!active(id)) return "Die Wochenkiste gibt es mit dem Farmpass.";
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(id))) return "Die Wochenkiste holst du in Survival ab.";
        Data d = data(id);
        if (week().equals(d.weekly)) return "Diese Woche hast du sie schon geholt. Nächsten Montag gibt es eine neue.";
        d.weekly = week();
        long coins = core.getConfig().getLong("farmpass.weekly-chest.coins", 2500);
        long tokens = core.getConfig().getLong("farmpass.weekly-chest.tokens", 3);
        if (coins > 0) core.economy().deposit(id, coins);
        if (tokens > 0) addon.jobs().addTokens(id, tokens);
        for (String raw : core.getConfig().getStringList("farmpass.weekly-chest.items")) {
            String[] parts = raw.split(";");
            Material m = Material.matchMaterial(parts[0].trim());
            int n;
            try {
                n = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
            } catch (NumberFormatException e) {
                continue;
            }
            if (m == null || !m.isItem() || n <= 0) continue;
            while (n > 0) {
                ItemStack it = new ItemStack(m, Math.min(n, m.getMaxStackSize()));
                n -= it.getAmount();
                p.getInventory().addItem(it).values().forEach(rest -> core.abholbox().add(id, rest));
            }
        }
        save();
        Msg.send(p, "<green><b>Wochenkiste!</b> <gold>+" + Msg.coins(coins) + " Coins <light_purple>+" + tokens
                + " Tokens <gray>und Items (was nicht passt, liegt in der /abholbox).");
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
        return null;
    }

    // ---------- Haustiere ----------

    public List<String> passPets() {
        return core.getConfig().getStringList("farmpass.pets");
    }

    public List<String> monthlyPets() {
        return core.getConfig().getStringList("farmpass.monthly-pets");
    }

    public synchronized List<String> chosenPets(UUID id) {
        return new ArrayList<>(data(id).pets);
    }

    public synchronized int petPicksLeft(UUID id) {
        Data d = data(id);
        return Math.max(0, d.months - d.pets.size());
    }

    /** Monats-Haustier wählen (bleibt für immer). */
    public synchronized String choosePet(UUID id, String pet) {
        Data d = data(id);
        if (!monthlyPets().contains(pet)) return "Dieses Haustier gibt es nicht zur Auswahl.";
        if (d.pets.contains(pet)) return "Das hast du schon.";
        if (petPicksLeft(id) <= 0) return "Für jeden bezahlten Monat gibt es ein Haustier. Gerade hast du keine Auswahl offen.";
        d.pets.add(pet);
        save();
        return null;
    }

    /** Alle Haustiere, die der Spieler gerade haben darf (für 4.13 Haustiere). */
    public synchronized List<String> allowedPets(UUID id) {
        List<String> out = new ArrayList<>(data(id).pets);
        if (active(id)) for (String p : passPets()) if (!out.contains(p)) out.add(p);
        return out;
    }

    // ---------- Speichern ----------

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ps = yml.getConfigurationSection("players");
        if (ps == null) return;
        for (String k : ps.getKeys(false)) {
            try {
                Data d = new Data();
                d.until = ps.getLong(k + ".until");
                d.months = ps.getInt(k + ".months");
                d.pets.addAll(ps.getStringList(k + ".pets"));
                d.weekly = ps.getString(k + ".weekly", "");
                d.fly = ps.getBoolean(k + ".fly", true);
                players.put(UUID.fromString(k), d);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Data> e : players.entrySet()) {
            Data d = e.getValue();
            String b = "players." + e.getKey() + ".";
            out.set(b + "until", d.until);
            out.set(b + "months", d.months);
            out.set(b + "pets", d.pets);
            out.set(b + "weekly", d.weekly);
            out.set(b + "fly", d.fly);
        }
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException ex) {
            addon.getLogger().warning("farmpass.yml: " + ex.getMessage());
        }
    }

    /** Beim Abschalten geschenktes Fliegen zurücknehmen. */
    public void shutdown() {
        for (UUID id : new HashSet<>(granted)) {
            Player p = addon.getServer().getPlayer(id);
            if (p != null) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
        }
        granted.clear();
        save();
    }
}
