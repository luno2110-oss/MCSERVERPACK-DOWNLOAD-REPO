package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.QuizService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Abstimmung über die Quiz-Kategorie der kommenden Woche. */
public final class QuizVoteMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    public QuizVoteMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("quiz-abstimmung")), 3);
    }

    @Override
    protected void build(Player p) {
        for (int i = 0; i < 27; i++) {
            set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        }
        QuizService quiz = plugin.quiz();
        Map<String, Integer> tally = quiz.tally();
        String mine = quiz.myVote(p.getUniqueId());
        String active = quiz.activeCategory();

        set(4, Items.make(Material.BOOK, "<aqua>Quiz-Kategorie", List.of(
                "<gray>Aktuell: <white>" + (active == null ? "alle Kategorien" : active),
                "<gray>Fragen im Pool: <white>" + quiz.questionCount(),
                "<gray>Die Abstimmung wird montags um 00:05 ausgewertet")), null);

        for (int i = 0; i < QuizService.CATEGORIES.size() && i < SLOTS.length; i++) {
            String cat = QuizService.CATEGORIES.get(i);
            boolean voted = cat.equals(mine);
            int votes = tally.getOrDefault(cat, 0);
            ItemStack item = Items.make(iconOf(cat), "<gold>" + SmallCaps.of(cat), List.of(
                    "<gray>Stimmen: <white>" + votes,
                    voted ? "<green>✔ Deine Stimme" : "<yellow>Klick: abstimmen"));
            if (voted) glint(item);
            set(SLOTS[i], item, e -> {
                String err = plugin.quiz().vote(p.getUniqueId(), cat);
                Sfx.play(plugin, p, err == null ? Sfx.Kind.PING : Sfx.Kind.ERROR);
                Msg.send(p, err == null ? "<green>Stimme für " + cat + " gespeichert." : "<red>" + err);
                open(p);
            });
        }
        addBack(p);
    }

    private static Material iconOf(String cat) {
        return switch (cat) {
            case "physik" -> Material.PRISMARINE_SHARD;
            case "mathe" -> Material.MAP;
            case "deutsch" -> Material.WRITABLE_BOOK;
            case "geografie" -> Material.FILLED_MAP;
            case "chemie" -> Material.EXPERIENCE_BOTTLE;
            case "minecraft" -> Material.GRASS_BLOCK;
            default -> Material.ENDER_EYE;
        };
    }

    private static void glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }
}
