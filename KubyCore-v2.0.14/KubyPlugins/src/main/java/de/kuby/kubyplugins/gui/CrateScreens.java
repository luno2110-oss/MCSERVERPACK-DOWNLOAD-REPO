package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CosmeticService;
import de.kuby.kubyplugins.service.CrateService;
import de.kuby.kubyplugins.service.CrateService.Crate;
import de.kuby.kubyplugins.service.CrateService.Reward;
import de.kuby.kubyplugins.service.KeyService;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.Ui;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Kisten-Menüs auf dem Dialog-Kit: Inhalt einer Kiste mit Chancen (Linksklick auf die Kiste) und /keys mit allen
 * Schlüsseln des Modus. Ohne Pack gibt es den Inhalt als Chat-Liste.
 */
public final class CrateScreens {

    private static final int W = 390, HEIGHT = 330;
    private static final TextColor ACCENT = Ui.Theme.ORANGE.accent, GRAY = TextColor.color(0xA0A0AC),
            LIGHT = TextColor.color(0xE4E4EA), WHITE = TextColor.color(0xF4F4F4), DIM = TextColor.color(0x80808A),
            ON = TextColor.color(0x201400), NAV = TextColor.color(0xC0C4CB);

    /** Eine Zeile im Inhalt: Icon, Name, Chance in Prozent. */
    public record Line(KitData.Icon icon, String name, double chance) {}

    public record PreviewView(String name, TextColor color, List<Line> lines, int keys, boolean atCrate) {}

    /** Eine Kiste in /keys: Schlüsselname, Farbe, Anzahl. */
    public record KeyRow(String crateId, String keyName, TextColor color, int count) {}

    public record KeysView(String modeLabel, List<KeyRow> rows) {}

    private final KubyPlugins plugin;

    public CrateScreens(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private static TextColor hex(String c) {
        TextColor t = TextColor.fromHexString(c);
        return t == null ? TextColor.color(0xF8D35A) : t;
    }

    private KitData.Icon iconOf(Reward r) {
        return switch (r.kind()) {
            case "coins" -> KitData.Icon.GOLD_NUGGET;
            case "tokens" -> KitData.Icon.AMETHYST;
            case "jewels" -> KitData.Icon.EMERALD;
            case "key" -> KitData.Icon.TRIAL_KEY;
            case "pet" -> KitData.Icon.LEAD;
            case "furniture" -> KitData.Icon.OAK_DOOR;
            case "cosmetic" -> {
                CosmeticService.Item it = plugin.cosmetics().find(r.value());
                yield it == null ? KitData.Icon.STAR : CosmeticsScreen.iconOf(it);
            }
            case "item" -> {
                try {
                    yield KitData.Icon.valueOf(r.value().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    yield KitData.Icon.BUNDLE;
                }
            }
            default -> KitData.Icon.BUNDLE;
        };
    }

    // =================================================================================================================
    // Inhalt
    // =================================================================================================================

    public void preview(Player p, Crate c, boolean atCrate) {
        KeyService.KeyType t = plugin.keys().type(c.key());
        int keys = plugin.keys().count(p.getUniqueId(), c.mode(), c.key());
        if (!plugin.uiPack().active(p)) {
            Msg.send(p, "<#F0A050><b>" + Msg.escape(c.name()) + "</b> <dark_gray>▶ <gray>Du hast " + keys + " Schlüssel. Inhalt:");
            for (Reward r : c.rewards()) {
                Msg.send(p, "<dark_gray>• <" + CrateService.colorOf(c.chance(r)).asHexString() + ">" + Msg.escape(r.name())
                        + " <dark_gray>" + String.format(Locale.GERMANY, "%.1f", c.chance(r)) + " %");
            }
            return;
        }
        List<Line> lines = new ArrayList<>();
        for (Reward r : c.rewards()) lines.add(new Line(iconOf(r), r.name(), c.chance(r)));
        lines.sort((a, b) -> Double.compare(b.chance(), a.chance()));
        PreviewView v = new PreviewView(c.name(), t == null ? TextColor.color(0xF8D35A) : hex(t.color()), lines, keys, atCrate);
        UiDialog.show(p, preview(v, pl -> {
            pl.closeDialog();
            plugin.crates().openFromPreview(pl, c);
        }, this::keys), HEIGHT);
    }

    public static Canvas preview(PreviewView v, Consumer<Player> open, Consumer<Player> keys) {
        Canvas c = new Canvas(W);
        c.box(0, 0, W, 51, KitData.Box.GOLD);
        c.smallIcon(13, 16, KitData.SmallIcon.H_CHEST);
        c.text(41, 15, v.name().toUpperCase(Locale.ROOT), v.color(), true);
        c.text(41, 28, "Das kann drin sein, mit Chance", GRAY, false);
        String kt = v.keys() + " Schlüssel";
        int kw = Canvas.width(kt, true);
        c.icon(378 - kw - 19, 17, KitData.Icon.TRIAL_KEY, false);
        c.textRight(378, 21, List.of(Span.bold(kt, v.keys() > 0 ? WHITE : GRAY)));
        int y = 53;
        int rows = Math.min(13, (v.lines().size() + 1) / 2);
        int h = 22 + 18 * Math.max(1, rows) + 2;
        c.box(0, y, W, h, KitData.Box.GOLD);
        c.text(9, y + 9, "INHALT", ACCENT, true);
        for (int i = 0; i < v.lines().size() && i < 26; i++) {
            Line l = v.lines().get(i);
            int col = i / rows, row = i % rows;
            int x = 8 + col * 190, ly = y + 19 + row * 18;
            c.icon(x, ly, l.icon(), false);
            String pct = String.format(Locale.GERMANY, l.chance() < 1 ? "%.2f %%" : "%.1f %%", l.chance());
            int pw = Canvas.width(pct, false);
            c.text(x + 19, ly + 4, Canvas.fit(l.name(), true, 180 - 19 - pw - 6), CrateService.colorOf(l.chance()), true);
            c.textRight(x + 180, ly + 4, pct, DIM, false);
        }
        y += h + 3;
        int bx = 0;
        if (v.atCrate()) {
            boolean can = v.keys() > 0;
            String label = can ? "Öffnen" : "Kein Schlüssel";
            int w = Canvas.width(label, can) - 1 + (can ? 24 : 18);
            c.button(0, y, w, can ? KitData.Button.H_GOLD : KitData.Button.H_NAV, label, can ? ON : DIM, can);
            if (can) c.action(0, y, w, KitData.BTN_H, open, "Kiste mit einem Schlüssel öffnen (Schleichen: ohne Animation)");
            bx = w + 4;
        }
        String kl = "Deine Schlüssel";
        int kw2 = Canvas.width(kl, false) - 1 + 18;
        c.button(bx, y, kw2, KitData.Button.H_NAV, kl, NAV, false);
        c.action(bx, y, kw2, KitData.BTN_H, keys, "Alle deine Schlüssel (/keys)");
        c.text(4, y + 28, "Rechtsklick öffnet, Schleichen + Rechtsklick ohne Animation.", DIM, false);
        return c;
    }

    // =================================================================================================================
    // /keys
    // =================================================================================================================

    public void keys(Player p) {
        String mode = plugin.modes().modeOf(p.getUniqueId());
        List<KeyRow> rows = new ArrayList<>();
        List<Crate> crates = plugin.crates().crates(mode);
        for (Crate cr : crates) {
            KeyService.KeyType t = plugin.keys().type(cr.key());
            rows.add(new KeyRow(cr.id(), t == null ? cr.key() : t.full(), t == null ? TextColor.color(0xF8D35A) : hex(t.color()),
                    plugin.keys().count(p.getUniqueId(), mode, cr.key())));
        }
        if (!plugin.uiPack().active(p)) {
            p.performCommand("keys liste");
            return;
        }
        UiDialog.show(p, keys(new KeysView(ModeService.label(mode), rows), id -> pl -> {
            Crate cr = plugin.crates().crate(plugin.modes().modeOf(pl.getUniqueId()), id);
            if (cr != null) preview(pl, cr, false);
        }, pl -> {
            pl.closeDialog();
            pl.performCommand("spawn");
        }), HEIGHT);
    }

    public static Canvas keys(KeysView v, java.util.function.Function<String, Consumer<Player>> show, Consumer<Player> spawn) {
        Canvas c = new Canvas(W);
        c.box(0, 0, W, 51, KitData.Box.GOLD);
        c.smallIcon(13, 16, KitData.SmallIcon.H_CHEST);
        c.text(41, 15, "SCHLÜSSEL", Ui.Theme.ORANGE.title, true);
        c.text(41, 28, "Deine Kisten-Schlüssel in " + v.modeLabel(), GRAY, false);
        int total = 0;
        for (KeyRow r : v.rows()) total += r.count();
        c.textRight(378, 21, List.of(Span.bold(total + " insgesamt", total > 0 ? WHITE : GRAY)));
        int y = 53;
        int h = 22 + 22 * Math.max(1, v.rows().size()) + 2;
        c.box(0, y, W, h, KitData.Box.GOLD);
        c.text(9, y + 9, "KISTEN AM SPAWN", ACCENT, true);
        if (v.rows().isEmpty()) c.text(12, y + 23, "In diesem Modus gibt es keine Kisten.", LIGHT, false);
        for (int i = 0; i < v.rows().size(); i++) {
            KeyRow r = v.rows().get(i);
            int ry = y + 19 + 22 * i;
            c.icon(10, ry + 1, KitData.Icon.TRIAL_KEY, false);
            c.text(30, ry + 5, r.keyName(), r.color(), true);
            String n = r.count() + "×";
            c.text(200, ry + 5, n, r.count() > 0 ? WHITE : DIM, true);
            String l = "Inhalt ›";
            int w = Canvas.width(l, true) + 14;
            c.smallButton(380 - w, ry + 1, w, KitData.Small.DARK, l, LIGHT, true);
            c.action(380 - w, ry + 1, w, KitData.SMALL_H, show.apply(r.crateId()), "Was in der Kiste sein kann");
        }
        y += h + 3;
        String label = "Zu den Kisten";
        int w = Canvas.width(label, true) - 1 + 24;
        c.button(0, y, w, KitData.Button.H_GOLD, label, ON, true);
        c.action(0, y, w, KitData.BTN_H, spawn, "Zum Spawn teleportieren");
        c.text(4, y + 28, "Schlüssel gibt es beim Voten, für Spielzeit, bei Events und im Juwelenshop.", DIM, false);
        return c;
    }
}
