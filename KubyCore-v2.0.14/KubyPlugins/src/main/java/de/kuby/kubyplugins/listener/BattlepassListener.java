package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.BattlepassService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.Tag;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Zählt Battle-Pass-Aufgaben: Kills (Ziel = Mob-Typ, MONSTER, ANIMAL, BOSS), Abbau (Block ohne DEEPSLATE_, ORE, LOG),
 * Drops (COLLECT, z. B. REDSTONE), Angeln, Züchten, Zähmen, Level, Essen, Craften, Verzaubern, Bauen, dazu jede Minute
 * Spielzeit und Reisen (Meter aus den Bewegungs-Statistiken, Teleports zählen nicht). Spieler-Kills, Duelle, Handel,
 * Kisten, Votes und die Tagesbelohnung melden ihre Dienste selbst.
 */
public final class BattlepassListener implements Listener {

    private static final Set<EntityType> BOSSES = Set.of(EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.WARDEN,
            EntityType.ELDER_GUARDIAN);
    private static final Statistic[] MOVES = {Statistic.WALK_ONE_CM, Statistic.SPRINT_ONE_CM, Statistic.CROUCH_ONE_CM,
            Statistic.SWIM_ONE_CM, Statistic.WALK_ON_WATER_ONE_CM, Statistic.WALK_UNDER_WATER_ONE_CM,
            Statistic.AVIATE_ONE_CM, Statistic.HORSE_ONE_CM, Statistic.BOAT_ONE_CM, Statistic.PIG_ONE_CM,
            Statistic.STRIDER_ONE_CM, Statistic.MINECART_ONE_CM, Statistic.CLIMB_ONE_CM};
    /** Höchstens so viele Meter je Minute (Elytra mit Raketen), alles darüber zählt nicht. */
    private static final long MAX_METERS_PER_MINUTE = 3000;

    private final KubyPlugins plugin;
    private final Map<UUID, Long> lastMoved = new HashMap<>();

    public BattlepassListener(KubyPlugins plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::minute, 1200L, 1200L);
    }

    private BattlepassService bp() {
        return plugin.battlepass();
    }

    private static boolean playing(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    /** Spielzeit (Minuten) und Reisen (Meter). */
    private void minute() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!playing(p)) {
                lastMoved.remove(id);
                continue;
            }
            bp().count(id, "PLAYTIME", 1);
            long cm = 0;
            for (Statistic s : MOVES) cm += p.getStatistic(s);
            Long before = lastMoved.put(id, cm);
            if (before != null && cm > before) {
                long meters = Math.min(MAX_METERS_PER_MINUTE, (cm - before) / 100);
                bp().count(id, "TRAVEL", meters);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastMoved.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || dead instanceof Player || !playing(killer)) return;
        List<String> tags = new ArrayList<>();
        tags.add(dead.getType().name());
        if (dead instanceof Enemy) tags.add("MONSTER");
        if (dead instanceof Animals) tags.add("ANIMAL");
        if (BOSSES.contains(dead.getType())) tags.add("BOSS");
        bp().count(killer.getUniqueId(), "KILL", 1, tags.toArray(new String[0]));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!playing(p)) return;
        Material m = e.getBlock().getType();
        String name = m.name();
        List<String> tags = new ArrayList<>();
        tags.add(name);
        if (name.startsWith("DEEPSLATE_")) tags.add(name.substring(10));
        if (name.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS) tags.add("ORE");
        if (Tag.LOGS.isTagged(m)) tags.add("LOG");
        bp().count(p.getUniqueId(), "MINE", 1, tags.toArray(new String[0]));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        if (!playing(p)) return;
        for (Item it : e.getItems()) {
            bp().count(p.getUniqueId(), "COLLECT", it.getItemStack().getAmount(), it.getItemStack().getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) bp().count(e.getPlayer().getUniqueId(), "FISH", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p) bp().count(p.getUniqueId(), "BREED", 1, e.getEntity().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (e.getOwner() instanceof Player p) bp().count(p.getUniqueId(), "TAME", 1, e.getEntity().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevel(PlayerLevelChangeEvent e) {
        if (e.getNewLevel() > e.getOldLevel() && playing(e.getPlayer())) {
            bp().count(e.getPlayer().getUniqueId(), "LEVELS", e.getNewLevel() - e.getOldLevel());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (e.getItem().getType().isEdible()) {
            bp().count(e.getPlayer().getUniqueId(), "EAT", 1, e.getItem().getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            bp().count(p.getUniqueId(), "CRAFT", Math.max(1, e.getRecipe().getResult().getAmount()),
                    e.getRecipe().getResult().getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        bp().count(e.getEnchanter().getUniqueId(), "ENCHANT", 1, e.getItem().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (playing(e.getPlayer())) {
            bp().count(e.getPlayer().getUniqueId(), "PLACE", 1, e.getBlock().getType().name());
        }
    }
}
