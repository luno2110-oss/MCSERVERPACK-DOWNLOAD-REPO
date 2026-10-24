package de.kuby.kubysurvival.jobs;

import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.entity.Animals;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Erkennt Job-Aktionen. Selbst gesetzte und von Generatoren erzeugte Blöcke werden im Chunk markiert und bringen
 * nichts (Markierung wandert mit Kolben mit). Pflanzen zählen nur ausgewachsen, Mobs nicht aus Spawnern oder Zucht.
 */
public final class JobListener implements Listener {

    /** Lohn je Block: XP und Coins. */
    private record Pay(int xp, int coins) {}

    private static final Map<Material, Pay> ORES = Map.ofEntries(
            Map.entry(Material.COAL_ORE, new Pay(4, 4)), Map.entry(Material.DEEPSLATE_COAL_ORE, new Pay(4, 4)),
            Map.entry(Material.COPPER_ORE, new Pay(4, 3)), Map.entry(Material.DEEPSLATE_COPPER_ORE, new Pay(4, 3)),
            Map.entry(Material.IRON_ORE, new Pay(6, 6)), Map.entry(Material.DEEPSLATE_IRON_ORE, new Pay(6, 6)),
            Map.entry(Material.GOLD_ORE, new Pay(8, 8)), Map.entry(Material.DEEPSLATE_GOLD_ORE, new Pay(8, 8)),
            Map.entry(Material.NETHER_GOLD_ORE, new Pay(5, 4)), Map.entry(Material.NETHER_QUARTZ_ORE, new Pay(4, 3)),
            Map.entry(Material.REDSTONE_ORE, new Pay(5, 4)), Map.entry(Material.DEEPSLATE_REDSTONE_ORE, new Pay(5, 4)),
            Map.entry(Material.LAPIS_ORE, new Pay(6, 6)), Map.entry(Material.DEEPSLATE_LAPIS_ORE, new Pay(6, 6)),
            Map.entry(Material.DIAMOND_ORE, new Pay(25, 30)), Map.entry(Material.DEEPSLATE_DIAMOND_ORE, new Pay(25, 30)),
            Map.entry(Material.EMERALD_ORE, new Pay(30, 40)), Map.entry(Material.DEEPSLATE_EMERALD_ORE, new Pay(30, 40)),
            Map.entry(Material.ANCIENT_DEBRIS, new Pay(50, 60)));
    /** Steine bringen nur XP (gegen Generator-Farmen zusätzlich markiert). */
    private static final Set<Material> STONES = Set.of(Material.STONE, Material.DEEPSLATE, Material.ANDESITE,
            Material.DIORITE, Material.GRANITE, Material.TUFF, Material.NETHERRACK, Material.BLACKSTONE,
            Material.BASALT, Material.END_STONE, Material.CALCITE);
    /** Gräber: XP und Coins je Block. */
    private static final Map<Material, Pay> DIG = Map.ofEntries(
            Map.entry(Material.DIRT, new Pay(1, 1)), Map.entry(Material.GRASS_BLOCK, new Pay(1, 1)),
            Map.entry(Material.COARSE_DIRT, new Pay(1, 1)), Map.entry(Material.ROOTED_DIRT, new Pay(1, 1)),
            Map.entry(Material.PODZOL, new Pay(2, 1)), Map.entry(Material.MYCELIUM, new Pay(2, 2)),
            Map.entry(Material.SAND, new Pay(2, 1)), Map.entry(Material.RED_SAND, new Pay(2, 2)),
            Map.entry(Material.GRAVEL, new Pay(2, 1)), Map.entry(Material.CLAY, new Pay(3, 2)),
            Map.entry(Material.MUD, new Pay(2, 1)), Map.entry(Material.SOUL_SAND, new Pay(2, 2)),
            Map.entry(Material.SOUL_SOIL, new Pay(2, 2)), Map.entry(Material.SNOW_BLOCK, new Pay(1, 1)));
    private static final Set<Material> CROPS = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART, Material.COCOA);
    private static final Set<Material> GROWN = Set.of(Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE,
            Material.CACTUS, Material.BAMBOO);
    private static final Set<SpawnReason> NO_PAY = Set.of(SpawnReason.SPAWNER, SpawnReason.SPAWNER_EGG,
            SpawnReason.TRIAL_SPAWNER, SpawnReason.BREEDING, SpawnReason.EGG, SpawnReason.DISPENSE_EGG,
            SpawnReason.BUILD_IRONGOLEM, SpawnReason.BUILD_SNOWMAN, SpawnReason.SLIME_SPLIT, SpawnReason.CUSTOM,
            SpawnReason.COMMAND);

    private final KubySurvival addon;
    private final JobService jobs;
    private final NamespacedKey placedKey;

    public JobListener(KubySurvival addon, JobService jobs) {
        this.addon = addon;
        this.jobs = jobs;
        this.placedKey = new NamespacedKey(addon, "job_placed");
    }

    private static boolean tracked(Material m) {
        return Tag.LOGS.isTagged(m) || ORES.containsKey(m) || STONES.contains(m) || GROWN.contains(m) || DIG.containsKey(m);
    }

    // ---------- Blöcke ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (tracked(e.getBlock().getType())) mark(e.getBlock(), true);
    }

    /** Bruchstein-, Stein- und Basalt-Generatoren: entstandene Blöcke zählen wie gesetzt. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        if (tracked(e.getNewState().getType())) mark(e.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material m = b.getType();
        Player p = e.getPlayer();
        if (CROPS.contains(m)) {
            if (b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge()) {
                jobs.reward(p, Job.FARMER, 2, 2);
                addon.quests().progress(p, "break", m, 1);
            }
            return;
        }
        if (!tracked(m)) return;
        if (isPlaced(b)) {
            mark(b, false);
            return;
        }
        addon.quests().progress(p, "break", m, 1);
        if (Tag.LOGS.isTagged(m)) {
            jobs.reward(p, Job.HOLZFAELLER, 3, 2);
        } else if (ORES.containsKey(m)) {
            Pay pay = ORES.get(m);
            jobs.reward(p, Job.MINER, pay.xp(), pay.coins());
        } else if (STONES.contains(m)) {
            jobs.reward(p, Job.MINER, 1, 0);
        } else if (DIG.containsKey(m)) {
            Pay pay = DIG.get(m);
            jobs.reward(p, Job.GRAEBER, pay.xp(), pay.coins());
        } else if (m == Material.MELON || m == Material.PUMPKIN) {
            jobs.reward(p, Job.FARMER, 3, 2);
        } else {
            jobs.reward(p, Job.FARMER, 1, 1);
        }
    }

    /** Fallender Sand und Kies: die Markierung wandert mit (sonst ließe sich gesetzter Sand "frisch" fallen lassen). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFall(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof FallingBlock)) return;
        Block b = e.getBlock();
        if (e.getTo() == Material.AIR) {
            if (isPlaced(b)) mark(b, false);
        } else if (tracked(e.getTo())) {
            mark(b, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        move(e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        move(e.getBlocks(), e.getDirection());
    }

    private void move(List<Block> blocks, BlockFace dir) {
        List<Block> marked = new ArrayList<>();
        for (Block b : blocks) if (isPlaced(b)) marked.add(b);
        for (Block b : marked) mark(b, false);
        for (Block b : marked) mark(b.getRelative(dir), true);
    }

    // ---------- Jagen und Angeln ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        Player p = mob.getKiller();
        if (p == null || mob instanceof Player || NO_PAY.contains(mob.getEntitySpawnReason())) return;
        addon.quests().progress(p, "kill", mob, 1);
        if (mob instanceof Monster) {
            int xp = switch (mob.getType()) {
                case ENDERMAN, WITCH, BLAZE, PIGLIN_BRUTE -> 8;
                case CREEPER -> 6;
                case ZOMBIE, SKELETON, HUSK, STRAY, DROWNED, WITHER_SKELETON -> 5;
                default -> 4;
            };
            jobs.reward(p, Job.JAEGER, xp, xp - 1);
        } else if (mob instanceof Animals) {
            jobs.reward(p, Job.JAEGER, 2, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (!(e.getBreeder() instanceof Player p)) return;
        jobs.reward(p, Job.ZUECHTER, 6, 5);
        addon.quests().progress(p, "breed", null, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent e) {
        if (!(e.getEntity() instanceof Sheep)) return;
        jobs.reward(e.getPlayer(), Job.ZUECHTER, 2, 2);
        addon.quests().progress(e.getPlayer(), "shear", null, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        int levels = e.getExpLevelCost();
        jobs.reward(e.getEnchanter(), Job.VERZAUBERER, levels * 4, levels * 4);
        addon.quests().progress(e.getEnchanter(), "enchant", null, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        // In Survival zahlt das Angeln (AngelListener) Job-XP nach Seltenheit; hier nur außerhalb davon.
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if ("survival".equals(addon.core().modes().modeOf(e.getPlayer().getUniqueId()))) return;
        jobs.reward(e.getPlayer(), Job.ANGLER, 5, 5);
        addon.quests().progress(e.getPlayer(), "fish", null, 1);
    }

    // ---------- Markierung im Chunk ----------

    private static long pack(Block b) {
        return (b.getX() & 15) | ((long) (b.getZ() & 15) << 4) | ((long) (b.getY() + 4096) << 8);
    }

    private boolean isPlaced(Block b) {
        long[] arr = b.getChunk().getPersistentDataContainer().get(placedKey, PersistentDataType.LONG_ARRAY);
        if (arr == null) return false;
        long key = pack(b);
        for (long v : arr) if (v == key) return true;
        return false;
    }

    private void mark(Block b, boolean placed) {
        Chunk c = b.getChunk();
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        long[] arr = pdc.get(placedKey, PersistentDataType.LONG_ARRAY);
        long key = pack(b);
        List<Long> list = new ArrayList<>();
        if (arr != null) for (long v : arr) if (v != key) list.add(v);
        if (placed) list.add(key);
        if (list.isEmpty()) {
            pdc.remove(placedKey);
        } else {
            long[] out = new long[list.size()];
            for (int i = 0; i < out.length; i++) out[i] = list.get(i);
            pdc.set(placedKey, PersistentDataType.LONG_ARRAY, out);
        }
    }
}
