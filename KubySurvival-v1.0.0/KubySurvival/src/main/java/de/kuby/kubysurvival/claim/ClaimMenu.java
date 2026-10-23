package de.kuby.kubysurvival.claim;

import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /claim: freie Claim-Blöcke, eigene Grundstücke (Rand zeigen), vertraute Spieler und Rechte für Fremde. */
public final class ClaimMenu extends GuiMenu {

    private static final String[][] FLAG_INFO = {
            {"tueren", "Türen, Falltüren, Zauntore"},
            {"knoepfe", "Knöpfe, Hebel, Druckplatten"},
            {"kisten", "Kisten, Fässer, Öfen"},
            {"tiere", "Tiere füttern, scheren, anleinen"}};
    private static final Material[] FLAG_ICONS = {Material.OAK_DOOR, Material.LEVER, Material.CHEST, Material.WHEAT};

    private final KubySurvival addon;

    public ClaimMenu(KubySurvival addon) {
        super(addon.core(), Msg.parse("<gold>Grundstücke"), 6);
        this.addon = addon;
    }

    @Override
    protected void build(Player p) {
        ClaimService claims = addon.claims();
        UUID id = p.getUniqueId();
        long perHour = plugin.getConfig().getLong("landclaim.blocks-per-hour", 100);
        set(4, Items.make(Material.GRASS_BLOCK, "<gold>" + claims.available(id) + " freie Claim-Blöcke", List.of(
                "<gray>Ein Grundstück kostet so viele", "<gray>Blöcke, wie Boden drin liegt.", "<gray>10×10 = 100 Blöcke.", "",
                "<gray>+" + perHour + " pro Stunde in Survival")), null);
        set(6, Items.make(Material.GOLDEN_AXE, "<yellow>Claim-Axt holen", List.of(
                "<gray>Linksklick: Ecke 1, Rechtsklick: Ecke 2,", "<gray>dann <white>/claim ok", "", "<yellow>Klick")), e -> {
            p.closeInventory();
            p.performCommand("claim axt");
        });
        set(2, Items.make(Material.BOOK, "<yellow>Hilfe", List.of("<gray>Alle Befehle zu Grundstücken", "", "<yellow>Klick")), e -> {
            p.closeInventory();
            p.performCommand("hilfe grundstuecke");
        });

        // Reihe 2: eigene Grundstücke
        List<ClaimService.Claim> own = claims.claimsOf(id);
        int max = plugin.getConfig().getInt("landclaim.max-claims", 10);
        if (own.isEmpty()) {
            set(13, Items.make(Material.BARRIER, "<gray>Noch kein Grundstück", List.of(
                    "<gray>Hol dir die Claim-Axt und", "<gray>stecke dein Land ab.")), null);
        }
        for (int i = 0; i < own.size() && i < 9; i++) {
            ClaimService.Claim c = own.get(i);
            int cx = (c.x1() + c.x2()) / 2, cz = (c.z1() + c.z2()) / 2;
            set(9 + i, Items.make(Material.OAK_FENCE, "<gold>Grundstück " + (i + 1), List.of(
                    "<gray>Größe: <white>" + c.width() + "×" + c.length() + " <gray>(" + c.area() + " Blöcke)",
                    "<gray>Mitte: <white>" + cx + ", " + cz, "", "<yellow>Klick: Rand anzeigen",
                    "<dark_gray>Löschen: drin stehen und /claim löschen")), e -> {
                p.closeInventory();
                addon.claimTool().show(p, c.x1(), c.z1(), c.x2(), c.z2());
                Msg.send(p, "<green>Der Rand wird 10 Sekunden angezeigt. <gray>(Mitte " + cx + ", " + cz + ")");
            });
        }
        set(18, Items.make(Material.PAPER, "<gray>" + own.size() + " / " + max + " Grundstücke", List.of()), null);

        // Reihe 4: vertraute Spieler
        List<UUID> trusted = new ArrayList<>(claims.trusted(id));
        set(27, Items.make(Material.NAME_TAG, "<green>Vertraute Spieler", List.of(
                "<gray>Dürfen auf allen deinen", "<gray>Grundstücken bauen.", "", "<white>/claim trust <Spieler>")), null);
        for (int i = 0; i < trusted.size() && i < 8; i++) {
            UUID t = trusted.get(i);
            OfflinePlayer op = Bukkit.getOfflinePlayer(t);
            String name = op.getName() == null ? "?" : op.getName();
            set(28 + i, Items.head(op, "<white>" + name, List.of("<gray>Darf bei dir bauen", "", "<red>Klick: Vertrauen entziehen")), e -> {
                claims.untrust(id, t);
                Msg.send(p, "<green>" + name + " darf auf deinen Grundstücken nicht mehr bauen.");
                open(p);
            });
        }

        // Reihe 5: Rechte für Fremde
        for (int i = 0; i < FLAG_INFO.length; i++) {
            String flag = FLAG_INFO[i][0];
            boolean on = claims.allows(id, flag);
            set(38 + i + (i >= 2 ? 1 : 0), Items.make(FLAG_ICONS[i], (on ? "<green>" : "<red>") + FLAG_INFO[i][1], List.of(
                    "<gray>Fremde dürfen das bei dir:", on ? "<green>erlaubt" : "<red>verboten", "", "<yellow>Klick zum Umschalten")), e -> {
                boolean now = claims.toggle(id, flag);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, now ? 1.4f : 0.9f);
                open(p);
            });
        }
        addBack(p);
    }
}
