package de.kuby.kubysurvival.claim;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** /claim: Übersicht, Schaufel, erstellen, löschen, Liste, Vertrauen und Rahmen anzeigen. */
public final class ClaimCommand implements CommandExecutor {

    private final KubySurvival addon;

    public ClaimCommand(KubySurvival addon) {
        this.addon = addon;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        ClaimService claims = addon.claims();
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        switch (sub) {
            case "info" -> info(p, claims);
            case "schaufel", "tool" -> {
                p.getInventory().addItem(addon.claimTool().create());
                Msg.send(p, "<green>Claim-Schaufel erhalten. <gray>Rechtsklick auf zwei gegenüberliegende Ecken.");
            }
            case "erstellen", "create" -> {
                Location[] c = addon.claimTool().corners(p.getUniqueId());
                String err = claims.create(p, c == null ? null : c[0], c == null ? null : c[1]);
                if (err != null) {
                    Msg.send(p, "<red>" + err);
                } else {
                    addon.claimTool().forget(p.getUniqueId());
                    Sfx.play(addon.core(), p, Sfx.Kind.SUCCESS);
                    Msg.send(p, "<green>Grundstück gesichert! <gray>Freie Claimblöcke: <white>" + claims.available(p.getUniqueId()));
                    ClaimService.Claim made = claims.at(c[0]);
                    if (made != null) addon.claimTool().show(p, made.x1(), made.z1(), made.x2(), made.z2());
                }
            }
            case "löschen", "loeschen", "delete" -> {
                ClaimService.Claim c = claims.at(p.getLocation());
                if (c == null) {
                    Msg.send(p, "<red>Du stehst in keinem Claim.");
                } else if (!c.owner().equals(p.getUniqueId()) && !p.hasPermission("kuby.admin")) {
                    Msg.send(p, "<red>Das ist nicht dein Claim.");
                } else {
                    claims.delete(c);
                    Msg.send(p, "<green>Claim gelöscht. <gray>" + c.area() + " Claimblöcke sind wieder frei.");
                }
            }
            case "liste", "list" -> {
                List<ClaimService.Claim> list = claims.claimsOf(p.getUniqueId());
                if (list.isEmpty()) Msg.send(p, "<gray>Du hast noch keine Claims. <white>/claim schaufel");
                for (ClaimService.Claim c : list) {
                    Msg.send(p, "<gold>▪ <white>" + c.width() + "x" + c.length() + " <gray>bei <white>"
                            + (c.x1() + c.x2()) / 2 + ", " + (c.z1() + c.z2()) / 2 + " <gray>(" + c.area() + " Blöcke)");
                }
            }
            case "anzeigen", "show" -> {
                ClaimService.Claim c = claims.at(p.getLocation());
                if (c == null) {
                    Msg.send(p, "<gray>Hier ist Wildnis.");
                } else {
                    addon.claimTool().show(p, c.x1(), c.z1(), c.x2(), c.z2());
                    Msg.send(p, "<green>Der Rand wird 10 Sekunden angezeigt.");
                }
            }
            case "trust", "untrust" -> {
                if (args.length < 2) {
                    Msg.send(p, "<red>Nutzung: /claim " + sub + " <Spieler>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    Msg.send(p, "<red>Spieler " + args[1] + " war noch nie auf dem Server.");
                    return true;
                }
                if (target.getUniqueId().equals(p.getUniqueId())) {
                    Msg.send(p, "<red>Dir selbst musst du nicht vertrauen.");
                    return true;
                }
                boolean changed = sub.equals("trust")
                        ? claims.trust(p.getUniqueId(), target.getUniqueId())
                        : claims.untrust(p.getUniqueId(), target.getUniqueId());
                Msg.send(p, changed
                        ? (sub.equals("trust") ? "<green>" + target.getName() + " darf jetzt auf allen deinen Claims bauen."
                        : "<green>" + target.getName() + " darf auf deinen Claims nicht mehr bauen.")
                        : "<gray>Keine Änderung.");
            }
            default -> Msg.send(p, "<red>Nutzung: /claim [schaufel|erstellen|löschen|liste|trust <Spieler>|untrust <Spieler>|anzeigen]");
        }
        return true;
    }

    private void info(Player p, ClaimService claims) {
        UUID id = p.getUniqueId();
        Msg.send(p, "<gold><b>Landclaims</b>");
        Msg.send(p, "<gray>Freie Claimblöcke: <white>" + claims.available(id) + " <gray>(+"
                + addon.core().getConfig().getLong("landclaim.blocks-per-hour", 100) + " pro Stunde in Survival)");
        Msg.send(p, "<gray>Deine Claims: <white>" + claims.claimsOf(id).size() + " <gray>/ "
                + addon.core().getConfig().getInt("landclaim.max-claims", 10));
        ClaimService.Claim here = claims.at(p.getLocation());
        if (here != null) {
            String owner = Bukkit.getOfflinePlayer(here.owner()).getName();
            Msg.send(p, "<gray>Hier: Grundstück von <white>" + (owner == null ? "?" : owner) + " <gray>(" + here.width() + "x" + here.length() + ")");
        }
        Set<UUID> trusted = claims.trusted(id);
        if (!trusted.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (UUID t : trusted) {
                String n = Bukkit.getOfflinePlayer(t).getName();
                if (sb.length() > 0) sb.append(", ");
                sb.append(n == null ? "?" : n);
            }
            Msg.send(p, "<gray>Vertraut: <white>" + sb);
        }
        Msg.send(p, "<gray>Befehle: <white>/claim schaufel, erstellen, löschen, liste, trust, untrust, anzeigen");
    }
}
