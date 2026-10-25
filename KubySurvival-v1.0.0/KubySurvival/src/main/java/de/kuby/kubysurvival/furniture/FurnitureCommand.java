package de.kuby.kubysurvival.furniture;

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

/** /sit, /lay und /moebel [give &lt;Spieler&gt; &lt;id&gt;] (give: Konsole/Admins, z. B. aus dem Juwelenshop). */
public final class FurnitureCommand implements CommandExecutor, TabCompleter {

    private final FurnitureService service;

    public FurnitureCommand(FurnitureService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("moebel") && args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            if (sender instanceof Player p && !p.hasPermission("kuby.admin")) {
                Msg.send(p, "<red>Möbel gibt es im <white>/juwelenshop</white>.");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            FurnitureCatalog.Def d = FurnitureCatalog.get(args[2].toLowerCase(Locale.ROOT));
            if (target == null || d == null) {
                sender.sendMessage("Spieler offline oder unbekanntes Möbel: " + args[1] + " " + args[2]);
                return true;
            }
            service.give(target, d);
            FurnitureService.send(target, "Neues Möbel: <white>" + d.name() + "</white>. Rechtsklick auf einen Block in deinem "
                    + "Grundstück stellt es auf.");
            return true;
        }
        if (!(sender instanceof Player p)) return true;
        switch (name) {
            case "sit" -> service.sit(p);
            case "lay" -> service.lay(p);
            default -> {
                FurnitureService.send(p, "Du hast <white>" + service.countOf(p.getUniqueId()) + "</white> Möbel aufgestellt "
                        + "(höchstens 50). Kaufen: <click:run_command:'/juwelenshop'><#F0A050>[Juwelenshop]</click>");
                Msg.send(p, "<gray>Rechtsklick auf einen Block: aufstellen · Schleichen + Rechtsklick: drehen · "
                        + "Schleichen + Linksklick: aufheben · Rechtsklick auf Stuhl, Bank, Hocker, Sofa: hinsetzen");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!cmd.getName().equalsIgnoreCase("moebel") || !sender.hasPermission("kuby.admin")) return out;
        if (args.length == 1) out.add("give");
        else if (args.length == 2) for (Player o : Bukkit.getOnlinePlayers()) out.add(o.getName());
        else if (args.length == 3) for (FurnitureCatalog.Def d : FurnitureCatalog.all()) out.add(d.id());
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
