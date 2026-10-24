package de.kuby.kubysurvival.heads;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.GuiMenu;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Truhe "» KÖPFE": oben die Kategorien und "Spieler online", in der Mitte 36 Köpfe je Seite, unten Blättern, Suche und
 * dein Kontostand. Klick kauft einen Kopf für heads.price Coins.
 */
final class HeadMenu extends GuiMenu {

    private static final int PER_PAGE = 36;
    /** Kategorie -1: Suchergebnis, -2: Spieler online. */
    private static final int SEARCH = -1, ONLINE = -2;

    private final HeadService heads;
    private final int cat;
    private final String term;
    private final int page;

    HeadMenu(HeadService heads, KubyPlugins core, int cat, String term, int page) {
        super(core, Msg.parse("<dark_gray>»</dark_gray> <#B47CF2><b>" + SmallCaps.of(term == null ? "köpfe" : "suche: " + term) + "</b>"), 6);
        this.heads = heads;
        this.cat = cat;
        this.term = term;
        this.page = page;
    }

    @Override
    protected boolean fillEmpty() {
        return false;
    }

    private List<HeadService.Head> content() {
        if (cat == SEARCH) return heads.search(term);
        if (cat == ONLINE) {
            List<HeadService.Head> out = new ArrayList<>();
            for (Player o : Bukkit.getOnlinePlayers()) out.add(new HeadService.Head("Kopf von " + o.getName(), o.getName()));
            return out;
        }
        List<HeadService.Category> cats = heads.categories();
        return cats.isEmpty() ? List.of() : cats.get(Math.min(cat, cats.size() - 1)).heads();
    }

    @Override
    protected void build(Player p) {
        String price = Msg.coins(heads.price());
        List<HeadService.Category> cats = heads.categories();
        for (int i = 0; i < cats.size() && i < 7; i++) {
            HeadService.Category c = cats.get(i);
            ItemStack tab = c.heads().isEmpty() ? new ItemStack(Material.PLAYER_HEAD)
                    : HeadService.item(c.heads().get(0), List.of());
            ItemMeta meta = tab.getItemMeta();
            meta.displayName(Msg.item((i == cat ? "<green>" : "<white>") + c.name()));
            meta.lore(List.of(Msg.item("<gray>" + c.heads().size() + " Köpfe")));
            if (i == cat) meta.setEnchantmentGlintOverride(true);
            tab.setItemMeta(meta);
            int idx = i;
            set(i, tab, e -> new HeadMenu(heads, plugin, idx, null, 0).open(p));
        }
        ItemStack online = Items.make(Material.PLAYER_HEAD, (cat == ONLINE ? "<green>" : "<white>") + "Spieler online",
                List.of("<gray>Köpfe aller Spieler, die gerade da sind"));
        set(8, online, e -> new HeadMenu(heads, plugin, ONLINE, null, 0).open(p));

        List<HeadService.Head> list = content();
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.min(page, pages - 1);
        for (int i = 0; i < PER_PAGE; i++) {
            int k = pg * PER_PAGE + i;
            if (k >= list.size()) break;
            HeadService.Head h = list.get(k);
            set(9 + i, HeadService.item(h, List.of("", "<gray>Preis: <gold>" + price + " Coins", "<yellow>Klick: kaufen")), e -> {
                String err = heads.buy(p, h);
                if (err != null) {
                    Msg.send(p, "<red>" + Msg.escape(err));
                    Sfx.play(plugin, p, Sfx.Kind.ERROR);
                } else {
                    Msg.send(p, "<green>Kopf gekauft: <white>" + Msg.escape(h.name()) + " <gray>(-" + price + " Coins)");
                    Sfx.play(plugin, p, Sfx.Kind.COIN);
                    refresh(p);
                }
            });
        }
        if (list.isEmpty()) {
            set(22, Items.make(Material.BARRIER, "<gray>Nichts gefunden", List.of("<gray>Tipp: /heads <Spielername>")), null);
        }
        if (pg > 0) {
            set(45, Items.make(Material.ARROW, "<white>Vorherige Seite", List.of("<gray>Seite " + pg + " von " + pages)),
                    e -> new HeadMenu(heads, plugin, cat, term, pg - 1).open(p));
        }
        if (pg < pages - 1) {
            set(53, Items.make(Material.ARROW, "<white>Nächste Seite", List.of("<gray>Seite " + (pg + 2) + " von " + pages)),
                    e -> new HeadMenu(heads, plugin, cat, term, pg + 1).open(p));
        }
        set(47, Items.make(Material.NAME_TAG, "<yellow>Suchen", List.of("<gray>Kopf nach Namen suchen,",
                "<gray>auch jeden Spielernamen", "", "<yellow>Klick: Suchbegriff eingeben")), e -> askTerm(p));
        set(49, Items.make(Material.GOLD_NUGGET, "<gold>Dein Geld: <white>" + Msg.coins(plugin.economy().balance(p.getUniqueId())) + " Coins",
                List.of("<gray>Jeder Kopf kostet <gold>" + price + " Coins")), null);
        set(51, Items.make(Material.OAK_DOOR, "<gray>Schließen", List.of()), e -> p.closeInventory());
    }

    private void askTerm(Player p) {
        DialogBase base = DialogBase.builder(Msg.parse("<#B47CF2><b>Kopf suchen"))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Msg.parse("<gray>Name des Kopfes oder ein Spielername"), 240)))
                .inputs(List.of(DialogInput.text("suche", Msg.parse("<gray>Suche")).width(200).maxLength(24).initial("").build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton yes = ActionButton.create(Msg.parse("<green><b>Suchen</b>"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (!(audience instanceof Player pl)) return;
                    String t = response.getText("suche");
                    if (t == null || t.trim().length() < 2) {
                        Msg.send(pl, "<red>Bitte mindestens 2 Zeichen suchen.");
                        new HeadMenu(heads, plugin, cat, term, page).open(pl);
                        return;
                    }
                    new HeadMenu(heads, plugin, SEARCH, t.trim(), 0).open(pl);
                }, once));
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) new HeadMenu(heads, plugin, cat, term, page).open(pl);
                }, once));
        p.closeInventory();
        p.showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(yes, no))));
    }
}
