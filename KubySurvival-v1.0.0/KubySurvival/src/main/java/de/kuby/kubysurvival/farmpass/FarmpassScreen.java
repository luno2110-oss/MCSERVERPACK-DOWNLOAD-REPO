package de.kuby.kubysurvival.farmpass;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.pets.PetService;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /farmpass als Dialog in Feld-Limette mit Reitern Vorteile und Haustiere. Kopf mit Status (aktiv bis …, bezahlte
 * Monate) und ohne Pass dem Store-Link; Vorteile als Karten (Icon, Name, zwei Zeilen), mit Pass Schalter Fliegen,
 * Wochenkiste und Werkbank. Haustiere: die Pass-Haustiere und die Monats-Haustiere zur Auswahl. Ohne Pack: FarmpassMenu.
 */
public final class FarmpassScreen {

    static final int W = 390;
    static final TextColor LIME = TextColor.color(0xD4F070), CREAM = TextColor.color(0xF4F8E4), OLIVE = TextColor.color(0xA8B880),
            GOLD = TextColor.color(0xF8D35A), GREEN = TextColor.color(0x7FE08A), RED = TextColor.color(0xFF7A7A),
            WHITE = TextColor.color(0xF4F4F4), DIM = TextColor.color(0x707A58), DARK = TextColor.color(0x141C06),
            LEAF = TextColor.color(0x5DA642);

    public enum Tab { PERKS, PETS }

    public record Perk(KitData.Icon icon, String name, String line1, String line2) {}

    /** Haustier: Name, Bild, Pass-Haustier (sonst Monats-Haustier), gehört dir, Klick (wählen, null = nicht wählbar). */
    public record PassPet(String name, KitData.Icon icon, boolean pass, boolean have, Consumer<Player> choose) {}

    public record View(boolean active, String until, int months, String price, String storeUrl, Tab tab, List<Perk> perks,
                       boolean fly, boolean weekly, Consumer<Player> toggleFly, Consumer<Player> claimWeekly,
                       Consumer<Player> workbench, List<PassPet> pets, int picks, java.util.function.Function<Tab, Consumer<Player>> tabs) {}

    private final KubySurvival addon;

    public FarmpassScreen(KubySurvival addon) {
        this.addon = addon;
    }

    public void open(Player p) {
        open(p, Tab.PERKS);
    }

    public void open(Player p, Tab tab) {
        if (!addon.core().uiPack().active(p)) {
            if (tab == Tab.PETS) new FarmpassPetMenu(addon).open(p);
            else new FarmpassMenu(addon).open(p);
            return;
        }
        FarmpassService fp = addon.farmpass();
        UUID id = p.getUniqueId();
        boolean active = fp.active(id);
        var cfg = addon.core().getConfig();
        String until = active ? DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(Instant.ofEpochMilli(fp.until(id))
                .atZone(ZoneId.of("Europe/Berlin"))) : null;
        int daily = addon.quests() == null ? 3 : addon.quests().dailyCount();
        int extraDaily = addon.quests() == null ? 0 : addon.quests().farmpassExtraDaily();
        List<Perk> perks = new ArrayList<>(List.of(
                new Perk(KitData.Icon.FEATHER, "Fliegen", "Am Spawn, im Casino und auf", "Grundstücken (deine, Freunde)"),
                new Perk(KitData.Icon.IRON_HOE, "Nebenjob", "Ein zweiter Job, der mitzahlt", "/job → Neben"),
                new Perk(KitData.Icon.GOLD_NUGGET, "Keine Gebühren", "Keine Steuer im Markt", "und keine Casino-Steuer"),
                new Perk(KitData.Icon.RED_BED, "+" + cfg.getInt("farmpass.extra-homes", 2) + " Homes", "Mehr Plätze für Homes", ""),
                new Perk(KitData.Icon.CHEST_BLOCK, "Mehr Markt-Plätze", cfg.getInt("farmpass.market-listings", 15) + " Angebote gleichzeitig", ""),
                new Perk(KitData.Icon.CRAFTING_TABLE, "Werkbank & Endertruhe", "Überall: /werkbank", "und /endertruhe"),
                new Perk(KitData.Icon.ENDER_CHEST, "Wochenkiste", "Jede Woche Coins, Tokens", "und nützliche Items"),
                new Perk(KitData.Icon.LEAD, "Haustiere", "Pass-Haustiere, dazu eins", "pro Monat für immer"),
                new Perk(KitData.Icon.GRASS_BLOCK, "+" + cfg.getLong("farmpass.claim-blocks-per-month", 250) + " Claim-Blöcke",
                        "pro bezahltem Monat,", "bleiben für immer"),
                new Perk(KitData.Icon.LILY, "Grünes Blatt", "☘ vor deinem Namen", "in Tab und Chat")));
        if (extraDaily > 0) {
            perks.add(new Perk(KitData.Icon.WRITABLE, "+" + extraDaily + " Tages-Quest" + (extraDaily == 1 ? "" : "s"),
                    "Jeden Tag " + (daily + extraDaily) + " statt " + daily, "Tages-Quests (/quest)"));
        }
        List<PassPet> pets = new ArrayList<>();
        PetService ps = addon.pets();
        for (String pet : fp.passPets()) pets.add(new PassPet(nice(pet), petIcon(pet), true, active, null));
        List<String> chosen = fp.chosenPets(id);
        int picks = fp.petPicksLeft(id);
        for (String pet : fp.monthlyPets()) {
            boolean have = chosen.contains(pet);
            pets.add(new PassPet(nice(pet), petIcon(pet), false, have, have || picks <= 0 ? null : pl -> {
                String err = fp.choosePet(pl.getUniqueId(), pet);
                Msg.send(pl, err == null ? "<green>" + nice(pet) + " gehört jetzt dir!" : "<red>" + err);
                open(pl, Tab.PETS);
            }));
        }
        View v = new View(active, until, fp.months(id), cfg.getString("farmpass.price-text", ""), cfg.getString("farmpass.store-url", ""),
                tab, perks, fp.flyOn(id), fp.weeklyReady(id), pl -> {
            fp.toggleFly(pl.getUniqueId());
            open(pl, Tab.PERKS);
        }, pl -> {
            String err = fp.claimWeekly(pl);
            if (err != null) Msg.send(pl, "<red>" + err);
            open(pl, Tab.PERKS);
        }, pl -> {
            de.kuby.kubyplugins.ui.UiDialog.close(pl);
            pl.openWorkbench(null, true);
        }, pets, picks, t -> pl -> open(pl, t));
        UiDialog.show(p, canvas(v), 330);
    }

    private String nice(String id) {
        var k = addon.pets() == null ? null : addon.pets().kind(id);
        if (k != null) return k.name();
        String s = id.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private KitData.Icon petIcon(String id) {
        var k = addon.pets() == null ? null : addon.pets().kind(id);
        return k == null ? KitData.Icon.EGG : egg(k);
    }

    private static KitData.Icon egg(PetService.Kind k) {
        try {
            return KitData.Icon.valueOf("EGG_" + k.type().name());
        } catch (IllegalArgumentException e) {
            return KitData.Icon.EGG;
        }
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Farmpass", LIME);
        c.box(0, 0, W, 54, KitData.Box.LIME);
        c.icon(10, 11, KitData.Icon.LILY, true, LEAF);
        c.headline(50, 12, "Farmpass", LIME);
        if (v.active()) {
            c.text(50, 36, "Aktiv bis " + v.until() + " · " + v.months() + (v.months() == 1 ? " Monat" : " Monate") + " bezahlt", OLIVE, false);
            c.smallButton(W - 70, 10, 62, KitData.Small.GREEN, "☘ aktiv", WHITE, true);
        } else {
            c.text(50, 36, "Kein Pass · nur im Store" + (v.price().isEmpty() ? "" : ": " + v.price()), OLIVE, false);
            if (!v.storeUrl().isEmpty()) {
                c.smallButton(W - 92, 10, 84, KitData.Small.GOLD_ON, "Zum Store", DARK, true);
                c.actionUrl(W - 92, 10, 84, KitData.SMALL_H, v.storeUrl(), net.kyori.adventure.text.Component.text("Farmpass im Store kaufen"));
            }
        }
        // Reiter
        int x = 0;
        for (Tab t : Tab.values()) {
            String label = t == Tab.PERKS ? "Vorteile" : "Haustiere" + (v.picks() > 0 ? " (" + v.picks() + " frei)" : "");
            boolean on = t == v.tab();
            int w = Canvas.width(label, true) + 16;
            c.smallButton(x, 58, w, on ? KitData.Small.GREEN : KitData.Small.DARK, label, on ? WHITE : CREAM, true);
            if (!on) c.action(x, 58, w, KitData.SMALL_H, v.tabs().apply(t), label);
            x += w + 3;
        }
        int y = 80;
        if (v.tab() == Tab.PERKS) {
            if (v.active()) {
                int bw = (W - 6) / 3;
                toggle(c, 0, y, bw, v.fly() ? KitData.Icon.ELYTRA : KitData.Icon.FEATHER, "Fliegen: " + (v.fly() ? "an" : "aus"),
                        v.fly() ? KitData.Button.GREEN : KitData.Button.DARK, v.toggleFly(), "Fliegen an/aus (/farmpass fly)");
                toggle(c, bw + 3, y, bw, v.weekly() ? KitData.Icon.ENDER_CHEST : KitData.Icon.CHEST_BLOCK,
                        v.weekly() ? "Wochenkiste!" : "ab Montag", v.weekly() ? KitData.Button.GOLD : KitData.Button.DARK,
                        v.weekly() ? v.claimWeekly() : null, "Wochenkiste");
                toggle(c, 2 * (bw + 3), y, bw, KitData.Icon.CRAFTING_TABLE, "Werkbank", KitData.Button.DARK, v.workbench(), "/werkbank");
                y += 22 + 8;
            }
            int cw = (W - 4) / 2, ch = 40;
            for (int i = 0; i < v.perks().size(); i++) {
                Perk pk = v.perks().get(i);
                int px = (i % 2) * (cw + 4), py = y + (i / 2) * (ch + 3);
                c.box(px, py, cw, ch, v.active() ? KitData.Box.LIME_CARD : KitData.Box.GRAY);
                c.icon(px + 7, py + 12, pk.icon(), false, pk.icon() == KitData.Icon.LILY ? LEAF
                        : net.kyori.adventure.text.format.NamedTextColor.WHITE);
                c.text(px + 29, py + 6, Canvas.fit(pk.name(), true, cw - 36), v.active() ? LIME : CREAM, true);
                c.text(px + 29, py + 17, Canvas.fit(pk.line1(), false, cw - 36), OLIVE, false);
                if (!pk.line2().isEmpty()) c.text(px + 29, py + 27, Canvas.fit(pk.line2(), false, cw - 36), OLIVE, false);
            }
            return c;
        }
        // Haustiere
        String sec = Canvas.caps("Nur mit Pass");
        c.text(2, y, sec, LIME, true);
        y += 12;
        int cols = 4, cw = (W - 9) / cols, ch = 52;
        int i = 0;
        for (PassPet pt : v.pets()) {
            if (!pt.pass()) continue;
            petTile(c, (i % cols) * (cw + 3), y + (i / cols) * (ch + 3), cw, ch, pt, pt.have() ? "freigeschaltet" : "Pass nötig",
                    pt.have() ? GREEN : DIM);
            i++;
        }
        y += ((i + cols - 1) / cols) * (ch + 3) + 6;
        c.text(2, y, Canvas.caps("Monats-Haustiere · bleiben für immer"), LIME, true);
        c.textRight(W - 2, y, v.picks() > 0 ? v.picks() + " Auswahl offen" : "keine Auswahl offen", v.picks() > 0 ? GOLD : DIM, false);
        y += 12;
        i = 0;
        for (PassPet pt : v.pets()) {
            if (pt.pass()) continue;
            String st = pt.have() ? "✔ gehört dir" : pt.choose() != null ? "Klick: wählen" : "–";
            petTile(c, (i % cols) * (cw + 3), y + (i / cols) * (ch + 3), cw, ch, pt, st, pt.have() ? GREEN : pt.choose() != null ? GOLD : DIM);
            i++;
        }
        c.text(2, y + ((i + cols - 1) / cols) * (ch + 3) + 4, "Rufen und reiten im Haustier-Menü: /pet", OLIVE, false);
        return c;
    }

    /** Selbsttest: alle Vorteile, viele Haustiere, lange Texte; ohne Pass. */
    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        List<Perk> perks = new ArrayList<>();
        for (int i = 0; i < 12; i++) perks.add(new Perk(KitData.Icon.FEATHER, "W".repeat(30), "W".repeat(40), "W".repeat(40)));
        List<PassPet> pets = new ArrayList<>();
        for (int i = 0; i < 20; i++) pets.add(new PassPet("W".repeat(20), KitData.Icon.EGG_WOLF, i < 6, i % 2 == 0, i % 3 == 0 ? no : null));
        reg.accept("Farmpass Vorteile", () -> canvas(new View(true, "31.12.2099 23:59", 999, "W".repeat(30), "https://x", Tab.PERKS, perks,
                true, true, no, no, no, pets, 99, t -> no)));
        reg.accept("Farmpass Haustiere", () -> canvas(new View(true, "31.12.2099 23:59", 999, "", "", Tab.PETS, perks, false, false,
                no, no, no, pets, 99, t -> no)));
        reg.accept("Farmpass ohne Pass", () -> canvas(new View(false, null, 0, "W".repeat(30), "https://x", Tab.PERKS, perks, false,
                false, no, no, no, List.of(), 0, t -> no)));
    }

    private static void petTile(Canvas c, int x, int y, int w, int h, PassPet pt, String state, TextColor sc) {
        c.box(x, y, w, h, pt.have() ? KitData.Box.LIME_CARD : KitData.Box.GRAY);
        c.icon(x + (w - 16) / 2, y + 6, pt.icon(), false);
        c.textCenter(x + w / 2, y + 26, Canvas.fit(pt.name(), true, w - 8), pt.have() ? CREAM : OLIVE, true);
        c.textCenter(x + w / 2, y + 37, Canvas.fit(state, false, w - 6), sc, false);
        if (pt.choose() != null) c.action(x, y, w, h, pt.choose(), pt.name() + " wählen");
    }

    private static void toggle(Canvas c, int x, int y, int w, KitData.Icon icon, String label, KitData.Button style,
                               Consumer<Player> click, String hover) {
        c.button(x, y, w, style);
        String l = Canvas.fit(label, true, w - 30);
        int gw = 20 + Canvas.width(l, true);
        c.icon(x + (w - gw) / 2, y + 3, icon, false);
        if (style == KitData.Button.GREEN || style == KitData.Button.GOLD) c.textFlat(x + (w - gw) / 2 + 20, y + 7, l, DARK, true);
        else c.text(x + (w - gw) / 2 + 20, y + 7, l, CREAM, true);
        if (click != null) c.action(x, y, w, 22, click, hover);
    }
}
