package de.kp.jumprun.gui;

import de.kp.jumprun.KPJumpRun;
import de.kp.jumprun.arena.Arena;
import de.kp.jumprun.holo.Holo;
import de.kp.jumprun.holo.HoloManager;
import de.kp.jumprun.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Zentrale Einstellungs-GUI über /kp parkour config.
 * Alle Werte wirken sofort, Texteingaben laufen über den Chat.
 */
public final class ConfigGui implements Listener {

    public enum Screen { MAIN, GENERAL, REWARDS, HOLOS, HOLO, NPCS, ARENAS }

    public static final class Holder implements InventoryHolder {
        private final Screen screen;
        private final String param;
        private Inventory inventory;

        private Holder(Screen screen, String param) {
            this.screen = screen;
            this.param = param;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final String DEFAULT_PREFIX = "<dark_gray>[</dark_gray><aqua>KP</aqua><dark_gray>]</dark_gray> ";

    private static final String CYCLE_HINT = "<dark_gray>Linksklick: erhöhen · Rechtsklick: verringern</dark_gray>";
    private static final String TEXT_HINT = "<dark_gray>Linksklick: im Chat ändern · Rechtsklick: Standard</dark_gray>";

    /** Plätze für Listen (bis zu 28 Einträge) */
    private static final int[] LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private final KPJumpRun plugin;
    /** Wartende Chat-Eingaben, Schlüssel ist die Spieler-UUID */
    private final Map<UUID, Consumer<String>> inputs = new ConcurrentHashMap<>();

    public ConfigGui(KPJumpRun plugin) {
        this.plugin = plugin;
    }

    // ---------- Öffnen & Zeichnen ----------

    public void open(Player player, Screen screen, String param) {
        Holder holder = new Holder(screen, param);
        Inventory inventory = Bukkit.createInventory(holder, size(screen), Text.mm(title(screen, param)));
        holder.inventory = inventory;
        render(player, holder);
        player.openInventory(inventory);
    }

    private void render(Player player, Holder holder) {
        Inventory inv = holder.getInventory();
        inv.clear();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler());
        }

        switch (holder.screen) {
            case MAIN -> renderMain(inv);
            case GENERAL -> renderGeneral(inv);
            case REWARDS -> renderRewards(inv);
            case HOLOS -> renderHolos(inv);
            case HOLO -> renderHolo(inv, holder.param);
            case NPCS -> renderNpcs(inv);
            case ARENAS -> renderArenas(inv);
        }

        inv.setItem(inv.getSize() - 1, item(Material.BARRIER, "Schließen"));
        if (parent(holder.screen) != null) {
            inv.setItem(backSlot(inv.getSize()), item(Material.ARROW, "Zurück"));
        }
    }

    private void renderMain(Inventory inv) {
        inv.setItem(11, item(Material.COMPARATOR, "Allgemein", "<gray>Fall, Feder, Bestenliste, Prefix</gray>"));
        inv.setItem(13, item(Material.GOLD_INGOT, "Belohnungen", "<gray>Befehle für Coins und Juwelen</gray>"));
        inv.setItem(15, item(Material.ITEM_FRAME, "Hologramme", "<gray>Bestenlisten verwalten</gray>"));
        inv.setItem(21, item(Material.VILLAGER_SPAWN_EGG, "NPCs", "<gray>Belohnungs-NPCs verwalten</gray>"));
        inv.setItem(23, item(Material.MAP, "Arenen", "<gray>Arenen auswählen und bearbeiten</gray>"));
    }

    private void renderGeneral(Inventory inv) {
        var cfg = plugin.getConfig();
        inv.setItem(10, item(Material.PISTON, "Fall-Reset",
                "<gray>Aktuell: <white>" + cfg.getInt("fall.reset-blocks", 2) + " Blöcke</white></gray>",
                CYCLE_HINT));
        inv.setItem(12, item(Material.FEATHER, "Feder-Boost",
                "<gray>Aktuell: <white>" + cfg.getDouble("feather.boost-blocks", 3.0) + " Blöcke</white></gray>",
                CYCLE_HINT));
        inv.setItem(14, item(Material.OAK_SIGN, "Bestenliste: Zeilen",
                "<gray>Aktuell: <white>" + cfg.getInt("leaderboard.visible-rows", 10) + "</white></gray>",
                CYCLE_HINT));
        inv.setItem(16, item(Material.NAME_TAG, "Prefix",
                "<gray>Aktuell:</gray> " + cfg.getString("messages-prefix", DEFAULT_PREFIX),
                TEXT_HINT));
        inv.setItem(20, item(Material.HOPPER, "Config neu laden",
                "<dark_gray>Liest config.yml erneut ein</dark_gray>"));
    }

    private void renderRewards(Inventory inv) {
        inv.setItem(11, item(Material.GOLD_NUGGET, "Coins",
                "<gray>Werden direkt auf dein Kontostand gutgeschrieben.</gray>",
                "<dark_gray>Kommen aus KubyCore</dark_gray>"));
        inv.setItem(13, item(Material.EMERALD, "Juwelen",
                "<gray>Werden direkt auf dein Juwelen-Konto gutgeschrieben.</gray>",
                "<dark_gray>Kommen aus KubyCore</dark_gray>"));
        inv.setItem(15, item(Material.PAPER, "Stufen",
                "<gray>Die Stufen von 1:00 bis 10:00 sind im Plugin festgelegt.</gray>"));
    }

    private void renderHolos(Inventory inv) {
        List<Holo> holos = new ArrayList<>(plugin.holos().all().values());
        for (int i = 0; i < holos.size() && i < LIST_SLOTS.length; i++) {
            Holo holo = holos.get(i);
            inv.setItem(LIST_SLOTS[i], item(Material.ITEM_FRAME, holo.name(),
                    "<gray>Arena: <white>" + holo.arena() + "</white></gray>",
                    "<gray>Anzeigedauer: <white>" + holo.seconds() + " s</white></gray>",
                    "<gray>Größe: <white>" + holo.scale() + "</white></gray>",
                    "<dark_gray>Klicken zum Bearbeiten</dark_gray>"));
        }
        inv.setItem(49, item(Material.LIME_DYE, "Neues Hologramm hier",
                "<dark_gray>Name im Chat eingeben, erstellt an deiner Position</dark_gray>"));
    }

    private void renderHolo(Inventory inv, String name) {
        Holo holo = plugin.holos().get(name);
        if (holo == null) {
            return;
        }
        inv.setItem(10, item(Material.CLOCK, "Anzeigedauer",
                "<gray>Aktuell: <white>" + holo.seconds() + " s</white></gray>", CYCLE_HINT));
        inv.setItem(12, item(Material.SPYGLASS, "Größe",
                "<gray>Aktuell: <white>" + holo.scale() + "</white></gray>", CYCLE_HINT));
        inv.setItem(14, item(Material.MAP, "Arena",
                "<gray>Aktuell: <white>" + holo.arena() + "</white></gray>",
                "<dark_gray>Linksklick: nächste · Rechtsklick: vorherige</dark_gray>"));
        inv.setItem(16, item(Material.ENDER_PEARL, "Hierher verschieben",
                "<dark_gray>Setzt das Hologramm auf deine Position</dark_gray>"));
        inv.setItem(20, item(Material.ENDER_EYE, "Teleportieren",
                "<dark_gray>Teleportiert dich zum Hologramm</dark_gray>"));
        inv.setItem(24, item(Material.TNT, "Löschen",
                "<red>Entfernt das Hologramm</red>"));
    }

    private void renderNpcs(Inventory inv) {
        inv.setItem(11, item(Material.VILLAGER_SPAWN_EGG, "NPC hier erstellen",
                "<dark_gray>Arena-Name im Chat eingeben</dark_gray>"));
        inv.setItem(13, item(Material.COMPASS, "NPCs zentrieren",
                "<dark_gray>Setzt NPCs im Umkreis von 4 Blöcken auf die Blockmitte</dark_gray>"));
        inv.setItem(15, item(Material.BARRIER, "NPCs entfernen",
                "<dark_gray>Entfernt NPCs im Umkreis von 4 Blöcken</dark_gray>"));
    }

    private void renderArenas(Inventory inv) {
        List<String> names = arenaNames();
        for (int i = 0; i < names.size() && i < LIST_SLOTS.length; i++) {
            String name = names.get(i);
            Arena arena = plugin.arenas().get(name);
            inv.setItem(LIST_SLOTS[i], item(Material.MAP, name,
                    "<gray>Start: " + (arena.getStart() != null ? "<green>gesetzt</green>" : "<red>fehlt</red>") + "</gray>",
                    "<gray>Ziel: " + (arena.getFinish() != null ? "<green>gesetzt</green>" : "<red>fehlt</red>") + "</gray>",
                    "<gray>Checkpoints: <white>" + arena.getCheckpoints().size() + "</white></gray>",
                    "<dark_gray>Klicken zum Bearbeiten</dark_gray>"));
        }
        inv.setItem(49, item(Material.LIME_DYE, "Neue Arena",
                "<dark_gray>Name im Chat eingeben</dark_gray>"));
    }

    // ---------- Klicks ----------

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

        Inventory inv = holder.getInventory();
        int slot = event.getSlot();
        if (slot == inv.getSize() - 1) {
            player.closeInventory();
            return;
        }

        Screen parent = parent(holder.screen);
        if (parent != null && slot == backSlot(inv.getSize())) {
            open(player, parent, null);
            return;
        }

        boolean right = event.isRightClick();
        switch (holder.screen) {
            case MAIN -> clickMain(player, slot);
            case GENERAL -> clickGeneral(player, holder, slot, right);
            case REWARDS -> clickRewards(player, holder, slot, right);
            case HOLOS -> clickHolos(player, slot);
            case HOLO -> clickHolo(player, holder, slot, right);
            case NPCS -> clickNpcs(player, holder, slot);
            case ARENAS -> clickArenas(player, slot);
        }
    }

    private void clickMain(Player player, int slot) {
        switch (slot) {
            case 11 -> open(player, Screen.GENERAL, null);
            case 13 -> open(player, Screen.REWARDS, null);
            case 15 -> open(player, Screen.HOLOS, null);
            case 21 -> open(player, Screen.NPCS, null);
            case 23 -> open(player, Screen.ARENAS, null);
            default -> {
            }
        }
    }

    private void clickGeneral(Player player, Holder holder, int slot, boolean right) {
        var cfg = plugin.getConfig();
        switch (slot) {
            case 10 -> setConfig("fall.reset-blocks",
                    cycleInt(cfg.getInt("fall.reset-blocks", 2), 1, 1, 6, !right));
            case 12 -> setConfig("feather.boost-blocks",
                    cycleDouble(cfg.getDouble("feather.boost-blocks", 3.0), 0.5, 0.5, 8.0, !right));
            case 14 -> setConfig("leaderboard.visible-rows",
                    cycleInt(cfg.getInt("leaderboard.visible-rows", 10), 1, 1, 10, !right));
            case 16 -> {
                if (!right) {
                    prompt(player, "Neuer Prefix (MiniMessage erlaubt):", Screen.GENERAL, null,
                            input -> setConfig("messages-prefix", input));
                    return;
                }
                setConfig("messages-prefix", DEFAULT_PREFIX);
            }
            case 20 -> {
                plugin.reloadConfig();
                Text.send(player, "Config neu geladen.");
            }
            default -> {
                return;
            }
        }
        render(player, holder);
    }

    private void clickRewards(Player player, Holder holder, int slot, boolean right) {
        // Belohnungen sind fest im Plugin und über KubyCore gebunden, hier gibt es nichts zu ändern
    }

    private void clickHolos(Player player, int slot) {
        if (slot == 49) {
            prompt(player, "Name für das neue Hologramm:", Screen.HOLOS, null, name -> {
                String holoName = name.toLowerCase();
                if (!holoName.matches("[a-z0-9_-]{1,32}")) {
                    Text.send(player, "<red>Ungültiger Name. Erlaubt sind a-z, 0-9, _ und -.</red>");
                    return;
                }
                if (plugin.holos().get(holoName) != null) {
                    Text.send(player, "<red>Ein Hologramm mit diesem Namen existiert bereits.</red>");
                    return;
                }
                plugin.holos().create(holoName, "default", player.getLocation().add(0, 2.5, 0));
                Text.send(player, "Hologramm <white>" + holoName + "</white> erstellt.");
            });
            return;
        }

        List<Holo> holos = new ArrayList<>(plugin.holos().all().values());
        int index = indexOfSlot(slot);
        if (index >= 0 && index < holos.size()) {
            open(player, Screen.HOLO, holos.get(index).name());
        }
    }

    private void clickHolo(Player player, Holder holder, int slot, boolean right) {
        HoloManager holos = plugin.holos();
        Holo holo = holos.get(holder.param);
        if (holo == null) {
            open(player, Screen.HOLOS, null);
            return;
        }

        switch (slot) {
            case 10 -> {
                holo.seconds(cycleInt(holo.seconds(), 5, 3, 60, !right));
                holos.save();
            }
            case 12 -> {
                holo.scale(cycleDouble(holo.scale(), 0.5, 0.5, 4.0, !right));
                holos.rebuild(holo);
            }
            case 14 -> {
                List<String> names = arenaNames();
                if (!names.isEmpty()) {
                    int index = Math.floorMod(names.indexOf(holo.arena()) + (right ? -1 : 1), names.size());
                    holo.arena(names.get(index));
                    holos.save();
                }
            }
            case 16 -> holos.move(holo, player.getLocation().add(0, 2.5, 0));
            case 20 -> player.teleport(holo.location().clone().add(0, 1, 0));
            case 24 -> {
                holos.delete(holder.param);
                open(player, Screen.HOLOS, null);
                return;
            }
            default -> {
                return;
            }
        }
        render(player, holder);
    }

    private void clickNpcs(Player player, Holder holder, int slot) {
        switch (slot) {
            case 11 -> {
                prompt(player, "Arena-Name für den NPC:", Screen.NPCS, null, input -> {
                    String arena = input.toLowerCase();
                    if (!arena.matches("[a-z0-9_-]{1,32}")) {
                        Text.send(player, "<red>Ungültiger Name. Erlaubt sind a-z, 0-9, _ und -.</red>");
                        return;
                    }
                    plugin.arenas().getOrCreate(arena);
                    plugin.arenas().save();
                    plugin.rewardNpc().spawn(player.getLocation(), arena);
                    Text.send(player, "NPC für Arena <white>" + arena + "</white> erstellt.");
                });
                return;
            }
            case 13 -> {
                int moved = plugin.rewardNpc().centerNear(player.getLocation(), 4);
                Text.send(player, "<white>" + moved + "</white> NPC(s) auf die Blockmitte gesetzt.");
            }
            case 15 -> {
                int removed = plugin.rewardNpc().removeNear(player.getLocation(), 4);
                Text.send(player, "<white>" + removed + "</white> NPC(s) entfernt.");
            }
            default -> {
                return;
            }
        }
        render(player, holder);
    }

    private void clickArenas(Player player, int slot) {
        if (slot == 49) {
            prompt(player, "Name der neuen Arena:", Screen.ARENAS, null, input -> {
                String name = input.toLowerCase();
                if (!name.matches("[a-z0-9_-]{1,32}")) {
                    Text.send(player, "<red>Ungültiger Name. Erlaubt sind a-z, 0-9, _ und -.</red>");
                    return;
                }
                plugin.arenas().getOrCreate(name);
                plugin.arenas().save();
                Text.send(player, "Arena <white>" + name + "</white> erstellt.");
            });
            return;
        }

        List<String> names = arenaNames();
        int index = indexOfSlot(slot);
        if (index >= 0 && index < names.size()) {
            plugin.editorGui().open(player, EditorHolder.Screen.MAIN, names.get(index));
        }
    }

    // ---------- Chat-Eingabe ----------

    /** Fragt im Chat nach einer Eingabe. Danach öffnet sich das Menü wieder. */
    private void prompt(Player player, String question, Screen back, String backParam, Consumer<String> action) {
        inputs.put(player.getUniqueId(), input -> {
            if (input.equalsIgnoreCase("abbrechen")) {
                Text.send(player, "Abgebrochen.");
            } else if (input.isEmpty()) {
                Text.send(player, "<red>Leere Eingabe.</red>");
            } else {
                action.accept(input);
            }
            open(player, back, backParam);
        });
        player.closeInventory();
        Text.send(player, question + " <gray>(abbrechen mit <white>abbrechen</white>)</gray>");
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> handler = inputs.remove(player.getUniqueId());
        if (handler == null) {
            return;
        }

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handler.accept(input));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    // ---------- Hilfsmethoden ----------

    private void setConfig(String path, Object value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }

    private static int cycleInt(int value, int step, int min, int max, boolean up) {
        int next = up ? value + step : value - step;
        if (next > max) {
            next = min;
        }
        if (next < min) {
            next = max;
        }
        return next;
    }

    private static double cycleDouble(double value, double step, double min, double max, boolean up) {
        double next = up ? value + step : value - step;
        if (next > max + 1e-9) {
            next = min;
        }
        if (next < min - 1e-9) {
            next = max;
        }
        return next;
    }

    private List<String> arenaNames() {
        return plugin.arenas().all().stream().map(Arena::getName).sorted().toList();
    }

    private static int indexOfSlot(int slot) {
        for (int i = 0; i < LIST_SLOTS.length; i++) {
            if (LIST_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static int size(Screen screen) {
        return switch (screen) {
            case HOLOS, ARENAS -> 54;
            default -> 27;
        };
    }

    private static int backSlot(int size) {
        return size == 54 ? 45 : 18;
    }

    private static Screen parent(Screen screen) {
        return switch (screen) {
            case MAIN -> null;
            case HOLO -> Screen.HOLOS;
            default -> Screen.MAIN;
        };
    }

    private static String title(Screen screen, String param) {
        String name = switch (screen) {
            case MAIN -> "Einstellungen";
            case GENERAL -> "Allgemein";
            case REWARDS -> "Belohnungen";
            case HOLOS -> "Hologramme";
            case HOLO -> "Hologramm " + (param == null ? "" : param);
            case NPCS -> "NPCs";
            case ARENAS -> "Arenen";
        };
        return "<color:#ffd166>" + Text.smallCaps(name) + "</color>";
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
