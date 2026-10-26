package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.JewelShopMenu;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * /juwelen und /juwelenshop [gift &lt;Spieler&gt;|&lt;Seite&gt;]. /juwelen give ist für Admins. SMP: Dialog des Kerns,
 * andere Modi: Dialog eines Addons (registerJewelShop), sonst der Truhen-Shop.
 */
public final class JewelCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public JewelCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("juwelenshop")) {
            String mode = plugin.modes().modeOf(p.getUniqueId());
            boolean smp = ModeService.SMP.equals(mode);
            KubyPlugins.JewelShopHook hook = plugin.jewelShopHook(mode);
            if (args.length > 0 && (args[0].equalsIgnoreCase("gift") || args[0].equalsIgnoreCase("schenken"))) {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (args.length < 2) {
                    Msg.send(p, "<gray>Verschenken: <white>/juwelenshop gift <Spieler> <gray>(Ränge, Keys, Pakete; bezahlt mit deinen Juwelen)");
                } else if (target == null) {
                    Msg.send(p, "<red>" + args[1] + " ist gerade nicht online.");
                } else if (target.equals(p)) {
                    Msg.send(p, "<red>An dich selbst kannst du nichts verschenken. Kauf es einfach im /juwelenshop.");
                } else if (smp) {
                    plugin.jewelShop().openGift(p, target);
                } else if (hook == null || !hook.gift(p, target)) {
                    new de.kuby.kubyplugins.gui.JewelStoreMenu(plugin, plugin.modes().modeOf(p.getUniqueId()), 0, target).open(p);
                }
                return true;
            }
            if (smp) {
                // SMP: Dialog wie in der Referenz (Seite direkt: /juwelenshop raenge|battlepass|crates|pakete)
                String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
                de.kuby.kubyplugins.gui.JewelShopScreen.Page page = switch (sub) {
                    case "raenge", "ränge", "ranks" -> de.kuby.kubyplugins.gui.JewelShopScreen.Page.RANKS;
                    case "battlepass", "pass" -> de.kuby.kubyplugins.gui.JewelShopScreen.Page.PASS;
                    case "crates", "kisten", "keys" -> de.kuby.kubyplugins.gui.JewelShopScreen.Page.CRATES;
                    case "pakete", "packages" -> de.kuby.kubyplugins.gui.JewelShopScreen.Page.PACKS;
                    default -> de.kuby.kubyplugins.gui.JewelShopScreen.Page.HOME;
                };
                plugin.jewelShop().open(p, page);
                return true;
            }
            // Addon (Survival: Dialog mit Möbeln, Pets, Tokens), sonst der Truhen-Shop
            if (hook != null && hook.open(p, args.length > 0 ? args[0] : "")) return true;
            new de.kuby.kubyplugins.gui.JewelStoreMenu(plugin, mode, 0).open(p);
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (!p.hasPermission("kuby.admin")) {
                Msg.send(p, "<red>Keine Berechtigung.");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            long amount;
            try {
                amount = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                Msg.send(p, "<red>Ungültige Menge.");
                return true;
            }
            if (target == null || amount <= 0) {
                Msg.send(p, "<red>Spieler offline oder Menge ungültig.");
                return true;
            }
            plugin.jewels().add(target.getUniqueId(), amount);
            Msg.send(p, "<green>" + amount + " Juwelen an " + target.getName() + " vergeben.");
            Msg.send(target, "<aqua>Du hast " + amount + " Juwelen erhalten!");
            return true;
        }
        Msg.send(p, "<gray>Deine Juwelen: <aqua>" + Msg.coins(plugin.jewels().balance(p.getUniqueId())));
        Msg.send(p, "<gray>Juwelen kaufst du mit <white>/juwelenshop");
        return true;
    }
}
