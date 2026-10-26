package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Was die Schalter im Reiter "Anzeige" und "Sonstiges" im Spiel tun (außer Scoreboard, Sounds, Actionbar):
 * <ul>
 * <li>Nachtsicht: dauerhafte Nachtsicht ohne Partikel, auch nach Tod, Milch und Weltwechsel.</li>
 * <li>Monster in der Nähe: Warnung in der Actionbar, solange Monster im Umkreis von 12 Blöcken sind.</li>
 * <li>Preise im Inventar: beim Wechsel des Gegenstands in der Hand dessen Verkaufswert in der Actionbar.</li>
 * <li>Team-Leuchten: über Teammitgliedern schwebt ein Marker, den nur das eigene Team sieht, auch durch Wände.</li>
 * <li>Deko-NPCs: Figuren mit der Markierung "deco" (/kp deko, Statuen) werden für diesen Spieler ausgeblendet.</li>
 * </ul>
 */
public final class DisplaySettings implements Listener {

    private static final int MONSTER_RANGE = 12;

    private final KubyPlugins plugin;
    private final NamespacedKey decoKey;
    private final Map<UUID, TextDisplay> markers = new HashMap<>();
    /** Geladene Deko-Figuren (über EntitiesLoadEvent und beim Start gesammelt). */
    private final Set<UUID> deco = new HashSet<>();

    public DisplaySettings(KubyPlugins plugin) {
        this.plugin = plugin;
        this.decoKey = new NamespacedKey(plugin, "deco");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
        for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) if (isDeco(e)) deco.add(e.getUniqueId());
        plugin.playerSettings().onChange((p, key) -> {
            switch (key) {
                case "nightvision" -> nightVision(p);
                case "npcs" -> decoVisibility(p);
                case "team.glow" -> markers();
                default -> {
                }
            }
        });
    }

    private boolean on(Player p, String key) {
        return plugin.playerSettings().get(p.getUniqueId(), key);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (on(p, "monster")) monsters(p);
        }
        markers();
    }

    // =================================================================================================================
    // Nachtsicht
    // =================================================================================================================

    private static boolean ours(PotionEffect e) {
        return e != null && e.getDuration() == PotionEffect.INFINITE_DURATION && !e.hasParticles() && !e.isAmbient();
    }

    public void nightVision(Player p) {
        PotionEffect cur = p.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (on(p, "nightvision")) {
            if (cur == null || cur.getDuration() != PotionEffect.INFINITE_DURATION) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0,
                        false, false, false));
            }
        } else if (ours(cur)) {
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }

    private void later(Player p, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) r.run();
        }, 2L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        later(p, () -> {
            nightVision(p);
            decoVisibility(p);
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        later(e.getPlayer(), () -> nightVision(e.getPlayer()));
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        later(e.getPlayer(), () -> {
            nightVision(e.getPlayer());
            decoVisibility(e.getPlayer());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getModifiedType() != PotionEffectType.NIGHT_VISION) return;
        if (e.getAction() == EntityPotionEffectEvent.Action.REMOVED || e.getAction() == EntityPotionEffectEvent.Action.CLEARED) {
            if (on(p, "nightvision")) later(p, () -> nightVision(p));
        }
    }

    // =================================================================================================================
    // Monster und Preise
    // =================================================================================================================

    private void monsters(Player p) {
        if (plugin.modes().isHub(p.getWorld())) return;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        int n = 0;
        for (Entity e : p.getNearbyEntities(MONSTER_RANGE, 6, MONSTER_RANGE)) {
            if (e instanceof Monster m && !m.isDead() && m.getLocation().distanceSquared(p.getLocation()) <= MONSTER_RANGE * MONSTER_RANGE) n++;
        }
        if (n == 0) return;
        boolean en = plugin.playerSettings().english(p.getUniqueId());
        Component text = Msg.parse("<#FC7A6A>⚠ <white>" + n + " <#FC7A6A>" + (en ? (n == 1 ? "monster nearby" : "monsters nearby")
                : "Monster in der Nähe"));
        plugin.hotbar().flash(p, text, 30, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!on(p, "prices") || plugin.modes().isHub(p.getWorld())) return;
        ItemStack it = p.getInventory().getItem(e.getNewSlot());
        if (it == null || it.getType().isAir()) return;
        long unit = plugin.sell().unitPrice(p.getUniqueId(), it);
        if (unit <= 0) return;
        boolean en = plugin.playerSettings().english(p.getUniqueId());
        String total = Msg.coins(unit * it.getAmount());
        String text = "<gray>" + (en ? "Sell value " : "Verkaufswert ") + "<gold>" + total + "$"
                + (it.getAmount() > 1 ? " <dark_gray>(" + it.getAmount() + " × " + Msg.coins(unit) + "$)" : "");
        plugin.hotbar().flash(p, Msg.parse(text), 50, 2);
    }

    // =================================================================================================================
    // Team-Marker
    // =================================================================================================================

    private TeamService.Team teamIn(Player p) {
        TeamService.Team t = plugin.teams().of(p.getUniqueId());
        if (t == null) return null;
        return t.mode().equals(plugin.modes().modeOf(p.getUniqueId())) ? t : null;
    }

    private void markers() {
        // Marker für jeden Spieler in einem Team (im Modus des Teams), sonst weg
        for (Player p : Bukkit.getOnlinePlayers()) {
            TeamService.Team t = teamIn(p);
            TextDisplay m = markers.get(p.getUniqueId());
            boolean want = t != null && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR;
            if (!want) {
                if (m != null) {
                    m.remove();
                    markers.remove(p.getUniqueId());
                }
                continue;
            }
            if (m == null || !m.isValid() || m.getWorld() != p.getWorld()) {
                if (m != null) m.remove();
                m = spawnMarker(p);
                markers.put(p.getUniqueId(), m);
            } else if (!p.equals(m.getVehicle())) {
                m.teleport(p.getLocation());
                p.addPassenger(m);
            }
        }
        markers.entrySet().removeIf(en -> {
            Player owner = Bukkit.getPlayer(en.getKey());
            if (owner == null || !en.getValue().isValid()) {
                en.getValue().remove();
                return true;
            }
            return false;
        });
        // wer sieht welchen Marker
        for (Map.Entry<UUID, TextDisplay> en : markers.entrySet()) {
            Player owner = Bukkit.getPlayer(en.getKey());
            TeamService.Team t = owner == null ? null : teamIn(owner);
            for (Player v : Bukkit.getOnlinePlayers()) {
                boolean see = t != null && !v.equals(owner) && t.members().contains(v.getUniqueId())
                        && v.getWorld() == owner.getWorld() && on(v, "team.glow") && v.canSee(owner);
                if (see) v.showEntity(plugin, en.getValue());
                else v.hideEntity(plugin, en.getValue());
            }
        }
    }

    private TextDisplay spawnMarker(Player p) {
        Location at = p.getLocation();
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setSeeThrough(true);
            t.setShadowed(false);
            t.setDefaultBackground(false);
            t.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            t.text(Msg.parse("<#6DE288>▼"));
            t.setTransformation(new Transformation(new Vector3f(0, 0.62f, 0), new AxisAngle4f(), new Vector3f(0.8f, 0.8f, 0.8f),
                    new AxisAngle4f()));
            t.setBrightness(new Display.Brightness(15, 15));
        });
        p.addPassenger(d);
        return d;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.hotbar().forget(e.getPlayer().getUniqueId());
        if (plugin.jewelShop() != null) plugin.jewelShop().forget(e.getPlayer().getUniqueId());
        if (plugin.battlepassScreen() != null) plugin.battlepassScreen().forget(e.getPlayer().getUniqueId());
        if (plugin.discordStatue() != null) plugin.discordStatue().forget(e.getPlayer().getUniqueId());
        TextDisplay m = markers.remove(e.getPlayer().getUniqueId());
        if (m != null) m.remove();
    }

    public void shutdown() {
        for (TextDisplay m : markers.values()) m.remove();
        markers.clear();
    }

    // =================================================================================================================
    // Deko-NPCs
    // =================================================================================================================

    public NamespacedKey decoKey() {
        return decoKey;
    }

    /** Markiert eine Figur als Deko (wird mit "Deko-NPCs: aus" ausgeblendet). */
    public void markDeco(Entity e) {
        e.getPersistentDataContainer().set(decoKey, PersistentDataType.BYTE, (byte) 1);
        deco.add(e.getUniqueId());
        for (Player p : Bukkit.getOnlinePlayers()) if (!on(p, "npcs")) p.hideEntity(plugin, e);
    }

    public boolean isDeco(Entity e) {
        return e.getPersistentDataContainer().has(decoKey, PersistentDataType.BYTE);
    }

    public void decoVisibility(Player p) {
        boolean show = on(p, "npcs");
        for (Iterator<UUID> it = deco.iterator(); it.hasNext(); ) {
            Entity e = Bukkit.getEntity(it.next());
            if (e == null) {
                it.remove();
                continue;
            }
            if (show) p.showEntity(plugin, e);
            else p.hideEntity(plugin, e);
        }
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (!isDeco(en)) continue;
            deco.add(en.getUniqueId());
            for (Player p : Bukkit.getOnlinePlayers()) if (!on(p, "npcs")) p.hideEntity(plugin, en);
        }
    }

    @EventHandler
    public void onUnload(EntitiesUnloadEvent e) {
        for (Entity en : e.getEntities()) deco.remove(en.getUniqueId());
    }

    /** Deko-Figuren im Umkreis (für /kp deko remove). */
    public Entity nearestDeco(Location at, double range) {
        Entity best = null;
        double bd = range * range;
        for (Entity e : at.getWorld().getNearbyEntities(at, range, range, range)) {
            if (!isDeco(e)) continue;
            double d = e.getLocation().distanceSquared(at);
            if (d <= bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }
}
