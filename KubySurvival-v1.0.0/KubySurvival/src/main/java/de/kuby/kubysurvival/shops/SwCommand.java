package de.kuby.kubysurvival.shops;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /sw [create <Name>|manage|delete <Name>|<Name>]. */
public final class SwCommand implements CommandExecutor, TabCompleter {

    private final KubySurvival addon;

    public SwCommand(KubySurvival addon) {
        this.addon = addon;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        WarpService ws = addon.warps();
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "" -> new WarpMenu(addon, false, 0).open(p);
            case "manage", "verwalten" -> new WarpMenu(addon, true, 0).open(p);
            case "create", "erstellen", "kaufen" -> {
                if (args.length < 2) {
                    long price = ws.nextPrice(p.getUniqueId());
                    Msg.send(p, "<red>Nutzung: /sw create <Name> <gray>(" + (price < 0 ? "du hast schon alle Warps"
                            : "nächster Warp: " + Msg.coins(price) + " Coins") + ")");
                    return true;
                }
                long price = ws.nextPrice(p.getUniqueId());
                String err = ws.create(p, args[1]);
                if (err != null) {
                    Msg.send(p, "<red>" + err);
                } else {
                    Msg.send(p, "<green>Warp <light_purple>" + args[1] + "<green> gekauft für <gold>" + Msg.coins(price)
                            + " Coins<green>. Andere finden ihn unter <white>/sw<green>.");
                }
            }
            case "delete", "löschen", "loeschen" -> {
                WarpService.Warp w = args.length > 1 ? ws.get(args[1]) : null;
                if (w == null || (!w.owner.equals(p.getUniqueId()) && !p.hasPermission("kuby.admin"))) {
                    Msg.send(p, "<red>Diesen Warp hast du nicht.");
                    return true;
                }
                ws.delete(w);
                Msg.send(p, "<gray>Warp " + w.name + " gelöscht.");
            }
            case "help", "hilfe" -> {
                Msg.send(p, "<light_purple><b>Spieler-Warps</b>");
                Msg.send(p, "<white>/sw <gray>Alle Warps · <white>/sw <Name> <gray>Hinreisen");
                Msg.send(p, "<white>/sw create <Name> <gray>Warp hier kaufen (50.000 / 150.000 / 500.000 Coins)");
                Msg.send(p, "<white>/sw manage <gray>Deine Warps: verschieben, Symbol, löschen");
            }
            default -> {
                WarpService.Warp w = ws.get(args[0]);
                if (w == null) Msg.send(p, "<red>Diesen Warp gibt es nicht. <gray>Alle: <white>/sw");
                else ws.travel(p, w);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("create", "manage", "help")) if (s.startsWith(args[0].toLowerCase())) out.add(s);
            for (WarpService.Warp w : addon.warps().all()) if (w.name.toLowerCase().startsWith(args[0].toLowerCase())) out.add(w.name);
        }
        return out;
    }
}
