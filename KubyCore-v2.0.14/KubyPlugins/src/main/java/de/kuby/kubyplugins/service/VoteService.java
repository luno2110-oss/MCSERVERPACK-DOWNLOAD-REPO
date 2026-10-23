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
 */
public final class VoteService implements Listener {

    private static final String EVENT_CLASS = "com.vexsoftware.votifier.model.VotifierEvent";

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Integer> votes = new HashMap<>();
    private final Map<UUID, Long> pendingCoins = new HashMap<>();
    private boolean hooked;

    public VoteService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "votes.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection v = yml.getConfigurationSection("votes");
        if (v != null) for (String key : v.getKeys(false)) parse(key, id -> votes.put(id, v.getInt(key)));
        ConfigurationSection p = yml.getConfigurationSection("pending");
        if (p != null) for (String key : p.getKeys(false)) parse(key, id -> pendingCoins.put(id, p.getLong(key)));
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
    public synchronized void handle(String name, String site) {
        OfflinePlayer op = Bukkit.getPlayerExact(name);
        if (op == null) op = Bukkit.getOfflinePlayerIfCached(name);
        if (op == null) {
            plugin.getLogger().info("Vote von unbekanntem Spieler " + name + " (" + site + ") ignoriert.");
            return;
        }
        UUID id = op.getUniqueId();
        int count = votes.merge(id, 1, Integer::sum);
        long jewels = plugin.getConfig().getLong("vote.jewels", 2);
        long coins = plugin.getConfig().getLong("vote.coins", 250);
        if (jewels > 0) plugin.jewels().add(id, jewels);
        boolean firstBonus = !plugin.homes().voted(id);
        if (firstBonus) plugin.homes().grantVote(id);
        if (coins > 0) pendingCoins.merge(id, coins, Long::sum);
        save();
        String shown = op.getName() == null ? name : op.getName();
        Bukkit.broadcast(Msg.parse("<green>" + shown + " <gray>hat bei <white>" + Msg.escape(site)
                + " <gray>gevotet und bekommt eine Belohnung. <green>/vote"));
        Player p = op.getPlayer();
        if (p != null) {
            Msg.send(p, "<green>Danke fürs Voten! <gray>(" + count + ". Vote) <light_purple>+" + jewels + " Juwelen"
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
        Msg.send(p, "<gray>Deine Votes: <white>" + votesOf(p.getUniqueId()));
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> e : votes.entrySet()) out.set("votes." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, Long> e : pendingCoins.entrySet()) out.set("pending." + e.getKey(), e.getValue());
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
