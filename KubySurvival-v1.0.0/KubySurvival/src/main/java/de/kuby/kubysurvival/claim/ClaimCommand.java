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

/** /claim: Menü, Claim-Axt, ok (anlegen), löschen, Liste, Vertrauen, Grenzen zeigen und Hilfe. */
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
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase();
        switch (sub) {
            case "menu", "menü" -> new ClaimMenu(addon).open(p);
            case "info" -> info(p, claims);
            case "axt", "schaufel", "tool" -> {
                for (org.bukkit.inventory.ItemStack it : p.getInventory().getContents()) {
                    if (addon.claimTool().isTool(it)) {
                        Msg.send(p, "<gray>Du hast schon eine Claim-Axt im Inventar.");
                        return true;
                    }
                }
                p.getInventory().addItem(addon.claimTool().create()).values()
                        .forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
                Msg.send(p, "<green>Claim-Axt erhalten. <gray>Linksklick: Ecke 1, Rechtsklick: Ecke 2, dann <white>/claim ok");
            }
            case "ok", "erstellen", "create" -> {
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
                if (list.isEmpty()) Msg.send(p, "<gray>Du hast noch keine Claims. <white>/claim axt");
                for (ClaimService.Claim c : list) {
                    Msg.send(p, "<gold>▪ <white>" + c.width() + "x" + c.length() + " <gray>bei <white>"
                            + (c.x1() + c.x2()) / 2 + ", " + (c.z1() + c.z2()) / 2 + " <gray>(" + c.area() + " Blöcke)");
                }
            }
            case "zeigen", "anzeigen", "show" -> {
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
            case "hilfe", "help" -> help(p);
            default -> {
                Msg.send(p, "<red>Unbekannter Befehl.");
                help(p);
            }
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
        Msg.send(p, "<gray>Alle Befehle: <white>/claim hilfe");
    }

    private void help(Player p) {
        Msg.send(p, "<gold><b>Grundstücke</b>");
        String[][] lines = {
                {"/claim", "Menü: Grundstücke, Freunde, Rechte"},
                {"/claim axt", "Claim-Axt holen"},
                {"/claim ok", "Markierte Fläche sichern"},
                {"/claim zeigen", "Grenzen hier anzeigen"},
                {"/claim info", "Freie Blöcke und wer hier wohnt"},
                {"/claim liste", "Deine Grundstücke"},
                {"/claim trust <Spieler>", "Freund eintragen"},
                {"/claim untrust <Spieler>", "Freund austragen"},
                {"/claim löschen", "Grundstück, in dem du stehst, löschen"}};
        for (String[] l : lines) {
            Msg.send(p, "<click:suggest_command:'" + l[0].replaceAll(" <.*", " ") + "'><white>" + Msg.escape(l[0]) + "</click> <gray>" + l[1]);
        }
        Msg.send(p, "<gray>Erweitern: Linksklick mit der Axt auf eine Ecke deines Grundstücks, dann Rechtsklick auf die neue Position.");
    }
}
