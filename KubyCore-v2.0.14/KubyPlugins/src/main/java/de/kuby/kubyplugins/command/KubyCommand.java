package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.DailyMenu;
import de.kuby.kubyplugins.gui.HomesMenu;
import de.kuby.kubyplugins.gui.LeaderboardMenu;
import de.kuby.kubyplugins.gui.MainMenu;
import de.kuby.kubyplugins.gui.ShopMenu;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Teleports;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class KubyCommand implements CommandExecutor, TabCompleter {

    private final KubyPlugins plugin;

    public KubyCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        UUID id = player.getUniqueId();

        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "befehle" -> new MainMenu(plugin).open(player);
            case "balance" -> balance(player);
            case "pay" -> pay(player, args);
            case "baltop" -> new LeaderboardMenu(plugin).open(player);
            case "shop" -> new ShopMenu(plugin).open(player);
            case "sethome" -> setHome(player, args.length > 0 ? args[0] : "base");
            case "home" -> home(player, args);
            case "delhome" -> delHome(player, args);
            case "homes" -> new HomesMenu(plugin).open(player);
            case "daily" -> new DailyMenu(plugin).open(player);
            case "rtp" -> Teleports.randomTeleport(plugin, player);
            case "spawn" -> Teleports.spawn(plugin, player);
            default -> { return false; }
        }
        return true;
    }

    private void balance(Player p) {
        UUID id = p.getUniqueId();
        String mode = plugin.modes().modeOf(id);
        Msg.send(p, "<gray>Kontostand <dark_gray>(" + de.kuby.kubyplugins.service.ModeService.label(mode) + ")<gray>: <gold>"
                + Msg.coins(plugin.economy().balance(id)) + " Coins <dark_gray>· <aqua>" + Msg.coins(plugin.jewels().balance(id)) + " Juwelen");
        if (de.kuby.kubyplugins.service.ModeService.SURVIVAL.equals(mode)) {
            Msg.send(p, "<gray>Coins verdienen: <click:run_command:'/job'><white>/job</click><gray>, <click:run_command:'/quest'><white>/quest</click>"
                    + "<gray>, <click:run_command:'/markt'><white>/markt</click><gray>, Voten. Abgeholtes wartet in <click:run_command:'/abholbox'><white>/abholbox</click><gray>.");
        }
    }

    private void pay(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, "<red>Nutzung: /pay <Spieler> <Betrag>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(p)) {
            Msg.send(p, "<red>Spieler nicht online oder ungültig.");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Ungültiger Betrag.");
            return;
        }
        if (amount <= 0 || plugin.economy().balance(p.getUniqueId()) < amount) {
            Msg.send(p, "<red>Nicht genug$ oder ungültiger Betrag.");
            return;
        }
        if (plugin.playerSettings().get(p.getUniqueId(), "pay.confirm")) {
            confirmPay(p, target, amount);
            return;
        }
        transfer(p, target, amount);
    }

    /** Pay-Bestätigung (Schalter in /settings): erst nach "Senden" wird überwiesen. */
    private void confirmPay(Player p, Player target, long amount) {
        boolean en = plugin.playerSettings().english(p.getUniqueId());
        String name = target.getName();
        io.papermc.paper.registry.data.dialog.DialogBase base = io.papermc.paper.registry.data.dialog.DialogBase
                .builder(Msg.parse("<gold><b>" + (en ? "Send money?" : "Geld senden?")))
                .canCloseWithEscape(true)
                .afterAction(io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction.CLOSE)
                .body(java.util.List.of(io.papermc.paper.registry.data.dialog.body.DialogBody.plainMessage(Msg.parse(
                        "<gray>" + (en ? "Send " : "") + "<gold>" + Msg.coins(amount) + "$</gold> " + (en ? "to " : "an ")
                                + "<white>" + name + "</white>" + (en ? "?" : " senden?")
                                + "\n<dark_gray>" + (en ? "Turn off in /settings: Pay confirmation" : "Abschalten in /settings: Pay-Bestätigung")), 260)))
                .build();
        net.kyori.adventure.text.event.ClickCallback.Options once = net.kyori.adventure.text.event.ClickCallback.Options.builder()
                .uses(1).lifetime(java.time.Duration.ofMinutes(2)).build();
        io.papermc.paper.registry.data.dialog.ActionButton yes = io.papermc.paper.registry.data.dialog.ActionButton.create(
                Msg.parse("<green><b>" + (en ? "Send" : "Senden") + "</b>"), null, 110,
                io.papermc.paper.registry.data.dialog.action.DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player pl)) return;
                    Player t = Bukkit.getPlayer(target.getUniqueId());
                    if (t == null) {
                        Msg.send(pl, "<red>" + name + (en ? " is offline now." : " ist jetzt offline."));
                        return;
                    }
                    transfer(pl, t, amount);
                }, once));
        io.papermc.paper.registry.data.dialog.ActionButton no = io.papermc.paper.registry.data.dialog.ActionButton.create(
                Msg.parse("<gray>" + (en ? "Cancel" : "Abbrechen")), null, 100,
                io.papermc.paper.registry.data.dialog.action.DialogAction.customClick((response, audience) -> { }, once));
        p.showDialog(io.papermc.paper.dialog.Dialog.create(b -> b.empty().base(base)
                .type(io.papermc.paper.registry.data.dialog.type.DialogType.confirmation(yes, no))));
    }

    private void transfer(Player p, Player target, long amount) {
        if (!plugin.economy().withdraw(p.getUniqueId(), amount)) {
            Msg.send(p, "<red>Nicht genug$ oder ungültiger Betrag.");
            return;
        }
        // Geld bleibt im Modus des Senders: Wer gerade woanders spielt, bekommt es in diesem Modus gutgeschrieben.
        String mode = plugin.modes().modeOf(p.getUniqueId());
        boolean sameMode = mode.equals(plugin.modes().modeOf(target.getUniqueId()));
        plugin.modes().depositIn(target.getUniqueId(), mode, amount);
        Msg.send(p, "<gray>Du hast <gold>" + Msg.coins(amount) + "$ <gray>an <white>" + target.getName() + " <gray>gesendet.");
        Msg.send(target, "<gray>Du hast <gold>" + Msg.coins(amount) + "$ <gray>von <white>" + p.getName() + " <gray>erhalten"
                + (sameMode ? "." : " <dark_gray>(auf dein " + de.kuby.kubyplugins.service.ModeService.label(mode) + "-Konto)."));
    }

    private void setHome(Player p, String name) {
        String blocked = plugin.homes().blockedReason(p.getLocation());
        if (blocked != null) {
            Msg.send(p, "<red>" + blocked);
            return;
        }
        if (plugin.homes().set(p, name, p.getLocation())) {
            Msg.send(p, "<green>Home <white>" + name + " <green>gesetzt.");
        } else {
            Msg.send(p, "<red>Home-Limit erreicht. Mehr Homes über /verify oder einen höheren Rang.");
        }
    }

    private void home(Player p, String[] args) {
        if (args.length == 0) {
            new HomesMenu(plugin).open(p);
            return;
        }
        Location loc = plugin.homes().get(p.getUniqueId(), args[0]);
        if (loc == null) {
            Msg.send(p, "<red>Home nicht gefunden. Siehe /homes");
            return;
        }
        Teleports.delayed(plugin, p, loc, args[0]);
    }

    private void delHome(Player p, String[] args) {
        if (args.length == 0) {
            Msg.send(p, "<red>Nutzung: /delhome <Name>");
            return;
        }
        plugin.homes().delete(p.getUniqueId(), args[0]);
        Msg.send(p, "<gray>Home <white>" + args[0] + " <gray>gelöscht.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && command.getName().equalsIgnoreCase("pay")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
