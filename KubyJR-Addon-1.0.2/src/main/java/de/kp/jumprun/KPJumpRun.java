package de.kp.jumprun;

import de.kp.jumprun.arena.ArenaManager;
import de.kp.jumprun.command.KpCommand;
import de.kp.jumprun.editor.EditorListener;
import de.kp.jumprun.editor.EditorStick;
import de.kp.jumprun.gui.ConfigGui;
import de.kp.jumprun.gui.EditorGui;
import de.kp.jumprun.gui.EditorGuiListener;
import de.kp.jumprun.holo.HoloManager;
import de.kp.jumprun.reward.RewardGui;
import de.kp.jumprun.reward.RewardManager;
import de.kp.jumprun.reward.RewardNpc;
import de.kp.jumprun.run.RunItems;
import de.kp.jumprun.run.RunListener;
import de.kp.jumprun.run.RunManager;
import de.kp.jumprun.stats.StatsManager;
import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class KPJumpRun extends JavaPlugin {

    private static KPJumpRun instance;

    private KubyPlugins kuby;
    private ArenaManager arenaManager;
    private EditorStick editorStick;
    private StatsManager statsManager;
    private RunManager runManager;
    private HoloManager holoManager;
    private EditorGui editorGui;
    private RewardManager rewardManager;
    private RewardNpc rewardNpc;
    private RewardGui rewardGui;
    private ConfigGui configGui;

    @Override
    public void onEnable() {
        instance = this;

        Plugin base = getServer().getPluginManager().getPlugin("KubyCore");
        if (!(base instanceof KubyPlugins kubyPlugins)) {
            getLogger().severe("KubyCore nicht gefunden. KubyJ&R Addon wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        kuby = kubyPlugins;

        saveDefaultConfig();

        arenaManager = new ArenaManager(this);
        arenaManager.load();

        statsManager = new StatsManager(this);
        statsManager.load();

        editorStick = new EditorStick(this);
        editorGui = new EditorGui(this);

        runManager = new RunManager(this, statsManager, new RunItems(this));
        runManager.startTicker();

        // Während eines Runs oder mit dem Editor Stick in der Hand bleibt die Lobby-Steuerung von KubyPlugins aus
        kuby.modes().registerActivity(p -> runManager.get(p.getUniqueId()) != null
                || editorStick.isStick(p.getInventory().getItemInMainHand()));

        holoManager = new HoloManager(this, statsManager);
        holoManager.load();
        holoManager.startTicker();

        rewardManager = new RewardManager(this, statsManager, kuby);
        rewardManager.load();
        rewardNpc = new RewardNpc(this);
        rewardGui = new RewardGui(this);
        getServer().getPluginManager().registerEvents(rewardGui, this);

        getServer().getPluginManager().registerEvents(new EditorListener(this), this);
        getServer().getPluginManager().registerEvents(new RunListener(this, runManager), this);
        getServer().getPluginManager().registerEvents(new EditorGuiListener(this, editorGui), this);

        configGui = new ConfigGui(this);
        getServer().getPluginManager().registerEvents(configGui, this);

        // Befehle laufen über den /kp-Befehl von KubyPlugins: /kp parkour ...
        KpCommand kpParkour = new KpCommand(this);
        kuby.registerKpSub("parkour", (p, args) -> kpParkour.onCommand(p, null, "kp", args));

        getLogger().info("KubyJ&R Addon 1.0.0 als Addon von KubyPlugins aktiviert.");
    }

    @Override
    public void onDisable() {
        if (arenaManager != null) {
            arenaManager.save();
        }
        if (statsManager != null) {
            statsManager.save();
        }
        if (rewardManager != null) {
            rewardManager.save();
        }
        if (holoManager != null) {
            holoManager.save();
        }
    }

    public static KPJumpRun get() {
        return instance;
    }

    public KubyPlugins kuby() {
        return kuby;
    }

    public ArenaManager arenas() {
        return arenaManager;
    }

    public EditorStick editorStick() {
        return editorStick;
    }

    public StatsManager stats() {
        return statsManager;
    }

    public RewardManager rewards() {
        return rewardManager;
    }

    public RewardNpc rewardNpc() {
        return rewardNpc;
    }

    public EditorGui editorGui() {
        return editorGui;
    }

    public HoloManager holos() {
        return holoManager;
    }

    public RunManager runs() {
        return runManager;
    }

    public ConfigGui configGui() {
        return configGui;
    }
}
