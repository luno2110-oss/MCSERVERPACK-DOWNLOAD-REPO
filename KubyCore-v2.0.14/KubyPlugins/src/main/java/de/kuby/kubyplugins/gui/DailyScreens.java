package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.DailyService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Consumer;

/**
 * Tageskalender (/daily) als Abreißkalender: 30 Tage auf Papier-Feldern (abgeholt mit Haken, heute golden, jeder
 * zehnte Tag mit Juwelen-Bonus), darunter ein großer Abhol-Knopf. Tagespot (/dailypot) als Lotterie in Kleeblatt-Grün:
 * Topf, eigene Lose und Gewinnchance, Ziehung mit Countdown, Lose kaufen (1, 5, 20). Ohne Pack: DailyMenu, DailyPotMenu.
 */
public final class DailyScreens {

    static final int W = 390;
    static final TextColor SKY = TextColor.color(0x8AD8FF), ICE = TextColor.color(0xD8F0FF), MIST = TextColor.color(0x8AA8C0),
            INK = TextColor.color(0x3A2C18), INK_LIGHT = TextColor.color(0x8A7650), GOLD = TextColor.color(0xF8D35A),
            GREEN = TextColor.color(0x7FE08A), JEWEL = TextColor.color(0x5FE3A0), WHITE = TextColor.color(0xF4F4F4),
            DARK = TextColor.color(0x10180A), CLOVER = TextColor.color(0x8BE88F), PALE = TextColor.color(0xA8C8A8),
            COINS = TextColor.color(0xF8D35A);

    /** Kalender: abgeholte Tage, heute abholbar, Belohnung je Tag (Index 0 = Tag 1), Klick. */
    public record Calendar(int done, boolean canClaim, long[] rewards, Consumer<Player> claim) {}

    /** Tagespot: Topf, eigene Lose, alle Lose, Preis je Los, höchstens je Tag, Stunde der Ziehung, Minuten bis dahin, Kauf. */
    public record Pot(long pot, int mine, long all, long price, int max, int hour, long minutes,
                      java.util.function.IntFunction<Consumer<Player>> buy) {}

    private final KubyPlugins plugin;

    public DailyScreens(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    // =================================================================================================================
    // Steuerung
    // =================================================================================================================

    public void calendar(Player p) {
        if (!plugin.uiPack().active(p)) {
            new DailyMenu(plugin).open(p);
            return;
        }
        DailyService d = plugin.daily();
        long[] rewards = new long[DailyService.CYCLE];
        for (int i = 0; i < rewards.length; i++) rewards[i] = d.rewardFor(i + 1);
        UiDialog.show(p, calendar(new Calendar(d.claimedDays(p.getUniqueId()), d.canClaim(p.getUniqueId()), rewards, pl -> {
            DailyMenu.doClaim(plugin, pl);
            calendar(pl);
        })), 330);
    }

    public void pot(Player p) {
        if (!plugin.uiPack().active(p)) {
            new DailyPotMenu(plugin).open(p);
            return;
        }
        var cfg = plugin.getConfig();
        long price = cfg.getLong("dailypot.ticket-price", 200);
        int hour = cfg.getInt("dailypot.draw-hour", 19);
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
        ZonedDateTime draw = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        if (!draw.isAfter(now)) draw = draw.plusDays(1);
        long pot = plugin.dailyPot().pot();
        UiDialog.show(p, pot(new Pot(pot, plugin.dailyPot().ticketsOf(p.getUniqueId()), price > 0 ? pot / price : 0, price,
                cfg.getInt("dailypot.max-tickets", 50), hour, Duration.between(now, draw).toMinutes(), n -> pl -> {
            String err = plugin.dailyPot().buy(pl.getUniqueId(), n);
            Msg.send(pl, err == null ? "<green>" + n + (n == 1 ? " Los" : " Lose") + " gekauft. Viel Glück!" : "<red>" + err);
            Sfx.play(plugin, pl, err == null ? Sfx.Kind.COIN : Sfx.Kind.ERROR);
            pot(pl);
        })), 300);
    }

    // =================================================================================================================
    // Darstellung
    // =================================================================================================================

    public static Canvas calendar(Calendar v) {
        Canvas c = new Canvas(W).heading("Tageskalender", SKY);
        int n = v.rewards().length;
        c.box(0, 0, W, 54, KitData.Box.SKY);
        c.icon(10, 11, KitData.Icon.CLOCK, true);
        c.title(50, 12, "Täglich", false, SKY);
        c.text(50, 36, "Einmal am Tag abholen · jeder 10. Tag mit Juwelen", MIST, false);
        c.textRight(378, 12, v.done() + " / " + n, ICE, true);
        c.textRight(378, 24, Canvas.caps("Tage"), MIST, false);
        c.bar(318, 36, 60, n == 0 ? 0 : v.done() / (double) n, KitData.Fill.CYAN);

        // 30 Felder in 6 Spalten
        int cols = 6, cw = (W - (cols - 1) * 3) / cols, ch = 40;
        int y = 60;
        int today = v.canClaim() ? v.done() + 1 : -1;
        for (int i = 0; i < n; i++) {
            int day = i + 1;
            int x = (i % cols) * (cw + 3), dy = y + (i / cols) * (ch + 3);
            boolean done = day <= v.done(), now = day == today, bonus = day % 10 == 0;
            KitData.Box box = now ? KitData.Box.GOLD : done ? KitData.Box.SKY : KitData.Box.PAPER;
            c.box(x, dy, cw, ch, box);
            TextColor head = done ? MIST : now ? GOLD : INK_LIGHT;
            String label = Canvas.caps("Tag") + " " + day;
            if (box == KitData.Box.PAPER) c.textFlat(x + 7, dy + 7, label, head, false);
            else c.text(x + 7, dy + 7, label, head, false);
            String reward = Msg.coins(v.rewards()[i]) + "$";
            if (done) {
                c.text(x + 7, dy + 20, Canvas.fit(reward, true, cw - 14), MIST, true);
                c.textRight(x + cw - 7, dy + 7, "✔", GREEN, true);
            } else if (now) {
                c.text(x + 7, dy + 20, Canvas.fit(reward, true, cw - 14), GOLD, true);
                c.action(x, dy, cw, ch, v.claim(), "Tag " + day + " abholen");
            } else {
                c.textFlat(x + 7, dy + 20, Canvas.fit(reward, true, cw - 14), INK, true);
            }
            if (bonus) c.icon(x + cw - 20, dy + 20, KitData.Icon.EMERALD, false);
        }
        y += ((n + cols - 1) / cols) * (ch + 3) + 5;
        if (v.canClaim()) {
            c.button(0, y, W, KitData.Button.GREEN);
            String t = "Heute abholen: Tag " + today + " · " + Msg.coins(v.rewards()[today - 1]) + "$";
            int gw = 16 + 4 + Canvas.width(t, true);
            c.icon((W - gw) / 2, y + 3, KitData.Icon.GOLD_INGOT, false);
            c.textFlat((W - gw) / 2 + 20, y + 7, t, DARK, true);
            c.action(0, y, W, 22, v.claim(), t);
        } else {
            c.button(0, y, W, KitData.Button.DARK);
            c.textCenter(W / 2, y + 7, v.done() >= n ? "Alle Tage geschafft!" : "Heute schon abgeholt · morgen wieder", MIST, true);
        }
        return c;
    }

    public static Canvas pot(Pot v) {
        Canvas c = new Canvas(W).heading("Tagespot", CLOVER);
        c.box(0, 0, W, 54, KitData.Box.CLOVER);
        c.icon(10, 11, KitData.Icon.GOLD_BLOCK, true);
        c.title(50, 12, "Tagespot", false, CLOVER);
        c.text(50, 36, "Lose kaufen, um " + v.hour() + ":00 Uhr gewinnt ein Los den ganzen Topf", PALE, false);

        // großer Topf
        int y = 60;
        c.box(0, y, W, 58, KitData.Box.GOLD);
        c.text(12, y + 9, Canvas.caps("Im Topf"), GOLD, false);
        String pot = Msg.coins(v.pot()) + "$";
        int tw = Canvas.titleWidth(pot);
        c.title(Math.max(12, (W - tw) / 2), y + 24, pot, true, null);
        long h = v.minutes() / 60, m = v.minutes() % 60;
        c.textRight(378, y + 9, "Ziehung in " + (h > 0 ? h + " Std. " : "") + m + " Min.", GOLD, false);
        y += 58 + 6;

        // eigene Lose
        int fw = (W - 6) / 3;
        stat(c, 0, y, fw, KitData.Icon.PAPER, "Deine Lose", v.mine() + " / " + v.max());
        double chance = v.all() <= 0 ? 0 : v.mine() * 100.0 / v.all();
        stat(c, fw + 3, y, fw, KitData.Icon.RABBIT_FOOT, "Chance", v.mine() == 0 ? "–"
                : String.format(java.util.Locale.GERMANY, chance >= 10 ? "%.0f %%" : "%.1f %%", chance));
        stat(c, 2 * (fw + 3), y, fw, KitData.Icon.SUNFLOWER, "Lose gesamt", Msg.coins(v.all()));
        y += 40 + 8;

        // kaufen
        int[] amounts = {1, 5, 20};
        int bw = (W - 6) / 3;
        for (int i = 0; i < amounts.length; i++) {
            int n = amounts[i];
            int x = i * (bw + 3);
            boolean ok = v.mine() + n <= v.max();
            c.box(x, y, bw, 50, ok ? KitData.Box.CLOVER : KitData.Box.GRAY);
            c.text(x + 10, y + 9, n + (n == 1 ? " Los" : " Lose"), ok ? WHITE : MIST, true);
            c.text(x + 10, y + 21, Msg.coins(v.price() * n) + "$", ok ? COINS : MIST, true);
            c.smallButton(x + 8, y + 31, bw - 16, ok ? KitData.Small.GREEN : KitData.Small.DARK, ok ? "Kaufen" : "Limit erreicht",
                    ok ? WHITE : MIST, false);
            if (ok) c.action(x, y, bw, 50, v.buy().apply(n), n + " Lose für " + Msg.coins(v.price() * n) + "$ kaufen");
        }
        y += 50 + 6;
        c.text(4, y, "Höchstens " + v.max() + " Lose je Tag. Jedes Los kostet " + Msg.coins(v.price()) + "$.", PALE, false);
        return c;
    }

    private static void stat(Canvas c, int x, int y, int w, KitData.Icon icon, String label, String value) {
        c.box(x, y, w, 40, KitData.Box.CLOVER);
        c.icon(x + 7, y + 12, icon, false);
        c.text(x + 29, y + 8, Canvas.caps(label), PALE, false);
        c.text(x + 29, y + 21, Canvas.fit(value, true, w - 36), WHITE, true);
    }
}
