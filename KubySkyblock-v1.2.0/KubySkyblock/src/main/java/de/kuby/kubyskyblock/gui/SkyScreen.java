package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyskyblock.KubySkyblock;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /skyblock als Dialog in Wolken-Weiß und Himmelblau: Fortschritt der Checkliste (Balken, Knopf öffnet die Truhe mit den
 * echten Items) und alle Erfolge als Karten mit Balken und Belohnung (Geld, XP, Juwelen). Ohne Pack: SkyblockMenu.
 */
public final class SkyScreen {

    static final int W = 390;
    static final TextColor SKY = TextColor.color(0x9AD8FF), CLOUD = TextColor.color(0xF2FAFF), MIST = TextColor.color(0x8AA8C0),
            GOLD = TextColor.color(0xF8D35A), GREEN = TextColor.color(0x7FE08A), XP = TextColor.color(0xC8F870),
            JEWEL = TextColor.color(0x5FE3A0), DIM = TextColor.color(0x5E7488), INK = TextColor.color(0x1E3448);

    public record Ach(String name, String desc, long have, long target, boolean done, long money, long xp, long jewels) {}

    public record View(int percent, int collected, int items, List<Ach> achievements, Consumer<Player> checklist) {}

    private final KubyPlugins core;

    public SkyScreen(KubyPlugins core) {
        this.core = core;
    }

    public void open(Player p) {
        if (!core.uiPack().active(p)) {
            new SkyblockMenu(core).open(p);
            return;
        }
        var prog = KubySkyblock.get().progress();
        UUID id = p.getUniqueId();
        var done = prog.achieved(id);
        List<Ach> list = new ArrayList<>();
        for (var a : prog.achievements()) {
            long value = switch (a.type()) {
                case "items" -> prog.collected(id).size();
                case "oneblock" -> prog.counter(id, 1);
                case "loot" -> prog.counter(id, 2);
                default -> prog.counter(id, 0);
            };
            list.add(new Ach(a.name(), a.desc(), Math.min(value, a.target()), a.target(), done.contains(a.id()), a.money(), a.xp(), a.jewels()));
        }
        UiDialog.show(p, canvas(new View(prog.percent(id), prog.collected(id).size(), prog.allItems().size(), list, pl -> {
            pl.closeDialog();
            new ChecklistMenu(core, 0).open(pl);
        })), 330);
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Skyblock", SKY);
        c.box(0, 0, W, 54, KitData.Box.CLOUD);
        c.icon(10, 11, KitData.Icon.GRASS_BLOCK, true);
        c.headline(50, 12, "Skyblock", SKY);
        c.text(50, 36, "Deine Insel, deine Sammlung", MIST, false);
        int done = (int) v.achievements().stream().filter(Ach::done).count();
        c.textRight(378, 12, done + " / " + v.achievements().size(), CLOUD, true);
        c.textRight(378, 24, Canvas.caps("Erfolge"), MIST, false);
        int y = 60;
        // Checkliste
        c.box(0, y, W, 40, KitData.Box.CLOUD_CARD);
        c.icon(8, y + 12, KitData.Icon.BOOK_PLAIN, false);
        c.text(30, y + 7, "Checkliste · " + v.percent() + " %", CLOUD, true);
        c.text(30, y + 19, v.collected() + " von " + v.items() + " Items gesammelt", MIST, false);
        c.bar(30, y + 29, 230, v.items() == 0 ? 0 : v.collected() / (double) v.items(), KitData.Fill.CYAN);
        c.smallButton(W - 86, y + 12, 78, KitData.Small.DARK, "Ansehen", CLOUD, false);
        c.action(W - 86, y + 12, 78, KitData.SMALL_H, v.checklist(), "Alle Items der Checkliste");
        y += 40 + 8;
        int cw = (W - 4) / 2, ch = 54;
        for (int i = 0; i < v.achievements().size(); i++) {
            Ach a = v.achievements().get(i);
            int x = (i % 2) * (cw + 4), ay = y + (i / 2) * (ch + 4);
            c.box(x, ay, cw, ch, a.done() ? KitData.Box.GOLD : KitData.Box.CLOUD_CARD);
            c.icon(x + 7, ay + 7, a.done() ? KitData.Icon.STAR : KitData.Icon.FEATHER, false,
                    a.done() ? NamedTextColor.WHITE : TextColor.color(0x9AB0C4));
            c.text(x + 28, ay + 7, Canvas.fit(a.name(), true, cw - 36), a.done() ? GOLD : CLOUD, true);
            c.text(x + 28, ay + 18, Canvas.fit(a.desc(), false, cw - 36), MIST, false);
            double f = a.target() <= 0 ? 1 : a.have() / (double) a.target();
            c.bar(x + 8, ay + 30, cw - 16 - 66, f, a.done() ? KitData.Fill.GREEN : KitData.Fill.CYAN);
            c.textRight(x + cw - 8, ay + 30, a.done() ? "✔" : Msg.coins(a.have()) + "/" + Msg.coins(a.target()), a.done() ? GREEN : CLOUD, false);
            int rx = x + 8;
            if (a.money() > 0) rx += c.text(rx, ay + 41, Msg.coins(a.money()) + "$", a.done() ? DIM : GOLD, false) + 6;
            if (a.xp() > 0) rx += c.text(rx, ay + 41, Msg.coins(a.xp()) + " XP", a.done() ? DIM : XP, false) + 6;
            if (a.jewels() > 0) c.text(rx, ay + 41, a.jewels() + " Juwelen", a.done() ? DIM : JEWEL, false);
        }
        if (v.achievements().isEmpty()) c.text(4, y, "Noch keine Erfolge eingerichtet.", MIST, false);
        return c;
    }

    /** Selbsttest: viele Erfolge mit langen Texten; leer. */
    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        List<Ach> list = new ArrayList<>();
        for (int i = 0; i < 30; i++) list.add(new Ach("W".repeat(30), "W".repeat(60), 999_999_999L, 999_999_999L, i % 2 == 0,
                999_999_999L, 999_999_999L, 99_999));
        reg.accept("Skyblock", () -> canvas(new View(100, 9999, 9999, list, no)));
        reg.accept("Skyblock leer", () -> canvas(new View(0, 0, 0, List.of(), no)));
    }
}
