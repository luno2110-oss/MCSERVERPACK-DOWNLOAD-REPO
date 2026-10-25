package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.EventExecutor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Votes über NuVotifier/Votifier. Die Anbindung läuft zur Laufzeit über das VotifierEvent, KubyCore braucht
 * Votifier also nicht zum Starten. Jeder Vote: Juwelen (modusübergreifend), Coins im aktuellen Modus und beim
 * ersten Vote der Home-Bonus. Wer offline votet, bekommt die Coins beim nächsten Einloggen.
 * Serie: Wer an aufeinanderfolgenden Tagen votet, bekommt ab 10/25/50 Tagen beim ersten Vote des Tages Extra-Coins
 * (vote.streak-bonus). Monatswertung: Die besten Voter des Vormonats bekommen Juwelen (vote.monthly-prizes).
 */
public final class VoteService implements Listener {

    private static final String EVENT_CLASS = "com.vexsoftware.votifier.model.VotifierEvent";

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Integer> votes = new HashMap<>();
    private final Map<UUID, Long> pendingCoins = new HashMap<>();
    private final Map<UUID, String> lastDay = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();
    private final Map<UUID, Integer> monthly = new HashMap<>();
    private String month = "";
    private boolean hooked;

    private static final java.time.ZoneId ZONE = java.time.ZoneId.of("Europe/Berlin");

    public VoteService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "votes.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection v = yml.getConfigurationSection("votes");
        if (v != null) for (String key : v.getKeys(false)) parse(key, id -> votes.put(id, v.getInt(key)));
        ConfigurationSection p = yml.getConfigurationSection("pending");
        if (p != null) for (String key : p.getKeys(false)) parse(key, id -> pendingCoins.put(id, p.getLong(key)));
        ConfigurationSection s = yml.getConfigurationSection("streak");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                parse(key, id -> {
                    lastDay.put(id, s.getString(key + ".day", ""));
                    streaks.put(id, s.getInt(key + ".count"));
                });
            }
        }
        month = yml.getString("month", currentMonth());
        ConfigurationSection m = yml.getConfigurationSection("monthly");
        if (m != null) for (String key : m.getKeys(false)) parse(key, id -> monthly.put(id, m.getInt(key)));
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkMonth, 1200L, 72_000L);
    }

    private static String currentMonth() {
        return java.time.YearMonth.now(ZONE).toString();
    }

    /** Aktuelle Serie (0, wenn gestern und heute nicht gevotet wurde). */
    public synchronized int streak(UUID id) {
        String d = lastDay.get(id);
        java.time.LocalDate today = java.time.LocalDate.now(ZONE);
        if (d == null || !(d.equals(today.toString()) || d.equals(today.minusDays(1).toString()))) return 0;
        return streaks.getOrDefault(id, 0);
    }

    /** Extra-Coins für eine Serie (höchste erreichte Stufe). */
    public long streakBonus(int streak) {
        long bonus = 0;
        int best = 0;
        for (String raw : plugin.getConfig().getStringList("vote.streak-bonus")) {
            String[] parts = raw.split(";");
            try {
                int days = Integer.parseInt(parts[0].trim());
                if (streak >= days && days >= best) {
                    best = days;
                    bonus = Long.parseLong(parts[1].trim());
                }
            } catch (RuntimeException ignored) {
            }
        }
        return bonus;
    }

    public synchronized int monthlyVotes(UUID id) {
        return monthly.getOrDefault(id, 0);
    }

    public synchronized java.util.List<Map.Entry<UUID, Integer>> monthlyTop(int n) {
        java.util.List<Map.Entry<UUID, Integer>> list = new java.util.ArrayList<>(monthly.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list.subList(0, Math.min(n, list.size()));
    }

    /** Monatswechsel: Preise an die besten Voter des Vormonats, dann neu zählen. */
    private synchronized void checkMonth() {
        String now = currentMonth();
        if (now.equals(month)) return;
        java.util.List<Map.Entry<UUID, Integer>> top = monthlyTop(10);
        for (String raw : plugin.getConfig().getStringList("vote.monthly-prizes")) {
            String[] parts = raw.split(";");
            try {
                int place = Integer.parseInt(parts[0].trim());
                long jewels = Long.parseLong(parts[1].trim());
                if (place < 1 || place > top.size() || jewels <= 0) continue;
                UUID id = top.get(place - 1).getKey();
                plugin.jewels().add(id, jewels);
                OfflinePlayer op = Bukkit.getOfflinePlayer(id);
                Bukkit.broadcast(Msg.parse("<green>Voter des Monats " + month + ", Platz " + place + ": <white>"
                        + (op.getName() == null ? "?" : op.getName()) + " <gray>(" + top.get(place - 1).getValue()
                        + " Votes) <light_purple>+" + jewels + " Juwelen"));
            } catch (RuntimeException ignored) {
            }
        }
        monthly.clear();
        month = now;
        save();
    }

    /** Meldet sich beim VotifierEvent an, falls NuVotifier oder Votifier installiert ist. */
    @SuppressWarnings("unchecked")
    public void hook() {
        Class<? extends Event> eventClass;
        try {
            eventClass = (Class<? extends Event>) Class.forName(EVENT_CLASS);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("Votifier nicht gefunden. Votes gehen nur über /kp vote <Spieler>.");
            return;
        }
        EventExecutor executor = (listener, event) -> {
            if (!eventClass.isInstance(event)) return;
            try {
                Object vote = event.getClass().getMethod("getVote").invoke(event);
                Method user = vote.getClass().getMethod("getUsername");
                Method service = vote.getClass().getMethod("getServiceName");
                String name = String.valueOf(user.invoke(vote));
                String site = String.valueOf(service.invoke(vote));
                // Votifier kann das Event außerhalb des Haupt-Threads feuern.
                Bukkit.getScheduler().runTask(plugin, () -> handle(name, site));
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().warning("Vote konnte nicht gelesen werden: " + e.getMessage());
            }
        };
        Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.NORMAL, executor, plugin);
        hooked = true;
        plugin.getLogger().info("Votifier angebunden.");
    }

    public boolean hooked() {
        return hooked;
    }

    /** Verarbeitet einen Vote nach Spielernamen (Votifier oder /kp vote). */
    /** Addons hängen sich hier an Votes an (z. B. Tokens in Survival). */
    private final java.util.List<java.util.function.Consumer<UUID>> hooks = new java.util.ArrayList<>();

    public void registerHook(java.util.function.Consumer<UUID> hook) {
        hooks.add(hook);
    }

    public synchronized void handle(String name, String site) {
        OfflinePlayer op = Bukkit.getPlayerExact(name);
        if (op == null) op = Bukkit.getOfflinePlayerIfCached(name);
        if (op == null) {
            plugin.getLogger().info("Vote von unbekanntem Spieler " + name + " (" + site + ") ignoriert.");
            return;
        }
        UUID id = op.getUniqueId();
        checkMonth();
        int count = votes.merge(id, 1, Integer::sum);
        monthly.merge(id, 1, Integer::sum);
        // Serie: erster Vote des Tages zählt.
        String today = java.time.LocalDate.now(ZONE).toString();
        long streakCoins = 0;
        int streak = streaks.getOrDefault(id, 0);
        if (!today.equals(lastDay.get(id))) {
            String yesterday = java.time.LocalDate.now(ZONE).minusDays(1).toString();
            streak = yesterday.equals(lastDay.get(id)) ? streak + 1 : 1;
            streaks.put(id, streak);
            lastDay.put(id, today);
            streakCoins = streakBonus(streak);
            if (streakCoins > 0) pendingCoins.merge(id, streakCoins, Long::sum);
        }
        long jewels = plugin.getConfig().getLong("vote.jewels", 2);
        long coins = plugin.getConfig().getLong("vote.coins", 250);
        if (jewels > 0) plugin.jewels().add(id, jewels);
        int voteKeys = plugin.getConfig().getInt("vote.keys", 1);
        if (voteKeys > 0) {
            String mode = plugin.modes().modeOf(id);
            plugin.keys().give(id, ModeService.HUB.equals(mode) ? ModeService.SURVIVAL : mode, "vote", voteKeys);
        }
        boolean firstBonus = !plugin.homes().voted(id);
        if (firstBonus) plugin.homes().grantVote(id);
        if (coins > 0) pendingCoins.merge(id, coins, Long::sum);
        save();
        for (java.util.function.Consumer<UUID> h : hooks) {
            try {
                h.accept(id);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Vote-Hook: " + ex.getMessage());
            }
        }
        String shown = op.getName() == null ? name : op.getName();
        Bukkit.broadcast(Msg.parse("<green>" + shown + " <gray>hat bei <white>" + Msg.escape(site)
                + " <gray>gevotet und bekommt eine Belohnung. <green>/vote"));
        Player p = op.getPlayer();
        if (p != null) {
            Msg.send(p, "<green>Danke fürs Voten! <gray>(" + count + ". Vote, Serie " + streak + " Tage) <light_purple>+" + jewels + " Juwelen"
                    + (streakCoins > 0 ? " <gold>+" + Msg.coins(streakCoins) + " Coins Serien-Extra" : "")
                    + (firstBonus ? " <gold>· +1 Home-Platz freigeschaltet" : ""));
            Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
            payPending(p);
        }
    }

    /** Coins aus Votes gutschreiben (beim Einloggen oder direkt nach dem Vote). */
    public synchronized void payPending(Player p) {
        Long coins = pendingCoins.remove(p.getUniqueId());
        if (coins == null || coins <= 0) return;
        plugin.economy().deposit(p.getUniqueId(), coins);
        Msg.send(p, "<green>Vote-Belohnung: <gold>+" + Msg.coins(coins) + " Coins");
        save();
    }

    /** Offene Vote-Coins kurz nach dem Einloggen gutschreiben (wenn der Modus geladen ist). */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) payPending(p);
        }, 60L);
    }

    public synchronized long votesOf(UUID id) {
        return votes.getOrDefault(id, 0);
    }

    /** /vote: Vote-Seiten als Links und die eigene Anzahl. */
    public void showLinks(Player p) {
        Msg.send(p, "<green><b>Voten</b> <gray>– jeder Vote: <light_purple>+" + plugin.getConfig().getLong("vote.jewels", 2)
                + " Juwelen <gray>und <gold>+" + Msg.coins(plugin.getConfig().getLong("vote.coins", 250)) + " Coins");
        boolean any = false;
        for (String raw : plugin.getConfig().getStringList("vote.links")) {
            String[] parts = raw.split(";", 2);
            if (parts.length < 2) continue;
            any = true;
            Msg.send(p, "<gray>▪ <click:open_url:'" + parts[1].trim() + "'><aqua><u>" + Msg.escape(parts[0].trim())
                    + "</u></aqua></click>");
        }
        if (!any) Msg.send(p, "<gray>Noch keine Vote-Seiten eingetragen (config.yml: vote.links).");
        UUID id = p.getUniqueId();
        int streak = streak(id);
        Msg.send(p, "<gray>Deine Votes: <white>" + votesOf(id) + " <dark_gray>· <gray>diesen Monat: <white>" + monthlyVotes(id)
                + " <dark_gray>· <gray>Serie: <white>" + streak + (streak == 1 ? " Tag" : " Tage"));
        StringBuilder tiers = new StringBuilder();
        for (String raw : plugin.getConfig().getStringList("vote.streak-bonus")) {
            String[] parts = raw.split(";");
            if (parts.length < 2) continue;
            if (tiers.length() > 0) tiers.append("<dark_gray>, ");
            tiers.append("<white>ab ").append(parts[0].trim()).append(" Tagen <gold>+").append(parts[1].trim());
        }
        if (tiers.length() > 0) Msg.send(p, "<gray>Serien-Extra pro Vote-Tag: " + tiers);
        java.util.List<Map.Entry<UUID, Integer>> top = monthlyTop(5);
        if (!top.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (Map.Entry<UUID, Integer> e : top) {
                String n = Bukkit.getOfflinePlayer(e.getKey()).getName();
                if (sb.length() > 0) sb.append("<dark_gray>, ");
                sb.append("<white>").append(i++).append(". ").append(n == null ? "?" : n).append(" <gray>(").append(e.getValue()).append(")");
            }
            Msg.send(p, "<gray>Voter des Monats: " + sb + " <dark_gray>· Preise am Monatsende");
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> e : votes.entrySet()) out.set("votes." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, Long> e : pendingCoins.entrySet()) out.set("pending." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, String> e : lastDay.entrySet()) {
            out.set("streak." + e.getKey() + ".day", e.getValue());
            out.set("streak." + e.getKey() + ".count", streaks.getOrDefault(e.getKey(), 0));
        }
        out.set("month", month);
        for (Map.Entry<UUID, Integer> e : monthly.entrySet()) out.set("monthly." + e.getKey(), e.getValue());
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("votes.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private static void parse(String key, java.util.function.Consumer<UUID> action) {
        try {
            action.accept(UUID.fromString(key));
        } catch (IllegalArgumentException ignored) {
        }
    }
}
