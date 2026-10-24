package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysurvival.KubySurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Darstellung beim Angeln wie in der Referenz: Hinweis in der Aktionsleiste beim Warten und beim Biss, großer Titel
 * in Seltenheitsfarbe mit Sternen und Gewicht, der Fisch springt aus dem Wasser vor den Spieler, kreiselt und
 * verschwindet im Inventar; Rekord, Level-Aufstieg, Schatz und volle Fischbuch-Seite im Chat.
 */
public final class AngelFx {

    static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final Component PREFIX = Component.text("ANGELN", CYAN, TextDecoration.BOLD)
            .append(Component.text(" ▶ ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false));

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final AngelService service;
    /** Spieler mit ausgeworfener Angel (für die Anzeige beim Warten). */
    private final Map<UUID, FishHook> hooks = new HashMap<>();
    private final Map<UUID, Long> bitten = new HashMap<>();

    public AngelFx(KubySurvival addon, KubyPlugins core, AngelService service) {
        this.addon = addon;
        this.core = core;
        this.service = service;
        Bukkit.getScheduler().runTaskTimer(addon, this::tickWaiting, 20L, 10L);
    }

    static Component fish() {
        return Component.text().append(de.kuby.kubyplugins.ui.Sym.FISH.component()).append(Component.text(" ")).build();
    }

    public void cast(Player p, FishHook hook) {
        hooks.put(p.getUniqueId(), hook);
        bitten.remove(p.getUniqueId());
    }

    public void stop(Player p) {
        hooks.remove(p.getUniqueId());
        bitten.remove(p.getUniqueId());
    }

    private void tickWaiting() {
        long now = System.currentTimeMillis();
        hooks.entrySet().removeIf(e -> {
            Player p = Bukkit.getPlayer(e.getKey());
            FishHook h = e.getValue();
            if (p == null || h == null || !h.isValid()) return true;
            Long bite = bitten.get(e.getKey());
            if (bite != null && now - bite < 1500) return false;
            if (h.getState() != FishHook.HookState.BOBBING) return false;
            AngelService.Zone z = service.zoneAt(h.getLocation());
            TextComponent.Builder b = Component.text().append(fish())
                    .append(Component.text("Du angelst gerade...", NamedTextColor.WHITE))
                    .append(Component.text(" · ", NamedTextColor.DARK_GRAY));
            if (z != null) {
                b.append(Component.text("Angel-Zone ", CYAN)).append(Component.text("+10 %", NamedTextColor.GREEN));
            } else {
                b.append(Component.text(Water.at(h.getLocation()).label, NamedTextColor.GRAY));
            }
            p.sendActionBar(b.build());
            return false;
        });
    }

    public void bite(Player p) {
        bitten.put(p.getUniqueId(), System.currentTimeMillis());
        p.sendActionBar(Component.text().append(fish())
                .append(Component.text("Es zieht! ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("Jetzt einholen!", NamedTextColor.WHITE)).build());
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
    }

    static Component stars(int n) {
        return Component.text().append(Component.text("★".repeat(n), NamedTextColor.YELLOW))
                .append(Component.text("★".repeat(5 - n), NamedTextColor.DARK_GRAY)).build();
    }

    /** Alles, was nach einem Fang zu sehen und zu hören ist. */
    public void caught(Player p, Location hook, AngelService.Catch c, ItemStack item) {
        stop(p);
        FishSpecies s = c.species();
        Rarity r = s.rarity();
        Component sub = Component.text().append(Component.text(s.name(), r.color())).append(Component.text(" "))
                .append(stars(c.stars())).append(Component.text(" · ", NamedTextColor.GRAY))
                .append(Component.text(AngelText.kg(c.kg()), NamedTextColor.WHITE)).build();
        p.showTitle(Title.title(Component.text(r.title, r.color(), TextDecoration.BOLD), sub,
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(r.atLeast(Rarity.SELTEN) ? 2200 : 1400),
                        Duration.ofMillis(500))));

        TextComponent.Builder bar = Component.text().append(Component.text(s.name(), r.color())).append(Component.text(" "))
                .append(stars(c.stars())).append(Component.text(" · " + AngelText.kg(c.kg()) + " · ", NamedTextColor.GRAY))
                .append(Component.text("+" + c.points() + " P", CYAN));
        if (c.amount() > 1) bar.append(Component.text(" · Doppelfang!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        if (c.autoSold() > 0) bar.append(Component.text(" · verkauft +" + AngelText.money(c.autoSold()), NamedTextColor.GREEN));
        Component barText = bar.build();
        Bukkit.getScheduler().runTaskLater(addon, () -> p.sendActionBar(barText), 4L);
        Bukkit.getScheduler().runTaskLater(addon, () -> p.sendActionBar(barText), 30L);

        // Töne nach Seltenheit
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.8f, 1.0f);
        switch (r) {
            case GEWOEHNLICH, UNGEWOEHNLICH -> Sfx.play(core, p, Sfx.Kind.PING);
            case SELTEN -> Sfx.play(core, p, Sfx.Kind.CHIME);
            case EPISCH -> Sfx.play(core, p, Sfx.Kind.SUCCESS_BIG);
            default -> Sfx.play(core, p, Sfx.Kind.EPIC);
        }

        jump(p, hook, item);

        if (c.record()) {
            p.sendMessage(Component.text().append(PREFIX)
                    .append(Component.text("Neuer persönlicher Rekord! ", TextColor.color(0xFFAA00)))
                    .append(Component.text(s.name(), r.color()))
                    .append(Component.text(" " + AngelText.kg(c.kg()), NamedTextColor.WHITE))
                    .append(Component.text(" (vorher " + AngelText.kg(c.previousBest()) + ")", NamedTextColor.GRAY)).build());
        }
        if (c.treasure() != null) {
            p.sendMessage(Component.text().append(Component.text("SCHATZ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" ▶ ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Am Haken hing ein Schatz: ", NamedTextColor.WHITE))
                    .append(Component.text(c.treasure(), NamedTextColor.GOLD)).build());
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
            Bukkit.getScheduler().runTaskLater(addon, () -> Sfx.play(core, p, Sfx.Kind.COIN), 6L);
        }
        if (c.pageDone() != null) {
            p.sendMessage(Component.text().append(PREFIX)
                    .append(Component.text("Fischbuch-Seite komplett: ", TextColor.color(0xFFAA00)))
                    .append(Component.text(c.pageDone().label, CYAN))
                    .append(Component.text(" · +" + AngelText.money(core.getConfig().getLong("angeln.page-coins", 3000))
                            + " und " + core.getConfig().getLong("angeln.page-tokens", 2) + " Tokens", NamedTextColor.GREEN)).build());
            Bukkit.getScheduler().runTaskLater(addon, () -> Sfx.play(core, p, Sfx.Kind.VICTORY), 20L);
        }
        if (c.levelAfter() > c.levelBefore()) {
            int lv = c.levelAfter();
            long rest = AngelService.pointsFor(lv + 1) - service.profile(p.getUniqueId()).points;
            Bukkit.getScheduler().runTaskLater(addon, () -> {
                p.showTitle(Title.title(Component.text().append(Component.text("LEVEL ", NamedTextColor.GREEN, TextDecoration.BOLD))
                                .append(Component.text(String.valueOf(lv), NamedTextColor.GRAY, TextDecoration.BOLD)).build(),
                        Component.text(lv >= 100 ? "Höchstes Level erreicht!" : "Noch " + AngelText.num(Math.max(0, rest))
                                + " Punkte bis Level " + (lv + 1), NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(600))));
                Sfx.play(core, p, Sfx.Kind.LEVEL_UP);
                p.sendMessage(Component.text().append(PREFIX)
                        .append(Component.text("Angel-Level " + lv + "! ", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.text("+" + AngelText.money(50L * lv), NamedTextColor.GOLD))
                        .append(Component.text(lv % 10 == 0 ? " und 1 Token" : "", NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text(" · " + AngelService.rank(lv), NamedTextColor.GRAY)).build());
            }, 45L);
        }
        if (r.atLeast(Rarity.LEGENDAER)) {
            Component msg = Component.text().append(PREFIX)
                    .append(Component.text(p.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" hat einen ", NamedTextColor.GRAY))
                    .append(Component.text(s.name(), r.color(), TextDecoration.BOLD))
                    .append(Component.text(" gefangen! ", NamedTextColor.GRAY))
                    .append(stars(c.stars()))
                    .append(Component.text(" · " + AngelText.kg(c.kg()), NamedTextColor.WHITE)).build();
            for (Player o : Bukkit.getOnlinePlayers()) {
                if ("survival".equals(core.modes().modeOf(o.getUniqueId()))) o.sendMessage(msg);
            }
        }
    }

    /** Der Fisch springt aus dem Wasser vor den Spieler, kreiselt und fliegt in ihn hinein (ca. 1,5 s). */
    private void jump(Player p, Location hook, ItemStack item) {
        if (hook == null || hook.getWorld() == null) return;
        Location start = hook.clone().add(0, 0.3, 0);
        hook.getWorld().spawnParticle(Particle.SPLASH, start, 30, 0.3, 0.1, 0.3, 0.1);
        hook.getWorld().spawnParticle(Particle.BUBBLE_POP, start, 12, 0.25, 0.1, 0.25, 0.05);
        ItemStack shown = item.clone();
        shown.setAmount(1);
        ItemDisplay d = hook.getWorld().spawn(start, ItemDisplay.class, e -> {
            e.setItemStack(shown);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setTeleportDuration(2);
            e.setInterpolationDuration(2);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setPersistent(false);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.5f, 0.5f, 0.5f),
                    new AxisAngle4f()));
        });
        final int total = 30;
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || !d.isValid()) {
                    d.remove();
                    cancel();
                    return;
                }
                t += 2;
                double f = Math.min(1.0, t / (double) total);
                Location eye = p.getEyeLocation();
                Vector dir = eye.getDirection().setY(0);
                if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
                dir.normalize();
                // Ziel: knapp vor dem Gesicht, leicht unter dem Titel
                Location face = eye.clone().add(dir.clone().multiply(1.1)).add(0, -0.15, 0);
                Location end = p.getLocation().add(0, 1.0, 0);
                Location pos;
                float scale;
                if (f < 0.72) {
                    double k = f / 0.72;
                    Vector a = start.toVector(), c = face.toVector();
                    Vector mid = a.clone().add(c).multiply(0.5).add(new Vector(0, 2.2, 0));
                    Vector v = a.clone().multiply((1 - k) * (1 - k)).add(mid.multiply(2 * k * (1 - k))).add(c.clone().multiply(k * k));
                    pos = v.toLocation(start.getWorld());
                    scale = (float) (0.5 + 0.9 * k);
                } else {
                    double k = (f - 0.72) / 0.28;
                    pos = face.toVector().multiply(1 - k).add(end.toVector().multiply(k)).toLocation(start.getWorld());
                    scale = (float) (1.4 * (1 - k) + 0.05);
                }
                float spin = (float) (f * Math.PI * 4);
                d.setInterpolationDelay(0);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(spin, 0, 0, 1),
                        new Vector3f(scale, scale, scale), new AxisAngle4f()));
                d.teleport(pos);
                if (t % 6 == 0) pos.getWorld().spawnParticle(Particle.FALLING_WATER, pos, 3, 0.1, 0.1, 0.1, 0);
                if (f >= 1.0) {
                    d.remove();
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
                    cancel();
                }
            }
        }.runTaskTimer(addon, 1L, 2L);
    }

    /** Kurze Meldung mit Angel-Präfix. */
    public static void say(Player p, Component text) {
        p.sendMessage(Component.text().append(PREFIX).append(text).build());
    }

    @SuppressWarnings("unused")
    private static String plain(String s) {
        return Msg.escape(s);
    }
}
