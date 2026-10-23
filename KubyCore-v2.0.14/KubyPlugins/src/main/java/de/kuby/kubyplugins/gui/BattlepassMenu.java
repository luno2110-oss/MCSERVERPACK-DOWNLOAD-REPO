package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.BattlepassService;
import de.kuby.kubyplugins.service.BattlepassService.Reward;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Battlepass-Übersicht. Neun Stufen pro Seite: Reihe 1 Stufen, Reihe 2 Free-Spur, Reihe 3 Premium-Spur.
 * Unten: Blättern, Abholen und Premium kaufen.
 */
public final class BattlepassMenu extends GuiMenu {

    private static final int PER_PAGE = 9;
    private static final int PAGES = (BattlepassService.MAX_LEVEL + PER_PAGE - 1) / PER_PAGE;
    private final int page;

    public BattlepassMenu(KubyPlugins plugin, int page) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("battlepass") + " <gray>" + (page + 1) + "/" + PAGES), 6);
        this.page = Math.max(0, Math.min(page, PAGES - 1));
    }

    @Override
    protected void build(Player p) {
        UUID id = p.getUniqueId();
        BattlepassService bp = plugin.battlepass();
        int level = bp.level(id);
        boolean premium = bp.hasPremium(id);
        int claimedFree = bp.claimedOf(id, BattlepassService.FREE);
        int claimedPremium = bp.claimedOf(id, BattlepassService.PREMIUM);

        for (int c = 0; c < PER_PAGE; c++) {
            int lvl = page * PER_PAGE + c + 1;
            if (lvl > BattlepassService.MAX_LEVEL) {
                set(c, filler(), null);
                set(9 + c, filler(), null);
                set(18 + c, filler(), null);
                continue;
            }
            boolean reached = lvl <= level;
            set(c, Items.make(reached ? Material.LIME_DYE : Material.GRAY_DYE, "<gold>Stufe " + lvl, List.of(
                    reached ? "<green>✔ Erreicht" : "<red>✘ Noch nicht erreicht")), null);
            set(9 + c, cell(lvl, BattlepassService.FREE, claimedFree, reached, true),
                    e -> claim(p, BattlepassService.FREE));
            set(18 + c, cell(lvl, BattlepassService.PREMIUM, claimedPremium, reached, premium),
                    e -> claim(p, BattlepassService.PREMIUM));
        }

        for (int i = 27; i < 36; i++) set(i, filler(), null);

        int slot = 36;
        for (BattlepassService.Quest q : bp.quests()) {
            if (slot > 44) break;
            int prog = Math.min(bp.progressOf(id, q.id()), q.target());
            boolean done = bp.isDone(id, q.id());
            set(slot++, Items.make(done ? Material.LIME_DYE : Material.PAPER, "<gold>" + q.label(), List.of(
                    "<gray>Fortschritt: <white>" + prog + "<gray>/<white>" + q.target(),
                    "<gray>Belohnung: <aqua>+" + q.reward() + " BP-XP",
                    done ? "<green>✔ Abgeschlossen" : "<gray>Wöchentliche Quest")), null);
        }

        set(45, page > 0
                ? Items.make(Material.ARROW, "<gray>Vorherige Seite", List.of())
                : Items.make(Material.ARROW, "<gray>Zurück zum Menü", List.of()),
                e -> { if (page > 0) new BattlepassMenu(plugin, page - 1).open(p); else new MainMenu(plugin).open(p); });

        set(47, Items.make(Material.HOPPER, "<green>Freie Belohnungen holen", List.of(
                "<gray>Holt alle erreichten Stufen der Free-Spur")),
                e -> claim(p, BattlepassService.FREE));

        set(49, Items.make(Material.BOOK, "<gold>Level " + level + " <gray>/ " + BattlepassService.MAX_LEVEL, List.of(
                "<gray>BP-XP: <white>" + bp.xp(id),
                "<gray>Nächste Stufe bei <white>" + ((level + 1) * bp.xpPerLevel()) + " BP-XP")), null);

        if (!premium) {
            set(51, Items.make(Material.EMERALD_BLOCK, "<green>Premium-Pass kaufen", List.of(
                    "<gray>Preis: <aqua>" + bp.premiumPrice() + " Juwelen",
                    "<gray>Belohnungen auf jeder Stufe")),
                    e -> {
                        String err = bp.buyPremium(p);
                        Sfx.play(plugin, p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
                        Msg.send(p, err == null ? "<green>Premium-Pass freigeschaltet!" : "<red>" + err);
                        open(p);
                    });
        } else {
            set(51, Items.make(Material.DIAMOND, "<light_purple>Premium-Pass aktiv", List.of(
                    "<gray>Premium-Belohnungen abholen")),
                    e -> claim(p, BattlepassService.PREMIUM));
        }

        if (page < PAGES - 1) {
            set(53, Items.make(Material.ARROW, "<gray>Nächste Seite", List.of()),
                    e -> new BattlepassMenu(plugin, page + 1).open(p));
        }
    }

    private ItemStack cell(int lvl, String track, int claimedUpTo, boolean reached, boolean unlocked) {
        List<Reward> rewards = plugin.battlepass().rewardsAt(lvl, track);
        if (rewards.isEmpty()) return filler();
        boolean claimed = lvl <= claimedUpTo;
        boolean canClaim = reached && !claimed && unlocked;
        List<String> lore = new ArrayList<>();
        for (Reward r : rewards) lore.add("<gray>" + BattlepassService.describe(r));
        if (claimed) {
            lore.add("<green>✔ Erhalten");
        } else if (!unlocked) {
            lore.add("<red>✘ Premium-Pass nötig");
        } else if (reached) {
            lore.add("<yellow>Klick: abholen");
        } else {
            lore.add("<dark_gray>Noch nicht erreicht");
        }
        String title = FREE_TITLE(track) + " Stufe " + lvl;
        ItemStack it = Items.make(BattlepassService.iconOf(rewards.get(0)), title, lore);
        if (canClaim) glint(it);
        return it;
    }

    private static String FREE_TITLE(String track) {
        return BattlepassService.PREMIUM.equals(track) ? "<light_purple>Premium" : "<green>Free";
    }

    private void claim(Player p, String track) {
        if (BattlepassService.PREMIUM.equals(track) && !plugin.battlepass().hasPremium(p.getUniqueId())) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>Kaufe zuerst den Premium-Pass.");
            open(p);
            return;
        }
        int n = plugin.battlepass().claimTrack(p, track);
        if (n == 0) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<gray>Noch keine Belohnung zum Abholen.");
        } else {
            Sfx.play(plugin, p, Sfx.Kind.LEVEL_UP);
            Msg.send(p, "<green>Belohnungen abgeholt!");
        }
        open(p);
    }

    private ItemStack filler() {
        return Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private static void glint(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }
}
