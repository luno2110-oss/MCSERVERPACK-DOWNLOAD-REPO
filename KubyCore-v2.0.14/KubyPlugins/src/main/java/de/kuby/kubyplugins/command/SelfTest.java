package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.casino.CasinoScreens;
import de.kuby.kubyplugins.gui.CosmeticsScreen;
import de.kuby.kubyplugins.gui.CrateScreens;
import de.kuby.kubyplugins.gui.FriendsScreen;
import de.kuby.kubyplugins.gui.PlaytimeScreen;
import de.kuby.kubyplugins.gui.StatsScreen;
import de.kuby.kubyplugins.service.CosmeticService;
import de.kuby.kubyplugins.service.SurvivalHelp;
import de.kuby.kubyplugins.support.SupportScreen;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.I18n;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * /kp selftest (Konsole): baut alle Grafik-Menüs und die Eingabe-Dialoge mit dem echten Server-Code, ohne sie
 * zu zeigen. Prüft so die Regeln von Paper für Dialoge (z. B. Pause/after_action, Breiten, Eingaben). Der CI-Server-Test
 * ruft das nach dem Start auf und sucht im Log nach "SELFTEST FEHLER".
 */
final class SelfTest {

    private SelfTest() {
    }

    private static final Consumer<Player> NO = p -> { };

    static void run(KubyPlugins plugin, CommandSender out) {
        List<String> fails = new ArrayList<>();
        List<String> layout = new ArrayList<>();
        List<String> bedrock = new ArrayList<>();
        StringBuilder forms = new StringBuilder();
        int[] ok = {0};
        int[] bf = {0, 0, 0};   // Formulare, Knöpfe, Knöpfe mit Bild
        java.util.function.BiConsumer<String, Supplier<Canvas>> canvas = (name, c) -> {
            try {
                Canvas cv = c.get();
                for (String issue : cv.lint()) layout.add(name + ": " + issue);
                // Bedrock: dasselbe Menü als Formular (Knopf je klickbarem Bereich, lesbare Beschriftung)
                de.kuby.kubyplugins.bedrock.FormModel.Model fm = de.kuby.kubyplugins.bedrock.FormModel.of(cv);
                long clicks = cv.clicks().stream().filter(k -> k.action() != null || k.url() != null).count();
                if (fm.buttons().size() != clicks) bedrock.add(name + ": " + fm.buttons().size() + " Knöpfe statt " + clicks);
                if (fm.content().isEmpty() && fm.buttons().isEmpty()) bedrock.add(name + ": leeres Formular");
                for (de.kuby.kubyplugins.bedrock.FormModel.Button b : fm.buttons()) {
                    String t = de.kuby.kubyplugins.bedrock.FormModel.plain(b.text().replaceAll("§.", ""));
                    if (t.isEmpty() || t.equals("›")) bedrock.add(name + ": Knopf ohne Beschriftung (" + b.hover() + ")");
                    if (b.image() != null) bf[2]++;
                }
                bf[0]++;
                bf[1] += fm.buttons().size();
                forms.append("=== ").append(name).append(" | ").append(fm.title()).append('\n').append(fm.content()).append('\n');
                for (de.kuby.kubyplugins.bedrock.FormModel.Button b : fm.buttons()) {
                    forms.append("  [").append(b.text().replace("\n", " / ")).append("]").append(b.image() == null ? "" : " <" + b.image() + ">").append('\n');
                }
                Dialog d = UiDialog.dialog(null, cv, 330);
                if (d == null) throw new IllegalStateException("kein Dialog");
                ok[0]++;
            } catch (Throwable t) {
                fails.add(name + ": " + t);
            }
        };
        SurvivalHelp.Actions ha = new SurvivalHelp.Actions() {
            public Consumer<Player> page(int index) { return NO; }
            public Consumer<Player> act(int index) { return NO; }
            public Consumer<Player> overview() { return NO; }
        };
        canvas.accept("Hilfe-Übersicht", () -> SurvivalHelp.overview(ha));
        for (int i = 0; i < SurvivalHelp.pageIds().size(); i++) {
            int k = i;
            canvas.accept("Hilfe " + SurvivalHelp.pageIds().get(i), () -> SurvivalHelp.screen(k, ha));
        }
        for (de.kuby.kubyplugins.casino.SlotTheme th : de.kuby.kubyplugins.casino.SlotTheme.ALL) {
            canvas.accept("Casino-Gewinntabelle " + th.key, () -> CasinoScreens.slotsPaytable(th, 100000, 100));
            canvas.accept("Casino-Gewinntabelle " + th.key + " 5000", () -> CasinoScreens.slotsPaytable(th, 2500000, 5000));
        }
        canvas.accept("Roulette-Verlauf", () -> CasinoScreens.rouletteHistory(List.of(0, 17, 32), NO));
        CasinoScreens.PlinkoActions pla = new CasinoScreens.PlinkoActions() {
            public Consumer<Player> risk(int i) { return NO; }
            public Consumer<Player> level(int i) { return NO; }
            public Consumer<Player> custom() { return NO; }
            public Consumer<Player> drop() { return NO; }
        };
        for (de.kuby.kubyplugins.casino.Plinko.Risk rk : de.kuby.kubyplugins.casino.Plinko.Risk.values()) {
            List<CasinoScreens.PlinkoResult> rec = new java.util.ArrayList<>();
            for (int sl : new int[]{6, 0, 3, 12, 7, 5, 9, 6, 2, 10, 11}) rec.add(new CasinoScreens.PlinkoResult(rk.mult(sl), sl, Math.round(5000 * rk.mult(sl)) - 5000));
            canvas.accept("Plinko " + rk.label, () -> CasinoScreens.plinko(new CasinoScreens.PlinkoView(12_345_678, rk, 5000,
                    de.kuby.kubyplugins.casino.PlinkoService.LEVELS, 5, "3 Kugeln im Fall", rec, 10, 15_000), pla));
        }
        CasinoScreens.RaceActions rca = new CasinoScreens.RaceActions() {
            public Consumer<Player> bet(int lane, boolean place) { return NO; }
            public Consumer<Player> chip(int index) { return NO; }
            public Consumer<Player> custom() { return NO; }
            public Consumer<Player> clear() { return NO; }
        };
        {
            java.util.Random rr = new java.util.Random(3);
            var rf = de.kuby.kubyplugins.casino.HorseRace.field(rr);
            long[][] stake = new long[6][2];
            stake[2][0] = 1000;
            stake[4][1] = 250;
            List<CasinoScreens.RaceBetLine> mine = List.of(new CasinoScreens.RaceBetLine(2, false, 1000, rf.winOdds()[2]),
                    new CasinoScreens.RaceBetLine(4, true, 250, rf.placeOdds()[4]), new CasinoScreens.RaceBetLine(4, false, 15000, rf.winOdds()[4]));
            canvas.accept("Pferderennen offen", () -> CasinoScreens.race(new CasinoScreens.RaceView(12_345_678, rf, stake, mine, 5, 15000,
                    "Wetten offen · Start in 23 s", true, List.of(2, 0, 4, 2, 1, 5, 3, 0, 1, 2, 3), 10, 60_000), rca));
            canvas.accept("Pferderennen läuft", () -> CasinoScreens.race(new CasinoScreens.RaceView(0, rf, new long[6][2], List.of(), 2, 100,
                    "Das Rennen läuft", false, List.of(), 10, 20_000), rca));
        }
        canvas.accept("Plinko leer, eigener Einsatz", () -> CasinoScreens.plinko(new CasinoScreens.PlinkoView(0, de.kuby.kubyplugins.casino.Plinko.Risk.MITTEL,
                2500, new long[]{10, 50, 100, 500, 1000, 5000, 15000}, -1, "Wähle Risiko und Einsatz, dann BALL", List.of(), 10, 15_000), pla));
        FriendsScreen.Actions fa = new FriendsScreen.Actions() {
            public Consumer<Player> tab(FriendsScreen.Tab t, boolean sent) { return NO; }
            public Consumer<Player> accept(String n) { return NO; }
            public Consumer<Player> deny(String n) { return NO; }
            public Consumer<Player> jump(String n) { return NO; }
            public Consumer<Player> write(String n) { return NO; }
            public Consumer<Player> remove(String n) { return NO; }
            public Consumer<Player> request(String n) { return NO; }
            public Consumer<Player> page(int p) { return NO; }
        };
        for (FriendsScreen.Tab t : FriendsScreen.Tab.values()) {
            for (boolean full : new boolean[]{false, true}) {
                canvas.accept("Freunde " + t + (full ? " voll" : " leer"), () -> FriendsScreen.canvas(new FriendsScreen.View(t, full,
                        "Survival", full ? List.of(new FriendsScreen.Row("Test", true, true, "Spawn")) : List.of(),
                        full ? List.of(new FriendsScreen.Req("Test2", true), new FriendsScreen.Req("Test3", false)) : List.of(),
                        full ? List.of(new FriendsScreen.Near("Test4", 12), new FriendsScreen.Near("WWWWWWWWWWWWWWWW", 99, "angefragt"),
                                new FriendsScreen.Near("Test6", 51, "fragt dich an"), new FriendsScreen.Near("Test7", 64), new FriendsScreen.Near("Test8", 70),
                                new FriendsScreen.Near("Test9", 80), new FriendsScreen.Near("Test10", 95)) : List.of(), 25, 0, null, 100), fa));
            }
        }
        // Team-Bereich: alle Seiten mit langen Namen und vollen Listen
        {
            de.kuby.kubyplugins.staff.StaffScreen.Nav sn = k -> NO;
            var red = de.kuby.kubyplugins.staff.StaffScreen.color("#FF5A5A");
            var sf = new de.kuby.kubyplugins.staff.StaffScreen.Frame("Moderator", red, 12, 120, 99, 7, true, true);
            String ww = "WWWWWWWWWWWWWWWW";
            List<de.kuby.kubyplugins.staff.StaffScreen.ChannelRow> chs = new ArrayList<>();
            for (de.kuby.kubyplugins.staff.StaffService.Channel ch : de.kuby.kubyplugins.staff.StaffService.Channel.values()) {
                chs.add(new de.kuby.kubyplugins.staff.StaffScreen.ChannelRow(ch.key, ch.desc, 150, true));
            }
            List<de.kuby.kubyplugins.staff.StaffScreen.Member> mem = new ArrayList<>();
            List<de.kuby.kubyplugins.staff.StaffScreen.Feed> feed = new ArrayList<>();
            List<de.kuby.kubyplugins.staff.StaffScreen.Line> lines = new ArrayList<>();
            List<de.kuby.kubyplugins.staff.StaffScreen.ReportRow> reps = new ArrayList<>();
            List<de.kuby.kubyplugins.staff.StaffScreen.TicketRow> tks = new ArrayList<>();
            List<de.kuby.kubyplugins.staff.StaffScreen.PlayerRow> prs = new ArrayList<>();
            String longText = "Sehr lange Nachricht ".repeat(14);
            for (int i = 0; i < 30; i++) {
                mem.add(new de.kuby.kubyplugins.staff.StaffScreen.Member(ww, "Admin", red, "survival", "akte:x"));
                feed.add(new de.kuby.kubyplugins.staff.StaffScreen.Feed(i % 2 == 0 ? "meldung" : "strafe", longText, "vor 12 Tagen"));
                lines.add(new de.kuby.kubyplugins.staff.StaffScreen.Line(ww, red, "vor 12 Tagen", longText));
                reps.add(new de.kuby.kubyplugins.staff.StaffScreen.ReportRow(9999, ww, ww, longText, "bei " + ww, red, "vor 12 Tagen"));
                tks.add(new de.kuby.kubyplugins.staff.StaffScreen.TicketRow(9999, longText, "beim Team", red, longText, "vor 12 Tagen"));
                prs.add(new de.kuby.kubyplugins.staff.StaffScreen.PlayerRow(ww, red, "skyblock", 99, 99, "akte:x"));
            }
            List<String[]> facts = new ArrayList<>();
            for (int i = 0; i < 7; i++) facts.add(new String[]{"Spielzeit", longText});
            List<String[]> notes = new ArrayList<>();
            for (int i = 0; i < 5; i++) notes.add(new String[]{longText, "vor 12 Tagen"});
            canvas.accept("Team-Bereich Übersicht voll", () -> de.kuby.kubyplugins.staff.StaffScreen.home(
                    new de.kuby.kubyplugins.staff.StaffScreen.Home(sf, chs, 999, 999, 999, 999, mem, feed), sn));
            canvas.accept("Team-Bereich Übersicht leer", () -> de.kuby.kubyplugins.staff.StaffScreen.home(
                    new de.kuby.kubyplugins.staff.StaffScreen.Home(new de.kuby.kubyplugins.staff.StaffScreen.Frame("Team", red, 1, 0, 0, 0, false, false),
                            chs.subList(0, 2), 0, 0, 0, 0, List.of(), List.of()), sn));
            canvas.accept("Team-Bereich Kanal voll", () -> de.kuby.kubyplugins.staff.StaffScreen.channel(
                    new de.kuby.kubyplugins.staff.StaffScreen.ChannelView(sf, chs, "ankündigungen", longText, false, true, lines, 5), sn));
            canvas.accept("Team-Bereich Kanal leer", () -> de.kuby.kubyplugins.staff.StaffScreen.channel(
                    new de.kuby.kubyplugins.staff.StaffScreen.ChannelView(sf, chs, "allgemein", "", true, false, List.of(), 0), sn));
            canvas.accept("Team-Bereich Schreiben", () -> de.kuby.kubyplugins.staff.StaffScreen.write("allgemein", longText, lines));
            canvas.accept("Team-Bereich Meldungen", () -> de.kuby.kubyplugins.staff.StaffScreen.reports(
                    new de.kuby.kubyplugins.staff.StaffScreen.Reports(sf, false, reps.subList(0, 8), 3, 9, 999, 999, 9999), sn));
            canvas.accept("Team-Bereich Meldung", () -> de.kuby.kubyplugins.staff.StaffScreen.report(
                    new de.kuby.kubyplugins.staff.StaffScreen.ReportDetail(sf, 9999, ww, true, "x", ww, "09.11.2026 18:42", "vor 12 Tagen",
                            "skyblock_nether · -29999999 320 -29999999", longText, "in Arbeit", red, ww, longText, 999, true, true, true), sn));
            canvas.accept("Team-Bereich Tickets", () -> de.kuby.kubyplugins.staff.StaffScreen.tickets(
                    new de.kuby.kubyplugins.staff.StaffScreen.Tickets(sf, "arbeit", true, 999, 999, 999, tks.subList(0, 6), 2, 5, mem), sn));
            canvas.accept("Team-Bereich Spieler", () -> de.kuby.kubyplugins.staff.StaffScreen.players(
                    new de.kuby.kubyplugins.staff.StaffScreen.Players(sf, prs.subList(0, 22), 0, 3, 999), sn));
            canvas.accept("Team-Bereich Akte", () -> de.kuby.kubyplugins.staff.StaffScreen.akte(
                    new de.kuby.kubyplugins.staff.StaffScreen.Akte(sf, ww, "x", false, longText, facts, notes, 999, notes, 999, true, true, false), sn));
            canvas.accept("Team-Bereich Akte ohne Rechte", () -> de.kuby.kubyplugins.staff.StaffScreen.akte(
                    new de.kuby.kubyplugins.staff.StaffScreen.Akte(sf, "Test", "x", true, "online", facts.subList(0, 2), List.of(), 0, List.of(), 0,
                            false, false, true), sn));
            List<de.kuby.kubyplugins.staff.StaffScreen.Tile> tiles = new ArrayList<>(de.kuby.kubyplugins.staff.StaffScreen.coreTiles());
            tiles.add(new de.kuby.kubyplugins.staff.StaffScreen.Tile("Verwaltung", org.bukkit.Material.IRON_SWORD, ww, longText, "addon0"));
            canvas.accept("Kadmin", () -> de.kuby.kubyplugins.staff.StaffScreen.kadmin(
                    new de.kuby.kubyplugins.staff.StaffScreen.Kadmin(sf, "2.0.14", 999, tiles), sn));
            canvas.accept("Team-Bereich Eingabe", () -> de.kuby.kubyplugins.staff.StaffScreen.prompt(KitData.Icon.BELL, "VERWARNEN", longText,
                    List.of(longText)));
        }
        canvas.accept("Spielzeit", () -> PlaytimeScreen.canvas(new PlaytimeScreen.View(90, List.of(
                new PlaytimeScreen.Row(1, "Neuling", "grau", 60, List.of(new PlaytimeScreen.Reward("100 Coins",
                        TextColor.color(0xFCD45D), "+100 Coins", "gold")), PlaytimeScreen.State.READY),
                new PlaytimeScreen.Row(2, "Legende der Spielzeit", "rot", 90000, List.of(), PlaytimeScreen.State.LOCKED)),
                "Discord fehlt", -1), new PlaytimeScreen.Actions() {
                            public Consumer<Player> claim(int n) { return NO; }
                            public Consumer<Player> claimAll() { return NO; }
                            public Consumer<Player> verify() { return NO; }
                            public Consumer<Player> page(int p) { return NO; }
                        }));
        canvas.accept("Kosmetik", () -> CosmeticsScreen.canvas(new CosmeticsScreen.View(List.<String[]>of(new String[]{"HAT", "Hüte"}),
                "HAT", List.of(new CosmeticsScreen.Tile("x", "Zylinder", CosmeticService.Rarity.RARE, KitData.Icon.STAR, true,
                CosmeticsScreen.State.BUY, "60 Juwelen")), 10, true, 0, "Hinweis"), new CosmeticsScreen.Actions() {
                    public Consumer<Player> tab(String k) { return NO; }
                    public Consumer<Player> click(String id) { return NO; }
                    public Consumer<Player> pets() { return NO; }
                    public Consumer<Player> page(int p) { return NO; }
                }));
        canvas.accept("Kisten-Inhalt", () -> CrateScreens.preview(new CrateScreens.PreviewView("Test", TextColor.color(0xFFFFFF),
                List.of(new CrateScreens.Line(KitData.Icon.EMERALD, "1 Juwel", 50)), 1, true), NO, NO));
        canvas.accept("Schlüssel", () -> CrateScreens.keys(new CrateScreens.KeysView("Survival", List.of(
                new CrateScreens.KeyRow("vote", "Vote-Schlüssel", TextColor.color(0xFFFFFF), 1))), id -> NO, NO));
        canvas.accept("Statistik", () -> StatsScreen.canvas(new StatsScreen.View("Test", "online", true, List.of(
                new StatsScreen.Cell(KitData.Icon.CLOCK, "Spielzeit", "1 Std.", TextColor.color(0xFFFFFF))),
                List.of(new StatsScreen.Board("Reichste", "baltop"))), c -> NO));

        // Einstellungen: alle Reiter in SMP und Survival, Deutsch und Englisch, und die Sprachseite
        de.kuby.kubyplugins.gui.SettingsScreen.Actions sa = new de.kuby.kubyplugins.gui.SettingsScreen.Actions() {
            public Consumer<Player> tab(de.kuby.kubyplugins.service.PlayerSettings.Tab t) { return NO; }
            public Consumer<Player> click(String key) { return NO; }
            public Consumer<Player> language() { return NO; }
            public Consumer<Player> translate() { return NO; }
        };
        java.util.UUID nobody = new java.util.UUID(0, 1);
        for (String mode : List.of(de.kuby.kubyplugins.service.ModeService.SMP, de.kuby.kubyplugins.service.ModeService.SURVIVAL)) {
            for (de.kuby.kubyplugins.service.PlayerSettings.Tab t : de.kuby.kubyplugins.service.PlayerSettings.Tab.values()) {
                canvas.accept("Einstellungen " + mode + " " + t, () -> de.kuby.kubyplugins.gui.SettingsScreen.canvas(
                        plugin.settingsScreen().view(nobody, t, mode), sa));
            }
        }
        canvas.accept("Einstellungen englisch", () -> {
            de.kuby.kubyplugins.gui.SettingsScreen.View v = plugin.settingsScreen().view(nobody,
                    de.kuby.kubyplugins.service.PlayerSettings.Tab.DISPLAY, de.kuby.kubyplugins.service.ModeService.SMP);
            return de.kuby.kubyplugins.gui.SettingsScreen.canvas(new de.kuby.kubyplugins.gui.SettingsScreen.View(v.tab(), v.tiles(),
                    v.on(), v.total(), true, "English", true, true), sa);
        });
        canvas.accept("Sprache", () -> de.kuby.kubyplugins.gui.SettingsScreen.languageCanvas(List.of("Deutsch", "English"), 0, false,
                i -> NO, NO));

        // Juwelenshop (SMP): alle Seiten mit dem Inhalt aus der config.yml
        de.kuby.kubyplugins.gui.JewelShopScreen.Actions ja = new de.kuby.kubyplugins.gui.JewelShopScreen.Actions() {
            public Consumer<Player> page(de.kuby.kubyplugins.gui.JewelShopScreen.Page pg) { return NO; }
            public Consumer<Player> buy(String id) { return NO; }
            public Consumer<Player> amount(String id, int n) { return NO; }
            public Consumer<Player> gift() { return NO; }
            public String storeUrl() { return "https://example.org"; }
        };
        for (de.kuby.kubyplugins.gui.JewelShopScreen.Page pg : de.kuby.kubyplugins.gui.JewelShopScreen.Page.values()) {
            canvas.accept("Juwelenshop " + pg, () -> de.kuby.kubyplugins.gui.JewelShopScreen.canvas(plugin.jewelShop().sample(pg), ja));
        }

        // Battle-Pass: alle Reiter mit dem Inhalt aus der config.yml, dazu Belohnungen mit Premium und englisch
        de.kuby.kubyplugins.gui.BattlepassScreen.Actions ba = new de.kuby.kubyplugins.gui.BattlepassScreen.Actions() {
            public Consumer<Player> tab(de.kuby.kubyplugins.gui.BattlepassScreen.Tab t) { return NO; }
            public Consumer<Player> page(int page) { return NO; }
            public Consumer<Player> claim() { return NO; }
            public Consumer<Player> premium() { return NO; }
            public Consumer<Player> amount(int delta) { return NO; }
            public Consumer<Player> buy() { return NO; }
        };
        for (de.kuby.kubyplugins.gui.BattlepassScreen.Tab t : de.kuby.kubyplugins.gui.BattlepassScreen.Tab.values()) {
            canvas.accept("Battlepass " + t, () -> de.kuby.kubyplugins.gui.BattlepassScreen.canvas(plugin.battlepassScreen().sample(t), ba));
            // Skypass (Skyblock): gleiche Seiten im Himmel-Stil
            canvas.accept("Skypass " + t, () -> de.kuby.kubyplugins.gui.BattlepassScreen.canvas(plugin.battlepassScreen().sample(t,
                    de.kuby.kubyplugins.service.BattlepassService.SKYPASS), ba));
        }
        canvas.accept("Battlepass englisch", () -> {
            de.kuby.kubyplugins.gui.BattlepassScreen.View v = plugin.battlepassScreen().sample(de.kuby.kubyplugins.gui.BattlepassScreen.Tab.PREMIUM);
            return de.kuby.kubyplugins.gui.BattlepassScreen.canvas(new de.kuby.kubyplugins.gui.BattlepassScreen.View(v.look(), v.tab(), true,
                    v.season(), v.seasonLeft(), 100, 0, v.perLevel(), true, 14, v.pages(), List.of(), List.of(), 3, v.daily(),
                    v.weekly(), v.dailyLeft(), v.weeklyLeft(), v.premiumPrice(), v.freeCount(), v.premCount(), v.perks(),
                    12_345, 7, v.levelPrice(), 0, "levels"), ba);
        });

        // Guide (SMP): alle Seiten; jede muss in den Textkasten passen
        de.kuby.kubyplugins.gui.GuideScreen.Actions ga = new de.kuby.kubyplugins.gui.GuideScreen.Actions() {
            public Consumer<Player> page(int index) { return NO; }
            public String url(String target) { return "https://discord.gg/kuby"; }
        };
        for (String gm : new String[]{null, de.kuby.kubyplugins.service.ModeService.SKYBLOCK})
        for (boolean en : new boolean[]{false, true}) {
            List<de.kuby.kubyplugins.gui.GuideScreen.Page> guide = plugin.guide().pages(en, gm);
            if (guide.isEmpty()) fails.add("Guide: keine Seiten in guide.pages");
            String lang = (gm == null ? "" : " " + gm) + (en ? " (englisch)" : "");
            for (int i = 0; i < guide.size(); i++) {
                final int k = i;
                de.kuby.kubyplugins.gui.GuideScreen.Page pg = guide.get(i);
                int used = de.kuby.kubyplugins.gui.GuideScreen.usedLines(pg, plugin.guide().discordLink());
                if (used > de.kuby.kubyplugins.gui.GuideScreen.maxLines()) {
                    fails.add("Guide " + pg.id() + lang + ": " + used + " Zeilen, Platz für "
                            + de.kuby.kubyplugins.gui.GuideScreen.maxLines());
                }
                canvas.accept("Guide " + pg.id() + lang, () -> de.kuby.kubyplugins.gui.GuideScreen.canvas(guide, k, en,
                        plugin.guide().discordLink(), ga));
            }
        }

        // Sprache (12.7): Vorlagen geladen, Stichproben (Werte, Großschrift, Klick-Befehle bleiben unverändert)
        if (I18n.size() < 4000) fails.add("Sprache: nur " + I18n.size() + " englische Vorlagen");
        String[][] probes = {
                {"<green>Tag 5 abgeholt: <gold>+500$", "<green>Day 5 claimed: <gold>+500$"},
                {"Tagesaufgaben", "Daily quests"},
                {"SPIELZEIT", "PLAYTIME"},
                {"<click:run_command:'/job waehlen Verzauberer'><yellow>[Verzauberer werden]</click>",
                        "<click:run_command:'/job waehlen Verzauberer'><yellow>[Become Enchanter]</click>"}};
        for (String[] pr : probes) {
            String got = I18n.tr(pr[0]);
            if (!got.equals(pr[1])) fails.add("Sprache: \"" + pr[0] + "\" -> \"" + got + "\"");
        }

        // Eingabe-Dialoge wie in Support, Freunde, Casino, Heiraten, Köpfe (Bestätigung, CLOSE, Text und mehrzeilig)
        try {
            DialogBase base = DialogBase.builder(Msg.parse("<b>Test"))
                    .canCloseWithEscape(true)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(List.of(DialogBody.plainMessage(Msg.parse("Text"), 300)))
                    .inputs(List.of(DialogInput.text("titel", Msg.parse("Titel")).width(300).maxLength(40).initial("").build(),
                            DialogInput.text("frage", Msg.parse("Frage")).width(300).maxLength(500).initial("")
                                    .multiline(TextDialogInput.MultilineOptions.create(6, 90)).build(),
                            DialogInput.text("betrag", Msg.parse("Betrag")).width(160).maxLength(10).initial("").build()))
                    .build();
            ActionButton yes = ActionButton.create(Msg.parse("Ja"), null, 120, DialogAction.customClick((r, a) -> { }, net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).build()));
            ActionButton no = ActionButton.create(Msg.parse("Nein"), null, 100, DialogAction.customClick((r, a) -> { }, net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).build()));
            Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(yes, no)));
            ok[0]++;
        } catch (Throwable t) {
            fails.add("Eingabe-Dialog: " + t);
        }

        // Paragraphen-Zeichen in Chat-Texten (Regeln, Verwarnungen): darf MiniMessage nicht als alten Farbcode abbrechen
        try {
            Msg.parse("<green><b>§3 Spam</b> <click:run_command:'/regeln 4'>[Weiter zu §4]</click> §a §k");
            Msg.item("Spam im Chat (§3)");
            ok[0]++;
        } catch (Throwable t) {
            fails.add("Paragraphen-Zeichen: " + t);
        }

        // Beta-Feedback: jede Formular-Seite als echter Dialog (Umschalt-Felder, mehrzeilige Textfelder) und der Kopf
        {
            List<de.kuby.kubyplugins.beta.FeedbackQuestions.Page> fps = de.kuby.kubyplugins.beta.FeedbackQuestions.pages();
            java.util.Set<String> keys = new java.util.HashSet<>();
            for (int i = 0; i < fps.size(); i++) {
                de.kuby.kubyplugins.beta.FeedbackQuestions.Page fp = fps.get(i);
                for (de.kuby.kubyplugins.beta.FeedbackQuestions.Question q : fp.questions()) {
                    if (!keys.add(q.key())) fails.add("Beta-Feedback: Schlüssel doppelt: " + q.key());
                }
                int idx = i;
                canvas.accept("Beta-Feedback Kopf " + (i + 1), () -> de.kuby.kubyplugins.beta.FeedbackDialog.header(idx, fps.size(),
                        fp.title(), fp.sub(), 99, 99, idx % 2 == 0));
                try {
                    java.util.Map<String, String> filled = new java.util.HashMap<>();
                    for (de.kuby.kubyplugins.beta.FeedbackQuestions.Question q : fp.questions()) filled.put(q.key(), q.type()
                            == de.kuby.kubyplugins.beta.FeedbackQuestions.Type.STARS ? "6" : "W".repeat(1000));
                    for (java.util.Map<String, String> vals : List.of(java.util.Map.<String, String>of(), filled)) {
                        DialogBase base = DialogBase.builder(Msg.parse("<b>Beta-Feedback"))
                                .canCloseWithEscape(true)
                                .pause(false)
                                .afterAction(DialogBase.DialogAfterAction.NONE)
                                .body(List.of(DialogBody.plainMessage(Msg.parse("Kopf"), 454)))
                                .inputs(de.kuby.kubyplugins.beta.FeedbackDialog.inputs(fp, vals))
                                .build();
                        ActionButton b1 = ActionButton.create(Msg.parse("Weiter"), null, 124, DialogAction.customClick((r, a) -> { },
                                net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).build()));
                        Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(List.of(b1, b1, b1), null, 3)));
                    }
                    ok[0]++;
                } catch (Throwable t) {
                    fails.add("Beta-Feedback Seite " + (i + 1) + ": " + t);
                }
            }
        }

        // Spielzeit: alle Seiten mit den echten Stufen; jede Schlüssel-Belohnung braucht ihre Art und eine Kiste im Modus
        PlaytimeScreen pts = new PlaytimeScreen(plugin);
        for (String mode : List.of("survival", "smp")) {
            for (int pg = 0; pg < pts.pageCount(mode); pg++) {
                int page = pg;
                canvas.accept("Spielzeit " + mode + " Seite " + (pg + 1), () -> pts.preview(mode, 300, page));
            }
            List<String> bad = new ArrayList<>();
            for (de.kuby.kubyplugins.service.PlaytimeService.Stage st : plugin.playtime().stages(mode)) {
                for (de.kuby.kubyplugins.service.PlaytimeService.Prize pr : st.prizes()) {
                    if (!pr.kind().equals("key")) continue;
                    boolean crate = plugin.crates().crates(mode).stream().anyMatch(c -> c.key().equalsIgnoreCase(pr.value()));
                    if (plugin.keys().type(pr.value()) == null || !crate) bad.add(st.number() + ":" + pr.value());
                }
            }
            if (bad.isEmpty()) ok[0]++;
            else fails.add("Spielzeit " + mode + ": Schlüssel ohne Art oder Kiste " + bad);
        }

        // Regeln: Übersicht und alle Paragraphen aus regeln.yml
        de.kuby.kubyplugins.service.RulesBook.Book book = plugin.rules().book();
        de.kuby.kubyplugins.gui.RulesScreen.Actions ra = new de.kuby.kubyplugins.gui.RulesScreen.Actions() {
            public Consumer<Player> paragraph(int i) { return NO; }
            public Consumer<Player> overview() { return NO; }
        };
        if (book.paragraphs().isEmpty()) fails.add("Regeln: regeln.yml ohne Paragraphen");
        canvas.accept("Regeln", () -> de.kuby.kubyplugins.gui.RulesScreen.overview(book, ra));
        for (int i = 0; i < book.paragraphs().size(); i++) {
            int k = i;
            canvas.accept("Regeln §" + (i + 1), () -> de.kuby.kubyplugins.gui.RulesScreen.paragraph(book, k, ra));
        }

        // Support: alle Seiten leer und voll, Neues Ticket mit Feldern und zwei Knöpfen (Fläche hinter der Beschriftung)
        SupportScreen.Actions su = (SupportScreen.Actions) java.lang.reflect.Proxy.newProxyInstance(
                SupportScreen.Actions.class.getClassLoader(), new Class<?>[]{SupportScreen.Actions.class}, (o, m, args) -> NO);
        TextColor blue = TextColor.color(0x7BC4FC);
        List<SupportScreen.Item> items = List.of(new SupportScreen.Item(14, "Testfrage mit einem langen Titel bis vierzig",
                "neue Antwort", blue, "27.10. 14:03 · Helfer: Test · 3 Nachrichten", true),
                new SupportScreen.Item(9, "Kurz", "offen", blue, "26.10. 20:11 · noch kein Helfer · 1 Nachricht", false));
        for (boolean full : new boolean[]{false, true}) {
            String n = full ? " voll" : " leer";
            List<SupportScreen.Item> its = full ? items : List.of();
            canvas.accept("Support" + n, () -> SupportScreen.home(new SupportScreen.Home(full ? 2 : 0, 3, its, its.size(), full ? 1 : 0,
                    full, full ? 4 : 0, 50, "https://discord.gg/"), su));
            canvas.accept("Support Meine Tickets" + n, () -> SupportScreen.mine(full ? 2 : 0, 3, its, su));
            canvas.accept("Support Helfen" + n, () -> SupportScreen.queue(new SupportScreen.Queue(full ? "team" : "offen", full,
                    new int[]{2, 0, 1}, its), su));
            canvas.accept("Support Bestenliste" + n, () -> SupportScreen.board(new SupportScreen.Board(full, full
                    ? List.of(new SupportScreen.Place("Test", 12), new SupportScreen.Place("Test2", 1)) : List.of(),
                    "Du bist kein Ingame-Helfer."), su));
            canvas.accept("Support Helfer werden" + n, () -> SupportScreen.become(new SupportScreen.Become(full ? 4000 : 699, 50,
                    full, false), su));
        }
        canvas.accept("Support Helfer", () -> SupportScreen.become(new SupportScreen.Become(4000, 50, true, true), su));
        canvas.accept("Support Ticket", () -> SupportScreen.ticket(new SupportScreen.Detail(14, "Testfrage", "Test", "27.10. 14:03",
                "in Arbeit", blue, true, List.of(new SupportScreen.Line("Test", "spieler", "27.10. 14:03", "Wie geht das? ".repeat(30)),
                new SupportScreen.Line("Test2 kümmert sich um dein Ticket.", "system", "27.10. 14:05", ""),
                new SupportScreen.Line("Test2", "helfer", "27.10. 14:06", "So geht das.")), 0, true, true, true, true, true), su));
        canvas.accept("Support Bewerten", () -> SupportScreen.rate(14, "Test2", su));
        try {
            ActionButton send = ActionButton.create(UiDialog.plate(KitData.Sprite.SU_PLATE_PURPLE, KitData.Sprite.SU_PENCIL,
                    "Abschicken", SupportScreen.ON), null, 199, DialogAction.customClick((r, a) -> { },
                    net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).build()));
            ActionButton back = ActionButton.create(UiDialog.plate(KitData.Sprite.SU_PLATE_GRAY, null, "‹ Zurück",
                    SupportScreen.ON), null, 199, DialogAction.customClick((r, a) -> { },
                    net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).build()));
            UiDialog.dialog(null, SupportScreen.ask(su), List.of(
                    DialogInput.text("titel", Msg.parse("Titel")).width(400).maxLength(40).initial("").build(),
                    DialogInput.text("frage", Msg.parse("Frage")).width(400).maxLength(200).initial("")
                            .multiline(TextDialogInput.MultilineOptions.create(5, 52)).build()), List.of(send, back), 2);
            ok[0]++;
        } catch (Throwable t) {
            fails.add("Support Neues Ticket: " + t);
        }

        // Info-Leiste: alle Designs, an und aus, eigene Reihenfolge
        de.kuby.kubyplugins.gui.InfoBarScreen.Actions ia = (de.kuby.kubyplugins.gui.InfoBarScreen.Actions)
                java.lang.reflect.Proxy.newProxyInstance(de.kuby.kubyplugins.gui.InfoBarScreen.Actions.class.getClassLoader(),
                        new Class<?>[]{de.kuby.kubyplugins.gui.InfoBarScreen.Actions.class}, (o, m, args) -> NO);
        for (de.kuby.kubyplugins.service.InfoBar.Design d : de.kuby.kubyplugins.service.InfoBar.Design.values()) {
            boolean on = d.ordinal() % 2 == 0;
            List<de.kuby.kubyplugins.service.InfoBar.Element> order = new ArrayList<>(de.kuby.kubyplugins.service.InfoBar.Element.group(d.sky));
            java.util.Collections.rotate(order, d.ordinal());
            List<Boolean> vis = new ArrayList<>();
            for (int i = 0; i < order.size(); i++) vis.add(i % 3 != 0);
            // Skyblock: mit großer Insel (Level, Platz) und ohne Insel
            de.kuby.kubyplugins.service.InfoBar.IslandLine isl = on
                    ? new de.kuby.kubyplugins.service.InfoBar.IslandLine(true, 12_345, 87, 104, 12, 12, "x2 59:59")
                    : de.kuby.kubyplugins.service.InfoBar.IslandLine.NONE;
            canvas.accept((d.sky ? "Insel-Leiste " : "Info-Leiste ") + d.label, () -> de.kuby.kubyplugins.gui.InfoBarScreen.canvas(
                    new de.kuby.kubyplugins.gui.InfoBarScreen.View(on, d, order, vis, new de.kuby.kubyplugins.gui.InfoBarScreen.Sample(
                            "1.234.567", "320", on ? new de.kuby.kubyplugins.service.InfoBar.JobLine("Holzfäller", 12, 45, 1200, 3060)
                            : de.kuby.kubyplugins.service.InfoBar.JobLine.NONE, isl)), ia));
        }

        // Modus-Wahl (Kompass): alle Modi, einer aktiv, viele Spieler, lange Zeilen
        canvas.accept("Modus-Wahl", () -> {
            java.util.function.Consumer<org.bukkit.entity.Player> nop = pl -> { };
            List<de.kuby.kubyplugins.gui.ModeSelectScreen.Card> cards = new ArrayList<>();
            String[] names = {"SMP", "Survival", "Skyblock"};
            org.bukkit.Material[] icons = {org.bukkit.Material.IRON_SWORD, org.bukkit.Material.GRASS_BLOCK, org.bukkit.Material.OAK_SAPLING};
            KitData.Box[] boxes = {KitData.Box.RED, KitData.Box.GREEN, KitData.Box.TEAL};
            for (int i = 0; i < 3; i++) {
                cards.add(new de.kuby.kubyplugins.gui.ModeSelectScreen.Card(names[i], icons[i], boxes[i],
                        net.kyori.adventure.text.format.TextColor.color(0xFF6A5A), List.of("W".repeat(30), "Jobs, Angeln, Farmen", "x"),
                        i * 999, i == 1, nop));
            }
            return de.kuby.kubyplugins.gui.ModeSelectScreen.canvas(new de.kuby.kubyplugins.gui.ModeSelectScreen.View(cards, 1998));
        });

        // Hauptmenü: lange Namen, große Zahlen, volle Bereiche
        {
            List<de.kuby.kubyplugins.gui.MainScreen.Tile> tiles = new ArrayList<>();
            for (int i = 0; i < 13; i++) {
                tiles.add(new de.kuby.kubyplugins.gui.MainScreen.Tile("WWWWWWWWWWWWWWWW", "W".repeat(80),
                        de.kuby.kubyplugins.ui.KitData.Icon.values()[i * 7], NO));
            }
            List<de.kuby.kubyplugins.gui.MainScreen.Quick> qs = List.of(
                    new de.kuby.kubyplugins.gui.MainScreen.Quick("WWWWWWWWWWWW", de.kuby.kubyplugins.ui.KitData.Icon.COMPASS,
                            de.kuby.kubyplugins.ui.KitData.Button.TEAL, NO),
                    new de.kuby.kubyplugins.gui.MainScreen.Quick("Einstellungen", de.kuby.kubyplugins.ui.KitData.Icon.COMPARATOR,
                            de.kuby.kubyplugins.ui.KitData.Button.GOLD_LINE, NO));
            List<de.kuby.kubyplugins.gui.MainScreen.Section> secs = List.of(
                    new de.kuby.kubyplugins.gui.MainScreen.Section("WWWWWWWWWWWW", net.kyori.adventure.text.format.TextColor.color(0x7FE08A),
                            de.kuby.kubyplugins.ui.KitData.Box.GREEN, tiles),
                    new de.kuby.kubyplugins.gui.MainScreen.Section("Leer", net.kyori.adventure.text.format.TextColor.color(0x64C0FF),
                            de.kuby.kubyplugins.ui.KitData.Box.NAVY, List.of()));
            canvas.accept("Hauptmenü", () -> de.kuby.kubyplugins.gui.MainScreen.canvas(new de.kuby.kubyplugins.gui.MainScreen.View(
                    "_WWWWWWWWWWWWWWW", "Skyblock", 999_999_999_999L, 999_999_999L, qs, secs, qs)));
        }

        // Homes: volle Liste mit langen Namen, Kauf-Knopf; leer ohne Platz
        {
            List<de.kuby.kubyplugins.gui.HomesScreen.Home> hs = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                hs.add(new de.kuby.kubyplugins.gui.HomesScreen.Home("WWWWWWWWWWWWWWWW", "WWWWWWWWWWWW", -29_999_999, 320, 29_999_999,
                        i == 1, NO, NO));
            }
            canvas.accept("Homes voll", () -> de.kuby.kubyplugins.gui.HomesScreen.canvas(new de.kuby.kubyplugins.gui.HomesScreen.View(
                    hs, 30, NO, "Platz kaufen · 999.999.999 XP", NO, List.of("W".repeat(100), "W".repeat(100)))));
            canvas.accept("Homes leer", () -> de.kuby.kubyplugins.gui.HomesScreen.canvas(new de.kuby.kubyplugins.gui.HomesScreen.View(
                    List.of(), 0, NO, null, null, List.of())));
        }

        // Tageskalender, Tagespot, Bestenlisten, Erfolge, Titel, Kits: Grenzwerte und leer
        {
            long[] rw = new long[30];
            java.util.Arrays.fill(rw, 999_999_999L);
            canvas.accept("Tageskalender", () -> de.kuby.kubyplugins.gui.DailyScreens.calendar(
                    new de.kuby.kubyplugins.gui.DailyScreens.Calendar(29, true, rw, NO)));
            canvas.accept("Tageskalender fertig", () -> de.kuby.kubyplugins.gui.DailyScreens.calendar(
                    new de.kuby.kubyplugins.gui.DailyScreens.Calendar(30, false, rw, NO)));
            canvas.accept("Tagespot", () -> de.kuby.kubyplugins.gui.DailyScreens.pot(new de.kuby.kubyplugins.gui.DailyScreens.Pot(
                    999_999_999_999L, 50, 9_999_999L, 999_999L, 50, 23, 1439, n -> NO)));
            List<de.kuby.kubyplugins.gui.TopListScreen.Entry> top = new ArrayList<>();
            for (int i = 1; i <= 10; i++) top.add(new de.kuby.kubyplugins.gui.TopListScreen.Entry(i, "_WWWWWWWWWWWWWWW", "999.999.999.999$", "WWWWWWWWWWWW · 99999/99999"));
            canvas.accept("Bestenliste", () -> de.kuby.kubyplugins.gui.TopListScreen.canvas(new de.kuby.kubyplugins.gui.TopListScreen.View(
                    "WWWWWWWWWWWW", "W".repeat(60), de.kuby.kubyplugins.ui.KitData.Icon.STAR, net.kyori.adventure.text.format.TextColor.color(0xF8D35A),
                    top, "W".repeat(80))));
            canvas.accept("Bestenliste leer", () -> de.kuby.kubyplugins.gui.TopListScreen.canvas(new de.kuby.kubyplugins.gui.TopListScreen.View(
                    "Vermögen", "", de.kuby.kubyplugins.ui.KitData.Icon.GOLD_INGOT, net.kyori.adventure.text.format.TextColor.color(0xF8D35A),
                    List.of(), null)));
            List<de.kuby.kubyplugins.gui.ProgressScreens.Ach> ach = new ArrayList<>();
            for (int i = 0; i < 10; i++) ach.add(new de.kuby.kubyplugins.gui.ProgressScreens.Ach("W".repeat(30), "W".repeat(60),
                    de.kuby.kubyplugins.ui.KitData.Icon.STAR, 999_999_999L, 999_999_999L, 999_999L, i % 2 == 0));
            canvas.accept("Erfolge", () -> de.kuby.kubyplugins.gui.ProgressScreens.achievements(
                    new de.kuby.kubyplugins.gui.ProgressScreens.Achievements(9999, 9999, ach, 98, 99, n -> NO)));
            List<de.kuby.kubyplugins.gui.ProgressScreens.Tag> tg = new ArrayList<>();
            for (int i = 0; i < 30; i++) tg.add(new de.kuby.kubyplugins.gui.ProgressScreens.Tag("W".repeat(30),
                    net.kyori.adventure.text.format.TextColor.color(0xFF55FF), i % 3 != 0, i == 1, i % 3 != 0 ? NO : null));
            canvas.accept("Titel", () -> de.kuby.kubyplugins.gui.ProgressScreens.tags(new de.kuby.kubyplugins.gui.ProgressScreens.Tags("W".repeat(40), tg, NO)));
            canvas.accept("Titel leer", () -> de.kuby.kubyplugins.gui.ProgressScreens.tags(new de.kuby.kubyplugins.gui.ProgressScreens.Tags(null, List.of(), NO)));
            List<de.kuby.kubyplugins.gui.ProgressScreens.KitRow> kr = new ArrayList<>();
            for (int i = 0; i < 8; i++) kr.add(new de.kuby.kubyplugins.gui.ProgressScreens.KitRow("W".repeat(30), de.kuby.kubyplugins.ui.KitData.Icon.IRON_CHESTPLATE,
                    "W".repeat(80), "9999 Std.", "WWWWWWWWWWWW", i % 2 == 0, "W".repeat(40), i % 2 == 0, NO, NO));
            canvas.accept("Kits", () -> de.kuby.kubyplugins.gui.ProgressScreens.kits(kr));
            canvas.accept("Kits leer", () -> de.kuby.kubyplugins.gui.ProgressScreens.kits(List.of()));
            canvas.accept("Kits Admin", () -> de.kuby.kubyplugins.gui.ProgressScreens.kits(kr, pl -> { }));
        }

        // Team, XP-Shop, Investments, Duo-RTP, Quiz: Grenzwerte und leer
        {
            List<de.kuby.kubyplugins.gui.TeamScreen.Member> ms = new ArrayList<>();
            for (int i = 0; i < 20; i++) ms.add(new de.kuby.kubyplugins.gui.TeamScreen.Member("_WWWWWWWWWWWWWWW", i == 0, i % 2 == 0));
            canvas.accept("Team", () -> de.kuby.kubyplugins.gui.TeamScreen.canvas(new de.kuby.kubyplugins.gui.TeamScreen.View(
                    "WWWWWWWWWWWWWWWW", 999, 0.5, ms, 999, 999_999_999_999L, 6, 99, 99, true, 999_999_999L, true,
                    NO, NO, NO, NO, NO, NO, NO)));
            canvas.accept("Team keins", () -> de.kuby.kubyplugins.gui.TeamScreen.canvas(new de.kuby.kubyplugins.gui.TeamScreen.View(
                    null, 0, 0, List.of(), 0, 0, 0, 0, 0, false, 999_999_999L, false, null, null, null, null, null, null, null)));
            // XP-Shop (v2.05): Übersicht und jede Kategorie je Modus, englisch, leer
            de.kuby.kubyplugins.gui.XpShopScreen.Actions xa = new de.kuby.kubyplugins.gui.XpShopScreen.Actions() {
                public Consumer<Player> home() { return NO; }
                public Consumer<Player> category(String id) { return NO; }
                public Consumer<Player> page(int page) { return NO; }
                public Consumer<Player> buy(String id) { return NO; }
                public Consumer<Player> amount(String id, int amount) { return NO; }
            };
            for (String xm : new String[]{de.kuby.kubyplugins.service.ModeService.SMP, de.kuby.kubyplugins.service.ModeService.SKYBLOCK}) {
                canvas.accept("XP-Shop " + xm, () -> de.kuby.kubyplugins.gui.XpShopScreen.canvas(
                        de.kuby.kubyplugins.gui.XpShopScreen.sample(plugin.getConfig(), xm, null, 999_999_999L, false), xa));
                for (var xc : de.kuby.kubyplugins.gui.XpShopScreen.categories(plugin.getConfig(), xm)) {
                    canvas.accept("XP-Shop " + xm + " " + xc.id(), () -> de.kuby.kubyplugins.gui.XpShopScreen.canvas(
                            de.kuby.kubyplugins.gui.XpShopScreen.sample(plugin.getConfig(), xm, xc.id(), 5_000, false), xa));
                    canvas.accept("XP-Shop " + xm + " " + xc.id() + " englisch", () -> de.kuby.kubyplugins.gui.XpShopScreen.canvas(
                            de.kuby.kubyplugins.gui.XpShopScreen.sample(plugin.getConfig(), xm, xc.id(), 999_999_999L, true), xa));
                }
            }
            canvas.accept("XP-Shop leer", () -> de.kuby.kubyplugins.gui.XpShopScreen.canvas(
                    de.kuby.kubyplugins.gui.XpShopScreen.sample(plugin.getConfig(), "keinmodus", null, 0, false), xa));
            List<de.kuby.kubyplugins.gui.ExtraScreens.Stock> st = new ArrayList<>();
            for (int i = 0; i < 20; i++) st.add(new de.kuby.kubyplugins.gui.ExtraScreens.Stock(org.bukkit.Material.DIAMOND,
                    "W".repeat(30), i % 3 != 0, 999_999_999L, 9999.99, 99999.99, 999_999_999L, i % 2 == 0 ? 999_999_999L : -999_999_999L, NO, NO, NO));
            canvas.accept("Investments", () -> de.kuby.kubyplugins.gui.ExtraScreens.investments(
                    new de.kuby.kubyplugins.gui.ExtraScreens.Portfolio(999_999_999_999L, 1, 1_000_000, st)));
            canvas.accept("Investments leer", () -> de.kuby.kubyplugins.gui.ExtraScreens.investments(
                    new de.kuby.kubyplugins.gui.ExtraScreens.Portfolio(0, 0, 1000, List.of())));
            canvas.accept("Duo-RTP", () -> de.kuby.kubyplugins.gui.ExtraScreens.rtp(false, NO, NO));
            List<de.kuby.kubyplugins.gui.ExtraScreens.Category> qc = new ArrayList<>();
            for (int i = 0; i < 7; i++) qc.add(new de.kuby.kubyplugins.gui.ExtraScreens.Category("W".repeat(20),
                    de.kuby.kubyplugins.ui.KitData.Icon.BOOK, 99_999, i == 0, NO));
            canvas.accept("Quiz-Abstimmung", () -> de.kuby.kubyplugins.gui.ExtraScreens.quiz(
                    new de.kuby.kubyplugins.gui.ExtraScreens.Quiz("W".repeat(30), 99_999, qc)));
        }

        // Cutscene „Das Schwert im Stein“: jedes Bild berechnen, Kamera und Teile müssen endliche Werte haben
        try {
            String err = cutsceneCheck(new de.kuby.kubyplugins.cinema.scenes.SwordInStone(new de.kuby.kubyplugins.cinema.V3(0.5, 64, 0.5),
                    new de.kuby.kubyplugins.cinema.V3(0.4, 0, -1), 11, 42L, 30f));
            if (err != null) fails.add("Cutscene Schwert: " + err);
            else ok[0]++;
        } catch (Throwable t) {
            fails.add("Cutscene Schwert: " + t);
        }
        for (int tier = 0; tier < 4; tier++) {
            try {
                String err = cutsceneCheck(new de.kuby.kubyplugins.cinema.scenes.CrateReveal(new de.kuby.kubyplugins.cinema.V3(0.5, 64, 0.5),
                        new de.kuby.kubyplugins.cinema.V3(0.5, 64, 0.5), tier, "minecraft:diamond", "W".repeat(22), "0,1 %", 7L));
                if (err != null) fails.add("Cutscene Kiste " + tier + ": " + err);
                else ok[0]++;
            } catch (Throwable t) {
                fails.add("Cutscene Kiste " + tier + ": " + t);
            }
        }

        // Prüfungen aus den Addons (z. B. Survival-Juwelenshop, Möbel- und Pet-Katalog)
        for (java.util.Map.Entry<String, Supplier<Canvas>> en : plugin.selfTestCanvases().entrySet()) {
            canvas.accept(en.getKey(), en.getValue());
        }
        for (java.util.Map.Entry<String, Supplier<String>> en : plugin.selfTestChecks().entrySet()) {
            try {
                String err = en.getValue().get();
                if (err != null) fails.add(en.getKey() + ": " + err);
                else ok[0]++;
            } catch (Throwable t) {
                fails.add(en.getKey() + ": " + t);
            }
        }

        // Währungen und Pässe je Modus (v2.05): XP nur SMP/Skyblock, Tokens nur Survival; Battlepass SMP, Skypass Skyblock
        try {
            String smp = de.kuby.kubyplugins.service.ModeService.SMP, sky = de.kuby.kubyplugins.service.ModeService.SKYBLOCK,
                    surv = de.kuby.kubyplugins.service.ModeService.SURVIVAL;
            if (!de.kuby.kubyplugins.service.ModeService.hasCurrency(smp, "xp") || !de.kuby.kubyplugins.service.ModeService.hasCurrency(sky, "xp")
                    || de.kuby.kubyplugins.service.ModeService.hasCurrency(surv, "xp")) fails.add("Währung: XP nicht nur in SMP und Skyblock");
            if (!de.kuby.kubyplugins.service.ModeService.hasCurrency(surv, "tokens") || de.kuby.kubyplugins.service.ModeService.hasCurrency(smp, "tokens")
                    || de.kuby.kubyplugins.service.ModeService.hasCurrency(sky, "tokens")) fails.add("Währung: Tokens nicht nur in Survival");
            if (!de.kuby.kubyplugins.service.BattlepassService.enabled(smp) || !de.kuby.kubyplugins.service.BattlepassService.enabled(sky)
                    || de.kuby.kubyplugins.service.BattlepassService.enabled(surv)) fails.add("Pässe: Battlepass/Skypass falsch freigegeben");
            if (!"skypass".equals(de.kuby.kubyplugins.service.BattlepassService.passOf(sky))
                    || !"battlepass".equals(de.kuby.kubyplugins.service.BattlepassService.passOf(smp))) fails.add("Pässe: falscher Pass je Modus");
            var bpq = plugin.battlepass().pool(de.kuby.kubyplugins.service.BattlepassService.Period.DAILY, "battlepass");
            var spq = plugin.battlepass().pool(de.kuby.kubyplugins.service.BattlepassService.Period.DAILY, "skypass");
            if (spq.isEmpty() || spq.stream().map(q -> q.id()).toList().equals(bpq.stream().map(q -> q.id()).toList()))
                fails.add("Skypass: keine eigenen Tagesaufgaben");
            if (plugin.battlepass().rewardLevels(de.kuby.kubyplugins.service.BattlepassService.PREMIUM, "skypass") < 50)
                fails.add("Skypass: zu wenige Premium-Belohnungen");
        } catch (Throwable t) {
            fails.add("Währungen/Pässe: " + t);
        }

        // XP-Shop (v2.05): jedes Angebot gültig (Items, Verzauberungen, Schlüssel, Booster), SMP mit PvP/Bauen/Team/Survival,
        // Skyblock mit Insel, beide mit 3x3- und 5x5-Spitzhacke
        try {
            for (String xm : new String[]{de.kuby.kubyplugins.service.ModeService.SMP, de.kuby.kubyplugins.service.ModeService.SKYBLOCK}) {
                var cats = de.kuby.kubyplugins.gui.XpShopScreen.categories(plugin.getConfig(), xm);
                java.util.Set<String> ids = new java.util.HashSet<>();
                for (var cat : cats) ids.add(cat.id());
                for (String need : new String[]{"geld", "juwelen", "keys", "booster", "optools"}) {
                    if (!ids.contains(need)) fails.add("XP-Shop " + xm + ": Kategorie " + need + " fehlt");
                }
                for (String need : xm.equals(de.kuby.kubyplugins.service.ModeService.SMP)
                        ? new String[]{"pvp", "bauen", "team", "survival"} : new String[]{"insel"}) {
                    if (!ids.contains(need)) fails.add("XP-Shop " + xm + ": Kategorie " + need + " fehlt");
                }
                java.util.Set<String> seen = new java.util.HashSet<>();
                int area3 = 0, area5 = 0;
                for (var o : de.kuby.kubyplugins.gui.XpShopScreen.allOffers(plugin.getConfig(), xm)) {
                    if (!seen.add(o.id())) fails.add("XP-Shop " + xm + ": doppelte ID " + o.id());
                    String err = de.kuby.kubyplugins.gui.XpShopScreen.check(plugin, o);
                    if (err != null) fails.add("XP-Shop " + xm + " " + o.id() + ": " + err);
                    if ("ITEM".equals(o.kind()) && err == null) {
                        for (var it : de.kuby.kubyplugins.gui.XpShopScreen.items(o)) {
                            int ar = de.kuby.kubyplugins.listener.AreaToolListener.area(it);
                            if (ar == 3) area3++;
                            if (ar == 5) area5++;
                        }
                    }
                }
                if (area3 == 0 || area5 == 0) fails.add("XP-Shop " + xm + ": 3x3- oder 5x5-Spitzhacke fehlt");
            }
            // Fläche: 5x5 = 24 weitere Blöcke, 3x3 = 8, je Ebene
            var w = org.bukkit.Bukkit.getWorlds().get(0);
            var b = w.getBlockAt(0, 200, 0);
            for (org.bukkit.block.BlockFace f : new org.bukkit.block.BlockFace[]{org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.EAST}) {
                var pl3 = de.kuby.kubyplugins.listener.AreaToolListener.plane(b, f, 3);
                var pl5 = de.kuby.kubyplugins.listener.AreaToolListener.plane(b, f, 5);
                if (pl3.size() != 8 || pl5.size() != 24) fails.add("Flächen-Werkzeug " + f + ": " + pl3.size() + "/" + pl5.size() + " statt 8/24");
                for (var x : pl5) {
                    int dx = x.getX() - b.getX(), dy = x.getY() - b.getY(), dz = x.getZ() - b.getZ();
                    boolean inPlane = switch (f) {
                        case UP -> dy == 0;
                        case EAST -> dx == 0;
                        default -> dz == 0;
                    };
                    if (!inPlane || Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) > 2) {
                        fails.add("Flächen-Werkzeug " + f + ": Block außerhalb der Ebene");
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            fails.add("XP-Shop: " + t);
        }

        // Casino: Juwelen nur in den freigegebenen Spielen, sonst immer Chips (auch wenn der Spieler Juwelen gewählt hat)
        try {
            var cp = plugin.casinoPlay();
            if (cp != null) {
                if (!cp.jewelGame("blackjack")) fails.add("Casino: Blackjack muss Juwelen erlauben");
                for (String g : new String[]{"roulette", "slots", "crash", "plinko", "rennen", "losbude"}) {
                    if (cp.jewelGame(g)) fails.add("Casino: " + g + " darf keine Juwelen erlauben");
                    if (cp.cur(java.util.UUID.randomUUID(), g) != de.kuby.kubyplugins.service.CasinoPlay.Cur.CHIPS)
                        fails.add("Casino: " + g + " spielt nicht mit Chips");
                }
            }
        } catch (Throwable t) {
            fails.add("Casino-Währung: " + t);
        }

        // High-Roller (weinrot): Serie +0,5x bis x8, normal bis x2,5; Juwelen auch am High-Roller-Blackjack
        try {
            var cp = plugin.casinoPlay();
            if (cp != null) {
                if (Math.abs(cp.streakMult("blackjack_hr", 14) - 8.0) > 1e-9) fails.add("High-Roller-Serie nach 14 Siegen: " + cp.streakMult("blackjack_hr", 14));
                if (cp.streakMult("blackjack_hr", 30) > 8.0) fails.add("High-Roller-Serie über x8");
                if (Math.abs(cp.streakMult("blackjack", 30) - 2.5) > 1e-9) fails.add("normale Serie nicht bei x2,5: " + cp.streakMult("blackjack", 30));
                if (!cp.jewelGame("blackjack_hr")) fails.add("High-Roller-Blackjack erlaubt keine Juwelen");
            }
            if (plugin.pokerHr() == null || !plugin.pokerHr().isHighRoller()) fails.add("High-Roller-Pokertisch fehlt");
            if (plugin.pokerVio() == null || plugin.pokerVio().tier() != de.kuby.kubyplugins.casino.TableTier.VIOLETT) fails.add("Violetter Pokertisch fehlt");
            // Tisch-Stufen: Pflicht- und Höchsteinsatz wie vorgegeben (grün 10/100k, rot 10k/500k, violett 50k/1 Mio.)
            long[][] want = {{10, 100_000}, {10_000, 500_000}, {50_000, 1_000_000}};
            for (var t : de.kuby.kubyplugins.casino.TableTier.values()) {
                long[] w = want[t.ordinal()];
                if (t.minBet(plugin) != w[0] || t.maxBet(plugin) != w[1])
                    fails.add("Tisch-Stufe " + t.key + ": " + t.minBet(plugin) + "–" + t.maxBet(plugin) + " statt " + w[0] + "–" + w[1]);
                if (!cp.jewelGame("blackjack" + t.suffix)) fails.add("Juwelen am Blackjack-Tisch " + t.key + " nicht erlaubt");
            }
            if (Math.abs(cp.streakMult("blackjack_violett_hr", 14) - 8.0) > 1e-9) fails.add("Violette Serie nicht bis x8");
        } catch (Throwable t) {
            fails.add("High-Roller: " + t);
        }

        // Roulette: jede Wettart hat Hausvorteil, auch Zahlen mit Blitz-Bonus (1 bis 3 Zahlen aus 0..36, x2 bis x5)
        try {
            double blitzAvg = (2 + 2 + 3 + 3 + 4 + 5) / 6.0, blitzCount = 2.0;
            for (var k : de.kuby.kubyplugins.casino.RouletteGame.Kind.values()) {
                int hits = 0;
                for (int n = 0; n <= 36; n++) {
                    if (new de.kuby.kubyplugins.casino.RouletteGame.Bet(null, "", k, k == de.kuby.kubyplugins.casino.RouletteGame.Kind.ZAHL ? 7
                            : k == de.kuby.kubyplugins.casino.RouletteGame.Kind.DUTZEND || k == de.kuby.kubyplugins.casino.RouletteGame.Kind.KOLONNE ? 1 : 0, 1).wins(n)) hits++;
                }
                double ev = hits / 37.0 * k.multiplier;
                if (k == de.kuby.kubyplugins.casino.RouletteGame.Kind.ZAHL) ev *= 1 + blitzCount / 37.0 * (blitzAvg - 1);
                if (ev >= 1.0) fails.add("Roulette " + k.label + ": Spieler im Vorteil (Erwartung " + String.format("%.3f", ev) + ")");
            }
        } catch (Throwable t) {
            fails.add("Roulette-Quoten: " + t);
        }

        // OP-Werkzeuge: jedes lässt sich bauen und trägt seine Art; jedes Shop-Angebot mit Werkzeug kennt es
        try {
            for (String k : de.kuby.kubyplugins.tools.SpecialTools.all().keySet()) {
                org.bukkit.inventory.ItemStack it = plugin.tools().create(k, 1);
                if (it == null || !k.equals(plugin.tools().kind(it))) fails.add("Werkzeug " + k + ": falsch gebaut");
                else ok[0]++;
            }
            for (String sec : de.kuby.kubyplugins.service.ShopService.SECTIONS) {
                for (de.kuby.kubyplugins.service.ShopService.Item it : plugin.shop().items(sec)) {
                    if (it.special() != null && de.kuby.kubyplugins.tools.SpecialTools.def(it.special()) == null) {
                        fails.add("Shop " + sec + ": unbekanntes Werkzeug " + it.special());
                    }
                }
            }
        } catch (Throwable t) {
            fails.add("Werkzeuge: " + t);
        }

        // Modus-Welt: Spawn in einer anders benannten Welt zählt als Survival-Welt (Claims, Läden, Warps)
        try {
            String keyW = "modes.survival.world", keyS = "modes.survival.spawn";
            Object oldW = plugin.getConfig().get(keyW), oldS = plugin.getConfig().get(keyS);
            org.bukkit.World w0 = org.bukkit.Bukkit.getWorlds().get(0);
            try {
                plugin.getConfig().set(keyW, "gibt_es_nicht_" + w0.getName());
                plugin.getConfig().set(keyS, w0.getName() + ";0.5;64;0.5;0;0");
                if (!plugin.modes().isWorldOf(de.kuby.kubyplugins.service.ModeService.SURVIVAL, w0)) {
                    fails.add("Survival-Welt: Spawn-Welt " + w0.getName() + " wird nicht erkannt (Claims gingen dort nicht)");
                } else ok[0]++;
                plugin.getConfig().set(keyS, null);
                if (plugin.modes().isWorldOf(de.kuby.kubyplugins.service.ModeService.SURVIVAL, w0)) {
                    fails.add("Survival-Welt: fremde Welt " + w0.getName() + " zählt als Survival-Welt");
                } else ok[0]++;
            } finally {
                plugin.getConfig().set(keyW, oldW);
                plugin.getConfig().set(keyS, oldS);
            }
        } catch (Throwable t) {
            fails.add("Modus-Welt: " + t);
        }

        // Poker-Regeln (v2.02): Handbewertung mit allen Blattklassen, Kicker, A-2-3-4-5, 7 Karten, geteilter Pot
        try {
            List<String> pk = de.kuby.kubyplugins.service.PokerService.selfCheck();
            if (pk.isEmpty()) ok[0]++;
            for (String e : pk) fails.add("Poker: " + e);
        } catch (Throwable t) {
            fails.add("Poker-Regeln: " + t);
        }

        // Roulette-Physik (v2.04): 400 Würfe, jede Kugel im richtigen Fach, im Kessel, ohne Sprünge, Sprungdauer 0–5 s
        try {
            List<String> rl = de.kuby.kubyplugins.casino.RouletteTable.selfCheck();
            if (rl.isEmpty()) ok[0]++;
            for (String e : rl) fails.add("Roulette-Physik: " + e);
            plugin.getLogger().info("[Roulette-Physik] " + (rl.isEmpty() ? "ok, " : rl.size() + " Fehler, ")
                    + de.kuby.kubyplugins.casino.RouletteTable.lastStats);
        } catch (Throwable t) {
            fails.add("Roulette-Physik: " + t);
        }

        // Pferderennen (v2.05): Quoten höchstens 95 % je Wette, Platz-Chancen stimmen, 300 Rennen wie geplant
        try {
            List<String> hr = de.kuby.kubyplugins.casino.HorseRace.selfCheck();
            if (hr.isEmpty()) ok[0]++;
            for (String e : hr) fails.add("Pferderennen: " + e);
            plugin.getLogger().info("[Pferderennen] " + (hr.isEmpty() ? "ok, " : hr.size() + " Fehler, ") + de.kuby.kubyplugins.casino.HorseRace.lastStats);
        } catch (Throwable t) {
            fails.add("Pferderennen: " + t);
        }

        // Plinko (v2.05): drei Risikostufen mit 95–97 % Rückfluss, 600 Bahnen im richtigen Fach, nie durch einen Stift
        try {
            List<String> pl = de.kuby.kubyplugins.casino.Plinko.selfCheck();
            if (pl.isEmpty()) ok[0]++;
            for (String e : pl) fails.add("Plinko: " + e);
            plugin.getLogger().info("[Plinko] " + (pl.isEmpty() ? "ok, " : pl.size() + " Fehler, ") + de.kuby.kubyplugins.casino.Plinko.lastStats);
        } catch (Throwable t) {
            fails.add("Plinko: " + t);
        }

        // Slot-Themen (v2.05): je Automat eigene Symbole und Gewinne, jede Tabelle mit 93–97 % Rückfluss
        try {
            List<String> st = de.kuby.kubyplugins.casino.SlotTheme.selfCheck();
            if (st.isEmpty()) ok[0]++;
            for (String e : st) fails.add("Slot-Themen: " + e);
            plugin.getLogger().info("[Slot-Themen] " + (st.isEmpty() ? "ok, " : st.size() + " Fehler, ")
                    + de.kuby.kubyplugins.casino.SlotTheme.stats());
        } catch (Throwable t) {
            fails.add("Slot-Themen: " + t);
        }

        // Aufstehen von Sitzen (v2.02): Füße auf der Bodenoberkante, nie im Block darunter, Wand vor dem Sitz umgehen
        try {
            org.bukkit.World w0 = org.bukkit.Bukkit.getWorlds().get(0);
            org.bukkit.Location sp = w0.getSpawnLocation();
            int x0 = sp.getBlockX(), z0 = sp.getBlockZ(), y0 = w0.getMaxHeight() - 12;
            java.util.Map<org.bukkit.block.Block, org.bukkit.block.data.BlockData> keep = new java.util.LinkedHashMap<>();
            java.util.function.BiConsumer<org.bukkit.block.Block, org.bukkit.Material> put = (b, m) -> {
                keep.putIfAbsent(b, b.getBlockData());
                b.setType(m, false);
            };
            try {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        put.accept(w0.getBlockAt(x0 + dx, y0, z0 + dz), org.bukkit.Material.STONE);
                        for (int dy = 1; dy <= 3; dy++) put.accept(w0.getBlockAt(x0 + dx, y0 + dy, z0 + dz), org.bukkit.Material.AIR);
                    }
                }
                org.bukkit.Location seat = new org.bukkit.Location(w0, x0 + 0.5, y0 + 1, z0 + 0.5);
                // /sit: Ständer 0,1 unter den Füßen -> Füße wieder genau auf den Stein
                org.bukkit.Location r1 = de.kuby.kubyplugins.util.SafeStand.find(seat, null);
                if (r1 == null || Math.abs(r1.getY() - (y0 + 1)) > 1e-6 || r1.distanceSquared(seat) > 1e-6) {
                    fails.add("Aufstehen: /sit landet bei " + r1 + " statt auf dem Boden");
                } else ok[0]++;
                // Teppich: auf den Teppich, nicht hinein
                put.accept(w0.getBlockAt(x0, y0 + 1, z0), org.bukkit.Material.RED_CARPET);
                org.bukkit.Location r2 = de.kuby.kubyplugins.util.SafeStand.find(seat, null);
                if (r2 == null || Math.abs(r2.getY() - (y0 + 1.0625)) > 1e-6) fails.add("Aufstehen: Teppich -> " + r2);
                else ok[0]++;
                // Wand vor dem Möbel (+X): nicht in die Wand, sondern daneben auf den Boden
                for (int dy = 1; dy <= 2; dy++) put.accept(w0.getBlockAt(x0 + 1, y0 + dy, z0), org.bukkit.Material.STONE);
                org.bukkit.Location r3 = de.kuby.kubyplugins.util.SafeStand.find(seat, new org.bukkit.util.Vector(0.7, 0, 0));
                boolean inWall = r3 != null && w0.hasCollisionsIn(new org.bukkit.util.BoundingBox(r3.getX() - 0.3, r3.getY() + 0.001,
                        r3.getZ() - 0.3, r3.getX() + 0.3, r3.getY() + 1.8, r3.getZ() + 0.3));
                if (r3 == null || inWall || r3.getY() < y0 + 1 - 1e-6) fails.add("Aufstehen: Wand vor dem Sitz -> " + r3);
                else ok[0]++;
            } finally {
                java.util.List<org.bukkit.block.Block> order = new java.util.ArrayList<>(keep.keySet());
                java.util.Collections.reverse(order);
                for (org.bukkit.block.Block b : order) b.setBlockData(keep.get(b), false);
            }
        } catch (Throwable t) {
            fails.add("Aufstehen: " + t);
        }

        // Backup: Packen und Entpacken einmal echt ausprobieren
        try {
            String err = de.kuby.kubyplugins.service.BackupService.selfCheck(plugin);
            if (err != null) fails.add("Backup: " + err);
            else ok[0]++;
        } catch (Throwable t) {
            fails.add("Backup: " + t);
        }

        // Layout: Text außerhalb des Fensters, über einem Kastenrand oder über anderem Text
        java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>(layout);
        out.sendMessage("SELFTEST LAYOUT " + uniq.size() + " Auffälligkeiten");
        int shown = 0;
        for (String l : uniq) {
            if (shown++ >= 600) break;
            out.sendMessage("SELFTEST LAYOUT " + l);
        }
        java.util.LinkedHashSet<String> buniq = new java.util.LinkedHashSet<>(bedrock);
        out.sendMessage("SELFTEST BEDROCK " + bf[0] + " Formulare, " + bf[1] + " Knöpfe, davon " + bf[2] + " mit Bild, "
                + buniq.size() + " Auffälligkeiten");
        int bshown = 0;
        for (String l : buniq) {
            if (bshown++ >= 200) break;
            out.sendMessage("SELFTEST BEDROCK " + l);
        }
        try {
            java.nio.file.Files.writeString(new java.io.File(plugin.getDataFolder(), "bedrock-formulare.txt").toPath(), forms.toString());
        } catch (java.io.IOException ignored) {
            // nur zum Nachsehen
        }
        out.sendMessage("SELFTEST " + ok[0] + " Dialoge gebaut, " + fails.size() + " Fehler");
        for (String f : fails) out.sendMessage("SELFTEST FEHLER " + f);
        plugin.getLogger().info("SELFTEST " + (fails.isEmpty() ? "OK" : "FEHLER (" + fails.size() + ")"));
    }

    private static boolean bad(double v) {
        return Double.isNaN(v) || Double.isInfinite(v);
    }

    /** Spielt ein Drehbuch rechnerisch ab; null = in Ordnung, sonst der erste Fehler. */
    static String cutsceneCheck(de.kuby.kubyplugins.cinema.Script s) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (int t = 0; t < s.length(); t++) {
            de.kuby.kubyplugins.cinema.Frame f = new de.kuby.kubyplugins.cinema.Frame();
            s.frame(t, f);
            if (bad(f.cam.x()) || bad(f.cam.y()) || bad(f.cam.z()) || bad(f.camYaw) || bad(f.camPitch)) return "Kamera ungültig bei Tick " + t;
            if (f.actor != null && (bad(f.actor.x()) || bad(f.actor.y()) || bad(f.headYaw))) return "Figur ungültig bei Tick " + t;
            keys.clear();
            for (de.kuby.kubyplugins.cinema.Prop p : f.props) {
                if (!keys.add(p.id())) return "Teil doppelt: " + p.id() + " bei Tick " + t;
                if (bad(p.pos().x()) || bad(p.pos().y()) || bad(p.scale().x()) || bad(p.scale().y()) || bad(p.rot()[3])) {
                    return "Teil " + p.id() + " ungültig bei Tick " + t;
                }
            }
        }
        return null;
    }
}
