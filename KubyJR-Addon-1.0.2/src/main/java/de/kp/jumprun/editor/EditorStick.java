package de.kp.jumprun.editor;

import de.kp.jumprun.KPJumpRun;
import de.kp.jumprun.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Der Editor Stick (verzauberte Blaze Rod) zum Setzen von Start, Ziel und Checkpoints.
 */
public final class EditorStick {

    public enum Mode {
        START_FINISH("Start & Ziel",
                "<green>Rechtsklick</green> <gray>→ Start setzen</gray>",
                "<green>Linksklick</green> <gray>→ Ziel setzen</gray>"),
        CHECKPOINT("Checkpoints",
                "<green>Rechtsklick</green> <gray>→ Checkpoint hinzufügen</gray>",
                "<green>Linksklick</green> <gray>→ letzten Checkpoint entfernen</gray>"),
        EXIT("Rückkehr-Punkt",
                "<green>Rechtsklick</green> <gray>→ Rückkehr-Punkt hier setzen</gray>",
                "<dark_gray>Linksklick → nichts</dark_gray>");

        private final String label;
        private final String rightHint;
        private final String leftHint;

        Mode(String label, String rightHint, String leftHint) {
            this.label = label;
            this.rightHint = rightHint;
            this.leftHint = leftHint;
        }

        public String label() {
            return label;
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private final NamespacedKey arenaKey;
    private final NamespacedKey modeKey;

    public EditorStick(KPJumpRun plugin) {
        this.arenaKey = new NamespacedKey(plugin, "editor_arena");
        this.modeKey = new NamespacedKey(plugin, "editor_mode");
    }

    public ItemStack create(String arena, Mode mode) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(arenaKey, PersistentDataType.STRING, arena);
        pdc.set(modeKey, PersistentDataType.STRING, mode.name());

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);
        meta.displayName(Text.item("Editor Stick"));
        meta.lore(lore(arena, mode));

        item.setItemMeta(meta);
        return item;
    }

    public boolean isStick(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(arenaKey, PersistentDataType.STRING);
    }

    public String getArena(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(arenaKey, PersistentDataType.STRING);
    }

    public Mode getMode(ItemStack item) {
        String raw = item.getItemMeta().getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        try {
            return raw == null ? Mode.START_FINISH : Mode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Mode.START_FINISH;
        }
    }

    private List<Component> lore(String arena, Mode mode) {
        return List.of(
                Text.mm("<!italic><dark_gray>Arena: <white>" + arena + "</white></dark_gray>"),
                Text.mm("<!italic><dark_gray>Modus: <yellow>" + mode.label() + "</yellow></dark_gray>"),
                Component.empty(),
                Text.mm("<!italic>" + mode.rightHint),
                Text.mm("<!italic>" + mode.leftHint),
                Text.mm("<!italic><dark_gray>Sneak + Rechtsklick → Modus wechseln</dark_gray>")
        );
    }
}
