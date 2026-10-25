package de.kuby.kubyplugins.util;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public final class Teleports {

    private Teleports() {}

    /** Teleport nach Wartezeit. Wer sich in dieser Zeit bewegt, wird abgebrochen. */
    public static void delayed(KubyPlugins plugin, Player player, Location target, String label) {
        delayed(plugin, player, () -> target, label);
    }

    /** Wie oben, das Ziel wird aber erst nach der Wartezeit bestimmt (z. B. ein Spieler, der weiterläuft). Null bricht ab. */
    public static void delayed(KubyPlugins plugin, Player player, java.util.function.Supplier<Location> where, String label) {
        if (plugin.pvp().inCombat(player.getUniqueId())) {
            Msg.send(player, "<red>Im Kampf kannst du nicht teleportieren.");
            return;
        }
        int delay = plugin.getConfig().getInt("homes.teleport-delay-seconds", 3);
        Location start = player.getLocation();
        Msg.send(player, "<gray>Teleport zu <white>" + label + " <gray>in <white>" + delay
                + "s<gray>, bleib stehen...");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (plugin.pvp().inCombat(player.getUniqueId())) {
                Msg.send(player, "<red>Teleport abgebrochen, du bist in einen Kampf geraten.");
                return;
            }
            Location now = player.getLocation();
            boolean moved = start.getWorld() != now.getWorld() || start.distanceSquared(now) > 0.25;
            if (moved) {
                Msg.send(player, "<red>Teleport abgebrochen, du hast dich bewegt.");
                return;
            }
            Location target = where.get();
            if (target == null) return;
            Location before = player.getLocation();
            player.teleportAsync(target);
            plugin.teleportFx().play(player, before);
            plugin.teleportFx().play(player, target);
            Msg.send(player, "<green>Teleportiert!");
        }, delay * 20L);
    }

    public static void spawn(KubyPlugins plugin, Player player) {
        org.bukkit.Location target = plugin.modes().spawnOf(plugin.modes().modeOf(player.getUniqueId()));
        if (target == null) {
            Msg.send(player, "<red>Der Spawn für diesen Modus ist noch nicht eingerichtet.");
            return;
        }
        delayed(plugin, player, target, "Spawn");
    }

    public static void randomTeleport(KubyPlugins plugin, Player player) {
        World world = Bukkit.getWorld(plugin.modes().worldOf(plugin.modes().modeOf(player.getUniqueId())));
        if (world == null) {
            Msg.send(player, "<red>Welt nicht gefunden.");
            return;
        }
        int radius = plugin.getConfig().getInt("rtp.radius", 5000);

        for (int attempt = 0; attempt < 10; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(-radius, radius);
            int z = ThreadLocalRandom.current().nextInt(-radius, radius);
            Location loc = findSafe(world, x, z);
            if (loc != null) {
                delayed(plugin, player, loc, "Zufallsort");
                return;
            }
        }
        Msg.send(player, "<red>Kein sicherer Ort gefunden, versuch es nochmal.");
    }

    /** Zufälliger sicherer Ort ohne Teleport, z. B. für Duo-RTP. Null, wenn keiner gefunden wurde. */
    public static Location randomSafeLocation(KubyPlugins plugin) {
        World world = Bukkit.getWorld(plugin.getConfig().getString("rtp.world", "world"));
        if (world == null) return null;
        int radius = plugin.getConfig().getInt("rtp.radius", 5000);
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(-radius, radius);
            int z = ThreadLocalRandom.current().nextInt(-radius, radius);
            Location loc = findSafe(world, x, z);
            if (loc != null) return loc;
        }
        return null;
    }

    private static Location findSafe(World world, int x, int z) {
        world.getChunkAt(new Location(world, x, 64, z)).load(true);
        int y = world.getHighestBlockYAt(x, z);
        Material ground = world.getType(x, y, z);
        if (!ground.isSolid() || ground == Material.CACTUS
                || ground == Material.MAGMA_BLOCK || ground.name().endsWith("LEAVES")) {
            return null;
        }
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }
}
