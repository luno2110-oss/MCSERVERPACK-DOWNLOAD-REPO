package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.MainMenu;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /befehle [seite]: Befehlsübersicht im Chat, mehrere Seiten mit Klick-Navigation.
 * /menu öffnet das Hauptmenü als GUI.
 */
public final class HelpCommand implements CommandExecutor {

    /** permission == null: für alle sichtbar. */
    private record Entry(String command, String description, String permission) {}
    private record Page(String title, List<Entry> entries) {}

    private static final List<Page> PAGES = List.of(
            new Page("Start", List.of(
                    new Entry("/rtp", "Zufalls-Teleport in die Wildnis (3 s Wartezeit)", null),
                    new Entry("/spawn", "Zurück zum Spawn", null),
                    new Entry("/sethome <Name>", "Home auf deiner Position setzen", null),
                    new Entry("/home <Name>", "Zu einem Home reisen", null),
                    new Entry("/homes", "Alle Homes als Menü", null),
                    new Entry("/delhome <Name>", "Home löschen", null),
                    new Entry("/daily", "30-Tage-Kalender mit Belohnungen", null),
                    new Entry("/menu", "Hauptmenü als GUI öffnen", null))),
            new Page("Wirtschaft", List.of(
                    new Entry("/balance", "Dein Kontostand", null),
                    new Entry("/pay <Spieler> <Betrag>", "$ an einen Spieler senden", null),
                    new Entry("/baltop", "Reichsten-Liste", null),
                    new Entry("/sell", "Alle verkaufbaren Items verkaufen", null),
                    new Entry("/worth", "Preisliste ansehen", null),
                    new Entry("/sellmulti", "Verkaufs-Multiplikator verbessern", null),
                    new Entry("/shop", "Items mit$ kaufen", null),
                    new Entry("/coinflip <Betrag>", "50/50 um$", null),
                    new Entry("/casino", "Chips, Jackpot, Blackjack, Roulette, Slots, Crash", null),
                    new Entry("/music", "Casino-Musik aussuchen", null),
                    new Entry("/poker", "Poker in der VIP-Lounge", null),
                    new Entry("/dailypot", "Tagespot: Lose kaufen", null))),
            new Page("Markt & Handel", List.of(
                    new Entry("/markt", "Auktionshaus öffnen", null),
                    new Entry("/markt sell <Preis>", "Item aus der Hand anbieten", null),
                    new Entry("/orders", "Offene Orders ansehen", null),
                    new Entry("/orders add <Material> <Menge> <Preis>", "Order aufgeben", null),
                    new Entry("/abholbox", "Käufe und Lieferungen ansehen", null),
                    new Entry("/abholen", "Alles aus der Abholbox einsammeln", null),
                    new Entry("/trade <Spieler>", "Mit einem Spieler handeln", null),
                    new Entry("/trade accept", "Handelsanfrage annehmen", null))),
            new Page("Team & Freunde", List.of(
                    new Entry("/team create <Name>", "Team gründen (kostet$)", null),
                    new Entry("/team invite <Spieler>", "Spieler einladen (nur Teamchef)", null),
                    new Entry("/team accept", "Einladung annehmen", null),
                    new Entry("/team leave", "Team verlassen", null),
                    new Entry("/team home", "Zum Teamhome reisen", null),
                    new Entry("/team sethome", "Teamhome auf deiner Position setzen", null),
                    new Entry("/team bank <Betrag>", "Teamkasse füllen", null),
                    new Entry("/team ally <Team>", "Mit einem anderen Team verbünden", null),
                    new Entry("/tc <Nachricht>", "Team-Chat", null),
                    new Entry("/friend add <Spieler>", "Freundschaftsanfrage senden", null),
                    new Entry("/friends", "Freundesliste öffnen", null))),
            new Page("Kampf & Duelle", List.of(
                    new Entry("/duell", "Duell-Menü öffnen", null),
                    new Entry("/duell <Spieler> [Einsatz]", "Herausforderung, optional mit Einsatz", null),
                    new Entry("/rtpqueue", "Duo-RTP: zu zweit an einen Ort, Kampf!", null),
                    new Entry("/bounty <Spieler> <Betrag>", "Kopfgeld auf einen Spieler setzen", null),
                    new Entry("/bounties", "Steckbriefe ansehen", null),
                    new Entry("/stats", "Deine Kills, Tode und Trophäen", null),
                    new Entry("/trophaeen", "Trophäen und Liga", null),
                    new Entry("/ranking", "Trophäen-Rangliste", null))),
            new Page("Sozial & Extras", List.of(
                    new Entry("/msg <Spieler> <Text>", "Private Nachricht senden", null),
                    new Entry("/r <Text>", "Auf die letzte Nachricht antworten", null),
                    new Entry("/inbox", "Verpasste Nachrichten lesen", null),
                    new Entry("/tags", "Titel auswählen", null),
                    new Entry("/juwelen", "Dein Juwelen-Konto", null),
                    new Entry("/juwelenshop", "Juwelen-Pakete ansehen", null),
                    new Entry("/kadmin", "Admin-Panel (nur Admins)", "kuby.admin"))),
            new Page("Weitere 1", List.of(
                    new Entry("/meta", "Wochen-Meta ansehen und abstimmen", null),
                    new Entry("/booster", "Booster-Status (18-24 Uhr: 1,5x XP)", null),
                    new Entry("/xpshop", "XP-Shop", null),
                    new Entry("/battlepass", "Battlepass ansehen", null),
                    new Entry("/cosmetics", "Kosmetik auswählen", null),
                    new Entry("/turnier", "Turnier-Anmeldung", null),
                    new Entry("/verify", "Discord verknüpfen (+3 Homeplätze)", null),
                    new Entry("/versichern", "Raid-Versicherung für Kisten", null),
                    new Entry("/enderchest", "Enderchest öffnen", null),
                    new Entry("/kristaltrade", "Kristall-Tausch", null),
                    new Entry("/investments", "Marktwirtschaft-Anteile", null)
            )),
            new Page("Weitere 2", List.of(
                    new Entry("/quizvote", "Quiz-Kategorie abstimmen", null),
                    new Entry("/totenkiste", "Totenkiste öffnen", null),
                    new Entry("/kits", "Kits abholen", null),
                    new Entry("/hub", "Zur Lobby wechseln", null),
                    new Entry("/tpa <Spieler>", "Teleport-Anfrage senden", null),
                    new Entry("/tpaccept", "Teleport-Anfrage annehmen", null),
                    new Entry("/tpadeny", "Teleport-Anfrage ablehnen", null),
                    new Entry("/spielzeit", "Spielzeit-Belohnungen", null),
                    new Entry("/erfolge", "Erfolge mit Fortschritt", null),
                    new Entry("/vote", "Voten für Juwelen und Coins", null),
                    new Entry("/claim", "Landclaims (Survival)", null),
                    new Entry("/angeln", "Angel-Menü (Survival)", null),
                    new Entry("/skyblock", "Skyblock-Checkliste und Erfolge", null),
                    new Entry("/kp", "Admin-Befehle", "kuby.admin")
            )));

    private final KubyPlugins plugin;

    public HelpCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("menu")) {
            new MainMenu(plugin).open(p);
            return true;
        }
        int requested = 1;
        if (args.length > 0) {
            try {
                requested = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                requested = 1;
            }
        }
        showPage(p, requested);
        return true;
    }

    private void showPage(Player p, int requested) {
        int total = PAGES.size();
        int page = Math.max(1, Math.min(requested, total));
        Page pg = PAGES.get(page - 1);

        p.sendMessage(Msg.parse("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        p.sendMessage(Msg.parse("<gold>Befehle <dark_gray>» <white>" + pg.title() + " <gray>(" + page + "/" + total + ")"));

        for (Entry e : pg.entries()) {
            if (e.permission() != null && !p.hasPermission(e.permission())) continue;
            Component line = Msg.parse("<yellow>" + Msg.escape(e.command()) + " <dark_gray>— <gray>" + Msg.escape(e.description()))
                    .clickEvent(ClickEvent.suggestCommand(e.command()))
                    .hoverEvent(HoverEvent.showText(Msg.parse("<gray>Klicken zum Eintragen")));
            p.sendMessage(line);
        }

        Component nav = Component.empty();
        if (page > 1) {
            nav = nav.append(Msg.parse("<aqua>« Zurück")
                    .clickEvent(ClickEvent.runCommand("/befehle " + (page - 1)))
                    .hoverEvent(HoverEvent.showText(Msg.parse("<gray>Seite " + (page - 1)))));
        }
        nav = nav.append(Msg.parse("<dark_gray>    " + page + " / " + total + "    "));
        if (page < total) {
            nav = nav.append(Msg.parse("<aqua>Weiter »")
                    .clickEvent(ClickEvent.runCommand("/befehle " + (page + 1)))
                    .hoverEvent(HoverEvent.showText(Msg.parse("<gray>Seite " + (page + 1)))));
        }
        p.sendMessage(nav);
        p.sendMessage(Msg.parse("<dark_gray>Tipp: <gray>Klick auf einen Befehl schreibt ihn ins Chatfeld."));
    }
}
