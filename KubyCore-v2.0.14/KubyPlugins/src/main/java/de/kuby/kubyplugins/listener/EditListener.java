package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.EditRodMenu;
import de.kuby.kubyplugins.util.Locs;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Klicks im Bearbeitungsmodus und der Einstieg über den Admin-Button in der Lobby. */
public final class EditListener implements Listener {

    private final KubyPlugins plugin;

    public EditListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack hand = e.getItem();
        Action a = e.getAction();
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;

        if (!plugin.edit().isEditing(p.getUniqueId())) {
            if (hand != null && hand.getType() == Material.NETHER_STAR && right
                    && p.hasPermission("kuby.admin") && plugin.modes().isHub(p.getWorld())) {
                e.setCancelled(true);
                String err = plugin.edit().enter(p);
                if (err != null) Msg.send(p, "<red>" + err);
            }
            return;
        }

        e.setCancelled(true);
        if (hand == null) return;
        boolean sneak = p.isSneaking();
        switch (hand.getType()) {
            case BLAZE_ROD -> rod(p, e.getClickedBlock(), left, right, sneak);
            case REDSTONE_TORCH -> {
                if (sneak && left) plugin.edit().cycleColor(p);
            }
            case BARRIER -> plugin.edit().exit(p);
            default -> { }
        }
    }

    private void rod(Player p, Block block, boolean left, boolean right, boolean sneak) {
        if (sneak && right) {
            new EditRodMenu(plugin).open(p);
            return;
        }
        if (block == null) return;
        Location loc = block.getLocation();
        switch (plugin.edit().action(p.getUniqueId())) {
            case "spawn" -> {
                if (!right) return;
                Location spawn = loc.clone().add(0.5, 1, 0.5);
                spawn.setYaw(p.getLocation().getYaw());
                spawn.setPitch(0);
                plugin.getConfig().set("modes.hub.spawn", Locs.serialize(spawn));
                plugin.saveConfig();
                Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                Msg.send(p, "<green>Hub-Spawn gesetzt.");
            }
            case "region" -> {
                if (left) plugin.edit().setCorner(p, 1, loc);
                else if (right) plugin.edit().setCorner(p, 2, loc);
            }
            case "marker" -> {
                if (!right) return;
                String color = plugin.edit().color(p.getUniqueId());
                Location target = loc.clone();
                plugin.prompts().ask(p, "Text für den Marker eingeben:", text -> {
                    if (text.isBlank()) {
                        Msg.send(p, "<red>Kein Text. Marker abgebrochen.");
                        return;
                    }
                    plugin.markers().create(target, color, text);
                    Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                    Msg.send(p, "<green>Marker erstellt.");
                });
            }
            default -> { }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (plugin.edit().isEditing(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }
}
