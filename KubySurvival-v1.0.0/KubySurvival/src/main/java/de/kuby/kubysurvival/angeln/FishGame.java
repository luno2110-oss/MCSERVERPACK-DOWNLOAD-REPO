package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Ui;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysurvival.KubySurvival;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fang-Minispiel für wirklich (Stufe 1) und extrem (Stufe 2) seltene Fänge, mit Roblox-artiger Würfel-Animation
 * („1 zu N“) davor. Im Untertitel zwei Leisten: oben schwimmt der Fisch (zufällig und in Mustern), unten der eigene
 * Fangbereich (Linksklick nach links, Rechtsklick nach rechts). Darüber füllt sich der Fangbalken, solange der Fisch im
 * Bereich ist; leer = Fisch weg (dann für Juwelen kaufbar).
 * <p>
 * Combo: alle 0,50 s, die der Fisch ganz im Bereich bleibt, gibt es +1 Combo und der Takt wird 0,10 s kürzer (bis
 * 0,10 s); rutscht er raus, beginnt der Takt wieder bei 0,50 s, die Combo bleibt. Kein Limit. Wert x (1 + Combo x
 * angeln.minispiel.combo-wert). Stufe 2 und schwere Fische: kleinerer Bereich, schnellerer Fisch, längerer Fang.
 */
public final class FishGame implements Listener {

    private static final Key FONT = Key.key("kuby", "fishgame");
    private static final int W = 110, FISH_W = 11;
    private static final int[] PIECES = {64, 32, 16, 8, 4, 2, 1};
    private static final String[] TIERS = {"Gewöhnlich", "Ungewöhnlich", "Selten", "Episch", "Legendär", "Mythisch", "Göttlich"};
    private static final int[] TIER_RGB = {0xAAAAAA, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00, 0xFF55FF, 0xFFE7A8};

    /** Abgelaufene Fänge, die man für Juwelen kaufen kann. */
    private record Offer(AngelService.Roll roll, int tier, long jewels, long until) {}

    private final class Session {
        final Player p;
        final AngelService.Roll roll;
        final int tier;
        final Location hook;
        int phase;              // 0 Animation, 1 Spiel
        int t;                  // Ticks in der Phase
        double fishX, fishTarget, fishSpeed;
        int patternLeft, pattern;
        double zoneX, zoneV;
        final int zoneW;
        double meter;
        final double meterMax;
        int combo, interval = 10, intervalTick;
        boolean clean = true;

        Session(Player p, AngelService.Roll roll, int tier, Location hook) {
            this.p = p;
            this.roll = roll;
            this.tier = tier;
            this.hook = hook;
            double q = heavy(roll);
            this.zoneW = (int) Math.round((tier >= 2 ? 22 : 36) - 8 * q);
            this.meterMax = (tier >= 2 ? 180 : 120) + 40 * q;
            this.meter = meterMax * 0.3;
            this.fishX = (W - FISH_W) / 2.0;
            this.zoneX = (W - zoneW) / 2.0;
            this.fishTarget = fishX;
        }

        double speedFactor() {
            return (tier >= 2 ? 1.45 : 1.0) * (1 + 0.35 * heavy(roll));
        }
    }

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final AngelService service;
    private final AngelFx fx;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Offer> offers = new HashMap<>();
    /** Wird nach einem gewonnenen Spiel aufgerufen (Job-XP, Quests, Effekte), gesetzt vom AngelListener. */
    private Finish finish;

    public interface Finish {
        void done(Player p, Location at, AngelService.Catch c, ItemStack fish);
    }

    public FishGame(KubySurvival addon, KubyPlugins core, AngelService service, AngelFx fx) {
        this.addon = addon;
        this.core = core;
        this.service = service;
        this.fx = fx;
        addon.getServer().getScheduler().runTaskTimer(addon, this::tick, 1L, 1L);
    }

    public void onFinish(Finish f) {
        this.finish = f;
    }

    public boolean active(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    /** 0 bis 1: wie schwer der Fisch für seine Art ist. */
    static double heavy(AngelService.Roll r) {
        FishSpecies s = r.species();
        double span = s.maxKg() - s.minKg();
        return span <= 0 ? 0.5 : Math.max(0, Math.min(1, (r.kg() - s.minKg()) / span));
    }

    private double cfg(String key, double def) {
        return core.getConfig().getDouble("angeln.minispiel." + key, def);
    }

    /** Startet Animation und Minispiel für einen ausgewürfelten seltenen Fang. */
    public void start(Player p, Location hook, AngelService.Roll roll, int tier) {
        sessions.put(p.getUniqueId(), new Session(p, roll, tier, hook.clone()));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
    }

    // =================================================================================================================
    // Eingabe
    // =================================================================================================================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null || e.getHand() == EquipmentSlot.OFF_HAND) return;
        e.setCancelled(true);
        if (s.phase != 1) return;
        double impulse = cfg("schub", 3.2);
        Action a = e.getAction();
        if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) s.zoneV -= impulse;
        else if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) s.zoneV += impulse;
    }

    /** Während des Spiels nicht neu auswerfen. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFish(PlayerFishEvent e) {
        if (sessions.containsKey(e.getPlayer().getUniqueId()) && e.getState() == PlayerFishEvent.State.FISHING) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sessions.remove(e.getPlayer().getUniqueId());
    }

    // =================================================================================================================
    // Ablauf
    // =================================================================================================================

    private void tick() {
        if (sessions.isEmpty()) return;
        for (Session s : new java.util.ArrayList<>(sessions.values())) {
            if (!s.p.isOnline()) {
                sessions.remove(s.p.getUniqueId());
                continue;
            }
            s.t++;
            if (s.phase == 0) animate(s);
            else play(s);
        }
    }

    /** Endstufe der Animation nach „1 zu N“; 6-Sterne-Secrets tarnen sich als Episch. */
    private static int finalTier(AngelService.Roll r) {
        double n = r.odds();
        int k = n >= 5_000_000 ? 6 : n >= 500_000 ? 5 : n >= 50_000 ? 4 : 3;
        return r.stars() >= 6 ? 3 : k;
    }

    /** Würfel-Animation: Stufen laufen durch, werden langsamer, landen auf der Endstufe (ca. 2,5 s). */
    private void animate(Session s) {
        int end = finalTier(s.roll);
        int[] steps = {0, 2, 4, 6, 8, 11, 14, 18, 22, 27, 33, 40};
        boolean secret = s.roll.stars() >= 6;
        for (int i = 0; i < steps.length; i++) {
            if (s.t != steps[i] + 1) continue;
            int k = i == steps.length - 1 ? end : (i + ThreadLocalRandom.current().nextInt(3)) % (end + 1);
            boolean last = i == steps.length - 1;
            Component title = Component.text(TIERS[k].toUpperCase(java.util.Locale.ROOT), TextColor.color(TIER_RGB[k]), TextDecoration.BOLD);
            Component sub = last ? oddsLine(s.roll) : Component.text("· · ·", NamedTextColor.GRAY);
            s.p.showTitle(Title.title(title, sub, Title.Times.times(Duration.ZERO, Duration.ofMillis(last ? 1500 : 600), Duration.ofMillis(200))));
            s.p.playSound(s.p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.8f + i * 0.1f);
            if (last) {
                s.p.playSound(s.p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                s.p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, s.p.getLocation().add(0, 1.2, 0), 40, 0.5, 0.6, 0.5, 0.3);
            }
        }
        if (secret && s.t == 66) {
            s.p.showTitle(Title.title(Msg.parse("<b><gradient:#FF5AE0:#FFD25A>SECRET!</gradient></b>"),
                    Msg.parse("<gradient:#FF5AE0:#FFD25A>★★★★★★</gradient> <gray>" + Msg.escape(s.roll.species().name())),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(200))));
            s.p.playSound(s.p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.6f);
        }
        if (s.t >= (secret ? 96 : 70)) {
            s.phase = 1;
            s.t = 0;
            Msg.send(s.p, "<aqua><b>ANGELN</b> <dark_gray>▶ <white>Halte den Fisch im grünen Bereich! <gray>Linksklick ◀ · Rechtsklick ▶");
        }
    }

    private static Component oddsLine(AngelService.Roll r) {
        FishVariant v = r.variant();
        String name = v.name(r.species());
        return Component.text().append(Component.text(name, v.special() ? v.color() : r.species().rarity().color()))
                .append(Component.text(" " + "★".repeat(Math.min(6, r.stars())) + " ", NamedTextColor.YELLOW))
                .append(Component.text("· 1 zu " + AngelText.num(Math.round(r.odds())), TextColor.color(0xFF8AD8))).build();
    }

    private void play(Session s) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double sp = s.speedFactor();
        // Fisch: Muster wechseln (treiben, Sprung, Zickzack, Zittern), Muster bewusst unregelmäßig
        if (--s.patternLeft <= 0) {
            s.pattern = rnd.nextInt(4);
            s.patternLeft = 15 + rnd.nextInt(30);
            s.fishTarget = rnd.nextDouble() * (W - FISH_W);
            s.fishSpeed = switch (s.pattern) {
                case 1 -> 2.6;
                case 2 -> 1.6;
                case 3 -> 0.6;
                default -> 0.9;
            } * sp;
        }
        if (s.pattern == 2 && s.t % 8 == 0) s.fishTarget = s.fishTarget < W / 2.0 ? W - FISH_W - rnd.nextDouble() * 20 : rnd.nextDouble() * 20;
        if (s.pattern == 3) s.fishTarget = Math.max(0, Math.min(W - FISH_W, s.fishTarget + (rnd.nextDouble() - 0.5) * 6 * sp));
        double d = s.fishTarget - s.fishX;
        s.fishX += Math.signum(d) * Math.min(Math.abs(d), s.fishSpeed);
        s.fishX = Math.max(0, Math.min(W - FISH_W, s.fishX));
        // Fangbereich: Schub aus Klicks, Reibung, Rand
        s.zoneX += s.zoneV;
        s.zoneV *= cfg("reibung", 0.82);
        if (s.zoneX < 0) {
            s.zoneX = 0;
            s.zoneV = 0;
        } else if (s.zoneX > W - s.zoneW) {
            s.zoneX = W - s.zoneW;
            s.zoneV = 0;
        }
        double center = s.fishX + FISH_W / 2.0;
        boolean inside = center >= s.zoneX && center <= s.zoneX + s.zoneW;
        // Fangbalken
        s.meter += inside ? 1 : -0.6;
        // Combo-Takt
        if (!inside) s.clean = false;
        if (++s.intervalTick >= s.interval) {
            if (s.clean) {
                s.combo++;
                s.interval = Math.max(2, s.interval - 2);
                s.p.playSound(s.p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35f, Math.min(2.0f, 0.9f + s.combo * 0.02f));
            } else {
                s.interval = 10;
            }
            s.intervalTick = 0;
            s.clean = true;
        }
        if (s.t % 2 == 0) render(s, inside);
        if (s.meter >= s.meterMax) win(s);
        else if (s.meter <= 0 || s.t > 20 * 120) lose(s);
    }

    private double mult(Session s) {
        return 1 + s.combo * cfg("combo-wert", 0.03);
    }

    private static Component glyph(String chars) {
        return Component.text(chars).font(FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
    }

    /** Füllung aus Stücken (64, 32, ... 1 px), jedes Stück hat w + 1 Vorschub, daher -1 dahinter. */
    private static void fill(TextComponent.Builder b, int width, int base) {
        int rest = width;
        for (int i = 0; i < PIECES.length; i++) {
            while (rest >= PIECES[i]) {
                b.append(glyph(String.valueOf((char) (base + (6 - i))))).append(Ui.space(-1));
                rest -= PIECES[i];
            }
        }
    }

    private void render(Session s, boolean inside) {
        Component title = Component.text().append(Component.text("COMBO " + s.combo, TextColor.color(0xFFD25A), TextDecoration.BOLD))
                .append(Component.text("  x" + AngelText.dec(Math.round(mult(s) * 100) / 100.0), NamedTextColor.WHITE)).build();
        s.p.showTitle(Title.title(title, bars(s.meter / s.meterMax, s.fishX, s.zoneX, s.zoneW, inside),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(400), Duration.ZERO)));
    }

    /** Die drei Leisten als Untertitel (öffentlich für die Vorschau). */
    public static Component bars(double frac, double fishX, double zoneX, int zoneW, boolean inside) {
        TextComponent.Builder b = Component.text();
        // Fortschritt
        int pw = (int) Math.round(Math.max(0, Math.min(1, frac)) * (W - 2));
        b.append(glyph("")).append(Ui.space(-(W + 1))).append(Ui.space(1));
        fill(b, pw, 0xE040);
        b.append(Ui.space(-(pw + 1)));
        // Fisch-Leiste
        int fx = (int) Math.round(fishX);
        b.append(glyph("")).append(Ui.space(-(W + 1))).append(Ui.space(fx)).append(glyph("")).append(Ui.space(-(fx + FISH_W + 1)));
        // Fangbereich
        int zx = (int) Math.round(zoneX);
        int zw = Math.max(2, Math.min(W - 2, zoneW));
        b.append(glyph("")).append(Ui.space(-(W + 1))).append(Ui.space(Math.max(1, Math.min(W - 1 - zw, zx))));
        fill(b, zw, inside ? 0xE010 : 0xE020);
        b.append(Ui.space(-(Math.max(1, Math.min(W - 1 - zw, zx)) + zw))).append(Ui.space(W + 1));
        return b.build();
    }

    private void win(Session s) {
        sessions.remove(s.p.getUniqueId());
        double mult = mult(s);
        AngelService.Catch c = service.book(s.p, s.roll, mult, true);
        ItemStack fish = AngelItems.fish(c.species(), c.stars(), c.kg(), c.variant(), mult, c.odds(), c.amount());
        for (ItemStack left : s.p.getInventory().addItem(fish.clone()).values()) s.p.getWorld().dropItem(s.p.getLocation(), left);
        if (finish != null) finish.done(s.p, s.hook, c, fish);
        s.p.showTitle(Title.title(Msg.parse("<b><gradient:#7CF5FF:#FFD25A>GEFANGEN!</gradient></b>"),
                Component.text("Combo " + s.combo + " · Wert x" + AngelText.dec(Math.round(mult * 100) / 100.0), NamedTextColor.GOLD),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1600), Duration.ofMillis(400))));
        if (s.tier >= 2) {
            Component msg = Msg.parse("<aqua><b>ANGELN</b> <dark_gray>▶ <white>" + Msg.escape(s.p.getName()) + " <gray>hat einen <light_purple><b>"
                    + Msg.escape(c.variant().name(c.species())) + "</b> <gray>(1 zu " + AngelText.num(Math.round(c.odds()))
                    + ") mit <gold>Combo " + s.combo + " <gray>gefangen!");
            for (Player o : addon.getServer().getOnlinePlayers()) {
                if ("survival".equals(core.modes().modeOf(o.getUniqueId()))) o.sendMessage(msg);
            }
        }
    }

    private void lose(Session s) {
        sessions.remove(s.p.getUniqueId());
        long jewels = buyPrice(s.roll.odds());
        offers.put(s.p.getUniqueId(), new Offer(s.roll, s.tier, jewels, System.currentTimeMillis() + 60_000L));
        s.p.showTitle(Title.title(Component.text("ENTKOMMEN", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(s.roll.variant().name(s.roll.species()) + " ist weg …", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(400))));
        Sfx.play(core, s.p, Sfx.Kind.FAIL);
        Msg.send(s.p, "<aqua><b>ANGELN</b> <dark_gray>▶ <gray>Der Fisch ist entkommen. Für <green>" + AngelText.num(jewels)
                + " Juwelen</green> kannst du ihn noch 60 Sekunden lang kaufen: <click:run_command:'/angeln freikaufen'><green>[Kaufen]</click>");
    }

    /** 100 bis 1.000 Juwelen, je seltener desto teurer (1 zu 5.000 = 100, ab 1 zu 5 Mio. = 1.000). */
    static long buyPrice(double odds) {
        double t = (Math.log10(Math.max(1, odds)) - Math.log10(5_000)) / (Math.log10(5_000_000) - Math.log10(5_000));
        t = Math.max(0, Math.min(1, t));
        return Math.round((100 + 900 * t) / 10.0) * 10;
    }

    /** /angeln freikaufen: entkommenen Fisch für Juwelen kaufen. */
    public void buyEscaped(Player p) {
        Offer o = offers.get(p.getUniqueId());
        if (o == null || o.until < System.currentTimeMillis()) {
            offers.remove(p.getUniqueId());
            Msg.send(p, "<red>Gerade gibt es keinen entkommenen Fisch zu kaufen.");
            return;
        }
        if (!core.jewels().take(p.getUniqueId(), o.jewels)) {
            Msg.send(p, "<red>Dafür fehlen dir Juwelen (" + AngelText.num(o.jewels) + ").");
            return;
        }
        offers.remove(p.getUniqueId());
        AngelService.Catch c = service.book(p, o.roll, 1.0, true);
        ItemStack fish = AngelItems.fish(c.species(), c.stars(), c.kg(), c.variant(), 1.0, c.odds(), c.amount());
        for (ItemStack left : p.getInventory().addItem(fish.clone()).values()) p.getWorld().dropItem(p.getLocation(), left);
        Sfx.play(core, p, Sfx.Kind.SUCCESS_BIG);
        Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <green>Gekauft: <white>" + Msg.escape(c.variant().name(c.species())));
    }
}
