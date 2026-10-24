package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BiConsumer;

/** Eingabe eines Betrags (Chips oder Coins) als kleiner Dialog mit Textfeld. */
public final class CasinoInput {

    private CasinoInput() {
    }

    /**
     * @param onValue bekommt den gültigen Betrag (min..max); bei ungültiger Eingabe kommt eine Meldung
     * @param onCancel nach "Abbrechen" (z. B. Menü wieder öffnen), darf null sein
     */
    public static void amount(Player p, String title, List<String> lines, long min, long max, String confirm,
                              BiConsumer<Player, Long> onValue, java.util.function.Consumer<Player> onCancel) {
        StringBuilder text = new StringBuilder();
        for (String l : lines) {
            if (!text.isEmpty()) text.append('\n');
            text.append(l);
        }
        DialogBase base = DialogBase.builder(Msg.parse(CasinoText.title("#F2B829", title)))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Msg.parse(text.toString()), 260)))
                .inputs(List.of(DialogInput.text("betrag", Msg.parse("<gray>Betrag"))
                        .width(160).maxLength(10).initial("").build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton yes = ActionButton.create(Msg.parse("<green><b>" + confirm + "</b>"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player pl)) return;
                    String raw = response.getText("betrag");
                    long v = parse(raw);
                    if (v < min || v > max) {
                        CasinoText.error(pl, v < 0 ? "Bitte eine Zahl eingeben." : "Erlaubt sind " + CasinoText.num(min)
                                + " bis " + CasinoText.num(max) + ".");
                        if (onCancel != null) onCancel.accept(pl);
                        return;
                    }
                    onValue.accept(pl, v);
                }, once));
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl && onCancel != null) onCancel.accept(pl);
                }, once));
        p.showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(yes, no))));
    }

    /** "1.000", "1000", "1k", "2,5k" -> Zahl; -1 bei ungültiger Eingabe. */
    public static long parse(String raw) {
        if (raw == null) return -1;
        String s = raw.trim().toLowerCase().replace(" ", "");
        if (s.isEmpty()) return -1;
        long factor = 1;
        if (s.endsWith("k")) {
            factor = 1000;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            factor = 1_000_000;
            s = s.substring(0, s.length() - 1);
        }
        try {
            if (factor > 1) {
                double d = Double.parseDouble(s.replace(".", "").replace(',', '.'));
                return d < 0 ? -1 : Math.round(d * factor);
            }
            long v = Long.parseLong(s.replace(".", "").replace(",", ""));
            return v < 0 ? -1 : v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
