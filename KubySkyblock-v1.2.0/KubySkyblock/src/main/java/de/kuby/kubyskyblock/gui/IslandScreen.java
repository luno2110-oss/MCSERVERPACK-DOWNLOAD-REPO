package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Icons;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.IslandService;
import de.kuby.kubyskyblock.service.IslandService.Island;
import de.kuby.kubyskyblock.service.IslandService.Upgrade;
import de.kuby.kubyskyblock.service.TemplateService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /insel als Dialog im Himmels-Look: Kopf mit Name, Level und Platz; Schnellknöpfe (Nach Hause, Zuhause hier, Besucher,
 * Top-Inseln); drei Upgrade-Karten (Größe, Mitglieder, Generator) mit Preis; Mitglieder mit Einladen und Entfernen;
 * Insel-Aktionen (Level zählen, Checkliste, Neu starten, Aufgeben). Dazu Top-Inseln, Wahl der Vorlage beim Start und
 * Rückfragen. Ohne Pack: IslandMenu (Truhe).
 */
public final class IslandScreen {

    static final int W = 390;
    static final TextColor SKY = TextColor.color(0x9AD8FF), CLOUD = TextColor.color(0xF2FAFF), MIST = TextColor.color(0x8AA8C0),
            GOLD = TextColor.color(0xF8D35A), GREEN = TextColor.color(0x7FE08A), RED = TextColor.color(0xF27A7A),
            DIM = TextColor.color(0x5E7488), DARK = TextColor.color(0x10202E);

    /** Upgrade-Karte: Name, Bild (je Stufe), Level und Höchst-Level, jetzt, nächstes Level (null = höchstes), Preis,
     *  in Juwelen (Stufe 4), bezahlbar, Klick (null = keiner). */
    public record Up(String name, KitData.Sprite icon, int level, int max, String now, String next, long price, boolean jewels,
                     boolean affordable, Consumer<Player> buy) {}

    /** Mitglied: Name, Besitzer, online, Entfernen (null = nicht erlaubt). */
    public record Member(String name, boolean owner, boolean online, Consumer<Player> kick, String role, String promoteLabel, Consumer<Player> promote) {
        public Member(String name, boolean owner, boolean online, Consumer<Player> kick) {
            this(name, owner, online, kick, owner ? "Besitzer" : "Mitglied", null, null);
        }
    }

    /** Stufe (Nether, End) im Insel-Menü: Name, Farbe, Bild, freigeschaltet, Stand, Anteil erfüllt, Knopf, Klick. */
    public record StageRow(String name, TextColor color, KitData.Sprite icon, boolean unlocked, String status, double frac,
                           String button, Consumer<Player> click) {}

    public record View(String name, long level, int rank, long points, long nextLevel, boolean open, boolean isOwner, long coins,
                       List<Up> ups, List<Member> members, int limit, Consumer<Player> home, Consumer<Player> setHome,
                       Consumer<Player> toggle, Consumer<Player> top, Consumer<Player> invite, Consumer<Player> leave,
                       Consumer<Player> recalc, Consumer<Player> progress, Consumer<Player> reset, Consumer<Player> abandon,
                       String resetHint, Consumer<Player> access, Consumer<Player> limits, List<StageRow> stages) {
        public View(String name, long level, int rank, long points, long nextLevel, boolean open, boolean isOwner, long coins,
                    List<Up> ups, List<Member> members, int limit, Consumer<Player> home, Consumer<Player> setHome,
                    Consumer<Player> toggle, Consumer<Player> top, Consumer<Player> invite, Consumer<Player> leave,
                    Consumer<Player> recalc, Consumer<Player> progress, Consumer<Player> reset, Consumer<Player> abandon, String resetHint,
                    Consumer<Player> access, Consumer<Player> limits) {
            this(name, level, rank, points, nextLevel, open, isOwner, coins, ups, members, limit, home, setHome, toggle, top, invite, leave,
                    recalc, progress, reset, abandon, resetHint, access, limits, null);
        }

        public View(String name, long level, int rank, long points, long nextLevel, boolean open, boolean isOwner, long coins,
                    List<Up> ups, List<Member> members, int limit, Consumer<Player> home, Consumer<Player> setHome,
                    Consumer<Player> toggle, Consumer<Player> top, Consumer<Player> invite, Consumer<Player> leave,
                    Consumer<Player> recalc, Consumer<Player> progress, Consumer<Player> reset, Consumer<Player> abandon, String resetHint,
                    Consumer<Player> access) {
            this(name, level, rank, points, nextLevel, open, isOwner, coins, ups, members, limit, home, setHome, toggle, top, invite, leave,
                    recalc, progress, reset, abandon, resetHint, access, null, null);
        }

        public View(String name, long level, int rank, long points, long nextLevel, boolean open, boolean isOwner, long coins,
                    List<Up> ups, List<Member> members, int limit, Consumer<Player> home, Consumer<Player> setHome,
                    Consumer<Player> toggle, Consumer<Player> top, Consumer<Player> invite, Consumer<Player> leave,
                    Consumer<Player> recalc, Consumer<Player> progress, Consumer<Player> reset, Consumer<Player> abandon, String resetHint) {
            this(name, level, rank, points, nextLevel, open, isOwner, coins, ups, members, limit, home, setHome, toggle, top, invite, leave,
                    recalc, progress, reset, abandon, resetHint, null, null, null);
        }
    }

    public record TopRow(int rank, String name, String owner, long level, long points, boolean mine) {}

    public record Choice(String name, String desc, KitData.Icon icon, Consumer<Player> pick) {}

    private IslandScreen() {
    }

    private static KubySkyblock sb() {
        return KubySkyblock.get();
    }

    private static Consumer<Player> run(Consumer<Player> action) {
        return p -> {
            de.kuby.kubyplugins.ui.UiDialog.close(p);
            action.accept(p);
        };
    }

    private static void feedback(Player p, String err) {
        Sfx.play(sb().core(), p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        if (err != null) Msg.send(p, "<red>" + err);
    }

    // =================================================================================================================
    // Öffnen
    // =================================================================================================================

    /** Insel-Menü; ohne Insel die Wahl der Vorlage. */
    public static void open(Player p) {
        IslandService is = sb().islands();
        Island i = is.islandOf(p.getUniqueId());
        if (i == null) {
            choose(p);
            return;
        }
        if (!sb().core().uiPack().active(p)) {
            new IslandMenu(sb().core()).open(p);
            return;
        }
        UUID id = p.getUniqueId();
        boolean owner = id.equals(i.owner);
        long coins = sb().core().economy().balance(id);
        List<Up> ups = new ArrayList<>();
        for (Upgrade u : Upgrade.values()) {
            long price = is.price(i, u);
            String now, next;
            KitData.Sprite icon;
            String name;
            switch (u) {
                case SIZE -> {
                    name = "Größe";
                    icon = KitData.Sprite.HB_GROESSE;
                    int r = is.radius(i), rn = is.radius(i.size + 1);
                    now = (2 * r + 1) + " x " + (2 * r + 1);
                    next = price < 0 ? null : (2 * rn + 1) + " x " + (2 * rn + 1);
                }
                case SLOTS -> {
                    name = "Mitglieder";
                    icon = KitData.Sprite.HB_MITGLIEDER;
                    now = is.memberLimit(i) + " Plätze";
                    Island probe = i;
                    probe.slots++;
                    int nl = is.memberLimit(probe);
                    probe.slots--;
                    next = price < 0 ? null : nl + " Plätze";
                }
                case GENERATOR -> {
                    name = "Generator";
                    icon = KitData.Sprite.HB_GENERATOR;
                    now = i.generator == 0 ? "Nur Bruchstein" : "Erze aus " + is.generatorLevels().get(Math.min(i.generator,
                            is.generatorLevels().size() - 1)).weights().size() + " Sorten";
                    next = price < 0 ? null : "Level " + (i.generator + 1) + ": " + newOres(is, i.generator + 1);
                }
                case GROWTH -> {
                    name = "Wachstum";
                    icon = KitData.Sprite.HB_IS_WACHSTUM;
                    now = i.growth == 0 ? "normal" : "+" + Math.round(is.growthChance(i) * 100) + " % Pflanzen";
                    next = price < 0 ? null : "+" + Math.round(is.growthAt(i.growth + 1) * 100) + " % schneller wachsen";
                }
                case SPAWN -> {
                    name = "Spawnrate";
                    icon = KitData.Sprite.HB_IS_SPAWN;
                    now = i.spawn == 0 ? "normal" : "+" + Math.round(is.spawnChance(i) * 100) + " % Wesen";
                    next = price < 0 ? null : "+" + Math.round(is.spawnAt(i.spawn + 1) * 100) + " % Tiere und Monster";
                }
                default -> {
                    name = "Limits";
                    icon = KitData.Sprite.HB_IS_LIMITS;
                    double per = sb().getConfig().getDouble("limits.bonus-je-stufe", 0.05);
                    now = i.limit == 0 ? "Grundwerte" : "+" + Math.round(sb().limits().bonus(i) * 100) + " %";
                    next = price < 0 ? null : "+" + Math.round((i.limit + 1) * per * 100) + " % Trichter, Tiere …";
                }
            }
            int lvl = is.level(i, u);
            boolean jw = is.jewelStep(i, u);
            long have = jw ? sb().core().jewels().balance(id) : coins;
            ups.add(new Up(name, UpgradeTier.icon(icon, lvl), lvl, is.maxLevel(u), now, next, Math.max(0, price), jw, price >= 0 && have >= price,
                    price < 0 ? null : run(pl -> {
                feedback(pl, is.upgrade(pl, u));
                open(pl);
            })));
        }
        List<Member> ms = new ArrayList<>();
        boolean manage = is.canManage(i, id);
        for (UUID m : i.everyone()) {
            boolean isOwner = m.equals(i.owner);
            IslandService.Role role = is.role(i, m);
            boolean co = role == IslandService.Role.CO_BESITZER;
            boolean mayKick = manage && !isOwner && !m.equals(id) && (owner || !co);
            ms.add(new Member(IslandService.nameOf(m), isOwner, Bukkit.getPlayer(m) != null, mayKick ? run(pl -> confirm(pl,
                    "Mitglied entfernen", IslandService.nameOf(m) + " von der Insel entfernen?", "Entfernen", q -> {
                        feedback(q, is.kick(q, m));
                        open(q);
                    })) : null, role.label, owner && !isOwner ? (co ? "Mitglied" : "Co") : null, owner && !isOwner ? run(pl -> {
                        feedback(pl, is.setCoOwner(pl, m, !co));
                        open(pl);
                    }) : null));
        }
        long left = is.resetCooldownLeft(i);
        UiDialog.show(p, canvas(new View(i.name, is.levelOf(i), is.rank(i), i.points, (is.levelOf(i) + 1) * is.perLevel(), i.open, owner,
                coins, ups, ms, is.memberLimit(i) + 1,
                run(pl -> is.home(pl, i)),
                run(pl -> {
                    feedback(pl, is.setHome(pl));
                    open(pl);
                }),
                manage ? run(pl -> {
                    feedback(pl, is.toggleOpen(pl));
                    open(pl);
                }) : null,
                run(IslandScreen::top),
                is.has(i, id, IslandService.Right.EINLADEN) ? run(IslandScreen::askInvite) : null,
                owner ? null : run(pl -> confirm(pl, "Insel verlassen", "Willst du " + i.name + " wirklich verlassen?", "Verlassen", q ->
                        feedback(q, is.leave(q)))),
                run(pl -> {
                    String err = is.recalc(i, pts -> {
                        Msg.send(pl, "<#9AD8FF>Insel-Level: <white>" + is.levelOf(i) + " <gray>(" + Msg.coins(pts) + " Punkte)");
                        open(pl);
                    });
                    if (err != null) feedback(pl, err);
                }),
                run(GeneratorScreen::island),
                owner && left == 0 ? run(IslandScreen::chooseReset) : null,
                owner ? run(pl -> confirm(pl, "Insel aufgeben", i.members.isEmpty()
                        ? "Deine Insel wird geleert und ist dann weg. Wirklich aufgeben?"
                        : "Die Insel geht an " + IslandService.nameOf(i.members.iterator().next()) + ". Wirklich aufgeben?", "Aufgeben", q ->
                        feedback(q, is.abandon(q)))) : null,
                owner && left > 0 ? "Neustart in " + de.kuby.kubyplugins.service.KitService.format(left) : null,
                manage ? run(IslandScreen::access) : null, run(IslandExtraScreen::limits), stageRows(i))), 330);
    }

    /** Nether- und End-Stufe als kleine Karten (Klick öffnet die Stufen, freigeschaltet: Hinreisen). */
    private static List<StageRow> stageRows(IslandService.Island i) {
        de.kuby.kubyskyblock.service.StageService ss = sb().stages();
        if (ss == null) return null;
        List<StageRow> out = new ArrayList<>();
        for (de.kuby.kubyskyblock.service.StageService.Stage s : de.kuby.kubyskyblock.service.StageService.Stage.values()) {
            boolean un = ss.unlocked(i, s);
            var reqs = un ? List.<de.kuby.kubyskyblock.service.StageService.Req>of() : ss.requirements(i, s);
            long done = reqs.stream().filter(de.kuby.kubyskyblock.service.StageService.Req::done).count();
            double frac = un ? 1 : reqs.isEmpty() ? 0 : (double) done / reqs.size();
            out.add(new StageRow(s.label + "-Stufe", TextColor.fromHexString(s.color), StageScreen.icon(s), un, ss.progressText(i, s), frac,
                    un ? "Hinreisen" : "Ansehen", un ? run(pl -> feedback(pl, ss.travel(pl, s))) : run(StageScreen::open)));
        }
        return out;
    }

    /** Welche Blöcke ab dieser Generator-Stufe neu dazukommen (kurz). */
    private static String newOres(IslandService is, int level) {
        var ls = is.generatorLevels();
        if (level >= ls.size()) return "";
        List<String> out = new ArrayList<>();
        for (Material m : ls.get(level).weights().keySet()) {
            if (level == 0 || !ls.get(level - 1).weights().containsKey(m)) out.add(nice(m));
        }
        return out.isEmpty() ? "mehr Erze" : String.join(", ", out);
    }

    static String nice(Material m) {
        String n = m.name().toLowerCase().replace("_ore", "").replace("deepslate_", "").replace('_', ' ');
        return switch (n) {
            case "coal" -> "Kohle";
            case "iron" -> "Eisen";
            case "copper" -> "Kupfer";
            case "gold" -> "Gold";
            case "redstone" -> "Redstone";
            case "lapis" -> "Lapis";
            case "diamond" -> "Diamant";
            case "emerald" -> "Smaragd";
            case "ancient debris" -> "Antiker Schrott";
            case "stone" -> "Stein";
            case "cobblestone" -> "Bruchstein";
            default -> Character.toUpperCase(n.charAt(0)) + n.substring(1);
        };
    }

    /** Top-Inseln. */
    public static void top(Player p) {
        IslandService is = sb().islands();
        Island mine = is.islandOf(p.getUniqueId());
        List<TopRow> rows = new ArrayList<>();
        int r = 1;
        for (Island i : is.top(10)) {
            rows.add(new TopRow(r++, i.name, IslandService.nameOf(i.owner), is.levelOf(i), i.points, i == mine));
        }
        TopRow own = mine == null ? null : new TopRow(is.rank(mine), mine.name, IslandService.nameOf(mine.owner), is.levelOf(mine),
                mine.points, true);
        if (!sb().core().uiPack().active(p)) {
            Msg.send(p, "<#9AD8FF><b>Top-Inseln</b>");
            for (TopRow t : rows) Msg.send(p, " <gold>#" + t.rank() + " <white>" + Msg.escape(t.name()) + " <gray>(" + t.owner() + ") Level "
                    + t.level());
            return;
        }
        UiDialog.show(p, top(rows, own, run(IslandScreen::open), run(IslandExtraScreen::prizes), run(IslandExtraScreen::visit)), 330);
    }

    /** Wahl der Insel-Vorlage (erste Insel). */
    public static void choose(Player p) {
        List<Choice> cs = new ArrayList<>();
        for (TemplateService.Template t : sb().templates().all()) {
            cs.add(new Choice(t.name(), t.desc(), Icons.of(t.icon(), KitData.Icon.GRASS_BLOCK), run(pl -> {
                if (!ModeService.SKYBLOCK.equals(sb().core().modes().modeOf(pl.getUniqueId()))) {
                    Msg.send(pl, "<red>Das geht nur im Skyblock-Modus.");
                    return;
                }
                String err = sb().islands().create(pl, t.id());
                if (err != null) feedback(pl, err);
            })));
        }
        if (!sb().core().uiPack().active(p)) {
            new TemplateMenu(sb().core(), false).open(p);
            return;
        }
        UiDialog.show(p, choose(cs, "Wähle deine Insel", "Du kannst sie später mit „Neu starten“ wechseln"), 330);
    }

    /** Neustart: Vorlage wählen, dann Rückfrage. */
    private static void chooseReset(Player p) {
        List<Choice> cs = new ArrayList<>();
        for (TemplateService.Template t : sb().templates().all()) {
            cs.add(new Choice(t.name(), t.desc(), Icons.of(t.icon(), KitData.Icon.GRASS_BLOCK), run(pl -> confirm(pl, "Insel neu starten",
                    "Alles auf der Insel wird gelöscht und neu als „" + t.name() + "“ gebaut. Upgrades und Mitglieder bleiben.", "Neu starten",
                    q -> feedback(q, sb().islands().reset(q, t.id()))))));
        }
        UiDialog.show(p, choose(cs, "Insel neu starten", "Wähle die neue Vorlage"), 330);
    }

    /** Rückfrage mit Ja und Nein. */
    public static void confirm(Player p, String title, String text, String yes, Consumer<Player> onYes) {
        if (!sb().core().uiPack().active(p)) {
            onYes.accept(p);
            return;
        }
        Canvas c = new Canvas(W).heading(title, SKY);
        c.box(0, 0, W, 72, KitData.Box.CLOUD);
        c.text(12, 10, Canvas.caps(title), SKY, true);
        List<String> lines = Canvas.wrap(text, W - 24);
        int y = 26;
        for (String l : lines) {
            c.text(12, y, l, CLOUD, false);
            y += 10;
        }
        int by = Math.max(52, y + 4);
        c.smallButton(W - 12 - 100, by, 100, KitData.Small.GOLD, yes, GOLD, true);
        c.action(W - 12 - 100, by, 100, KitData.SMALL_H, run(onYes), yes);
        c.smallButton(W - 12 - 210, by, 100, KitData.Small.DARK, "Abbrechen", CLOUD, false);
        c.action(W - 12 - 210, by, 100, KitData.SMALL_H, run(IslandScreen::open), "Abbrechen");
        UiDialog.show(p, c, 330);
    }

    /** Spieler einladen: Name eingeben. */
    public static void askInvite(Player p) {
        DialogBase base = DialogBase.builder(Msg.parse("<#9AD8FF><b>Auf die Insel einladen</b>"))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Msg.parse("<gray>Name des Spielers (muss online sein)."), 240)))
                .inputs(List.of(DialogInput.text("name", Msg.parse("<gray>Spieler")).width(200).maxLength(16).initial("").build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton ok = ActionButton.create(Msg.parse("<green><b>Einladen</b>"), null, 100, DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl)) return;
            String n = response.getText("name");
            Player t = n == null ? null : Bukkit.getPlayerExact(n.trim());
            if (t == null) feedback(pl, "Dieser Spieler ist nicht online.");
            else {
                String err = sb().islands().invite(pl, t);
                feedback(pl, err);
                if (err == null) Msg.send(pl, "<green>Einladung an " + t.getName() + " geschickt.");
            }
            open(pl);
        }, once));
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100, DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player pl) open(pl);
        }, once));
        de.kuby.kubyplugins.ui.UiDialog.open(p, Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(ok, no))));
    }

    // =================================================================================================================
    // Rechte und Besucher
    // =================================================================================================================

    /** Ein Spieler in einer Liste mit bis zu zwei Knöpfen. */
    public record Person(String name, boolean online, String b1, Consumer<Player> a1, String b2, Consumer<Player> a2) {}

    /** Rechte-Tabelle: je Recht die Häkchen für Mitglied, Gast, Besucher; Listen Gäste, Besucher auf der Insel, Gesperrte. */
    public record AccessView(String island, List<String> rights, boolean[][] on, Consumer<Player>[][] toggle, List<Person> guests,
                             List<Person> visitors, List<Person> banned, Consumer<Player> addGuest, Consumer<Player> addBan, Consumer<Player> back) {}

    @SuppressWarnings("unchecked")
    public static void access(Player p) {
        IslandService is = sb().islands();
        Island i = is.islandOf(p.getUniqueId());
        if (i == null || !is.canManage(i, p.getUniqueId())) {
            feedback(p, "Das dürfen nur Besitzer und Co-Besitzer.");
            return;
        }
        IslandService.Right[] rs = IslandService.Right.values();
        List<String> names = new ArrayList<>();
        boolean[][] on = new boolean[rs.length][IslandService.EDITABLE.size()];
        Consumer<Player>[][] tg = new Consumer[rs.length][IslandService.EDITABLE.size()];
        for (int r = 0; r < rs.length; r++) {
            names.add(rs[r].label);
            for (int k = 0; k < IslandService.EDITABLE.size(); k++) {
                IslandService.Role role = IslandService.EDITABLE.get(k);
                IslandService.Right right = rs[r];
                on[r][k] = i.rights.getOrDefault(role, java.util.EnumSet.noneOf(IslandService.Right.class)).contains(right);
                // Besucher und Gäste dürfen nie einladen oder Upgrades kaufen
                boolean allowed = role == IslandService.Role.MITGLIED || (right != IslandService.Right.EINLADEN && right != IslandService.Right.UPGRADES);
                tg[r][k] = allowed ? run(pl -> {
                    feedback(pl, is.toggleRight(pl, role, right));
                    access(pl);
                }) : null;
            }
        }
        List<Person> guests = new ArrayList<>();
        for (UUID g : i.trusted) {
            guests.add(new Person(IslandService.nameOf(g), Bukkit.getPlayer(g) != null, "Entfernen", run(pl -> {
                feedback(pl, is.trust(pl, g, false));
                access(pl);
            }), null, null));
        }
        List<Person> visitors = new ArrayList<>();
        for (Player v : is.visitors(i)) {
            UUID vid = v.getUniqueId();
            boolean guest = i.trusted.contains(vid);
            visitors.add(new Person(v.getName() + (guest ? " (Gast)" : ""), true, "Rauswerfen", run(pl -> {
                feedback(pl, is.expel(pl, vid));
                access(pl);
            }), "Sperren", run(pl -> confirm(pl, "Spieler sperren", v.getName() + " sperren? Er wird sofort von der Insel geworfen und kann nicht mehr hinein.",
                    "Sperren", q -> {
                        feedback(q, is.ban(q, vid, true));
                        access(q);
                    }))));
        }
        List<Person> banned = new ArrayList<>();
        for (UUID b : i.banned) {
            banned.add(new Person(IslandService.nameOf(b), Bukkit.getPlayer(b) != null, "Entsperren", run(pl -> {
                feedback(pl, is.ban(pl, b, false));
                access(pl);
            }), null, null));
        }
        UiDialog.show(p, access(new AccessView(i.name, names, on, tg, guests, visitors, banned,
                run(pl -> askName(pl, "Gast hinzufügen", "Gäste dürfen auch auf geschlossene Inseln und bekommen die Rechte der Spalte Gast.",
                        "Hinzufügen", (q, id) -> feedback(q, is.trust(q, id, true)))),
                run(pl -> askName(pl, "Spieler sperren", "Gesperrte kommen nicht mehr auf deine Insel, auch nicht, wenn sie offen ist.",
                        "Sperren", (q, id) -> feedback(q, is.ban(q, id, true)))),
                run(IslandScreen::open))), 330);
    }

    public static Canvas access(AccessView v) {
        Canvas c = new Canvas(W).heading("Rechte & Besucher", SKY);
        c.box(0, 0, W, 40, KitData.Box.CLOUD);
        c.sprite(10 + (32 - KitData.Sprite.HB_MITGLIEDER.width) / 2, 4, KitData.Sprite.HB_MITGLIEDER);
        c.text(50, 9, Canvas.caps("Rechte & Besucher"), SKY, true);
        c.text(50, 22, Canvas.fit(Canvas.lit(v.island()) + " · Besitzer und Co-Besitzer dürfen alles", false, W - 60), MIST, false);
        int y = 46;
        y = section(c, y, "Rechte", null);
        String[] cols = {"Mitglied", "Gast", "Besucher"};
        int cx0 = 170, cw = (W - cx0) / 3;
        for (int k = 0; k < 3; k++) c.textCenter(cx0 + k * cw + cw / 2, y, Canvas.caps(cols[k]), CLOUD, true);
        y += 12;
        for (int r = 0; r < v.rights().size(); r++) {
            c.box(0, y, W, 20, KitData.Box.CLOUD_CARD);
            c.text(8, y + 6, Canvas.fit(v.rights().get(r), false, cx0 - 12), CLOUD, false);
            for (int k = 0; k < 3; k++) {
                int bx = cx0 + k * cw + (cw - 52) / 2;
                Consumer<Player> t = v.toggle()[r][k];
                boolean on = v.on()[r][k];
                if (t == null) {
                    c.textCenter(cx0 + k * cw + cw / 2, y + 6, "—", DIM, false);
                    continue;
                }
                c.smallButton(bx, y + 2, 52, on ? KitData.Small.GREEN : KitData.Small.DARK, on ? "✔ an" : "aus", on ? CLOUD : DIM, true);
                c.action(bx, y + 2, 52, KitData.SMALL_H, t, (on ? "Ausschalten: " : "Einschalten: ") + v.rights().get(r) + " für " + cols[k]);
            }
            y += 22;
        }
        y += 6;
        y = people(c, y, "Gäste (Vertrauensliste)", v.guests(), "Noch keine Gäste.", v.addGuest(), "Gast hinzufügen");
        y = people(c, y, "Besucher auf der Insel", v.visitors(), "Gerade ist niemand zu Besuch.", null, null);
        y = people(c, y, "Gesperrt", v.banned(), "Niemand gesperrt.", v.addBan(), "Spieler sperren");
        c.smallButton(0, y, 120, KitData.Small.DARK, "« Zur Insel", CLOUD, false);
        c.action(0, y, 120, KitData.SMALL_H, v.back(), "Zurück zur Insel");
        return c;
    }

    private static int people(Canvas c, int y, String title, List<Person> list, String empty, Consumer<Player> add, String addLabel) {
        y = section(c, y, title, list.isEmpty() ? null : String.valueOf(list.size()));
        int h = 8 + Math.max(1, list.size()) * 16 + (add != null ? 22 : 0);
        c.box(0, y, W, h, KitData.Box.CLOUD_CARD);
        int my = y + 6;
        if (list.isEmpty()) {
            c.text(10, my + 3, empty, DIM, false);
            my += 16;
        }
        for (Person ps : list) {
            c.text(10, my + 3, ps.online() ? "●" : "○", ps.online() ? GREEN : DIM, false);
            c.text(22, my + 3, Canvas.fit(Canvas.lit(ps.name()), true, 200), CLOUD, true);
            int bx = W - 8 - 76;
            if (ps.a1() != null) {
                c.smallButton(bx, my, 76, KitData.Small.DARK, ps.b1(), "Sperren".equals(ps.b1()) || "Rauswerfen".equals(ps.b1()) ? GOLD : CLOUD, false);
                c.action(bx, my, 76, KitData.SMALL_H, ps.a1(), ps.b1());
            }
            if (ps.a2() != null) {
                c.smallButton(bx - 80, my, 76, KitData.Small.DARK, ps.b2(), RED, false);
                c.action(bx - 80, my, 76, KitData.SMALL_H, ps.a2(), ps.b2());
            }
            my += 16;
        }
        if (add != null) {
            c.smallButton(10, my + 2, 130, KitData.Small.GREEN, addLabel, CLOUD, true);
            c.action(10, my + 2, 130, KitData.SMALL_H, add, addLabel);
        }
        return y + h + 8;
    }

    /** Name eingeben (auch für Spieler, die gerade nicht online sind, wenn sie schon einmal da waren). */
    public static void askName(Player p, String title, String hint, String button, java.util.function.BiConsumer<Player, UUID> onName) {
        DialogBase base = DialogBase.builder(Msg.parse("<#9AD8FF><b>" + Msg.escape(title) + "</b>"))
                .canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.of(DialogBody.plainMessage(Msg.parse("<gray>" + Msg.escape(hint)), 240)))
                .inputs(List.of(DialogInput.text("name", Msg.parse("<gray>Spieler")).width(200).maxLength(16).initial("").build()))
                .build();
        ClickCallback.Options once = ClickCallback.Options.builder().uses(1).build();
        ActionButton ok = ActionButton.create(Msg.parse("<green><b>" + Msg.escape(button) + "</b>"), null, 100, DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player pl)) return;
            String n = response.getText("name");
            Player online = n == null ? null : Bukkit.getPlayerExact(n.trim());
            org.bukkit.OfflinePlayer op = online != null ? online : n == null ? null : Bukkit.getOfflinePlayerIfCached(n.trim());
            if (op == null) feedback(pl, "Diesen Spieler kennt der Server nicht.");
            else onName.accept(pl, op.getUniqueId());
            access(pl);
        }, once));
        ActionButton no = ActionButton.create(Msg.parse("<gray>Abbrechen"), null, 100, DialogAction.customClick((response, audience) -> {
            if (audience instanceof Player pl) access(pl);
        }, once));
        de.kuby.kubyplugins.ui.UiDialog.open(p, Dialog.create(b -> b.empty().base(base).type(DialogType.confirmation(ok, no))));
    }

    // =================================================================================================================
    // Darstellung
    // =================================================================================================================

    /** Knopf mit Icon (16 px) und Text; style null = gesperrt (grau). */
    private static void quick(Canvas c, int x, int y, int w, KitData.Button style, KitData.Sprite icon, String label, Consumer<Player> click) {
        c.button(x, y, w, style == null ? KitData.Button.DARK : style);
        String l = Canvas.fit(label, true, w - 26);
        int gw = 16 + 4 + Canvas.width(l, true);
        int gx = x + (w - gw) / 2;
        c.sprite(gx + (16 - icon.width) / 2, y + 3 + (16 - icon.height) / 2, icon);
        if (style == KitData.Button.TEAL || style == KitData.Button.GREEN || style == KitData.Button.GOLD) c.textFlat(gx + 20, y + 7, l, DARK, true);
        else c.text(gx + 20, y + 7, l, style == null ? DIM : CLOUD, true);
        if (click != null) c.action(x, y, w, 22, click, label);
    }

    private static int section(Canvas c, int y, String title, String right) {
        String t = Canvas.caps(title);
        c.text(2, y, t, SKY, true);
        int tw = Canvas.width(t, true);
        int rw = right == null ? 0 : Canvas.width(right, false) + 8;
        c.line(4 + tw + 4, y + 3, W - 8 - tw - 4 - rw, SKY);
        if (right != null) c.textRight(W - 2, y, right, MIST, false);
        return y + 12;
    }

    public static Canvas canvas(View v) {
        Canvas c = new Canvas(W).heading("Insel", SKY);
        c.box(0, 0, W, 54, KitData.Box.CLOUD);
        c.sprite(10 + (32 - KitData.Sprite.HB_INSEL.width) / 2, 11, KitData.Sprite.HB_INSEL);
        c.headline(50, 9, "Insel", SKY);
        c.text(50, 33, Canvas.fit(Canvas.lit(v.name()), true, 200), CLOUD, true);
        c.text(50, 43, "Level " + Msg.coins(v.level()) + " · " + Msg.coins(v.points()) + " / " + Msg.coins(v.nextLevel()) + " Punkte", MIST, false);
        c.textRight(378, 10, v.rank() > 0 ? "#" + v.rank() : "–", GOLD, true);
        c.textRight(378, 21, Canvas.caps("Platz"), MIST, false);
        c.textRight(378, 38, Msg.coins(v.coins()) + " $", GOLD, false);

        int y = 60, qw = (W - 9) / 4;
        quick(c, 0, y, qw, KitData.Button.TEAL, KitData.Sprite.HB_S_HOMES, "Nach Hause", v.home());
        quick(c, qw + 3, y, qw, KitData.Button.GREEN, KitData.Sprite.QU_COMPASS, "Home hier", v.setHome());
        quick(c, 2 * (qw + 3), y, qw, v.toggle() == null ? null : v.open() ? KitData.Button.PURPLE : KitData.Button.RED,
                KitData.Sprite.FR_PEOPLE, v.open() ? "Besuch: an" : "Besuch: aus", v.toggle());
        quick(c, 3 * (qw + 3), y, qw, KitData.Button.GOLD, KitData.Sprite.QU_S_RANK, "Top-Inseln", v.top());
        y += 22 + 10;

        // Upgrades
        y = section(c, y, "Upgrades", null);
        int cw = (W - 8) / 3, ch = 82;
        for (int k = 0; k < v.ups().size(); k++) {
            Up u = v.ups().get(k);
            int x = (k % 3) * (cw + 4);
            int cy = y + (k / 3) * (ch + 4);
            c.box(x, cy, cw, ch, KitData.Box.CLOUD_CARD);
            c.sprite(x + 6 + (32 - u.icon().width) / 2, cy + 6 + (32 - u.icon().height) / 2, u.icon());
            c.text(x + 42, cy + 8, Canvas.fit(u.name(), true, cw - 48), CLOUD, true);
            c.text(x + 42, cy + 20, Canvas.fit(u.now(), false, cw - 48), MIST, false);
            UpgradeTier.text(c, x + 42, cy + 31, Canvas.fit(UpgradeTier.shortLabel(u.level()), false, cw - 48), u.level(), false);
            if (u.next() == null) {
                c.text(x + 8, cy + 46, Canvas.caps("Höchstes Level"), GREEN, false);
            } else {
                List<String> nl = Canvas.wrap("→ " + u.next(), cw - 16);
                for (int l = 0; l < Math.min(2, nl.size()); l++) c.text(x + 8, cy + 42 + 10 * l, nl.get(l), GREEN, false);
                int bw = 46;
                if (u.jewels()) {
                    // Stufe 4: Preis in Juwelen, mit dem Juwelen-Zeichen statt Wort (passt in die schmale Karte)
                    int tw = c.text(x + 8, cy + ch - 14, Canvas.fit(Msg.coins(u.price()), false, cw - 16 - bw - 16), u.affordable() ? UpgradeTier.JEWEL : RED, false);
                    c.sprite(x + 8 + tw + 2, cy + ch - 16, KitData.Sprite.IB_EL_JEWELS);
                } else {
                    c.text(x + 8, cy + ch - 14, Canvas.fit(Msg.coins(u.price()) + " $", false, cw - 16 - bw - 4), u.affordable() ? GOLD : RED, false);
                }
                c.smallButton(x + cw - 6 - bw, cy + ch - 20, bw, u.affordable() ? KitData.Small.GOLD : KitData.Small.DARK, "Kaufen",
                        u.affordable() ? GOLD : DIM, true);
                if (u.buy() != null && u.affordable()) c.action(x + cw - 6 - bw, cy + ch - 20, bw, KitData.SMALL_H, u.buy(), u.name() + " verbessern");
            }
        }
        y += ((v.ups().size() + 2) / 3) * (ch + 4) + 6;

        // Stufen (Nether, End)
        if (v.stages() != null && !v.stages().isEmpty()) {
            long free = v.stages().stream().filter(StageRow::unlocked).count();
            y = section(c, y, "Stufen", free + "/" + v.stages().size() + " frei");
            int sw = (W - 4) / 2, sh = 48;
            for (int k = 0; k < v.stages().size(); k++) {
                StageRow s = v.stages().get(k);
                int x = (k % 2) * (sw + 4), sy = y + (k / 2) * (sh + 4);
                c.box(x, sy, sw, sh, s.unlocked() ? KitData.Box.GOLD : KitData.Box.CLOUD_CARD);
                c.sprite(x + 6 + (32 - s.icon().width) / 2, sy + 8 + (32 - s.icon().height) / 2, s.icon());
                c.text(x + 44, sy + 8, Canvas.fit(s.name(), true, sw - 52), s.color(), true);
                c.text(x + 44, sy + 20, Canvas.fit(s.status(), false, sw - 52), s.unlocked() ? GREEN : MIST, false);
                int bw = 64, bx = x + sw - 6 - bw, by = sy + sh - KitData.SMALL_H - 5;
                if (!s.unlocked()) c.barFlat(x + 44, by + 5, bx - x - 44 - 6, s.frac(), KitData.Fill.GOLD);
                c.smallButton(bx, by, bw, s.unlocked() ? KitData.Small.GREEN : KitData.Small.DARK, s.button(), CLOUD, true);
                if (s.click() != null) c.action(bx, by, bw, KitData.SMALL_H, s.click(), s.button() + " · " + s.name());
            }
            y += ((v.stages().size() + 1) / 2) * (sh + 4) + 6;
        }

        // Mitglieder
        y = section(c, y, "Mitglieder", v.members().size() + "/" + v.limit());
        int rows = v.members().size();
        int mh = 8 + rows * 16 + 26;
        c.box(0, y, W, mh, KitData.Box.CLOUD_CARD);
        int my = y + 8;
        for (Member m : v.members()) {
            c.text(10, my + 3, m.online() ? "●" : "○", m.online() ? GREEN : DIM, false);
            int nw = c.text(22, my + 3, Canvas.fit(Canvas.lit(m.name()), true, 180), CLOUD, true);
            c.text(22 + nw + 6, my + 3, m.role(), m.owner() ? GOLD : "Co-Besitzer".equals(m.role()) ? SKY : MIST, false);
            if (m.kick() != null) {
                c.smallButton(W - 8 - 70, my, 70, KitData.Small.DARK, "Entfernen", RED, false);
                c.action(W - 8 - 70, my, 70, KitData.SMALL_H, m.kick(), "Mitglied entfernen");
            }
            if (m.promote() != null) {
                c.smallButton(W - 8 - 70 - 74, my, 70, KitData.Small.DARK, m.promoteLabel().equals("Co") ? "↑ Co-Besitzer" : "↓ Mitglied", SKY, false);
                c.action(W - 8 - 70 - 74, my, 70, KitData.SMALL_H, m.promote(), m.promoteLabel().equals("Co") ? "Zum Co-Besitzer machen" : "Zum Mitglied zurückstufen");
            }
            my += 16;
        }
        if (v.invite() != null) {
            c.smallButton(10, my + 4, 100, KitData.Small.GREEN, "Einladen", CLOUD, true);
            c.action(10, my + 4, 100, KitData.SMALL_H, v.invite(), "Spieler einladen");
        }
        if (v.leave() != null) {
            c.smallButton(10, my + 4, 100, KitData.Small.DARK, "Verlassen", RED, false);
            c.action(10, my + 4, 100, KitData.SMALL_H, v.leave(), "Insel verlassen");
        }
        if (v.access() != null) {
            c.smallButton(114, my + 4, 150, KitData.Small.GOLD, "Rechte & Besucher", GOLD, true);
            c.action(114, my + 4, 150, KitData.SMALL_H, v.access(), "Rollen, Rechte, Gäste, Sperrliste");
        }
        y += mh + 10;

        // Insel
        y = section(c, y, "Insel", v.resetHint());
        int bw = (W - 12) / 5;
        String[] labels = {"Level zählen", "Generatoren", "Limits", "Neu starten", "Aufgeben"};
        @SuppressWarnings("unchecked")
        Consumer<Player>[] acts = new Consumer[]{v.recalc(), v.progress(), v.limits(), v.reset(), v.abandon()};
        for (int k = 0; k < 5; k++) {
            int x = k * (bw + 3);
            boolean on = acts[k] != null;
            KitData.Small st = !on ? KitData.Small.DARK : k >= 3 ? KitData.Small.DARK : KitData.Small.GOLD;
            c.smallButton(x, y, bw, st, labels[k], !on ? DIM : k >= 3 ? RED : GOLD, false);
            if (on) c.action(x, y, bw, KitData.SMALL_H, acts[k], labels[k]);
        }
        return c;
    }

    public static Canvas top(List<TopRow> rows, TopRow own, Consumer<Player> back) {
        return top(rows, own, back, null, null);
    }

    public static Canvas top(List<TopRow> rows, TopRow own, Consumer<Player> back, Consumer<Player> prizes, Consumer<Player> visit) {
        Canvas c = new Canvas(W).heading("Top-Inseln", SKY);
        c.box(0, 0, W, 54, KitData.Box.CLOUD);
        c.sprite(10 + (32 - KitData.Sprite.QU_RANK_HEAD.width) / 2, 11 + (32 - KitData.Sprite.QU_RANK_HEAD.height) / 2, KitData.Sprite.QU_RANK_HEAD);
        c.headline(50, 12, "Top-Inseln", SKY);
        c.text(50, 36, "Level nach Punkten aller Blöcke", MIST, false);
        c.smallButton(W - 8 - 70, 18, 70, KitData.Small.DARK, "Zurück", CLOUD, false);
        c.action(W - 8 - 70, 18, 70, KitData.SMALL_H, back, "Zurück");
        int y = 60;
        if (prizes != null || visit != null) {
            int bw = (W - 4) / 2;
            c.smallButton(0, y, bw, KitData.Small.GOLD, "Preise & Gewinner", GOLD, true);
            if (prizes != null) c.action(0, y, bw, KitData.SMALL_H, prizes, "Wochen- und Monatspreise der besten Inseln");
            c.smallButton(bw + 4, y, bw, KitData.Small.GREEN, "Inseln besuchen", CLOUD, true);
            if (visit != null) c.action(bw + 4, y, bw, KitData.SMALL_H, visit, "Offene Inseln und Biom-Inseln");
            y += KitData.SMALL_H + 6;
        }
        KitData.Sprite[] medals = {KitData.Sprite.QU_M_GOLD, KitData.Sprite.QU_M_SILVER, KitData.Sprite.QU_M_BRONZE};
        for (TopRow t : rows) y = topRow(c, y, t, medals);
        if (rows.isEmpty()) {
            c.text(4, y, "Noch keine Inseln.", MIST, false);
            y += 14;
        }
        if (own != null && own.rank() > rows.size()) {
            y += 6;
            topRow(c, y, own, medals);
        }
        return c;
    }

    private static int topRow(Canvas c, int y, TopRow t, KitData.Sprite[] medals) {
        int h = t.rank() <= 3 ? 32 : 22;
        c.box(0, y, W, h, t.mine() ? KitData.Box.GOLD : KitData.Box.CLOUD_CARD);
        if (t.rank() <= 3) c.sprite(8 + (18 - medals[t.rank() - 1].width) / 2, y + (h - medals[t.rank() - 1].height) / 2, medals[t.rank() - 1]);
        else c.textCenter(17, y + 7, t.rank() < 1000 ? "#" + t.rank() : String.valueOf(t.rank()), MIST, t.rank() < 1000);
        int lw = c.textRight(W - 10, y + 7, "Level " + Canvas.num(t.level()), GOLD, true);
        c.text(34, y + 7, Canvas.fit(Canvas.lit(t.name()), true, Math.min(210, W - 10 - lw - 40)), t.mine() ? GOLD : CLOUD, true);
        if (h == 32) c.text(34, y + 18, Canvas.lit(t.owner()), MIST, false);
        if (h == 32) c.textRight(W - 10, y + 18, Canvas.num(t.points()) + " Punkte", MIST, false);
        return y + h + 3;
    }

    public static Canvas choose(List<Choice> choices, String title, String sub) {
        Canvas c = new Canvas(W).heading(title, SKY);
        c.box(0, 0, W, 54, KitData.Box.CLOUD);
        c.sprite(10 + (32 - KitData.Sprite.HB_INSEL.width) / 2, 11, KitData.Sprite.HB_INSEL);
        c.headline(50, 12, title, SKY);
        c.text(50, 36, sub, MIST, false);
        int y = 60, cw = (W - 4) / 2, ch = 64;
        for (int k = 0; k < choices.size(); k++) {
            Choice ch1 = choices.get(k);
            int x = (k % 2) * (cw + 4), cy = y + (k / 2) * (ch + 4);
            c.box(x, cy, cw, ch, KitData.Box.CLOUD_CARD);
            c.icon(x + 8, cy + 8, ch1.icon(), true);
            c.text(x + 48, cy + 9, Canvas.fit(ch1.name(), true, cw - 56), CLOUD, true);
            List<String> dl = Canvas.wrap(ch1.desc(), cw - 56);
            for (int l = 0; l < Math.min(2, dl.size()); l++) c.text(x + 48, cy + 21 + 10 * l, dl.get(l), MIST, false);
            c.smallButton(x + cw - 8 - 70, cy + ch - 20, 70, KitData.Small.GOLD, "Wählen", GOLD, true);
            c.action(x, cy, cw, ch, ch1.pick(), ch1.name() + " wählen");
        }
        if (choices.isEmpty()) c.text(4, y, "Es gibt noch keine Insel-Vorlage.", MIST, false);
        return c;
    }

    // =================================================================================================================
    // Selbsttest
    // =================================================================================================================

    public static void samples(java.util.function.BiConsumer<String, java.util.function.Supplier<Canvas>> reg) {
        Consumer<Player> no = p -> { };
        String w = "W".repeat(40);
        List<Up> ups = List.of(new Up("Größe", UpgradeTier.icon(KitData.Sprite.HB_GROESSE, 19), 19, 50, "999 x 999", "999 x 999", 999_999_999L, false, true, no),
                new Up("Mitglieder", UpgradeTier.icon(KitData.Sprite.HB_MITGLIEDER, 12), 12, 50, "99 Plätze", "99 Plätze", 999_999_999L, false, false, no),
                new Up("Generator", KitData.Sprite.HB_GENERATOR, 4, 50, "Erze aus 99 Sorten", "Level 99: Diamant, Smaragd, Antiker Schrott, Lapis", 1, false, true, no),
                new Up("Wachstum", UpgradeTier.icon(KitData.Sprite.HB_IS_WACHSTUM, 37), 37, 50, "+200 % Pflanzen", "+200 % schneller wachsen", 9_999, true, true, no),
                new Up("Spawnrate", UpgradeTier.icon(KitData.Sprite.HB_IS_SPAWN, 16), 16, 50, "+100 % Wesen", "+100 % Tiere und Monster", 999_999_999L, false, false, no),
                new Up("Limits", UpgradeTier.icon(KitData.Sprite.HB_IS_LIMITS, 50), 50, 50, "+999 %", null, 0, false, false, null));
        List<Member> ms = new ArrayList<>();
        for (int k = 0; k < 11; k++) ms.add(new Member("W".repeat(16), k == 0, k % 2 == 0, k == 0 ? null : no));
        List<StageRow> st = List.of(new StageRow("Nether-Stufe", StageScreen.NETHER, KitData.Sprite.HB_ST_NETHER, true, "Freigeschaltet", 1,
                        "Hinreisen", no),
                new StageRow("End-Stufe", StageScreen.END, KitData.Sprite.HB_ST_END, false, "99/99 Bedingungen", 0.6, "Ansehen", no));
        reg.accept("Insel", () -> canvas(new View(w, 999_999L, 99_999, 999_999_999L, 999_999_999L, true, true, 999_999_999L, ups, ms, 11,
                no, no, no, no, no, null, no, no, no, no, "Neustart in 23h 59m 59s", no, no, st)));
        reg.accept("Insel Mitglied", () -> canvas(new View("Insel", 0, 0, 0, 100, false, false, 0,
                List.of(new Up("Größe", KitData.Sprite.HB_GROESSE, 0, 50, "31 x 31", null, 0, false, false, null),
                        new Up("Mitglieder", KitData.Sprite.HB_MITGLIEDER, 0, 50, "2 Plätze", null, 0, false, false, null),
                        new Up("Generator", KitData.Sprite.HB_GENERATOR, 0, 50, "Nur Bruchstein", null, 0, false, false, null)),
                List.of(new Member("Luno", true, true, null), new Member("Kuby", false, false, null)), 3,
                no, no, null, no, null, no, no, no, null, null, null)));
        List<TopRow> rows = new ArrayList<>();
        for (int k = 1; k <= 10; k++) rows.add(new TopRow(k, w, "W".repeat(16), 999_999_999L, 999_999_999L, k == 4));
        reg.accept("Insel Top", () -> top(rows, new TopRow(99_999, w, "W".repeat(16), 1, 1, true), no, no, no));
        reg.accept("Insel Top leer", () -> top(List.of(), null, no));
        List<Member> roles = new ArrayList<>();
        roles.add(new Member("W".repeat(16), true, true, null, "Besitzer", null, null));
        roles.add(new Member("W".repeat(16), false, true, no, "Co-Besitzer", "Mitglied", no));
        roles.add(new Member("W".repeat(16), false, false, no, "Mitglied", "Co", no));
        reg.accept("Insel Rollen", () -> canvas(new View(w, 1, 1, 1, 2, true, true, 1, ups, roles, 11, no, no, no, no, no, null, no, no, no, no, null, no)));
        IslandService.Right[] rs = IslandService.Right.values();
        boolean[][] on = new boolean[rs.length][3];
        @SuppressWarnings("unchecked")
        Consumer<Player>[][] tg = new Consumer[rs.length][3];
        List<String> rn = new ArrayList<>();
        for (int r = 0; r < rs.length; r++) {
            rn.add(rs[r].label);
            for (int k = 0; k < 3; k++) {
                on[r][k] = (r + k) % 2 == 0;
                tg[r][k] = r >= 5 && k > 0 ? null : no;
            }
        }
        List<Person> many = new ArrayList<>();
        for (int k = 0; k < 6; k++) many.add(new Person("W".repeat(16), k % 2 == 0, "Rauswerfen", no, "Sperren", no));
        reg.accept("Insel Rechte", () -> access(new AccessView(w, rn, on, tg, many, many, many, no, no, no)));
        reg.accept("Insel Rechte leer", () -> access(new AccessView("Insel", rn, on, tg, List.of(), List.of(), List.of(), no, no, no)));
        List<Choice> cs = new ArrayList<>();
        for (int k = 0; k < 7; k++) cs.add(new Choice(w, "W".repeat(120), KitData.Icon.GRASS_BLOCK, no));
        reg.accept("Insel Vorlagen", () -> choose(cs, "Wähle deine Insel", "Du kannst sie später mit „Neu starten“ wechseln"));
    }
}
