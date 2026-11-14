package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.jobs.Job;
import org.bukkit.Location;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Angeln in Survival: Wartezeit (Upgrades, Köder, Zone), Biss-Hinweis, Fang durch eigene Fische ersetzen.
 * Fisch-Items lassen sich nicht braten, verbauen oder verfüttern. Rechtsklick auf den Fischhändler öffnet sein Menü.
 */
public final class AngelListener implements Listener {

    private static final String SURVIVAL = "survival";

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final AngelService service;
    private final AngelFx fx;
    private final AngelMenus menus;
    private final AngelDealer dealer;
    private final FishGame game;

    public AngelListener(KubySurvival addon, KubyPlugins core, AngelService service, AngelFx fx, AngelMenus menus,
                         AngelDealer dealer, FishGame game) {
        this.addon = addon;
        this.core = core;
        this.service = service;
        this.fx = fx;
        this.menus = menus;
        this.dealer = dealer;
        this.game = game;
        if (game != null) game.onFinish(this::afterCatch);
    }

    /** Alles nach einem Fang: Effekte, Angel-Effekte, Job-XP, Quests. */
    private void afterCatch(Player p, Location at, AngelService.Catch c, ItemStack fish) {
        fx.caught(p, at, c, fish);
        rodFx(p, at, c);
        Rarity r = c.species().rarity();
        int xp = 4 + 4 * r.ordinal() * r.ordinal();
        addon.jobs().reward(p, Job.ANGLER, xp, xp);
        addon.questProgress(p, "fish", null, c.amount());
        if (c.treasure() != null) addon.questProgress(p, "treasure", null, 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        if (!SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return;
        FishHook hook = e.getHook();
        switch (e.getState()) {
            case FISHING -> {
                // Angelwelt: auf der Insel eines Gewässers nur mit passender Angel, Köder und Level
                AngelWorld world = service.world();
                AngelWorld.Spot spot = world == null ? null : world.islandAt(p.getLocation());
                if (spot != null && !allowed(e, p, world, spot)) return;
                boolean zone = service.zoneAt(hook.getLocation()) != null || service.zoneAt(p.getLocation()) != null;
                Rod rod = AngelService.rodOf(p);
                boolean rain = !hook.getWorld().isClearWeather();
                double f = Math.max(0.15, 1.0 - (service.faster(p.getUniqueId(), zone, rod, rain)
                        + (spot == null ? 0 : spot.faster)) / 100.0);
                // Angelpass: doppelt so schnell
                if (service.passOwner(p.getUniqueId())) f *= 0.5;
                int min = Math.max(1, (int) (hook.getMinWaitTime() * f));
                int max = Math.max(min, (int) (hook.getMaxWaitTime() * f));
                hook.setMinWaitTime(min);
                hook.setMaxWaitTime(max);
                fx.cast(p, hook);
            }
            case BITE -> fx.bite(p);
            case CAUGHT_FISH -> {
                if (!(e.getCaught() instanceof Item item)) return;
                Location at = hook.getLocation();
                // vom Nachbarsteg in ein gesperrtes Gewässer geworfen: kein Fang
                AngelWorld world = service.world();
                AngelWorld.Spot spot = world == null ? null : world.spotAt(at);
                if (spot != null && !allowed(e, p, world, spot)) {
                    item.remove();
                    return;
                }
                // wirklich seltene Fänge: Würfel-Animation und Minispiel statt sofort
                AngelService.Roll roll = service.roll(p, at);
                int tier = service.tier(roll);
                if (tier > 0 && game != null && !game.active(p)) {
                    item.remove();
                    game.start(p, at, roll, tier);
                    return;
                }
                AngelService.Catch c = service.book(p, roll, 1.0, false);
                ItemStack fish = AngelItems.fish(c.species(), c.stars(), c.kg(), c.variant(), c.amount());
                if (c.autoSold() > 0) {
                    try {
                        item.setItemStack(ItemStack.empty());
                    } catch (IllegalArgumentException ex) {
                        item.remove();
                    }
                } else {
                    item.setItemStack(fish);
                    item.setPickupDelay(0);
                    item.setVelocity(item.getVelocity().multiply(0.2));
                    item.teleport(p.getLocation().add(0, 0.3, 0));
                }
                afterCatch(p, at, c, fish);
            }
            default -> fx.stop(p);
        }
    }

    /** Prüft die Voraussetzungen eines Gewässers; false (und Abbruch mit Hinweis), wenn etwas fehlt. */
    private boolean allowed(PlayerFishEvent e, Player p, AngelWorld world, AngelWorld.Spot spot) {
        String miss = world.missing(p, spot);
        if (miss == null) return true;
        e.setCancelled(true);
        Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <gray>Am <white>" + Msg.escape(spot.name) + " <gray>fehlt dir noch: <red>"
                + Msg.escape(miss) + "<gray>. Alle Gewässer: <white>/angeln gewaesser");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
        return false;
    }

    /** Effekte der besonderen Angeln beim Fang. */
    private void rodFx(Player p, Location at, AngelService.Catch c) {
        Rod rod = AngelService.rodOf(p);
        if (rod == null) return;
        var w = at.getWorld();
        switch (rod) {
            case RAINBOW -> {
                int[] cols = {0xFF4B4B, 0xFFA43B, 0xFFE44B, 0x5BEA6B, 0x4BB8FF, 0xB46CFF};
                for (int i = 0; i < cols.length; i++) {
                    w.spawnParticle(org.bukkit.Particle.DUST, at.clone().add(0, 0.3 + i * 0.25, 0), 4, 0.25, 0.05, 0.25,
                            new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(cols[i]), 1.3f));
                }
            }
            case VOID -> {
                w.spawnParticle(org.bukkit.Particle.PORTAL, at, 40, 0.3, 0.5, 0.3, 0.6);
                w.spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, at, 15, 0.2, 0.4, 0.2, 0.05);
            }
            case GOETTLICH -> {
                w.spawnParticle(org.bukkit.Particle.END_ROD, at, 12, 0.2, 0.4, 0.2, 0.05);
                if (c.species().rarity().atLeast(Rarity.EPISCH)) {
                    // Lichtstrahl aus dem Himmel
                    for (int y = 0; y < 24; y++) {
                        w.spawnParticle(org.bukkit.Particle.END_ROD, at.clone().add(0, y * 0.75, 0), 2, 0.05, 0.2, 0.05, 0.0);
                    }
                    w.spawnParticle(org.bukkit.Particle.DUST, at, 40, 0.8, 0.3, 0.8,
                            new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0xFFE7A8), 1.8f));
                    p.playSound(at, "kuby:cine.shing", 0.8f, 1.3f);
                }
            }
            case GOLDFIEBER -> {
                w.spawnParticle(org.bukkit.Particle.DUST, at, 30, 0.4, 0.4, 0.4,
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0xFFC93A), 1.5f));
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
            }
            case STURM -> {
                w.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, at, 30, 0.4, 0.6, 0.4, 0.2);
                if (c.amount() > 1) {
                    // Blitzfang: Blitz ohne Schaden über dem Haken
                    w.strikeLightningEffect(at);
                    Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <#8AB4FF>⚡ Blitzfang! <gray>Ein zweiter Fisch hing am Haken.");
                }
            }
            case KOSMOS -> {
                w.spawnParticle(org.bukkit.Particle.END_ROD, at, 20, 0.5, 0.6, 0.5, 0.03);
                w.spawnParticle(org.bukkit.Particle.DUST, at, 25, 0.6, 0.6, 0.6,
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(0xB59CFF), 1.2f));
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        fx.stop(e.getPlayer());
    }

    // ---------- Händler ----------

    @EventHandler(ignoreCancelled = true)
    public void onDealer(PlayerInteractEntityEvent e) {
        if (dealer.is(e.getRightClicked())) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) menus.openDealer(e.getPlayer(), null);
            return;
        }
        // Fische nicht verfüttern (Katzen, Delfine, Axolotl ...)
        ItemStack hand = e.getPlayer().getInventory().getItem(e.getHand());
        if (AngelItems.isFish(hand) && (e.getRightClicked() instanceof Animals || e.getRightClicked() instanceof WaterMob
                || e.getRightClicked() instanceof Dolphin || e.getRightClicked() instanceof Axolotl
                || e.getRightClicked() instanceof Villager)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDealerDamage(EntityDamageEvent e) {
        if (dealer.is(e.getEntity())) e.setCancelled(true);
    }

    // ---------- Fisch-Items schützen ----------

    @EventHandler(ignoreCancelled = true)
    public void onSmelt(FurnaceStartSmeltEvent e) {
        if (AngelItems.isFish(e.getSource())) e.setTotalCookTime(Integer.MAX_VALUE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCook(BlockCookEvent e) {
        if (AngelItems.isFish(e.getSource())) e.setCancelled(true);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (AngelItems.isFish(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }
}
