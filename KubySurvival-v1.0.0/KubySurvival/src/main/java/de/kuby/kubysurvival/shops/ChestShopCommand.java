package de.kuby.kubysurvival.shops;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /chestshop: eigene Läden mit Lager und Kasse; /chestshop help: kurze Anleitung. */
public final class ChestShopCommand implements CommandExecutor {

    private final KubySurvival addon;

    public ChestShopCommand(KubySurvival addon) {
        this.addon = addon;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length > 0 && (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("hilfe"))) {
            Msg.send(p, "<dark_blue><b>Kistenladen</b>");
            Msg.send(p, "<white>1. <gray>Stell eine Kiste oder ein Fass auf ein Grundstück, auf dem du bauen darfst.");
            Msg.send(p, "<white>2. <gray>Häng ein Schild dran (oder stell es obendrauf) und schreib <white>[Shop]<gray> in die erste Zeile.");
            Msg.send(p, "<white>3. <gray>Nimm die Ware in die Hand und klick das Schild an: Menge und Preise einstellen.");
            Msg.send(p, "<white>4. <gray>Füll die Kiste mit Ware. Für den Ankauf zahlst du Coins in die Ladenkasse ein.");
            Msg.send(p, "<gray>Kunden klicken aufs Schild. Nur du kannst die Kiste öffnen oder abbauen.");
            return true;
        }
        List<ChestShopService.Shop> list = addon.chestShops().of(p.getUniqueId());
        if (list.isEmpty()) {
            Msg.send(p, "<gray>Du hast noch keinen Laden. Anleitung: <click:run_command:'/chestshop help'><white>/chestshop help</click>");
            return true;
        }
        Msg.send(p, "<dark_blue><b>Deine Läden</b> <gray>(" + list.size() + ")");
        long total = 0;
        for (ChestShopService.Shop s : list) {
            total += s.cash;
            String item = s.item == null ? "<red>keine Ware" : "<white>" + s.amount + "x " + Msg.escape(ChestShopService.name(s.item));
            Msg.send(p, "<gray>▪ " + item + " <dark_gray>· <gray>K " + (s.buyPrice > 0 ? Msg.coins(s.buyPrice) : "-")
                    + " V " + (s.sellPrice > 0 ? Msg.coins(s.sellPrice) : "-") + " <dark_gray>· <gray>Lager <white>"
                    + addon.chestShops().stock(s) + " <dark_gray>· <gray>Kasse <gold>" + Msg.coins(s.cash)
                    + " <dark_gray>(" + s.sign.getBlockX() + ", " + s.sign.getBlockY() + ", " + s.sign.getBlockZ() + ")");
        }
        Msg.send(p, "<gray>In allen Kassen: <gold>" + Msg.coins(total) + " Coins <gray>(abheben am Schild)");
        return true;
    }
}
