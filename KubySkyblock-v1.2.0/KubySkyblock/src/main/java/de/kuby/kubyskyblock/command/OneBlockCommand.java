package de.kuby.kubyskyblock.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.gui.OneBlockScreen;
import de.kuby.kubyskyblock.service.OneBlockService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /oneblock (auch /ob): ohne Argument der Dialog (ohne Pack eine Liste im Chat). Unterbefehle: beanspruchen <1-3>,
 * freigeben, einzahlen <Menge|alles>, upgrade <glueck|cooldown|tempo|xp|geld|beute>, tp <1-3>, upgrades.
 */
public final class OneBlockCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("beanspruchen", "freigeben", "einzahlen", "upgrade", "upgrades", "tp");

    private final KubySkyblock addon;
    private final KubyPlugins core;

    public OneBlockCommand(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
    }

    private void result(Player p, String err, String ok) {
        Sfx.play(core, p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        if (err != null) Msg.send(p, "<red>" + err);
        else if (ok != null) Msg.send(p, "<green>" + ok);
    }

    private int index(Player p, String[] args) {
        if (args.length < 2) return -1;
        try {
            return Integer.parseInt(args[1]) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        OneBlockService s = addon.oneblock();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> OneBlockScreen.open(p);
            case "upgrades" -> OneBlockScreen.block(p);
            case "beanspruchen", "claim" -> {
                int i = index(p, args);
                if (i < 0) {
                    Msg.send(p, "<red>/oneblock beanspruchen <1-" + s.platforms().size() + ">");
                    return true;
                }
                result(p, s.claim(p, i), null);
            }
            case "freigeben", "release" -> result(p, s.release(p), null);
            case "einzahlen", "deposit" -> {
                long n;
                if (args.length < 2) n = -1;
                else if (args[1].equalsIgnoreCase("alles") || args[1].equalsIgnoreCase("all")) n = -1;
                else {
                    try {
                        n = Long.parseLong(args[1].replace(".", ""));
                    } catch (NumberFormatException e) {
                        Msg.send(p, "<red>/oneblock einzahlen <Menge|alles>");
                        return true;
                    }
                }
                result(p, s.deposit(p, n), null);
            }
            case "upgrade" -> {
                if (args.length < 2) {
                    OneBlockScreen.block(p);
                    return true;
                }
                OneBlockService.Upgrade u = null;
                for (OneBlockService.Upgrade c : OneBlockService.Upgrade.values()) {
                    if (c.key().equalsIgnoreCase(args[1]) || c.label.equalsIgnoreCase(args[1])) u = c;
                }
                if (u == null) {
                    Msg.send(p, "<red>Upgrades: glueck, cooldown, tempo, xp, geld, beute");
                    return true;
                }
                result(p, s.upgrade(p, u), null);
            }
            case "tp", "hin" -> {
                int i = index(p, args);
                if (i < 0 || i >= s.platforms().size()) {
                    Msg.send(p, "<red>/oneblock tp <1-" + s.platforms().size() + ">");
                    return true;
                }
                OneBlockScreen.teleport(p, s.platforms().get(i));
            }
            default -> Msg.send(p, "<#9AD8FF><b>/oneblock</b> <gray>Menü · <white>beanspruchen <Nr> · freigeben · einzahlen <Menge|alles> · "
                    + "upgrade <Art> · upgrades · tp <Nr>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "beanspruchen", "tp" -> {
                    for (int i = 1; i <= addon.oneblock().platforms().size(); i++) out.add(String.valueOf(i));
                }
                case "einzahlen" -> out.addAll(List.of("100", "1000", "alles"));
                case "upgrade" -> {
                    for (OneBlockService.Upgrade u : OneBlockService.Upgrade.values()) if (u.key().startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(u.key());
                }
                default -> { }
            }
        }
        return out;
    }
}
