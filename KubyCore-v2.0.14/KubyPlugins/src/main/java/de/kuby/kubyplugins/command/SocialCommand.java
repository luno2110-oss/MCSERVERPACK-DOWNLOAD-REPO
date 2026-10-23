package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.FriendsMenu;
import de.kuby.kubyplugins.gui.RankingMenu;
import de.kuby.kubyplugins.service.StatsService;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/** Freunde, Nachrichten, Kopfgeld, Statistik und Rangliste. */
public final class SocialCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public SocialCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "friend" -> friend(p, args);
            case "friends" -> new FriendsMenu(plugin).open(p);
            case "msg" -> msg(p, args);
            case "r" -> reply(p, args);
            case "inbox" -> plugin.messages().showInbox(p);
            case "stats" -> stats(p);
            case "ranking" -> new RankingMenu(plugin).open(p);
            default -> { return false; }
        }
        return true;
    }

    private void friend(Player p, String[] args) {
        if (args.length < 2) {
            if (args.length == 0) new FriendsMenu(plugin).open(p);
            else Msg.send(p, "<red>Nutzung: /friend add|accept|remove <Spieler>");
            return;
        }
        UUID target = resolve(args[1]);
        if (target == null) {
            Msg.send(p, "<red>Spieler nicht gefunden.");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                String err = plugin.friends().request(p.getUniqueId(), target);
                if (err != null) { Msg.send(p, "<red>" + err); return; }
                Msg.send(p, "<green>Anfrage an <white>" + args[1] + " <green>gesendet.");
                Player other = Bukkit.getPlayer(target);
                if (other != null) Msg.send(other, "<gray>Freundesanfrage von <white>" + p.getName()
                        + "<gray>. Annehmen mit <white>/friend accept " + p.getName());
            }
            case "accept" -> {
                String err = plugin.friends().accept(p.getUniqueId(), target);
                Msg.send(p, err == null ? "<green>Ihr seid jetzt befreundet!" : "<red>" + err);
            }
            case "remove" -> {
                plugin.friends().remove(p.getUniqueId(), target);
                Msg.send(p, "<gray>Freundschaft beendet.");
            }
            default -> Msg.send(p, "<red>Nutzung: /friend add|accept|remove <Spieler>");
        }
    }

    private void msg(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, "<red>Nutzung: /msg <Spieler> <Text>");
            return;
        }
        UUID target = resolve(args[0]);
        if (target == null) { Msg.send(p, "<red>Spieler nicht gefunden."); return; }
        if (target.equals(p.getUniqueId())) { Msg.send(p, "<red>Du kannst dir nicht selbst schreiben."); return; }
        plugin.messages().send(p, target, args[0], String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
    }

    private void reply(Player p, String[] args) {
        UUID target = plugin.messages().lastContact(p.getUniqueId());
        if (target == null) { Msg.send(p, "<red>Niemand zum Antworten da."); return; }
        if (args.length == 0) { Msg.send(p, "<red>Nutzung: /r <Text>"); return; }
        OfflinePlayer op = Bukkit.getOfflinePlayer(target);
        plugin.messages().send(p, target, op.getName() == null ? "Unbekannt" : op.getName(), String.join(" ", args));
    }


    private void stats(Player p) {
        StatsService.Stats s = plugin.stats().get(p.getUniqueId());
        Msg.send(p, "<gold>Deine Statistik:");
        Msg.send(p, "<gray>Kills: <white>" + s.kills + " <dark_gray>| <gray>Tode: <white>" + s.deaths);
        Msg.send(p, "<gray>Trophäen: <gold>" + s.trophies + " <dark_gray>| <gray>Liga: <white>" + StatsService.league(s.trophies));
        Msg.send(p, "<gray>XP: <white>" + s.xp);
    }


    /** Spieler-UUID aus Name: online oder aus dem Cache. */
    private static UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op == null ? null : op.getUniqueId();
    }
}
