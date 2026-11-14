package de.kuby.kubyplugins.tools;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * OP-Werkzeuge und -Waffen für den Daily Shop und das Wochenend-Angebot. Jedes Stück trägt seine Art in den Plugin-Daten
 * (kuby_tool), die Fähigkeit hängt daran, nicht am Namen. Zusätzliche Blöcke (Bohrer, Baumfäller, Erntesense) laufen
 * über echte BlockBreak-Events: Grundstücke, Inseln, Regionen und Jobs sehen sie wie normales Abbauen und dürfen sie
 * verbieten.
 *
 * <ul>
 *   <li>bohrer3 / bohrer5: baut 3 x 3 bzw. 5 x 5 Blöcke quer zur Blickrichtung ab (Schleichen: nur einer)</li>
 *   <li>sellaxt_eisen/gold/diamant/netherit: Rechtsklick auf eine Kiste verkauft den Inhalt mit 1,5x / 2x / 3x / 4x
 *       (auf den normalen Verkaufspreis mit Verkaufs-Stufe), begrenzte Ladungen</li>
 *   <li>holzfaeller / waldfresser: fällt den ganzen Baum (bis 48 bzw. 200 Stämme, nur echte Bäume mit Laub)</li>
 *   <li>schmelzhacke: Erze und Sand kommen geschmolzen heraus</li>
 *   <li>erntesense: erntet reife Felder 3 x 3 und pflanzt neu</li>
 *   <li>vampirklinge: heilt um 20 % des Schadens; donnerklinge: 15 % Blitz mit 5 Extraschaden</li>
 *   <li>enterhaken: zieht dich zum Haken, wenn er im Block steckt</li>
 *   <li>buch_reparatur / buch_effizienz: Verzauberungsbücher (Reparatur, Effizienz V)</li>
 * </ul>
 */
public final class SpecialTools implements Listener {

    /** Art, Grund-Material, Name, Beschreibung. */
    public record Def(String id, Material material, String name, String color, List<String> lore) {}

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    static {
        def("bohrer3", Material.DIAMOND_PICKAXE, "3x3-Bohrer", "#5CE1FF",
                "Baut 3 x 3 Blöcke auf einmal ab", "Schleichen: nur ein Block");
        def("bohrer5", Material.NETHERITE_PICKAXE, "5x5-Bohrer", "#FF5CD6",
                "Baut 5 x 5 Blöcke auf einmal ab", "Schleichen: nur ein Block");
        def("sellaxt_eisen", Material.IRON_AXE, "Sell-Axt (Eisen)", "#D8D8D8",
                "Rechtsklick auf eine Kiste: Inhalt verkaufen", "Gewinn x1,5");
        def("sellaxt_gold", Material.GOLDEN_AXE, "Sell-Axt (Gold)", "#FFD25A",
                "Rechtsklick auf eine Kiste: Inhalt verkaufen", "Gewinn x2");
        def("sellaxt_diamant", Material.DIAMOND_AXE, "Sell-Axt (Diamant)", "#5CE1FF",
                "Rechtsklick auf eine Kiste: Inhalt verkaufen", "Gewinn x3");
        def("sellaxt_netherit", Material.NETHERITE_AXE, "Sell-Axt (Netherit)", "#B98BFF",
                "Rechtsklick auf eine Kiste: Inhalt verkaufen", "Gewinn x4");
        def("holzfaeller", Material.IRON_AXE, "Holzfäller-Axt", "#7CD47C",
                "Fällt den ganzen Baum (bis 48 Stämme)", "Schleichen: nur ein Block");
        def("waldfresser", Material.DIAMOND_AXE, "Waldfresser", "#3FE07A",
                "Fällt den ganzen Baum (bis 200 Stämme)", "Auch Riesenbäume · Schleichen: nur ein Block");
        def("schmelzhacke", Material.DIAMOND_PICKAXE, "Schmelz-Spitzhacke", "#FF8A3D",
                "Erze kommen als Barren heraus", "Sand wird zu Glas");
        def("erntesense", Material.NETHERITE_HOE, "Erntesense", "#B6E35C",
                "Erntet reife Felder 3 x 3", "und pflanzt gleich neu");
        def("vampirklinge", Material.NETHERITE_SWORD, "Vampirklinge", "#FF4D6D",
                "Heilt dich um 20 % des Schadens");
        def("donnerklinge", Material.DIAMOND_SWORD, "Donnerklinge", "#7FB2FF",
                "15 %: Blitz mit 5 Extraschaden");
        def("enterhaken", Material.FISHING_ROD, "Enterhaken", "#E3C35C",
                "Steckt der Haken im Block, zieht er dich hin");
        def("buch_reparatur", Material.ENCHANTED_BOOK, "Buch: Reparatur", "#C7A5FF",
                "Reparatur (Mending)");
        def("buch_effizienz", Material.ENCHANTED_BOOK, "Buch: Effizienz V", "#C7A5FF",
                "Effizienz V");
    }

    private static void def(String id, Material m, String name, String color, String... lore) {
        DEFS.put(id, new Def(id, m, name, color, List.of(lore)));
    }

    public static Def def(String id) {
        return DEFS.get(id);
    }

    public static Map<String, Def> all() {
        return DEFS;
    }

    /** Ladungen der Sell-Äxte. */
    private static final Map<String, Integer> USES = Map.of("sellaxt_eisen", 64, "sellaxt_gold", 128,
            "sellaxt_diamant", 256, "sellaxt_netherit", 512);
    private static final Map<String, Double> SELL_FACTOR = Map.of("sellaxt_eisen", 1.5, "sellaxt_gold", 2.0,
            "sellaxt_diamant", 3.0, "sellaxt_netherit", 4.0);

    private final KubyPlugins plugin;
    private final NamespacedKey key, usesKey;
    /** Zuletzt angeklickte Seite je Spieler (für die Bohr-Ebene). */
    private final Map<UUID, BlockFace> face = new HashMap<>();
    private final Map<UUID, Long> grappleAt = new HashMap<>();
    /** Gerade laufende Zusatz-Abbauten (keine Rekursion). */
    private boolean working;

    public SpecialTools(KubyPlugins plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "kuby_tool");
        this.usesKey = new NamespacedKey(plugin, "kuby_tool_uses");
    }

    // =================================================================================================================
    // Items
    // =================================================================================================================

    public ItemStack create(String id, int amount) {
        Def d = DEFS.get(id);
        if (d == null) return null;
        ItemStack it = new ItemStack(d.material(), Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.item("<" + d.color() + "><b>" + d.name() + "</b>"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String l : d.lore()) lore.add(Msg.item("<gray>" + Msg.escape(l)));
        Integer uses = USES.get(id);
        if (uses != null) {
            m.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, uses);
            lore.add(Msg.item("<dark_gray>Ladungen: <white>" + uses + "</white>/" + uses));
        }
        lore.add(Msg.item("<#FCD45D>✦ Shop-Werkzeug"));
        m.lore(lore);
        m.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        switch (id) {
            case "bohrer3", "bohrer5", "schmelzhacke" -> {
                m.addEnchant(Enchantment.EFFICIENCY, id.equals("bohrer5") ? 6 : 5, true);
                m.addEnchant(Enchantment.UNBREAKING, 3, true);
            }
            case "holzfaeller", "waldfresser" -> {
                m.addEnchant(Enchantment.EFFICIENCY, 4, true);
                m.addEnchant(Enchantment.UNBREAKING, 3, true);
            }
            case "erntesense" -> m.addEnchant(Enchantment.UNBREAKING, 3, true);
            case "vampirklinge" -> {
                m.addEnchant(Enchantment.SHARPNESS, 5, true);
                m.addEnchant(Enchantment.UNBREAKING, 3, true);
            }
            case "donnerklinge" -> {
                m.addEnchant(Enchantment.SHARPNESS, 4, true);
                m.addEnchant(Enchantment.UNBREAKING, 3, true);
            }
            case "enterhaken" -> m.addEnchant(Enchantment.UNBREAKING, 3, true);
            default -> { }
        }
        if (m instanceof EnchantmentStorageMeta book) {
            if (id.equals("buch_reparatur")) book.addStoredEnchant(Enchantment.MENDING, 1, true);
            else if (id.equals("buch_effizienz")) book.addStoredEnchant(Enchantment.EFFICIENCY, 5, true);
        }
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    /** Werkzeug-Art eines Items oder null. */
    public String kind(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    // =================================================================================================================
    // Bohrer, Baumfäller, Erntesense, Schmelzhacke
    // =================================================================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeftClick(PlayerInteractEvent e) {
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getBlockFace() != null) face.put(e.getPlayer().getUniqueId(), e.getBlockFace());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (working) return;
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        String k = kind(tool);
        if (k == null) return;
        Block b = e.getBlock();
        switch (k) {
            case "bohrer3", "bohrer5" -> {
                if (p.isSneaking() || !b.isPreferredTool(tool)) return;
                drill(p, tool, b, k.equals("bohrer3") ? 1 : 2);
            }
            case "holzfaeller", "waldfresser" -> {
                if (p.isSneaking() || !Tag.LOGS.isTagged(b.getType())) return;
                fell(p, tool, b, k.equals("holzfaeller") ? 48 : 200);
            }
            case "erntesense" -> {
                if (!(b.getBlockData() instanceof Ageable)) return;
                harvest(p, tool, b);
            }
            default -> { }
        }
    }

    private void drill(Player p, ItemStack tool, Block center, int r) {
        BlockFace f = face.getOrDefault(p.getUniqueId(), BlockFace.UP);
        List<Block> list = new ArrayList<>();
        for (int a = -r; a <= r; a++) {
            for (int c = -r; c <= r; c++) {
                if (a == 0 && c == 0) continue;
                Block t = switch (f) {
                    case UP, DOWN -> center.getRelative(a, 0, c);
                    case NORTH, SOUTH -> center.getRelative(a, c, 0);
                    default -> center.getRelative(0, c, a);
                };
                list.add(t);
            }
        }
        int broken = breakAll(p, tool, list, t -> !t.getType().isAir() && t.getType().getHardness() >= 0
                && t.getType().getHardness() <= 50 && t.isPreferredTool(tool) && !(t.getState() instanceof Container));
        if (broken > 0) p.damageItemStack(tool, Math.max(1, broken / 3));
    }

    private void fell(Player p, ItemStack tool, Block start, int limit) {
        // nur echte Bäume: Laub (nicht vom Spieler gesetzt) in der Nähe der Stämme
        Set<Block> logs = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        boolean leaves = false;
        while (!queue.isEmpty() && logs.size() < limit + 1) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = b.getRelative(dx, dy, dz);
                        if (Tag.LOGS.isTagged(n.getType()) && n.getType() == start.getType() && logs.add(n)) queue.add(n);
                        else if (!leaves && Tag.LEAVES.isTagged(n.getType())
                                && n.getBlockData() instanceof org.bukkit.block.data.type.Leaves lv && !lv.isPersistent()) leaves = true;
                    }
                }
            }
        }
        logs.remove(start);
        if (!leaves || logs.isEmpty()) return;
        List<Block> list = new ArrayList<>(logs);
        list.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        if (list.size() > limit) list = list.subList(0, limit);
        int broken = breakAll(p, tool, list, t -> Tag.LOGS.isTagged(t.getType()));
        if (broken > 0) {
            p.damageItemStack(tool, Math.max(1, broken / 4));
            p.getWorld().playSound(start.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 0.7f);
        }
    }

    private void harvest(Player p, ItemStack tool, Block center) {
        List<Block> list = new ArrayList<>();
        list.add(center);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) list.add(center.getRelative(dx, 0, dz));
        for (Block b : list) {
            if (!(b.getBlockData() instanceof Ageable age) || age.getAge() < age.getMaximumAge()) continue;
            Material seed = b.getType();
            if (b != center) {
                working = true;
                try {
                    BlockBreakEvent ev = new BlockBreakEvent(b, p);
                    plugin.getServer().getPluginManager().callEvent(ev);
                    if (ev.isCancelled()) continue;
                    b.breakNaturally(tool, true);
                } finally {
                    working = false;
                }
            }
            Block target = b;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (target.getType().isAir() && target.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND
                        || target.getType().isAir() && seed == Material.NETHER_WART) {
                    target.setType(seed);
                }
            });
        }
    }

    /** Baut die Blöcke ab, wenn kein Schutz-Plugin etwas dagegen hat. Gibt die Zahl der abgebauten Blöcke zurück. */
    private int breakAll(Player p, ItemStack tool, List<Block> blocks, java.util.function.Predicate<Block> ok) {
        int n = 0;
        working = true;
        try {
            for (Block t : blocks) {
                if (!ok.test(t)) continue;
                BlockBreakEvent ev = new BlockBreakEvent(t, p);
                plugin.getServer().getPluginManager().callEvent(ev);
                if (ev.isCancelled()) continue;
                if (t.breakNaturally(tool, true)) n++;
            }
        } finally {
            working = false;
        }
        return n;
    }

    private static final Map<Material, Material> SMELT = Map.of(Material.RAW_IRON, Material.IRON_INGOT,
            Material.RAW_GOLD, Material.GOLD_INGOT, Material.RAW_COPPER, Material.COPPER_INGOT,
            Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP, Material.SAND, Material.GLASS, Material.RED_SAND, Material.GLASS,
            Material.COBBLESTONE, Material.STONE, Material.CLAY_BALL, Material.BRICK);

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        if (!"schmelzhacke".equals(kind(e.getPlayer().getInventory().getItemInMainHand()))) return;
        boolean any = false;
        for (Item drop : e.getItems()) {
            ItemStack s = drop.getItemStack();
            Material to = SMELT.get(s.getType());
            if (to == null) continue;
            drop.setItemStack(new ItemStack(to, s.getAmount()));
            any = true;
        }
        if (any) e.getBlock().getWorld().spawnParticle(Particle.FLAME, e.getBlock().getLocation().add(0.5, 0.5, 0.5), 6, 0.2, 0.2, 0.2, 0.01);
    }

    // =================================================================================================================
    // Sell-Axt
    // =================================================================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSellAxe(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack tool = e.getItem();
        String k = kind(tool);
        if (k == null || !SELL_FACTOR.containsKey(k)) return;
        Block b = e.getClickedBlock();
        if (b == null || !(b.getState() instanceof Container box)) return;
        // Schutz: Grundstücke und Inseln haben den Klick schon erlaubt (sonst wäre das Event abgebrochen). Kistenläden
        // und andere Schutz-Plugins prüfen das Öffnen: die Kiste einmal öffnen lassen, wer es verbietet, verhindert den Verkauf
        e.setCancelled(true);
        Player p = e.getPlayer();
        org.bukkit.inventory.InventoryView view = p.openInventory(box.getInventory());
        if (view == null) {
            Msg.send(p, "<red>Diese Kiste darfst du nicht verkaufen.");
            return;
        }
        p.closeInventory();
        long paid = plugin.sell().sellContainer(p, box.getInventory(), SELL_FACTOR.get(k));
        if (paid <= 0) {
            Msg.send(p, "<gray>In dieser Kiste ist nichts, das sich verkaufen lässt.");
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            return;
        }
        Msg.send(p, "<green>Verkauft für <gold>" + Msg.coins(paid) + "$</gold> <gray>(Sell-Axt x"
                + de.kuby.kubyplugins.util.Msg.escape(String.valueOf(SELL_FACTOR.get(k)).replace(".0", "").replace('.', ',')) + ")");
        Sfx.play(plugin, p, Sfx.Kind.COIN);
        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 1, 0.5), 12, 0.3, 0.3, 0.3, 0);
        useCharge(p, tool);
    }

    private void useCharge(Player p, ItemStack tool) {
        ItemMeta m = tool.getItemMeta();
        Integer left = m.getPersistentDataContainer().get(usesKey, PersistentDataType.INTEGER);
        if (left == null) return;
        left--;
        if (left <= 0) {
            tool.setAmount(0);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            Msg.send(p, "<gray>Deine Sell-Axt ist aufgebraucht.");
            return;
        }
        m.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, left);
        List<net.kyori.adventure.text.Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        int max = USES.getOrDefault(kind(tool), left);
        for (int i = 0; i < lore.size(); i++) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(lore.get(i));
            if (plain.startsWith("Ladungen:")) lore.set(i, Msg.item("<dark_gray>Ladungen: <white>" + left + "</white>/" + max));
        }
        m.lore(lore);
        tool.setItemMeta(m);
    }

    // =================================================================================================================
    // Waffen und Enterhaken
    // =================================================================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target)) return;
        String k = kind(p.getInventory().getItemInMainHand());
        if (k == null) return;
        if (k.equals("vampirklinge")) {
            double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 20
                    : p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(max, p.getHealth() + e.getFinalDamage() * 0.2));
            p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 4, 0.2, 0.3, 0.2, 0);
        } else if (k.equals("donnerklinge") && ThreadLocalRandom.current().nextDouble() < 0.15) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            e.setDamage(e.getDamage() + 5);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.IN_GROUND && e.getState() != PlayerFishEvent.State.REEL_IN) return;
        Player p = e.getPlayer();
        if (!"enterhaken".equals(kind(p.getInventory().getItemInMainHand()))) return;
        Location hook = e.getHook().getLocation();
        boolean stuck = e.getState() == PlayerFishEvent.State.IN_GROUND
                || !hook.clone().add(0, -0.3, 0).getBlock().isPassable();
        if (!stuck) return;
        long now = System.currentTimeMillis();
        if (now - grappleAt.getOrDefault(p.getUniqueId(), 0L) < 1500) return;
        grappleAt.put(p.getUniqueId(), now);
        Vector v = hook.toVector().subtract(p.getLocation().toVector());
        double d = v.length();
        if (d < 1.5) return;
        v.normalize().multiply(Math.min(2.6, 0.6 + d * 0.12)).setY(Math.min(1.4, v.getY() * 0.9 + 0.45));
        p.setVelocity(v);
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.6f);
        p.setFallDistance(0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        face.remove(e.getPlayer().getUniqueId());
        grappleAt.remove(e.getPlayer().getUniqueId());
    }
}
