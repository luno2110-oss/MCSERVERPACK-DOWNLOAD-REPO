package de.kp.jumprun.reward;

import de.kp.jumprun.KPJumpRun;
import de.kp.jumprun.reward.RewardManager.State;
import de.kp.jumprun.util.Text;
import de.kp.jumprun.util.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Belohnungsmenü, das sich per Rechtsklick auf den NPC öffnet.
 */
public final class RewardGui implements Listener {

    private static final int SIZE = 54;
    private static final int INFO_SLOT = 49;
    private static final int CLOSE_SLOT = 53;
    private static final int[] TIER_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};

    public static final class Holder implements InventoryHolder {
        private final String arena;
        private Inventory inventory;

        private Holder(String arena) {
            this.arena = arena;
        }

        public String arena() {
            return arena;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final KPJumpRun plugin;

    public RewardGui(KPJumpRun plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String arena) {
        Holder holder = new Holder(arena);
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                Text.mm("<color:#ffd166>" + Text.smallCaps("Belohnungen") + "</color>"));
        holder.inventory = inventory;
        render(player, holder);
        player.openInventory(inventory);
    }

    private void render(Player player, Holder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler());
        }

        List<RewardTier> tiers = plugin.rewards().tiers();
        for (int i = 0; i < tiers.size() && i < TIER_SLOTS.length; i++) {
            inventory.setItem(TIER_SLOTS[i], tierItem(player, holder.arena(), tiers.get(i)));
        }

        long best = plugin.rewards().bestMillis(player.getUniqueId(), holder.arena());
        inventory.setItem(INFO_SLOT, item(Material.CLOCK, "Deine Bestzeit",
                best < 0 ? "<gray>Noch keine Zeit</gray>" : "<white>" + TimeFormat.format(best) + "</white>",
                "<dark_gray>Arena: " + holder.arena() + "</dark_gray>"));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Schließen"));
    }

    private ItemStack tierItem(Player player, String arena, RewardTier tier) {
        State state = plugin.rewards().state(player.getUniqueId(), arena, tier);

        Material material = switch (state) {
            case LOCKED -> Material.GRAY_DYE;
            case AVAILABLE -> Material.LIME_DYE;
            case CLAIMED -> Material.ENCHANTED_BOOK;
        };
        String status = switch (state) {
            case LOCKED -> "<red>Noch nicht erreicht</red>";
            case AVAILABLE -> "<green>Klicke zum Abholen</green>";
            case CLAIMED -> "<gold>Bereits abgeholt</gold>";
        };

        List<String> lore = new ArrayList<>();
        lore.add(status);
        lore.add("<gray>Belohnung:</gray>");
        if (tier.gems() > 0) {
            lore.add("<aqua>" + tier.gems() + " Juwelen</aqua>");
        }
        lore.add("<yellow>" + tier.coins() + " Coins</yellow>");
        lore.add("<green>" + tier.xp() + " XP</green>");

        ItemStack item = item(material, "Zeit " + tier.label(), lore.toArray(new String[0]));
        if (state == State.AVAILABLE) {
            ItemMeta meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        int index = indexOfSlot(slot);
        List<RewardTier> tiers = plugin.rewards().tiers();
        if (index < 0 || index >= tiers.size()) {
            return;
        }

        RewardTier tier = tiers.get(index);
        RewardManager rewards = plugin.rewards();
        switch (rewards.state(player.getUniqueId(), holder.arena(), tier)) {
            case AVAILABLE -> {
                rewards.claim(player, holder.arena(), tier);
                Text.send(player, "Belohnung für <white>" + tier.label() + "</white> abgeholt!");
                player.playSound(player.getLocation(), "minecraft:entity.player.levelup",
                        SoundCategory.MASTER, 1f, 1f);
                render(player, holder);
            }
            case CLAIMED -> Text.send(player, "<gray>Diese Belohnung hast du schon abgeholt.</gray>");
            case LOCKED -> Text.send(player, "<red>Schaffe die Arena in " + tier.label()
                    + " oder schneller, um diese Belohnung zu bekommen.</red>");
        }
    }

    /** Der NPC kann nicht beschädigt werden, egal von wem oder wodurch. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.rewardNpc().isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Entity entity = event.getRightClicked();
        RewardNpc npc = plugin.rewardNpc();
        if (!npc.isNpc(entity)) {
            return;
        }

        event.setCancelled(true);
        open(event.getPlayer(), npc.arenaOf(entity));
    }

    private int indexOfSlot(int slot) {
        for (int i = 0; i < TIER_SLOTS.length; i++) {
            if (TIER_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static ItemStack item(Material material, String title, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(title));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Text.mm("<!italic>" + line));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }
}
