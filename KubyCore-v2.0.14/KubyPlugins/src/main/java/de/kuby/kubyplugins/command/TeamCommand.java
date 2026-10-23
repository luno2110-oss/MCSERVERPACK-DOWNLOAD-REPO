package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.TeamMenu;
import de.kuby.kubyplugins.service.TeamService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Teleports;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/** Bearbeitet /team (inkl. Unterbefehle) und /tc (Team-Chat). */
public final class TeamCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public TeamCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("tc")) {
            chat(p, String.join(" ", args));
            return true;
        }

        if (args.length == 0) {
            new TeamMenu(plugin).open(p);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 2) { Msg.send(p, "<red>Nutzung: /team create <Name>"); return true; }
                reply(p, plugin.teams().create(p, args[1]), "<green>Team <white>" + args[1] + " <green>gegründet!");
            }
            case "invite" -> {
                if (args.length < 2) { Msg.send(p, "<red>Nutzung: /team invite <Spieler>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { Msg.send(p, "<red>Spieler nicht online."); return true; }
                String err = plugin.teams().invite(p, target);
                if (err == null) {
                    Msg.send(target, "<gray>Einladung von <white>" + p.getName() + " <gray>erhalten. Annehmen mit <white>/team accept"); Sfx.play(plugin, target, Sfx.Kind.PING);
                }
                reply(p, err, "<green>Einladung an <white>" + target.getName() + " <green>gesendet.");
            }
            case "accept" -> reply(p, plugin.teams().accept(p.getUniqueId()), "<green>Du bist dem Team beigetreten!");
            case "leave" -> reply(p, plugin.teams().leave(p.getUniqueId()), "<gray>Du hast das Team verlassen.");
            case "disband" -> reply(p, plugin.teams().disband(p), "<gray>Team aufgelöst.");
            case "home" -> {
                Location loc = plugin.teams().homeOf(p.getUniqueId());
                if (loc == null) { Msg.send(p, "<red>Kein Teamhome gesetzt. Nutze /team sethome."); return true; }
                Teleports.delayed(plugin, p, loc, "Teamhome");
            }
            case "sethome" -> reply(p, plugin.teams().setHome(p), "<green>Teamhome gesetzt.");
            case "bank" -> {
                if (args.length < 2) { Msg.send(p, "<red>Nutzung: /team bank <Betrag>"); return true; }
                long amount;
                try {
                    amount = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    Msg.send(p, "<red>Ungültiger Betrag.");
                    return true;
                }
                reply(p, plugin.teams().deposit(p, amount), "<green>Eingezahlt: <gold>" + Msg.coins(amount) + "$");
            }
            case "ally" -> {
                if (args.length < 2) { Msg.send(p, "<red>Nutzung: /team ally <Team>"); return true; }
                reply(p, plugin.teams().ally(p, args[1]), "<green>Allianz mit <white>" + args[1] + " <green>geschlossen. Kein PvP mehr untereinander.");
            }
            case "help" -> sendHelp(p);
            default -> new TeamMenu(plugin).open(p);
        }
        return true;
    }

    private void reply(Player p, String error, String ok) {
        Msg.send(p, error == null ? ok : "<red>" + error); Sfx.play(plugin, p, error == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
    }

    private void chat(Player p, String message) {
        if (message.isBlank()) {
            Msg.send(p, "<red>Nutzung: /tc <Nachricht>");
            return;
        }
        TeamService.Team team = plugin.teams().of(p.getUniqueId());
        if (team == null) {
            Msg.send(p, "<red>Du bist in keinem Team.");
            return;
        }
        String line = "<gold>[Team] <white>" + p.getName() + "<dark_gray>: <gray>" + Msg.escape(message);
        for (UUID id : team.members()) {
            Player member = Bukkit.getPlayer(id);
            if (member != null) Msg.send(member, line);
        }
    }

    private void sendHelp(Player p) {
        Msg.send(p, "<gold>Team-Befehle:");
        Msg.send(p, "<gray>/team create <Name> <dark_gray>- <gray>Team gründen");
        Msg.send(p, "<gray>/team invite <Spieler> <dark_gray>- <gray>Spieler einladen");
        Msg.send(p, "<gray>/team accept <dark_gray>- <gray>Einladung annehmen");
        Msg.send(p, "<gray>/team leave <dark_gray>- <gray>Team verlassen");
        Msg.send(p, "<gray>/team home <dark_gray>- <gray>zum Teamhome");
        Msg.send(p, "<gray>/team sethome <dark_gray>- <gray>Teamhome setzen");
        Msg.send(p, "<gray>/team bank <Betrag> <dark_gray>- <gray>Teamkasse füllen");
        Msg.send(p, "<gray>/tc <Nachricht> <dark_gray>- <gray>Team-Chat");
    }
}
