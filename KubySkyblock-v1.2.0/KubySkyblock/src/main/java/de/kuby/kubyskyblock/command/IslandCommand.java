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
            "top", "level", "name", "besucher", "upgrade", "neustart", "aufgeben", "rechte", "gast", "sperren", "entsperren", "rauswerfen", "co", "shop", "biom", "limits", "preise");

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
                    de.kuby.kubyskyblock.gui.IslandExtraScreen.visit(p);
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
                    case "wachstum", "growth" -> IslandService.Upgrade.GROWTH;
                    case "spawnrate", "spawn" -> IslandService.Upgrade.SPAWN;
                    case "limits", "limit" -> IslandService.Upgrade.LIMIT;
                    default -> null;
                };
                if (u == null) Msg.send(p, "<red>/insel upgrade <groesse|mitglieder|generator|wachstum|spawnrate|limits>");
                else result(p, is.upgrade(p, u), "Upgrade gekauft.");
            }
            case "neustart", "reset" -> {
                if (i == null) result(p, "Du hast keine Insel.", "");
                else if (core.uiPack().active(p)) IslandScreen.open(p);
                else new de.kuby.kubyskyblock.gui.TemplateMenu(core, true).open(p);
            }
            case "shop" -> {
                int t = 0;
                if (args.length >= 2) {
                    try {
                        t = Integer.parseInt(args[1]) - 1;
                    } catch (NumberFormatException ignored) {
                    }
                }
                de.kuby.kubyskyblock.gui.SkyShopScreen.open(p, t, 0);
            }
            case "limits", "limit" -> de.kuby.kubyskyblock.gui.IslandExtraScreen.limits(p);
            case "preise", "belohnungen" -> de.kuby.kubyskyblock.gui.IslandExtraScreen.prizes(p);
            case "biom", "biome", "biominsel" -> {
                String act = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                int id = -1;
                if (args.length > 2) {
                    try {
                        id = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                var bs = addon.biomes();
                switch (act) {
                    case "hin", "tp" -> result(p, bs.teleport(p, id), "");
                    case "beanspruchen", "claim" -> result(p, bs.claim(p, id), "Biom-Insel beansprucht.");
                    case "freigeben", "release" -> result(p, bs.release(p, id), "Biom-Insel freigegeben.");
                    default -> de.kuby.kubyskyblock.gui.IslandExtraScreen.visit(p, 1, 0);
                }
            }
            case "rechte", "rollen" -> {
                if (i == null || !is.canManage(i, p.getUniqueId())) result(p, "Das dürfen nur Besitzer und Co-Besitzer.", "");
                else IslandScreen.access(p);
            }
            case "gast", "sperren", "entsperren", "rauswerfen", "co" -> {
                if (args.length < 2) {
                    Msg.send(p, "<red>/insel " + sub + " <Spieler>");
                    return true;
                }
                Player online = Bukkit.getPlayerExact(args[1]);
                OfflinePlayer op = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[1]);
                if (op == null) {
                    result(p, "Diesen Spieler kennt der Server nicht.", "");
                    return true;
                }
                java.util.UUID t = op.getUniqueId();
                switch (sub) {
                    case "gast" -> {
                        boolean add = i == null || !i.trusted.contains(t);
                        result(p, is.trust(p, t, add), add ? op.getName() + " ist jetzt Gast deiner Insel." : op.getName() + " ist kein Gast mehr.");
                    }
                    case "sperren" -> result(p, is.ban(p, t, true), op.getName() + " ist gesperrt.");
                    case "entsperren" -> result(p, is.ban(p, t, false), op.getName() + " ist nicht mehr gesperrt.");
                    case "rauswerfen" -> result(p, is.expel(p, t), op.getName() + " wurde von der Insel geworfen.");
                    default -> {
                        boolean co = i == null || !i.coOwners.contains(t);
                        result(p, is.setCoOwner(p, t, co), op.getName() + (co ? " ist jetzt Co-Besitzer." : " ist jetzt wieder Mitglied."));
                    }
                }
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
                        + "verlassen · besuchen [Spieler] · top · preise · biom · limits · level · name <Name> · besucher · upgrade <Art> · neustart · aufgeben · rechte · "
                        + "gast <Spieler> · sperren <Spieler> · entsperren <Spieler> · rauswerfen <Spieler> · co <Spieler>");
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
                for (String s : List.of("groesse", "mitglieder", "generator", "wachstum", "spawnrate", "limits")) if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(s);
            } else if (sub.startsWith("biom")) {
                for (String s : List.of("hin", "beanspruchen", "freigeben")) if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(s);
            } else if (List.of("einladen", "besuchen", "entfernen", "gast", "sperren", "entsperren", "rauswerfen", "co").contains(sub)) {
                for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }
}
