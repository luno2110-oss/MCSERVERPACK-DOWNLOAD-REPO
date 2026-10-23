package de.kuby.kubyplugins;

import de.kuby.kubyplugins.service.JewelStoreService;
import de.kuby.kubyplugins.listener.RegionListener;
import de.kuby.kubyplugins.service.HubSettingsService;
import de.kuby.kubyplugins.service.RegionService;
import de.kuby.kubyplugins.listener.EditListener;
import de.kuby.kubyplugins.service.EditModeService;
import de.kuby.kubyplugins.service.MarkerService;
import de.kuby.kubyplugins.service.CasinoService;
import de.kuby.kubyplugins.service.BlackjackService;
import de.kuby.kubyplugins.listener.BlackjackListener;
import de.kuby.kubyplugins.service.RouletteService;
import de.kuby.kubyplugins.service.SlotsService;
import de.kuby.kubyplugins.service.CrashService;
import de.kuby.kubyplugins.service.CasinoLog;
import de.kuby.kubyplugins.service.JackpotDraw;
import de.kuby.kubyplugins.service.CasinoVip;
import de.kuby.kubyplugins.service.CasinoMusic;
import de.kuby.kubyplugins.listener.CasinoVipListener;
import de.kuby.kubyplugins.service.PokerService;
import de.kuby.kubyplugins.command.PokerCommand;
import de.kuby.kubyplugins.service.VoteService;
import de.kuby.kubyplugins.service.AchievementService;
import de.kuby.kubyplugins.service.UiPackService;
import de.kuby.kubyplugins.command.CasinoCommand;
import de.kuby.kubyplugins.service.ModuleService;
import de.kuby.kubyplugins.listener.WorldBorderListener;
import de.kuby.kubyplugins.service.WorldService;
import de.kuby.kubyplugins.listener.XpSourceListener;
import de.kuby.kubyplugins.service.TeleportEffectService;
import de.kuby.kubyplugins.service.TpaService;
import de.kuby.kubyplugins.service.PlaytimeService;
import de.kuby.kubyplugins.command.KpCommand;
import de.kuby.kubyplugins.listener.ModeListener;
import de.kuby.kubyplugins.service.VisibilityService;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.service.KitService;
import de.kuby.kubyplugins.listener.TotenkisteListener;
import de.kuby.kubyplugins.service.TotenkisteService;
import de.kuby.kubyplugins.gui.QuizVoteMenu;
import de.kuby.kubyplugins.service.ItemValueService;
import de.kuby.kubyplugins.service.EconomyMarketService;
import de.kuby.kubyplugins.service.HotbarDisplay;
import de.kuby.kubyplugins.listener.GuiHoldersListener;
import de.kuby.kubyplugins.service.EnderChestService;
import de.kuby.kubyplugins.service.ShopStorageService;
import de.kuby.kubyplugins.service.ShopService;
import de.kuby.kubyplugins.service.DiscordLink;
import de.kuby.kubyplugins.service.CosmeticService;
import de.kuby.kubyplugins.service.BattlepassService;
import de.kuby.kubyplugins.command.EconomyCommand;
import de.kuby.kubyplugins.command.JewelCommand;
import de.kuby.kubyplugins.command.AdminCommand;
import de.kuby.kubyplugins.command.ExtraCommand;
import de.kuby.kubyplugins.command.HelpCommand;
import de.kuby.kubyplugins.command.CoinflipCommand;
import de.kuby.kubyplugins.listener.QuizListener;
import de.kuby.kubyplugins.service.KeyAllTimer;
import de.kuby.kubyplugins.service.QuizService;
import de.kuby.kubyplugins.command.KubyCommand;
import de.kuby.kubyplugins.command.MarketCommand;
import de.kuby.kubyplugins.command.SocialCommand;
import de.kuby.kubyplugins.command.TeamCommand;
import de.kuby.kubyplugins.gui.GuiListener;
import de.kuby.kubyplugins.listener.TradeListener;
import de.kuby.kubyplugins.service.AbholboxService;
import de.kuby.kubyplugins.service.DailyService;
import de.kuby.kubyplugins.service.EconomyService;
import de.kuby.kubyplugins.service.FriendService;
import de.kuby.kubyplugins.service.HomeService;
import de.kuby.kubyplugins.service.JewelService;
import de.kuby.kubyplugins.service.MarketService;
import de.kuby.kubyplugins.service.MessageService;
import de.kuby.kubyplugins.service.OrderService;
import de.kuby.kubyplugins.service.DailyPotService;
import de.kuby.kubyplugins.service.PromptService;
import de.kuby.kubyplugins.service.RtpQueueService;
import de.kuby.kubyplugins.service.TagService;
import de.kuby.kubyplugins.service.TradeService;
import de.kuby.kubyplugins.service.BoosterService;
import de.kuby.kubyplugins.service.WeeklyService;
import de.kuby.kubyplugins.service.SellService;
import de.kuby.kubyplugins.service.StatsService;
import de.kuby.kubyplugins.service.TeamService;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class KubyPlugins extends JavaPlugin {

    private EconomyService economy;
    private HomeService homes;
    private DailyService daily;
    private TeamService teams;
    private MarketService market;
    private SellService sell;
    private OrderService orders;
    private AbholboxService abholbox;
    private FriendService friends;
    private MessageService messages;
    private StatsService stats;
    private JewelService jewels;
    private PromptService prompts;
    private DailyPotService dailyPot;
    private TagService tags;
    private TradeService trades;
    private RtpQueueService rtpQueue;
    private BoosterService booster;
    private WeeklyService weekly;
    private BattlepassService battlepass;
    private CosmeticService cosmetics;
    private DiscordLink discord;
    private ShopService shop;
    private ShopStorageService shopStorage;
    private EnderChestService enderchests;
    private HotbarDisplay hotbar;
    private EconomyMarketService investments;
    private ItemValueService itemValues;
    private TotenkisteService totenkisten;
    private KitService kits;
    private ModeService modes;
    private de.kuby.kubyplugins.service.CustomItemService customItems;
    private de.kuby.kubyplugins.service.ModelService models;
    private VisibilityService visibility;
    private PlaytimeService playtime;
    private TpaService tpa;
    private TeleportEffectService teleportFx;
    private WorldService worlds;
    private ModuleService modules;
    private MarkerService markers;
    private CasinoService casino;
    private BlackjackService blackjack;
    private RouletteService roulette;
    private SlotsService slots;
    private CrashService crash;
    private CasinoLog casinoLog;
    private JackpotDraw jackpotDraw;
    private CasinoVip casinoVip;
    private CasinoMusic casinoMusic;
    private PokerService poker;
    private VoteService votes;
    private AchievementService achievements;
    private UiPackService uiPack;
    private EditModeService edit;
    private RegionService regions;
    private HubSettingsService hubSettings;
    private JewelStoreService jewelStore;
    private QuizService quiz;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Msg.setPrefix(getConfig().getString("prefix", ""));

        economy = new EconomyService(this);
        homes = new HomeService(this);
        daily = new DailyService(this);
        teams = new TeamService(this);
        market = new MarketService(this);
        sell = new SellService(this);
        orders = new OrderService(this);
        abholbox = new AbholboxService(this);
        friends = new FriendService(this);
        messages = new MessageService(this);
        stats = new StatsService(this);
        prompts = new PromptService(this);
        dailyPot = new DailyPotService(this);
        tags = new TagService(this);
        trades = new TradeService(this);
        rtpQueue = new RtpQueueService(this);
        booster = new BoosterService(this);
        weekly = new WeeklyService(this);
        battlepass = new BattlepassService(this);
        cosmetics = new CosmeticService(this);
        discord = new DiscordLink(this);
        shop = new ShopService(this);
        shopStorage = new ShopStorageService(this);
        enderchests = new EnderChestService(this);
        hotbar = new HotbarDisplay(this);
        investments = new EconomyMarketService(this);
        itemValues = new ItemValueService(this);
        totenkisten = new TotenkisteService(this);
        kits = new KitService(this);
        visibility = new VisibilityService(this);
        modes = new ModeService(this);
        customItems = new de.kuby.kubyplugins.service.CustomItemService(this);
        models = new de.kuby.kubyplugins.service.ModelService(this);
        worlds = new WorldService(this);
        modules = new ModuleService(this);
        markers = new MarkerService(this);
        casino = new CasinoService(this);
        casinoLog = new CasinoLog(this);
        jackpotDraw = new JackpotDraw(this);
        jackpotDraw.start();
        casinoVip = new CasinoVip(this);
        casinoMusic = new CasinoMusic(this);
        poker = new PokerService(this);
        poker.start();
        votes = new VoteService(this);
        achievements = new AchievementService(this);
        achievements.start();
        uiPack = new UiPackService(this);
        uiPack.start();
        // Stat-Quellen für Erfolge (Addons melden weitere an, z. B. Angeln und Claims).
        registerStat("playtime", id -> playtime().minutesOf(id));
        registerStat("votes", id -> votes.votesOf(id));
        registerStat("kills", id -> (long) stats().get(id).kills);
        registerStat("casino_plus", id -> Math.max(0L, casinoLog.netOf(id)));
        registerMainEntry(new MainEntry(org.bukkit.Material.NETHER_STAR, "Erfolge", "Fortschritt und Belohnungen",
                p -> new de.kuby.kubyplugins.gui.AchievementMenu(this, 0).open(p)));
        blackjack = new BlackjackService(this);
        roulette = new RouletteService(this);
        slots = new SlotsService(this);
        crash = new CrashService(this);
        edit = new EditModeService(this);
        regions = new RegionService(this);
        hubSettings = new HubSettingsService(this);
        jewelStore = new JewelStoreService(this);
        // PvP-Schnittstelle. Bis KubySMP da ist, läuft sie über die Dienste im Kern.
        pvp = de.kuby.kubyplugins.pvp.PvpApi.NONE;
        // Prüfungen für den Moduswechsel. Die PvP-Prüfungen (Kampf, Duell, RTP) wandern mit KubySMP.
        // Handel gehört zu beiden Modi und bleibt im Kern.
        modes.registerBusyCheck(p -> trades().inTrade(p.getUniqueId()));
        modes.registerBusyCheck(p -> pvp().isDueling(p.getUniqueId()));
        modes.registerBusyCheck(p -> pvp().inCombat(p.getUniqueId()));
        modes.registerBusyCheck(p -> rtpQueue().inQueue(p.getUniqueId()));
        modes.registerBusyCheck(p -> blackjack.inGame(p.getUniqueId()));
        modes.registerBusyCheck(p -> poker.isSeated(p.getUniqueId()));
        modes.registerExtras(java.util.List.of(homes, battlepass, cosmetics, kits, tags, daily, investments, shopStorage));
        playtime = new PlaytimeService(this);
        tpa = new TpaService(this);
        teleportFx = new TeleportEffectService(this);
        jewels = new JewelService(this);
        quiz = new QuizService(this);

        KubyCommand kuby = new KubyCommand(this);
        for (String name : List.of("befehle", "balance", "pay", "baltop", "shop",
                "sethome", "home", "homes", "delhome", "daily", "rtp", "spawn")) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(kuby);
                cmd.setTabCompleter(kuby);
            }
        }
        setAll(new TeamCommand(this), "team", "tc");
        setAll(new MarketCommand(this), "markt");
        setAll(new EconomyCommand(this), "sell", "worth", "sellmulti", "orders", "abholbox", "abholen");
        setAll(new SocialCommand(this), "friend", "friends", "msg", "r", "inbox", "bounty", "bounties",
                "stats", "trophaeen", "ranking");
        setAll(new JewelCommand(this), "juwelen", "juwelenshop");
        setAll(new CoinflipCommand(this), "coinflip");
        setAll(new CasinoCommand(this), "casino");
        setAll(new PokerCommand(this), "poker");
        de.kuby.kubyplugins.command.RulesCommand rules = new de.kuby.kubyplugins.command.RulesCommand(this);
        setAll(rules, "regeln", "report");
        for (String n : List.of("regeln", "report")) {
            PluginCommand rc = getCommand(n);
            if (rc != null) rc.setTabCompleter(rules);
        }
        PluginCommand hilfe = getCommand("hilfe");
        if (hilfe != null) {
            hilfe.setExecutor((sender, c, label, args) -> {
                if (!(sender instanceof org.bukkit.entity.Player pl)) return true;
                if (!de.kuby.kubyplugins.service.SurvivalHelp.open(this, pl, args.length > 0 ? String.join(" ", args) : null)) {
                    de.kuby.kubyplugins.util.Msg.send(pl, "<red>Diese Seite gibt es nicht. <gray>Seiten: "
                            + String.join(", ", de.kuby.kubyplugins.service.SurvivalHelp.pageIds()));
                }
                return true;
            });
            hilfe.setTabCompleter((sender, c, label, args) -> {
                if (args.length != 1) return List.of();
                List<String> out = new java.util.ArrayList<>();
                for (String id : de.kuby.kubyplugins.service.SurvivalHelp.pageIds()) if (id.startsWith(args[0].toLowerCase())) out.add(id);
                return out;
            });
        }
        setAll((sender, command, label, args) -> {
            if (sender instanceof org.bukkit.entity.Player p) votes.showLinks(p);
            return true;
        }, "vote");
        setAll((sender, command, label, args) -> {
            if (sender instanceof org.bukkit.entity.Player p) new de.kuby.kubyplugins.gui.AchievementMenu(this, 0).open(p);
            return true;
        }, "erfolge");
        registerKpSub("vote", (p, args) -> {
            if (!p.hasPermission("kuby.admin")) return;
            if (args.length < 2) {
                Msg.send(p, "<red>Nutzung: /kp vote <Spieler> (simuliert einen Vote)");
                return;
            }
            votes.handle(args[1], "Test (" + p.getName() + ")");
        });
        setAll((sender, command, label, args) -> {
            if (sender instanceof org.bukkit.entity.Player p) casinoMusic.openMenu(p);
            return true;
        }, "music");
        setAll(new AdminCommand(this), "kadmin");
        setAll(new HelpCommand(this), "befehle", "menu");
        setAll(new ExtraCommand(this), "dailypot", "trade", "tags", "rtpqueue", "meta", "booster", "xpshop", "battlepass", "cosmetics", "turnier", "verify", "versichern", "enderchest", "kristaltrade", "investments", "quizvote", "totenkiste", "kits", "hub", "tpa", "tpaccept", "tpadeny", "spielzeit");
        setAll(new KpCommand(this), "kp");
        getServer().getPluginManager().registerEvents(new TradeListener(this), this);
        getServer().getPluginManager().registerEvents(new QuizListener(this), this);
        new KeyAllTimer(this).start();

        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new GuiHoldersListener(this), this);
        getServer().getPluginManager().registerEvents(new TotenkisteListener(this), this);
        getServer().getPluginManager().registerEvents(new ModeListener(this), this);
        getServer().getPluginManager().registerEvents(new XpSourceListener(this), this);
        getServer().getPluginManager().registerEvents(new de.kuby.kubyplugins.listener.ModelListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldBorderListener(this), this);
        getServer().getPluginManager().registerEvents(new EditListener(this), this);
        getServer().getPluginManager().registerEvents(new RegionListener(this), this);
        getServer().getPluginManager().registerEvents(new BlackjackListener(this), this);
        getServer().getPluginManager().registerEvents(new CasinoVipListener(this), this);
        getServer().getPluginManager().registerEvents(votes, this);
        getServer().getPluginManager().registerEvents(uiPack, this);
        getServer().getScheduler().runTaskLater(this, () -> {
            markers.respawnAll();
            casino.respawn();
            blackjack.respawn();
            roulette.respawn();
            slots.respawn();
            crash.respawn();
            poker.respawn();
            votes.hook();
            hubSettings.applyAll();
        }, 40L);

        // Autosave alle 5 Minuten, synchron, damit die Maps nicht parallel verändert werden
        getServer().getScheduler().runTaskTimer(this, this::saveAll, 6000L, 6000L);
    }

    private void setAll(CommandExecutor executor, String... names) {
        for (String name : names) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) cmd.setExecutor(executor);
        }
    }

    @Override
    public void onDisable() {
        blackjack.shutdown();
        roulette.shutdown();
        slots.shutdown();
        crash.shutdown();
        poker.shutdown();
        saveAll();
    }

    public void saveAll() {
        economy.save();
        homes.save();
        daily.save();
        teams.save();
        market.save();
        sell.save();
        orders.save();
        abholbox.save();
        friends.save();
        stats.save();
        jewels.save();
        dailyPot.save();
        investments.saveNow();
        totenkisten.saveNow();
        modes.save();
        playtime.save();
        regions.save();
        casino.save();
        casinoLog.save();
        jackpotDraw.save();
        casinoVip.save();
        votes.save();
        achievements.save();
        homes.save();
        shop.save();
        shopStorage.save();
        tags.save();
        weekly.save();
        battlepass.save();
        cosmetics.save();
    }

    public EconomyService economy() { return economy; }
    public HomeService homes() { return homes; }
    public DailyService daily() { return daily; }
    public TeamService teams() { return teams; }
    public MarketService market() { return market; }
    public SellService sell() { return sell; }
    public OrderService orders() { return orders; }
    public AbholboxService abholbox() { return abholbox; }
    public FriendService friends() { return friends; }
    public MessageService messages() { return messages; }
    public StatsService stats() { return stats; }
    private de.kuby.kubyplugins.pvp.PvpApi pvp;
    public de.kuby.kubyplugins.pvp.PvpApi pvp() { return pvp; }
    /** Von KubySMP aufgerufen, sobald es die PvP-Schnittstelle übernimmt. */
    public void setPvp(de.kuby.kubyplugins.pvp.PvpApi api) { pvp = api; }
    public JewelService jewels() { return jewels; }
    public PromptService prompts() { return prompts; }
    public DailyPotService dailyPot() { return dailyPot; }
    public TagService tags() { return tags; }
    public TradeService trades() { return trades; }
    public RtpQueueService rtpQueue() { return rtpQueue; }
    public BoosterService booster() { return booster; }
    public WeeklyService weekly() { return weekly; }
    public BattlepassService battlepass() { return battlepass; }
    public CosmeticService cosmetics() { return cosmetics; }
    public DiscordLink discord() { return discord; }
    public ShopService shop() { return shop; }
    public ShopStorageService shopStorage() { return shopStorage; }
    public EnderChestService enderchests() { return enderchests; }
    public EconomyMarketService investments() { return investments; }
    public ItemValueService itemValues() { return itemValues; }
    public TotenkisteService totenkisten() { return totenkisten; }
    public KitService kits() { return kits; }
    public ModeService modes() { return modes; }
    public de.kuby.kubyplugins.service.CustomItemService customItems() { return customItems; }
    public de.kuby.kubyplugins.service.ModelService models() { return models; }

    /** Werte für das Scoreboard, die ein Addon bereitstellt (z. B. Claimblöcke). */
    private final java.util.Map<String, java.util.function.Function<java.util.UUID, Long>> statProviders = new java.util.HashMap<>();
    public void registerStat(String key, java.util.function.Function<java.util.UUID, Long> fn) { statProviders.put(key, fn); }

    /** Vorteile, die Addons vergeben (z. B. "farmpass"). Der Kern fragt sie bei Steuern, Homes usw. ab. */
    private final java.util.Map<String, java.util.function.Predicate<java.util.UUID>> perks = new java.util.concurrent.ConcurrentHashMap<>();

    public void registerPerk(String key, java.util.function.Predicate<java.util.UUID> check) { perks.put(key, check); }

    public boolean hasPerk(String key, java.util.UUID id) {
        java.util.function.Predicate<java.util.UUID> c = perks.get(key);
        return c != null && c.test(id);
    }

    /** Zeichen vor dem Namen in Chat und Tab (MiniMessage), z. B. das grüne Blatt des Farmpasses. */
    private final java.util.List<java.util.function.Function<java.util.UUID, String>> namePrefixes = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void registerNamePrefix(java.util.function.Function<java.util.UUID, String> fn) { namePrefixes.add(fn); }

    public String namePrefix(java.util.UUID id) {
        StringBuilder sb = new StringBuilder();
        for (java.util.function.Function<java.util.UUID, String> f : namePrefixes) {
            String s = f.apply(id);
            if (s != null && !s.isEmpty()) sb.append(s);
        }
        return sb.toString();
    }

    /** Tab-Namen mit Präfix aktualisieren. */
    public void refreshTabName(org.bukkit.entity.Player p) {
        String prefix = namePrefix(p.getUniqueId());
        p.playerListName(prefix.isEmpty() ? null : de.kuby.kubyplugins.util.Msg.parse(prefix).append(net.kyori.adventure.text.Component.text(p.getName())));
    }

    /** Texte von Addons für Anzeigen (z. B. der aktuelle Job). Leer, wenn kein Addon den Wert liefert. */
    private final java.util.Map<String, java.util.function.Function<java.util.UUID, String>> textProviders = new java.util.HashMap<>();
    public void registerText(String key, java.util.function.Function<java.util.UUID, String> fn) { textProviders.put(key, fn); }
    public String textValue(String key, java.util.UUID id) {
        java.util.function.Function<java.util.UUID, String> fn = textProviders.get(key);
        if (fn == null) return "";
        try {
            String v = fn.apply(id);
            return v == null ? "" : v;
        } catch (RuntimeException e) {
            return "";
        }
    }
    public boolean hasStat(String key) { return statProviders.containsKey(key); }
    public long statValue(String key, java.util.UUID id) {
        java.util.function.Function<java.util.UUID, Long> f = statProviders.get(key);
        return f == null ? 0L : f.apply(id);
    }

    /** Vertrauensprüfung für Claims (Owner, Spieler). Standard: niemand ist vertraut. */
    private java.util.function.BiPredicate<String, java.util.UUID> trustCheck = (owner, who) -> false;
    public void setTrustCheck(java.util.function.BiPredicate<String, java.util.UUID> check) { trustCheck = check; }
    public boolean isTrusted(String owner, java.util.UUID who) { return trustCheck.test(owner, who); }

    /** Unterbefehle für /kp, die ein Addon bereitstellt. */
    private final java.util.Map<String, java.util.function.BiConsumer<org.bukkit.entity.Player, String[]>> kpSubs = new java.util.HashMap<>();
    public void registerKpSub(String name, java.util.function.BiConsumer<org.bukkit.entity.Player, String[]> handler) { kpSubs.put(name, handler); }
    public java.util.function.BiConsumer<org.bukkit.entity.Player, String[]> kpSub(String name) { return kpSubs.get(name); }

    /** Eintrag im Hauptmenü, den ein Addon hinzufügt. */
    public record MainEntry(org.bukkit.Material icon, String name, String desc,
                            java.util.function.Consumer<org.bukkit.entity.Player> action) {}

    /** Knopf im Admin-Menü an einem festen Platz, den ein Addon hinzufügt. */
    public record AdminButton(int slot, org.bukkit.Material icon, String name, String desc,
                              java.util.function.Consumer<org.bukkit.entity.Player> action) {}

    private final java.util.List<MainEntry> mainEntries = new java.util.ArrayList<>();
    private final java.util.List<AdminButton> adminButtons = new java.util.ArrayList<>();

    public void registerMainEntry(MainEntry e) { mainEntries.add(e); }
    public void registerAdminButton(AdminButton b) { adminButtons.add(b); }
    public java.util.List<MainEntry> mainEntries() { return mainEntries; }
    public java.util.List<AdminButton> adminButtons() { return adminButtons; }

    /** Anzeige des Kontostands (Hotbar). Wird vom Addon KubyScoreboard überschrieben. */
    private java.util.function.Function<org.bukkit.entity.Player, String> moneyProvider;

    public void setMoneyProvider(java.util.function.Function<org.bukkit.entity.Player, String> f) { moneyProvider = f; }

    public String moneyText(org.bukkit.entity.Player p) {
        if (moneyProvider != null) return moneyProvider.apply(p);
        return "<green>" + String.format(java.util.Locale.GERMANY, "%,d", economy.balance(p.getUniqueId())) + "$";
    }
    public PlaytimeService playtime() { return playtime; }
    public TpaService tpa() { return tpa; }
    public WorldService worlds() { return worlds; }
    public ModuleService modules() { return modules; }
    public MarkerService markers() { return markers; }
    public CasinoService casino() { return casino; }
    public BlackjackService blackjack() { return blackjack; }
    public RouletteService roulette() { return roulette; }
    public SlotsService slots() { return slots; }
    public CrashService crash() { return crash; }
    public CasinoLog casinoLog() { return casinoLog; }
    public JackpotDraw jackpotDraw() { return jackpotDraw; }
    public CasinoVip casinoVip() { return casinoVip; }
    public CasinoMusic casinoMusic() { return casinoMusic; }
    public PokerService poker() { return poker; }
    public VoteService votes() { return votes; }
    public AchievementService achievements() { return achievements; }
    public UiPackService uiPack() { return uiPack; }

    /** Addons können die Geld-/XP-Anzeige über der Hotbar für einzelne Spieler ausblenden (z. B. Survival-Leiste oben). */
    private java.util.function.Predicate<org.bukkit.entity.Player> hotbarHidden = p -> false;
    public void setHotbarHidden(java.util.function.Predicate<org.bukkit.entity.Player> check) { hotbarHidden = check; }
    public boolean hotbarHidden(org.bukkit.entity.Player p) { return hotbarHidden.test(p); }
    public EditModeService edit() { return edit; }
    public RegionService regions() { return regions; }
    public HubSettingsService hubSettings() { return hubSettings; }
    public JewelStoreService jewelStore() { return jewelStore; }
    public TeleportEffectService teleportFx() { return teleportFx; }
    public VisibilityService visibility() { return visibility; }
    public QuizService quiz() { return quiz; }
}
