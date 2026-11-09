package de.kuby.kubyplugins.cinema;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WeatherType;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kino-Engine: spielt ein Drehbuch (Script) für einen Spieler ab. Die Kamera ist eine unsichtbare Anzeige-Entity, der
 * Spieler schaut im Zuschauermodus durch sie (weich, weil der Client Anzeigen zwischen den Ticks gleiten lässt). Der
 * Spieler selbst spielt mit: eine Figur mit seinem Skin. Alles Gezeigte (Figur, Teile, Risse im Boden, Himmel) sieht nur
 * der Zuschauer. Danach ist alles wie vorher (Spielmodus, Ort, Flug); stürzt der Server ab, setzt der nächste Login
 * zurück.
 *
 * <p>Bildschirm: Kino-Balken oben (Bossbar-Titel) und unten (Actionbar), Farbstimmung über dem ganzen Bild (Actionbar),
 * Blitzbilder und Titelkarte (Titel). Die Glyphen kommen aus der Schrift kuby:cine (tools/build_cinema.py).
 */
public final class Cinema implements Listener {

    public enum Result { DONE, SKIPPED, ABORTED }

    @FunctionalInterface
    public interface End {
        void done(Player p, Result result);
    }

    /** Einstellungen einer Vorführung. */
    public static final class Options {
        /** Teile, die der Zuschauer während der Szene nicht sieht (z. B. das echte Schwert im Stein). */
        public final List<Entity> shared = new ArrayList<>();
        /** Ort für "@ground" (Block darunter) und für die Risse. */
        public Location anchor;
        /** Wo der Spieler am Ende steht (null = wo er vorher war). */
        public Location endAt;
        public boolean skippable;
        public End onEnd;
    }

    // ---------- Schrift kuby:cine (Breiten wie in tools/build_cinema.py) ----------
    static final Key FONT = Key.key("kuby", "cine");
    static final Key CARD_FONT = Key.key("kuby", "cine_card");
    /** Balken: 128 x 16 Pixel, Höhe 256 → 2048 breit. */
    static final int BAR_ADV = 2049;
    /** Blitz: 64 x 16, Höhe 300 → 1200 breit. */
    static final int FLASH_ADV = 1201;
    /** Stimmung: 64 x 16, Höhe 700 → 2800 breit. */
    static final int GRADE_ADV = 2801;
    static final char TOP = '', BOTTOM = '', FLASH = '';
    static final Map<String, Character> GRADES = Map.of("red", '', "warm", '', "space", '', "dark", '',
            "gold", '', "white", '', "cold", '', "purple", '');

    private static final Set<UUID> BUSY = ConcurrentHashMap.newKeySet();

    /** Läuft für diesen Spieler gerade eine Szene? (HUD, Scoreboard und Leisten halten dann still.) */
    public static boolean busy(UUID id) {
        return BUSY.contains(id);
    }

    private final KubyPlugins plugin;
    private final NamespacedKey restoreKey;
    private final Map<UUID, Run> runs = new LinkedHashMap<>();

    public Cinema(KubyPlugins plugin) {
        this.plugin = plugin;
        this.restoreKey = new NamespacedKey(plugin, "cine_restore");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public boolean active(Player p) {
        return runs.containsKey(p.getUniqueId());
    }

    // =================================================================================================================
    // Start und Ende
    // =================================================================================================================

    private final class Run {
        final Player p;
        final Script s;
        final Options o;
        final World w;
        final BlockData ground;
        int t = -3;
        ItemDisplay cam;
        int camDur = 3;
        Mannequin actor;
        final Map<String, Display> props = new HashMap<>();
        final Map<String, Prop> state = new HashMap<>();
        final Map<Location, BlockData> fake = new LinkedHashMap<>();
        final Map<Location, Integer> damage = new HashMap<>();
        final List<BossBar> hiddenBars = new ArrayList<>();
        final BossBar top = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        GameMode mode;
        Location back;
        boolean allowFlight, flying;
        Frame next;
        int barLevel = -1;
        String grade = "-";
        String card, cardSub;
        int flashUntil;
        String hand;
        boolean crouch, raise, glow, hidden;

        Run(Player p, Script s, Options o) {
            this.p = p;
            this.s = s;
            this.o = o;
            Location a = o.anchor != null ? o.anchor : p.getLocation();
            this.w = a.getWorld();
            Block b = a.clone().add(0, -0.5, 0).getBlock();
            this.ground = b.getType().isSolid() ? b.getBlockData() : Material.STONE.createBlockData();
        }
    }

    /** Szene starten. false, wenn für den Spieler schon eine läuft. */
    public boolean play(Player p, Script s, Options o) {
        if (runs.containsKey(p.getUniqueId()) || !p.isOnline()) return false;
        Run r = new Run(p, s, o);
        r.mode = p.getGameMode();
        r.back = p.getLocation();
        r.allowFlight = p.getAllowFlight();
        r.flying = p.isFlying();
        p.getPersistentDataContainer().set(restoreKey, PersistentDataType.STRING, serialize(r.back, r.mode, r.allowFlight));
        BUSY.add(p.getUniqueId());
        runs.put(p.getUniqueId(), r);

        Frame f0 = new Frame();
        s.frame(0, f0);
        r.next = f0;
        Location cl = loc(r, f0.cam, f0.camYaw, f0.camPitch);
        r.cam = r.w.spawn(cl, ItemDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.setPersistent(false);
            e.setTeleportDuration(3);
            e.setViewRange(0.1f);
        });
        p.showEntity(plugin, r.cam);
        for (Entity e : o.shared) if (e != null && e.isValid()) p.hideEntity(plugin, e);
        // schwarz, solange der Wechsel in die Kamera läuft
        p.showTitle(Title.title(flashComp(0), Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(400), Duration.ZERO)));
        for (BossBar b : p.activeBossBars()) r.hiddenBars.add(b);
        for (BossBar b : r.hiddenBars) p.hideBossBar(b);
        p.showBossBar(r.top);
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(cl);
        return true;
    }

    /** Laufende Szene beenden (Schleichen, Befehl, Abbruch). */
    public void stop(Player p, Result res) {
        Run r = runs.get(p.getUniqueId());
        if (r != null) finish(r, res);
    }

    public void shutdown() {
        for (Run r : new ArrayList<>(runs.values())) finish(r, Result.ABORTED);
    }

    private void finish(Run r, Result res) {
        runs.remove(r.p.getUniqueId());
        Player p = r.p;
        cleanup(r);
        // offline: der gespeicherte Stand (PDC) setzt beim nächsten Login zurück
        if (p.isOnline()) {
            p.setSpectatorTarget(null);
            p.setGameMode(r.mode);
            Location end = res == Result.DONE && r.o.endAt != null ? r.o.endAt : r.back;
            p.teleport(end);
            p.setAllowFlight(r.allowFlight);
            if (r.allowFlight) p.setFlying(r.flying);
            p.resetPlayerTime();
            p.resetPlayerWeather();
            // bei normalem Ende läuft die Abblende noch aus
            if (res != Result.DONE) p.clearTitle();
            p.sendActionBar(Component.empty());
            p.hideBossBar(r.top);
            for (BossBar b : r.hiddenBars) p.showBossBar(b);
            p.stopSound(SoundStop.source(Sound.Source.MASTER));
            p.getPersistentDataContainer().remove(restoreKey);
        }
        BUSY.remove(p.getUniqueId());
        if (r.o.onEnd != null) {
            try {
                r.o.onEnd.done(p, res);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Cutscene-Ende: " + e);
            }
        }
    }

    private void cleanup(Run r) {
        if (r.cam != null) r.cam.remove();
        if (r.actor != null) r.actor.remove();
        for (Display d : r.props.values()) d.remove();
        r.props.clear();
        mend(r);
        if (r.p.isOnline()) for (Entity e : r.o.shared) if (e != null && e.isValid()) r.p.showEntity(plugin, e);
    }

    private static String serialize(Location l, GameMode m, boolean fly) {
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch() + ";"
                + m.name() + ";" + fly;
    }

    // =================================================================================================================
    // Ablauf
    // =================================================================================================================

    private void tick() {
        if (runs.isEmpty()) return;
        for (Run r : new ArrayList<>(runs.values())) {
            try {
                step(r);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Cutscene-Fehler bei Tick " + r.t + ": " + e);
                finish(r, Result.ABORTED);
            }
        }
    }

    private void step(Run r) {
        Player p = r.p;
        if (!p.isOnline()) {
            finish(r, Result.ABORTED);
            return;
        }
        if (r.t < 0) {
            if (r.t == -1) p.setSpectatorTarget(r.cam);
            r.t++;
            return;
        }
        if (r.t >= r.s.length()) {
            finish(r, Result.DONE);
            return;
        }
        Frame f = r.next;
        if (f == null) {
            f = new Frame();
            r.s.frame(r.t, f);
        }
        r.next = null;
        if (r.t + 1 < r.s.length()) {
            r.next = new Frame();
            r.s.frame(r.t + 1, r.next);
        }
        apply(r, f);
        // harter Schnitt im nächsten Bild: Gleiten jetzt abschalten (die Daten kommen beim Client nach der Position an)
        int dur = r.next != null && r.next.cut ? 0 : 3;
        if (dur != r.camDur) {
            r.cam.setTeleportDuration(dur);
            r.camDur = dur;
        }
        if (p.getSpectatorTarget() == null || !p.getSpectatorTarget().equals(r.cam)) p.setSpectatorTarget(r.cam);
        r.t++;
    }

    private Location loc(Run r, V3 v, float yaw, float pitch) {
        return new Location(r.w, v.x(), v.y(), v.z(), yaw, pitch);
    }

    private void apply(Run r, Frame f) {
        r.cam.teleport(loc(r, f.cam, f.camYaw, f.camPitch));
        actor(r, f);
        props(r, f.props);
        for (Object e : f.events) event(r, e);
        overlay(r, f);
    }

    // ---------- Darsteller ----------

    private void actor(Run r, Frame f) {
        if (f.actor == null) {
            if (r.actor != null && !r.hidden) {
                r.actor.setInvisible(true);
                r.hidden = true;
            }
            return;
        }
        Location l = loc(r, f.actor, f.headYaw, f.headPitch);
        if (r.actor == null || !r.actor.isValid()) {
            Player p = r.p;
            r.actor = r.w.spawn(l, Mannequin.class, m -> {
                m.setVisibleByDefault(false);
                m.setPersistent(false);
                m.setProfile(ResolvableProfile.resolvableProfile(p.getPlayerProfile()));
                m.setDescription(null);
                m.setImmovable(true);
                m.setAI(false);
                m.setGravity(false);
                m.setInvulnerable(true);
                m.setSilent(true);
                m.setCollidable(false);
                m.getEquipment().setHelmet(p.getInventory().getHelmet());
                m.getEquipment().setChestplate(p.getInventory().getChestplate());
                m.getEquipment().setLeggings(p.getInventory().getLeggings());
                m.getEquipment().setBoots(p.getInventory().getBoots());
            });
            r.p.showEntity(plugin, r.actor);
        }
        if (r.hidden) {
            r.actor.setInvisible(false);
            r.hidden = false;
        }
        r.actor.teleport(l);
        V3 eye = f.actor.up(1.62);
        V3 look = eye.add(V3.dir(f.headYaw, f.headPitch).mul(4));
        r.actor.lookAt(look.x(), look.y(), look.z(), io.papermc.paper.entity.LookAnchor.EYES);
        r.actor.setBodyYaw(f.actorYaw);
        if (f.crouch != r.crouch) {
            r.actor.setPose(f.crouch ? Pose.SNEAKING : Pose.STANDING, true);
            r.crouch = f.crouch;
        }
        if (!Objects.equals(f.hand, r.hand)) {
            String model = f.hand == null ? null : f.hand.split("\\|")[0];
            String use = f.hand != null && f.hand.contains("|") ? f.hand.substring(f.hand.indexOf('|') + 1) : null;
            if (r.raise) r.actor.clearActiveItem();
            r.actor.getEquipment().setItemInMainHand(model == null ? null : handItem(model, use));
            r.hand = f.hand;
            r.raise = use != null;
            if (r.raise) r.actor.startUsingItem(EquipmentSlot.HAND);
        }
        if (f.swing) r.actor.swingMainHand();
        if (f.glow != r.glow) {
            r.actor.setGlowing(f.glow);
            r.glow = f.glow;
        }
    }

    /** Item für die Hand der Figur; mit Haltung „benutzbar“ (Arm-Pose wie beim Dreizack usw.), ohne Ton und Partikel. */
    private static ItemStack handItem(String model, String use) {
        ItemStack it = ItemStack.of(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key(model));
        if (use != null) {
            ItemUseAnimation anim = switch (use) {
                case "crossbow" -> ItemUseAnimation.CROSSBOW;
                case "block" -> ItemUseAnimation.BLOCK;
                case "bow" -> ItemUseAnimation.BOW;
                case "spyglass" -> ItemUseAnimation.SPYGLASS;
                default -> ItemUseAnimation.TRIDENT;
            };
            it.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().consumeSeconds(3600).animation(anim)
                    .hasConsumeParticles(false).sound(Key.key("minecraft", "intentionally_empty")).build());
        }
        return it;
    }

    // ---------- Teile ----------

    private void props(Run r, List<Prop> list) {
        Set<String> seen = new HashSet<>();
        for (Prop pr : list) {
            seen.add(pr.id());
            Display d = r.props.get(pr.id());
            Prop old = r.state.get(pr.id());
            if (d == null || !d.isValid() || old == null || old.kind() != pr.kind()) {
                if (d != null) d.remove();
                d = spawn(r, pr);
                r.props.put(pr.id(), d);
                r.state.put(pr.id(), pr);
                continue;
            }
            if (!pr.what().equals(old.what()) || pr.color() != old.color()) look(r, d, pr);
            if (pr.light() != old.light()) d.setBrightness(pr.light() < 0 ? null : new Display.Brightness(pr.light(), pr.light()));
            if (pr.billboard() != old.billboard()) d.setBillboard(board(pr.billboard()));
            if (!pr.pos().equals(old.pos()) || pr.yaw() != old.yaw() || pr.pitch() != old.pitch()) {
                if (d.getTeleportDuration() != pr.smooth()) d.setTeleportDuration(pr.smooth());
                d.teleport(loc(r, pr.pos(), pr.yaw(), pr.pitch()));
            }
            if (!pr.move().equals(old.move()) || !pr.scale().equals(old.scale()) || !Arrays.equals(pr.rot(), old.rot())) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(pr.smooth());
                d.setTransformation(tf(pr));
            }
            r.state.put(pr.id(), pr);
        }
        if (r.props.size() > seen.size()) {
            for (var it = r.props.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (seen.contains(e.getKey())) continue;
                e.getValue().remove();
                r.state.remove(e.getKey());
                it.remove();
            }
        }
    }

    private static Display.Billboard board(int b) {
        return switch (b) {
            case 1 -> Display.Billboard.CENTER;
            case 2 -> Display.Billboard.VERTICAL;
            default -> Display.Billboard.FIXED;
        };
    }

    private static Transformation tf(Prop pr) {
        double[] q = pr.rot();
        return new Transformation(new Vector3f((float) pr.move().x(), (float) pr.move().y(), (float) pr.move().z()),
                new Quaternionf((float) q[0], (float) q[1], (float) q[2], (float) q[3]),
                new Vector3f((float) pr.scale().x(), (float) pr.scale().y(), (float) pr.scale().z()), new Quaternionf());
    }

    private Display spawn(Run r, Prop pr) {
        Location l = loc(r, pr.pos(), pr.yaw(), pr.pitch());
        Display d = switch (pr.kind()) {
            case ITEM -> r.w.spawn(l, ItemDisplay.class, e -> {
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                base(e, pr);
                look(r, e, pr);
            });
            case BLOCK -> r.w.spawn(l, BlockDisplay.class, e -> {
                base(e, pr);
                look(r, e, pr);
            });
            case TEXT -> r.w.spawn(l, TextDisplay.class, e -> {
                e.setShadowed(false);
                e.setLineWidth(4000);
                base(e, pr);
                look(r, e, pr);
            });
        };
        r.p.showEntity(plugin, d);
        return d;
    }

    private static void base(Display e, Prop pr) {
        e.setVisibleByDefault(false);
        e.setPersistent(false);
        e.setTeleportDuration(pr.smooth());
        e.setInterpolationDuration(pr.smooth());
        e.setTransformation(tf(pr));
        e.setBrightness(pr.light() < 0 ? null : new Display.Brightness(pr.light(), pr.light()));
        e.setBillboard(board(pr.billboard()));
        e.setViewRange(8f);
        e.setShadowRadius(0);
    }

    private void look(Run r, Display d, Prop pr) {
        switch (pr.kind()) {
            case ITEM -> {
                ItemStack it = ItemStack.of(Material.PAPER);
                it.setData(DataComponentTypes.ITEM_MODEL, Key.key(pr.what()));
                if (pr.color() != 0) it.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(Color.fromRGB(pr.color() & 0xFFFFFF)));
                ((ItemDisplay) d).setItemStack(it);
            }
            case BLOCK -> {
                BlockData bd;
                try {
                    bd = pr.what().equals("@ground") ? r.ground : Bukkit.createBlockData(pr.what());
                } catch (IllegalArgumentException e) {
                    bd = Material.STONE.createBlockData();
                }
                ((BlockDisplay) d).setBlock(bd);
            }
            case TEXT -> {
                TextDisplay td = (TextDisplay) d;
                td.text(Msg.parse(pr.what()));
                td.setBackgroundColor(Color.fromARGB(pr.color()));
            }
        }
    }

    // ---------- Ereignisse ----------

    private void event(Run r, Object e) {
        Player p = r.p;
        switch (e) {
            case Ev.Sound s -> p.playSound(Sound.sound(Key.key(s.key()), Sound.Source.MASTER, s.volume(), s.pitch()), Sound.Emitter.self());
            case Ev.SoundAt s -> p.playSound(Sound.sound(Key.key(s.key()), Sound.Source.MASTER, s.volume(), s.pitch()),
                    s.pos().x(), s.pos().y(), s.pos().z());
            case Ev.Silence s -> p.stopSound(SoundStop.source(Sound.Source.MASTER));
            case Ev.Particle pa -> particle(p, pa);
            case Ev.Flash fl -> {
                p.showTitle(Title.title(flashComp(fl.rgb()), Component.empty(), Title.Times.times(Duration.ofMillis(fl.in() * 50L),
                        Duration.ofMillis(fl.stay() * 50L), Duration.ofMillis(fl.out() * 50L))));
                r.flashUntil = r.t + fl.in() + fl.stay() + fl.out();
                r.card = null;
                r.cardSub = null;
            }
            case Ev.Sky sk -> {
                if (sk.time() >= 0) p.setPlayerTime(sk.time(), false);
                if (sk.weather() == 0) p.setPlayerWeather(WeatherType.CLEAR);
                else if (sk.weather() == 1) p.setPlayerWeather(WeatherType.DOWNFALL);
            }
            case Ev.Crack c -> crack(r, c);
            case Ev.Mend m -> mend(r);
            case Ev.Shared sh -> {
                for (Entity en : r.o.shared) {
                    if (en == null || !en.isValid()) continue;
                    if (sh.visible()) p.showEntity(plugin, en);
                    else p.hideEntity(plugin, en);
                }
            }
            default -> {
            }
        }
    }

    private static void particle(Player p, Ev.Particle pa) {
        Particle type;
        try {
            type = Particle.valueOf(pa.type());
        } catch (IllegalArgumentException ex) {
            return;
        }
        Object data = null;
        if (type == Particle.DUST) data = new Particle.DustOptions(Color.fromRGB(Math.max(0, pa.rgb()) & 0xFFFFFF), pa.size());
        else if (type.getDataType() != Void.class) return;
        p.spawnParticle(type, pa.pos().x(), pa.pos().y(), pa.pos().z(), pa.count(), pa.sx(), pa.sy(), pa.sz(), pa.speed(), data, true);
    }

    // ---------- Boden ----------

    private static double hash01(int x, int z, long seed) {
        long h = x * 73856093L ^ z * 19349663L ^ seed * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xffffff) / (double) 0xffffff;
    }

    /** Boden um center bricht auf: Spalten mit Magma, dunkles Gestein drumherum, Risse in den übrigen Blöcken. */
    private void crack(Run r, Ev.Crack c) {
        Player p = r.p;
        int R = (int) Math.ceil(c.radius());
        int cx = (int) Math.floor(c.center().x()), cz = (int) Math.floor(c.center().z()), cy = (int) Math.floor(c.center().y());
        int rays = 5;
        double[] ang = new double[rays];
        for (int k = 0; k < rays; k++) ang[k] = (k + hash01(k, 7, c.seed()) * 0.7) * Math.PI * 2 / rays;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int bx = cx + dx, bz = cz + dz;
                double px = bx + 0.5 - c.center().x(), pz = bz + 0.5 - c.center().z();
                double d = Math.sqrt(px * px + pz * pz);
                if (d > c.radius() || d < 1.2) continue;
                Block top = null;
                for (int y = cy + 1; y >= cy - 4; y--) {
                    Block b = r.w.getBlockAt(bx, y, bz);
                    if (b.getType().isSolid()) {
                        top = b;
                        break;
                    }
                }
                if (top == null) continue;
                Location key = top.getLocation();
                if (r.fake.containsKey(key) || r.damage.containsKey(key)) continue;
                double a = Math.atan2(pz, px);
                double best = 99;
                for (int k = 0; k < rays; k++) {
                    double wob = 0.22 * Math.sin(d * 0.9 + k * 1.7);
                    double da = Math.abs(((a - ang[k] - wob) % (Math.PI * 2) + Math.PI * 3) % (Math.PI * 2) - Math.PI);
                    best = Math.min(best, da * d);
                }
                double h = hash01(bx, bz, c.seed());
                BlockData nd = null;
                if (best < 0.36 && d > 2.0) {
                    fake(r, key, Material.AIR.createBlockData());
                    Location below = key.clone().add(0, -1, 0);
                    fake(r, below, Material.MAGMA_BLOCK.createBlockData());
                    continue;
                } else if (best < 0.5) {
                    nd = Material.MAGMA_BLOCK.createBlockData();
                } else if (best < 1.0) {
                    nd = (h < 0.45 ? Material.BLACKSTONE : h < 0.8 ? Material.COBBLED_DEEPSLATE : Material.BASALT).createBlockData();
                } else if (h < 0.12 * (1 - d / (c.radius() + 1)) + 0.05) {
                    nd = (h < 0.1 ? Material.TUFF : Material.COARSE_DIRT).createBlockData();
                }
                if (nd != null) fake(r, key, nd);
                else {
                    int id = 0x5A000000 + r.damage.size();
                    r.damage.put(key, id);
                    p.sendBlockDamage(key, (float) (0.35 + h * 0.6), id);
                }
            }
        }
    }

    private void fake(Run r, Location key, BlockData nd) {
        if (!r.fake.containsKey(key)) r.fake.put(key, key.getBlock().getBlockData());
        r.p.sendBlockChange(key, nd);
    }

    private void mend(Run r) {
        if (!r.p.isOnline()) {
            r.fake.clear();
            r.damage.clear();
            return;
        }
        for (Map.Entry<Location, BlockData> e : r.fake.entrySet()) r.p.sendBlockChange(e.getKey(), e.getKey().getBlock().getBlockData());
        for (Map.Entry<Location, Integer> e : r.damage.entrySet()) r.p.sendBlockDamage(e.getKey(), 0f, e.getValue());
        r.fake.clear();
        r.damage.clear();
    }

    // ---------- Bildschirm ----------

    private static Component glyph(char c, int adv, Key font, int rgb) {
        int left = adv / 2;
        return Component.text()
                .append(de.kuby.kubyplugins.ui.Ui.space(-left))
                .append(Component.text(String.valueOf(c)).font(font).color(TextColor.color(rgb)).shadowColor(ShadowColor.none()))
                .append(de.kuby.kubyplugins.ui.Ui.space(-(adv - left)))
                .build();
    }

    static Component flashComp(int rgb) {
        return glyph(FLASH, FLASH_ADV, FONT, rgb);
    }

    private void overlay(Run r, Frame f) {
        Player p = r.p;
        int level = (int) Math.round(Ease.clamp01(f.bars) * 4);
        boolean dirty = false;
        if (level != r.barLevel) {
            r.top.name(level == 0 ? Component.empty() : glyph((char) (TOP + level - 1), BAR_ADV, FONT, 0xFFFFFF));
            r.barLevel = level;
            dirty = true;
        }
        String g = f.grade == null ? "" : f.grade;
        if (!g.equals(r.grade)) {
            r.grade = g;
            dirty = true;
        }
        if (dirty || r.t % 8 == 0) {
            var b = Component.text();
            if (level > 0) b.append(glyph((char) (BOTTOM + level - 1), BAR_ADV, FONT, 0xFFFFFF));
            Character gc = GRADES.get(g);
            if (gc != null) b.append(glyph(gc, GRADE_ADV, FONT, 0xFFFFFF));
            p.sendActionBar(b.build());
        }
        // Titelkarte (nicht während eines Blitzbilds)
        if (r.t >= r.flashUntil && (!Objects.equals(f.card, r.card) || !Objects.equals(f.cardSub, r.cardSub))) {
            r.card = f.card;
            r.cardSub = f.cardSub;
            if (f.card == null && f.cardSub == null) p.clearTitle();
            else {
                Component title = f.card == null ? Component.empty() : Component.text(de.kuby.kubyplugins.ui.Canvas.titleChars(f.card, true))
                        .font(CARD_FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
                Component sub = f.cardSub == null ? Component.empty() : Msg.parse(f.cardSub);
                p.showTitle(Title.title(title, sub, Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(60), Duration.ofMillis(500))));
            }
        }
    }

    // =================================================================================================================
    // Ereignisse vom Server
    // =================================================================================================================

    @EventHandler(ignoreCancelled = true)
    public void onStopSpectating(com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent e) {
        Run r = runs.get(e.getPlayer().getUniqueId());
        if (r != null && r.cam != null && r.cam.equals(e.getSpectatorTarget())) e.setCancelled(true);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Run r = runs.get(e.getPlayer().getUniqueId());
        if (r == null || !r.o.skippable || r.s.skippableFrom() < 0 || r.t < r.s.skippableFrom()) return;
        finish(r, Result.SKIPPED);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && busy(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!busy(e.getPlayer().getUniqueId())) return;
        String m = e.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (m.startsWith("/kino") && e.getPlayer().hasPermission("kuby.admin")) return;
        e.setCancelled(true);
        Msg.send(e.getPlayer(), "<gray>Während der Szene geht das nicht.");
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        if (BUSY.isEmpty()) return;
        e.viewers().removeIf(a -> a instanceof Player pl && BUSY.contains(pl.getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Run r = runs.get(e.getPlayer().getUniqueId());
        if (r != null) finish(r, Result.ABORTED);
    }

    /** Nach einem Absturz mitten in einer Szene: Spielmodus und Ort zurück. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String s = p.getPersistentDataContainer().get(restoreKey, PersistentDataType.STRING);
        if (s == null) return;
        p.getPersistentDataContainer().remove(restoreKey);
        String[] a = s.split(";");
        try {
            World w = Bukkit.getWorld(a[0]);
            GameMode m = GameMode.valueOf(a[6]);
            p.setGameMode(m);
            if (w != null) p.teleport(new Location(w, Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]),
                    Float.parseFloat(a[4]), Float.parseFloat(a[5])));
            p.setAllowFlight(Boolean.parseBoolean(a[7]) || m == GameMode.CREATIVE);
        } catch (RuntimeException ex) {
            if (p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(GameMode.SURVIVAL);
        }
    }
}
