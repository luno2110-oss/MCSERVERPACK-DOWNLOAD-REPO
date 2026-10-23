package de.kuby.kubysurvival;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.claim.ClaimCommand;
import de.kuby.kubysurvival.claim.ClaimListener;
import de.kuby.kubysurvival.claim.ClaimService;
import de.kuby.kubysurvival.claim.ClaimTool;
import de.kuby.kubysurvival.fishing.AngelMenu;
import de.kuby.kubysurvival.fishing.FishDealer;
import de.kuby.kubysurvival.fishing.FishingListener;
import de.kuby.kubysurvival.hud.SurvivalHud;
import de.kuby.kubysurvival.intro.IntroService;
import de.kuby.kubysurvival.jobs.Job;
import de.kuby.kubysurvival.jobs.JobCommand;
import de.kuby.kubysurvival.jobs.JobListener;
import de.kuby.kubysurvival.jobs.JobMenu;
import de.kuby.kubysurvival.jobs.JobService;
import de.kuby.kubysurvival.jobs.TokenShopMenu;
import de.kuby.kubysurvival.fishing.FishingService;
import de.kuby.kubysurvival.quests.QuestCommand;
import de.kuby.kubysurvival.quests.QuestListener;
import de.kuby.kubysurvival.quests.QuestMenu;
import de.kuby.kubysurvival.quests.QuestService;
import de.kuby.kubysurvival.quests.SideQuestCommand;
import de.kuby.kubysurvival.quests.SideQuestService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Survival-Teil: Angeln (Fischarten, Level, Rute, Fischbuch, Turnier, Händler) und Landclaims. Braucht KubyCore. */
public final class KubySurvival extends JavaPlugin {

    private KubyPlugins core;
    private FishingService fishing;
    private FishDealer dealer;
    private ClaimService claims;
    private ClaimTool claimTool;
    private SurvivalHud hud;
    private JobService jobs;
    private IntroService intro;
    private QuestService quests;
    private SideQuestService sideQuests;
    private final Map<UUID, Location[]> zoneCorners = new HashMap<>();

    public KubyPlugins core() { return core; }
    public FishingService fishing() { return fishing; }
    public FishDealer dealer() { return dealer; }
    public ClaimService claims() { return claims; }
    public ClaimTool claimTool() { return claimTool; }
    public JobService jobs() { return jobs; }
    public IntroService intro() { return intro; }
    public QuestService quests() { return quests; }
    public SideQuestService sideQuests() { return sideQuests; }

    @Override
    public void onEnable() {
        if (!(getServer().getPluginManager().getPlugin("KubyCore") instanceof KubyPlugins c)) {
            getLogger().severe("KubyCore fehlt oder ist nicht geladen. KubySurvival wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        core = c;
        fishing = new FishingService(this, core);
        dealer = new FishDealer(this);
        claims = new ClaimService(this, core);
        claimTool = new ClaimTool(this);
        jobs = new JobService(this, core);
        quests = new QuestService(this, core);
        sideQuests = new SideQuestService(this, core);
        getServer().getPluginManager().registerEvents(sideQuests, this);
        getServer().getPluginManager().registerEvents(new QuestListener(this), this);
        intro = new IntroService(this, core);
        getServer().getPluginManager().registerEvents(intro, this);

        getServer().getPluginManager().registerEvents(new FishingListener(this, core), this);
        getServer().getPluginManager().registerEvents(new ClaimListener(this, core), this);
        getServer().getPluginManager().registerEvents(new JobListener(this, jobs), this);
        hud = new SurvivalHud(this, core);
        getServer().getPluginManager().registerEvents(hud, this);
        command("angeln", (sender, cmd, label, args) -> {
            if (sender instanceof Player p) new AngelMenu(this, core).open(p);
            return true;
        });
        command("claim", new ClaimCommand(this));
        command("bauwelt", (sender, cmd, label, args) -> {
            if (sender instanceof Player p) intro.toBuildWorld(p);
            return true;
        });
        command("tutorial", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) return true;
            String sub = args.length > 0 ? args[0].toLowerCase() : "";
            switch (sub) {
                case "weiter" -> intro.next(p);
                case "stop" -> intro.stop(p);
                default -> intro.start(p);
            }
            return true;
        });
        core.registerKpSub("tutorial", (p, args) -> {
            if (p.hasPermission("kuby.admin")) intro.admin(p, args);
            else Msg.send(p, "<red>Nur Admins.");
        });
        core.registerKpSub("bauwelt", (p, args) -> {
            if (!p.hasPermission("kuby.admin")) Msg.send(p, "<red>Nur Admins.");
            else if (args.length > 1 && args[1].equalsIgnoreCase("setzen")) intro.setBuildWorld(p);
            else Msg.send(p, "<white>/kp bauwelt setzen <gray>Bauwelt-Punkt an deiner Position. Brunnen: Region <white>bauwelt-brunnen");
        });
        JobCommand jobCommand = new JobCommand(this);
        command("job", jobCommand);
        PluginCommand jobCmd = getCommand("job");
        if (jobCmd != null) jobCmd.setTabCompleter(jobCommand);
        QuestCommand questCommand = new QuestCommand(this);
        command("quest", questCommand);
        PluginCommand questCmd = getCommand("quest");
        if (questCmd != null) questCmd.setTabCompleter(questCommand);
        SideQuestCommand sideCommand = new SideQuestCommand(this);
        command("sidequests", sideCommand);
        PluginCommand sideCmd = getCommand("sidequests");
        if (sideCmd != null) sideCmd.setTabCompleter(sideCommand);
        core.registerKpSub("figur", (p, args) -> {
            if (p.hasPermission("kuby.admin")) sideQuests.admin(p, args);
            else Msg.send(p, "<red>Nur Admins.");
        });
        core.registerStat("quests_done", id -> quests.total(id));
        core.registerStat("quest_streak", id -> (long) quests.streak(id));
        command("tokenshop", (sender, cmd, label, args) -> {
            if (sender instanceof Player p) new TokenShopMenu(this, core).open(p);
            return true;
        });

        core.registerKpSub("angel", this::kpAngel);
        core.registerStat("claimblocks", claims::available);
        // Werte für die Erfolge im Kern.
        core.registerStat("fish_caught", id -> (long) fishing.angler(id).total());
        core.registerStat("angel_level", id -> (long) FishingService.levelOf(fishing.angler(id).xp));
        core.registerStat("claims", id -> (long) claims.claimsOf(id).size());
        // Level, Job und Tokens für Scoreboard und Erfolge.
        core.registerStat("player_level", id -> (long) JobService.playerLevel(jobs.worker(id)));
        core.registerStat("tokens", id -> jobs.worker(id).tokens);
        core.registerStat("job_level", id -> {
            JobService.Worker w = jobs.worker(id);
            return w.job == null ? 0L : (long) jobs.jobLevel(w, w.job);
        });
        core.registerText("job", id -> {
            Job j = jobs.worker(id).job;
            return j == null ? "" : j.display;
        });
        // Regionen im Kern fragen hierüber, wem ein Besitzer vertraut.
        core.setTrustCheck((owner, who) -> {
            try {
                return claims.isTrusted(UUID.fromString(owner), who);
            } catch (IllegalArgumentException e) {
                return false;
            }
        });
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.FISHING_ROD, "Angeln", "Fischbuch, Rute, Verkauf, Bestenliste",
                p -> new AngelMenu(this, core).open(p)));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.IRON_PICKAXE, "Jobs", "Job wählen, Level, Tokens und Token-Shop",
                p -> new JobMenu(this, core).open(p)));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.WRITABLE_BOOK, "Quests", "Tages-, Wochen- und Team-Quests, Geschichte",
                p -> new QuestMenu(this).open(p)));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.GOLDEN_AXE, "Grundstücke", "Landclaims sichern",
                p -> p.performCommand("claim")));

        getServer().getScheduler().runTaskTimer(this, () -> {
            fishing.tick();
            claims.tick();
        }, 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            fishing.save();
            claims.save();
            jobs.save();
            quests.save();
            sideQuests.save();
        }, 6000L, 6000L);
        getServer().getScheduler().runTaskLater(this, fishing::respawnSigns, 40L);
        getLogger().info("KubySurvival aktiv.");
    }

    @Override
    public void onDisable() {
        if (hud != null) hud.shutdown();
        if (fishing != null) fishing.save();
        if (claims != null) claims.save();
        if (jobs != null) jobs.save();
        if (quests != null) quests.save();
        if (sideQuests != null) sideQuests.save();
        if (intro != null) intro.save();
    }

    private void command(String name, org.bukkit.command.CommandExecutor exec) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) cmd.setExecutor(exec);
    }

    /** /kp angel ...: Angelzonen, Turnier und Fischhändler (nur Admins). */
    private void kpAngel(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase() : "";
        switch (sub) {
            case "pos1", "pos2" -> {
                Location[] c = zoneCorners.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
                c[sub.equals("pos1") ? 0 : 1] = p.getLocation().getBlock().getLocation();
                Msg.send(p, "<green>Ecke " + sub.charAt(3) + " der Angelzone gesetzt.");
            }
            case "zone" -> zone(p, args);
            case "turnier" -> {
                String action = args.length > 2 ? args[2].toLowerCase() : "";
                String err;
                if (action.equals("start")) {
                    int minutes = 30;
                    try {
                        if (args.length > 3) minutes = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        Msg.send(p, "<red>Ungültige Minutenzahl.");
                        return;
                    }
                    err = fishing.startTournament(minutes);
                } else if (action.equals("stop")) {
                    err = fishing.stopTournament();
                } else {
                    err = "Nutzung: /kp angel turnier start <Minuten> | stop";
                }
                if (err != null) Msg.send(p, "<red>" + err);
            }
            case "haendler", "händler" -> {
                if (args.length > 2 && args[2].equalsIgnoreCase("entfernen")) {
                    Msg.send(p, dealer.removeNear(p) ? "<green>Fischhändler entfernt." : "<red>Kein Fischhändler im Umkreis von 5 Blöcken.");
                } else {
                    dealer.spawn(p);
                    Msg.send(p, "<green>Fischhändler gesetzt. Entfernen mit /kp angel haendler entfernen.");
                }
            }
            default -> {
                Msg.send(p, "<aqua><b>Angeln (Admin)</b>");
                Msg.send(p, "<white>/kp angel pos1 | pos2 <gray>Ecken der Zone an deiner Position");
                Msg.send(p, "<white>/kp angel zone erstellen <id> [Name] <gray>Zone aus den Ecken (oder Blaze-Rod-Ecken)");
                Msg.send(p, "<white>/kp angel zone löschen <id> <gray>· <white>/kp angel zone liste");
                Msg.send(p, "<white>/kp angel turnier start <Minuten> | stop");
                Msg.send(p, "<white>/kp angel haendler [entfernen] <gray>Fischhändler an deiner Position");
            }
        }
    }

    private void zone(Player p, String[] args) {
        String action = args.length > 2 ? args[2].toLowerCase() : "";
        switch (action) {
            case "erstellen", "create" -> {
                if (args.length < 4) {
                    Msg.send(p, "<red>Nutzung: /kp angel zone erstellen <id> [Name]");
                    return;
                }
                Location[] c = zoneCorners.get(p.getUniqueId());
                if (c == null || c[0] == null || c[1] == null) c = core.edit().corners(p.getUniqueId());
                String name = args.length > 4 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : args[3];
                String err = fishing.createZone(args[3].toLowerCase(), c == null ? null : c[0], c == null ? null : c[1], name);
                Msg.send(p, err == null ? "<green>Angelzone " + args[3] + " angelegt." : "<red>" + err);
            }
            case "löschen", "loeschen", "delete" -> {
                if (args.length < 4) {
                    Msg.send(p, "<red>Nutzung: /kp angel zone löschen <id>");
                    return;
                }
                Msg.send(p, fishing.deleteZone(args[3].toLowerCase()) ? "<green>Angelzone gelöscht." : "<red>Keine Angelzone mit dieser ID.");
            }
            default -> {
                if (fishing.zones().isEmpty()) {
                    Msg.send(p, "<gray>Noch keine Angelzonen. Solange keine existiert, gilt das Angelsystem überall in Survival.");
                }
                for (FishingService.Zone z : fishing.zones()) {
                    Msg.send(p, "<aqua>▪ <white>" + z.id() + " <gray>(" + z.name() + ") " + z.world() + " "
                            + z.x1() + "," + z.z1() + " bis " + z.x2() + "," + z.z2());
                }
            }
        }
    }
}
