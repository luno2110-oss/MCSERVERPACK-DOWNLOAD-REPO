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
                service.zoneTeleport() != null, a.rods.size(), service.world() != null && service.world().world() != null),
                this));
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
                service.priceMin(p.getUniqueId(), s), service.priceMax(p.getUniqueId(), s),
                service.bonusPercent(p.getUniqueId(), s), s.water() == Water.MYTHOS), this));
    }

    public void openRod(Player p) {
        UUID id = p.getUniqueId();
        AngelProfile a = service.profile(id);
        int[] levels = new int[Upgrade.values().length];
        for (Upgrade u : Upgrade.values()) levels[u.ordinal()] = a.upgrade(u);
        show(p, AngelScreens.rod(new AngelScreens.RodView(service.baseLuck(id) + Math.max(0, service.jobBonus(id)),
                4.0 * a.upgrade(Upgrade.SCHNELLSPULE), 1.2 + 0.5 * a.upgrade(Upgrade.SCHATZSINN), levels, a.autoSell,
                service.autoSellPrice(), armedFor(p), core.economy().balance(id), addon.jobs().worker(id).tokens, a.autoSellAll,
                a.autoSellAllOn, service.autoSellAllPrice(), core.jewels().balance(id), service.boostLeft(id), service.boostPrice(),
                service.boostMinutes(), service.passOwner(id)), this));
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
        show(p, AngelScreens.baits(new AngelScreens.BaitView(counts, a.active, can, missing, armedFor(p), service.passOwner(p.getUniqueId())), this));
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

    public void openShop(Player p, int tab) {
        if (!survival(p)) return;
        UUID id = p.getUniqueId();
        AngelProfile a = service.profile(id);
        int n = Rod.values().length;
        boolean[] owned = new boolean[n], carried = new boolean[n];
        for (Rod r : a.rods) owned[r.ordinal()] = true;
        for (org.bukkit.inventory.ItemStack it : p.getInventory().getContents()) {
            Rod r = AngelItems.rodOf(it);
            if (r != null) carried[r.ordinal()] = true;
        }
        show(p, AngelScreens.shop(new AngelScreens.ShopView(tab, owned, carried, AngelService.rodOf(p), core.economy().balance(id),
                core.jewels().balance(id), armedFor(p), service.passOwner(id)), this));
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

    /** Angelwelt: alle Gewässer mit Boni und Voraussetzungen (✓/✗ für diesen Spieler). */
    public void openWorld(Player p) {
        AngelWorld w = service.world();
        AngelProfile a = service.profile(p.getUniqueId());
        List<AngelScreens.SpotCard> cards = new ArrayList<>();
        AngelWorld.Spot here = w == null ? null : w.islandAt(p.getLocation());
        if (w != null) {
            for (AngelWorld.Spot s : w.spots()) {
                List<AngelScreens.SpotReq> reqs = new ArrayList<>();
                reqs.add(new AngelScreens.SpotReq(AngelWorld.rodText(s), w.rodOk(p, s)));
                String second = (s.bait != null ? s.bait.label + " · " : "") + "Level " + s.level;
                reqs.add(new AngelScreens.SpotReq(second, w.baitOk(p, s) && w.levelOk(p, s)));
                String water = s.extras().isEmpty() ? s.waterLabel() : s.waterLabel() + " · " + s.extras();
                cards.add(new AngelScreens.SpotCard(s.id, s.name, s.color, water, FishSpecies.byId(s.iconFish),
                        s.bonus(), reqs, w.open(p, s), s == here));
            }
        }
        show(p, AngelScreens.world(new AngelScreens.WorldView(cards, a.level(), w != null && w.world() != null), this));
    }

    @Override
    public Consumer<Player> world() {
        return nav(this::openWorld);
    }

    @Override
    public Consumer<Player> travel(String spot) {
        return p -> {
            AngelWorld w = service.world();
            if (w == null) {
                err(p, "Die Angelwelt ist ausgeschaltet.");
                return;
            }
            AngelWorld.Spot s = spot == null ? null : w.spot(spot);
            if (s != null && !w.open(p, s)) {
                err(p, "Dafür fehlt dir noch: " + w.missing(p, s));
                openWorld(p);
                return;
            }
            de.kuby.kubyplugins.ui.UiDialog.close(p);
            String e = w.travel(p, s);
            if (e != null) {
                err(p, e);
                return;
            }
            Sfx.play(core, p, Sfx.Kind.TELEPORT);
            Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <white>" + (s == null ? "Willkommen in der <aqua>Angelwelt<white>! "
                    + "<gray>Stege führen zu allen Gewässern." : "Am <aqua>" + Msg.escape(s.name) + "<white>. <gray>" + s.allBonus()));
        };
    }

    @Override
    public Consumer<Player> toZone() {
        return p -> {
            Location to = service.zoneTeleport();
            if (to == null) {
                err(p, "Es gibt noch keine Angel-Zone.");
                return;
            }
            de.kuby.kubyplugins.ui.UiDialog.close(p);
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

    // =================================================================================================================
    // Fisch-Markt
    // =================================================================================================================

    @Override
    public Consumer<Player> market() {
        return p -> openMarket(p, 0);
    }

    public void openMarket(Player p, int page) {
        if (!survival(p)) return;
        UUID id = p.getUniqueId();
        AngelMarket m = service.market();
        List<AngelMarket.Listing> all = m.all();
        int pages = Math.max(1, (all.size() + AngelMarketScreen.PER_PAGE - 1) / AngelMarketScreen.PER_PAGE);
        int pg = Math.max(0, Math.min(pages - 1, page));
        List<AngelMarketScreen.Card> cards = new ArrayList<>();
        for (AngelMarket.Listing l : all.subList(pg * AngelMarketScreen.PER_PAGE, Math.min(all.size(), (pg + 1) * AngelMarketScreen.PER_PAGE))) {
            AngelItems.FishItem f = AngelItems.read(l.item());
            if (f == null) continue;
            cards.add(new AngelMarketScreen.Card(l.id(), f.species(), f.stars(), f.variant(), f.kg(), f.mult(), f.odds(), l.sellerName(),
                    l.price(), l.seller().equals(id)));
        }
        String armedKey = armedFor(p);
        show(p, AngelMarketScreen.canvas(new AngelMarketScreen.View(cards, pg, pages, core.jewels().balance(id), (int) m.countOf(id), armedKey),
                marketActions(pg)));
    }

    private AngelMarketScreen.Actions marketActions(int page) {
        return new AngelMarketScreen.Actions() {
            @Override
            public Consumer<Player> back() {
                return AngelMenus.this.main();
            }

            @Override
            public Consumer<Player> buy(String id) {
                return p -> {
                    if (!confirm(p, "buy:" + id)) {
                        openMarket(p, page);
                        return;
                    }
                    String e = service.market().buy(p, id);
                    if (e != null) err(p, e);
                    else {
                        Sfx.play(core, p, Sfx.Kind.SUCCESS_BIG);
                        Msg.send(p, "<aqua><b>FISCH-MARKT</b> <dark_gray>▶ <green>Gekauft! <gray>Der Fisch ist in deinem Inventar.");
                    }
                    openMarket(p, page);
                };
            }

            @Override
            public Consumer<Player> cancel(String id) {
                return p -> {
                    String e = service.market().cancel(p, id);
                    if (e != null) err(p, e);
                    else Sfx.play(core, p, Sfx.Kind.CLICK);
                    openMarket(p, page);
                };
            }

            @Override
            public Consumer<Player> page(int pg) {
                return p -> openMarket(p, pg);
            }
        };
    }

    // =================================================================================================================
    // Angelpass
    // =================================================================================================================

    private final Map<UUID, Integer> passPages = new HashMap<>();

    @Override
    public Consumer<Player> pass() {
        return p -> openPass(p, -1);
    }

    /** Angelpass-Dialog; page -1 = Seite der nächsten Stufe. */
    public void openPass(Player p, int page) {
        if (!survival(p)) return;
        UUID id = p.getUniqueId();
        AngelPass ap = service.pass();
        int level = ap.level(id);
        if (page < 0) page = passPages.getOrDefault(id, Math.min(level, AngelPass.MAX - 1) / AngelPassScreen.PER_PAGE);
        passPages.put(id, page);
        boolean[] claimed = new boolean[AngelPass.MAX];
        for (int i = 1; i <= AngelPass.MAX; i++) claimed[i - 1] = ap.claimed(id, i);
        AngelPassScreen.View v = new AngelPassScreen.View(ap.owned(id), level, ap.xp(id), ap.perLevel(), ap.claimable(id), ap.price(),
                ap.levelPrice(level), core.jewels().balance(id), page, claimed, armedFor(p));
        show(p, AngelPassScreen.canvas(v, passActions()));
    }

    private AngelPassScreen.Actions passActions() {
        return new AngelPassScreen.Actions() {
            @Override
            public Consumer<Player> back() {
                return AngelMenus.this.main();
            }

            @Override
            public Consumer<Player> buy() {
                return p -> {
                    if (!confirm(p, "pass")) {
                        openPass(p, -1);
                        return;
                    }
                    String e = service.pass().buy(p);
                    if (e != null) {
                        err(p, e);
                    } else {
                        Sfx.play(core, p, Sfx.Kind.EPIC);
                        p.showTitle(net.kyori.adventure.title.Title.title(Msg.parse("<gradient:#FF8AD8:#FFD25A><b>ANGELPASS</b></gradient>"),
                                Msg.parse("<gray>+150 % Gewinn · 2x schneller · 2x Glück · -50 % Preise")));
                        Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <light_purple>Angelpass aktiv! <gray>Jeder Fang bringt jetzt "
                                + "Pass-Punkte. Belohnungen: <white>/angeln pass");
                    }
                    openPass(p, -1);
                };
            }

            @Override
            public Consumer<Player> buyLevel() {
                return p -> {
                    if (!confirm(p, "level")) {
                        openPass(p, -1);
                        return;
                    }
                    String e = service.pass().buyLevel(p);
                    if (e != null) err(p, e);
                    else Sfx.play(core, p, Sfx.Kind.LEVEL_UP);
                    openPass(p, -1);
                };
            }

            @Override
            public Consumer<Player> claim() {
                return p -> {
                    List<String> got = service.pass().claimAll(p);
                    if (got.isEmpty()) {
                        err(p, "Gerade gibt es nichts abzuholen.");
                    } else {
                        Sfx.play(core, p, Sfx.Kind.SUCCESS_BIG);
                        Msg.send(p, "<aqua><b>ANGELPASS</b> <dark_gray>▶ <green>Abgeholt: <white>" + Msg.escape(String.join(", ", got)));
                    }
                    openPass(p, -1);
                };
            }

            @Override
            public Consumer<Player> page(int page) {
                return p -> openPass(p, page);
            }
        };
    }

    @Override
    public Consumer<Player> buyAutoSellAll() {
        return p -> {
            if (!confirm(p, "autosellall")) {
                openRod(p);
                return;
            }
            String e = service.buyAutoSellAll(p);
            if (e != null) {
                err(p, e);
            } else {
                Sfx.play(core, p, Sfx.Kind.SUCCESS_BIG);
                Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <green>Auto-Verkauf Plus aktiv: <white>alle Fische gehen sofort an den "
                        + "Händler (außer Silber, Gold und Diamant). <gray>Ausschalten unter Rute & Upgrades.");
            }
            openRod(p);
        };
    }

    @Override
    public Consumer<Player> toggleAutoSellAll() {
        return p -> {
            boolean on = service.toggleAutoSellAll(p.getUniqueId());
            Sfx.play(core, p, Sfx.Kind.CLICK);
            Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <gray>Auto-Verkauf Plus " + (on ? "<green>an" : "<red>aus") + "<gray>.");
            openRod(p);
        };
    }

    @Override
    public Consumer<Player> buyBoost() {
        return p -> {
            if (!confirm(p, "boost")) {
                openRod(p);
                return;
            }
            String e = service.buyBoost(p);
            if (e != null) {
                err(p, e);
            } else {
                Sfx.play(core, p, Sfx.Kind.EPIC);
                Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <gold>Angelboost aktiv: <white>+400 % Glück <gray>für noch "
                        + AngelText.duration(service.boostLeft(p.getUniqueId())) + ".");
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
            new de.kuby.kubysurvival.jobs.JobScreen(addon).open(p, de.kuby.kubysurvival.jobs.JobScreen.Tab.SHOP);
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
    public Consumer<Player> shop(int tab) {
        return nav(p -> openShop(p, tab));
    }

    @Override
    public Consumer<Player> rodAction(Rod r, String action) {
        int tab = r.jewels ? 1 : 0;
        return p -> {
            String e;
            if (action.equals("buy")) {
                if (!confirm(p, "rod:" + r.id)) {
                    openShop(p, tab);
                    return;
                }
                e = service.buyRod(p, r);
                if (e == null) {
                    Sfx.play(core, p, r.jewels ? Sfx.Kind.SUCCESS_BIG : Sfx.Kind.LEVEL_UP);
                    Msg.send(p, "<aqua><b>ANGELN</b> <dark_gray>▶ <green>" + r.label + " gekauft! <gray>Sie ist in deinem Inventar. "
                            + "<#F2DA90>" + Msg.escape(r.perk));
                }
            } else {
                e = service.takeRod(p, r);
                if (e == null) Sfx.play(core, p, Sfx.Kind.CLICK);
            }
            if (e != null) err(p, e);
            openShop(p, tab);
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
