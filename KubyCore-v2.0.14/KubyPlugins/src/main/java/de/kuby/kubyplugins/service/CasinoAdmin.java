package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Casino-Admin als Dialog-Fenster: Status und Aufbau, Auszahlung je Spiel, die letzten Protokoll-Einträge,
 * Schieberegler für Preise und Einsatzlimits, Buttons zum Einrichten der Tische und zum Öffnen oder Schließen.
 */
public final class CasinoAdmin {

    /** Ein Wert aus der config.yml. scale rechnet die Anzeige in den gespeicherten Wert um (Prozent → Anteil). */
    private record Setting(String key, String path, String label, float min, float max, float step, double def, double scale) {}

    private static final List<Setting> SETTINGS = List.of(
            new Setting("bj_bet", "casino.blackjack.bet", "Blackjack-Einsatz (Chips)", 10, 10000, 10, 100, 1),
            new Setting("roulette_max", "casino.roulette.max-bet", "Roulette-Höchsteinsatz (Chips)", 100, 100000, 100, 5000, 1),
            new Setting("roulette_wait", "casino.roulette.wait-seconds", "Roulette-Wartezeit (Sekunden)", 5, 120, 5, 20, 1),
            new Setting("slots_bet", "casino.slots.bet", "Slots-Einsatz (Chips)", 10, 5000, 10, 100, 1),
            new Setting("crash_bet", "casino.crash.bet", "Crash-Einsatz (Chips)", 10, 10000, 10, 100, 1),
            new Setting("crash_max", "casino.crash.max-multiplier", "Crash-Höchstwert (x)", 2, 100, 1, 50, 1),
            new Setting("jackpot_share", "casino.jackpot-share", "Jackpot-Anteil (%)", 0, 10, 0.5f, 0.01, 100));

    private static final int WIDTH = 320;
    private static final int LOG_PREVIEW = 6;

    private CasinoAdmin() {
    }

    public static void open(KubyPlugins plugin, Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins.");
            return;
        }
        p.showDialog(main(plugin));
    }

    private static Dialog main(KubyPlugins plugin) {
        boolean open = plugin.getConfig().getBoolean("casino.open", true);
        List<DialogBody> body = new ArrayList<>();
        body.add(text("<red><b>STATUS</b></red>\n"
                + "<white>Casino: " + (open ? "<green>offen" : "<red>geschlossen") + "\n"
                + "<white>Jackpot: <gold>" + Msg.coins(plugin.casino().jackpot()) + " Coins"
                + " <gray>· Ziehung " + plugin.jackpotDraw().nextDrawLabel() + " · " + plugin.jackpotDraw().participantCount() + " Teilnehmer\n"
                + "<white>Aufbau: " + mark("Kasse", plugin.casino().hasKasse())
                + " " + mark("Blackjack", plugin.casino().tableLocation() != null)
                + " " + mark("Roulette", plugin.roulette().hasTable())
                + " " + mark("Slots", plugin.slots().hasMachine())
                + " " + mark("Crash", plugin.crash().hasMachine())
                + " " + mark("Poker", plugin.poker().hasTable())));

        StringBuilder payout = new StringBuilder("<red><b>AUSZAHLUNG</b></red>");
        Map<String, CasinoLog.Totals> totals = plugin.casinoLog().totals();
        if (totals.isEmpty()) payout.append("\n<gray>Noch keine Runden gespielt.");
        for (Map.Entry<String, CasinoLog.Totals> e : totals.entrySet()) {
            CasinoLog.Totals t = e.getValue();
            long house = t.staked - t.paid;
            payout.append("\n<white>").append(e.getKey()).append(": <gray>")
                    .append(t.rounds).append(" Runden · eingesetzt ").append(t.staked)
                    .append(" · ausgezahlt ").append(t.paid).append(" · Casino ")
                    .append(house >= 0 ? "<green>+" : "<red>").append(house);
        }
        payout.append("\n<white>Jackpots: <gray>").append(plugin.casinoLog().jackpotsPaid())
                .append(" (").append(Msg.coins(plugin.casinoLog().jackpotMoney())).append(" Coins)");
        body.add(text(payout.toString()));

        StringBuilder log = new StringBuilder("<red><b>PROTOKOLL</b></red>");
        List<String> recent = plugin.casinoLog().recent();
        if (recent.isEmpty()) log.append("\n<gray>Noch keine Einträge seit dem Start.");
        for (int i = 0; i < Math.min(LOG_PREVIEW, recent.size()); i++) {
            log.append("\n<gray>").append(Msg.escape(recent.get(i)));
        }
        body.add(text(log.toString()));

        List<DialogInput> inputs = new ArrayList<>();
        for (Setting s : SETTINGS) {
            float value = (float) (plugin.getConfig().getDouble(s.path(), s.def()) * s.scale());
            float initial = Math.max(s.min(), Math.min(s.max(), value));
            inputs.add(DialogInput.numberRange(s.key(), Msg.parse("<white>" + s.label()), s.min(), s.max())
                    .step(s.step())
                    .initial(initial)
                    .width(WIDTH)
                    .build());
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button("<green><b>Speichern</b>", "Werte in die config.yml schreiben", (pl, response) -> {
            for (Setting s : SETTINGS) {
                Float v = response.getFloat(s.key());
                if (v == null) continue;
                if (s.scale() == 1 && s.step() >= 1) {
                    plugin.getConfig().set(s.path(), (long) Math.round(v));
                } else {
                    plugin.getConfig().set(s.path(), v / s.scale());
                }
            }
            plugin.saveConfig();
            Sfx.play(plugin, pl, Sfx.Kind.SUCCESS);
            Msg.send(pl, "<green>Casino-Einstellungen gespeichert.");
            pl.showDialog(main(plugin));
        }));
        buttons.add(button(open ? "<red>Casino schließen" : "<green>Casino öffnen",
                open ? "Keine neuen Runden, Verkaufen geht weiter" : "Spiele wieder freigeben", (pl, response) -> {
                    boolean now = !plugin.getConfig().getBoolean("casino.open", true);
                    plugin.getConfig().set("casino.open", now);
                    plugin.saveConfig();
                    Msg.send(pl, now ? "<green>Das Casino ist wieder offen." : "<red>Das Casino ist geschlossen.");
                    pl.showDialog(main(plugin));
                }));
        buttons.add(command("Kasse setzen", "casino kasse"));
        buttons.add(command("Blackjack-Tisch", "casino tisch"));
        buttons.add(command("Roulette-Tisch", "casino roulette-tisch"));
        buttons.add(command("Slots-Automat", "casino automat"));
        buttons.add(command("Crash-Automat", "casino crash"));
        buttons.add(command("Poker-Tisch", "casino poker-tisch"));
        buttons.add(button("<white>Barkeeper setzen", "Stellt einen Barkeeper an deine Position", (pl, response) -> pl.performCommand("casino barkeeper")));
        buttons.add(button("<white>Protokoll", "Die letzten Einträge", (pl, response) -> pl.showDialog(logDialog(plugin))));
        buttons.add(button("<gold>Jackpot jetzt ziehen", "Zieht sofort unter den aktuellen Teilnehmern", (pl, response) -> {
            plugin.jackpotDraw().drawNow();
            pl.showDialog(main(plugin));
        }));

        DialogBase base = DialogBase.builder(Msg.parse("<red><b>CASINO-ADMIN</b>"))
                .canCloseWithEscape(true)
                .body(body)
                .inputs(inputs)
                .build();
        return Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(buttons).columns(2).build()));
    }

    private static Dialog logDialog(KubyPlugins plugin) {
        StringBuilder sb = new StringBuilder("<red><b>PROTOKOLL</b></red> <gray>(neueste zuerst, vollständig in casino-log.txt)");
        List<String> recent = plugin.casinoLog().recent();
        if (recent.isEmpty()) sb.append("\n<gray>Noch keine Einträge seit dem Start.");
        for (String line : recent) sb.append("\n<gray>").append(Msg.escape(line));
        DialogBase base = DialogBase.builder(Msg.parse("<red><b>CASINO-PROTOKOLL</b>"))
                .canCloseWithEscape(true)
                .body(List.of(text(sb.toString())))
                .build();
        List<ActionButton> buttons = List.of(
                button("<white>< Zurück", "Zum Casino-Admin", (pl, response) -> pl.showDialog(main(plugin))));
        return Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(buttons).columns(1).build()));
    }

    private static DialogBody text(String mini) {
        return DialogBody.plainMessage(Msg.parse(mini), WIDTH);
    }

    private static String mark(String name, boolean set) {
        return (set ? "<green>✔ " : "<red>✘ ") + "<gray>" + name;
    }

    /** Button, der einen Befehl als der klickende Spieler ausführt (der Dialog schließt sich dabei). */
    private static ActionButton command(String label, String cmd) {
        return button("<white>" + label, "Schau auf den Block und klick", (pl, response) -> pl.performCommand(cmd));
    }

    private static ActionButton button(String label, String tooltip, BiConsumer<Player, DialogResponseView> onClick) {
        return ActionButton.create(Msg.parse(label), Msg.parse("<gray>" + tooltip), 150,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl && pl.hasPermission("kuby.admin")) onClick.accept(pl, response);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()));
    }
}
