package de.kuby.kubyskyblock.command;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/** /kp skyblock ... : Admin-Befehle für den Skyblock-Modus. */
public final class SkyblockCommand implements BiConsumer<Player, String[]> {

    private final KubySkyblock addon;
    private final NamespacedKey toolKey;

    public SkyblockCommand(KubySkyblock addon) {
        this.addon = addon;
        this.toolKey = new NamespacedKey(addon, "kp_sb_tool");
    }

    public boolean isTool(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    @Override
    public void accept(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Keine Berechtigung.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "tool" -> {
                ItemStack tool = new ItemStack(Material.STICK);
                ItemMeta meta = tool.getItemMeta();
                meta.displayName(Msg.parse("<aqua>Skyblock-Werkzeug"));
                meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
                tool.setItemMeta(meta);
                p.getInventory().addItem(tool);
                Msg.send(p, "<gray>Linksklick: Ecke 1, Rechtsklick: Ecke 2. Dann <white>/kp skyblock template save");
            }
            case "template" -> {
                if (args.length < 3 || !args[2].equalsIgnoreCase("save")) { help(p); return; }
                var c = addon.templateCorners(p.getUniqueId());
                if (c == null) { Msg.send(p, "<red>Markiere erst zwei Ecken mit dem Werkzeug."); return; }
                String err = addon.templates().save(c[0], c[1], p.getLocation());
                if (err != null) Msg.send(p, "<red>" + err);
                else { Sfx.play(addon.core(), p, Sfx.Kind.SUCCESS); Msg.send(p, "<green>Insel-Vorlage gespeichert."); }
            }
            case "loot" -> {
                if (args.length < 3 || !args[2].equalsIgnoreCase("generate")) { help(p); return; }
                int n = 10;
                if (args.length >= 4) { try { n = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) { } }
                int made = addon.loot().generate(p.getWorld(), Math.min(200, n));
                Msg.send(p, "<green>" + made + " Loot-Inseln erzeugt.");
            }
            case "oneblock" -> {
                if (args.length < 3 || !args[2].equalsIgnoreCase("setup")) { help(p); return; }
                int made = addon.oneblock().setup(p.getWorld());
                Msg.send(p, "<green>" + made + " One-Block-Plattformen aufgebaut.");
            }
            case "list" -> Msg.send(p, "<gray>Loot-Inseln: <white>" + addon.loot().all().size()
                    + " <dark_gray>| <gray>Inseln der Spieler: <white>" + addon.islands().count());
            default -> help(p);
        }
    }

    private void help(Player p) {
        Msg.send(p, "<gold>/kp skyblock tool <dark_gray>- <gray>Werkzeug holen");
        Msg.send(p, "<gold>/kp skyblock template save <dark_gray>- <gray>Insel-Vorlage aus den Ecken");
        Msg.send(p, "<gold>/kp skyblock loot generate [anzahl] <dark_gray>- <gray>Loot-Inseln erzeugen");
        Msg.send(p, "<gold>/kp skyblock oneblock setup <dark_gray>- <gray>One-Block-Plattformen aufbauen");
        Msg.send(p, "<gold>/kp skyblock list <dark_gray>- <gray>Übersicht");
    }
}
