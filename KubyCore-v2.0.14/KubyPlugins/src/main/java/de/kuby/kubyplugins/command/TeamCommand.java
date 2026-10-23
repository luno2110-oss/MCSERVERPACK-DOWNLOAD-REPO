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
                Location loc = args.length > 1 ? plugin.teams().homeOf(p.getUniqueId(), args[1]) : plugin.teams().homeOf(p.getUniqueId());
                if (loc == null) { Msg.send(p, "<red>Dieses Team-Home gibt es nicht. <gray>Alle: <white>/team homes"); return true; }
                Teleports.delayed(plugin, p, loc, "Teamhome");
            }
            case "homes" -> {
                TeamService.Team t = plugin.teams().of(p.getUniqueId());
                if (t == null) { Msg.send(p, "<red>Du hast kein Team."); return true; }
                Msg.send(p, "<gold>Team-Homes <gray>(" + t.homes().size() + "/" + plugin.teams().homeLimit(t) + "):");
                for (String h : t.homes().keySet()) {
                    Msg.send(p, "<click:run_command:'/team home " + h + "'><white>▪ " + h + "</click>");
                }
            }
            case "sethome" -> reply(p, plugin.teams().setHome(p, args.length > 1 ? args[1] : "home"), "<green>Teamhome gesetzt.");
            case "delhome" -> {
                if (args.length < 2) { Msg.send(p, "<red>Nutzung: /team delhome <Name>"); return true; }
                reply(p, plugin.teams().delHome(p, args[1]), "<gray>Team-Home gelöscht.");
            }
            case "lager", "storage" -> {
                String err = plugin.teams().openStorage(p);
                if (err != null) Msg.send(p, "<red>" + err);
            }
            case "kick" -> {
                if (args.length < 2) { Msg.send(p, "<red>Nutzung: /team kick <Spieler>"); return true; }
                var op = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (op == null) { Msg.send(p, "<red>Diesen Spieler kennen wir nicht."); return true; }
                reply(p, plugin.teams().kick(p, op.getUniqueId()), "<gray>" + args[1] + " ist nicht mehr im Team.");
            }
            case "info" -> {
                TeamService.Team t = plugin.teams().of(p.getUniqueId());
                if (t == null) { Msg.send(p, "<red>Du hast kein Team."); return true; }
                int lv = plugin.teams().level(t);
                long from = TeamService.xpFor(lv), to = TeamService.xpFor(lv + 1);
                Msg.send(p, "<gold><b>" + t.name() + "</b> <gray>· Level <green>" + lv + " <gray>· Mitglieder <white>"
                        + t.members().size() + "/" + plugin.teams().maxMembers(t) + " <gray>· Kasse <gold>" + Msg.coins(t.bank()) + "$");
                Msg.send(p, lv >= plugin.teams().maxLevel() ? "<green>Höchstes Team-Level erreicht."
                        : "<gray>Team-XP: <white>" + Msg.coins(t.xp() - from) + "/" + Msg.coins(to - from)
                        + " <gray>bis Level " + (lv + 1) + " (gemeinsam spielen bringt XP)");
            }
            case "bank" -> {
                if (args.length < 2) { Msg.send(p, "<red>Nutzung: /team bank <Betrag> · /team bank abheben <Betrag>"); return true; }
                boolean out = args[1].equalsIgnoreCase("abheben");
                long amount;
                try {
                    amount = Long.parseLong(args[out ? 2 : 1]);
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    Msg.send(p, "<red>Ungültiger Betrag.");
                    return true;
                }
                if (out) reply(p, plugin.teams().withdraw(p, amount), "<green>Abgehoben: <gold>" + Msg.coins(amount) + "$");
                else reply(p, plugin.teams().deposit(p, amount), "<green>Eingezahlt: <gold>" + Msg.coins(amount) + "$");
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
        Msg.send(p, "<gray>/team info <dark_gray>- <gray>Level, Plätze, Kasse");
        Msg.send(p, "<gray>/team home [Name] <dark_gray>- <gray>zum Team-Home · <gray>/team homes");
        Msg.send(p, "<gray>/team sethome [Name] <dark_gray>- <gray>Team-Home setzen · <gray>/team delhome <Name>");
        Msg.send(p, "<gray>/team lager <dark_gray>- <gray>gemeinsames Lager");
        Msg.send(p, "<gray>/team bank <Betrag> <dark_gray>- <gray>Teamkasse füllen · <gray>/team bank abheben <Betrag> (Chef)");
        Msg.send(p, "<gray>/team kick <Spieler> <dark_gray>- <gray>Mitglied entfernen (Chef)");
        Msg.send(p, "<gray>/team ally <Team> <dark_gray>- <gray>Bündnis (Chef)");
        Msg.send(p, "<gray>/tc <Nachricht> <dark_gray>- <gray>Team-Chat");
    }
}
