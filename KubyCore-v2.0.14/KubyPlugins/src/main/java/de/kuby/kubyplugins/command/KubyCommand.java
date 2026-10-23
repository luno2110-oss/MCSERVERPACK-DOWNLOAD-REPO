package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.DailyMenu;
import de.kuby.kubyplugins.gui.HomesMenu;
import de.kuby.kubyplugins.gui.LeaderboardMenu;
import de.kuby.kubyplugins.gui.MainMenu;
import de.kuby.kubyplugins.gui.ShopMenu;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Teleports;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class KubyCommand implements CommandExecutor, TabCompleter {

    private final KubyPlugins plugin;

    public KubyCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        UUID id = player.getUniqueId();

        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "befehle" -> new MainMenu(plugin).open(player);
            case "balance" -> Msg.send(player, "<gray>Kontostand: <gold>"
                    + Msg.coins(plugin.economy().balance(id)) + "$");
            case "pay" -> pay(player, args);
            case "baltop" -> new LeaderboardMenu(plugin).open(player);
            case "shop" -> new ShopMenu(plugin).open(player);
            case "sethome" -> setHome(player, args.length > 0 ? args[0] : "base");
            case "home" -> home(player, args);
            case "delhome" -> delHome(player, args);
            case "homes" -> new HomesMenu(plugin).open(player);
            case "daily" -> new DailyMenu(plugin).open(player);
            case "rtp" -> Teleports.randomTeleport(plugin, player);
            case "spawn" -> Teleports.spawn(plugin, player);
            default -> { return false; }
        }
        return true;
    }

    private void pay(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, "<red>Nutzung: /pay <Spieler> <Betrag>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(p)) {
            Msg.send(p, "<red>Spieler nicht online oder ungültig.");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Ungültiger Betrag.");
            return;
        }
        if (amount <= 0 || !plugin.economy().withdraw(p.getUniqueId(), amount)) {
            Msg.send(p, "<red>Nicht genug$ oder ungültiger Betrag.");
            return;
        }
        plugin.economy().deposit(target.getUniqueId(), amount);
        Msg.send(p, "<gray>Du hast <gold>" + Msg.coins(amount) + "$ <gray>an <white>" + target.getName() + " <gray>gesendet.");
        Msg.send(target, "<gray>Du hast <gold>" + Msg.coins(amount) + "$ <gray>von <white>" + p.getName() + " <gray>erhalten.");
    }

    private void setHome(Player p, String name) {
        String blocked = plugin.homes().blockedReason(p.getLocation());
        if (blocked != null) {
            Msg.send(p, "<red>" + blocked);
            return;
        }
        if (plugin.homes().set(p, name, p.getLocation())) {
            Msg.send(p, "<green>Home <white>" + name + " <green>gesetzt.");
        } else {
            Msg.send(p, "<red>Home-Limit erreicht. Mehr Homes über /verify oder einen höheren Rang.");
        }
    }

    private void home(Player p, String[] args) {
        if (args.length == 0) {
            new HomesMenu(plugin).open(p);
            return;
        }
        Location loc = plugin.homes().get(p.getUniqueId(), args[0]);
        if (loc == null) {
            Msg.send(p, "<red>Home nicht gefunden. Siehe /homes");
            return;
        }
        Teleports.delayed(plugin, p, loc, args[0]);
    }

    private void delHome(Player p, String[] args) {
        if (args.length == 0) {
            Msg.send(p, "<red>Nutzung: /delhome <Name>");
            return;
        }
        plugin.homes().delete(p.getUniqueId(), args[0]);
        Msg.send(p, "<gray>Home <white>" + args[0] + " <gray>gelöscht.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && command.getName().equalsIgnoreCase("pay")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
