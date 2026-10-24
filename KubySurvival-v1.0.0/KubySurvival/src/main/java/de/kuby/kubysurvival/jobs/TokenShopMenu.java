package de.kuby.kubysurvival.jobs;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.tokens.TokenEnchantMenu;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Token-Shop: Claim-Blöcke, Rucksack, Booster, Jobwechsel ohne Wartezeit, besondere Verzauberungen,
 * Bauhelfer (tokens.build-helpers) und weitere Items (jobs.token-shop). Format der Item-Listen: MATERIAL;Anzahl;Preis.
 */
public final class TokenShopMenu extends GuiMenu {

    private final KubySurvival addon;

    public TokenShopMenu(KubySurvival addon, KubyPlugins core) {
        super(core, Msg.parse("<light_purple>Token-Shop"), 6);
        this.addon = addon;
    }

    private boolean pay(Player p, long price) {
        if (addon.jobs().takeTokens(p.getUniqueId(), price)) return true;
        Msg.send(p, "<red>Dafür brauchst du " + price + " Tokens.");
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
        return false;
    }

    private void bought(Player p, String what) {
        Msg.send(p, "<green>Gekauft: <white>" + what);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
    }

    @Override
    protected void build(Player p) {
        UUID id = p.getUniqueId();
        JobService jobs = addon.jobs();
        long tokens = jobs.worker(id).tokens;
        var ts = addon.tokens();
        set(4, Items.make(Material.AMETHYST_SHARD, "<light_purple>" + tokens + " Tokens", List.of(
                "<gray>Nächster Token in <white>" + ts.minutesToNext(id) + " Min. <gray>Spielzeit",
                "<gray>Serie: <white>" + ts.streak(id) + " Tage",
                "", "<dark_gray>Tokens gibt es fürs Spielen, als Tagesbonus,", "<dark_gray>beim Voten, für Quests und Aufstiege.",
                "<dark_gray>Kaufen oder tauschen kann man sie nicht.")), null);

        // Claim-Blöcke
        set(9, Items.make(Material.GOLDEN_AXE, "<gold>Claim-Blöcke", List.of("<gray>Mehr Platz für Grundstücke")), null);
        long[][] blocks = {{100, cfg("claim-100", 2)}, {500, cfg("claim-500", 8)}, {2000, cfg("claim-2000", 25)}};
        for (int i = 0; i < blocks.length; i++) {
            long amount = blocks[i][0], price = blocks[i][1];
            set(10 + i, Items.make(Material.GRASS_BLOCK, "<white>+" + Msg.coins(amount) + " Claim-Blöcke", List.of(
                    "<gray>Preis: <light_purple>" + price + " Tokens", "", "<yellow>Klick zum Kaufen")), e -> {
                if (!pay(p, price)) return;
                addon.claims().addBlocks(id, amount);
                bought(p, Msg.coins(amount) + " Claim-Blöcke");
                open(p);
            });
        }

        // Rucksack
        var bp = addon.backpacks();
        int rows = bp.rows(id);
        long next = bp.nextPrice(id);
        set(14, Items.make(Material.LEATHER, "<dark_green>Rucksack " + (rows == 0 ? "freischalten" : "vergrößern"), List.of(
                "<gray>Jetzt: <white>" + (rows == 0 ? "keiner" : rows + (rows == 1 ? " Reihe" : " Reihen")),
                next < 0 ? "<green>Größer geht nicht" : "<gray>Nächste Reihe: <light_purple>" + next + " Tokens",
                "", next < 0 ? "" : "<yellow>Klick zum Kaufen")), e -> {
            if (next < 0 || !pay(p, next)) return;
            bp.addRow(id);
            bought(p, "Rucksack mit " + bp.rows(id) + (bp.rows(id) == 1 ? " Reihe" : " Reihen") + " (/backpack)");
            open(p);
        });
        if (rows > 0) {
            set(15, Items.make(Material.CHEST, "<dark_green>Rucksack öffnen", List.of("<white>/backpack", "", "<yellow>Klick")), e -> bp.open(p));
            set(16, Items.make(Material.LEATHER, "<dark_green>Rucksack-Item holen", List.of("<gray>Rechtsklick damit öffnet", "<gray>den Rucksack. Kostenlos.",
                    "", "<yellow>Klick")), e -> {
                p.getInventory().addItem(bp.item()).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
                p.closeInventory();
            });
        }

        // Booster und Jobs
        set(18, Items.make(Material.BLAZE_POWDER, "<gold>Booster", List.of("<gray>Mehr Job-XP, Coins und Stundenlimit")), null);
        long left = jobs.boosterMinutes(id);
        int pct = (int) Math.round((addon.core().getConfig().getDouble("tokens.booster-multiplier", 1.5) - 1) * 100);
        int[][] boosters = {{30, (int) cfg("booster-30", 5)}, {60, (int) cfg("booster-60", 9)}};
        for (int i = 0; i < boosters.length; i++) {
            int minutes = boosters[i][0];
            long price = boosters[i][1];
            set(19 + i, Items.make(Material.BLAZE_POWDER, "<gold>Job-Booster " + minutes + " Min.", List.of(
                    "<gray>+" + pct + " % Job-XP und Coins,", "<gray>Stundenlimit +" + pct + " %",
                    left > 0 ? "<green>Läuft noch " + left + " Min. (wird verlängert)" : "",
                    "<gray>Preis: <light_purple>" + price + " Tokens", "", "<yellow>Klick zum Kaufen")), e -> {
                if (!pay(p, price)) return;
                jobs.addBooster(id, minutes);
                bought(p, "Job-Booster für " + minutes + " Minuten");
                open(p);
            });
        }
        long skip = addon.core().getConfig().getLong("jobs.skip-cooldown-tokens", 2);
        set(21, Items.make(Material.CLOCK, "<yellow>Jobwechsel ohne Wartezeit", List.of(
                "<gray>Preis: <light_purple>" + skip + " Tokens", "", "<yellow>Klick zum Kaufen")), e -> {
            String err = jobs.skipCooldown(p);
            Msg.send(p, err == null ? "<green>Du kannst deinen Job jetzt sofort wechseln." : "<red>" + err);
            open(p);
        });

        // Verzauberungen
        set(23, Items.make(Material.ENCHANTED_BOOK, "<light_purple>Besondere Verzauberungen", List.of(
                "<gray>Eine Stufe über dem Maximum:", "<gray>Effizienz VI, Haltbarkeit IV,", "<gray>Glück IV, Schärfe VI und mehr.",
                "", "<yellow>Klick")), e -> new TokenEnchantMenu(addon).open(p));

        // Angeln: Doppelfang (Chance auf zwei Fische)
        var angeln = addon.angeln();
        int dbl = angeln.profile(id).doubleCatch;
        boolean maxed = dbl >= de.kuby.kubysurvival.angeln.AngelService.DOUBLE_MAX;
        long dblPrice = angeln.doublePrice(dbl);
        set(25, Items.make(Material.TROPICAL_FISH, "<aqua>Doppelfang " + (maxed ? "(max)" : "Stufe " + (dbl + 1)), List.of(
                "<gray>Beim Angeln: Chance auf zwei Fische", "<gray>Jetzt: <white>" + (5 * dbl) + " %"
                        + (maxed ? "" : " <dark_gray>→ <green>" + (5 * (dbl + 1)) + " %"),
                maxed ? "<green>Höchste Stufe erreicht" : "<gray>Preis: <light_purple>" + dblPrice + " Tokens",
                "", maxed ? "" : "<yellow>Klick zum Kaufen")), e -> {
            if (maxed) return;
            String err = angeln.buyDouble(p);
            if (err != null) {
                Msg.send(p, "<red>" + err);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            } else {
                bought(p, "Doppelfang Stufe " + angeln.profile(id).doubleCatch);
            }
            open(p);
        });

        // Bauhelfer
        set(27, Items.make(Material.SCAFFOLDING, "<aqua>Bauhelfer", List.of("<gray>Licht-Blöcke, Gerüste und mehr")), null);
        items(p, "tokens.build-helpers", List.of("LIGHT;16;3", "SCAFFOLDING;64;1", "SEA_LANTERN;16;2", "GLASS;64;1"), 28, 35);
        // Sonstiges
        set(36, Items.make(Material.CHEST, "<white>Sonstiges", List.of()), null);
        items(p, "jobs.token-shop", List.of(), 37, 44);
        addBack(p);
    }

    private long cfg(String key, long def) {
        return addon.core().getConfig().getLong("tokens.prices." + key, def);
    }

    private void items(Player p, String path, List<String> defaults, int from, int to) {
        List<String> lines = addon.core().getConfig().isList(path) ? addon.core().getConfig().getStringList(path) : defaults;
        int slot = from;
        for (String line : lines) {
            if (slot >= to) break;
            String[] parts = line.split(";");
            if (parts.length < 3) continue;
            Material m = Material.matchMaterial(parts[0].trim());
            int amount;
            long price;
            try {
                amount = Integer.parseInt(parts[1].trim());
                price = Long.parseLong(parts[2].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (m == null || !m.isItem() || amount <= 0 || price <= 0) continue;
            ItemStack shown = Items.make(m, "<white>" + amount + "x " + nice(m), List.of(
                    "<gray>Preis: <light_purple>" + price + " Tokens", "", "<yellow>Klick zum Kaufen"));
            shown.setAmount(Math.min(amount, m.getMaxStackSize()));
            set(slot++, shown, e -> {
                if (!pay(p, price)) return;
                int left = amount;
                while (left > 0) {
                    ItemStack give = new ItemStack(m, Math.min(left, m.getMaxStackSize()));
                    left -= give.getAmount();
                    p.getInventory().addItem(give).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
                }
                bought(p, amount + "x " + nice(m));
                open(p);
            });
        }
    }

    private static String nice(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
