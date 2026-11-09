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
        int[] ok = {0};
        java.util.function.BiConsumer<String, Supplier<Canvas>> canvas = (name, c) -> {
            try {
                Dialog d = UiDialog.dialog(null, c.get(), 330);
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
        canvas.accept("Casino-Gewinntabelle", () -> CasinoScreens.slotsPaytable(100000, 100));
        canvas.accept("Roulette-Verlauf", () -> CasinoScreens.rouletteHistory(List.of(0, 17, 32), NO));
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
        }
        canvas.accept("Battlepass englisch", () -> {
            de.kuby.kubyplugins.gui.BattlepassScreen.View v = plugin.battlepassScreen().sample(de.kuby.kubyplugins.gui.BattlepassScreen.Tab.PREMIUM);
            return de.kuby.kubyplugins.gui.BattlepassScreen.canvas(new de.kuby.kubyplugins.gui.BattlepassScreen.View(v.tab(), true,
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
            List<de.kuby.kubyplugins.service.InfoBar.Element> order = new ArrayList<>(List.of(de.kuby.kubyplugins.service.InfoBar.Element.values()));
            java.util.Collections.rotate(order, d.ordinal());
            List<Boolean> vis = new ArrayList<>();
            for (int i = 0; i < order.size(); i++) vis.add(i % 3 != 0);
            canvas.accept("Info-Leiste " + d.label, () -> de.kuby.kubyplugins.gui.InfoBarScreen.canvas(
                    new de.kuby.kubyplugins.gui.InfoBarScreen.View(on, d, order, vis, new de.kuby.kubyplugins.gui.InfoBarScreen.Sample(
                            "1.234.567", "320", on ? new de.kuby.kubyplugins.service.InfoBar.JobLine("Holzfäller", 12, 45, 1200, 3060)
                            : de.kuby.kubyplugins.service.InfoBar.JobLine.NONE)), ia));
        }

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
            List<de.kuby.kubyplugins.gui.ExtraScreens.XpOffer> xo = new ArrayList<>();
            for (int i = 0; i < 12; i++) xo.add(new de.kuby.kubyplugins.gui.ExtraScreens.XpOffer(de.kuby.kubyplugins.ui.KitData.Icon.EMERALD,
                    "W".repeat(30), net.kyori.adventure.text.format.TextColor.color(0x5FE3A0), 999_999_999L, i % 2 == 0, NO));
            canvas.accept("XP-Shop", () -> de.kuby.kubyplugins.gui.ExtraScreens.xpShop(999_999_999L, xo));
            canvas.accept("XP-Shop leer", () -> de.kuby.kubyplugins.gui.ExtraScreens.xpShop(0, List.of()));
            List<de.kuby.kubyplugins.gui.ExtraScreens.Stock> st = new ArrayList<>();
            for (int i = 0; i < 20; i++) st.add(new de.kuby.kubyplugins.gui.ExtraScreens.Stock(de.kuby.kubyplugins.ui.KitData.Icon.DIAMOND,
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

        out.sendMessage("SELFTEST " + ok[0] + " Dialoge gebaut, " + fails.size() + " Fehler");
        for (String f : fails) out.sendMessage("SELFTEST FEHLER " + f);
        plugin.getLogger().info("SELFTEST " + (fails.isEmpty() ? "OK" : "FEHLER (" + fails.size() + ")"));
    }
}
