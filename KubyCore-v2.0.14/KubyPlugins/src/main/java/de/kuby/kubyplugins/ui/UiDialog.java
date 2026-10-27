package de.kuby.kubyplugins.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Zeigt ein Canvas-Menü als Dialog: nur der gezeichnete Körper, keine Knopfleiste (geschlossen wird mit Esc).
 * Klicks lassen den Dialog offen (afterAction NONE); die Aktion öffnet das nächste Menü oder schließt selbst.
 */
public final class UiDialog {

    private UiDialog() {
    }

    /**
     * @param minHeight Mindesthöhe in Pixeln: Menüs einer Familie bekommen dieselbe Höhe, dann steht der Kopf beim
     *                  Blättern immer an derselben Stelle (Minecraft zentriert den Körper senkrecht).
     */
    public static void show(Player player, Canvas canvas, int minHeight) {
        player.showDialog(dialog(player, canvas, minHeight));
    }

    /** Baut den Dialog (ohne ihn zu zeigen); viewer null = ohne Klicks (Selbsttest). */
    public static Dialog dialog(Player player, Canvas canvas, int minHeight) {
        Component body = canvas.build(player);
        int lines = countLines(body);
        int want = (minHeight + 8) / 9;
        if (lines < want) {
            TextComponent.Builder b = Component.text().append(body);
            for (int i = lines; i < want; i++) b.append(Component.newline()).append(Ui.space(canvas.width()));
            body = b.build();
        }
        DialogBase base = DialogBase.builder(canvas.heading())
                .canCloseWithEscape(true)
                // Paper verlangt: ein Dialog, der nach Klicks offen bleibt (NONE), darf das Spiel nicht pausieren
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(body, Math.min(1024, canvas.width() + 64))))
                .build();
        return Dialog.create(b -> b.empty().base(base).type(DialogType.serverLinks(null, 1, 100)));
    }

    public static void show(Player player, Canvas canvas) {
        show(player, canvas, 0);
    }

    /**
     * Canvas-Körper, darunter Eingabefelder und ein Minecraft-Knopf (im Pack golden), z. B. „Anfrage senden“ unter dem
     * Feld „Spielername“. Der Körper wird nicht aufgefüllt, damit die Felder direkt darunter stehen.
     */
    public static Dialog dialog(Player player, Canvas canvas, List<DialogInput> inputs, ActionButton button) {
        DialogBase base = DialogBase.builder(canvas.heading())
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(canvas.build(player), Math.min(1024, canvas.width() + 64))))
                .inputs(inputs)
                .build();
        return Dialog.create(b -> b.empty().base(base).type(DialogType.notice(button)));
    }

    /**
     * Beschriftung für einen Minecraft-Knopf: Grafik aus dem Kit (eine Kachel, bis 16 px hoch) vor dem Text, senkrecht
     * zur Schrift mittig.
     */
    public static Component label(KitData.Sprite icon, String text, net.kyori.adventure.text.format.TextColor color) {
        int shift = -Math.max(0, (icon.height - 7) / 2);
        return Component.text()
                .append(Component.text(String.valueOf(icon.chars.charAt(0)))
                        .font(net.kyori.adventure.key.Key.key("kuby", "k/i" + shift))
                        .shadowColor(net.kyori.adventure.text.format.ShadowColor.none()))
                .append(Component.text(" " + de.kuby.kubyplugins.util.I18n.ctx(text), color)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true))
                .build();
    }

    private static int countLines(Component c) {
        int n = 1;
        if (c instanceof TextComponent t) {
            for (int i = 0; i < t.content().length(); i++) if (t.content().charAt(i) == '\n') n++;
        }
        for (Component child : c.children()) n += countLines(child) - 1;
        return n;
    }
}
