package de.kuby.kubysmp;

import java.util.UUID;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubysmp.listener.InsuranceListener;
import de.kuby.kubyplugins.pvp.PvpApi;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysmp.command.BountyCommand;
import de.kuby.kubysmp.command.DuelCommand;
import de.kuby.kubysmp.command.TrophyCommand;
import de.kuby.kubysmp.command.TurnierCommand;
import de.kuby.kubysmp.command.VersichernCommand;
import de.kuby.kubysmp.gui.AdminCombatMenu;
import de.kuby.kubysmp.gui.DuelMenu;
import de.kuby.kubysmp.gui.InsuranceMenu;
import de.kuby.kubysmp.gui.TournamentAdminMenu;
import de.kuby.kubysmp.gui.TournamentMenu;
import de.kuby.kubysmp.gui.TrophyMenu;
import de.kuby.kubysmp.listener.CombatListener;
import de.kuby.kubysmp.service.BountyService;
import de.kuby.kubysmp.service.CombatService;
import de.kuby.kubysmp.service.DuelService;
import de.kuby.kubysmp.service.RaidInsuranceService;
import de.kuby.kubysmp.service.TournamentService;
import de.kuby.kubysmp.service.TrophyService;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** PvP-Teil: Kampf, Duelle, Kopfgelder, Trophäen, Turniere und Raid-Versicherung. Braucht KubyCore. */
public final class KubySMP extends JavaPlugin {

    private static KubySMP instance;

    private KubyPlugins core;
    private BountyService bounties;
    private CombatService combat;
    private DuelService duels;
    private TrophyService trophies;
    private TournamentService tournaments;
    private RaidInsuranceService insurance;

    public static KubySMP get() { return instance; }

    public BountyService bounties() { return bounties; }
    public CombatService combat() { return combat; }
    public DuelService duels() { return duels; }
    public TrophyService trophies() { return trophies; }
    public TournamentService tournaments() { return tournaments; }
    public RaidInsuranceService insurance() { return insurance; }

    @Override
    public void onEnable() {
        if (!(getServer().getPluginManager().getPlugin("KubyCore") instanceof KubyPlugins c)) {
            getLogger().severe("KubyCore fehlt oder ist nicht geladen. KubySMP wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        instance = this;
        core = c;
        saveDefaultConfig();

        bounties = new BountyService(core);
        combat = new CombatService(core);
        duels = new DuelService(core);
        trophies = new TrophyService(core);
        tournaments = new TournamentService(core);
        insurance = new RaidInsuranceService(core);

        core.setPvp(new PvpApi() {
            @Override public boolean inCombat(java.util.UUID id) { return combat.inCombat(id); }
            @Override public boolean isDueling(java.util.UUID id) { return duels.isDueling(id); }
            @Override public void tagCombat(java.util.UUID id) { combat.tag(id); }
            @Override public String startTournamentMatch(org.bukkit.entity.Player a, org.bukkit.entity.Player b) { return duels.startMatch(a, b); }
            @Override public long claimBounty(java.util.UUID id) { return bounties.claim(id); }
        });

        command("duell", new DuelCommand(core));
        command("bounty", new BountyCommand(core));
        command("bounties", new BountyCommand(core));
        command("trophaeen", new TrophyCommand(core));
        command("turnier", new TurnierCommand(core));
        command("versichern", new VersichernCommand(core));

        getServer().getPluginManager().registerEvents(new CombatListener(core), this);
        getServer().getPluginManager().registerEvents(new InsuranceListener(core), this);

        core.registerMainEntry(new KubyPlugins.MainEntry(Material.IRON_SWORD, "Duelle", "Duelle starten oder annehmen",
                p -> new DuelMenu(core).open(p)));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.TOTEM_OF_UNDYING, "Turnier", "K.O.-Turnier anmelden",
                p -> new TournamentMenu(core).open(p)));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.NETHER_STAR, "Trophäen", "Trophäen und Ligen",
                p -> new TrophyMenu(core).open(p)));
        core.registerMainEntry(new KubyPlugins.MainEntry(Material.SHIELD, "Versicherung", "Raid-Versicherung für Kisten",
                p -> new InsuranceMenu(core).open(p)));
        core.registerAdminButton(new KubyPlugins.AdminButton(19, Material.IRON_SWORD, "Kampf", "Kopfgelder und laufende Duelle",
                p -> new AdminCombatMenu(core).open(p)));
        core.registerAdminButton(new KubyPlugins.AdminButton(21, Material.TOTEM_OF_UNDYING, "Turnier", "Turnier steuern",
                p -> new TournamentAdminMenu(core).open(p)));

        getLogger().info("KubySMP aktiv.");
    }

    private void command(String name, org.bukkit.command.CommandExecutor exec) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) cmd.setExecutor(exec);
    }

    @Override
    public void onDisable() {
        if (bounties != null) bounties.save();
        if (insurance != null) insurance.save();
    }
}
