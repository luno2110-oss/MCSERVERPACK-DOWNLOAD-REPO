package de.kuby.kubysurvival.jobs;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Angebote des Token-Shops für den Dialog (dieselben Preise und Wirkungen wie TokenShopMenu): Claim-Blöcke, Rucksack,
 * Booster, Jobwechsel, Doppelfang, Bauhelfer und weitere Items aus der Config.
 */
public final class TokenOffers {

    /**
     * Ein Angebot: Bereich, Icon, Name, kurzer Text, Preis in Tokens (0 = gratis), Hinweis statt Preis (z. B. „max“,
     * null = Preis zeigen), kaufbar, Aktion (gibt eine Fehlermeldung zurück oder null).
     */
    public record Offer(String section, Material icon, String name, String desc, long price, String note, boolean enabled,
                        Function<Player, String> buy) {}

    private TokenOffers() {
    }

    private static long cfg(KubySurvival addon, String key, long def) {
        return addon.core().getConfig().getLong("tokens.prices." + key, def);
    }

    private static String pay(KubySurvival addon, Player p, long price) {
        return addon.jobs().takeTokens(p.getUniqueId(), price) ? null : "Dafür brauchst du " + price + " Tokens.";
    }

    static void bought(Player p, String what) {
        Msg.send(p, "<green>Gekauft: <white>" + what);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
    }

    public static List<Offer> list(KubySurvival addon, Player viewer) {
        UUID id = viewer.getUniqueId();
        JobService jobs = addon.jobs();
        List<Offer> out = new ArrayList<>();

        // Claim-Blöcke
        long[][] blocks = {{100, cfg(addon, "claim-100", 2)}, {500, cfg(addon, "claim-500", 8)}, {2000, cfg(addon, "claim-2000", 25)}};
        for (long[] b : blocks) {
            long amount = b[0], price = b[1];
            out.add(new Offer("Claim-Blöcke", Material.GRASS_BLOCK, "+" + Msg.coins(amount) + " Claim-Blöcke",
                    "Mehr Platz für Grundstücke", price, null, true, p -> {
                String err = pay(addon, p, price);
                if (err != null) return err;
                addon.claims().addBlocks(p.getUniqueId(), amount);
                bought(p, Msg.coins(amount) + " Claim-Blöcke");
                return null;
            }));
        }

        // Rucksack
        var bp = addon.backpacks();
        int rows = bp.rows(id);
        long next = bp.nextPrice(id);
        out.add(new Offer("Rucksack", Material.LEATHER, "Rucksack " + (rows == 0 ? "freischalten" : "vergrößern"),
                rows == 0 ? "Eigenes Lager zum Mitnehmen" : "Jetzt " + rows + (rows == 1 ? " Reihe" : " Reihen"),
                Math.max(0, next), next < 0 ? "max" : null, next >= 0, p -> {
            long price = bp.nextPrice(p.getUniqueId());
            if (price < 0) return "Größer geht der Rucksack nicht.";
            String err = pay(addon, p, price);
            if (err != null) return err;
            bp.addRow(p.getUniqueId());
            int r = bp.rows(p.getUniqueId());
            bought(p, "Rucksack mit " + r + (r == 1 ? " Reihe" : " Reihen") + " (/backpack)");
            return null;
        }));
        if (rows > 0) {
            out.add(new Offer("Rucksack", Material.CHEST, "Rucksack öffnen", "Auch mit /backpack", 0, "gratis", true, p -> {
                de.kuby.kubyplugins.ui.UiDialog.close(p);
                bp.open(p);
                return null;
            }));
            out.add(new Offer("Rucksack", Material.LEATHER, "Rucksack-Item holen", "Rechtsklick damit öffnet ihn", 0, "gratis",
                    true, p -> {
                p.getInventory().addItem(bp.item()).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
                Msg.send(p, "<green>Rucksack-Item ist in deinem Inventar.");
                return null;
            }));
        }

        // Booster und Jobwechsel
        int pct = (int) Math.round((addon.core().getConfig().getDouble("tokens.booster-multiplier", 1.5) - 1) * 100);
        long left = jobs.boosterMinutes(id);
        for (int minutes : new int[]{30, 60}) {
            long price = cfg(addon, "booster-" + minutes, minutes == 30 ? 5 : 9);
            out.add(new Offer("Booster", Material.BLAZE_POWDER, "Job-Booster " + minutes + " Min.",
                    left > 0 ? "Läuft noch " + left + " Min., wird verlängert" : "+" + pct + " % Job-XP, Coins und Limit",
                    price, null, true, p -> {
                String err = pay(addon, p, price);
                if (err != null) return err;
                jobs.addBooster(p.getUniqueId(), minutes);
                bought(p, "Job-Booster für " + minutes + " Minuten");
                return null;
            }));
        }
        long skip = addon.core().getConfig().getLong("jobs.skip-cooldown-tokens", 2);
        boolean waiting = jobs.cooldownMinutes(id) > 0;
        out.add(new Offer("Booster", Material.CLOCK, "Jobwechsel sofort",
                waiting ? "Wartezeit noch " + jobs.cooldownMinutes(id) + " Min." : "Gerade keine Wartezeit",
                skip, waiting ? null : "–", waiting, p -> {
            String err = jobs.skipCooldown(p);
            if (err == null) Msg.send(p, "<green>Du kannst deinen Job jetzt sofort wechseln.");
            return err;
        }));

        // Angeln: Doppelfang
        var angeln = addon.angeln();
        int dbl = angeln.profile(id).doubleCatch;
        boolean maxed = dbl >= de.kuby.kubysurvival.angeln.AngelService.DOUBLE_MAX;
        out.add(new Offer("Angeln", Material.TROPICAL_FISH, "Doppelfang " + (maxed ? "(max)" : "Stufe " + (dbl + 1)),
                "Chance auf zwei Fische: " + (5 * dbl) + " %" + (maxed ? "" : " → " + (5 * (dbl + 1)) + " %"),
                maxed ? 0 : angeln.doublePrice(dbl), maxed ? "max" : null, !maxed, p -> {
            String err = angeln.buyDouble(p);
            if (err == null) bought(p, "Doppelfang Stufe " + angeln.profile(p.getUniqueId()).doubleCatch);
            return err;
        }));

        // Bauhelfer und Sonstiges (Config: MATERIAL;Anzahl;Preis)
        items(addon, out, "Bauhelfer", "tokens.build-helpers", List.of("LIGHT;16;3", "SCAFFOLDING;64;1", "SEA_LANTERN;16;2", "GLASS;64;1"));
        items(addon, out, "Sonstiges", "jobs.token-shop", List.of());
        return out;
    }

    private static void items(KubySurvival addon, List<Offer> out, String section, String path, List<String> defaults) {
        List<String> lines = addon.core().getConfig().isList(path) ? addon.core().getConfig().getStringList(path) : defaults;
        for (String line : lines) {
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
            String name = amount + "x " + nice(m);
            out.add(new Offer(section, m, name, "Kommt in dein Inventar", price, null, true, p -> {
                String err = pay(addon, p, price);
                if (err != null) return err;
                int rest = amount;
                while (rest > 0) {
                    ItemStack give = new ItemStack(m, Math.min(rest, m.getMaxStackSize()));
                    rest -= give.getAmount();
                    p.getInventory().addItem(give).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
                }
                bought(p, name);
                return null;
            }));
        }
    }

    private static final java.util.Map<Material, String> NAMES = java.util.Map.of(Material.LIGHT, "Licht-Block",
            Material.SCAFFOLDING, "Gerüst", Material.SEA_LANTERN, "Seelaterne", Material.GLASS, "Glas",
            Material.TORCH, "Fackel", Material.LANTERN, "Laterne", Material.GLOWSTONE, "Leuchtstein");

    static String nice(Material m) {
        if (NAMES.containsKey(m)) return NAMES.get(m);
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
