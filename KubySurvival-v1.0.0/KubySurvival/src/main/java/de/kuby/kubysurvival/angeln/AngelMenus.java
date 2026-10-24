package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.jobs.TokenShopMenu;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Öffnet die Angel-Menüs (AngelScreens) mit den Daten aus dem AngelService und führt die Klicks aus. */
public final class AngelMenus implements AngelScreens.Nav {

    private static final String SURVIVAL = "survival";

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final AngelService service;
    /** Zweimal klicken zum Kaufen: was zuletzt angeklickt wurde und wann. */
    private final Map<UUID, String> armed = new HashMap<>();
    private final Map<UUID, Long> armedAt = new HashMap<>();

    public AngelMenus(KubySurvival addon, KubyPlugins core, AngelService service) {
        this.addon = addon;
        this.core = core;
        this.service = service;
    }

    private void show(Player p, Canvas c) {
        UiDialog.show(p, c, AngelScreens.HEIGHT);
    }

    private boolean survival(Player p) {
        if (SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return true;
        Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <gray>Angeln gibt es nur in Survival.");
        return false;
    }

    private String armedFor(Player p) {
        Long at = armedAt.get(p.getUniqueId());
        if (at == null || System.currentTimeMillis() - at > 5000) return null;
        return armed.get(p.getUniqueId());
    }

    /** true beim zweiten Klick auf dasselbe innerhalb von 5 Sekunden, sonst wird scharf gestellt. */
    private boolean confirm(Player p, String what) {
        if (what.equals(armedFor(p))) {
            armed.remove(p.getUniqueId());
            armedAt.remove(p.getUniqueId());
            return true;
        }
        armed.put(p.getUniqueId(), what);
        armedAt.put(p.getUniqueId(), System.currentTimeMillis());
        Sfx.play(core, p, Sfx.Kind.TICK);
        return false;
    }

    private void err(Player p, String err) {
        Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <red>" + Msg.escape(err));
        Sfx.play(core, p, Sfx.Kind.ERROR);
    }

    // =================================================================================================================
    // Menüs öffnen
    // =================================================================================================================

    public void open(Player p) {
        if (!survival(p)) return;
        UUID id = p.getUniqueId();
        AngelProfile a = service.profile(id);
        int level = a.level();
        long next = AngelService.pointsFor(level + 1);
        long cur = AngelService.pointsFor(level);
        double frac = level >= 100 ? 1 : (double) (a.points - cur) / Math.max(1, next - cur);
        boolean golden = AngelService.goldenActive();
        long goldenSec = golden ? AngelService.goldenSecondsLeft() : AngelService.secondsToGolden();
        Bait bait = a.active != null && a.bait(a.active) > 0 ? a.active : null;
        show(p, AngelScreens.main(new AngelScreens.MainView(a.points, level, AngelService.rank(level),
                Math.max(0, next - a.points), frac, a.catches, a.weekCatches, a.caughtSpecies(), a.earned, a.bestStreak,
                a.treasures, service.jobBonus(id), service.jobLevel(id), AngelService.fishOfDay(), golden, goldenSec,
                service.luck(id, false, false), service.faster(id, false), bait == null ? null : bait.label,
                bait == null ? null : bait.icon, bait == null ? 0 : a.bait(bait),
                service.inventoryValue(p, AngelService.SellMode.ALLES), a.ordersDone(),
                service.zoneTeleport() != null), this));
    }

    public void openBook(Player p, Water w) {
        AngelProfile a = service.profile(p.getUniqueId());
        List<AngelScreens.BookEntry> entries = new ArrayList<>();
        for (FishSpecies s : FishSpecies.of(w)) {
            entries.add(new AngelScreens.BookEntry(s, a.caught.getOrDefault(s.id(), 0), a.best.getOrDefault(s.id(), 0.0)));
        }
        show(p, AngelScreens.book(new AngelScreens.BookView(w, entries, a.caughtSpecies(w), a.pagesDone.contains(w),
                a.caughtSpecies(), core.getConfig().getLong("angeln.page-coins", 3000),
                core.getConfig().getLong("angeln.page-tokens", 2)), this));
    }

    public void openDetail(Player p, FishSpecies s) {
        AngelProfile a = service.profile(p.getUniqueId());
        Map.Entry<UUID, Double> rec = service.recordOf(s.id());
        String recName = rec == null ? null : org.bukkit.Bukkit.getOfflinePlayer(rec.getKey()).getName();
        show(p, AngelScreens.detail(new AngelScreens.DetailView(s, a.caught.getOrDefault(s.id(), 0),
                a.best.getOrDefault(s.id(), 0.0), recName, rec == null ? 0 : rec.getValue(),
                service.price(p.getUniqueId(), s, 1), service.price(p.getUniqueId(), s, 5),
                service.bonusPercent(p.getUniqueId(), s), s.water() == Water.MYTHOS), this));
    }

    public void openRod(Player p) {
        UUID id = p.getUniqueId();
        AngelProfile a = service.profile(id);
        int[] levels = new int[Upgrade.values().length];
        for (Upgrade u : Upgrade.values()) levels[u.ordinal()] = a.upgrade(u);
        show(p, AngelScreens.rod(new AngelScreens.RodView(service.baseLuck(id) + Math.max(0, service.jobBonus(id)),
                4.0 * a.upgrade(Upgrade.SCHNELLSPULE), 1.2 + 0.5 * a.upgrade(Upgrade.SCHATZSINN), levels, a.autoSell,
                service.autoSellPrice(), armedFor(p), core.economy().balance(id), addon.jobs().worker(id).tokens), this));
    }

    public void openBaits(Player p) {
        AngelProfile a = service.profile(p.getUniqueId());
        int n = Bait.values().length;
        int[] counts = new int[n];
        boolean[] can = new boolean[n];
        String[] missing = new String[n];
        for (Bait b : Bait.values()) {
            counts[b.ordinal()] = a.bait(b);
            missing[b.ordinal()] = service.missing(p, b);
            can[b.ordinal()] = missing[b.ordinal()] == null;
        }
        show(p, AngelScreens.baits(new AngelScreens.BaitView(counts, a.active, can, missing, armedFor(p)), this));
    }

    public void openDealer(Player p, String lastSale) {
        if (!survival(p)) return;
        UUID id = p.getUniqueId();
        show(p, AngelScreens.dealer(new AngelScreens.DealerView(service.inventoryValue(p, AngelService.SellMode.ALLES),
                service.budgetLeft(id), service.inventoryValue(p, AngelService.SellMode.KLEINE),
                service.inventoryValue(p, AngelService.SellMode.SELTENE),
                service.inventoryValue(p, AngelService.SellMode.TAGESFISCHE), AngelService.fishOfDay(),
                service.jobBonus(id), AngelService.goldenActive(), lastSale), this));
    }

    public void openOrders(Player p) {
        AngelProfile a = service.profile(p.getUniqueId());
        show(p, AngelScreens.orders(new AngelScreens.OrderView(service.orders(p.getUniqueId()), a.bonusDone,
                AngelService.secondsToNewOrders()), this));
    }

    public void openTop(Player p, String tab) {
        List<AngelService.TopEntry> list = service.top(tab);
        show(p, AngelScreens.top(new AngelScreens.TopView(tab, list, 0, null), this));
    }

    public void openHelp(Player p) {
        show(p, AngelScreens.help(this));
    }

    // =================================================================================================================
    // Nav: Klick-Ziele
    // =================================================================================================================

    private Consumer<Player> nav(Consumer<Player> c) {
        return p -> {
            Sfx.play(core, p, Sfx.Kind.CLICK);
            c.accept(p);
        };
    }

    @Override
    public Consumer<Player> main() {
        return nav(this::open);
    }

    @Override
    public Consumer<Player> book(Water w) {
        return nav(p -> openBook(p, w));
    }

    @Override
    public Consumer<Player> detail(FishSpecies s) {
        return nav(p -> openDetail(p, s));
    }

    @Override
    public Consumer<Player> rod() {
        return nav(this::openRod);
    }

    @Override
    public Consumer<Player> baits() {
        return nav(this::openBaits);
    }

    @Override
    public Consumer<Player> dealer() {
        return nav(p -> openDealer(p, null));
    }

    @Override
    public Consumer<Player> orders() {
        return nav(this::openOrders);
    }

    @Override
    public Consumer<Player> top(String tab) {
        return nav(p -> openTop(p, tab));
    }

    @Override
    public Consumer<Player> help() {
        return nav(this::openHelp);
    }

    @Override
    public Consumer<Player> toZone() {
        return p -> {
            Location to = service.zoneTeleport();
            if (to == null) {
                err(p, "Es gibt noch keine Angel-Zone.");
                return;
            }
            p.closeDialog();
            p.teleportAsync(to).thenAccept(ok -> {
                if (ok) {
                    Sfx.play(core, p, Sfx.Kind.TELEPORT);
                    Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <white>Willkommen in der <aqua>Angel-Zone<white>! "
                            + "<gray>Hier beißt es besser.");
                }
            });
        };
    }

    @Override
    public Consumer<Player> buyUpgrade(Upgrade u) {
        return p -> {
            if (!confirm(p, "up:" + u.name())) {
                openRod(p);
                return;
            }
            String e = service.buyUpgrade(p, u);
            if (e != null) {
                err(p, e);
            } else {
                Sfx.play(core, p, Sfx.Kind.LEVEL_UP);
                Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <green>" + u.label + " auf Stufe "
                        + service.profile(p.getUniqueId()).upgrade(u) + "!");
            }
            openRod(p);
        };
    }

    @Override
    public Consumer<Player> buyAutoSell() {
        return p -> {
            if (!confirm(p, "autosell")) {
                openRod(p);
                return;
            }
            String e = service.buyAutoSell(p);
            if (e != null) {
                err(p, e);
            } else {
                Sfx.play(core, p, Sfx.Kind.SUCCESS_BIG);
                Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <green>Auto-Verkauf aktiv: <white>Fische bis Ungewöhnlich "
                        + "gehen ab jetzt sofort an den Händler.");
            }
            openRod(p);
        };
    }

    @Override
    public Consumer<Player> tokenShop() {
        return nav(p -> {
            p.closeDialog();
            new TokenShopMenu(addon, core).open(p);
        });
    }

    @Override
    public Consumer<Player> bait(Bait b, String action) {
        return p -> {
            String e = null;
            switch (action) {
                case "use" -> {
                    e = service.toggleBait(p, b);
                    if (e == null) Sfx.play(core, p, Sfx.Kind.CLICK);
                }
                case "buy" -> {
                    if (!confirm(p, "buy:" + b.name())) {
                        openBaits(p);
                        return;
                    }
                    e = service.buyBait(p, b);
                    if (e == null) {
                        Sfx.play(core, p, Sfx.Kind.COIN);
                        Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <green>10x " + b.label + " gekauft.");
                    }
                }
                case "craft" -> {
                    e = service.craftBait(p, b);
                    if (e == null) {
                        Sfx.play(core, p, Sfx.Kind.SUCCESS);
                        Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <green>" + b.craftAmount + "x " + b.label + " gebastelt.");
                    }
                }
                default -> {
                }
            }
            if (e != null) err(p, e);
            openBaits(p);
        };
    }

    @Override
    public Consumer<Player> sell(AngelService.SellMode mode) {
        return p -> {
            if (!survival(p)) return;
            AngelService.SaleResult r = service.sell(p, mode);
            String msg;
            if (r.count() == 0) {
                msg = "Keine passenden Fische im Inventar.";
                Sfx.play(core, p, Sfx.Kind.ERROR);
            } else {
                msg = r.count() + (r.count() == 1 ? " Fisch" : " Fische") + " verkauft für " + AngelText.money(r.money())
                        + (r.capped() ? " (Stundenbudget voll)" : "");
                Sfx.play(core, p, Sfx.Kind.COIN);
                Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <green>" + msg);
            }
            openDealer(p, msg);
        };
    }
}
