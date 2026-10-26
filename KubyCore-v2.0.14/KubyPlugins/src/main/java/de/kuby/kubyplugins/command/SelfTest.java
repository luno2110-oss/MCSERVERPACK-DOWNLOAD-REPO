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
        canvas.accept("Freunde", () -> FriendsScreen.canvas(new FriendsScreen.View(List.of(
                new FriendsScreen.Row("Test", true, true, "Wildnis")), List.of(new FriendsScreen.Req("Test2", true)), 20, true,
                true, 0, null), new FriendsScreen.Actions() {
                    public Consumer<Player> accept(String n) { return NO; }
                    public Consumer<Player> deny(String n) { return NO; }
                    public Consumer<Player> jump(String n) { return NO; }
                    public Consumer<Player> write(String n) { return NO; }
                    public Consumer<Player> remove(String n) { return NO; }
                    public Consumer<Player> add() { return NO; }
                    public Consumer<Player> toggleRequests() { return NO; }
                    public Consumer<Player> toggleJump() { return NO; }
                    public Consumer<Player> page(int p) { return NO; }
                }));
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
        for (boolean en : new boolean[]{false, true}) {
            List<de.kuby.kubyplugins.gui.GuideScreen.Page> guide = plugin.guide().pages(en);
            if (guide.isEmpty()) fails.add("Guide: keine Seiten in guide.pages");
            String lang = en ? " (englisch)" : "";
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
