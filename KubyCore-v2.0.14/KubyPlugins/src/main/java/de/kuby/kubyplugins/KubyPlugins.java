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
        blackjack = new BlackjackService(this);
        roulette = new RouletteService(this);
        slots = new SlotsService(this);
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
        getServer().getScheduler().runTaskLater(this, () -> {
            markers.respawnAll();
            casino.respawn();
            blackjack.respawn();
            roulette.respawn();
            slots.respawn();
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
    public EditModeService edit() { return edit; }
    public RegionService regions() { return regions; }
    public HubSettingsService hubSettings() { return hubSettings; }
    public JewelStoreService jewelStore() { return jewelStore; }
    public TeleportEffectService teleportFx() { return teleportFx; }
    public VisibilityService visibility() { return visibility; }
    public QuizService quiz() { return quiz; }
}
