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
    private SkyblockCommand command;
    private final Map<UUID, Location[]> corners = new HashMap<>();

    public static KubySkyblock get() { return instance; }
    public KubyPlugins core() { return core; }
    public IslandService islands() { return islands; }
    public StreakService streaks() { return streaks; }
    public TemplateService templates() { return templates; }
    public LootService loot() { return loot; }
    public OneBlockService oneblock() { return oneblock; }
    public ProgressService progress() { return progress; }
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
        saveDefaultConfig();
        islands = new IslandService(this, core);
        streaks = new StreakService(this, core);
        templates = new TemplateService(this);
        loot = new LootService(this, core);
        oneblock = new OneBlockService(this, core);
        progress = new ProgressService(this, core);
        command = new SkyblockCommand(this);

        core.modes().onEnter(ModeService.SKYBLOCK, p -> islands.ensureIsland(p));
        core.registerKpSub("skyblock", command);
        core.registerMainEntry(ModeService.SKYBLOCK, new KubyPlugins.MainEntry(org.bukkit.Material.GRASS_BLOCK, "Insel",
                "Zuhause, Mitglieder, Upgrades und Level", de.kuby.kubyskyblock.gui.IslandScreen::open));
        core.registerMainEntry(ModeService.SKYBLOCK, new KubyPlugins.MainEntry(org.bukkit.Material.GOLD_INGOT, "Top-Inseln",
                "Die besten Inseln nach Level", de.kuby.kubyskyblock.gui.IslandScreen::top));
        core.registerMainEntry(ModeService.SKYBLOCK, new KubyPlugins.MainEntry(org.bukkit.Material.BOOK, "Checkliste",
                "Alle Items sammeln, Erfolge und Meilensteine", pl -> new de.kuby.kubyskyblock.gui.SkyScreen(core).open(pl)));
        getServer().getPluginManager().registerEvents(new SkyblockListener(this, core), this);
        getServer().getPluginManager().registerEvents(new ProgressListener(this, core), this);
        getServer().getPluginManager().registerEvents(new de.kuby.kubyskyblock.listener.IslandListener(this, core), this);
        getServer().getPluginManager().registerEvents(new de.kuby.kubyskyblock.gui.LootEditMenu.Events(), this);
        if (getCommand("skyblock") != null) getCommand("skyblock").setExecutor(new SkyblockPlayerCommand(core));
        var insel = getCommand("insel");
        if (insel != null) {
            var ic = new de.kuby.kubyskyblock.command.IslandCommand(this, core);
            insel.setExecutor(ic);
            insel.setTabCompleter(ic);
        }
        getServer().getScheduler().runTaskTimer(this, islands::saveIfDirty, 6000L, 6000L);
        getLogger().info("KubySkyblock aktiv.");
    }

    @Override
    public void onDisable() {
        if (streaks != null) streaks.save();
        if (islands != null) islands.save();
        if (loot != null) loot.save();
        if (progress != null) progress.save();
    }
}
