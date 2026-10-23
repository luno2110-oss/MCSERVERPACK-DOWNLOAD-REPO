package de.kuby.kubysurvival.farmpass;

import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /farmpass: Status, alle Vorteile, Fliegen, Wochenkiste, Haustiere, Werkbank, Store-Link. */
public final class FarmpassMenu extends GuiMenu {

    private final KubySurvival addon;

    public FarmpassMenu(KubySurvival addon) {
        super(addon.core(), Msg.parse("<dark_green>☘ Farmpass"), 6);
        this.addon = addon;
    }

    @Override
    protected void build(Player p) {
        FarmpassService fp = addon.farmpass();
        UUID id = p.getUniqueId();
        boolean active = fp.active(id);
        var cfg = addon.core().getConfig();
        String until = active ? DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(Instant.ofEpochMilli(fp.until(id)).atZone(ZoneId.of("Europe/Berlin"))) : "";
        String url = cfg.getString("farmpass.store-url", "");
        set(4, Items.make(active ? Material.LIME_DYE : Material.GRAY_DYE,
                active ? "<green><b>☘ Farmpass aktiv</b>" : "<gray><b>Kein Farmpass</b>", List.of(
                        active ? "<gray>Läuft bis <white>" + until : "<gray>Nur im Store: <white>" + cfg.getString("farmpass.price-text", ""),
                        "<gray>Bezahlte Monate: <white>" + fp.months(id), "",
                        url.isEmpty() ? "" : "<yellow>Klick: Store-Link im Chat")), e -> {
            if (url.isEmpty()) return;
            p.closeInventory();
            Msg.send(p, "<green>Farmpass im Store: <click:open_url:'" + url + "'><u><aqua>" + url + "</aqua></u></click>");
        });

        String[][] perks = {
                {"FEATHER", "Fliegen", "Am Spawn, im Casino und auf deinen", "und den Grundstücken deiner Freunde"},
                {"IRON_HOE", "Nebenjob", "Ein zweiter Job, der mitzahlt", "/job nebenjob <Beruf>"},
                {"GOLD_NUGGET", "Keine Gebühren", "Keine Steuer im Markt", "und keine Casino-Steuer"},
                {"RED_BED", "+" + cfg.getInt("farmpass.extra-homes", 2) + " Homes", "Mehr Plätze für Homes", ""},
                {"CHEST", "Mehr Markt-Plätze", cfg.getInt("farmpass.market-listings", 15) + " Angebote gleichzeitig", ""},
                {"CRAFTING_TABLE", "/werkbank überall", "Werkbank immer dabei", ""},
                {"ENDER_CHEST", "Wochenkiste", "Jede Woche Coins, Tokens", "und nützliche Items"},
                {"LEAD", "Haustiere", "4 Haustiere nur für den Pass,", "dazu eins pro Monat für immer"},
                {"GRASS_BLOCK", "+" + cfg.getLong("farmpass.claim-blocks-per-month", 250) + " Claim-Blöcke", "pro bezahltem Monat,", "bleiben für immer"},
                {"LILY_PAD", "Grünes Blatt", "☘ vor deinem Namen", "in Tab und Chat"}};
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
        for (int i = 0; i < perks.length; i++) {
            String[] k = perks[i];
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + k[2]);
            if (!k[3].isEmpty()) lore.add("<gray>" + k[3]);
            set(slots[i], Items.make(Material.valueOf(k[0]), (active ? "<green>" : "<gray>") + k[1], lore), null);
        }

        if (active) {
            boolean fly = fp.flyOn(id);
            set(37, Items.make(fly ? Material.ELYTRA : Material.FEATHER, "<white>Fliegen: " + (fly ? "<green>an" : "<red>aus"),
                    List.of("<gray>/farmpass fly", "", "<yellow>Klick zum Umschalten")), e -> {
                fp.toggleFly(id);
                open(p);
            });
            boolean weekly = fp.weeklyReady(id);
            set(39, Items.make(weekly ? Material.ENDER_CHEST : Material.CHEST, weekly ? "<gold>Wochenkiste abholen" : "<gray>Wochenkiste",
                    List.of(weekly ? "<yellow>Klick zum Öffnen" : "<gray>Nächste ab Montag")), e -> {
                String err = fp.claimWeekly(p);
                if (err != null) Msg.send(p, "<red>" + err);
                open(p);
            });
            set(41, Items.make(Material.CRAFTING_TABLE, "<white>Werkbank öffnen", List.of("<gray>/werkbank", "", "<yellow>Klick")),
                    e -> p.openWorkbench(null, true));
        }
        int picks = fp.petPicksLeft(id);
        set(43, Items.make(Material.LEAD, "<gold>Haustiere", List.of(
                "<gray>Deine Monats-Haustiere: <white>" + fp.chosenPets(id).size(),
                picks > 0 ? "<green>" + picks + " Auswahl offen!" : "<gray>Keine Auswahl offen", "", "<yellow>Klick")),
                e -> new FarmpassPetMenu(addon).open(p));
        addBack(p);
    }
}
