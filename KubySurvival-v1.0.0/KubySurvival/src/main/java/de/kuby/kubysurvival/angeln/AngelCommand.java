package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /angeln [buch|rute|koeder|haendler|auftraege|top|hilfe|zone] für Spieler und /kp angel ... für Admins
 * (Zonen, Teleport-Punkt, Hologramm, Händler, Turnier).
 */
public final class AngelCommand implements CommandExecutor, TabCompleter {

    private final KubyPlugins core;
    private final AngelService service;
    private final AngelMenus menus;
    private final AngelHolo holo;
    private final AngelDealer dealer;
    private final Map<UUID, Location[]> corners = new HashMap<>();

    public AngelCommand(KubyPlugins core, AngelService service, AngelMenus menus, AngelHolo holo, AngelDealer dealer) {
        this.core = core;
        this.service = service;
        this.menus = menus;
        this.holo = holo;
        this.dealer = dealer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "buch", "fischbuch" -> menus.openBook(p, Water.FLUSS);
            case "rute", "upgrades" -> menus.openRod(p);
            case "koeder", "köder" -> menus.openBaits(p);
            case "haendler", "händler", "verkaufen" -> menus.openDealer(p, null);
            case "auftraege", "aufträge" -> menus.openOrders(p);
            case "top", "bestenliste" -> menus.openTop(p, "woche");
            case "hilfe" -> menus.openHelp(p);
            case "zone" -> menus.toZone().accept(p);
            default -> menus.open(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        return List.of("buch", "rute", "koeder", "haendler", "auftraege", "top", "hilfe", "zone").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
    }

    /** /kp angel ... (nur Admins). */
    public void admin(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase() : "";
        switch (sub) {
            case "pos1", "pos2" -> {
                Location[] c = corners.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
                c[sub.equals("pos1") ? 0 : 1] = p.getLocation().getBlock().getLocation();
                Msg.send(p, "<green>Ecke " + sub.charAt(3) + " der Angel-Zone gesetzt.");
            }
            case "zone" -> zone(p, args);
            case "tp" -> {
                AngelService.Zone z = zoneHere(p, args, 2);
                if (z == null) return;
                z.tp = p.getLocation().clone();
                service.changed();
                Msg.send(p, "<green>Teleport-Punkt von " + z.id + " gesetzt (\"Zur Angel-Zone\").");
            }
            case "holo", "hologramm" -> {
                AngelService.Zone z = zoneHere(p, args, 2);
                if (z == null) return;
                if (args.length > 3 && args[3].equalsIgnoreCase("entfernen")) {
                    holo.remove(z);
                    z.holo = null;
                    service.changed();
                    Msg.send(p, "<green>Hologramm entfernt.");
                    return;
                }
                holo.remove(z);
                z.holo = p.getLocation().clone();
                service.changed();
                holo.spawn(z);
                Msg.send(p, "<green>Hologramm von " + z.id + " an deiner Position aufgestellt.");
            }
            case "turnier" -> {
                String action = args.length > 2 ? args[2].toLowerCase() : "";
                String err;
                if (action.equals("start")) {
                    int minutes = 30;
                    try {
                        if (args.length > 3) minutes = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        Msg.send(p, "<red>Ungültige Minutenzahl.");
                        return;
                    }
                    err = service.startTournament(minutes);
                } else if (action.equals("stop")) {
                    err = service.stopTournament();
                } else {
                    err = "Nutzung: /kp angel turnier start <Minuten> | stop";
                }
                if (err != null) Msg.send(p, "<red>" + err);
            }
            case "haendler", "händler" -> {
                if (args.length > 2 && args[2].equalsIgnoreCase("entfernen")) {
                    Msg.send(p, dealer.removeNear(p) ? "<green>Fischhändler entfernt." : "<red>Kein Fischhändler im Umkreis von 5 Blöcken.");
                } else {
                    dealer.spawn(p);
                    Msg.send(p, "<green>Fischhändler gesetzt. Entfernen mit /kp angel haendler entfernen.");
                }
            }
            default -> {
                Msg.send(p, "<aqua><b>Angeln (Admin)</b>");
                Msg.send(p, "<white>/kp angel pos1 | pos2 <gray>Ecken der Zone an deiner Position");
                Msg.send(p, "<white>/kp angel zone erstellen <id> [Name] <gray>Zone aus den Ecken, Teleport-Punkt = deine Position");
                Msg.send(p, "<white>/kp angel zone löschen <id> <gray>· <white>/kp angel zone liste");
                Msg.send(p, "<white>/kp angel tp [id] <gray>Teleport-Punkt \"Zur Angel-Zone\" an deiner Position");
                Msg.send(p, "<white>/kp angel holo [id] [entfernen] <gray>Hologramm an deiner Position");
                Msg.send(p, "<white>/kp angel haendler [entfernen] <gray>Fischhändler an deiner Position");
                Msg.send(p, "<white>/kp angel turnier start <Minuten> | stop");
            }
        }
    }

    /** Zone aus dem Argument oder die, in der der Admin steht (sonst die einzige). */
    private AngelService.Zone zoneHere(Player p, String[] args, int idx) {
        AngelService.Zone z = args.length > idx ? service.zone(args[idx].toLowerCase()) : service.zoneAt(p.getLocation());
        if (z == null && args.length <= idx && service.zones().size() == 1) z = service.zones().get(0);
        if (z == null) Msg.send(p, "<red>Keine Angel-Zone gefunden. Stell dich hinein oder gib die ID an.");
        return z;
    }

    private void zone(Player p, String[] args) {
        String action = args.length > 2 ? args[2].toLowerCase() : "";
        switch (action) {
            case "erstellen", "create" -> {
                if (args.length < 4) {
                    Msg.send(p, "<red>Nutzung: /kp angel zone erstellen <id> [Name]");
                    return;
                }
                Location[] c = corners.get(p.getUniqueId());
                if (c == null || c[0] == null || c[1] == null) c = core.edit().corners(p.getUniqueId());
                String name = args.length > 4 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : args[3];
                String err = service.createZone(args[3].toLowerCase(), c == null ? null : c[0], c == null ? null : c[1],
                        name, p.getLocation());
                Msg.send(p, err == null ? "<green>Angel-Zone " + args[3] + " angelegt. Hologramm: /kp angel holo" : "<red>" + err);
            }
            case "löschen", "loeschen", "delete" -> {
                if (args.length < 4) {
                    Msg.send(p, "<red>Nutzung: /kp angel zone löschen <id>");
                    return;
                }
                AngelService.Zone z = service.zone(args[3].toLowerCase());
                if (z != null) holo.remove(z);
                Msg.send(p, service.deleteZone(args[3].toLowerCase()) ? "<green>Angel-Zone gelöscht." : "<red>Keine Angel-Zone mit dieser ID.");
            }
            default -> {
                if (service.zones().isEmpty()) Msg.send(p, "<gray>Noch keine Angel-Zonen.");
                for (AngelService.Zone z : service.zones()) {
                    Msg.send(p, "<aqua>▪ <white>" + z.id + " <gray>(" + Msg.escape(z.name) + ") " + z.world + " "
                            + z.x1 + "," + z.z1 + " bis " + z.x2 + "," + z.z2 + (z.tp != null ? " · Teleport" : "")
                            + (z.holo != null ? " · Hologramm" : ""));
                }
            }
        }
    }
}
