package de.kuby.kubyskyblock;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyskyblock.command.SkyblockCommand;
import de.kuby.kubyskyblock.listener.SkyblockListener;
import de.kuby.kubyskyblock.service.IslandService;
import de.kuby.kubyskyblock.service.LootService;
import de.kuby.kubyskyblock.service.OneBlockService;
import de.kuby.kubyskyblock.service.ProgressService;
import de.kuby.kubyskyblock.listener.ProgressListener;
import de.kuby.kubyskyblock.command.SkyblockPlayerCommand;
import de.kuby.kubyskyblock.service.StreakService;
import de.kuby.kubyskyblock.service.TemplateService;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Skyblock-Modus: Inseln mit Mitgliedern, Upgrades, Level und Schutz (/insel), Vorlagen, Loot-Inseln, One-Block,
 * Multiplikator-Serie, Checkliste und Erfolge (/skyblock). Braucht KubyCore.
 */
public final class KubySkyblock extends JavaPlugin {

    private static KubySkyblock instance;
    private KubyPlugins core;
    private IslandService islands;
    private StreakService streaks;
    private TemplateService templates;
    private LootService loot;
    private OneBlockService oneblock;
    private ProgressService progress;
    private de.kuby.kubyskyblock.service.GeneratorService generators;
    private SkyblockCommand command;
    private final Map<UUID, Location[]> corners = new HashMap<>();

    public static KubySkyblock get() { return instance; }
    public KubyPlugins core() { return core; }
    public IslandService islands() { return islands; }
    public StreakService streaks() { return streaks; }
    public TemplateService templates() { return templates; }
    public LootService loot() { return loot; }
    public OneBlockService oneblock() { return oneblock; }
    private de.kuby.kubyskyblock.service.SkyShopService shop;
    public de.kuby.kubyskyblock.service.SkyShopService shop() { return shop; }
    private de.kuby.kubyskyblock.service.BiomeIslandService biomes;
    private de.kuby.kubyskyblock.service.LimitService limits;
    private de.kuby.kubyskyblock.service.TopRewardService rewards;
    public de.kuby.kubyskyblock.service.BiomeIslandService biomes() { return biomes; }
    public de.kuby.kubyskyblock.service.LimitService limits() { return limits; }
    public de.kuby.kubyskyblock.service.TopRewardService rewards() { return rewards; }
    public ProgressService progress() { return progress; }
    public de.kuby.kubyskyblock.service.GeneratorService generators() { return generators; }
    public SkyblockCommand command() { return command; }

    /** Ecken, die ein Admin mit dem Werkzeug markiert hat. */
    public Location[] templateCorners(UUID id) {
        Location[] c = corners.get(id);
        return (c == null || c[0] == null || c[1] == null) ? null : c;
    }

    public void setCorner(UUID id, int which, Location l) {
        corners.computeIfAbsent(id, k -> new Location[2])[which - 1] = l;
    }

    @Override
    public void onEnable() {
        if (!(getServer().getPluginManager().getPlugin("KubyCore") instanceof KubyPlugins c)) {
            getLogger().severe("KubyCore fehlt oder ist nicht geladen. KubySkyblock wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        instance = this;
        core = c;
        de.kuby.kubyskyblock.gui.SkyScreen.samples(core::registerSelfTestCanvas);
        de.kuby.kubyskyblock.gui.IslandScreen.samples(core::registerSelfTestCanvas);
        de.kuby.kubyskyblock.gui.GeneratorScreen.samples(core::registerSelfTestCanvas);
        de.kuby.kubyskyblock.gui.OneBlockScreen.samples(core::registerSelfTestCanvas);
        de.kuby.kubyskyblock.gui.SkyShopScreen.samples(core::registerSelfTestCanvas);
        de.kuby.kubyskyblock.gui.IslandExtraScreen.samples(core::registerSelfTestCanvas);
        saveDefaultConfig();
        // Upgrades mit 20 Level: alte Standard-Werte der config ersetzen, bevor Inseln und One-Block ihre Stände laden
        de.kuby.kubyskyblock.service.UpgradeLevels.updateConfig(this);
        islands = new IslandService(this, core);
        biomes = new de.kuby.kubyskyblock.service.BiomeIslandService(this, core);
        limits = new de.kuby.kubyskyblock.service.LimitService(this);
        rewards = new de.kuby.kubyskyblock.service.TopRewardService(this, core);
        streaks = new StreakService(this, core);
        templates = new TemplateService(this);
        loot = new LootService(this, core);
        oneblock = new OneBlockService(this, core);
        progress = new ProgressService(this, core);
        generators = new de.kuby.kubyskyblock.service.GeneratorService(this, core);
        shop = new de.kuby.kubyskyblock.service.SkyShopService(this, core);
        core.registerShop(ModeService.SKYBLOCK, new KubyPlugins.ShopHook() {
            @Override
            public void open(org.bukkit.entity.Player p) {
                de.kuby.kubyskyblock.gui.SkyShopScreen.open(p);
            }

            @Override
            public long sellAll(org.bukkit.entity.Player p) {
                return shop.sellAll(p);
            }
        });
        command = new SkyblockCommand(this);

        core.modes().onEnter(ModeService.SKYBLOCK, p -> islands.ensureIsland(p));
        // Werte für Skyblock-Quests (stat:...) und Erfolge
        core.registerStat("island_level", id -> {
            var i = islands.islandOf(id);
            return i == null ? 0L : islands.levelOf(i);
        });
        core.registerStat("island_points", id -> {
            var i = islands.islandOf(id);
            return i == null ? 0L : i.points;
        });
        core.registerStat("island_members", id -> {
            var i = islands.islandOf(id);
            return i == null ? 0L : (long) i.everyone().size();
        });
        core.registerStat("generators", id -> {
            var i = islands.islandOf(id);
            return i == null || generators == null ? 0L : (long) generators.count(i.slot);
        });
        core.registerStat("island_rank", id -> (long) islands.rank(islands.islandOf(id)));
        core.registerStat("generator_slots", id -> {
            var i = islands.islandOf(id);
            return i == null || generators == null ? 0L : (long) generators.slots(i);
        });
        // Texte fürs Scoreboard (schnell: nur Nachschlagen in Maps)
        core.registerText("sb_boost", id -> {
            var i = islands.islandOf(id);
            double[] b = i == null || generators == null ? null : generators.boost(i.slot);
            return b == null ? "" : "x" + de.kuby.kubyskyblock.service.GeneratorService.fmt(b[0]) + " "
                    + de.kuby.kubyplugins.service.KitService.format((long) b[1] - System.currentTimeMillis());
        });
        core.registerText("sb_oneblock", id -> {
            if (oneblock == null) return "";
            var t = oneblock.team(id);
            if (t == null) return "";
            var pf = oneblock.claimOf(t);
            return (pf == null ? "Stufe " : "#" + (pf.index + 1) + " · Stufe ") + oneblock.progress(t).total();
        });
        core.registerKpSub("skyblock", command);
        core.registerMainEntry(ModeService.SKYBLOCK, new KubyPlugins.MainEntry(org.bukkit.Material.GRASS_BLOCK, "Insel",
                "Zuhause, Mitglieder, Upgrades und Level", de.kuby.kubyskyblock.gui.IslandScreen::open));
        core.registerMainEntry(ModeService.SKYBLOCK, new KubyPlugins.MainEntry(org.bukkit.Material.GOLD_INGOT, "Top-Inseln",
                "Die besten Inseln nach Level", de.kuby.kubyskyblock.gui.IslandScreen::top));
        core.registerMainEntry(ModeService.SKYBLOCK, new KubyPlugins.MainEntry(org.bukkit.Material.OAK_SIGN, "Besuchen",
                "Offene Inseln anderer Spieler und Biom-Inseln", de.kuby.kubyskyblock.gui.IslandExtraScreen::visit));
        core.registerMainEntry(ModeService.SKYBLOCK, new KubyPlugins.MainEntry(org.bukkit.Material.BOOK, "Checkliste",
                "Alle Items sammeln, Erfolge und Meilensteine", pl -> new de.kuby.kubyskyblock.gui.SkyScreen(core).open(pl)));
        core.registerMainEntry(ModeService.SKYBLOCK, new KubyPlugins.MainEntry(org.bukkit.Material.BLAST_FURNACE, "Generatoren",
                "Geld, XP und Items von selbst: kaufen, aufstellen, einsammeln", de.kuby.kubyskyblock.gui.GeneratorScreen::island));
        core.registerMainEntry(ModeService.SKYBLOCK, new KubyPlugins.MainEntry(org.bukkit.Material.GRASS_BLOCK, "One-Block",
                "Block fürs Team beanspruchen, Block-XP sammeln, Glück, Tempo und Bonus verbessern", de.kuby.kubyskyblock.gui.OneBlockScreen::open));
        getServer().getPluginManager().registerEvents(new SkyblockListener(this, core), this);
        getServer().getPluginManager().registerEvents(new ProgressListener(this, core), this);
        getServer().getPluginManager().registerEvents(new de.kuby.kubyskyblock.listener.IslandListener(this, core), this);
        getServer().getPluginManager().registerEvents(new de.kuby.kubyskyblock.gui.LootEditMenu.Events(), this);
        getServer().getPluginManager().registerEvents(new de.kuby.kubyskyblock.listener.GeneratorListener(this), this);
        getServer().getPluginManager().registerEvents(new de.kuby.kubyskyblock.listener.LimitListener(this, core), this);
        core.registerJewelShop(ModeService.SKYBLOCK, new KubyPlugins.JewelShopHook() {
            @Override
            public boolean open(org.bukkit.entity.Player p, String sub) {
                if (!core.uiPack().active(p)) return false;
                de.kuby.kubyskyblock.gui.GeneratorScreen.jewels(p);
                return true;
            }

            @Override
            public boolean gift(org.bukkit.entity.Player p, org.bukkit.entity.Player target) {
                return false;
            }
        });
        if (getCommand("skyblock") != null) getCommand("skyblock").setExecutor(new SkyblockPlayerCommand(core));
        var obc = getCommand("oneblock");
        if (obc != null) {
            var oc = new de.kuby.kubyskyblock.command.OneBlockCommand(this, core);
            obc.setExecutor(oc);
            obc.setTabCompleter(oc);
        }
        var insel = getCommand("insel");
        if (insel != null) {
            var ic = new de.kuby.kubyskyblock.command.IslandCommand(this, core);
            insel.setExecutor(ic);
            insel.setTabCompleter(ic);
        }
        getServer().getScheduler().runTaskTimer(this, islands::saveIfDirty, 6000L, 6000L);
        // Preise der Top-Inseln alle 5 Minuten prüfen, Biom-Inseln stündlich (inaktive freigeben)
        getServer().getScheduler().runTaskTimer(this, rewards::tick, 1200L, 6000L);
        getServer().getScheduler().runTaskTimer(this, biomes::tick, 2400L, 72_000L);
        getLogger().info("KubySkyblock aktiv.");
    }

    @Override
    public void onDisable() {
        if (streaks != null) streaks.save();
        if (islands != null) islands.save();
        if (biomes != null) biomes.save();
        if (rewards != null) rewards.save();
        if (loot != null) loot.save();
        if (progress != null) progress.save();
        if (generators != null) {
            generators.removeHolograms();
            generators.save();
        }
        if (oneblock != null) {
            oneblock.removeHolograms();
            oneblock.save();
        }
    }
}
