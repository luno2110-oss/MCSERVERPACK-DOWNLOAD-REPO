package de.kuby.kubyplugins.weapon;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.cinema.Cinema;
import de.kuby.kubyplugins.cinema.V3;
import de.kuby.kubyplugins.cinema.scenes.SwordInStone;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Eigene Waffen. Die erste: das Heilige Schwert. Es steckt in einem Stein (z. B. auf einem Schlachtfeld); wer den
 * Bereich um den Stein betritt, sieht die Cutscene „Das Schwert im Stein“ und bekommt danach das Schwert.
 *
 * <p>Fähigkeit „Sternenschlag“ (Rechtsklick, Abklingzeit): ein Strahl aus dem Himmel schlägt beim Ziel ein (Schaden
 * und Rückstoß im Umkreis, Schutzgebiete und PvP-Regeln gelten). Gegen Untote macht das Schwert mehr Schaden.
 *
 * <p>Befehle: /schwert setzen | entfernen | testen | ansehen | geben &lt;Spieler&gt; | zuruecksetzen &lt;Spieler&gt;
 * und /kino stop (Admin).
 */
public final class WeaponService implements Listener, CommandExecutor, TabCompleter {

    public static final String HOLY = "heiliges_schwert";
    private static final String CFG = "waffen.schwert-im-stein.";

    private final KubyPlugins plugin;
    private final NamespacedKey weaponKey, pulledKey, partKey;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Map<UUID, Long> triggerBlock = new HashMap<>();
    private Location stone;
    private float stoneYaw;
    private final List<UUID> parts = new ArrayList<>();

    public WeaponService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.weaponKey = new NamespacedKey(plugin, "weapon");
        this.pulledKey = new NamespacedKey(plugin, "holy_sword_pulled");
        this.partKey = new NamespacedKey(plugin, "sword_stone");
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::ambient, 40L, 10L);
    }

    // =================================================================================================================
    // Waffe
    // =================================================================================================================

    public ItemStack holySword() {
        ItemStack it = ItemStack.of(Material.NETHERITE_SWORD);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("kuby", "weapons/heiliges_schwert"));
        it.setData(DataComponentTypes.CUSTOM_NAME, Msg.parse("<!i><gradient:#FFF6D6:#F2B829:#FFF6D6><b>Heiliges Schwert</b></gradient>"));
        it.setData(DataComponentTypes.RARITY, ItemRarity.EPIC);
        it.setData(DataComponentTypes.UNBREAKABLE);
        it.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
        List<Component> lore = new ArrayList<>();
        for (String l : List.of(
                "<!i><gray>Aus dem Stein gezogen. Der Himmel hat geantwortet.",
                "",
                "<!i><#F2DA90>✦ Sternenschlag <dark_gray>(Rechtsklick)",
                "<!i><gray>Ein Strahl aus dem All schlägt beim Ziel ein:",
                "<!i><gray>Schaden und Rückstoß im Umkreis von 3,5 Blöcken.",
                "<!i><dark_gray>Abklingzeit: " + cooldownSeconds() + " Sekunden",
                "",
                "<!i><#F2DA90>✦ Heiliges Licht",
                "<!i><gray>+50 % Schaden gegen Untote.",
                "<!i><gray>Unzerstörbar.")) {
            lore.add(l.isEmpty() ? Component.empty() : Msg.parse(l).decoration(TextDecoration.ITALIC, false));
        }
        it.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        it.editPersistentDataContainer(pdc -> pdc.set(weaponKey, PersistentDataType.STRING, HOLY));
        return it;
    }

    public String weaponOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
    }

    private int cooldownSeconds() {
        return plugin.getConfig().getInt("waffen.heiliges-schwert.abklingzeit", 40);
    }

    public boolean pulled(OfflinePlayer p) {
        return p.getPlayer() != null && Boolean.TRUE.equals(p.getPlayer().getPersistentDataContainer().get(pulledKey, PersistentDataType.BOOLEAN));
    }

    private void give(Player p) {
        var left = p.getInventory().addItem(holySword());
        for (ItemStack rest : left.values()) p.getWorld().dropItem(p.getLocation(), rest);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!HOLY.equals(weaponOf(p.getInventory().getItemInMainHand()))) return;
        if (Tag.ENTITY_TYPES_UNDEAD.isTagged(e.getEntityType())) {
            e.setDamage(e.getDamage() * 1.5);
            e.getEntity().getWorld().spawnParticle(Particle.END_ROD, e.getEntity().getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.03);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!HOLY.equals(weaponOf(p.getInventory().getItemInMainHand()))) return;
        if (Cinema.busy(p.getUniqueId())) return;
        long now = System.currentTimeMillis();
        long until = cooldown.getOrDefault(p.getUniqueId(), 0L);
        if (now < until) {
            p.sendActionBar(Msg.parse("<#F2DA90>✦ Sternenschlag <gray>wieder bereit in <white>" + ((until - now) / 1000 + 1) + " s"));
            return;
        }
        Block target = p.getTargetBlockExact(30, FluidCollisionMode.NEVER);
        Location at = target != null ? target.getLocation().add(0.5, 1.0, 0.5)
                : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(12));
        cooldown.put(p.getUniqueId(), now + cooldownSeconds() * 1000L);
        p.setCooldown(p.getInventory().getItemInMainHand(), cooldownSeconds() * 20);
        starStrike(p, at);
    }

    /** Sternenschlag: kurzes Aufladen am Himmel, Strahl, Einschlag mit Druckwelle. */
    private void starStrike(Player p, Location at) {
        World w = at.getWorld();
        Location top = at.clone().add(0, 48, 0);
        w.playSound(at, "kuby:cine.charge", 1.2f, 1.4f);
        List<Entity> fx = new ArrayList<>();
        ItemDisplay star = w.spawn(top, ItemDisplay.class, d -> {
            fxBase(d, "kuby:fx/star", 0xFFF4D8);
            d.setTransformation(tf(0, 0, 0, 0.1f, 0));
        });
        fx.add(star);
        ItemDisplay halo = w.spawn(top, ItemDisplay.class, d -> {
            fxBase(d, "kuby:fx/glow", 0xFFD98A);
            d.setBillboard(Display.Billboard.CENTER);
            d.setTransformation(tf(0, 0, 0, 0.1f, 0));
        });
        fx.add(halo);
        // aufladen
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            grow(star, tf(0, 0, 0, 5f, 0), 14);
            grow(halo, tf(0, 0, 0, 12f, 0), 14);
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Strahl von oben bis zum Boden
            ItemDisplay beam = w.spawn(top, ItemDisplay.class, d -> {
                fxBase(d, "kuby:fx/beam", 0xFFFFFF);
                d.setTransformation(tf(0, -1, 0, 1.2f, 2));
            });
            ItemDisplay glow = w.spawn(top, ItemDisplay.class, d -> {
                fxBase(d, "kuby:fx/beam_glow", 0xFFD27A);
                d.setTransformation(tf(0, -1, 0, 3f, 2));
            });
            fx.add(beam);
            fx.add(glow);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                grow(beam, tf(0, -24, 0, 1.2f, 48), 3);
                grow(glow, tf(0, -24, 0, 3f, 48), 3);
            }, 1L);
            w.playSound(at, "kuby:cine.beam", 1.6f, 1.2f);
        }, 16L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Einschlag
            w.playSound(at, "kuby:cine.impact_small", 2f, 1f);
            w.playSound(at, "minecraft:entity.generic.explode", 1.2f, 0.7f);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
            w.spawnParticle(Particle.END_ROD, at, 60, 1.2, 1.5, 1.2, 0.25);
            w.spawnParticle(Particle.DUST, at, 50, 1.6, 0.4, 1.6, new Particle.DustOptions(Color.fromRGB(0xFFD45A), 2.2f));
            ItemDisplay ring = w.spawn(at.clone().add(0, -0.85, 0), ItemDisplay.class, d -> {
                fxBase(d, "kuby:fx/ring_4", 0xFFF1D0);
                d.setTransformation(tf(0, 0, 0, 0.5f, 0));
            });
            fx.add(ring);
            Bukkit.getScheduler().runTaskLater(plugin, () -> grow(ring, tf(0, 0, 0, 9f, 0), 8), 1L);
            for (Entity e : w.getNearbyEntities(at, 3.5, 3, 3.5)) {
                if (!(e instanceof LivingEntity le) || e.equals(p) || e instanceof org.bukkit.entity.ArmorStand) continue;
                if (le instanceof Player o && (o.getGameMode() == GameMode.SPECTATOR || o.getGameMode() == GameMode.CREATIVE)) continue;
                double dmg = plugin.getConfig().getDouble("waffen.heiliges-schwert.sternenschlag-schaden", 12);
                if (Tag.ENTITY_TYPES_UNDEAD.isTagged(le.getType())) dmg *= 1.5;
                le.damage(dmg, p);
                Vector push = le.getLocation().toVector().subtract(at.toVector()).setY(0);
                if (push.lengthSquared() > 0.01) push.normalize();
                le.setVelocity(le.getVelocity().add(push.multiply(0.9)).setY(0.75));
            }
        }, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Entity e : fx) if (e.isValid()) e.remove();
        }, 34L);
    }

    private static Transformation tf(float tx, float ty, float tz, float s, float len) {
        Vector3f scale = len > 0 ? new Vector3f(s, len, s) : new Vector3f(s, s, s);
        if (len > 0 && s <= 0) scale = new Vector3f(0.1f, len, 0.1f);
        return new Transformation(new Vector3f(tx, ty, tz), new AxisAngle4f(), scale, new AxisAngle4f());
    }

    private static void grow(Display d, Transformation t, int ticks) {
        if (!d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(t);
    }

    private static void fxBase(ItemDisplay d, String model, int rgb) {
        ItemStack it = ItemStack.of(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key(model));
        it.setData(DataComponentTypes.DYED_COLOR, io.papermc.paper.datacomponent.item.DyedItemColor.dyedItemColor(Color.fromRGB(rgb)));
        d.setItemStack(it);
        d.setPersistent(false);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(4f);
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
    }

    // =================================================================================================================
    // Schwert im Stein
    // =================================================================================================================

    /** Ort des Schwerts im Stein (null = keins aufgestellt). */
    public Location stoneLocation() {
        return stone == null ? null : stone.clone();
    }

    private void load() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("waffen.schwert-im-stein");
        if (c == null || !c.getBoolean("aktiv", false)) return;
        World w = Bukkit.getWorld(c.getString("welt", "world"));
        if (w == null) return;
        stone = new Location(w, c.getDouble("x"), c.getDouble("y"), c.getDouble("z"));
        stoneYaw = (float) c.getDouble("ausrichtung", 0);
        for (String s : c.getStringList("teile")) {
            try {
                parts.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // ungültig
            }
        }
    }

    private void saveStone() {
        if (stone == null) {
            plugin.getConfig().set(CFG + "aktiv", false);
        } else {
            plugin.getConfig().set(CFG + "aktiv", true);
            plugin.getConfig().set(CFG + "welt", stone.getWorld().getName());
            plugin.getConfig().set(CFG + "x", stone.getX());
            plugin.getConfig().set(CFG + "y", stone.getY());
            plugin.getConfig().set(CFG + "z", stone.getZ());
            plugin.getConfig().set(CFG + "ausrichtung", stoneYaw);
        }
        List<String> l = new ArrayList<>();
        for (UUID u : parts) l.add(u.toString());
        plugin.getConfig().set(CFG + "teile", l);
        plugin.saveConfig();
    }

    private List<Entity> partEntities() {
        List<Entity> out = new ArrayList<>();
        for (UUID u : parts) {
            Entity e = Bukkit.getEntity(u);
            if (e != null && e.isValid()) out.add(e);
        }
        return out;
    }

    private int radius() {
        return plugin.getConfig().getInt(CFG + "radius", 7);
    }

    /** Stein mit Schwert auf den Block, auf den p schaut. */
    private void place(Player p) {
        Block b = p.getTargetBlockExact(20, FluidCollisionMode.NEVER);
        if (b == null) {
            Msg.send(p, "<red>Schau auf den Boden, wo der Stein stehen soll.");
            return;
        }
        remove();
        Location s = b.getLocation().add(0.5, 1.0, 0.5);
        stone = s;
        stoneYaw = Math.round(p.getLocation().getYaw() / 15f) * 15f;
        World w = s.getWorld();
        Location rock = s.clone().add(0, SwordInStone.ROCK / 2, 0);
        rock.setYaw(stoneYaw);
        ItemDisplay st = w.spawn(rock, ItemDisplay.class, d -> part(d, "kuby:cine/stone", false));
        Location sw = s.clone().add(0, SwordInStone.SWORD, 0);
        sw.setYaw(stoneYaw);
        ItemDisplay sword = w.spawn(sw, ItemDisplay.class, d -> part(d, "kuby:cine/sword", true));
        TextDisplay holo = w.spawn(s.clone().add(0, 2.55, 0), TextDisplay.class, d -> {
            d.text(Msg.parse("<gradient:#FFF6D6:#F2B829><b>✦ Das Schwert im Stein ✦</b></gradient>\n<gray>Nur wer würdig ist, kann es ziehen."));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.getPersistentDataContainer().set(partKey, PersistentDataType.BOOLEAN, true);
        });
        parts.clear();
        parts.add(st.getUniqueId());
        parts.add(sword.getUniqueId());
        parts.add(holo.getUniqueId());
        Block center = s.getBlock();
        if (center.getType().isAir()) center.setType(Material.BARRIER);
        saveStone();
        Msg.send(p, "<green>Das Schwert im Stein steht. Wer näher als " + radius() + " Blöcke kommt, sieht die Cutscene.");
    }

    private void part(ItemDisplay d, String model, boolean glow) {
        ItemStack it = ItemStack.of(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key(model));
        d.setItemStack(it);
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        if (glow) d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(4f);
        d.getPersistentDataContainer().set(partKey, PersistentDataType.BOOLEAN, true);
    }

    private void remove() {
        for (Entity e : partEntities()) e.remove();
        if (stone != null) {
            for (Entity e : stone.getWorld().getNearbyEntities(stone, 4, 4, 4)) {
                if (e.getPersistentDataContainer().has(partKey)) e.remove();
            }
            Block center = stone.getBlock();
            if (center.getType() == Material.BARRIER) center.setType(Material.AIR);
        }
        parts.clear();
        stone = null;
    }

    /** Glitzern am Schwert, solange es im Stein steckt. */
    private void ambient() {
        if (stone == null || !stone.isChunkLoaded()) return;
        Location g = stone.clone().add(0, SwordInStone.SWORD + 0.4, 0);
        stone.getWorld().spawnParticle(Particle.END_ROD, g, 1, 0.15, 0.35, 0.15, 0.004);
        stone.getWorld().spawnParticle(Particle.DUST, g, 2, 0.3, 0.5, 0.3, new Particle.DustOptions(Color.fromRGB(0xFFE7A8), 0.8f));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (stone == null || !e.hasChangedBlock()) return;
        Player p = e.getPlayer();
        if (!p.getWorld().equals(stone.getWorld()) || Cinema.busy(p.getUniqueId())) return;
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return;
        Location to = e.getTo();
        double dx = to.getX() - stone.getX(), dz = to.getZ() - stone.getZ();
        if (dx * dx + dz * dz > radius() * radius() || Math.abs(to.getY() - stone.getY()) > 4) return;
        if (pulled(p)) return;
        Long block = triggerBlock.get(p.getUniqueId());
        if (block != null && System.currentTimeMillis() < block) return;
        triggerBlock.put(p.getUniqueId(), System.currentTimeMillis() + 15000);
        play(p, true);
    }

    /** Cutscene abspielen; reward: danach das Schwert geben (erstes Mal). */
    public boolean play(Player p, boolean reward) {
        if (stone == null || plugin.cinema() == null) return false;
        if (!plugin.uiPack().active(p)) {
            // ohne Grafik-Pack keine Szene (Modelle, Schrift und Sounds kommen aus dem Pack): Schwert direkt
            if (!reward) {
                Msg.send(p, "<gray>Die Szene gibt es nur mit dem Grafik-Pack.");
                return false;
            }
            p.getPersistentDataContainer().set(pulledKey, PersistentDataType.BOOLEAN, true);
            give(p);
            Sfx.play(plugin, p, Sfx.Kind.SUCCESS_BIG);
            Msg.send(p, "<gradient:#FFF6D6:#F2B829><b>✦ Heiliges Schwert ✦</b></gradient> <gray>gehört jetzt dir. Mit dem Grafik-Pack "
                    + "siehst du dabei eine Szene (<white>/schwert ansehen</white><gray>).");
            return true;
        }
        V3 s = new V3(stone.getX(), stone.getY(), stone.getZ());
        Location pl = p.getLocation();
        V3 toward = new V3(pl.getX() - s.x(), 0, pl.getZ() - s.z());
        double dist = Math.hypot(toward.x(), toward.z());
        SwordInStone script = new SwordInStone(s, toward, dist + 3, p.getUniqueId().getLeastSignificantBits(), stoneYaw);
        Cinema.Options o = new Cinema.Options();
        o.anchor = stone.clone();
        o.shared.addAll(partEntities());
        V3 end = script.endPoint();
        o.endAt = new Location(stone.getWorld(), end.x(), stone.getY(), end.z(), script.endYaw(), 10);
        o.skippable = !reward;
        o.onEnd = (pp, res) -> {
            if (!reward || res != Cinema.Result.DONE) return;
            pp.getPersistentDataContainer().set(pulledKey, PersistentDataType.BOOLEAN, true);
            give(pp);
            Sfx.play(plugin, pp, Sfx.Kind.SUCCESS_BIG);
            Msg.send(pp, "<gradient:#FFF6D6:#F2B829><b>✦ Heiliges Schwert ✦</b></gradient> <gray>gehört jetzt dir. Rechtsklick: <#F2DA90>Sternenschlag");
            for (Player o2 : Bukkit.getOnlinePlayers()) {
                if (!o2.equals(pp)) Msg.send(o2, "<#F2DA90>✦ " + Msg.escape(pp.getName()) + " <gray>hat das <#F2DA90>Heilige Schwert <gray>aus dem Stein gezogen!");
            }
        };
        return plugin.cinema().play(p, script, o);
    }

    // =================================================================================================================
    // Befehle
    // =================================================================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("kino")) {
            if (sender instanceof Player p && p.hasPermission("kuby.admin")) {
                if (plugin.cinema() != null && plugin.cinema().active(p)) plugin.cinema().stop(p, Cinema.Result.SKIPPED);
                else Msg.send(p, "<gray>Gerade läuft keine Szene.");
            }
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur im Spiel.");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = p.hasPermission("kuby.admin");
        switch (sub) {
            case "ansehen" -> {
                if (!pulled(p) && !admin) Msg.send(p, "<gray>Zieh das Schwert erst selbst aus dem Stein.");
                else if (stone == null) Msg.send(p, "<gray>Es steht gerade kein Schwert im Stein.");
                else if (!play(p, false)) Msg.send(p, "<red>Das geht gerade nicht.");
            }
            case "setzen" -> {
                if (admin) place(p);
            }
            case "entfernen" -> {
                if (!admin) return true;
                remove();
                saveStone();
                Msg.send(p, "<gray>Schwert im Stein entfernt.");
            }
            case "testen" -> {
                if (!admin) return true;
                if (stone == null) Msg.send(p, "<red>Erst mit /schwert setzen aufstellen.");
                else play(p, false);
            }
            case "geben" -> {
                if (!admin) return true;
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : p;
                if (t == null) Msg.send(p, "<red>Spieler nicht online.");
                else {
                    give(t);
                    Msg.send(p, "<green>Heiliges Schwert an " + t.getName() + " gegeben.");
                }
            }
            case "zuruecksetzen" -> {
                if (!admin) return true;
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : p;
                if (t == null) Msg.send(p, "<red>Spieler nicht online.");
                else {
                    t.getPersistentDataContainer().remove(pulledKey);
                    triggerBlock.remove(t.getUniqueId());
                    Msg.send(p, "<green>" + t.getName() + " kann das Schwert wieder ziehen.");
                }
            }
            default -> {
                Msg.send(p, "<#F2DA90><b>Heiliges Schwert</b>");
                Msg.send(p, stone == null ? "<gray>Es steckt gerade in keinem Stein." : pulled(p)
                        ? "<gray>Du hast es schon gezogen. <click:run_command:'/schwert ansehen'><#F2DA90>[Szene noch mal ansehen]</click>"
                        : "<gray>Es steckt in einem Stein bei <white>" + stone.getBlockX() + " " + stone.getBlockY() + " " + stone.getBlockZ()
                        + "<gray>. Geh hin, wenn du dich traust.");
                if (admin) Msg.send(p, "<dark_gray>Admin: /schwert setzen | entfernen | testen | geben <Spieler> | zuruecksetzen <Spieler>");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("kino")) {
            if (args.length == 1) out.add("stop");
            return out;
        }
        if (args.length == 1) {
            List<String> subs = sender.hasPermission("kuby.admin") ? List.of("ansehen", "setzen", "entfernen", "testen", "geben", "zuruecksetzen")
                    : List.of("ansehen");
            for (String s : subs) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && sender.hasPermission("kuby.admin")) {
            for (Player o : Bukkit.getOnlinePlayers()) if (o.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(o.getName());
        }
        return out;
    }
}
