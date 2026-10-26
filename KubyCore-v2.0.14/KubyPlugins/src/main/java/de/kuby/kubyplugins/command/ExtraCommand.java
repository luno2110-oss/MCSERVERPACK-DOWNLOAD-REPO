package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.gui.TeleportEffectMenu;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.gui.KitMenu;
import de.kuby.kubyplugins.gui.QuizVoteMenu;
import de.kuby.kubyplugins.gui.InvestmentsMenu;
import de.kuby.kubyplugins.gui.KristalTradeMenu;
import org.bukkit.block.Block;
import de.kuby.kubyplugins.gui.XpShopMenu;
import de.kuby.kubyplugins.gui.MetaMenu;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.DailyPotMenu;
import de.kuby.kubyplugins.gui.RtpQueueMenu;
import de.kuby.kubyplugins.gui.TagsMenu;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** /dailypot, /trade, /tags und /rtpqueue */
public final class ExtraCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public ExtraCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "dailypot" -> new DailyPotMenu(plugin).open(p);
            case "trade" -> trade(p, args);
            case "tags" -> tags(p, args);
            case "rtpqueue" -> rtp(p, args);
            case "meta" -> new MetaMenu(plugin).open(p);
            case "xpshop" -> new XpShopMenu(plugin).open(p);
            case "booster" -> booster(p);
            case "battlepass" -> plugin.battlepassScreen().open(p);
            case "verify" -> verify(p);
            case "enderchest" -> p.openInventory(plugin.enderchests().open(p));
            case "kristaltrade" -> new KristalTradeMenu(plugin).open(p);
            case "investments" -> new InvestmentsMenu(plugin).open(p);
            case "quizvote" -> new QuizVoteMenu(plugin).open(p);
            case "kits" -> new KitMenu(plugin).open(p);
            case "tpa" -> tpa(p, args, false);
            case "tpahere" -> tpa(p, args, true);
            case "tpaccept" -> {
                String err = plugin.tpa().accept(p);
                if (err != null) Msg.send(p, "<red>" + err);
            }
            case "tpadeny" -> {
                String err = plugin.tpa().deny(p);
                if (err != null) Msg.send(p, "<red>" + err);
            }
            case "spielzeit", "playtime" -> {
                if (args.length > 0 && (args[0].equalsIgnoreCase("claim") || args[0].equalsIgnoreCase("abholen"))) {
                    int n = plugin.playtime().claimAll(p);
                    if (n < 0) Msg.send(p, "<red>" + plugin.playtime().blocked(p));
                    else if (n == 0) Msg.send(p, "<gray>Gerade ist keine Spielzeit-Stufe fertig. <white>/playtime");
                    else {
                        Msg.send(p, "<#F2C84A><b>SPIELZEIT</b> <dark_gray>▶ <gray>" + n + (n == 1 ? " Stufe" : " Stufen") + " abgeholt.");
                        Sfx.play(plugin, p, Sfx.Kind.SUCCESS_BIG);
                    }
                } else {
                    new de.kuby.kubyplugins.gui.PlaytimeScreen(plugin).open(p);
                }
            }
            case "hub" -> {
                String err = plugin.modes().switchTo(p, ModeService.HUB);
                if (err != null) Msg.send(p, "<red>" + err);
            }
            case "totenkiste" -> {
                String err = plugin.totenkisten().place(p);
                if (err != null) Msg.send(p, "<red>" + err);
                else Msg.send(p, "<green>Totenkiste gesetzt. Rechtsklick öffnet sie.");
            }
            default -> { return false; }
        }
        return true;
    }

    private void trade(Player p, String[] args) {
        if (args.length == 0) {
            Msg.send(p, "<red>Nutzung: /trade <Spieler> oder /trade accept");
            return;
        }
        if (args[0].equalsIgnoreCase("accept")) {
            String err = plugin.trades().accept(p);
            if (err != null) Msg.send(p, "<red>" + err);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Msg.send(p, "<red>Spieler nicht online.");
            return;
        }
        String err = plugin.trades().request(p, target);
        Msg.send(p, err == null ? "<green>Handelsanfrage an <white>" + target.getName() + " <green>gesendet."
                : "<red>" + err);
    }

    private void tags(Player p, String[] args) {
        if (args.length == 0) {
            new TagsMenu(plugin).open(p);
            return;
        }
        if (args[0].equalsIgnoreCase("grant") && args.length == 3 && p.hasPermission("kuby.admin")) {
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (op == null) {
                Msg.send(p, "<red>Spieler unbekannt.");
                return;
            }
            plugin.tags().grant(op.getUniqueId(), args[2]);
            Msg.send(p, "<green>Titel <white>" + args[2] + " <green>an " + args[1] + " vergeben.");
            return;
        }
        Msg.send(p, "<red>Nutzung: /tags (Menü) oder /tags grant <Spieler> <Titel-ID>");
    }

    private void rtp(Player p, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("leave")) {
            String err = plugin.rtpQueue().leave(p.getUniqueId());
            Msg.send(p, err == null ? "<gray>Aus der Warteschlange entfernt." : "<red>" + err);
            return;
        }
        if (args.length == 0 && plugin.getConfig().getBoolean("rtpqueue.open-menu", false)) {
            new RtpQueueMenu(plugin).open(p);
            return;
        }
        String err = plugin.rtpQueue().join(p);
        if (err != null) Msg.send(p, "<red>" + err);
    }

    private void booster(Player p) {
        int start = plugin.getConfig().getInt("booster.start-hour", 18);
        int end = plugin.getConfig().getInt("booster.end-hour", 24);
        double mult = plugin.getConfig().getDouble("booster.multiplier", 1.5);
        if (plugin.booster().active()) {
            Msg.send(p, "<gold>Booster ist <green>aktiv<gold>: x" + mult + " auf XP und Daily-Belohnungen.");
        } else {
            Msg.send(p, "<gray>Booster ist gerade <red>inaktiv<gray>. Er läuft täglich von " + start + " bis " + end + " Uhr.");
        }
    }



    /** /verify: zeigt den Weg zur Verlinkung oder schaltet nach dem Verlinken Haken und Home frei. */
    private void verify(Player p) {
        String link = plugin.getConfig().getString("discord.invite", "https://discord.gg/");
        if (!plugin.discord().available()) {
            Msg.send(p, "<red>Verlinken ist auf diesem Server gerade nicht möglich (DiscordSRV fehlt).");
            return;
        }
        if (plugin.discord().discordIdOf(p.getUniqueId()) == null) {
            Msg.send(p, "<#5865F2><b>DISCORD</b> <dark_gray>▶ <gray>So verlinkst du deinen Account:");
            Msg.send(p, "<gray>1. Tritt unserem Discord bei: <click:open_url:'" + link + "'><#5865F2><u>" + Msg.escape(link) + "</u></click>");
            Msg.send(p, "<gray>2. Geh in den Kanal <white>„verify“</white> und klick auf <white>„Verifizieren“</white>.");
            Msg.send(p, "<gray>3. Danach hier noch einmal <white>/verify</white>.");
            Msg.send(p, "<gray>Dafür bekommst du einen <#5865F2>✔</#5865F2> hinter deinem Namen und ein Home mehr. Spielzeit-Belohnungen "
                    + "und Helfer im Support gibt es nur mit Verlinkung.");
            return;
        }
        plugin.discord().syncBooster(p);
        if (plugin.homes().verified(p.getUniqueId())) {
            Msg.send(p, "<green>Dein Discord-Account ist verlinkt. <#5865F2>✔");
            return;
        }
        plugin.homes().grantVerify(p.getUniqueId());
        refreshTabName(p);
        int homes = ModeService.SURVIVAL.equals(plugin.modes().modeOf(p.getUniqueId()))
                ? plugin.getConfig().getInt("homes.survival.discord-bonus", 1) : 3;
        Msg.send(p, "<green>Discord verlinkt! <#5865F2>✔</#5865F2> <green>steht jetzt hinter deinem Namen, dazu <white>+" + homes
                + (homes == 1 ? " Home" : " Homes") + "<green>.");
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS_BIG);
    }

    private void refreshTabName(Player p) {
        plugin.refreshTabName(p);
    }


    private void tpa(Player p, String[] args, boolean here) {
        if (args.length == 0) {
            Msg.send(p, "<red>Nutzung: /" + (here ? "tpahere" : "tpa") + " <Spieler>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Msg.send(p, "<red>Spieler nicht online.");
            return;
        }
        String err = plugin.tpa().request(p, target, here);
        if (err != null) Msg.send(p, "<red>" + err);
    }
}
