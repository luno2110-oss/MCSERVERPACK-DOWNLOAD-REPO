package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sound und schwebender Text für Casino-Ergebnisse. Der Text steigt über dem Tisch auf und verschwindet nach
 * zwei Sekunden. Er wird nicht gespeichert, bleibt also auch bei einem Neustart nicht hängen.
 */
public final class CasinoFx {

    public enum Result { WIN, BIG_WIN, PUSH, LOSE, JACKPOT }

    private static final long SHOWN_TICKS = 40L;
    private static final double JACKPOT_RANGE = 30;

    private CasinoFx() {
    }

    /** Spielt den Sound für den Spieler und zeigt den Betrag (Chips, mit Vorzeichen) über dem Tisch. */
    public static void show(KubyPlugins plugin, UUID id, Location at, Result result, long net) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) Sfx.play(plugin, p, sound(result));
        String amount = net > 0 ? "<green>+" + net : net < 0 ? "<red>" + net : "<gray>±0";
        String text = result == Result.BIG_WIN
                ? "<gold><b>GROSSER GEWINN</b> " + amount + " Chips"
                : amount + " Chips";
        floating(plugin, at, "<white>" + nameOf(id) + " " + text);
    }

    /** Jackpot: eigener Text, Partikel und Sound für alle in der Nähe. */
    public static void jackpot(KubyPlugins plugin, UUID id, Location at, long money) {
        floating(plugin, at, "<gold><b>JACKPOT!</b> <white>" + nameOf(id) + " <yellow>+" + Msg.coins(money) + "$");
        if (at == null || at.getWorld() == null) return;
        at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 120, 0.6, 1.0, 0.6, 0.35);
        for (Player near : at.getWorld().getPlayers()) {
            if (near.getLocation().distanceSquared(at) <= JACKPOT_RANGE * JACKPOT_RANGE) {
                Sfx.play(plugin, near, Sfx.Kind.JACKPOT);
            }
        }
    }

    private static void floating(KubyPlugins plugin, Location at, String mini) {
        if (at == null || at.getWorld() == null) return;
        if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return;
        Location start = at.clone().add(ThreadLocalRandom.current().nextDouble(-0.3, 0.3), 0, 0);
        TextDisplay d = at.getWorld().spawn(start, TextDisplay.class, e -> {
            e.text(Msg.parse(mini));
            e.setBillboard(Display.Billboard.CENTER);
            e.setPersistent(false);
            e.setTeleportDuration(30);
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> d.teleport(start.clone().add(0, 1.2, 0)), 2L);
        plugin.getServer().getScheduler().runTaskLater(plugin, d::remove, SHOWN_TICKS);
    }

    private static Sfx.Kind sound(Result r) {
        return switch (r) {
            case WIN -> Sfx.Kind.WIN;
            case BIG_WIN -> Sfx.Kind.VICTORY;
            case PUSH -> Sfx.Kind.TICK;
            case LOSE -> Sfx.Kind.LOSE;
            case JACKPOT -> Sfx.Kind.JACKPOT;
        };
    }

    private static String nameOf(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? "Jemand" : name;
    }
}
