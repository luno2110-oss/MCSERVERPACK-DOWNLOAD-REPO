package de.kuby.rematch.client.anim;

import de.kuby.rematch.KubyRematch;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Vorführung für Aufnahmen ohne Bildschirm (nur mit -Dkuby.demo=clip1,clip2,...): legt eine flache Welt an,
 * stellt ein Mannequin auf, schaut von der Seite zu und spielt die Animationen nacheinander ab.
 * -Dkuby.demo.zeitlupe=0.25 verlangsamt (für ruckelfreie Aufnahmen mit Software-Grafik).
 * Schreibt Zeitstempel nach run/kuby_demo.log und beendet das Spiel am Ende.
 */
public final class DemoMode {
    private static int state, ticks, index;
    private static List<String> clips = new ArrayList<>();
    private static int target = -1;
    private static long phaseStart;

    private DemoMode() { }

    public static void init() {
        String s = System.getProperty("kuby.demo");
        if (s == null || s.isBlank()) return;
        for (String c : s.split(",")) if (!c.isBlank()) clips.add(c.trim());
        ClientAnims.setTimeScale(Double.parseDouble(System.getProperty("kuby.demo.zeitlupe", "1")));
        ClientTickEvents.END_CLIENT_TICK.register(DemoMode::tick);
        KubyRematch.LOG.info("Demo-Modus: {}", clips);
    }

    private static void log(String line) {
        try {
            Files.writeString(FabricLoader.getInstance().getGameDir().resolve("kuby_demo.log"),
                    System.currentTimeMillis() + " " + line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }

    private static void tick(Minecraft mc) {
        ticks++;
        switch (state) {
            case 0 -> {
                if (mc.screen instanceof TitleScreen && ticks > 40) {
                    Path saves = mc.gameDirectory.toPath().resolve("saves").resolve("kuby_demo");
                    if (Files.isDirectory(saves)) {
                        mc.createWorldOpenFlows().openWorld("kuby_demo", () -> { });
                    } else {
                        WorldDataConfiguration cfg = WorldDataConfiguration.DEFAULT;
                        LevelSettings ls = new LevelSettings("kuby_demo", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(cfg.enabledFeatures()), cfg);
                        mc.createWorldOpenFlows().createFreshLevel("kuby_demo", ls, new WorldOptions(20261110L, false, false),
                                WorldPresets::createFlatWorldDimensions, mc.screen);
                    }
                    state = 1;
                    ticks = 0;
                    log("welt");
                }
            }
            case 1 -> {
                if (mc.player != null && mc.level != null && ticks > 60) {
                    mc.player.connection.sendCommand("time set noon");
                    mc.player.connection.sendCommand("weather clear");
                    mc.player.connection.sendCommand("kill @e[type=minecraft:mannequin]");
                    mc.player.connection.sendCommand("summon minecraft:mannequin -1.5 -60 0.5 {Rotation:[-90f,0f],NoGravity:1b}");
                    mc.player.connection.sendCommand(System.getProperty("kuby.demo.kamera", "tp @s -2.2 -60 3.7 180 14"));
                    mc.options.hideGui = true;
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    state = 2;
                    ticks = 0;
                }
            }
            case 2 -> {
                if (ticks > 60) {
                    for (Entity e : mc.level.entitiesForRendering())
                        if (e.getType() == EntityType.MANNEQUIN) target = e.getId();
                    if (target < 0) { if (ticks > 200) finish(mc); return; }
                    log("bereit " + target);
                    state = 3;
                    ticks = 0;
                }
            }
            case 3 -> { // nächste Animation starten, mit Pause dazwischen
                if (ticks < 30) return;
                if (index >= clips.size()) { finish(mc); return; }
                String c = clips.get(index++);
                ClientAnims.play(target, c, 1.0, true);
                log("start " + c);
                phaseStart = System.nanoTime();
                state = 4;
                ticks = 0;
            }
            case 4 -> {
                Entity t = mc.level.getEntity(target);
                if (t instanceof net.minecraft.world.entity.LivingEntity le) { // Blickrichtung festhalten
                    le.setYBodyRot(-90f);
                    le.yBodyRotO = -90f;
                    le.setYHeadRot(-90f);
                    le.yHeadRotO = -90f;
                }
                var a = ClientAnims.animator(target);
                if (a == null || !a.active()) {
                    log("ende");
                    state = 3;
                    ticks = 0;
                } else if ((System.nanoTime() - phaseStart) / 1e9 > 120) {
                    finish(mc);
                }
            }
            default -> { }
        }
    }

    private static void finish(Minecraft mc) {
        log("fertig");
        state = 99;
        mc.stop();
    }
}
