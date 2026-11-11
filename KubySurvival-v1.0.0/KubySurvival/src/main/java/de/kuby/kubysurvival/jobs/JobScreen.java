package de.kuby.kubysurvival.jobs;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Icons;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.tokens.TokenEnchantMenu;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /job als Dialog in Holz-Optik (Werkstatt): Reiter Berufe, Token-Shop und Verzauberungen.
 * Berufe: Kopf mit Spielerlevel und Tokens, drei Felder (Stundenlimit, Wochenauftrag, Booster), acht Berufe als Karten
 * mit Level, Balken, Aufgabe und Status; Klick wählt den Beruf, mit Farmpass gibt es den Nebenjob-Knopf.
 * Token-Shop: Angebote nach Bereichen als Karten mit Preis. Verzauberungen: Item in der Hand und neun Stufen-Upgrades.
 * Ohne Pack bleiben die Truhen (JobMenu, TokenShopMenu, TokenEnchantMenu).
 */
public final class JobScreen {

    static final int W = 390;
    static final TextColor AMBER = TextColor.color(0xF0A050), CREAM = TextColor.color(0xF4E2C4),
            TAN = TextColor.color(0xB89A78), WHITE = TextColor.color(0xF4F4F4), GOLD = TextColor.color(0xF8D35A),
            TOKENS = TextColor.color(0xD08CFF), GREEN = TextColor.color(0x7FE08A), RED = TextColor.color(0xFF7A7A),
            TEAL = TextColor.color(0x5FD8CC), DARK = TextColor.color(0x1A1206), DIM = TextColor.color(0x8A7A66);

    public enum Tab {
        JOBS("Berufe"), SHOP("Token-Shop"), ENCHANT("Verzauberungen");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    /** Kopf: Spielerlevel, Fortschritt zum nächsten (0..1), Tokens, Hauptjob (null = keiner). */
    public record Head(int level, double progress, long tokens, String job) {}

    /** Ein Beruf: Name, Farbe, Icon, Beschreibung, Level, Höchstlevel, Fortschritt, Hauptjob, Nebenjob, Status, Klicks. */
    public record JobCard(String name, TextColor color, KitData.Icon icon, String desc, int level, int max, double progress,
                          boolean main, boolean side, String state, TextColor stateColor, Consumer<Player> click,
                          Consumer<Player> sideClick) {}

    /** Stundenlimit, Wochenauftrag (null = noch keiner), Booster-Minuten. */
    public record Info(long earned, long cap, long minutes, String orderText, double orderProgress, String orderReward,
                       boolean orderDone, long boosterMinutes) {}

    /** Angebot im Token-Shop (Darstellung). */
    public record Offer(String section, Material icon, String name, String desc, String price, boolean enabled,
                        Consumer<Player> click) {}

    /** Verzauberung: Name mit Stufe, Icon, Preis, Status, kaufbar. */
    public record Ench(String name, KitData.Icon icon, long price, String state, TextColor stateColor, boolean ready,
                       Consumer<Player> click) {}

    public interface Actions {
        Consumer<Player> tab(Tab t);
    }

    private final KubySurvival addon;

    public JobScreen(KubySurvival addon) {
        this.addon = addon;
    }

    // =================================================================================================================
    // Steuerung
    // =================================================================================================================

    public void open(Player p) {
        open(p, Tab.JOBS);
    }

    public void open(Player p, Tab tab) {
        if (!addon.core().uiPack().active(p)) {
            switch (tab) {
                case SHOP -> new TokenShopMenu(addon, addon.core()).open(p);
                case ENCHANT -> new TokenEnchantMenu(addon).open(p);
                default -> new JobMenu(addon, addon.core()).open(p);
            }
            return;
        }
        Actions a = t -> pl -> open(pl, t);
        Head head = head(p);
        Canvas c = switch (tab) {
            case JOBS -> jobs(head, info(p), cards(p), a);
            case SHOP -> shop(head, offers(p), a);
            case ENCHANT -> enchant(head, hand(p), enchants(p), a);
        };
        UiDialog.show(p, c, 330);
    }

    private Head head(Player p) {
        JobService.Worker w = addon.jobs().worker(p.getUniqueId());
        int level = JobService.playerLevel(w);
        long from = JobService.playerXpFor(level), to = JobService.playerXpFor(level + 1);
        double prog = Math.min(1, (w.totalXp - from) / (double) Math.max(1, to - from));
        return new Head(level, prog, w.tokens, w.job == null ? null : w.job.display);
    }

    private Info info(Player p) {
        JobService jobs = addon.jobs();
        JobService.Worker w = jobs.worker(p.getUniqueId());
        String text = null, reward = null;
        double prog = 0;
        if (w.orderJob != null && w.orderGoal > 0) {
            text = Msg.coins(Math.min(w.orderDone, w.orderGoal)) + " / " + Msg.coins(w.orderGoal) + " XP";
            prog = Math.min(1, w.orderDone / (double) w.orderGoal);
            reward = Msg.coins(jobs.orderCoins()) + " Coins + " + jobs.orderTokens() + " Tokens";
        }
        return new Info(w.earnedHour, jobs.hourlyCap(w), JobService.minutesToNextHour(), text, prog, reward, w.orderFinished,
                jobs.boosterMinutes(p.getUniqueId()));
    }

    static TextColor color(String tag) {
        if (tag == null) return WHITE;
        String t = tag.replace("<", "").replace(">", "");
        if (t.startsWith("#") && t.length() >= 7) {
            TextColor c = TextColor.fromHexString(t.substring(0, 7));
            return c == null ? WHITE : c;
        }
        NamedTextColor n = NamedTextColor.NAMES.value(t);
        return n == null ? WHITE : n;
    }

    private List<JobCard> cards(Player p) {
        JobService jobs = addon.jobs();
        UUID id = p.getUniqueId();
        JobService.Worker w = jobs.worker(id);
        long wait = jobs.cooldownMinutes(id);
        boolean pass = addon.farmpass() != null && addon.farmpass().active(id);
        List<JobCard> out = new ArrayList<>();
        for (Job j : Job.values()) {
            boolean main = w.job == j, side = w.sideJob == j;
            String state;
            TextColor sc;
            if (main) {
                state = "✔ Dein Job";
                sc = GREEN;
            } else if (side) {
                state = "Nebenjob (Farmpass)";
                sc = TEAL;
            } else if (wait > 0) {
                state = "Wechsel in " + wait + " Min.";
                sc = RED;
            } else {
                state = "Klick: wählen";
                sc = GOLD;
            }
            Consumer<Player> click = main ? null : pl -> {
                String err = jobs.choose(pl, j);
                if (err != null) Msg.send(pl, "<red>" + err);
                else {
                    Msg.send(pl, "<green>Du bist jetzt " + j.color + j.display + "<green>.");
                    pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
                }
                open(pl, Tab.JOBS);
            };
            Consumer<Player> sideClick = !pass || main ? null : pl -> {
                String err = jobs.chooseSide(pl, side ? null : j);
                Msg.send(pl, err != null ? "<red>" + err : side ? "<gray>Nebenjob aus."
                        : "<green>Dein Nebenjob: " + j.color + j.display + "<green>.");
                open(pl, Tab.JOBS);
            };
            out.add(new JobCard(j.display, color(j.color), Icons.of(j.icon, KitData.Icon.STAR), j.desc, jobs.jobLevel(w, j),
                    jobs.maxLevel(), jobs.progress(w, j) / 100.0, main, side, state, sc, click, sideClick));
        }
        return out;
    }

    private List<Offer> offers(Player p) {
        List<Offer> out = new ArrayList<>();
        for (TokenOffers.Offer o : TokenOffers.list(addon, p)) {
            String price = o.note() != null ? o.note() : o.price() <= 0 ? "gratis" : o.price() + " Tokens";
            Consumer<Player> click = !o.enabled() ? null : pl -> {
                String err = o.buy().apply(pl);
                if (err != null) {
                    Msg.send(pl, "<red>" + err);
                    pl.playSound(pl.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
                }
                if (o.icon() != Material.CHEST) open(pl, Tab.SHOP);
            };
            out.add(new Offer(o.section(), o.icon(), o.name(), o.desc(), price, o.enabled(), click));
        }
        return out;
    }

    private static String hand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        return hand.getType().isAir() ? null : toolName(hand.getType());
    }

    /** Deutscher Name für Werkzeuge, Waffen und Rüstung (sonst der Material-Name). */
    static String toolName(Material m) {
        String n = m.name();
        String[][] pre = {{"DIAMOND_", "Diamant"}, {"NETHERITE_", "Netherit"}, {"IRON_", "Eisen"}, {"GOLDEN_", "Gold"},
                {"STONE_", "Stein"}, {"WOODEN_", "Holz"}, {"CHAINMAIL_", "Ketten"}, {"LEATHER_", "Leder"}, {"TURTLE_", "Schildkröten"}};
        String[][] post = {{"PICKAXE", "spitzhacke"}, {"AXE", "axt"}, {"SHOVEL", "schaufel"}, {"HOE", "hacke"}, {"SWORD", "schwert"},
                {"HELMET", "helm"}, {"CHESTPLATE", "harnisch"}, {"LEGGINGS", "hose"}, {"BOOTS", "stiefel"}};
        for (String[] a : pre) {
            if (!n.startsWith(a[0])) continue;
            for (String[] b : post) if (n.equals(a[0] + b[0])) return a[1] + b[1];
        }
        return switch (m) {
            case BOW -> "Bogen";
            case CROSSBOW -> "Armbrust";
            case FISHING_ROD -> "Angel";
            case TRIDENT -> "Dreizack";
            case MACE -> "Streitkolben";
            case SHEARS -> "Schere";
            case ELYTRA -> "Elytren";
            case BOOK, ENCHANTED_BOOK -> "Buch";
            default -> TokenOffers.nice(m);
        };
    }

    private List<Ench> enchants(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        List<Ench> out = new ArrayList<>();
        for (TokenEnchantMenu.Upgrade u : TokenEnchantMenu.UPGRADES) {
            int max = u.ench().getMaxLevel();
            int have = hand.getType().isAir() ? 0 : hand.getEnchantmentLevel(u.ench());
            boolean done = have > max, ready = have == max;
            String state = done ? "✔ hat dein Item" : ready ? "Klick: verzaubern" : "braucht " + u.name() + " " + TokenEnchantMenu.roman(max);
            out.add(new Ench(u.name() + " " + TokenEnchantMenu.roman(max + 1), Icons.of(u.icon(), KitData.Icon.BOOK), u.price(),
                    state, done ? GREEN : ready ? GOLD : DIM, ready, !ready ? null : pl -> {
                TokenEnchantMenu.apply(addon, pl, u);
                open(pl, Tab.ENCHANT);
            }));
        }
        return out;
    }

    /** Selbsttest: alle Seiten mit Grenzwerten. */
    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        Actions a = t -> no;
        Head h = new Head(9999, 0.99, 999_999_999L, "WWWWWWWWWWWWWWWW");
        List<JobCard> cards = new ArrayList<>();
        for (Job j : Job.values()) {
            boolean main = j.ordinal() == 0, side = j.ordinal() == 1;
            cards.add(new JobCard("WWWWWWWWWWWWWWWW", color(j.color), Icons.of(j.icon, KitData.Icon.STAR), "W".repeat(60), 100, 100, 1,
                    main, side, "W".repeat(40), GOLD, main ? null : no, main ? null : no));
        }
        reg.accept("Jobs Berufe", () -> jobs(h, new Info(999_999_999L, 999_999_999L, 59, "999.999.999 / 999.999.999 XP", 1,
                "999.999 Coins + 999 Tokens", false, 999), cards, a));
        reg.accept("Jobs Berufe leer", () -> jobs(new Head(1, 0, 0, null), new Info(0, 0, 0, null, 0, null, true, 0), List.of(), a));
        List<Offer> offers = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            offers.add(new Offer("WWWWWWWWWWWWWWWW" + (i / 4), Material.GRASS_BLOCK, "W".repeat(30), "W".repeat(40),
                    "999.999 Tokens", i % 3 != 0, i % 3 != 0 ? no : null));
        }
        reg.accept("Jobs Token-Shop", () -> shop(h, offers, a));
        reg.accept("Jobs Token-Shop leer", () -> shop(h, List.of(), a));
        List<Ench> en = new ArrayList<>();
        for (int i = 0; i < 9; i++) en.add(new Ench("W".repeat(20), KitData.Icon.BOOK, 999_999, "W".repeat(30), GOLD, i == 0, i == 0 ? no : null));
        reg.accept("Jobs Verzauberungen", () -> enchant(h, "W".repeat(40), en, a));
        reg.accept("Jobs Verzauberungen leer", () -> enchant(h, null, List.of(), a));
    }

    // =================================================================================================================
    // Darstellung
    // =================================================================================================================

    /** Kopf und Reiter, gibt die Oberkante des Inhalts zurück. */
    static int frame(Canvas c, Head h, Tab tab, Actions a) {
        c.box(0, 0, W, 54, KitData.Box.WOOD);
        c.icon(10, 11, KitData.Icon.IRON_PICKAXE, true);
        c.headline(50, 12, "Jobs", AMBER);
        c.text(50, 36, "Level " + h.level() + " · " + (h.job() == null ? "noch kein Beruf" : h.job()), TAN, false);
        int tw = c.textRight(372, 12, Msg.coins(h.tokens()) + " Tokens", TOKENS, true);
        c.icon(372 - tw - 20, 8, KitData.Icon.AMETHYST, false);
        c.bar(276, 30, 96, h.progress(), KitData.Fill.GOLD);
        c.textRight(270, 31, (int) Math.round(h.progress() * 100) + " %", TAN, false);
        int x = 0;
        for (Tab t : Tab.values()) {
            boolean on = t == tab;
            int w = Canvas.width(t.label, true) + 16;
            c.smallButton(x, 58, w, on ? KitData.Small.GOLD_ON : KitData.Small.DARK, t.label, on ? DARK : CREAM, true);
            if (!on) c.action(x, 58, w, KitData.SMALL_H, a.tab(t), t.label);
            x += w + 3;
        }
        return 58 + KitData.SMALL_H + 6;
    }

    public static Canvas jobs(Head h, Info info, List<JobCard> cards, Actions a) {
        Canvas c = new Canvas(W).heading("Jobs", AMBER);
        int y = frame(c, h, Tab.JOBS, a);
        // drei Felder
        int fw = (W - 6) / 3;
        field(c, 0, y, fw, KitData.Icon.CLOCK, "Stundenlimit", Msg.coins(info.earned()) + " / " + Msg.coins(info.cap()),
                GOLD, "Coins · neu in " + info.minutes() + " Min.", info.cap() > 0 ? info.earned() / (double) info.cap() : 0);
        if (info.orderText() == null) {
            field(c, fw + 3, y, fw, KitData.Icon.WRITABLE, "Wochenauftrag", "noch keiner", TAN, "Wähle einen Beruf", -1);
        } else {
            field(c, fw + 3, y, fw, KitData.Icon.WRITABLE, "Wochenauftrag", info.orderDone() ? "✔ erledigt" : info.orderText(),
                    info.orderDone() ? GREEN : CREAM, info.orderDone() ? "Neuer am Montag" : info.orderReward(), info.orderProgress());
        }
        field(c, 2 * (fw + 3), y, fw, KitData.Icon.BLAZE_POWDER, "Booster",
                info.boosterMinutes() > 0 ? "noch " + info.boosterMinutes() + " Min." : "aus",
                info.boosterMinutes() > 0 ? GREEN : TAN, "im Token-Shop", -1);
        c.action(2 * (fw + 3), y, fw, FIELD_H, a.tab(Tab.SHOP), "Booster im Token-Shop");
        y += FIELD_H + 6;
        // Berufe in zwei Spalten
        int cw = (W - 4) / 2, ch = 58;
        for (int i = 0; i < cards.size(); i++) {
            JobCard k = cards.get(i);
            int x = (i % 2) * (cw + 4), cy = y + (i / 2) * (ch + 4);
            c.box(x, cy, cw, ch, k.main() ? KitData.Box.GOLD : k.side() ? KitData.Box.TEAL : KitData.Box.WOOD_CARD);
            c.icon(x + 7, cy + 13, k.icon(), true);
            int lv = c.textRight(x + cw - 8, cy + 8, "Lv " + k.level(), CREAM, true);
            c.text(x + 46, cy + 8, Canvas.fit(k.name(), true, cw - 46 - 8 - lv - 6), k.color(), true);
            c.bar(x + 46, cy + 19, cw - 46 - 8, k.progress(), KitData.Fill.GOLD);
            c.text(x + 46, cy + 30, Canvas.fit(k.desc(), false, cw - 46 - 8), TAN, false);
            int sw = 0;
            if (k.sideClick() != null) {
                String label = k.side() ? "aus" : "Neben";
                sw = Canvas.width(label, false) + 10;
                c.smallButton(x + cw - 6 - sw, cy + ch - 20, sw, KitData.Small.SET_TEAL, label, WHITE, false);
                c.action(x + cw - 6 - sw, cy + ch - 20, sw, KitData.SMALL_H, k.sideClick(), label);
            }
            c.text(x + 46, cy + 42, Canvas.fit(k.state(), false, cw - 46 - 8 - (sw > 0 ? sw + 4 : 0)), k.stateColor(), false);
            if (k.click() != null) c.action(x, cy, cw - (sw > 0 ? sw + 8 : 0), ch, k.click(), k.state());
        }
        return c;
    }

    static final int FIELD_H = 54;

    /** Feld mit Icon, Überschrift, Wert, (progress >= 0) Balken und Zusatz. */
    private static void field(Canvas c, int x, int y, int w, KitData.Icon icon, String title, String value, TextColor vc,
                              String sub, double progress) {
        c.box(x, y, w, FIELD_H, KitData.Box.WOOD_CARD);
        c.icon(x + 6, y + 5, icon, false);
        c.text(x + 26, y + 9, Canvas.caps(title), AMBER, false);
        c.text(x + 7, y + 23, Canvas.fit(value, true, w - 14), vc, true);
        if (progress >= 0) c.bar(x + 7, y + 33, w - 14, progress, KitData.Fill.GOLD);
        if (sub != null) c.text(x + 7, y + (progress >= 0 ? 42 : 34), Canvas.fit(sub, false, w - 14), TAN, false);
    }

    public static Canvas shop(Head h, List<Offer> offers, Actions a) {
        Canvas c = new Canvas(W).heading("Token-Shop", TOKENS);
        int y = frame(c, h, Tab.SHOP, a);
        String section = null;
        int i = 0, cw = (W - 4) / 2, ch = 34;
        for (Offer o : offers) {
            if (!o.section().equals(section)) {
                if (section != null) y += ((i + 1) / 2) * (ch + 4);
                section = o.section();
                i = 0;
                c.text(2, y, Canvas.caps(section), AMBER, true);
                c.line(6 + Canvas.width(Canvas.caps(section), true), y + 3, W - 8 - Canvas.width(Canvas.caps(section), true), DIM);
                y += 12;
            }
            int x = (i % 2) * (cw + 4), oy = y + (i / 2) * (ch + 4);
            c.box(x, oy, cw, ch, o.enabled() ? KitData.Box.PURPLE : KitData.Box.WOOD_CARD, Canvas.Z_BUTTON);
            c.item(x + 7, oy + 9, o.icon(), false, KitData.Icon.PAPER);
            int pw = Canvas.width(o.price(), true);
            c.text(x + 29, oy + 7, Canvas.fit(o.name(), true, cw - 29 - 7), o.enabled() ? CREAM : TAN, true);
            c.textRight(x + cw - 7, oy + 19, o.price(), o.enabled() ? TOKENS : DIM, true);
            c.text(x + 29, oy + 19, Canvas.fit(o.desc(), false, cw - 29 - pw - 14), TAN, false);
            if (o.click() != null) c.action(x, oy, cw, ch, o.click(), o.name() + " · " + o.price());
            i++;
        }
        if (section == null) c.text(4, y, "Gerade gibt es nichts zu kaufen.", TAN, false);
        return c;
    }

    public static Canvas enchant(Head h, String hand, List<Ench> list, Actions a) {
        Canvas c = new Canvas(W).heading("Verzauberungen", TOKENS);
        int y = frame(c, h, Tab.ENCHANT, a);
        c.box(0, y, W, 40, KitData.Box.WOOD_CARD);
        c.icon(8, y + 12, KitData.Icon.ENCHANTING_TABLE, false);
        c.text(30, y + 9, hand == null ? "Nimm ein Werkzeug, eine Waffe oder Rüstung in die Hand."
                : "In der Hand: " + Canvas.fit(hand, true, 250), hand == null ? GOLD : CREAM, hand != null);
        c.text(30, y + 22, "Die Verzauberung muss schon auf der höchsten Stufe sein.", TAN, false);
        y += 46;
        int cw = (W - 8) / 3, ch = 46;
        for (int i = 0; i < list.size(); i++) {
            Ench e = list.get(i);
            int x = (i % 3) * (cw + 4), ey = y + (i / 3) * (ch + 4);
            c.box(x, ey, cw, ch, e.ready() ? KitData.Box.PURPLE : KitData.Box.WOOD_CARD, Canvas.Z_BUTTON);
            c.icon(x + 6, ey + 7, e.icon(), false);
            c.text(x + 26, ey + 8, Canvas.fit(e.name(), true, cw - 26 - 6), e.ready() ? CREAM : TAN, true);
            c.text(x + 7, ey + 24, e.price() + " Tokens", TOKENS, true);
            c.text(x + 7, ey + 35, Canvas.fit(e.state(), false, cw - 14), e.stateColor(), false);
            if (e.click() != null) c.action(x, ey, cw, ch, e.click(), e.name() + " · " + e.price() + " Tokens");
        }
        return c;
    }
}
