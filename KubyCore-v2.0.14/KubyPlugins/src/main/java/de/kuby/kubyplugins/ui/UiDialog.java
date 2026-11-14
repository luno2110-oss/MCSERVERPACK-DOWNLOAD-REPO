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

    // ---------- Klick-Ablauf: Mauszeiger behalten, Klick-Ton ----------
    // Schließt eine Menü-Aktion erst den Dialog und öffnet dann das nächste Menü, schnappt Minecraft dazwischen die
    // Maus ein und setzt sie beim neuen Fenster in die Mitte. Darum wird „schließen“ während eines Menü-Klicks nur
    // vorgemerkt: öffnet die Aktion ein neues Menü (oder eine Truhe), entfällt es, sonst wird am Ende geschlossen.

    private static java.util.UUID clickPlayer;
    private static boolean closeRequested, shown, sounded;

    /** Zähler je Spieler: steigt bei jedem gezeigten oder geschlossenen Dialog (für Menüs, die sich selbst aktualisieren:
     *  hat sich der Zähler seitdem geändert, ist ein anderes Fenster offen oder der Dialog zu). */
    private static final java.util.Map<java.util.UUID, Long> GENERATION = new java.util.concurrent.ConcurrentHashMap<>();

    public static long generation(Player p) {
        return GENERATION.getOrDefault(p.getUniqueId(), 0L);
    }

    private static void bump(Player p) {
        GENERATION.merge(p.getUniqueId(), 1L, Long::sum);
    }

    public static void forget(java.util.UUID id) {
        GENERATION.remove(id);
    }

    /** Vom Klick-Handler: eine Menü-Aktion dieses Spielers beginnt. */
    static void beginClick(Player p) {
        clickPlayer = p.getUniqueId();
        closeRequested = false;
        shown = false;
        sounded = false;
    }

    /** Vom Klick-Handler: Aktion fertig. Gibt zurück, ob schon ein Ton lief (sonst spielt der Handler den Klick). */
    static boolean endClick(Player p) {
        boolean s = sounded;
        boolean close = closeRequested && !shown;
        clickPlayer = null;
        closeRequested = false;
        shown = false;
        sounded = false;
        if (close && p.isOnline()) {
            // eine Truhe (oder ein anderes Fenster) hat den Dialog schon ersetzt: dann nicht schließen
            org.bukkit.event.inventory.InventoryType top = p.getOpenInventory().getTopInventory().getType();
            if (top == org.bukkit.event.inventory.InventoryType.CRAFTING || top == org.bukkit.event.inventory.InventoryType.CREATIVE) {
                bump(p);
                p.closeDialog();
            }
        }
        return s;
    }

    private static boolean inClick(Player p) {
        return clickPlayer != null && p != null && clickPlayer.equals(p.getUniqueId());
    }

    /** Dialog schließen (in einer Menü-Aktion erst am Ende und nur, wenn kein neues Menü kommt). */
    public static void close(Player p) {
        if (inClick(p)) {
            closeRequested = true;
            return;
        }
        bump(p);
        p.closeDialog();
    }

    /** Dialog zeigen (merkt sich in einer Menü-Aktion, dass ein neues Menü kommt). */
    public static void open(Player p, Dialog dialog) {
        Canvas src = CANVAS.get(dialog);
        if (src != null && bedrockShow(p, src, null)) return;
        if (inClick(p)) shown = true;
        bump(p);
        p.showDialog(dialog);
    }

    // ---------- Bedrock: Canvas-Menüs als native Formulare (de.kuby.kubyplugins.bedrock) ----------

    /** Canvas hinter einem gebauten Dialog (für open(): Bedrock-Spieler bekommen das Formular statt des Dialogs). */
    private static final java.util.Map<Dialog, Canvas> CANVAS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Bedrock-Spieler: Formular senden. true = erledigt (kein Java-Dialog). */
    private static boolean bedrockShow(Player p, Canvas canvas, java.util.function.Consumer<Player> onClose) {
        if (p == null || !de.kuby.kubyplugins.bedrock.Bedrock.forms(p)) return false;
        boolean click = inClick(p);
        if (!de.kuby.kubyplugins.bedrock.Bedrock.show(p, canvas, onClose, click)) return false;
        if (click) shown = true;
        bump(p);
        return true;
    }

    /**
     * Bedrock-Spieler, Dialog mit Eingabefeldern: derselbe Dialog mit lesbarem Text statt der Java-Grafik (Geyser macht
     * daraus ein Bedrock-Formular mit denselben Feldern und Knöpfen); klickbare Stellen des Canvas werden zusätzliche Knöpfe.
     */
    private static Dialog bedrockInputs(Player p, Canvas canvas, List<DialogInput> inputs, List<ActionButton> buttons) {
        de.kuby.kubyplugins.bedrock.FormModel.Model m = de.kuby.kubyplugins.bedrock.FormModel.of(canvas);
        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer leg =
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        List<ActionButton> all = new java.util.ArrayList<>(buttons);
        net.kyori.adventure.text.event.ClickCallback.Options opts = net.kyori.adventure.text.event.ClickCallback.Options.builder()
                .uses(1).lifetime(java.time.Duration.ofMinutes(10)).build();
        for (de.kuby.kubyplugins.bedrock.FormModel.Button b : m.buttons()) {
            if (b.action() == null) continue;
            java.util.function.Consumer<Player> act = b.action();
            all.add(ActionButton.create(leg.deserialize(b.text().replace("\n", " ")), null, 200,
                    io.papermc.paper.registry.data.dialog.action.DialogAction.customClick((response, audience) -> {
                        if (audience instanceof Player pl) UiClicks.runAction(pl, act);
                    }, opts)));
        }
        DialogBase base = DialogBase.builder(Component.text(m.title()))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(m.content().isEmpty() ? List.of() : List.of(DialogBody.plainMessage(leg.deserialize(m.content()), 300)))
                .inputs(inputs)
                .build();
        if (all.size() == 1) return Dialog.create(b -> b.empty().base(base).type(DialogType.notice(all.get(0))));
        return Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(all, null, 1)));
    }

    /**
     * Wie show, aber mit Knopf „Schließen“ unten: Esc löst ihn ebenfalls aus, onExit erfährt also, dass der Spieler das
     * Menü zugemacht hat (für Menüs, die sich selbst aktualisieren).
     */
    public static void showWithExit(Player player, Canvas canvas, int minHeight, java.util.function.Consumer<Player> onExit) {
        if (bedrockShow(player, canvas, onExit)) return;
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
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(body, Math.min(1024, canvas.width() + 64))))
                .build();
        net.kyori.adventure.text.event.ClickCallback.Options opts = net.kyori.adventure.text.event.ClickCallback.Options.builder()
                .uses(1).lifetime(java.time.Duration.ofMinutes(5)).build();
        ActionButton exit = ActionButton.create(Component.text("Schließen", net.kyori.adventure.text.format.NamedTextColor.GRAY), null, 120,
                io.papermc.paper.registry.data.dialog.action.DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) onExit.accept(pl);
                }, opts));
        open(player, Dialog.create(b -> b.empty().base(base).type(DialogType.serverLinks(exit, 1, 120))));
    }

    /** Von Sfx: in dieser Menü-Aktion lief schon ein Ton. */
    public static void soundPlayed(Player p) {
        if (inClick(p)) sounded = true;
    }

    /**
     * @param minHeight Mindesthöhe in Pixeln: Menüs einer Familie bekommen dieselbe Höhe, dann steht der Kopf beim
     *                  Blättern immer an derselben Stelle (Minecraft zentriert den Körper senkrecht).
     */
    public static void show(Player player, Canvas canvas, int minHeight) {
        if (bedrockShow(player, canvas, null)) return;
        open(player, dialog(player, canvas, minHeight));
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
        Dialog d = Dialog.create(b -> b.empty().base(base).type(DialogType.serverLinks(null, 1, 100)));
        CANVAS.put(d, canvas);
        return d;
    }

    public static void show(Player player, Canvas canvas) {
        show(player, canvas, 0);
    }

    /**
     * Canvas-Körper, darunter Eingabefelder und ein Minecraft-Knopf (im Pack golden), z. B. „Anfrage senden“ unter dem
     * Feld „Spielername“. Der Körper wird nicht aufgefüllt, damit die Felder direkt darunter stehen.
     */
    public static Dialog dialog(Player player, Canvas canvas, List<DialogInput> inputs, ActionButton button) {
        if (player != null && de.kuby.kubyplugins.bedrock.Bedrock.forms(player)) return bedrockInputs(player, canvas, inputs, List.of(button));
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
     * Canvas-Körper, darunter Eingabefelder und mehrere Minecraft-Knöpfe nebeneinander (columns je Zeile, 2 px Abstand),
     * z. B. „Abschicken“ und „Zurück“ unter „Neues Ticket“.
     */
    public static Dialog dialog(Player player, Canvas canvas, List<DialogInput> inputs, List<ActionButton> buttons, int columns) {
        if (player != null && de.kuby.kubyplugins.bedrock.Bedrock.forms(player)) return bedrockInputs(player, canvas, inputs, buttons);
        DialogBase base = DialogBase.builder(canvas.heading())
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(canvas.build(player), Math.min(1024, canvas.width() + 64))))
                .inputs(inputs)
                .build();
        return Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(buttons, null, columns)));
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

    /**
     * Beschriftung mit farbiger Fläche dahinter wie „Abschicken“ (lila) und „Zurück“ (grau) unter „Neues Ticket“: die
     * Fläche (eine Kachelreihe aus dem Kit, bis 16 px hoch) liegt hinter Grafik und Text, beides mittig darauf, der Text
     * fett und ohne Schatten. Der Knopf muss plate.width + 4 breit sein: dann füllt die Fläche ihn bis auf den Rand, und
     * Minecraft lässt die Beschriftung nicht laufen. icon darf null sein.
     */
    public static Component plate(KitData.Sprite plate, KitData.Sprite icon, String text,
                                  net.kyori.adventure.text.format.TextColor color) {
        TextComponent.Builder b = Component.text();
        int x = 0;
        for (int i = 0; i < plate.cols; i++) {
            int adv = plate.advances.charAt(i) - '0';
            b.append(tile(plate.chars.charAt(i), plate.height));
            x += adv;
            if (i + 1 < plate.cols) {
                b.append(Ui.space(32 - adv));
                x += 32 - adv;
            }
        }
        String t = (icon == null ? "" : " ") + de.kuby.kubyplugins.util.I18n.ctx(text);
        int content = (icon == null ? 0 : icon.width + 1) + Canvas.width(t, true);
        int left = (plate.width - content + 1) / 2;
        b.append(Ui.space(left - x));
        if (icon != null) b.append(tile(icon.chars.charAt(0), icon.height));
        b.append(Component.text(t, color).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)
                .shadowColor(net.kyori.adventure.text.format.ShadowColor.none()));
        b.append(Ui.space(plate.width - left - content));
        return b.build();
    }

    /** Eine Kachel aus dem Kit, senkrecht mittig zur Schrift (wie die Grafik in label). */
    private static Component tile(char c, int height) {
        return Component.text(String.valueOf(c))
                .font(net.kyori.adventure.key.Key.key("kuby", "k/i" + -Math.max(0, (height - 7) / 2)))
                .shadowColor(net.kyori.adventure.text.format.ShadowColor.none());
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
