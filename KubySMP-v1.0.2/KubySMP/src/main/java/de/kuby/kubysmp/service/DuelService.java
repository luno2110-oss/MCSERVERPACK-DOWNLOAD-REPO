package de.kuby.kubysmp.service;

import de.kuby.kubysmp.KubySMP;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Locs;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1v1-Duelle: Inventar wird gesichert, Kit gegeben, danach zurückgegeben.
 * Einsätze gehen an den Gewinner (abzüglich Steuer). Wer ausloggt, verliert,
 * seine Originalsachen wandern in die Abholbox.
 */
public final class DuelService {

    private static final long CHALLENGE_TIMEOUT_MS = 60_000L;

    private record Challenge(UUID from, UUID to, long bet, long createdAt) {}
    private record Snapshot(ItemStack[] contents, Location loc) {}
    private record Duel(UUID a, UUID b, long bet, Snapshot sa, Snapshot sb) {
        Snapshot of(UUID id) { return id.equals(a) ? sa : sb; }
    }

    private final KubyPlugins plugin;
    private final Map<UUID, Challenge> challenges = new ConcurrentHashMap<>(); // Schlüssel: Herausgeforderter
    private final Map<UUID, Duel> duels = new ConcurrentHashMap<>();           // Schlüssel: beide Spieler
    private final Map<UUID, Snapshot> awaitingRespawn = new ConcurrentHashMap<>();

    public DuelService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public UUID pendingChallengerOf(UUID target) {
        Challenge c = challenges.get(target);
        return c == null ? null : c.from();
    }

    public boolean isDueling(UUID id) {
        return duels.containsKey(id);
    }

    public boolean inSameDuel(UUID a, UUID b) {
        Duel d = duels.get(a);
        return d != null && d == duels.get(b);
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String challenge(Player from, Player to, long bet) {
        if (!plugin.discord().linkedOrDisabled(from.getUniqueId(), "require-for-duels")
                || !plugin.discord().linkedOrDisabled(to.getUniqueId(), "require-for-duels")) {
            return "Beide Spieler brauchen einen verknüpften Discord-Account.";
        }
        if (from.equals(to)) return "Du kannst dich nicht selbst herausfordern.";
        if (bet < 0) return "Ungültiger Einsatz.";
        if (isDueling(from.getUniqueId()) || isDueling(to.getUniqueId())) return "Einer der beiden ist gerade im Duell.";
        if (KubySMP.get().combat().inCombat(from.getUniqueId()) || KubySMP.get().combat().inCombat(to.getUniqueId())) {
            return "Einer der beiden ist gerade im Kampf.";
        }
        if (arena(true) == null || arena(false) == null) return "Die Duellarena ist nicht konfiguriert.";
        if (plugin.economy().balance(from.getUniqueId()) < bet) return "Du hast nicht genug$ für diesen Einsatz.";

        challenges.put(to.getUniqueId(), new Challenge(from.getUniqueId(), to.getUniqueId(), bet, System.currentTimeMillis()));
        Msg.send(to, "<gold>" + from.getName() + " <gray>fordert dich zum Duell heraus"
                + (bet > 0 ? " (Einsatz <gold>" + Msg.coins(bet) + "$<gray>)" : "")
                + ". Annehmen mit <white>/duell accept");
        return null;
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String accept(Player to) {
        Challenge c = challenges.remove(to.getUniqueId());
        if (c == null) return "Keine offene Herausforderung.";
        if (System.currentTimeMillis() - c.createdAt() > CHALLENGE_TIMEOUT_MS) return "Die Herausforderung ist abgelaufen.";
        Player from = Bukkit.getPlayer(c.from());
        if (from == null) return "Der Herausforderer ist offline.";
        if (isDueling(from.getUniqueId()) || isDueling(to.getUniqueId())) return "Einer der beiden ist bereits im Duell.";
        if (KubySMP.get().combat().inCombat(from.getUniqueId()) || KubySMP.get().combat().inCombat(to.getUniqueId())) {
            return "Einer der beiden ist gerade im Kampf.";
        }
        Location aLoc = arena(true);
        Location bLoc = arena(false);
        if (aLoc == null || bLoc == null) return "Die Duellarena ist nicht konfiguriert.";

        long bet = c.bet();
        if (bet > 0) {
            if (!plugin.economy().withdraw(from.getUniqueId(), bet)) return "Der Herausforderer hat nicht mehr genug$.";
            if (!plugin.economy().withdraw(to.getUniqueId(), bet)) {
                plugin.economy().deposit(from.getUniqueId(), bet);
                return "Du hast nicht genug$ für den Einsatz.";
            }
        }

        Snapshot sa = snapshot(from);
        Snapshot sb = snapshot(to);
        Duel d = new Duel(from.getUniqueId(), to.getUniqueId(), bet, sa, sb);
        duels.put(from.getUniqueId(), d);
        duels.put(to.getUniqueId(), d);
        prepare(from, aLoc);
        prepare(to, bLoc);

        String stake = bet > 0 ? " <gray>Einsatz: <gold>" + Msg.coins(bet) + "$" : "";
        Msg.send(from, "<green>Duell gegen <white>" + to.getName() + " <green>gestartet!" + stake);
        Msg.send(to, "<green>Duell gegen <white>" + from.getName() + " <green>gestartet!" + stake);
        Sfx.play(plugin, from, Sfx.Kind.DUEL_START);
        Sfx.play(plugin, to, Sfx.Kind.DUEL_START);
        return null;
    }

    /** Liefert true, wenn der Tod zu einem Duell gehörte. Dann übernimmt das Duell die Folgen. */
    public synchronized List<UUID> activeDuelPlayers() {
        return new ArrayList<>(duels.keySet());
    }

    /** Admin: Duell abbrechen. Beide Einsätze gehen zurück, beide Spieler bekommen ihr Inventar zurück. */
    public synchronized void adminCancel(UUID id) {
        Duel d = duels.get(id);
        if (d == null) return;
        duels.remove(d.a());
        duels.remove(d.b());
        if (d.bet() > 0) {
            plugin.economy().deposit(d.a(), d.bet());
            plugin.economy().deposit(d.b(), d.bet());
        }
        for (UUID u : List.of(d.a(), d.b())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                restore(p, d.of(u));
                Msg.send(p, "<gray>Das Duell wurde abgebrochen, deine Einsätze sind zurück.");
            }
        }
    }

    /** Startet ein Turnier-Match ohne Einsatz und ohne Herausforderung. */
    public synchronized String startMatch(Player a, Player b) {
        if (isDueling(a.getUniqueId()) || isDueling(b.getUniqueId())) return "Einer der beiden ist bereits im Duell.";
        if (plugin.trades().inTrade(a.getUniqueId()) || plugin.trades().inTrade(b.getUniqueId())) return "Einer der beiden handelt gerade.";
        Location aLoc = arena(true);
        Location bLoc = arena(false);
        if (aLoc == null || bLoc == null) return "Die Duellarena ist nicht konfiguriert.";
        Snapshot sa = snapshot(a);
        Snapshot sb = snapshot(b);
        Duel d = new Duel(a.getUniqueId(), b.getUniqueId(), 0, sa, sb);
        duels.put(a.getUniqueId(), d);
        duels.put(b.getUniqueId(), d);
        prepare(a, aLoc);
        prepare(b, bLoc);
        Msg.send(a, "<gold>Turnier-Match gegen <white>" + b.getName() + " <gold>gestartet!");
        Msg.send(b, "<gold>Turnier-Match gegen <white>" + a.getName() + " <gold>gestartet!");
        Sfx.play(plugin, a, Sfx.Kind.DUEL_START);
        Sfx.play(plugin, b, Sfx.Kind.DUEL_START);
        return null;
    }

    public synchronized boolean handleDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Duel d = duels.get(victim.getUniqueId());
        if (d == null) return false;

        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.setKeepLevel(true);
        e.deathMessage(null);

        UUID winner = d.a().equals(victim.getUniqueId()) ? d.b() : d.a();
        awaitingRespawn.put(victim.getUniqueId(), d.of(victim.getUniqueId()));
        endDuel(d, winner, victim.getUniqueId());
        return true;
    }

    /** Logout während eines Duells: Verlust, Originalsachen in die Abholbox. */
    public synchronized void handleQuit(Player p) {
        Duel d = duels.get(p.getUniqueId());
        if (d == null) return;
        UUID loser = p.getUniqueId();
        UUID winner = d.a().equals(loser) ? d.b() : d.a();
        for (ItemStack it : d.of(loser).contents()) {
            if (it != null && !it.getType().isAir()) plugin.abholbox().add(loser, it);
        }
        p.getInventory().clear();
        endDuel(d, winner, loser);
    }

    public void onRespawn(PlayerRespawnEvent e) {
        Snapshot s = awaitingRespawn.remove(e.getPlayer().getUniqueId());
        if (s == null) return;
        e.setRespawnLocation(s.loc());
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) restore(p, s);
        }, 1L);
    }

    private void endDuel(Duel d, UUID winnerId, UUID loserId) {
        duels.remove(d.a());
        duels.remove(d.b());

        long pot = d.bet() * 2;
        long tax = pot * plugin.getConfig().getInt("duel.tax-percent", 5) / 100;
        if (pot > 0) plugin.economy().deposit(winnerId, pot - tax);

        var winStats = plugin.stats().get(winnerId);
        winStats.kills++;
        if (!KubySMP.get().tournaments().isMatchPair(winnerId, loserId)) KubySMP.get().trophies().transfer(winnerId, loserId);
        plugin.battlepass().progress(winnerId, "duel", 1);
        var loseStats = plugin.stats().get(loserId);
        loseStats.deaths++;

        Player winner = Bukkit.getPlayer(winnerId);
        if (winner != null) {
            restore(winner, d.of(winnerId));
            Msg.send(winner, "<green>Du hast das Duell gewonnen! <gray>+" + Msg.coins(pot - tax) + "$");
            Sfx.play(plugin, winner, Sfx.Kind.WIN);
        }
        Player loser = Bukkit.getPlayer(loserId);
        if (loser != null && loser.isOnline()) {
            Msg.send(loser, "<red>Du hast das Duell verloren.");
            Sfx.play(plugin, loser, Sfx.Kind.LOSE);
        }
        // Verzögert, damit Inventar und Position beider Spieler zuerst zurückgesetzt sind
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> KubySMP.get().tournaments().onDuelEnd(winnerId, loserId), 60L);
    }

    private Snapshot snapshot(Player p) {
        Snapshot s = new Snapshot(p.getInventory().getContents().clone(), p.getLocation());
        p.getInventory().clear();
        return s;
    }

    private void prepare(Player p, Location loc) {
        giveKit(p);
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) p.setHealth(max.getValue());
        p.setFoodLevel(20);
        p.teleportAsync(loc);
    }

    private void restore(Player p, Snapshot s) {
        p.getInventory().clear();
        p.getInventory().setContents(s.contents());
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) p.setHealth(max.getValue());
        p.teleportAsync(s.loc());
    }

    private void giveKit(Player p) {
        for (String entry : plugin.getConfig().getStringList("duel.kit")) {
            String[] parts = entry.split(":");
            Material m = Material.matchMaterial(parts[0].toUpperCase(Locale.ROOT));
            if (m == null) continue;
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }
            ItemStack stack = new ItemStack(m, amount);
            String name = m.name();
            if (name.endsWith("_HELMET")) p.getInventory().setHelmet(stack);
            else if (name.endsWith("_CHESTPLATE")) p.getInventory().setChestplate(stack);
            else if (name.endsWith("_LEGGINGS")) p.getInventory().setLeggings(stack);
            else if (name.endsWith("_BOOTS")) p.getInventory().setBoots(stack);
            else p.getInventory().addItem(stack);
        }
    }

    private Location arena(boolean first) {
        return Locs.parse(plugin.getConfig().getString(first ? "duel.arena-a" : "duel.arena-b"));
    }
}
