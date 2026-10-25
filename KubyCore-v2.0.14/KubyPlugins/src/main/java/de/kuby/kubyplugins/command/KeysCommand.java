package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.KeyService;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /keys: deine Schlüssel im aktuellen Modus. /keys give|take &lt;Spieler&gt; &lt;Art&gt; [Anzahl] [Modus] für Konsole und
 * Admins (Juwelenshop, Votes, Events). Ohne Modus zählt der Modus, in dem der Spieler gerade ist (Hub: Survival).
 */
public final class KeysCommand implements CommandExecutor, TabCompleter {

    private final KubyPlugins plugin;

    public KeysCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if ((sub.equals("give") || sub.equals("take")) && args.length >= 3) {
            if (sender instanceof Player p && !p.hasPermission("kuby.admin")) {
                Msg.send(p, "<red>Schlüssel gibt es beim Voten, für Spielzeit, bei Events und im <white>/juwelenshop</white>.");
                return true;
            }
            OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
            if (target == null) target = Bukkit.getOfflinePlayerIfCached(args[1]);
            KeyService.KeyType t = plugin.keys().type(args[2]);
            if (target == null || t == null) {
                sender.sendMessage("Spieler oder Schlüsselart unbekannt: " + args[1] + " " + args[2] + " (Arten: "
                        + String.join(", ", plugin.keys().types().keySet()) + ")");
                return true;
            }
            int n;
            try {
                n = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
            } catch (NumberFormatException e) {
                sender.sendMessage("Anzahl muss eine Zahl sein.");
                return true;
            }
            String mode = args.length >= 5 ? args[4].toLowerCase(Locale.ROOT) : plugin.modes().modeOf(target.getUniqueId());
            if (ModeService.HUB.equals(mode)) mode = ModeService.SURVIVAL;
            boolean ok = sub.equals("give") ? plugin.keys().give(target.getUniqueId(), mode, t.id(), n)
                    : plugin.keys().take(target.getUniqueId(), mode, t.id(), n);
            sender.sendMessage((ok ? "OK: " : "Nicht möglich: ") + sub + " " + n + "x " + t.full() + " für " + target.getName()
                    + " (" + ModeService.label(mode) + ")");
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("/keys give|take <Spieler> <Art> [Anzahl] [Modus]");
            return true;
        }
        if (sub.equals("kiste") && p.hasPermission("kuby.admin")) {
            crateAdmin(p, args);
            return true;
        }
        if (!sub.equals("liste")) {
            plugin.crateScreens().keys(p);
            return true;
        }
        String mode = plugin.modes().modeOf(p.getUniqueId());
        Map<String, Integer> mine = plugin.keys().all(p.getUniqueId(), mode);
        Msg.send(p, "<#F0A050><b>SCHLÜSSEL</b> <dark_gray>▶ <gray>Deine Schlüssel in " + ModeService.label(mode) + ":");
        boolean any = false;
        for (KeyService.KeyType t : plugin.keys().types().values()) {
            int n = mine.getOrDefault(t.id(), 0);
            if (n <= 0) continue;
            any = true;
            Msg.send(p, "<dark_gray>• <white>" + n + "× " + t.colored());
        }
        if (!any) Msg.send(p, "<gray>Du hast gerade keine Schlüssel. Es gibt sie beim Voten, für Spielzeit, bei Events und im Juwelenshop.");
        Msg.send(p, "<gray>Die Kisten stehen am Spawn: Rechtsklick öffnet, Linksklick zeigt den Inhalt.");
        return true;
    }

    /** /keys kiste set <id> [modus] | remove | list: der Block, auf den du schaust. */
    private void crateAdmin(Player p, String[] args) {
        String op = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        org.bukkit.block.Block b = p.getTargetBlockExact(6);
        switch (op) {
            case "set" -> {
                if (b == null || args.length < 3) {
                    Msg.send(p, "<red>Schau auf einen Block: /keys kiste set <id> [modus]");
                    return;
                }
                String mode = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : plugin.modes().modeOf(p.getUniqueId());
                String err = plugin.crates().set(b, mode, args[2].toLowerCase(Locale.ROOT));
                Msg.send(p, err == null ? "<green>Kiste " + args[2] + " (" + ModeService.label(mode) + ") gesetzt." : "<red>" + err);
            }
            case "remove" -> Msg.send(p, b != null && plugin.crates().remove(b) ? "<green>Kiste entfernt." : "<red>Hier ist keine Kiste.");
            default -> {
                Msg.send(p, "<gray>Kisten: <white>/keys kiste set <id> [modus]</white>, <white>remove</white>. Aufgestellt:");
                plugin.crates().places().forEach((k, v) -> Msg.send(p, "<dark_gray>• <white>" + v + " <gray>" + k));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("kuby.admin")) return out;
        if (args.length == 1) out.addAll(List.of("give", "take", "kiste", "liste"));
        else if (args[0].equalsIgnoreCase("kiste")) {
            if (args.length == 2) out.addAll(List.of("set", "remove", "list"));
            else if (args.length == 3 && sender instanceof Player p)
                for (var c : plugin.crates().crates(plugin.modes().modeOf(p.getUniqueId()))) out.add(c.id());
        }
        else if (args.length == 2 && !args[0].equalsIgnoreCase("kiste")) for (Player o : Bukkit.getOnlinePlayers()) out.add(o.getName());
        else if (args.length == 3 && !args[0].equalsIgnoreCase("kiste")) out.addAll(plugin.keys().types().keySet());
        else if (args.length == 5) out.addAll(List.of(ModeService.SURVIVAL, ModeService.SMP, ModeService.SKYBLOCK));
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
