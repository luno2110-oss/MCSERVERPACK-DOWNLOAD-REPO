package de.kuby.kubyskyblock.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.TopListScreen;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.StreakService;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** /combo: Tages-Rangliste der besten Combo (Podest wie /baltop), Preise für die ersten drei um Mitternacht. */
public final class ComboCommand implements CommandExecutor {

    private static final TextColor GOLD = TextColor.color(0xF8D35A);

    private final KubySkyblock addon;
    private final KubyPlugins core;

    public ComboCommand(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        open(p);
        return true;
    }

    private static String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    static String prizeText(long[] pr) {
        return pr[0] <= 0 && pr[1] <= 0 ? null : Canvas.num(pr[0]) + " $" + (pr[1] > 0 ? " + " + pr[1] + " Juwelen" : "");
    }

    public void open(Player p) {
        StreakService s = addon.streaks();
        if (s == null) return;
        UUID me = p.getUniqueId();
        List<TopListScreen.Entry> list = new ArrayList<>();
        int i = 1;
        for (Map.Entry<UUID, Integer> e : s.top(10)) {
            String extra = i <= s.prizePlaces() ? prizeText(s.prize(i, e.getKey())) : null;
            list.add(new TopListScreen.Entry(i++, name(e.getKey()), Canvas.num(e.getValue()) + " Blöcke", extra));
        }
        List<String> last = s.lastWinners();
        String sub = "Beste Combo heute · Preise um 0 Uhr" + (last.isEmpty() ? "" : " · Gestern: " + last.get(0));
        String mine = "Dein Bestwert heute: " + Canvas.num(s.bestToday(me)) + " · jetzt " + s.combo(me)
                + (s.starter(me) > 1.0 ? " · Start-Boost noch " + s.starterHoursLeft(me) + " Std." : "");
        if (!core.uiPack().active(p)) {
            Msg.send(p, "<#FFD25A><b>Combo-Rangliste</b> <gray>(heute, Preise um 0 Uhr)");
            if (list.isEmpty()) Msg.send(p, "<gray>Noch niemand. Bau los!");
            for (TopListScreen.Entry e : list) {
                Msg.send(p, "<yellow>" + e.place() + ". <white>" + e.name() + " <gray>" + e.value() + (e.extra() == null ? "" : " <dark_gray>· <gold>" + e.extra()));
            }
            if (!last.isEmpty()) Msg.send(p, "<gray>Gestern: <white>" + String.join("<gray>, <white>", last));
            Msg.send(p, "<#FFD25A>" + mine);
            return;
        }
        UiDialog.show(p, canvas(sub, list, mine), 330);
    }

    static Canvas canvas(String sub, List<TopListScreen.Entry> list, String me) {
        return TopListScreen.canvas(new TopListScreen.View("Combo heute", sub, KitData.Icon.STAR, GOLD, list, me));
    }

    /** Für den Selbsttest: Grenzwerte. */
    public static void samples(BiConsumer<String, Supplier<Canvas>> reg) {
        String w = "W".repeat(16);
        List<TopListScreen.Entry> full = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            full.add(new TopListScreen.Entry(i, w, Canvas.num(999_999) + " Blöcke", i <= 3 ? prizeText(new long[] {999_999_999L, 999}) : null));
        }
        reg.accept("Combo-Rangliste", () -> canvas("Beste Combo heute · Preise um 0 Uhr · Gestern: 1. " + w + " (99999)", full,
                "Dein Bestwert heute: 999.999 · jetzt 99999 · Start-Boost noch 72 Std."));
        reg.accept("Combo-Rangliste leer", () -> canvas("Beste Combo heute · Preise um 0 Uhr", List.of(), "Dein Bestwert heute: 0 · jetzt 0"));
    }
}
