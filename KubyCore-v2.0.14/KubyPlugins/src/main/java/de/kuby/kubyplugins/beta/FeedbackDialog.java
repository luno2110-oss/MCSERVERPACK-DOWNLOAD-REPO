package de.kuby.kubyplugins.beta;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Beta-Feedback als Minecraft-Dialog: je Seite ein Kopf (Kit-Grafik: Titel, Seite x von 7, Fortschritt) und darunter
 * die Fragen. Sterne-Fragen sind Umschalt-Knöpfe („– bitte wählen –“, 1 bis 6 Sterne, „nicht getestet“), Textfragen
 * mehrzeilige Felder. Lange Seiten scrollen. „Weiter“/„Zurück“ speichern den Entwurf, „Absenden“ prüft die Pflichtfragen.
 */
public final class FeedbackDialog {

    private static final int W = 390;
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).build();
    private static final TextColor CYAN = TextColor.color(0x7CF5FF), GOLD = TextColor.color(0xF8D35A), GRAY = TextColor.color(0xA0A0A8),
            LIGHT = TextColor.color(0xCAD2D4), STAR = TextColor.color(0xFFD25A), DIM = TextColor.color(0x5A5A64);

    private final KubyPlugins plugin;
    private final BetaService beta;

    public FeedbackDialog(KubyPlugins plugin, BetaService beta) {
        this.plugin = plugin;
        this.beta = beta;
    }

    /** /feedback: erste Seite mit fehlenden Antworten (oder Seite 1). */
    public void open(Player p) {
        if (!beta.isTester(p)) {
            Msg.send(p, "<gray>Das Feedback-Formular ist für Beta-Tester. Frag das Team nach der Rolle.");
            return;
        }
        List<FeedbackQuestions.Question> miss = beta.missing(p.getUniqueId());
        open(p, miss.isEmpty() ? 0 : pageOf(miss.get(0)));
    }

    static int pageOf(FeedbackQuestions.Question q) {
        List<FeedbackQuestions.Page> pages = FeedbackQuestions.pages();
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).questions().contains(q)) return i;
        return 0;
    }

    static Component stars(int n) {
        return Component.text().append(Component.text("★".repeat(n), STAR)).append(Component.text("★".repeat(6 - n), DIM))
                .append(Component.text("  " + n, NamedTextColor.WHITE)).build();
    }

    /** Kopf einer Seite (öffentlich für den Selbsttest). */
    public static Canvas header(int page, int pages, String title, String sub, int answered, int total, boolean submitted) {
        Canvas c = new Canvas(W).heading("Beta-Feedback", CYAN);
        c.box(0, 0, W, 70, KitData.Box.NAVY);
        c.icon(10, 10, KitData.Icon.BOOK, true);
        c.headline(50, 10, "Beta-Feedback", CYAN);
        c.textRight(380, 12, "Seite " + (page + 1) + " von " + pages, GRAY, false);
        c.text(50, 32, Canvas.fit(title + " · " + sub, true, 330), GOLD, true);
        c.text(12, 48, "Beantwortet:", LIGHT, false);
        c.bar(80, 48, 150, total == 0 ? 0 : (double) answered / total, KitData.Fill.GOLD);
        c.text(236, 48, Canvas.fit(answered + " / " + total + (submitted ? " · abgeschickt ✔" : ""), false, 144),
                submitted ? TextColor.color(0x6CE86E) : LIGHT, false);
        c.text(12, 59, Canvas.fit("Klick aufs Feld schaltet weiter · 6 Sterne = am besten · Text freiwillig", false, 366), GRAY, false);
        return c;
    }

    public void open(Player p, int page) {
        List<FeedbackQuestions.Page> pages = FeedbackQuestions.pages();
        page = Math.max(0, Math.min(pages.size() - 1, page));
        FeedbackQuestions.Page pg = pages.get(page);
        BetaService.Answers a = beta.answersOf(p.getUniqueId());
        int total = 0, answered = 0;
        for (FeedbackQuestions.Question q : FeedbackQuestions.all()) {
            if (q.type() != FeedbackQuestions.Type.STARS) continue;
            total++;
            if (a.values.containsKey(q.key())) answered++;
        }
        Canvas head = header(page, pages.size(), pg.title(), pg.sub(), answered, total, a.submitted > 0);

        List<DialogInput> inputs = inputs(pg, a.values);

        final int at = page;
        List<ActionButton> buttons = new ArrayList<>();
        if (page > 0) buttons.add(button("<#ACA48E>‹ Zurück", (pl, r) -> open(pl, at - 1), pg));
        if (page + 1 < pages.size()) buttons.add(button("<#F4D46A><b>Weiter ›</b>", (pl, r) -> open(pl, at + 1), pg));
        else buttons.add(button("<#6CE86E><b>Absenden ✔</b>", (pl, r) -> submit(pl), pg));
        buttons.add(button("<#ACA48E>Später weiter", (pl, r) -> {
            Msg.send(pl, "<#7CF5FF><b>BETA</b> <dark_gray>▶ <gray>Entwurf gespeichert. Weiter mit <yellow>/feedback");
            UiDialog.close(pl);
        }, pg));
        Dialog d = UiDialog.dialog(p, head, inputs, buttons, buttons.size());
        UiDialog.open(p, d);
    }

    /** Eingabefelder einer Seite mit den bisherigen Antworten (öffentlich für den Selbsttest). */
    public static List<DialogInput> inputs(FeedbackQuestions.Page pg, Map<String, String> values) {
        List<DialogInput> inputs = new ArrayList<>();
        int qn = 1;
        for (FeedbackQuestions.Question q : pg.questions()) {
            String cur = values.get(q.key());
            Component label = Component.text().append(Component.text(qn++ + ". ", GOLD)).append(Component.text(q.text(), NamedTextColor.WHITE))
                    .append(q.hint() == null ? Component.empty() : Component.text("  (" + q.hint() + ")", GRAY)).build();
            if (q.type() == FeedbackQuestions.Type.STARS) {
                List<SingleOptionDialogInput.OptionEntry> opts = new ArrayList<>();
                opts.add(SingleOptionDialogInput.OptionEntry.create("0", Component.text("– bitte wählen –", GRAY), cur == null));
                for (int i = 1; i <= 6; i++) opts.add(SingleOptionDialogInput.OptionEntry.create(String.valueOf(i), stars(i), String.valueOf(i).equals(cur)));
                opts.add(SingleOptionDialogInput.OptionEntry.create("n", Component.text("nicht getestet", GRAY), "n".equals(cur)));
                inputs.add(DialogInput.singleOption(q.key(), label, opts).width(380).build());
            } else {
                inputs.add(DialogInput.text(q.key(), label).width(380).maxLength(1000).initial(cur == null ? "" : cur)
                        .multiline(TextDialogInput.MultilineOptions.create(8, 60)).build());
            }
        }
        return inputs;
    }

    private interface Then {
        void run(Player p, DialogResponseView r);
    }

    /** Knopf, der zuerst die Antworten der Seite als Entwurf speichert. */
    private ActionButton button(String label, Then then, FeedbackQuestions.Page pg) {
        DialogAction act = DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl)) return;
            Map<String, String> vals = new HashMap<>();
            for (FeedbackQuestions.Question q : pg.questions()) {
                String v = response.getText(q.key());
                vals.put(q.key(), v == null ? null : v.trim());
            }
            beta.draft(pl.getUniqueId(), pl.getName(), vals);
            Sfx.play(plugin, pl, Sfx.Kind.CLICK);
            then.run(pl, response);
        }, ONCE);
        return ActionButton.create(Msg.parse(label), null, 124, act);
    }

    private void submit(Player p) {
        List<FeedbackQuestions.Question> miss = beta.missing(p.getUniqueId());
        if (!miss.isEmpty()) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(4, miss.size()); i++) sb.append(i == 0 ? "" : ", ").append(miss.get(i).text());
            Msg.send(p, "<#7CF5FF><b>BETA</b> <dark_gray>▶ <red>Noch " + miss.size() + " Frage(n) offen: <gray>" + Msg.escape(sb.toString())
                    + (miss.size() > 4 ? " …" : "") + " <gray>(„nicht getestet“ geht auch)");
            open(p, pageOf(miss.get(0)));
            return;
        }
        boolean first = beta.answersOf(p.getUniqueId()).submitted == 0;
        beta.submit(p.getUniqueId());
        UiDialog.close(p);
        Sfx.play(plugin, p, Sfx.Kind.VICTORY);
        p.showTitle(Title.title(Msg.parse("<b><gradient:#7CF5FF:#FFD25A>DANKE!</gradient></b>"),
                Component.text("Dein Feedback ist angekommen", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2200), Duration.ofMillis(400))));
        long reward = plugin.getConfig().getLong("beta.belohnung-juwelen", 50);
        if (first && reward > 0) {
            plugin.jewels().add(p.getUniqueId(), reward);
            Msg.send(p, "<#7CF5FF><b>BETA</b> <dark_gray>▶ <green>Danke fürs Feedback! <light_purple>+" + reward + " Juwelen");
        } else {
            Msg.send(p, "<#7CF5FF><b>BETA</b> <dark_gray>▶ <green>Dein Feedback wurde aktualisiert. Danke!");
        }
        if (plugin.alerts() != null) {
            for (Player o : plugin.getServer().getOnlinePlayers()) {
                if (o.hasPermission("kuby.admin") && !o.equals(p)) {
                    Msg.send(o, "<#7CF5FF><b>BETA</b> <dark_gray>▶ <white>" + p.getName() + " <gray>hat das Feedback-Formular "
                            + (first ? "abgeschickt" : "aktualisiert") + ". <click:run_command:'/kadmin feedback'><yellow>[Ansehen]</click>");
                }
            }
        }
    }
}
