package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Befehle nur in bestimmten Modi (config: modus-befehle, z. B. battlepass: [smp], marry: [survival]). Wer in einem
 * anderen Modus ist, bekommt eine Meldung statt des Befehls; Admins dürfen überall.
 */
public final class ModeCommandListener implements Listener {

    private final KubyPlugins plugin;

    public ModeCommandListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("modus-befehle");
        if (s == null || e.getPlayer().hasPermission("kuby.admin")) return;
        String msg = e.getMessage();
        if (msg.length() < 2) return;
        String label = msg.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (!s.contains(label)) return;
        List<String> modes = s.getStringList(label);
        String mode = plugin.modes().modeOf(e.getPlayer().getUniqueId());
        if (modes.isEmpty() || modes.contains(mode)) return;
        e.setCancelled(true);
        List<String> names = new ArrayList<>();
        for (String m : modes) names.add(ModeService.label(m));
        Sfx.play(plugin, e.getPlayer(), Sfx.Kind.ERROR);
        Msg.send(e.getPlayer(), "<red>/" + label + " gibt es nur in " + String.join(" und ", names) + ".");
    }
}
