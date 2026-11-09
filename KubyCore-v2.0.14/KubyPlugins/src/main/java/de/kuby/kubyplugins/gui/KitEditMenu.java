package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.KitService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Editor für ein Kit (Admins): die oberen vier Reihen sind der Inhalt (echte Items hineinlegen, herausnehmen,
 * umsortieren), die unterste Reihe stellt Name, Icon, Rang, Abklingzeit und Modi ein, testet und löscht das Kit.
 * Jede Änderung und das Schließen speichern sofort (kits-config.yml).
 */
public final class KitEditMenu implements InventoryHolder {

    static final int CONTENT = 36;
    private static final int BACK = 45, NAME = 46, ICON = 47, RANK = 48, COOLDOWN = 49, MODES = 50, TEST = 51, SAVE = 52, DELETE = 53;
    /** Modi zum Durchschalten (leer = alle). */
    private static final List<Set<String>> MODE_STEPS = List.of(Set.of(), Set.of("survival"), Set.of("smp"), Set.of("skyblock"),
            orderedSet("survival", "smp"));

    private final KubyPlugins plugin;
    private final String id;
    private final Inventory inventory;
    private String name, rank;
    private Material icon;
    private long cooldown;
    private Set<String> modes;
    private boolean deleted;

    public KitEditMenu(KubyPlugins plugin, String id) {
        this.plugin = plugin;
        this.id = id;
        KitService.Kit k = plugin.kits().kit(id);
        this.name = k == null ? "Neues Kit" : k.name();
        this.rank = k == null ? "" : k.rank();
        this.icon = k == null ? Material.CHEST : k.icon();
        this.cooldown = k == null ? 60 : k.cooldownMinutes();
        this.modes = k == null ? Set.of() : k.modes();
        this.inventory = Bukkit.createInventory(this, 54, Msg.parse("<dark_gray>" + SmallCaps.of("Kit") + ": " + Msg.escape(name)));
        if (k != null) {
            for (int i = 0; i < k.items().size() && i < CONTENT; i++) inventory.setItem(i, k.items().get(i).clone());
        }
        controls();
    }

    private static Set<String> orderedSet(String... v) {
        return new LinkedHashSet<>(List.of(v));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player p) {
        p.openInventory(inventory);
        Sfx.play(plugin, p, Sfx.Kind.OPEN);
    }

    static String minutes(long m) {
        if (m <= 0) return "keine";
        if (m % 1440 == 0) return (m / 1440) + (m == 1440 ? " Tag" : " Tage");
        if (m >= 60) return (m / 60) + " Std." + (m % 60 > 0 ? " " + (m % 60) + " Min." : "");
        return m + " Min.";
    }

    static String modesText(Set<String> modes) {
        if (modes.isEmpty()) return "alle";
        List<String> out = new ArrayList<>();
        for (String m : modes) out.add(switch (m) {
            case "survival" -> "Survival";
            case "smp" -> "SMP";
            case "skyblock" -> "Skyblock";
            default -> m;
        });
        return String.join(", ", out);
    }

    private void controls() {
        ItemStack glass = Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = CONTENT; i < 45; i++) inventory.setItem(i, glass);
        inventory.setItem(BACK, Items.make(Material.ARROW, "<gray>Zurück", List.of("<gray>Speichert und zeigt alle Kits")));
        inventory.setItem(NAME, Items.make(Material.NAME_TAG, "<gold>Name: <white>" + Msg.escape(name), List.of("<yellow>Klick: ändern")));
        inventory.setItem(ICON, Items.make(icon, "<gold>Icon", List.of("<gray>Nimm ein Item auf den Mauszeiger", "<gray>und klick hier.")));
        List<String> rl = new ArrayList<>();
        rl.add("<gray>Ab welchem Rang das Kit erlaubt ist.");
        rl.add("<gray>Höhere Ränge dürfen auch niedrigere Kits.");
        rl.add("");
        rl.add("<yellow>Linksklick: nächster · Rechtsklick: voriger");
        inventory.setItem(RANK, Items.make(Material.GOLD_INGOT, "<gold>Rang: <white>" + (rank.isEmpty() ? "für alle" : "ab " + KitService.rankName(rank)), rl));
        inventory.setItem(COOLDOWN, Items.make(Material.CLOCK, "<gold>Abklingzeit: <white>" + minutes(cooldown), List.of(
                "<yellow>Linksklick: +10 Min. · Rechtsklick: -10 Min.",
                "<yellow>Shift: +1 Std. / -1 Std.")));
        inventory.setItem(MODES, Items.make(Material.COMPASS, "<gold>Modi: <white>" + modesText(modes), List.of(
                "<gray>In welchen Modi das Kit zu sehen ist.", "<yellow>Klick: weiter")));
        inventory.setItem(TEST, Items.make(Material.CHEST, "<aqua>Testen", List.of("<gray>Gibt dir den Inhalt (ohne Abklingzeit).")));
        inventory.setItem(SAVE, Items.make(Material.LIME_DYE, "<green>Speichern", List.of("<gray>Speichert auch beim Schließen.")));
        inventory.setItem(DELETE, Items.make(Material.BARRIER, "<red>Kit löschen", List.of("<gray>Shift + Klick: endgültig löschen")));
    }

    /** Inhalt und Einstellungen speichern. */
    void save() {
        if (deleted) return;
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < CONTENT; i++) {
            ItemStack it = inventory.getItem(i);
            if (it != null && !it.getType().isAir()) items.add(it.clone());
        }
        plugin.kits().put(new KitService.Kit(id, name, icon, cooldown, items, rank, modes));
    }

    void click(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        Player p = (Player) e.getWhoClicked();
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }
        if (raw >= inventory.getSize()) {
            // eigenes Inventar: Shift-Klick legt das Item in den ersten freien Inhalts-Platz
            if (e.isShiftClick()) {
                e.setCancelled(true);
                ItemStack cur = e.getCurrentItem();
                if (cur == null || cur.getType().isAir()) return;
                for (int i = 0; i < CONTENT; i++) {
                    if (inventory.getItem(i) == null) {
                        inventory.setItem(i, cur.clone());
                        e.setCurrentItem(null);
                        return;
                    }
                }
                Msg.send(p, "<red>Das Kit ist voll (36 Plätze).");
            }
            return;
        }
        if (raw < CONTENT) return;
        e.setCancelled(true);
        switch (raw) {
            case BACK -> {
                save();
                new KitAdminMenu(plugin).open(p);
                return;
            }
            case NAME -> {
                save();
                p.closeInventory();
                askName(p);
                return;
            }
            case ICON -> {
                ItemStack cur = e.getCursor();
                if (cur == null || cur.getType().isAir()) {
                    Msg.send(p, "<gray>Nimm ein Item auf den Mauszeiger und klick dann auf das Icon.");
                    return;
                }
                icon = cur.getType();
            }
            case RANK -> {
                List<String> all = new ArrayList<>();
                all.add("");
                all.addAll(plugin.kits().allRanks());
                int i = Math.max(0, all.indexOf(rank));
                rank = all.get(Math.floorMod(i + (e.isRightClick() ? -1 : 1), all.size()));
            }
            case COOLDOWN -> {
                long step = e.isShiftClick() ? 60 : 10;
                cooldown = Math.max(0, cooldown + (e.isRightClick() ? -step : step));
            }
            case MODES -> {
                int i = MODE_STEPS.indexOf(modes);
                modes = MODE_STEPS.get(Math.floorMod(i + 1, MODE_STEPS.size()));
            }
            case TEST -> {
                for (int i = 0; i < CONTENT; i++) {
                    ItemStack it = inventory.getItem(i);
                    if (it != null && !it.getType().isAir()) {
                        p.getInventory().addItem(it.clone()).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
                    }
                }
                Msg.send(p, "<green>Inhalt von " + Msg.escape(name) + " in dein Inventar gelegt.");
            }
            case SAVE -> Msg.send(p, "<green>Kit " + Msg.escape(name) + " gespeichert.");
            case DELETE -> {
                if (!e.isShiftClick()) {
                    Msg.send(p, "<gray>Zum Löschen <white>Shift + Klick</white> auf die Barriere.");
                    return;
                }
                deleted = true;
                plugin.kits().delete(id);
                Msg.send(p, "<red>Kit " + Msg.escape(name) + " gelöscht.");
                new KitAdminMenu(plugin).open(p);
                return;
            }
            default -> {
                return;
            }
        }
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
        save();
        controls();
    }

    void drag(InventoryDragEvent e) {
        for (int raw : e.getRawSlots()) {
            if (raw >= CONTENT && raw < inventory.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** Name im kleinen Dialog mit Textfeld ändern, danach wieder den Editor öffnen. */
    private void askName(Player p) {
        DialogBase base = DialogBase.builder(Msg.parse("<gold><b>Kit-Name</b>"))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Msg.parse("<gray>Name des Kits (Farben wie " + Msg.escape("<gold>") + " gehen)."), 240)))
                .inputs(List.of(DialogInput.text("name", Msg.parse("<gray>Name")).width(200).maxLength(40).initial(name).build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton ok = ActionButton.create(Msg.parse("<green><b>Übernehmen</b>"), null, 100, DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl)) return;
            String v = response.getText("name");
            if (v != null && !v.isBlank()) name = v.trim();
            save();
            new KitEditMenu(plugin, id).open(pl);
        }, once));
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100, DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player pl) new KitEditMenu(plugin, id).open(pl);
        }, once));
        de.kuby.kubyplugins.ui.UiDialog.open(p, Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(ok, no))));
    }
}
