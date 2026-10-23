package de.kuby.kubysurvival.quests;

import de.kuby.kubysurvival.KubySurvival;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Quest-Aktionen, die nicht schon über die Jobs gemeldet werden: Craften, Schmelzen, Essen, Handeln.
 * Abbauen, Jagen, Angeln, Züchten, Scheren und Verzaubern meldet der JobListener (mit Schutz gegen gesetzte Blöcke).
 * Dazu jede Minute Online-Zeit und Werte-Quests.
 */
public final class QuestListener implements Listener {

    private final KubySurvival addon;

    public QuestListener(KubySurvival addon) {
        this.addon = addon;
        addon.getServer().getScheduler().runTaskTimer(addon, () -> {
            for (Player p : addon.getServer().getOnlinePlayers()) {
                addon.quests().progress(p, "playtime", null, 1);
                addon.quests().checkStats(p);
            }
        }, 1200L, 1200L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack result = e.getRecipe().getResult();
        int crafts = 1;
        if (e.isShiftClick()) {
            // Mit Shift wird so oft gecraftet, wie die knappste Zutat reicht.
            crafts = Integer.MAX_VALUE;
            for (ItemStack it : e.getInventory().getMatrix()) {
                if (it != null && it.getType() != Material.AIR) crafts = Math.min(crafts, it.getAmount());
            }
            if (crafts == Integer.MAX_VALUE) crafts = 1;
        }
        addon.quests().progress(p, "craft", result.getType(), (long) crafts * result.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmelt(FurnaceExtractEvent e) {
        addon.quests().progress(e.getPlayer(), "smelt", e.getItemType(), e.getItemAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (e.getItem().getType().isEdible()) addon.quests().progress(e.getPlayer(), "eat", e.getItem().getType(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent e) {
        addon.quests().progress(e.getPlayer(), "trade", null, 1);
    }
}
