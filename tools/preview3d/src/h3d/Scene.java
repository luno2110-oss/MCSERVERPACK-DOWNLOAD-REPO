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
            case "slots" -> {
                double width = args.length > 2 ? Double.parseDouble(args[2]) : 2.7;
                SlotMachine m = new SlotMachine("s1", new Location(w, 0.5, 1.5, 0.03), 0f, width, 1,
                        new Location(w, 0.5, 0, 1.6), tag, click);
                String[][] start = {{"cherry", "seven", "bell"}, {"lemon", "seven", "diamond"}, {"star", "seven", "coin"}};
                m.build(start);
                m.panel(new long[]{10, 50, 100, 500, 1000, 5000}, 2);
                m.holo(20080, 10, 5000);
                m.seat();
            }
            case "blackjack" -> {
                BlackjackTable t = new BlackjackTable(Mock.plugin(), "b1", new Location(w, 0.5, 0, 0.5), 0f, 3.0, tag, click);
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
                t.dealerText(Component.text("Dealer", NamedTextColor.GOLD));
                t.seatText(0, Component.text("Freier Platz", NamedTextColor.GOLD));
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
            default -> throw new IllegalArgumentException(args[0]);
        }
        try (FileWriter fw = new FileWriter(args[1])) {
            fw.write(Mock.dump().toString());
        }
        System.out.println(Mock.ENTITIES.stream().filter(e -> !e.removed).count() + " Anzeigen");
    }
}
