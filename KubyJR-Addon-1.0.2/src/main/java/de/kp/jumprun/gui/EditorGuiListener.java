package de.kp.jumprun.gui;

import de.kp.jumprun.KPJumpRun;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class EditorGuiListener implements Listener {

    private final KPJumpRun plugin;
    private final EditorGui gui;

    public EditorGuiListener(KPJumpRun plugin, EditorGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EditorHolder holder)) {
            return;
        }

        // Im Editor darf nichts verschoben werden
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        gui.handleClick(player, holder, event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EditorHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        EditorGui.Prompt prompt = gui.takePrompt(player.getUniqueId());
        if (prompt == null) {
            return;
        }

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> gui.applyPrompt(player, prompt, input));
    }
}
