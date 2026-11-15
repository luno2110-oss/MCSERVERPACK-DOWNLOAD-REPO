package h3d;

import de.kuby.kubyplugins.casino.BjCards;
import de.kuby.kubyplugins.casino.BlackjackTable;
import de.kuby.kubyplugins.casino.RouletteTable;
import de.kuby.kubyplugins.casino.SlotMachine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.io.FileWriter;
import java.util.List;

/** Baut eine Casino-Szene mit den echten Klassen auf und schreibt alle Anzeigen als JSON. Aufruf: Scene <name> <out.json> */
public final class Scene {
    public static void main(String[] args) throws Exception {
        World w = (World) Mock.world();
        NamespacedKey tag = new NamespacedKey("kuby", "tag");
        NamespacedKey click = new NamespacedKey("kuby", "click");
        switch (args[0]) {
            case "slots", "slots_gold", "slots_neon" -> {
                double width = args.length > 2 ? Double.parseDouble(args[2]) : 2.7;
                String style = args[0].equals("slots") ? "klassik" : args[0].substring(6);
                SlotMachine m = new SlotMachine("s1", new Location(w, 0.5, 1.5, 0.03), 0f, width, 1,
                        new Location(w, 0.5, 0, 1.6), tag, click, style);
                String[][] start = {{"cherry", "seven", "bell"}, {"lemon", "seven", "diamond"}, {"star", "seven", "coin"}};
                m.build(start);
                m.panel(new long[]{10, 50, 100, 500, 1000, 5000}, 2);
                m.holo(20080, 10, 5000);
                m.seat();
            }
            case "slot_spin" -> {
                // Ganzer Spin Bild für Bild (args[2] = Ordner, args[3] = Ergebnis z. B. 9,9,9): Walzen wie im Spiel,
                // Kamera wie im Spiel (Spieler-Blick, ab der zweiten Walze die Kino-Szene), Münzen auf Wurfbahnen
                String dir = args[2];
                String[] rs = (args.length > 3 ? args[3] : "9,9,9").split(",");
                int[] result = {Integer.parseInt(rs[0]), Integer.parseInt(rs[1]), Integer.parseInt(rs[2])};
                SlotMachine m = new SlotMachine("s1", new Location(w, 0.5, 1.5, 0.03), 0f, 2.7, 1,
                        new Location(w, 0.5, 0, 1.6), tag, click, "gold");
                m.build(new String[][]{{"lemon", "cherry", "bell"}, {"clover", "cherry", "chip"}, {"star", "bell", "coin"}});
                m.panel(new long[]{10, 50, 100, 500, 1000, 5000}, 2);
                m.holo(20080, 10, 5000);
                boolean tension = result[0] == result[1];
                boolean win = (result[0] == result[1] && result[1] == result[2]) || result[0] == 0 || result[1] == 0 || result[2] == 0;
                int[] arrive = {32, 46, tension ? 46 + 66 : 60};
                de.kuby.kubyplugins.casino.SlotSpin[] spin = new de.kuby.kubyplugins.casino.SlotSpin[3];
                for (int i = 0; i < 3; i++) spin[i] = de.kuby.kubyplugins.casino.SlotSpin.plan(m.reelAngle(i),
                        SlotMachine.stopAngle(i, SlotMachine.SYMBOLS[result[i]]), arrive[i], tension && i == 2 ? arrive[1] + 6 : -1);
                int start = spin[1].arrive;
                de.kuby.kubyplugins.cinema.V3[] faces = new de.kuby.kubyplugins.cinema.V3[3];
                for (int i = 0; i < 3; i++) {
                    Location f = m.reelFront(i);
                    faces[i] = new de.kuby.kubyplugins.cinema.V3(f.getX(), f.getY(), f.getZ());
                }
                var script = new de.kuby.kubyplugins.cinema.scenes.SlotTension(faces,
                        new de.kuby.kubyplugins.cinema.V3(0, 0, 1), spin[2].arrive - start, SlotMachine.SYMBOLS[result[0]], win, null, 1);
                int end = Math.max(spin[2].length() + 30, tension ? start + script.length() : 0);
                java.util.Random rnd = new java.util.Random(3);
                int coins = win ? 60 : 0;
                double[][] cv = new double[coins][3];
                for (double[] c : cv) {
                    c[0] = (rnd.nextDouble() * 2 - 1) * 0.35;
                    c[1] = 0.35 + rnd.nextDouble() * 0.35;
                    c[2] = 0.05 + rnd.nextDouble() * 0.25;
                }
                java.util.List<org.bukkit.entity.Entity> flying = new java.util.ArrayList<>();
                StringBuilder cams = new StringBuilder();
                new java.io.File(dir).mkdirs();
                for (int t = 0; t < end; t++) {
                    for (int i = 0; i < 3; i++) {
                        de.kuby.kubyplugins.casino.SlotSpin sp = spin[i];
                        int k = Math.min(t, sp.length() - 1);
                        m.reel(i, sp.angle[k], Math.abs(sp.speed[k]) > 14, 0);
                    }
                    for (org.bukkit.entity.Entity e : flying) e.remove();
                    flying.clear();
                    int since = t - spin[2].arrive;
                    if (coins > 0 && since >= 0) {
                        Location from = m.effectLocation();
                        for (int c = 0; c < coins; c++) {
                            double dt = (since - c / 6) / 20.0 * 1.0;
                            if (dt < 0) continue;
                            double tt = dt * 20;
                            double x = from.getX() + cv[c][0] * tt, z = from.getZ() + cv[c][2] * tt;
                            double y = from.getY() + cv[c][1] * tt - 0.04 * tt * tt / 2;
                            if (y < 0.05) y = 0.05;
                            int cc = c;
                            flying.add(w.spawn(new Location(w, x, y, z), org.bukkit.entity.ItemDisplay.class, d -> {
                                d.setItemStack(BlackjackTable.model(cc % 4 == 0 ? "slot_chip" : "slot_coin"));
                                d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),
                                        new org.joml.Quaternionf().rotateY((float) (tt * 0.4 + cc)), new org.joml.Vector3f(0.28f, 0.28f, 0.28f),
                                        new org.joml.Quaternionf()));
                            }));
                        }
                    }
                    try (FileWriter fw = new FileWriter(String.format("%s/f%04d.json", dir, t))) {
                        fw.write(Mock.dump().toString());
                    }
                    double cx = 0.5, cy = 1.95, cz = 1.75, lx = 0.5, ly = 1.42, lz = 0.1;
                    String grade = "-";
                    double bars = 0;
                    boolean flash = false;
                    String lights = "";
                    if (tension && t >= start && t - start < script.length()) {
                        de.kuby.kubyplugins.cinema.Frame fr = new de.kuby.kubyplugins.cinema.Frame();
                        script.frame(t - start, fr);
                        cx = fr.cam.x();
                        cy = fr.cam.y();
                        cz = fr.cam.z();
                        de.kuby.kubyplugins.cinema.V3 d = de.kuby.kubyplugins.cinema.V3.dir(fr.camYaw, fr.camPitch);
                        lx = cx + d.x();
                        ly = cy + d.y();
                        lz = cz + d.z();
                        grade = fr.grade == null ? "-" : fr.grade;
                        bars = fr.bars;
                        for (Object ev : fr.events) if (ev instanceof de.kuby.kubyplugins.cinema.Ev.Flash) flash = true;
                        StringBuilder lp = new StringBuilder();
                        for (de.kuby.kubyplugins.cinema.Prop pr : fr.props) {
                            lp.append(String.format(java.util.Locale.ROOT, " %s:%.4f:%.4f:%.4f:%.4f:%06x", pr.what().replace("kuby:fx/", ""),
                                    pr.pos().x(), pr.pos().y(), pr.pos().z(), pr.scale().x(), pr.color() & 0xFFFFFF));
                        }
                        lights = lp.toString();
                    }
                    cams.append(String.format(java.util.Locale.ROOT, "%d %.4f %.4f %.4f %.4f %.4f %.4f %s %.3f %b%s%n", t, cx, cy, cz, lx, ly, lz, grade, bars, flash, lights));
                }
                try (FileWriter fw = new FileWriter(dir + "/cams.txt")) {
                    fw.write(cams.toString());
                }
            }
            case "dealer_anim" -> {
                // Ein Clip: Bild k liegt bei x = 4k (args[2] = Geste, args[3] = Bilder, args[4] = Ticks je Bild)
                int frames = Integer.parseInt(args[3]), every = Integer.parseInt(args[4]);
                for (int k = 0; k < frames; k++) {
                    de.kuby.kubyplugins.casino.DealerAnim d = new de.kuby.kubyplugins.casino.DealerAnim(Mock.plugin(), w,
                            new Location(w, k * 4.0, 0, 0), 0f, null, null);
                    d.simulate(args[2], k * every);
                }
            }
            case "models" -> {
                // alle Casino-Item-Modelle im Raster (args[2] = Ordner items/casino), Reihen zu 12
                java.io.File[] fs = new java.io.File(args[2]).listFiles((dir, n) -> n.endsWith(".json"));
                java.util.Arrays.sort(fs);
                for (int i = 0; i < fs.length; i++) {
                    String n = fs[i].getName().replace(".json", "");
                    Location l = new Location(w, (i % 12) * 1.5, 0.5, (i / 12) * -3.0);
                    w.spawn(l, org.bukkit.entity.ItemDisplay.class, d -> {
                        d.setItemStack(BlackjackTable.model(n));
                        d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE);
                    });
                    w.spawn(l.clone().add(0, -0.75, 0.6), org.bukkit.entity.TextDisplay.class, td -> {
                        td.text(Component.text(n, NamedTextColor.WHITE));
                        td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                    });
                }
            }
            case "dealer" -> {
                // Posen des Dealers nebeneinander: Name:Tick
                String[] poses = {"rest:0", "wait:0", "lean:0", "tie:15", "sleeve:15", "cuff:16", "sweep:8", "sweep:26",
                        "deal:4", "shuffle:12", "applaud:5", "bow:12"};
                for (int i = 0; i < poses.length; i++) {
                    String[] pt = poses[i].split(":");
                    de.kuby.kubyplugins.casino.DealerAnim d = new de.kuby.kubyplugins.casino.DealerAnim(Mock.plugin(), w,
                            new Location(w, i * 1.4, 0, 0), 0f, null, null);
                    d.previewPose(pt[0], Integer.parseInt(pt[1]));
                    w.spawn(new Location(w, i * 1.4, 2.25, 0), org.bukkit.entity.TextDisplay.class, td -> {
                        td.text(Component.text(pt[0] + " " + pt[1], NamedTextColor.WHITE));
                        td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                    });
                }
            }
            case "blackjack", "blackjack_rot" -> {
                BlackjackTable t = new BlackjackTable(Mock.plugin(), "b1", new Location(w, 0.5, 0, 0.5), 0f, 3.0, tag, click)
                        .highRoller(args[0].endsWith("rot"));
                t.build();
                t.card(2, 0, 0, new BjCards.Card(13, 'c'), 1);
                t.card(2, 0, 1, new BjCards.Card(4, 'd'), 1);
                t.card(1, 0, 0, new BjCards.Card(1, 'h'), 1);
                t.card(1, 0, 1, new BjCards.Card(9, 's'), 1);
                t.card(5, 0, 0, new BjCards.Card(6, 'h'), 1);
                t.card(5, 0, 1, null, 1);
                t.total(2, "14");
                t.total(1, "20");
                t.bet(2, List.of(100L, 100L, 50L), 250);
                t.rack(2, new long[]{10, 50, 100, 500, 1000, 5000});
                t.actions(2, new boolean[]{true, true, true, false});
                t.timer(2, 0.6);
                t.rack(3, new long[]{10, 50, 100, 500, 1000, 5000});
                t.controls(3, true, false, Boolean.FALSE);
                t.bet(1, List.of(500L, 100L), 600);
                t.result(1, Component.text("GEWONNEN", NamedTextColor.AQUA));
                t.total(5, "17");
                t.dealerText(Component.text("Dealer", NamedTextColor.GOLD));
                t.seatText(0, Component.text("Freier Platz", NamedTextColor.GOLD));
                Mock.runLater();
            }
            case "poker", "poker_rot" -> {
                de.kuby.kubyplugins.casino.PokerTable t = new de.kuby.kubyplugins.casino.PokerTable(Mock.plugin(), "pk",
                        new Location(w, 0.5, 0, 0.5), 0f, tag, click).highRoller(args[0].endsWith("rot"));
                t.build();
                for (int i = 0; i < 6; i++) t.holeCards(i, null, new String[]{"ah", "kd"});
                t.reveal(1);
                String[] b5 = {"10s", "js", "qs", "2h", "9c"};
                for (int k = 0; k < 5; k++) t.boardCard(k, b5[k]);
                t.bet(0, 200, false);
                t.bet(4, 600, false);
                t.pot(2400, "1 Side-Pot", false);
                t.dealerButton(2);
                t.buttons(0, new String[]{"fold", "call", "raise", "allin"}, Component.text("Mitgehen 400", NamedTextColor.WHITE));
                for (int i = 0; i < 6; i++) t.plate(i, Component.text("Spieler " + (i + 1), NamedTextColor.WHITE));
                t.dealerText(Component.text("POKER", NamedTextColor.GOLD));
                Mock.runLater();
            }
            case "roulette" -> {
                RouletteTable t = new RouletteTable(tag);
                t.geometry(new Location(w, 0.5, 1.0, 0.5), 3.75, 2.75, 0f);
                t.build();
                t.center(Component.text("ROULETTE", NamedTextColor.WHITE).append(Component.newline()).append(Component.text("Warte auf Einsätze..", NamedTextColor.YELLOW)));
                t.chip("zahl", 17, 100);
                t.chip("zahl", 17, 50);
                t.chip("rot", 0, 500);
                t.chip("dutzend2", 0, 1000);
                t.light(RouletteTable.position(17), true);
            }
            case "crash", "crash_bereit", "crash_crash" -> {
                de.kuby.kubyplugins.casino.CrashScreen c = new de.kuby.kubyplugins.casino.CrashScreen(
                        new Location(w, 0.5, 2.5, 0.01), 0f, 6, 3, null, null, tag);
                c.build();
                net.kyori.adventure.key.Key title = net.kyori.adventure.key.Key.key("kuby", "title");
                net.kyori.adventure.text.format.TextColor green = net.kyori.adventure.text.format.TextColor.color(0x5AE878);
                net.kyori.adventure.text.format.TextColor red = net.kyori.adventure.text.format.TextColor.color(0xFF4A4A);
                Component hint = Component.text("Sneaken + Klick = Auszahlen", net.kyori.adventure.text.format.TextColor.color(0x9A9AA4));
                double cur = args[0].equals("crash_crash") ? 1.48 : 1.45;
                if (args[0].equals("crash_bereit")) {
                    c.texts(Component.text(de.kuby.kubyplugins.ui.Canvas.titleChars("BEREIT", false)).font(title).color(NamedTextColor.WHITE), 0.6f,
                            Component.text("Einsätze", net.kyori.adventure.text.format.TextColor.color(0xF6E23A))
                                    .append(Component.text(" — ", NamedTextColor.GRAY)).append(Component.text("9s", NamedTextColor.WHITE)), hint);
                    c.hideCurve();
                } else {
                    boolean crashed = args[0].equals("crash_crash");
                    String v = String.format(java.util.Locale.GERMAN, "%.2fx", cur);
                    Component big = Component.text(v, crashed ? red : green).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
                    c.texts(big, 1.0f, crashed ? Component.text("CRASH", red).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                            .append(Component.text(" — 1 Spieler verloren 50 Chips", NamedTextColor.WHITE)
                                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false))
                            : Component.text("Läuft", NamedTextColor.GRAY), crashed ? Component.empty() : hint);
                    double rate = 0.07, t = Math.log(cur) / rate, tMax = Math.max(10, t * 1.06), mMax = Math.max(1.8, 1 + (cur - 1) * 1.1);
                    int n = de.kuby.kubyplugins.casino.CrashScreen.SEGMENTS;
                    double[] xs = new double[n + 1], ys = new double[n + 1];
                    for (int i = 0; i <= n; i++) {
                        double ti = t * i / n, m = Math.exp(rate * ti);
                        xs[i] = c.plotLeft() + ti / tMax * c.plotWidth();
                        ys[i] = c.plotBottom() + (m - 1) / (mMax - 1) * c.plotHeight();
                    }
                    c.curve(xs, ys, crashed, big);
                }
                c.rounds(Component.text("LETZTE RUNDEN", net.kyori.adventure.text.format.TextColor.color(0xF2A02A))
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD).append(Component.newline())
                        .append(Component.text("1,96x", net.kyori.adventure.text.format.TextColor.color(0xFF6A6A))
                                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false))
                        .append(Component.text(" · ", NamedTextColor.DARK_GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false))
                        .append(Component.text("3,19x", green).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)));
            }
            case "holos" -> {
                // Jackpot (links), Eingang (Mitte), Ranking an der Wand (rechts) wie in CasinoHolos
                java.util.function.BiFunction<Location, Object[], org.bukkit.entity.TextDisplay> holo = (at, o) ->
                        w.spawn(at, org.bukkit.entity.TextDisplay.class, d -> {
                            d.text((Component) o[0]);
                            float sc = (Float) o[1];
                            boolean bb = (Boolean) o[2];
                            d.setBillboard(bb ? org.bukkit.entity.Display.Billboard.VERTICAL : org.bukkit.entity.Display.Billboard.FIXED);
                            d.setBackgroundColor(org.bukkit.Color.fromARGB(o.length > 3 ? (Integer) o[3] : 0, 0, 0, 0));
                            d.setShadowed(true);
                            d.setLineWidth(400);
                            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(),
                                    new org.joml.Vector3f(sc, sc, sc), new org.joml.Quaternionf()));
                        });
                de.kuby.kubyplugins.casino.CasinoHolos.class.getName();
                Location j = new Location(w, -4.5, 0, 0.5);
                holo.apply(j.clone().add(0, 3.05, 0), new Object[]{de.kuby.kubyplugins.casino.CasinoHolos.jackpotTitle(), 0.62f, true});
                holo.apply(j.clone().add(0, 2.62, 0), new Object[]{de.kuby.kubyplugins.casino.CasinoHolos.jackpotAmount(192203), 1.35f, true});
                holo.apply(j.clone().add(0, 1.3, 0), new Object[]{de.kuby.kubyplugins.casino.CasinoHolos.jackpotBody("Samstag 18:00", "22 Std. 46 Min.",
                        221, "OfficalNixo", 25265), 0.8f, true});
                holo.apply(j.clone().add(0, 0.98, 0), new Object[]{Component.text("✦ ", net.kyori.adventure.text.format.TextColor.color(0xF23A5A))
                        .append(Component.text("Du bist dabei ✓", net.kyori.adventure.text.format.TextColor.color(0x5AE878))
                                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                        .append(Component.text(" ✦", net.kyori.adventure.text.format.TextColor.color(0xF6E23A))), 0.95f, true});
                Location e = new Location(w, 0.5, 0, 0.5);
                holo.apply(e.clone().add(0, 3.0, 0), new Object[]{de.kuby.kubyplugins.casino.CrashScreen.logo("CASINO",
                        de.kuby.kubyplugins.casino.CasinoHolos.CASINO_LOGO), 1.4f, true});
                holo.apply(e.clone().add(0, 0.9, 0), new Object[]{de.kuby.kubyplugins.casino.CasinoHolos.entranceBody(), 1.0f, true});
                Location r = new Location(w, 5.5, 1.0, -0.95);
                holo.apply(r, new Object[]{de.kuby.kubyplugins.casino.CasinoHolos.rankingText(
                        List.of("Kyamix", "Shortsguy", "Imperiatos", "OfficalNixo", "_TestoThorsten_"),
                        List.of(1345800L, 679000L, 303000L, 233768L, 217203L)), 0.62f, false, 70});
            }
            case "npcs" -> {
                de.kuby.kubyplugins.casino.CasinoNpc.Role[] roles = {de.kuby.kubyplugins.casino.CasinoNpc.Role.MIA,
                        de.kuby.kubyplugins.casino.CasinoNpc.Role.LOSBUDE, de.kuby.kubyplugins.casino.CasinoNpc.Role.CRASH,
                        de.kuby.kubyplugins.casino.CasinoNpc.Role.KASSE};
                String[] skins = {"mia", "croupier", null, null};
                for (int i = 0; i < roles.length; i++) {
                    Location base = new Location(w, -4.5 + 3 * i, 0, 0.5);
                    var role = roles[i];
                    Component text = de.kuby.kubyplugins.casino.CasinoNpc.body(role);
                    int lines = 1 + (int) net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(text).chars().filter(c -> c == '\n').count();
                    for (Object[] o : new Object[][]{{de.kuby.kubyplugins.casino.CasinoNpc.title(role), 0.62f, 2.12 + 0.25 * lines},
                            {text, 1.0f, 2.12}}) {
                        w.spawn(base.clone().add(0, (Double) o[2], 0), org.bukkit.entity.TextDisplay.class, d -> {
                            d.text((Component) o[0]);
                            float sc = (Float) o[1];
                            d.setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);
                            d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                            d.setShadowed(true);
                            d.setLineWidth(400);
                            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(),
                                    new org.joml.Vector3f(sc, sc, sc), new org.joml.Quaternionf()));
                        });
                    }
                    if (skins[i] != null) {
                        String sk = skins[i];
                        w.spawn(base, org.bukkit.entity.Mannequin.class, m -> m.setProfile(
                                io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile().name("x")
                                        .skinPatch(sp -> sp.body(net.kyori.adventure.key.Key.key("kuby", "entity/casino/" + sk))
                                                .model(org.bukkit.profile.PlayerTextures.SkinModel.SLIM)).build()));
                    }
                }
            }
            case "furniture" -> {
                // Möbel aus KubySurvival (FurnitureCatalog) per Reflexion, damit der Kern ohne KubySurvival baut
                try {
                    Class<?> cat = Class.forName("de.kuby.kubysurvival.furniture.FurnitureCatalog");
                    java.util.List<?> defs = (java.util.List<?>) cat.getMethod("all").invoke(null);
                    int k = 0;
                    for (Object d : defs) {
                        java.util.List<?> parts = (java.util.List<?>) d.getClass().getMethod("parts").invoke(d);
                        int width = (Integer) d.getClass().getMethod("width").invoke(d);
                        double ox = (k % 6) * 2.5, oz = (k / 6) * 3.0;
                        k++;
                        for (Object pt : parts) {
                            Class<?> pc = pt.getClass();
                            org.bukkit.Material m = (org.bukkit.Material) pc.getMethod("m").invoke(pt);
                            float[] v = new float[6];
                            String[] names = {"x", "y", "z", "sx", "sy", "sz"};
                            for (int i = 0; i < 6; i++) v[i] = (Float) pc.getMethod(names[i]).invoke(pt);
                            w.spawn(new Location(w, ox + 0.5, 0, oz + 0.5), org.bukkit.entity.BlockDisplay.class, bd -> {
                                bd.setBlock(m.createBlockData());
                                bd.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(v[0], v[1], v[2]),
                                        new org.joml.Quaternionf(), new org.joml.Vector3f(v[3], v[4], v[5]), new org.joml.Quaternionf()));
                            });
                        }
                    }
                } catch (ReflectiveOperationException ex) {
                    throw new RuntimeException(ex);
                }
            }
            case "grave" -> {
                // Grabstein wie GraveService.spawn (KubySurvival): Sockel, Stein, Kappe, Kreuz, Schild
                Location at = new Location(w, 0.5, 0, 0.5);
                float[][] parts = {{-0.42f, 0f, -0.30f, 0.84f, 0.14f, 0.60f}, {-0.30f, 0.14f, 0.02f, 0.60f, 0.70f, 0.16f},
                        {-0.22f, 0.84f, 0.02f, 0.44f, 0.08f, 0.16f}, {-0.035f, 0.36f, -0.005f, 0.07f, 0.36f, 0.03f},
                        {-0.11f, 0.56f, -0.005f, 0.22f, 0.07f, 0.03f}};
                org.bukkit.Material[] mats = {org.bukkit.Material.MOSSY_COBBLESTONE, org.bukkit.Material.POLISHED_ANDESITE,
                        org.bukkit.Material.POLISHED_ANDESITE, org.bukkit.Material.POLISHED_DEEPSLATE, org.bukkit.Material.POLISHED_DEEPSLATE};
                for (int k = 0; k < parts.length; k++) {
                    float[] q = parts[k];
                    org.bukkit.Material m = mats[k];
                    w.spawn(at, org.bukkit.entity.BlockDisplay.class, d -> {
                        d.setBlock(m.createBlockData());
                        d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(q[0], q[1], q[2]), new org.joml.Quaternionf(),
                                new org.joml.Vector3f(q[3], q[4], q[5]), new org.joml.Quaternionf()));
                    });
                }
                w.spawn(at.clone().add(0, 1.15, 0), org.bukkit.entity.TextDisplay.class, d -> {
                    d.text(de.kuby.kubyplugins.util.Msg.parse("<#D2D2DA>✝ <b>Luno2110</b>\n<gray>noch <white>27:41</white> · 214 Items"));
                    d.setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);
                    d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                    d.setShadowed(true);
                    d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(),
                            new org.joml.Vector3f(0.8f, 0.8f, 0.8f), new org.joml.Quaternionf()));
                });
            }
            case "survival_npcs" -> {
                // Priester und Bankier wie SurvivalNpc (KubySurvival): breite Arme, Schild darüber
                String[][] npcs = {{"priester", "PRIESTER", "#FF7AC0", "Rechtsklick · Heiraten"},
                        {"bankier", "BANKIER", "#F2C84A", "Rechtsklick · Bank und Konten"}};
                for (int i = 0; i < npcs.length; i++) {
                    String[] n = npcs[i];
                    Location base = new Location(w, -1.5 + 3 * i, 0, 0.5);
                    w.spawn(base.clone().add(0, 2.15, 0), org.bukkit.entity.TextDisplay.class, d -> {
                        d.text(de.kuby.kubyplugins.util.Msg.parse("<" + n[2] + "><b>" + n[1] + "</b></" + n[2] + ">\n<gray>" + n[3]));
                        d.setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);
                        d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                        d.setShadowed(true);
                    });
                    w.spawn(base, org.bukkit.entity.Mannequin.class, m -> m.setProfile(
                            io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile().name("x")
                                    .skinPatch(sp -> sp.body(net.kyori.adventure.key.Key.key("kuby", "entity/survival/" + n[0]))
                                            .model(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC)).build()));
                }
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
        try (FileWriter fw = new FileWriter(args[1])) {
            fw.write(Mock.dump().toString());
        }
        System.out.println(Mock.ENTITIES.stream().filter(e -> !e.removed).count() + " Anzeigen");
    }
}
