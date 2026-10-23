package de.kp.jumprun.run;

import de.kp.jumprun.KPJumpRun;
import de.kp.jumprun.arena.Arena;
import de.kp.jumprun.stats.StatsManager;
import de.kp.jumprun.util.Announcer;
import de.kp.jumprun.util.Text;
import de.kp.jumprun.util.TimeFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Verwaltet alle laufenden Runs: Start, Neustart, Ziel, Tod und die Anzeige (Stoppuhr, Kopf).
 */
public final class RunManager {

    private static final long TICK_PERIOD = 2L;
    private static final int HEAD_UPDATE_TICKS = 20;

    private final KPJumpRun plugin;
    private final StatsManager stats;
    private final RunItems items;

    private final Map<UUID, Run> runs = new HashMap<>();
    /** Hotbar-Inhalte, die nach einem Tod beim Respawn zurückkommen. */
    private final Map<UUID, Map<Integer, ItemStack>> pendingRestore = new HashMap<>();

    private int ticks;

    public RunManager(KPJumpRun plugin, StatsManager stats, RunItems items) {
        this.plugin = plugin;
        this.stats = stats;
        this.items = items;
    }

    public Run get(UUID uuid) {
        return runs.get(uuid);
    }

    public RunItems items() {
        return items;
    }

    public void start(Player player, Arena arena, int groundBlockY) {
        Run run = new Run(arena.getName(), groundBlockY);

        for (int slot : RunItems.SLOTS) {
            run.saveSlot(slot, player.getInventory().getItem(slot));
        }

        runs.put(player.getUniqueId(), run);
        // Kein Teleport: der Spieler steht bereits auf dem Start-Block und läuft direkt los
        applyItems(player, run);
        Announcer.announce(player, arena, "start", Map.of());
    }

    /** Setzt den Run auf den Start zurück. Mit fail = true zählt der Versuch als Fail. */
    public void restart(Player player, boolean fail) {
        Run run = runs.get(player.getUniqueId());
        if (run == null) {
            return;
        }

        Arena arena = plugin.arenas().get(run.getArena());
        if (arena == null || arena.getStart() == null) {
            end(player, true);
            return;
        }

        if (fail) {
            stats.recordFail(arena.getName(), player.getUniqueId(), player.getName());
            Announcer.announce(player, arena, "fail-start", Map.of());
        }

        run.resetTimer();
        run.setLastGroundBlockY(arena.getStart().getBlockY());
        player.getInventory().setItem(RunItems.FEATHER_SLOT, items.feather());
        teleportToStart(player, arena);
        updateHead(player, run);
    }

    public void finish(Player player) {
        Run run = runs.get(player.getUniqueId());
        if (run == null) {
            return;
        }

        Arena arena = plugin.arenas().get(run.getArena());
        long millis = run.elapsed();
        stats.recordFinish(run.getArena(), player.getUniqueId(), player.getName(), millis);

        Announcer.announce(player, arena, "finish", Map.of("time", TimeFormat.format(millis)));
        celebrate(player, millis);
        end(player, true);

        if (arena != null && arena.getExit() != null) {
            player.teleport(arena.getExit());
        }
    }

    /** Zieleffekt: Titel, Feuerwerk und Partikel. */
    private void celebrate(Player player, long millis) {
        Location loc = player.getLocation().add(0, 1, 0);
        World world = loc.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.FIREWORK, loc, 80, 0.6, 0.8, 0.6, 0.15);
            world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 40, 0.8, 1.0, 0.8, 0.0);

            Firework firework = world.spawn(loc, Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.LIME, Color.AQUA)
                    .withFade(Color.WHITE)
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .flicker(true)
                    .trail(true)
                    .build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
        }

        player.showTitle(Title.title(
                Text.mm("<#3dff6b>Ziel erreicht!</#3dff6b>"),
                Text.mm("<white>" + TimeFormat.format(millis) + "</white>"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))));
    }

    /** Run abbrechen, ohne dass es ein Fail ist. Geht zum Rückkehr-Punkt, falls gesetzt. */
    public void quit(Player player) {
        Run run = runs.get(player.getUniqueId());
        if (run == null) {
            return;
        }

        Arena arena = plugin.arenas().get(run.getArena());
        end(player, true);
        Text.send(player, "Run beendet.");

        if (arena != null && arena.getExit() != null) {
            player.teleport(arena.getExit());
        }
    }

    /** Beendet den Run und stellt die Hotbar wieder her. */
    public void end(Player player, boolean restoreNow) {
        Run run = runs.remove(player.getUniqueId());
        if (run == null) {
            return;
        }

        player.sendActionBar(Component.empty());

        if (restoreNow) {
            restoreSlots(player, run.savedSlots());
        } else {
            pendingRestore.put(player.getUniqueId(), run.savedSlots());
        }
    }

    /**
     * Fall nach einem Checkpoint: Teleport zum letzten Checkpoint, Stoppuhr läuft weiter, zählt als Fail.
     */
    public void respawnAtCheckpoint(Player player) {
        Run run = runs.get(player.getUniqueId());
        if (run == null) {
            return;
        }

        Arena arena = plugin.arenas().get(run.getArena());
        int index = run.lastCheckpoint();
        if (arena == null || index < 0 || index >= arena.getCheckpoints().size()) {
            restart(player, true);
            return;
        }

        Location checkpoint = arena.getCheckpoints().get(index);
        stats.recordFail(arena.getName(), player.getUniqueId(), player.getName());
        Announcer.announce(player, arena, "fail-checkpoint", Map.of());

        run.setLastGroundBlockY(checkpoint.getBlockY());
        player.teleport(checkpoint.clone().add(0.5, 1, 0.5));
        updateHead(player, run);
    }

    /** Tod während eines Runs: zählt als Fail, Run-Items werden nicht gedroppt. */
    public void onDeath(Player player, List<ItemStack> drops) {
        Run run = runs.get(player.getUniqueId());
        if (run == null) {
            return;
        }

        drops.removeIf(items::isRunItem);
        stats.recordFail(run.getArena(), player.getUniqueId(), player.getName());
        end(player, false);
    }

    public void restorePending(Player player) {
        Map<Integer, ItemStack> saved = pendingRestore.remove(player.getUniqueId());
        if (saved != null) {
            Bukkit.getScheduler().runTask(plugin, () -> restoreSlots(player, saved));
        }
    }

    /** Zeigt Stoppuhr (Actionbar) und aktualisiert den Kopf jede Sekunde. */
    public void startTicker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                tick();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Fehler bei der Stoppuhr", t);
            }
        }, 0L, TICK_PERIOD);
    }

    private void tick() {
        ticks += (int) TICK_PERIOD;
        boolean updateHeads = ticks % HEAD_UPDATE_TICKS == 0;

        for (Map.Entry<UUID, Run> entry : runs.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            Run run = entry.getValue();
            player.sendActionBar(Text.mm("<gold>⏱ <white>" + TimeFormat.format(run.elapsed()) + "</white>"));
            if (updateHeads) {
                updateHead(player, run);
            }
        }
    }

    private void applyItems(Player player, Run run) {
        player.getInventory().setItem(RunItems.FEATHER_SLOT, items.feather());
        player.getInventory().setItem(RunItems.RESTART_SLOT, items.restart());
        player.getInventory().setItem(RunItems.QUIT_SLOT, items.quit());
        updateHead(player, run);
    }

    /**
     * Setzt den Kopf auf den Spieler, der im Rang direkt über dem Spieler steht.
     * Ohne eigene Zeit wird der Letzte der Liste angezeigt.
     */
    private void updateHead(Player player, Run run) {
        List<Map.Entry<UUID, StatsManager.Record>> rank = stats.ranking(run.getArena());

        int myIndex = -1;
        for (int i = 0; i < rank.size(); i++) {
            if (rank.get(i).getKey().equals(player.getUniqueId())) {
                myIndex = i;
                break;
            }
        }

        ItemStack head;
        if (rank.isEmpty()) {
            head = items.head(null, "Niemand", "<gray>Noch keine Zeit in der Bestenliste</gray>");
        } else if (myIndex == 0) {
            head = items.head(null, "Niemand", "<gray>Du führst die Bestenliste an</gray>");
        } else {
            Map.Entry<UUID, StatsManager.Record> above =
                    myIndex == -1 ? rank.get(rank.size() - 1) : rank.get(myIndex - 1);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(above.getKey());
            String name = above.getValue().name().isEmpty() ? "Unbekannt" : above.getValue().name();
            String time = "<gray>Zeit: <white>" + TimeFormat.format(above.getValue().bestMillis()) + "</white></gray>";
            head = items.head(owner, name, time);
        }

        player.getInventory().setItem(RunItems.HEAD_SLOT, head);
    }

    private void teleportToStart(Player player, Arena arena) {
        Location spawn = arena.getStart().clone().add(0.5, 1, 0.5);
        player.teleport(spawn);
    }

    private void restoreSlots(Player player, Map<Integer, ItemStack> saved) {
        for (Map.Entry<Integer, ItemStack> entry : saved.entrySet()) {
            player.getInventory().setItem(entry.getKey(), entry.getValue());
        }
    }
}
