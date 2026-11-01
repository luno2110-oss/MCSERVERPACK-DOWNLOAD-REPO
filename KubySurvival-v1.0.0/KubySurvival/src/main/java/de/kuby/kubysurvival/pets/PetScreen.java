package de.kuby.kubysurvival.pets;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * /pet als Dialog in Blütenrosa: oben das gerufene Haustier (Aufsteigen, Wegschicken), darunter alle Haustiere als
 * Kacheln (eigene zuerst, Lieblinge mit Stern): Bild (Shop-Pets aus dem Pack, sonst das Spawn-Ei), Name, Status;
 * Klick ruft oder schickt weg, kleine Knöpfe für Liebling und Umbenennen; gesperrte zeigen Quelle und Preis (Kaufen nach
 * Nachfrage, Juwelen-Pets öffnen den Juwelenshop). 16 je Seite. Ohne Pack: PetMenu.
 */
public final class PetScreen {

    static final int W = 390, PER_PAGE = 16;
    static final TextColor PINK = TextColor.color(0xFFB0D8), CREAM = TextColor.color(0xFFF0F6), ROSE = TextColor.color(0xC890AC),
            GOLD = TextColor.color(0xF8D35A), GREEN = TextColor.color(0x7FE08A), WHITE = TextColor.color(0xF4F4F4),
            DIM = TextColor.color(0x806878), JEWEL = TextColor.color(0x5FE3A0), LOCKED = TextColor.color(0x505050);

    /** Haustier-Kachel: Name, Bild (Sprite oder Icon), eigenes, Liebling, gerufen, Status, Klicks (null = keiner). */
    public record Pet(String name, KitData.Sprite sprite, KitData.Icon icon, boolean own, boolean fav, boolean out, String state,
                      TextColor stateColor, Consumer<Player> click, Consumer<Player> favorite, Consumer<Player> rename) {}

    /** Gerufenes Haustier (null = keins): Name, reitbar, Klicks. */
    public record Active(String name, boolean rideable, Consumer<Player> ride, Consumer<Player> dismiss) {}

    public record View(int owned, int total, Active active, List<Pet> pets, int page, int pages, IntFunction<Consumer<Player>> goTo) {}

    private static final Map<UUID, String> ARMED = new HashMap<>();
    private final KubySurvival addon;

    public PetScreen(KubySurvival addon) {
        this.addon = addon;
    }

    static KitData.Sprite sprite(PetService.Kind k) {
        if (k.model() == null || k.model().isBlank()) return null;
        try {
            return KitData.Sprite.valueOf("JSS_P_" + k.id().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static KitData.Icon egg(PetService.Kind k) {
        try {
            return KitData.Icon.valueOf("EGG_" + k.type().name());
        } catch (IllegalArgumentException e) {
            return KitData.Icon.EGG;
        }
    }

    public void open(Player p) {
        open(p, 0);
    }

    public void open(Player p, int page) {
        if (!addon.core().uiPack().active(p)) {
            new PetMenu(addon, page).open(p);
            return;
        }
        PetService ps = addon.pets();
        UUID id = p.getUniqueId();
        String activeId = ps.activeKind(id);
        List<PetService.Kind> list = new ArrayList<>(ps.kinds());
        list.sort(Comparator.comparing((PetService.Kind k) -> !ps.owns(p, k)).thenComparing(k -> !ps.favorite(id, k))
                .thenComparing(PetService.Kind::name));
        int owned = (int) list.stream().filter(k -> ps.owns(p, k)).count();
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        String armed = ARMED.get(id);
        List<Pet> pets = new ArrayList<>();
        for (PetService.Kind k : list.subList(pg * PER_PAGE, Math.min(list.size(), (pg + 1) * PER_PAGE))) {
            boolean own = ps.owns(p, k), fav = own && ps.favorite(id, k), out = k.id().equals(activeId);
            String state;
            TextColor sc;
            Consumer<Player> click;
            if (own) {
                state = out ? "✔ gerufen · Klick: weg" : "Klick: rufen";
                sc = out ? GREEN : ROSE;
                click = out ? pl -> {
                    ps.dismiss(pl.getUniqueId());
                    open(pl, pg);
                } : pl -> {
                    String err = ps.summon(pl, k);
                    if (err != null) Msg.send(pl, "<red>" + err);
                    open(pl, pg);
                };
            } else if (k.jewels() > 0) {
                state = Msg.coins(k.jewels()) + " Juwelen";
                sc = JEWEL;
                click = pl -> {
                    pl.closeDialog();
                    pl.performCommand("juwelenshop pets");
                };
            } else if (k.price() > 0) {
                boolean sure = k.id().equals(armed);
                state = sure ? "Nochmal: kaufen!" : Msg.coins(k.price()) + " Coins";
                sc = GOLD;
                click = pl -> {
                    if (!k.id().equals(ARMED.get(pl.getUniqueId()))) {
                        ARMED.put(pl.getUniqueId(), k.id());
                    } else {
                        ARMED.remove(pl.getUniqueId());
                        String err = ps.buy(pl, k);
                        Msg.send(pl, err == null ? "<green>" + k.name() + " gehört jetzt dir!" : "<red>" + err);
                    }
                    open(pl, pg);
                };
            } else {
                state = k.source();
                sc = DIM;
                click = null;
            }
            pets.add(new Pet(own ? ps.displayName(id, k) : k.name(), sprite(k), egg(k), own, fav, out, state, sc, click,
                    !own ? null : pl -> {
                        ps.toggleFavorite(pl.getUniqueId(), k);
                        open(pl, pg);
                    },
                    !own ? null : pl -> {
                        pl.closeDialog();
                        addon.core().prompts().ask(pl, "Neuer Name für " + ps.displayName(pl.getUniqueId(), k) + "?", text -> {
                            String err = ps.rename(pl.getUniqueId(), k, text);
                            Msg.send(pl, err == null ? "<green>Dein Haustier heißt jetzt <gold>" + Msg.escape(text) : "<red>" + err);
                        });
                    }));
        }
        Active active = null;
        if (activeId != null) {
            PetService.Kind k = ps.kind(activeId);
            if (k != null) {
                active = new Active(ps.displayName(id, k), k.rideable(), pl -> {
                    pl.closeDialog();
                    String err = ps.ride(pl);
                    if (err != null) Msg.send(pl, "<red>" + err);
                }, pl -> {
                    ps.dismiss(pl.getUniqueId());
                    open(pl, pg);
                });
            }
        }
        UiDialog.show(p, canvas(new View(owned, list.size(), active, pets, pg, pages, n -> pl -> open(pl, n))), 330);
    }

    /** Selbsttest: volle Seite mit langen Namen, gerufenes Reittier; leer. */
    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        List<Pet> pets = new ArrayList<>();
        for (int i = 0; i < PER_PAGE; i++) {
            boolean own = i % 2 == 0;
            pets.add(new Pet("W".repeat(16), i % 4 == 0 ? KitData.Sprite.JSS_P_ANZUKI : null, KitData.Icon.EGG_WOLF, own, own, i == 0,
                    "W".repeat(30), GOLD, no, own ? no : null, own ? no : null));
        }
        reg.accept("Haustiere", () -> canvas(new View(9999, 9999, new Active("W".repeat(16), true, no, no), pets, 98, 99, n -> no)));
        reg.accept("Haustiere leer", () -> canvas(new View(0, 0, null, List.of(), 0, 1, n -> no)));
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Haustiere", PINK);
        c.box(0, 0, W, 54, KitData.Box.PETAL);
        c.icon(10, 11, KitData.Icon.LEAD, true);
        c.title(50, 12, "Haustiere", false, PINK);
        c.text(50, 36, "Rufen, reiten, umbenennen · ★ = Liebling", ROSE, false);
        c.textRight(378, 12, v.owned() + " / " + v.total(), CREAM, true);
        c.textRight(378, 24, Canvas.caps("gesammelt"), ROSE, false);
        int y = 60;
        if (v.active() != null) {
            c.box(0, y, W, 30, KitData.Box.PETAL_CARD);
            c.text(10, y + 11, Canvas.caps("Gerufen:"), ROSE, false);
            c.text(62, y + 11, Canvas.fit(Canvas.lit(v.active().name()), true, 170), CREAM, true);
            int x = W - 8;
            x -= 86;
            c.smallButton(x, y + 7, 86, KitData.Small.DARK, "Wegschicken", CREAM, false);
            c.action(x, y + 7, 86, KitData.SMALL_H, v.active().dismiss(), "Haustier wegschicken");
            if (v.active().rideable()) {
                x -= 78;
                c.smallButton(x, y + 7, 74, KitData.Small.GREEN, "Aufsteigen", WHITE, false);
                c.action(x, y + 7, 74, KitData.SMALL_H, v.active().ride(), "Aufsteigen (/pet ride)");
            }
            y += 30 + 6;
        }
        int cols = 4, cw = (W - 9) / cols, ch = 62;
        for (int i = 0; i < v.pets().size(); i++) {
            Pet pt = v.pets().get(i);
            int x = (i % cols) * (cw + 3), py = y + (i / cols) * (ch + 3);
            c.box(x, py, cw, ch, pt.out() ? KitData.Box.GOLD : pt.own() ? KitData.Box.PETAL_CARD : KitData.Box.GRAY);
            if (pt.sprite() != null) {
                c.sprite(x + (cw - pt.sprite().width) / 2, py + 4, pt.sprite(), Canvas.Z_ICON, pt.own() ? NamedTextColor.WHITE : LOCKED);
            } else {
                c.icon(x + (cw - 32) / 2, py + 3, pt.icon(), true, pt.own() ? NamedTextColor.WHITE : LOCKED);
            }
            String name = Canvas.fit(Canvas.lit(pt.name()), true, cw - 8);
            c.textCenter(x + cw / 2, py + 37, name, pt.own() ? CREAM : DIM, true);
            c.textCenter(x + cw / 2, py + 48, Canvas.fit(pt.state(), false, cw - 6), pt.stateColor(), false);
            if (pt.click() != null) c.action(x, py, cw, 34, pt.click(), pt.state());
            if (pt.favorite() != null) {
                c.smallButton(x + 3, py + 3, 14, pt.fav() ? KitData.Small.GOLD_ON : KitData.Small.DARK, "★", pt.fav() ? TextColor.color(0x3A2A06) : DIM, false);
                c.action(x + 3, py + 3, 14, KitData.SMALL_H, pt.favorite(), pt.fav() ? "Kein Liebling mehr" : "Als Liebling markieren");
            }
            if (pt.rename() != null) {
                c.smallButton(x + cw - 17, py + 3, 14, KitData.Small.DARK, "✎", CREAM, false);
                c.action(x + cw - 17, py + 3, 14, KitData.SMALL_H, pt.rename(), "Umbenennen");
            }
        }
        y += ((v.pets().size() + cols - 1) / cols) * (ch + 3) + 3;
        if (v.pages() > 1) {
            c.textCenter(W / 2, y + 4, "Seite " + (v.page() + 1) + " / " + v.pages(), ROSE, false);
            if (v.page() > 0) {
                c.smallButton(W / 2 - 90, y, 40, KitData.Small.DARK, "‹", CREAM, true);
                c.action(W / 2 - 90, y, 40, KitData.SMALL_H, v.goTo().apply(v.page() - 1), "Vorige Seite");
            }
            if (v.page() + 1 < v.pages()) {
                c.smallButton(W / 2 + 50, y, 40, KitData.Small.DARK, "›", CREAM, true);
                c.action(W / 2 + 50, y, 40, KitData.SMALL_H, v.goTo().apply(v.page() + 1), "Nächste Seite");
            }
        }
        return c;
    }
}
