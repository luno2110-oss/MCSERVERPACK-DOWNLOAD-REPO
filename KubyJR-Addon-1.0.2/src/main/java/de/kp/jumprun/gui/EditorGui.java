package de.kp.jumprun.gui;

import de.kp.jumprun.KPJumpRun;
import de.kp.jumprun.arena.Arena;
import de.kp.jumprun.editor.EditorStick;
import de.kp.jumprun.gui.EditorHolder.Screen;
import de.kp.jumprun.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Die Config-GUI über /kp parkour editor: Start, Ziel, Checkpoints, Texte und Sounds.
 */
public final class EditorGui {

    public record Prompt(String arena, String key) {
    }

    private static final int SIZE = 27;
    private static final String NONE = "none";

    private static final List<String> SOUND_PRESETS = List.of(
            NONE,
            "minecraft:ui.button.click",
            "minecraft:block.note_block.pling",
            "minecraft:block.note_block.bell",
            "minecraft:entity.experience_orb.pickup",
            "minecraft:entity.player.levelup",
            "minecraft:ui.toast.challenge_complete",
            "minecraft:entity.villager.no",
            "minecraft:entity.firework_rocket.blast");

    private static final Map<String, String> LABELS = Map.of(
            "start", "Start",
            "checkpoint", "Checkpoint",
            "finish", "Ziel",
            "fail-start", "Fail (Start)",
            "fail-checkpoint", "Fail (Checkpoint)");

    private final KPJumpRun plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public EditorGui(KPJumpRun plugin) {
        this.plugin = plugin;
    }

    // ---------- Öffnen & Zeichnen ----------

    public void open(Player player, Screen screen, String arenaName) {
        EditorHolder holder = new EditorHolder(screen, arenaName);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Text.mm(title(screen)));
        holder.setInventory(inventory);
        render(holder);
        player.openInventory(inventory);
    }

    private void render(EditorHolder holder) {
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }

        inventory.clear();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler());
        }

        Arena arena = plugin.arenas().get(holder.arena());
        if (arena == null) {
            return;
        }

        switch (holder.screen()) {
            case MAIN -> renderMain(inventory, arena);
            case POINTS -> renderPoints(inventory, arena);
            case TEXTS -> renderTexts(inventory, arena);
            case SOUNDS -> renderSounds(inventory, arena);
        }
    }

    private void renderMain(Inventory inv, Arena arena) {
        inv.setItem(4, item(Material.COMPASS, "Arena " + arena.getName(),
                "<gray>Start: " + status(arena.getStart()) + "</gray>",
                "<gray>Ziel: " + status(arena.getFinish()) + "</gray>",
                "<gray>Checkpoints: <white>" + arena.getCheckpoints().size() + "</white></gray>"));
        inv.setItem(11, item(Material.ENDER_PEARL, "Start & Ziel",
                "<gray>Start, Ziel und Checkpoints setzen</gray>"));
        inv.setItem(13, item(Material.WRITABLE_BOOK, "Texte",
                "<gray>Nachrichten für Start, Ziel und Fails</gray>"));
        inv.setItem(15, item(Material.NOTE_BLOCK, "Sounds",
                "<gray>Sound für jedes Ereignis</gray>"));
        inv.setItem(22, item(Material.BLAZE_ROD, "Editor Stick",
                "<gray>Klicken, um den Editor Stick zu erhalten</gray>"));
        inv.setItem(26, item(Material.BARRIER, "Schließen"));
    }

    private void renderPoints(Inventory inv, Arena arena) {
        inv.setItem(10, item(Material.LIME_CONCRETE, "Start setzen",
                "<gray>Aktuell: " + status(arena.getStart()) + "</gray>",
                "<dark_gray>Auf dem Block stehen, dann klicken</dark_gray>"));
        inv.setItem(11, item(Material.RED_CONCRETE, "Ziel setzen",
                "<gray>Aktuell: " + status(arena.getFinish()) + "</gray>",
                "<dark_gray>Auf dem Block stehen, dann klicken</dark_gray>"));
        inv.setItem(12, item(Material.YELLOW_CONCRETE, "Checkpoint hinzufügen",
                "<gray>Anzahl: <white>" + arena.getCheckpoints().size() + "</white></gray>",
                "<dark_gray>Auf dem Block stehen, dann klicken</dark_gray>"));
        inv.setItem(13, item(Material.ORANGE_CONCRETE, "Letzten Checkpoint entfernen",
                "<dark_gray>Entfernt den zuletzt gesetzten Checkpoint</dark_gray>"));
        inv.setItem(14, item(Material.GRAY_CONCRETE, "Alle Checkpoints entfernen",
                "<dark_gray>Löscht alle Checkpoints dieser Arena</dark_gray>"));
        inv.setItem(15, item(Material.RECOVERY_COMPASS, "Rückkehr-Punkt setzen",
                "<gray>Aktuell: " + status(arena.getExit()) + "</gray>",
                "<dark_gray>Hier stehst du nach dem Erreichen des Ziels</dark_gray>"));
        inv.setItem(16, item(Material.ENDER_PEARL, "Zum Start teleportieren"));
        inv.setItem(17, item(Material.ENDER_EYE, "Zum Ziel teleportieren"));
        inv.setItem(18, item(Material.ARROW, "Zurück"));
        inv.setItem(26, item(Material.BARRIER, "Schließen"));
    }

    private void renderTexts(Inventory inv, Arena arena) {
        for (int i = 0; i < Arena.TEXT_KEYS.size(); i++) {
            String key = Arena.TEXT_KEYS.get(i);
            inv.setItem(10 + i, item(Material.PAPER, LABELS.get(key),
                    "<gray>Aktuell: " + arena.text(key) + "</gray>",
                    "<dark_gray>Platzhalter: {time} {index} {total} {arena}</dark_gray>",
                    "<dark_gray>Linksklick: im Chat ändern</dark_gray>",
                    "<dark_gray>Rechtsklick: Standard</dark_gray>"));
        }
        inv.setItem(18, item(Material.ARROW, "Zurück"));
        inv.setItem(26, item(Material.BARRIER, "Schließen"));
    }

    private void renderSounds(Inventory inv, Arena arena) {
        for (int i = 0; i < Arena.TEXT_KEYS.size(); i++) {
            String key = Arena.TEXT_KEYS.get(i);
            inv.setItem(10 + i, item(Material.NOTE_BLOCK, LABELS.get(key),
                    "<gray>Aktuell: <white>" + arena.soundSetting(key) + "</white></gray>",
                    "<dark_gray>Linksklick: nächster Sound</dark_gray>",
                    "<dark_gray>Rechtsklick: vorheriger Sound</dark_gray>"));
        }
        inv.setItem(18, item(Material.ARROW, "Zurück"));
        inv.setItem(26, item(Material.BARRIER, "Schließen"));
    }

    // ---------- Klicks ----------

    public void handleClick(Player player, EditorHolder holder, InventoryClickEvent event) {
        Arena arena = plugin.arenas().get(holder.arena());
        if (arena == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();
        switch (holder.screen()) {
            case MAIN -> handleMain(player, arena, slot);
            case POINTS -> handlePoints(player, arena, slot, holder);
            case TEXTS -> handleTexts(player, arena, slot, event.isRightClick(), holder);
            case SOUNDS -> handleSounds(player, arena, slot, event.isRightClick(), holder);
        }
    }

    private void handleMain(Player player, Arena arena, int slot) {
        switch (slot) {
            case 11 -> open(player, Screen.POINTS, arena.getName());
            case 13 -> open(player, Screen.TEXTS, arena.getName());
            case 15 -> open(player, Screen.SOUNDS, arena.getName());
            case 22 -> giveStick(player, arena);
            case 26 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handlePoints(Player player, Arena arena, int slot, EditorHolder holder) {
        boolean changed = false;

        switch (slot) {
            case 10 -> {
                arena.setStart(blockUnder(player));
                Text.send(player, "Start gesetzt.");
                changed = true;
            }
            case 11 -> {
                arena.setFinish(blockUnder(player));
                Text.send(player, "Ziel gesetzt.");
                changed = true;
            }
            case 12 -> {
                arena.getCheckpoints().add(blockUnder(player));
                Text.send(player, "Checkpoint <yellow>#" + arena.getCheckpoints().size() + "</yellow> gesetzt.");
                changed = true;
            }
            case 13 -> {
                if (arena.getCheckpoints().isEmpty()) {
                    Text.send(player, "<red>Keine Checkpoints vorhanden.</red>");
                } else {
                    arena.getCheckpoints().remove(arena.getCheckpoints().size() - 1);
                    Text.send(player, "Letzter Checkpoint entfernt.");
                    changed = true;
                }
            }
            case 14 -> {
                arena.getCheckpoints().clear();
                Text.send(player, "Alle Checkpoints entfernt.");
                changed = true;
            }
            case 15 -> {
                arena.setExit(player.getLocation().clone());
                Text.send(player, "Rückkehr-Punkt gesetzt.");
                changed = true;
            }
            case 16 -> teleport(player, arena.getStart());
            case 17 -> teleport(player, arena.getFinish());
            case 18 -> open(player, Screen.MAIN, arena.getName());
            case 26 -> player.closeInventory();
            default -> {
            }
        }

        if (changed) {
            plugin.arenas().save();
            render(holder);
        }
    }

    private void handleTexts(Player player, Arena arena, int slot, boolean right, EditorHolder holder) {
        if (slot == 18) {
            open(player, Screen.MAIN, arena.getName());
            return;
        }
        if (slot == 26) {
            player.closeInventory();
            return;
        }

        int index = slot - 10;
        if (index < 0 || index >= Arena.TEXT_KEYS.size()) {
            return;
        }

        String key = Arena.TEXT_KEYS.get(index);
        if (right) {
            arena.setText(key, null);
            plugin.arenas().save();
            render(holder);
            Text.send(player, "Standardtext für <white>" + LABELS.get(key) + "</white> wiederhergestellt.");
        } else {
            startPrompt(player, arena.getName(), key);
        }
    }

    private void handleSounds(Player player, Arena arena, int slot, boolean right, EditorHolder holder) {
        if (slot == 18) {
            open(player, Screen.MAIN, arena.getName());
            return;
        }
        if (slot == 26) {
            player.closeInventory();
            return;
        }

        int index = slot - 10;
        if (index < 0 || index >= Arena.TEXT_KEYS.size()) {
            return;
        }

        String key = Arena.TEXT_KEYS.get(index);
        int size = SOUND_PRESETS.size();
        int current = Math.max(0, SOUND_PRESETS.indexOf(arena.soundSetting(key)));
        int next = right ? (current - 1 + size) % size : (current + 1) % size;

        arena.setSound(key, SOUND_PRESETS.get(next));
        plugin.arenas().save();
        render(holder);

        // Vorhören
        String sound = arena.sound(key);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 1f, 1f);
        }
    }

    // ---------- Chat-Eingabe ----------

    public void startPrompt(Player player, String arena, String key) {
        prompts.put(player.getUniqueId(), new Prompt(arena, key));
        player.closeInventory();
        Text.send(player, "Schreibe den neuen Text für <yellow>" + LABELS.get(key) + "</yellow> in den Chat.");
        Text.send(player, "Platzhalter: <white>{time} {index} {total} {arena}</white> · abbrechen mit <white>abbrechen</white>");
    }

    /** Wird vom Chat-Listener abgeholt, ist thread-sicher. */
    public Prompt takePrompt(UUID playerId) {
        return prompts.remove(playerId);
    }

    /** Läuft auf dem Hauptthread. */
    public void applyPrompt(Player player, Prompt prompt, String input) {
        Arena arena = plugin.arenas().get(prompt.arena());
        if (arena == null) {
            return;
        }

        if (input.equalsIgnoreCase("abbrechen")) {
            Text.send(player, "Abgebrochen.");
        } else if (input.isEmpty() || input.length() > 200) {
            Text.send(player, "<red>Der Text muss zwischen 1 und 200 Zeichen lang sein.</red>");
        } else {
            arena.setText(prompt.key(), input);
            plugin.arenas().save();
            Text.send(player, "Text gespeichert.");
        }

        open(player, Screen.TEXTS, arena.getName());
    }

    // ---------- Hilfsmethoden ----------

    private void giveStick(Player player, Arena arena) {
        ItemStack stick = plugin.editorStick().create(arena.getName(), EditorStick.Mode.START_FINISH);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stick);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        Text.send(player, "Du hast den <yellow>Editor Stick</yellow> erhalten.");
    }

    private void teleport(Player player, Location location) {
        if (location == null) {
            Text.send(player, "<red>Dieser Punkt ist noch nicht gesetzt.</red>");
            return;
        }
        player.teleport(location.clone().add(0.5, 1, 0.5));
    }

    /** Der Block, auf dem der Spieler steht. */
    private static Location blockUnder(Player player) {
        return player.getLocation().clone().subtract(0, 0.1, 0).getBlock().getLocation();
    }

    private static String status(Location location) {
        if (location == null) {
            return "<red>fehlt</red>";
        }
        return "<green>" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + "</green>";
    }

    private static String title(Screen screen) {
        String name = switch (screen) {
            case MAIN -> "Parkour Editor";
            case POINTS -> "Start & Ziel";
            case TEXTS -> "Texte";
            case SOUNDS -> "Sounds";
        };
        return "<color:#22d3ee>" + Text.smallCaps(name) + "</color>";
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
