package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.CosmeticsScreen;
import de.kuby.kubyplugins.service.CosmeticService;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /cosmetics öffnet das Menü; /cosmetics give &lt;Spieler&gt; &lt;id&gt; schaltet frei (Konsole/Admins, z. B. Kisten). */
public final class CosmeticsCommand implements CommandExecutor, TabCompleter {

    private final KubyPlugins plugin;
    private final CosmeticsScreen screen;

    public CosmeticsCommand(KubyPlugins plugin) {
        this.plugin = plugin;
        this.screen = new CosmeticsScreen(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            if (sender instanceof Player p && !p.hasPermission("kuby.admin")) return true;
            Player target = Bukkit.getPlayerExact(args[1]);
            CosmeticService.Item it = plugin.cosmetics().find(args[2].toLowerCase(Locale.ROOT));
            if (target == null || it == null) {
                sender.sendMessage("Spieler offline oder Kosmetik unbekannt: " + args[1] + " " + args[2]);
                return true;
            }
            plugin.cosmetics().grant(target.getUniqueId(), it);
            Msg.send(target, "<#FF7AC0>Neue Kosmetik: <white>" + it.label() + "</white> <gray>(<click:run_command:'/cosmetics'>/cosmetics</click>)");
            sender.sendMessage("OK: " + it.label() + " für " + target.getName());
            return true;
        }
        if (sender instanceof Player p) screen.open(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("kuby.admin")) return out;
        if (args.length == 1) out.add("give");
        else if (args.length == 2) for (Player o : Bukkit.getOnlinePlayers()) out.add(o.getName());
        else if (args.length == 3) for (CosmeticService.Item i : plugin.cosmetics().catalogue()) out.add(i.id());
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
