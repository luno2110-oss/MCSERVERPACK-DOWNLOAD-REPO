package de.kuby.kubysurvival;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.claim.ClaimCommand;
import de.kuby.kubysurvival.claim.ClaimListener;
import de.kuby.kubysurvival.claim.ClaimService;
import de.kuby.kubysurvival.claim.ClaimTool;
import de.kuby.kubysurvival.angeln.AngelCommand;
import de.kuby.kubysurvival.angeln.AngelDealer;
import de.kuby.kubysurvival.angeln.AngelFx;
import de.kuby.kubysurvival.angeln.AngelHolo;
import de.kuby.kubysurvival.angeln.AngelListener;
import de.kuby.kubysurvival.angeln.AngelMenus;
import de.kuby.kubysurvival.hud.SurvivalHud;
import de.kuby.kubysurvival.intro.IntroService;
import de.kuby.kubysurvival.jobs.Job;
import de.kuby.kubysurvival.jobs.JobCommand;
import de.kuby.kubysurvival.jobs.JobListener;
import de.kuby.kubysurvival.jobs.JobMenu;
import de.kuby.kubysurvival.jobs.JobService;
import de.kuby.kubysurvival.jobs.LevelCommand;
import de.kuby.kubysurvival.jobs.TokenShopMenu;
import de.kuby.kubysurvival.heads.HeadService;
import de.kuby.kubysurvival.graves.GraveCommand;
import de.kuby.kubysurvival.graves.GraveService;
import de.kuby.kubysurvival.marry.BankService;
import de.kuby.kubysurvival.marry.MarryCommand;
import de.kuby.kubysurvival.marry.MarryMenus;
import de.kuby.kubysurvival.marry.MarryService;
import de.kuby.kubysurvival.marry.SurvivalNpc;
import de.kuby.kubysurvival.angeln.AngelService;
import de.kuby.kubysurvival.farm.FarmWorldService;
import de.kuby.kubysurvival.farmpass.FarmpassCommand;
import de.kuby.kubysurvival.farmpass.FarmpassMenu;
import de.kuby.kubysurvival.farmpass.FarmpassService;
import de.kuby.kubysurvival.pets.PetCommand;
import de.kuby.kubysurvival.pets.PetMenu;
import de.kuby.kubysurvival.pets.PetService;
import de.kuby.kubysurvival.tokens.BackpackService;
import de.kuby.kubysurvival.tokens.TokenService;
import de.kuby.kubysurvival.shops.ChestShopCommand;
import de.kuby.kubysurvival.shops.ChestShopService;
import de.kuby.kubysurvival.shops.SwCommand;
import de.kuby.kubysurvival.shops.WarpMenu;
import de.kuby.kubysurvival.shops.WarpService;
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

/** Survival-Teil: Angeln, Landclaims, Jobs, Quests, Haustiere, Heiraten und Bank und mehr. Braucht KubyCore. */
public final class KubySurvival extends JavaPlugin {

    private KubyPlugins core;
    private AngelService angeln;
    private AngelMenus angelMenus;
    private AngelHolo angelHolo;
    private ClaimService claims;
    private ClaimTool claimTool;
    private SurvivalHud hud;
    private JobService jobs;
    private IntroService intro;
    private QuestService quests;
    private SideQuestService sideQuests;
    private FarmWorldService farm;
    private WarpService warps;
    private TokenService tokens;
    private FarmpassService farmpass;
    private PetService pets;
    private BackpackService backpacks;
    private ChestShopService chestShops;
    private MarryService marry;
    private BankService bank;
    private GraveService graves;

    public KubyPlugins core() { return core; }
    public AngelService angeln() { return angeln; }
    public AngelMenus angelMenus() { return angelMenus; }
    public ClaimService claims() { return claims; }
    public ClaimTool claimTool() { return claimTool; }
    public JobService jobs() { return jobs; }
    public IntroService intro() { return intro; }
    public QuestService quests() { return quests; }
    public SideQuestService sideQuests() { return sideQuests; }
    public FarmWorldService farm() { return farm; }
    public WarpService warps() { return warps; }
    public TokenService tokens() { return tokens; }
    public FarmpassService farmpass() { return farmpass; }
    public PetService pets() { return pets; }
    public BackpackService backpacks() { return backpacks; }
    public ChestShopService chestShops() { return chestShops; }
    public MarryService marry() { return marry; }
    public BankService bank() { return bank; }
    public GraveService graves() { return graves; }

    @Override
    public void onEnable() {
        if (!(getServer().getPluginManager().getPlugin("KubyCore") instanceof KubyPlugins c)) {
            getLogger().severe("KubyCore fehlt oder ist nicht geladen. KubySurvival wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        core = c;
        angeln = new AngelService(this, core);
        claims = new ClaimService(this, core);
        claimTool = new ClaimTool(this);
        jobs = new JobService(this, core);
        quests = new QuestService(this, core);
        sideQuests = new SideQuestService(this, core);
        farm = new FarmWorldService(this, core);
        warps = new WarpService(this, core);
        farmpass = new FarmpassService(this, core);
        getServer().getPluginManager().registerEvents(farmpass, this);
        pets = new PetService(this, core);
        getServer().getPluginManager().registerEvents(pets, this);
        tokens = new TokenService(this, core);
        getServer().getPluginManager().registerEvents(tokens, this);
        backpacks = new BackpackService(this, core);
        getServer().getPluginManager().registerEvents(backpacks, this);
        chestShops = new ChestShopService(this, core);
        getServer().getPluginManager().registerEvents(chestShops, this);
        SurvivalNpc npcs = new SurvivalNpc(this);
        getServer().getPluginManager().registerEvents(npcs, this);
        marry = new MarryService(this, core, npcs);
        bank = new BankService(this, core, npcs);
        MarryMenus marryMenus = new MarryMenus(this, core, marry, bank);
        marry.wire(bank, marryMenus);
        getServer().getPluginManager().registerEvents(marry, this);
        MarryCommand marryCommand = new MarryCommand(core, marry, bank, marryMenus, npcs);
        getServer().getPluginManager().registerEvents(farm, this);
        core.homes().registerBlock(l -> farm.isFarm(l.getWorld()) ? "In der Farmwelt kannst du kein Home setzen. Sie wird jede Woche neu erstellt." : null);
        core.homes().registerBlock(l -> {
            Location spawn = core.modes().spawnOf(de.kuby.kubyplugins.service.ModeService.SURVIVAL);
            int r = core.getConfig().getInt("homes.survival.spawn-radius", 80);
            if (spawn != null && spawn.getWorld() == l.getWorld() && spawn.distanceSquared(l) <= (double) r * r) {
                return "Am Spawn kannst du kein Home setzen.";
            }
            for (String id : core.getConfig().getStringList("homes.survival.blocked-regions")) {
                var reg = core.regions().find(id);
                if (reg != null && reg.contains(l)) return id.equalsIgnoreCase("casino") ? "Im Casino kannst du kein Home setzen." : "Hier kannst du kein Home setzen.";
            }
            return null;
        });
        getServer().getPluginManager().registerEvents(sideQuests, this);
        getServer().getPluginManager().registerEvents(new QuestListener(this), this);
        intro = new IntroService(this, core);
        getServer().getPluginManager().registerEvents(intro, this);

        angelMenus = new AngelMenus(this, core, angeln);
        angelHolo = new AngelHolo(this);
        AngelDealer angelDealer = new AngelDealer(this);
        AngelFx angelFx = new AngelFx(this, core, angeln);
        getServer().getPluginManager().registerEvents(new AngelListener(this, core, angeln, angelFx, angelMenus, angelDealer), this);
        AngelCommand angelCommand = new AngelCommand(core, angeln, angelMenus, angelHolo, angelDealer);
        getServer().getPluginManager().registerEvents(new ClaimListener(this, core), this);
        getServer().getPluginManager().registerEvents(new JobListener(this, jobs), this);
        hud = new SurvivalHud(this, core);
        getServer().getPluginManager().registerEvents(hud, this);
        command("angeln", angelCommand);
        PluginCommand angelnCmd = getCommand("angeln");
        if (angelnCmd != null) angelnCmd.setTabCompleter(angelCommand);
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
        command("farmwelt", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) return true;
            if (args.length > 0 && args[0].equalsIgnoreCase("info")) farm.info(p);
            else if (args.length > 0 && args[0].equalsIgnoreCase("reset") && p.hasPermission("kuby.admin")) farm.reset();
            else farm.travel(p);
            return true;
        });
        SwCommand swCommand = new SwCommand(this);
        command("sw", swCommand);
        PluginCommand swCmd = getCommand("sw");
        if (swCmd != null) swCmd.setTabCompleter(swCommand);
        command("chestshop", new ChestShopCommand(this));
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
        PetCommand petCommand = new PetCommand(this);
        command("pet", petCommand);
        PluginCommand petCmd = getCommand("pet");
        if (petCmd != null) petCmd.setTabCompleter(petCommand);
        FarmpassCommand farmpassCommand = new FarmpassCommand(this);
        command("farmpass", farmpassCommand);
        command("werkbank", farmpassCommand);
        PluginCommand fpCmd = getCommand("farmpass");
        if (fpCmd != null) fpCmd.setTabCompleter(farmpassCommand);
        LevelCommand levelCommand = new LevelCommand(this);
        command("level", levelCommand);
        PluginCommand levelCmd = getCommand("level");
        if (levelCmd != null) levelCmd.setTabCompleter(levelCommand);
        command("tokens", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) return true;
            UUID id = p.getUniqueId();
            Msg.send(p, "<light_purple><b>Tokens: " + jobs.worker(id).tokens + "</b>");
            Msg.send(p, "<gray>Nächster Token in <white>" + tokens.minutesToNext(id) + " Min. <gray>Spielzeit in Survival · Serie: <white>"
                    + tokens.streak(id) + " Tage");
            Msg.send(p, "<gray>Tokens gibt es: <white>1 pro Stunde<gray>, <white>Tagesbonus<gray>, Extra für Tage in Folge, beim <white>Voten<gray>, "
                    + "für Quests sowie Job- und Level-Aufstiege. Kaufen oder tauschen kann man sie nicht.");
            Msg.send(p, "<click:run_command:'/tokenshop'><light_purple>[Token-Shop öffnen]</click>");
            return true;
        });
        command("backpack", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) return true;
            if (args.length > 0 && args[0].equalsIgnoreCase("item")) {
                if (backpacks.rows(p.getUniqueId()) <= 0) {
                    Msg.send(p, "<gray>Du hast noch keinen Rucksack. Freischalten im <white>/tokenshop<gray>.");
                } else {
                    p.getInventory().addItem(backpacks.item()).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
                    Msg.send(p, "<green>Rucksack-Item erhalten. <gray>Rechtsklick öffnet ihn.");
                }
            } else {
                backpacks.open(p);
            }
            return true;
        });
        command("tokenshop", (sender, cmd, label, args) -> {
            if (sender instanceof Player p) new TokenShopMenu(this, core).open(p);
            return true;
        });

        command("marry", marryCommand);
        command("bank", marryCommand);
        for (String name : new String[]{"marry", "bank"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) pc.setTabCompleter(marryCommand);
        }
        core.registerKpSub("heiraten", (p, args) -> {
            if (p.hasPermission("kuby.admin")) marryCommand.adminMarry(p, args);
            else Msg.send(p, "<red>Nur Admins.");
        });
        core.registerKpSub("bank", (p, args) -> {
            if (p.hasPermission("kuby.admin")) marryCommand.adminBank(p, args);
            else Msg.send(p, "<red>Nur Admins.");
        });
        graves = new GraveService(this, core);
        getServer().getPluginManager().registerEvents(graves, this);
        command("grab", new GraveCommand(core, graves));
        HeadService heads = new HeadService(this, core);
        command("heads", heads);
        PluginCommand headsCmd = getCommand("heads");
        if (headsCmd != null) headsCmd.setTabCompleter(heads);
        core.registerKpSub("heads", (p, args) -> {
            if (p.hasPermission("kuby.admin")) heads.admin(p, args);
            else Msg.send(p, "<red>Nur Admins.");
        });
        core.registerKpSub("angel", angelCommand::admin);
        core.registerStat("claimblocks", claims::available);
        // Werte für die Erfolge im Kern.
        core.registerStat("fish_caught", id -> (long) angeln.profile(id).catches);
        core.registerStat("angel_level", id -> (long) angeln.profile(id).level());
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
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.FISHING_ROD, "Angeln", "Fischbuch, Rute, Köder, Händler, Bestenliste",
                p -> angelMenus.open(p)));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.IRON_PICKAXE, "Jobs", "Job wählen, Level, Tokens und Token-Shop",
                p -> new JobMenu(this, core).open(p)));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.WRITABLE_BOOK, "Quests", "Tages-, Wochen- und Team-Quests, Geschichte",
                p -> new QuestMenu(this).open(p)));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.LEAD, "Haustiere", "Begleiter rufen, reiten, umbenennen",
                p -> new PetMenu(this).open(p)));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.LILY_PAD, "Farmpass", "Monats-Pass mit Vorteilen",
                p -> new FarmpassMenu(this).open(p)));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.ENDER_PEARL, "Spieler-Warps", "Läden und Bauwerke anderer besuchen",
                p -> new WarpMenu(this, false, 0).open(p)));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.GOLDEN_AXE, "Grundstücke", "Landclaims sichern",
                p -> p.performCommand("claim")));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.POPPY, "Heiraten", "Antrag, Hochzeit und Ehe-Konto",
                marryMenus::marry));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.GOLD_INGOT, "Bank", "Ehe-Konto und Konten mit Freunden",
                marryMenus::bank));

        getServer().getScheduler().runTaskTimer(this, () -> {
            angeln.tick();
            claims.tick();
        }, 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            angeln.save();
            claims.save();
            jobs.save();
            quests.save();
            sideQuests.save();
            farm.save();
            warps.save();
            chestShops.save();
            tokens.save();
            backpacks.save();
            farmpass.save();
            pets.save();
            marry.save();
            bank.save();
            graves.save();
        }, 6000L, 6000L);
        getServer().getScheduler().runTaskLater(this, () -> {
            for (AngelService.Zone z : angeln.zones()) angelHolo.spawn(z);
        }, 60L);
        getLogger().info("KubySurvival aktiv.");
    }

    @Override
    public void onDisable() {
        if (hud != null) hud.shutdown();
        if (angeln != null) angeln.save();
        if (claims != null) claims.save();
        if (jobs != null) jobs.save();
        if (quests != null) quests.save();
        if (sideQuests != null) sideQuests.save();
        if (farm != null) farm.save();
        if (warps != null) warps.save();
        if (chestShops != null) chestShops.save();
        if (tokens != null) tokens.save();
        if (farmpass != null) farmpass.shutdown();
        if (pets != null) pets.shutdown();
        if (backpacks != null) backpacks.shutdown();
        if (intro != null) intro.save();
        if (marry != null) marry.save();
        if (bank != null) bank.save();
        if (graves != null) graves.save();
    }

    private void command(String name, org.bukkit.command.CommandExecutor exec) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) cmd.setExecutor(exec);
    }
}
