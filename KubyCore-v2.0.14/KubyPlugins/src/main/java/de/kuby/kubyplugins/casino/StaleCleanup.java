package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Reste der Casino-Tische nach einem Neustart entfernen (v2.07, Fehlerbericht des Besitzers: „wenn der Server neu
 * startet, sind bei manchen Tischen die NPCs doppelt, das Doppelte hängt im Boden“).
 *
 * <p>Alles, was zu einem Tisch gehört (Filz, Chips, Karten, Texte, Klickflächen und die Dealer-Figur aus Einzelteilen),
 * wird beim Start neu aufgebaut und soll nie gespeichert werden. Landet trotzdem etwas im Spielstand (z. B. aus einer
 * älteren Version oder durch einen Absturz mitten im Aufbau), lädt es beim nächsten Start zusätzlich: die Dealer-Teile
 * dann ohne ihre Pose, alle Glieder in der Mitte der Füße, also halb im Boden. Was aus dem Spielstand geladen wird und
 * eine Tisch-Markierung oder ein Dealer-Modell trägt, ist darum ein Überbleibsel und wird entfernt; die echten Tische
 * stehen zu diesem Zeitpunkt schon neu da (neu gebaute Anzeigen lösen dieses Ereignis nicht aus).
 */
public final class StaleCleanup implements Listener {

    private final KubyPlugins plugin;
    private int removed;

    private final String ns;

    public StaleCleanup(KubyPlugins plugin) {
        this.plugin = plugin;
        this.ns = plugin.getName().toLowerCase(java.util.Locale.ROOT);
    }

    /** Beim Start: alles schon Geladene prüfen (vor dem Aufbau der Tische aufrufen). */
    public void sweep() {
        for (World w : plugin.getServer().getWorlds()) for (Entity e : w.getEntities()) check(e);
        if (removed > 0) plugin.getLogger().info("Casino: " + removed + " Reste alter Tisch-Anzeigen entfernt (Neustart).");
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        int before = removed;
        for (Entity en : e.getEntities()) check(en);
        if (removed > before) {
            plugin.getLogger().info("Casino: " + (removed - before) + " Reste alter Tisch-Anzeigen in Chunk "
                    + e.getChunk().getX() + "/" + e.getChunk().getZ() + " entfernt.");
        }
    }

    private void check(Entity e) {
        if (stale(e, ns)) {
            e.remove();
            removed++;
        }
    }

    /** Tisch-Teil: Markierung eines Tischs (Roulette, Poker, Blackjack) oder ein Teil der Dealer-Figur. */
    static boolean stale(Entity e, String ns) {
        for (NamespacedKey k : e.getPersistentDataContainer().getKeys()) {
            if (!k.getNamespace().equals(ns)) continue;
            String n = k.getKey();
            if (n.startsWith("roulette_table") || n.startsWith("kp_poker") || n.equals("bj_table")) return true;
        }
        if (e instanceof ItemDisplay d) {
            ItemStack it = d.getItemStack();
            if (it != null && !it.isEmpty() && it.hasData(DataComponentTypes.ITEM_MODEL)) {
                Key m = it.getData(DataComponentTypes.ITEM_MODEL);
                return m != null && m.namespace().equals("kuby") && m.value().startsWith("casino/rig_");
            }
        }
        return false;
    }
}
