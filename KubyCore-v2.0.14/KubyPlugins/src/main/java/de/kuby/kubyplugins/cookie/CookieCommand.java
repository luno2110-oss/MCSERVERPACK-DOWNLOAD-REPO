package de.kuby.kubyplugins.cookie;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /cookie (auch /keks, /cookieclicker): Menü. Unterbefehle: gebaeude, upgrades, erfolge, aufstieg, rangliste, statistik,
 * hilfe, station, kaufen <Gebäude> [Menge|max]. /cookiehilfe öffnet die Hilfe. Admins: admin setzen|entfernen|regen|gold.
 */
public final class CookieCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("gebaeude", "upgrades", "erfolge", "aufstieg", "rangliste", "statistik", "hilfe", "station", "kaufen", "laden");

    private final KubyPlugins plugin;

    public CookieCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (command.getName().equalsIgnoreCase("cookiehilfe")) {
            CookieScreen.help(p, 0);
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        CookieStation st = plugin.cookieStation();
        switch (sub) {
            case "" -> CookieScreen.main(p);
            case "gebaeude", "gebäude" -> CookieScreen.buildings(p, 0);
            case "upgrades" -> CookieScreen.upgrades(p, 0);
            case "erfolge" -> CookieScreen.achievements(p, 0, 0);
            case "aufstieg", "prestige" -> {
                if (plugin.uiPack().active(p)) CookieScreen.prestige(p);
                else {
                    String err = plugin.cookies().ascend(p);
                    if (err != null) Msg.send(p, "<red>" + err);
                }
            }
            case "rangliste", "top" -> CookieScreen.ranking(p, CookieService.Board.ALLTIME);
            case "statistik", "stats" -> CookieScreen.stats(p);
            case "hilfe", "help" -> CookieScreen.help(p, 0);
            case "laden", "shop" -> CookieScreen.shop(p, args.length > 1 && args[1].toLowerCase().startsWith("i") ? 1 : 0);
            case "station" -> {
                if (st == null || st.spot() == null) Msg.send(p, "<red>Es gibt noch keine Keks-Station.");
                else de.kuby.kubyplugins.util.Teleports.delayed(plugin, p, st.spot(), "Keks-Station");
            }
            case "kaufen" -> {
                if (args.length < 2) {
                    Msg.send(p, "<red>/cookie kaufen <Gebäude> [Menge|max]");
                    return true;
                }
                int b = -1;
                for (int i = 0; i < CookieService.N; i++) {
                    var bd = CookieContent.BUILDINGS.get(i);
                    if (bd.id().equalsIgnoreCase(args[1]) || bd.name().equalsIgnoreCase(args[1])) b = i;
                }
                int n = 1;
                if (args.length >= 3) {
                    if (args[2].equalsIgnoreCase("max")) n = -1;
                    else try {
                        n = Math.max(1, Integer.parseInt(args[2]));
                    } catch (NumberFormatException ignored) {
                    }
                }
                String err = b < 0 ? "Dieses Gebäude gibt es nicht." : plugin.cookies().buy(p, b, n);
                Sfx.play(plugin, p, err == null ? Sfx.Kind.COIN : Sfx.Kind.ERROR);
                Msg.send(p, err == null ? "<green>Gekauft. <gray>Kekse: <white>" + CookieService.fmt(plugin.cookies().data(p.getUniqueId()).cookies) : "<red>" + err);
            }
            case "admin" -> {
                if (!p.hasPermission("kuby.admin")) {
                    Msg.send(p, "<red>Nur für Admins.");
                    return true;
                }
                String a = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
                switch (a) {
                    case "setzen" -> {
                        st.set(p.getLocation());
                        Msg.send(p, "<green>Keks-Station hier gesetzt. Der Keks schwebt über dieser Stelle.");
                    }
                    case "entfernen" -> {
                        st.remove();
                        Msg.send(p, "<green>Keks-Station entfernt.");
                    }
                    case "regen" -> {
                        st.startRain();
                        Msg.send(p, "<green>Keksregen startet gleich.");
                    }
                    case "gold" -> {
                        st.goldenNow(p);
                        Msg.send(p, "<green>Glückskeks kommt gleich (an der Station).");
                    }
                    default -> Msg.send(p, "<gold>/cookie admin setzen|entfernen|regen|gold");
                }
            }
            default -> Msg.send(p, "<#F2C443><b>/cookie</b> <gray>Menü · <white>gebaeude · upgrades · erfolge · aufstieg · rangliste · statistik · hilfe · station · kaufen <Gebäude> [Menge|max]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("cookiehilfe")) return out;
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            if (sender.hasPermission("kuby.admin") && "admin".startsWith(args[0].toLowerCase(Locale.ROOT))) out.add("admin");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("kaufen")) {
            for (var b : CookieContent.BUILDINGS) if (b.id().startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(b.id());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            out.addAll(List.of("setzen", "entfernen", "regen", "gold"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("kaufen")) {
            out.addAll(List.of("1", "10", "100", "max"));
        }
        return out;
    }
}
