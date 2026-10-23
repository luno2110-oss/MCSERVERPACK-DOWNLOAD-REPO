package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.MarketMenu;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class MarketCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public MarketCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("sell")) {
            sell(p, args);
        } else {
            new MarketMenu(plugin).open(p);
        }
        return true;
    }

    private void sell(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, "<red>Nutzung: /markt sell <Preis>");
            return;
        }
        long price;
        try {
            price = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Ungültiger Preis.");
            return;
        }
        if (price <= 0) {
            Msg.send(p, "<red>Der Preis muss größer als 0 sein.");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            Msg.send(p, "<red>Halte das Item in deiner Haupthand.");
            return;
        }
        int max = plugin.getConfig().getInt("market.max-listings", 10);
        if (plugin.hasPerk("farmpass", p.getUniqueId())) max = Math.max(max, plugin.getConfig().getInt("farmpass.market-listings", 15));
        long own = plugin.market().inMode(plugin.modes().modeOf(p.getUniqueId())).stream()
                .filter(l -> l.seller().equals(p.getUniqueId()))
                .count();
        if (own >= max) {
            Msg.send(p, "<red>Du hast schon " + max + " Angebote. Zieh eins zurück, bevor du ein neues einstellst.");
            return;
        }
        plugin.market().add(p.getUniqueId(), p.getName(), hand, price);
        p.getInventory().setItemInMainHand(null);
        Msg.send(p, "<green>Angeboten für <gold>" + Msg.coins(price) + "$ <green>im Markt.");
    }
}
