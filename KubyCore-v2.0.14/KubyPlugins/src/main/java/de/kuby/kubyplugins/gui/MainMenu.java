package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import de.kuby.kubyplugins.util.Teleports;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Hauptmenü mit Abständen: 4 Einträge pro Reihe, nur jede zweite Reihe belegt.
 * Mehr Einträge gibt es auf weiteren Seiten, blättern mit den Pfeilen unten.
 */
public final class MainMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 12, 14, 16, 28, 30, 32, 34};
    private static final int PER_PAGE = SLOTS.length;

    private record Entry(Material icon, String name, String desc, Consumer<Player> action) {}

    private final int page;

    public MainMenu(KubyPlugins plugin) {
        this(plugin, 0);
    }

    public MainMenu(KubyPlugins plugin, int page) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Hauptmenü")), 6);
        this.page = page;
    }

    private List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        // Seite 1: Alltag
        list.add(new Entry(Material.GOLD_INGOT, "Wirtschaft", "Rangliste der reichsten Spieler", p -> new LeaderboardMenu(plugin).open(p)));
        list.add(new Entry(Material.EMERALD, "Shop", "Sektionen, Wochenende und Daily Shop", p -> new ShopMenu(plugin).open(p)));
        list.add(new Entry(Material.RED_BED, "Homes", "Deine Basen verwalten", p -> new HomesMenu(plugin).open(p)));
        list.add(new Entry(Material.CLOCK, "Täglich", "30-Tage-Kalender mit Belohnungen", p -> new DailyMenu(plugin).open(p)));
        list.add(new Entry(Material.WHITE_BANNER, "Team", "Team verwalten, Kasse und Teamhome", p -> new TeamMenu(plugin).open(p)));
        list.add(new Entry(Material.CHEST, "Markt", "Items kaufen und verkaufen", p -> new MarketMenu(plugin).open(p)));
        list.add(new Entry(Material.ENDER_PEARL, "Wildnis", "Zufalls-Teleport in die Kuby-Welt", p -> {
            p.closeInventory();
            Teleports.randomTeleport(plugin, p);
        }));
        list.add(new Entry(Material.COMPASS, "Spawn", "Zurück zum Spawn", p -> {
            p.closeInventory();
            Teleports.spawn(plugin, p);
        }));
        // Seite 2: Spiel und Fortschritt
        list.add(new Entry(Material.GOLD_BLOCK, "Tagespot", "Lose kaufen, Ziehung um 19 Uhr", p -> new DailyPotMenu(plugin).open(p)));
        list.add(new Entry(Material.FIRE_CHARGE, "Duo-RTP", "Zu zweit an einen Zufallsort, Kampf!", p -> new RtpQueueMenu(plugin).open(p)));
        list.add(new Entry(Material.NAME_TAG, "Titel", "Wähle deinen Titel neben dem Namen", p -> new TagsMenu(plugin).open(p)));
        list.add(new Entry(Material.ENCHANTED_BOOK, "Battlepass", "100 Stufen, Quests und Belohnungen", p -> plugin.battlepassScreen().open(p)));
        list.add(new Entry(Material.IRON_CHESTPLATE, "Kits", "Starter- und Rang-Kits abholen", p -> new KitMenu(plugin).open(p)));
        list.add(new Entry(Material.AMETHYST_SHARD, "Kosmetik", "Hüte, Spuren und Effekte", p -> new CosmeticsScreen(plugin).open(p)));
        // Seite 3: Schutz und Lager
        list.add(new Entry(Material.CLOCK, "Spielzeit", "Stufen nach aktiver Spielzeit", p -> new PlaytimeScreen(plugin).open(p)));
        list.add(new Entry(Material.ENDER_CHEST, "Enderchest", "Dein erweiterbares Lager", p -> p.openInventory(plugin.enderchests().open(p))));
        list.add(new Entry(Material.KNOWLEDGE_BOOK, "Guide", "Alles Wichtige auf zwölf Seiten", p -> p.performCommand("guide")));
        for (KubyPlugins.MainEntry e : plugin.mainEntries()) {
            list.add(new Entry(e.icon(), e.name(), e.desc(), e.action()));
        }
        return list;
    }

    @Override
    protected void build(Player p) {
        List<Entry> all = entries();
        int pages = (all.size() + PER_PAGE - 1) / PER_PAGE;
        int cur = Math.max(0, Math.min(page, pages - 1));
        int start = cur * PER_PAGE;

        for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
            Entry en = all.get(start + i);
            set(SLOTS[i], Items.make(en.icon(), "<gold>" + SmallCaps.of(en.name()), List.of(
                    "<gray>" + en.desc(),
                    "",
                    "<yellow>Klick zum Öffnen")),
                    e -> en.action().accept(p));
        }

        if (cur > 0) {
            set(45, Items.make(Material.ARROW, "<gray>Vorherige Seite", List.of()),
                    e -> new MainMenu(plugin, cur - 1).open(p));
        }
        set(49, Items.make(Material.PAPER, "<white>Seite " + (cur + 1) + " / " + pages, List.of(
                "<gray>Mit den Pfeilen blättern")), null);
        if (cur < pages - 1) {
            set(53, Items.make(Material.ARROW, "<gray>Nächste Seite", List.of()),
                    e -> new MainMenu(plugin, cur + 1).open(p));
        }
    }
}
