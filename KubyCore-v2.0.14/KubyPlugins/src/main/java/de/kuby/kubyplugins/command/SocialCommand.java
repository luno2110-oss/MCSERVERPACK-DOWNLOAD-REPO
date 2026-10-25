package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.FriendsScreen;
import de.kuby.kubyplugins.gui.RankingMenu;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
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
    private final FriendsScreen friendsScreen;

    public SocialCommand(KubyPlugins plugin) {
        this.plugin = plugin;
        this.friendsScreen = new FriendsScreen(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "friend" -> friend(p, args);
            case "friends" -> friendsScreen.open(p);
            case "msg" -> msg(p, args);
            case "r" -> reply(p, args);
            case "inbox" -> plugin.messages().showInbox(p);
            case "stats" -> {
                OfflinePlayer target = p;
                if (args.length > 0) {
                    UUID t = FriendsScreen.resolve(args[0]);
                    if (t == null) {
                        Msg.send(p, "<red>Spieler nicht gefunden.");
                        return true;
                    }
                    target = Bukkit.getOfflinePlayer(t);
                }
                new de.kuby.kubyplugins.gui.StatsScreen(plugin).open(p, target);
            }
            case "ranking" -> new RankingMenu(plugin).open(p);
            default -> { return false; }
        }
        return true;
    }

    private void friend(Player p, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.isEmpty()) {
            friendsScreen.open(p);
            return;
        }
        if (sub.equals("list") || sub.equals("liste")) {
            friendsScreen.chatList(p);
            return;
        }
        if (args.length < 2) {
            Msg.send(p, "<red>Nutzung: /friend add|accept|deny|jump|remove <Spieler>, /friend list");
            return;
        }
        if (sub.equals("add")) {
            friendsScreen.request(p, args[1]);
            return;
        }
        UUID target = FriendsScreen.resolve(args[1]);
        if (target == null) {
            Msg.send(p, "<red>Spieler nicht gefunden.");
            return;
        }
        switch (sub) {
            case "accept", "annehmen" -> {
                String err = plugin.friends().accept(p.getUniqueId(), target);
                Msg.send(p, err == null ? "<green>Du und <white>" + args[1] + " <green>seid jetzt Freunde!" : "<red>" + err);
                Sfx.play(plugin, p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
            }
            case "deny", "ablehnen" -> Msg.send(p, plugin.friends().deny(p.getUniqueId(), target)
                    ? "<gray>Anfrage abgelehnt." : "<red>Keine Anfrage von oder an diesen Spieler.");
            case "jump", "tp" -> {
                String err = plugin.friends().jump(p, Bukkit.getPlayer(target));
                if (err != null) {
                    Msg.send(p, "<red>" + err);
                    Sfx.play(plugin, p, Sfx.Kind.ERROR);
                }
            }
            case "remove", "entfernen" -> {
                if (!plugin.friends().friendsOf(p.getUniqueId()).contains(target)) {
                    Msg.send(p, "<red>Ihr seid keine Freunde.");
                    return;
                }
                plugin.friends().remove(p.getUniqueId(), target);
                Msg.send(p, "<gray>Freundschaft mit <white>" + args[1] + " <gray>beendet.");
            }
            default -> Msg.send(p, "<red>Nutzung: /friend add|accept|deny|jump|remove <Spieler>, /friend list");
        }
    }

    /** Private Nachrichten aus (Einstellung "msg"): nur Freunde und Admins kommen durch. */
    private boolean blocked(Player p, java.util.UUID target) {
        if (target == null || p.hasPermission("kuby.admin") || plugin.friends().areFriends(p.getUniqueId(), target)) return false;
        if (plugin.playerSettings().get(target, "msg")) return false;
        Msg.send(p, "<red>Dieser Spieler nimmt gerade keine privaten Nachrichten an.");
        return true;
    }

    private void msg(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, "<red>Nutzung: /msg <Spieler> <Text>");
            return;
        }
        UUID target = resolve(args[0]);
        if (target == null) { Msg.send(p, "<red>Spieler nicht gefunden."); return; }
        if (target.equals(p.getUniqueId())) { Msg.send(p, "<red>Du kannst dir nicht selbst schreiben."); return; }
        if (blocked(p, target)) return;
        plugin.messages().send(p, target, args[0], String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
    }

    private void reply(Player p, String[] args) {
        UUID target = plugin.messages().lastContact(p.getUniqueId());
        if (target == null) { Msg.send(p, "<red>Niemand zum Antworten da."); return; }
        if (args.length == 0) { Msg.send(p, "<red>Nutzung: /r <Text>"); return; }
        OfflinePlayer op = Bukkit.getOfflinePlayer(target);
        if (blocked(p, target)) return;
        plugin.messages().send(p, target, op.getName() == null ? "Unbekannt" : op.getName(), String.join(" ", args));
    }


    /** Spieler-UUID aus Name: online oder aus dem Cache. */
    private static UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op == null ? null : op.getUniqueId();
    }
}
