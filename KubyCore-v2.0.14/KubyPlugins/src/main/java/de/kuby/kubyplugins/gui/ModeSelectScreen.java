package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Modus-Wahl als Dialog (Kompass in der Lobby, Modus-NPCs): drei Karten nebeneinander mit Bild, Name, kurzer
 * Beschreibung, Spielern im Modus und „Betreten“. Ohne Pack bleibt das Truhen-Menü (ModeSelectMenu).
 */
public final class ModeSelectScreen {

    public static final int W = 390, HEIGHT = 250;
    private static final TextColor GOLD = TextColor.color(0xFBCD01), GRAY = TextColor.color(0x9CA3B4),
            CREAM = TextColor.color(0xF4E6C0), GREEN = TextColor.color(0x6CE086), DARK = TextColor.color(0x1A1206);

    /** Karte eines Modus. */
    public record Card(String name, Material icon, KitData.Box box, TextColor color, List<String> lines, int players,
                       boolean here, Consumer<Player> enter) {}

    public record View(List<Card> cards, int online) {}

    private ModeSelectScreen() {
    }

    public static void open(KubyPlugins plugin, Player p) {
        if (!plugin.uiPack().active(p)) {
            new ModeSelectMenu(plugin).open(p);
            return;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (Player o : Bukkit.getOnlinePlayers()) counts.merge(plugin.modes().modeOf(o.getUniqueId()), 1, Integer::sum);
        String cur = plugin.modes().modeOf(p.getUniqueId());
        List<Card> cards = new ArrayList<>();
        cards.add(card(plugin, "SMP", ModeService.SMP, Material.IRON_SWORD, KitData.Box.RED, 0xFF6A5A,
                List.of("PvP und Kopfgelder", "Teams und Turniere", "Trophäen sammeln"), counts, cur));
        cards.add(card(plugin, "Survival", ModeService.SURVIVAL, Material.GRASS_BLOCK, KitData.Box.GREEN, 0x6CE086,
                List.of("Ruhig bauen", "Jobs, Angeln, Farmen", "Grundstücke, Haustiere"), counts, cur));
        cards.add(card(plugin, "Skyblock", ModeService.SKYBLOCK, Material.OAK_SAPLING, KitData.Box.TEAL, 0x6FE3D2,
                List.of("Eigene Insel", "Generatoren", "One-Block, Nether, End"), counts, cur));
        UiDialog.show(p, canvas(new View(cards, Bukkit.getOnlinePlayers().size())), HEIGHT);
    }

    private static Card card(KubyPlugins plugin, String name, String mode, Material icon, KitData.Box box, int color,
                             List<String> lines, Map<String, Integer> counts, String cur) {
        boolean here = mode.equals(cur);
        return new Card(name, icon, box, TextColor.color(color), lines, counts.getOrDefault(mode, 0), here, pl -> {
            UiDialog.close(pl);
            if (here) return;
            String err = plugin.modes().switchTo(pl, mode);
            if (err != null) {
                Sfx.play(plugin, pl, Sfx.Kind.ERROR);
                Msg.send(pl, "<red>" + err);
            }
        });
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Modus wählen", GOLD);
        c.box(0, 0, W, 52, KitData.Box.HUB);
        c.icon(12, 10, KitData.Icon.COMPASS, true);
        c.headline(52, 10, "Modus wählen", GOLD);
        c.text(52, 35, v.online() + (v.online() == 1 ? " Spieler" : " Spieler") + " online · wo willst du spielen?", GRAY, false);
        int cw = (W - 8) / 3, ch = 176, y = 58;
        for (int i = 0; i < v.cards().size(); i++) {
            Card k = v.cards().get(i);
            int x = i * (cw + 4);
            c.box(x, y, cw, ch, k.box());
            c.item(x + (cw - 32) / 2, y + 10, k.icon(), true, KitData.Icon.COMPASS);
            c.textCenter(x + cw / 2, y + 48, k.name(), k.color(), true);
            int ly = y + 64;
            for (String line : k.lines()) {
                c.textCenter(x + cw / 2, ly, Canvas.fit(line, false, cw - 10), CREAM, false);
                ly += 11;
            }
            c.textCenter(x + cw / 2, y + 108, k.players() + (k.players() == 1 ? " Spieler" : " Spieler"), k.players() > 0 ? GREEN : GRAY, true);
            int bw = cw - 24, bx = x + 12, by = y + ch - 32;
            if (k.here()) {
                c.button(bx, by, bw, KitData.Button.DARK, "Du bist hier", GRAY, false);
            } else {
                c.button(bx, by, bw, KitData.Button.GOLD, "Betreten", DARK, true);
                c.action(bx, by, bw, 22, k.enter(), k.name() + " betreten");
            }
            c.action(x, y, cw, ch - 40, k.enter(), k.name() + " betreten");
        }
        return c;
    }
}
