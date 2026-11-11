package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modus wechseln für Spieler: /modus öffnet die Modus-Wahl (wie der Kompass in der Lobby), /modus &lt;smp|survival|skyblock|hub&gt;
 * wechselt direkt. Kurzbefehle /smp, /survival, /lobby (und /hub, /skyblock außerhalb von Skyblock).
 */
public final class ModeSwitchCommand implements TabExecutor {

    private static final List<String> NAMES = List.of("smp", "survival", "skyblock", "hub");

    private final KubyPlugins plugin;

    public ModeSwitchCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    /** Eingabe zu Modus (auch Kurzformen wie sv, sb, lobby). Null = unbekannt. */
    public static String parse(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "smp", "pvp" -> ModeService.SMP;
            case "survival", "sv", "surv" -> ModeService.SURVIVAL;
            case "skyblock", "sb", "sky", "insel" -> ModeService.SKYBLOCK;
            case "hub", "lobby", "l", "spawn" -> ModeService.HUB;
            default -> null;
        };
    }

    /** Wechsel mit Rückmeldung (gemeinsam für alle Befehle). */
    public static void go(KubyPlugins plugin, Player p, String mode) {
        if (mode.equals(plugin.modes().modeOf(p.getUniqueId())) && !ModeService.HUB.equals(mode)) {
            Msg.send(p, "<gray>Du bist schon in <white>" + ModeService.label(mode) + "</white>. Zum Spawn: <white>/spawn");
            return;
        }
        String err = plugin.modes().switchTo(p, mode);
        if (err != null) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>" + err);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können den Modus wechseln.");
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        String direct = switch (name) {
            case "smp" -> ModeService.SMP;
            case "survival" -> ModeService.SURVIVAL;
            default -> null;
        };
        if (direct != null) {
            go(plugin, p, direct);
            return true;
        }
        // /modus
        if (args.length == 0) {
            de.kuby.kubyplugins.gui.ModeSelectScreen.open(plugin, p);
            return true;
        }
        String mode = parse(args[0]);
        if (mode == null) {
            Msg.send(p, "<red>Unbekannter Modus. <gray>/modus smp, survival, skyblock oder hub (ohne Angabe: Auswahl)");
            return true;
        }
        go(plugin, p, mode);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!command.getName().equalsIgnoreCase("modus") || args.length != 1) return out;
        for (String n : NAMES) if (n.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(n);
        return out;
    }
}
