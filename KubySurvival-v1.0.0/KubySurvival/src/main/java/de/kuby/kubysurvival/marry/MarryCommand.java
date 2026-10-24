package de.kuby.kubysurvival.marry;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.casino.CasinoInput;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /marry und /bank für Spieler, dazu die Admin-Befehle /kp heiraten und /kp bank. */
public final class MarryCommand implements CommandExecutor, TabCompleter {

    private final KubyPlugins core;
    private final MarryService marry;
    private final BankService bank;
    private final MarryMenus menus;
    private final SurvivalNpc npcs;

    public MarryCommand(KubyPlugins core, MarryService marry, BankService bank, MarryMenus menus, SurvivalNpc npcs) {
        this.core = core;
        this.marry = marry;
        this.bank = bank;
        this.menus = menus;
        this.npcs = npcs;
    }

    private static void err(Player p, String e) {
        if (e != null) MarryService.send(p, "<red>" + Msg.escape(e));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (cmd.getName().equalsIgnoreCase("bank")) return bank(p, args);
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> menus.marry(p);
            case "propose", "antrag" -> {
                if (args.length < 2) {
                    MarryService.send(p, "<white>/marry propose <Spieler>");
                    return true;
                }
                err(p, marry.propose(p, Bukkit.getPlayerExact(args[1])));
            }
            case "accept", "ja" -> err(p, marry.accept(p));
            case "deny", "nein", "ablehnen" -> err(p, marry.deny(p));
            case "cancel", "zurueckziehen", "zurückziehen" -> err(p, marry.cancel(p));
            case "church", "kirche" -> err(p, marry.toChurch(p));
            case "tp" -> err(p, marry.tp(p));
            case "divorce", "scheidung" -> {
                if (marry.partnerOf(p.getUniqueId()) == null) {
                    err(p, "Du bist nicht verheiratet.");
                } else if (args.length > 1 && args[1].equalsIgnoreCase("bestaetigen")) {
                    err(p, marry.divorce(p));
                } else {
                    MarryService.send(p, "Wirklich scheiden? Das Ehe-Konto wird halbiert, danach dürft ihr eine Zeit lang nicht "
                            + "neu heiraten. <click:run_command:'/marry divorce bestaetigen'><red><b>[Ja, scheiden]</b></click>");
                }
            }
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        MarryService.send(p, "<white>Befehle:");
        String[][] lines = {
                {"/marry", "Ehe-Menü"}, {"/marry propose <Spieler>", "Antrag machen"},
                {"/marry accept", "Antrag annehmen, beim Priester"}, {"/marry deny", "Antrag ablehnen"},
                {"/marry cancel", "Eigenen Antrag zurückziehen"}, {"/marry church", "Zur Kirche"},
                {"/marry tp", "Zu deinem Partner"}, {"/bank", "Bank und Ehe-Konto"}, {"/marry divorce", "Scheidung"}};
        for (String[] l : lines) Msg.send(p, "<aqua>" + Msg.escape(l[0]) + " <dark_gray>- <gray>" + l[1]);
    }

    private boolean bank(Player p, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("einzahlen") && !sub.equals("abheben")) {
            menus.bank(p);
            return true;
        }
        if (args.length < 2) {
            MarryService.send(p, "<white>/bank " + sub + " <Betrag> [Konto]");
            return true;
        }
        long amount = CasinoInput.parse(args[1]);
        if (amount <= 0) {
            err(p, "Bitte einen Betrag angeben, z. B. 1000 oder 2,5k.");
            return true;
        }
        List<BankService.Account> mine = bank.of(p.getUniqueId());
        BankService.Account target = null;
        if (args.length > 2) {
            String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            for (BankService.Account a : mine) if (a.name.equalsIgnoreCase(name)) target = a;
            if (target == null) {
                err(p, "Kein Konto mit diesem Namen. Deine Konten: /bank");
                return true;
            }
        } else if (!mine.isEmpty()) {
            target = mine.get(0);
        }
        if (target == null) {
            err(p, "Du hast noch kein Konto. Öffne /bank.");
            return true;
        }
        String e = sub.equals("einzahlen") ? bank.deposit(p, target, amount) : bank.withdraw(p, target, amount);
        if (e != null) err(p, e);
        else MarryService.send(p, (sub.equals("einzahlen") ? "Eingezahlt: <gold>" : "Abgehoben: <gold>") + Msg.coins(amount)
                + " Coins</gold> <gray>(" + Msg.escape(target.name) + ", Stand <gold>" + Msg.coins(target.balance) + "</gold>)");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("bank")) {
            if (args.length == 1) out.addAll(List.of("einzahlen", "abheben"));
            else if (args.length == 3 && sender instanceof Player p) {
                for (BankService.Account a : bank.of(p.getUniqueId())) out.add(a.name);
            }
        } else if (args.length == 1) {
            out.addAll(List.of("propose", "accept", "deny", "cancel", "church", "tp", "divorce"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("propose")) {
            for (Player o : Bukkit.getOnlinePlayers()) if (o != sender) out.add(o.getName());
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    // =================================================================================================================
    // Admin: /kp heiraten ..., /kp bank ...
    // =================================================================================================================

    public void adminMarry(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "priester" -> {
                npcs.spawn(SurvivalNpc.Role.PRIESTER, p.getLocation());
                if (marry.church() == null) marry.setChurch(inFront(p.getLocation()));
                MarryService.send(p, "Priester steht. Wer gefragt wurde, sagt mit Rechtsklick auf ihn Ja (beide in "
                        + core.getConfig().getDouble("marry.priest-radius", 10) + " Blöcken).");
            }
            case "kirche" -> {
                marry.setChurch(p.getLocation());
                MarryService.send(p, "Kirche gesetzt: hierhin führt /marry church.");
            }
            case "entfernen" -> {
                int n = npcs.remove(p.getLocation(), SurvivalNpc.Role.PRIESTER, 4);
                MarryService.send(p, n + " Priester entfernt.");
            }
            default -> MarryService.send(p, "<white>/kp heiraten priester <gray>Priester an deiner Position (schaut wie du) · "
                    + "<white>kirche <gray>Ziel für /marry church · <white>entfernen <gray>Priester in 4 Blöcken entfernen");
        }
    }

    public void adminBank(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "bankier" -> {
                npcs.spawn(SurvivalNpc.Role.BANKIER, p.getLocation());
                bank.setBankPoint(inFront(p.getLocation()));
                MarryService.send(p, "Bankier steht. Im Umkreis von " + core.getConfig().getDouble("bank.radius", 8)
                        + " Blöcken gilt: an der Bank (ohne Grenze, Konten mit Freunden).");
            }
            case "entfernen" -> {
                int n = npcs.remove(p.getLocation(), SurvivalNpc.Role.BANKIER, 4);
                MarryService.send(p, n + " Bankier entfernt.");
            }
            default -> MarryService.send(p, "<white>/kp bank bankier <gray>Bankier an deiner Position (schaut wie du) · "
                    + "<white>entfernen <gray>Bankier in 4 Blöcken entfernen");
        }
    }

    /** Punkt zwei Blöcke vor der Figur, zu ihr gedreht (Ziel für Teleports). */
    private static Location inFront(Location l) {
        Vector dir = l.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        Location at = l.clone().add(dir.normalize().multiply(2));
        at.setYaw(l.getYaw() + 180);
        at.setPitch(0);
        return at;
    }
}
