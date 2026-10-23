package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.gui.SellMenu;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.AbholboxMenu;
import de.kuby.kubyplugins.gui.OrdersMenu;
import de.kuby.kubyplugins.gui.SellMultiMenu;
import de.kuby.kubyplugins.gui.WorthMenu;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** /sell, /worth, /sellmulti, /orders, /abholbox, /abholen */
public final class EconomyCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public EconomyCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sell" -> {
                if (args.length == 0) {
                    SellMenu.open(plugin, p);
                    return true;
                }
                long payout = plugin.sell().sellInventory(p); if (payout > 0) Sfx.play(plugin, p, Sfx.Kind.COIN);
                Msg.send(p, payout > 0
                        ? "<green>Verkauft für <gold>" + Msg.coins(payout) + "$"
                        : "<gray>Keine verkaufbaren Items im Inventar. Preise mit <white>/worth");
            }
            case "worth" -> new WorthMenu(plugin).open(p);
            case "sellmulti" -> new SellMultiMenu(plugin).open(p);
            case "orders" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("add")) addOrder(p, args);
                else new OrdersMenu(plugin).open(p);
            }
            case "abholbox" -> new AbholboxMenu(plugin).open(p);
            case "abholen" -> AbholboxMenu.claim(plugin, p);
            default -> { return false; }
        }
        return true;
    }

    private void addOrder(Player p, String[] args) {
        if (args.length < 4) {
            Msg.send(p, "<red>Nutzung: /orders add <Material> <Menge> <Preis pro Stück>");
            return;
        }
        Material m = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
        if (m == null || !m.isItem()) {
            Msg.send(p, "<red>Unbekanntes Item: " + args[1]);
            return;
        }
        int amount;
        long price;
        try {
            amount = Integer.parseInt(args[2]);
            price = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Menge und Preis müssen Zahlen sein.");
            return;
        }
        if (amount <= 0 || amount > 2304 || price <= 0) {
            Msg.send(p, "<red>Menge (1-2304) und Preis (> 0) prüfen.");
            return;
        }
        long total = amount * price;
        if (!plugin.economy().withdraw(p.getUniqueId(), total)) {
            Msg.send(p, "<red>Du brauchst <gold>" + Msg.coins(total) + "$ <red>für diesen Order.");
            return;
        }
        plugin.orders().add(p.getUniqueId(), p.getName(), m, amount, price);
        Msg.send(p, "<green>Order online: <white>" + amount + "x " + m.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + " <gray>für je <gold>" + Msg.coins(price) + "$ <gray>(<gold>" + Msg.coins(total) + "<gray> hinterlegt)");
    }
}
