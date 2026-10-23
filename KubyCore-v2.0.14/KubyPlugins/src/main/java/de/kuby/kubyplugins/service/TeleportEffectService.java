package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Partikel und Ton bei jedem Teleport über das Plugin. Der gewählte Effekt ist eine Kosmetik vom Typ TELEPORT. */
public final class TeleportEffectService {

    private final KubyPlugins plugin;

    public TeleportEffectService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public void play(Player p, Location at) {
        if (at == null || at.getWorld() == null) return;
        Particle particle = defaultParticle();
        String id = plugin.cosmetics().activeId(p.getUniqueId(), "TELEPORT");
        if (id != null) {
            CosmeticService.Item it = plugin.cosmetics().find(id);
            if (it != null) particle = it.particle();
        }
        at.getWorld().spawnParticle(particle, at.clone().add(0, 1, 0), 50, 0.4, 0.6, 0.4, 0.2);
        de.kuby.kubyplugins.util.Sfx.play(plugin, p, de.kuby.kubyplugins.util.Sfx.Kind.TELEPORT);
    }

    private Particle defaultParticle() {
        try {
            return Particle.valueOf(plugin.getConfig().getString("teleport.default-particle", "PORTAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Particle.PORTAL;
        }
    }
}
