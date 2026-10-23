package de.kp.jumprun.run;

import de.kp.jumprun.KPJumpRun;
import de.kp.jumprun.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Die drei Run-Items in der Hotbar: Kopf (links), Feder (Mitte), roter Neustart (rechts).
 */
public final class RunItems {

    public static final int HEAD_SLOT = 0;
    public static final int FEATHER_SLOT = 4;
    public static final int RESTART_SLOT = 8;
    public static final int QUIT_SLOT = 6;
    public static final int[] SLOTS = {HEAD_SLOT, FEATHER_SLOT, QUIT_SLOT, RESTART_SLOT};

    private final NamespacedKey headKey;
    private final NamespacedKey featherKey;
    private final NamespacedKey restartKey;
    private final NamespacedKey quitKey;

    public RunItems(KPJumpRun plugin) {
        this.headKey = new NamespacedKey(plugin, "run_head");
        this.featherKey = new NamespacedKey(plugin, "run_feather");
        this.restartKey = new NamespacedKey(plugin, "run_restart");
        this.quitKey = new NamespacedKey(plugin, "run_quit");
    }

    public ItemStack feather() {
        ItemStack item = new ItemStack(Material.FEATHER);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(featherKey, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Text.item("Feder"));
        meta.lore(List.of(
                Text.mm("<!italic><gray>Einmaliger Boost in Blickrichtung</gray>"),
                Text.mm("<!italic><dark_gray>Rechtsklick zum Benutzen</dark_gray>")));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack restart() {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(restartKey, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Text.item("Neustart"));
        meta.lore(List.of(
                Text.mm("<!italic><gray>Startet den Run neu</gray>"),
                Text.mm("<!italic><dark_gray>Rechtsklick zum Benutzen</dark_gray>")));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Kopf des Spielers, der im Rang direkt über dir steht.
     * Ist owner null, wird ein neutraler Kopf angezeigt.
     */
    public ItemStack head(OfflinePlayer owner, String name, String loreLine) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (owner != null) {
            meta.setOwningPlayer(owner);
        }
        meta.getPersistentDataContainer().set(headKey, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Text.mm("<!italic><white>" + name + "</white>"));
        meta.lore(List.of(Text.mm("<!italic>" + loreLine)));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack quit() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(quitKey, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Text.item("Run beenden"));
        meta.lore(List.of(
                Text.mm("<!italic><gray>Beendet den Run ohne Fail</gray>"),
                Text.mm("<!italic><dark_gray>Rechtsklick zum Benutzen</dark_gray>")));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isQuit(ItemStack item) {
        return has(item, quitKey);
    }

    public boolean isFeather(ItemStack item) {
        return has(item, featherKey);
    }

    public boolean isRestart(ItemStack item) {
        return has(item, restartKey);
    }

    public boolean isRunItem(ItemStack item) {
        return has(item, headKey) || has(item, featherKey) || has(item, restartKey) || has(item, quitKey);
    }

    private boolean has(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
