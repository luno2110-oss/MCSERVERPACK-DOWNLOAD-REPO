package de.kuby.kubyskyblock.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.gui.IslandScreen;
import de.kuby.kubyskyblock.service.IslandService;
import de.kuby.kubyskyblock.service.IslandService.Island;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /insel (auch /is, /island): ohne Argument das Insel-Menü. Unterbefehle: home, sethome, einladen, annehmen, entfernen,
 * verlassen, besuchen, top, level, name, besucher, upgrade, neustart, aufgeben. Nur im Skyblock-Modus.
 */
public final class IslandCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("home", "sethome", "einladen", "annehmen", "entfernen", "verlassen", "besuchen",
            "top", "level", "name", "besucher", "upgrade", "neustart", "aufgeben");

    private final KubySkyblock addon;
    private final KubyPlugins core;

    public IslandCommand(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
    }

    private void result(Player p, String err, String ok) {
        Sfx.play(core, p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        Msg.send(p, err == null ? "<green>" + ok : "<red>" + err);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        if (!ModeService.SKYBLOCK.equals(core.modes().modeOf(p.getUniqueId()))) {
            Msg.send(p, "<red>Inseln gibt es nur im Skyblock-Modus.");
            return true;
        }
        IslandService is = addon.islands();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        Island i = is.islandOf(p.getUniqueId());
        switch (sub) {
            case "" -> IslandScreen.open(p);
            case "home", "h", "zuhause" -> {
                if (i == null) IslandScreen.choose(p);
                else is.home(p, i);
            }
            case "sethome" -> result(p, is.setHome(p), "Zuhause gesetzt.");
            case "einladen", "invite" -> {
                if (args.length < 2) {
                    Msg.send(p, "<red>/insel einladen <Spieler>");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) result(p, "Dieser Spieler ist nicht online.", "");
                else result(p, is.invite(p, t), "Einladung an " + t.getName() + " geschickt.");
            }
            case "annehmen", "accept" -> result(p, is.accept(p), "Willkommen auf der Insel!");
            case "entfernen", "kick" -> {
                if (args.length < 2) {
                    Msg.send(p, "<red>/insel entfernen <Spieler>");
                    return true;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (t == null) result(p, "Diesen Spieler kenne ich nicht.", "");
                else result(p, is.kick(p, t.getUniqueId()), t.getName() + " wurde entfernt.");
            }
            case "verlassen", "leave" -> result(p, is.leave(p), "Insel verlassen.");
            case "besuchen", "visit" -> {
                if (args.length < 2) {
                    Msg.send(p, "<red>/insel besuchen <Spieler>");
                    return true;
                }
                String err = is.visit(p, args[1]);
                if (err != null) result(p, err, "");
            }
            case "top" -> IslandScreen.top(p);
            case "level" -> {
                if (i == null) {
                    result(p, "Du hast keine Insel.", "");
                    return true;
                }
                String err = is.recalc(i, pts -> Msg.send(p, "<#9AD8FF>Insel-Level: <white>" + is.levelOf(i) + " <gray>(" + Msg.coins(pts)
                        + " Punkte, Platz #" + is.rank(i) + ")"));
                if (err != null) result(p, err, "");
                else Msg.send(p, "<gray>Die Insel wird gezählt ...");
            }
            case "name" -> {
                if (args.length < 2) {
                    Msg.send(p, "<red>/insel name <Name>");
                    return true;
                }
                result(p, is.rename(p, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))), "Insel umbenannt.");
            }
            case "besucher" -> {
                String err = is.toggleOpen(p);
                result(p, err, i != null && i.open ? "Besucher sind jetzt erlaubt." : "Besucher sind jetzt gesperrt.");
            }
            case "upgrade" -> {
                IslandService.Upgrade u = args.length < 2 ? null : switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "groesse", "größe", "size" -> IslandService.Upgrade.SIZE;
                    case "mitglieder", "members" -> IslandService.Upgrade.SLOTS;
                    case "generator" -> IslandService.Upgrade.GENERATOR;
                    default -> null;
                };
                if (u == null) Msg.send(p, "<red>/insel upgrade <groesse|mitglieder|generator>");
                else result(p, is.upgrade(p, u), "Upgrade gekauft.");
            }
            case "neustart", "reset" -> {
                if (i == null) result(p, "Du hast keine Insel.", "");
                else if (core.uiPack().active(p)) IslandScreen.open(p);
                else new de.kuby.kubyskyblock.gui.TemplateMenu(core, true).open(p);
            }
            case "aufgeben" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("bestätigen") && !args[1].equalsIgnoreCase("bestaetigen")) {
                    Msg.send(p, "<red>Das löscht deine Insel (oder gibt sie an ein Mitglied). Sicher? <white>/insel aufgeben bestätigen");
                    return true;
                }
                result(p, is.abandon(p), "Insel aufgegeben.");
            }
            default -> {
                Msg.send(p, "<#9AD8FF><b>/insel</b> <gray>Menü · <white>home · sethome · einladen <Spieler> · annehmen · entfernen <Spieler> · "
                        + "verlassen · besuchen <Spieler> · top · level · name <Name> · besucher · upgrade <Art> · neustart · aufgeben");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("upgrade")) {
                for (String s : List.of("groesse", "mitglieder", "generator")) if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(s);
            } else if (List.of("einladen", "besuchen", "entfernen").contains(sub)) {
                for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }
}
