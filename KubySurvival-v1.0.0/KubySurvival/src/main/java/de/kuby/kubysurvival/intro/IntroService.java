package de.kuby.kubysurvival.intro;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.service.RegionService;
import de.kuby.kubyplugins.util.Locs;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Teleports;
import de.kuby.kubysurvival.KubySurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Einstieg in Survival: /bauwelt (fester Punkt oder Zufallsteleport), Brunnen am Spawn (Region bauwelt-brunnen)
 * und /tutorial (Stationen aus der Config, "Ort;Titel;Text"). Wer die Tour zum ersten Mal beendet, bekommt eine Belohnung.
 */
public final class IntroService implements Listener {

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final Set<UUID> finished = new HashSet<>();
    private final Map<UUID, Integer> touring = new HashMap<>();
    private final Map<UUID, Long> fountainCooldown = new HashMap<>();

    public IntroService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "intro.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String s : yml.getStringList("finished")) {
            try {
                finished.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    // ---------- Bauwelt ----------

    public void toBuildWorld(Player p) {
        Location loc = Locs.parse(core.getConfig().getString("bauwelt.location", ""));
        if (loc != null) {
            Teleports.delayed(core, p, loc, "Bauwelt");
        } else {
            Teleports.randomTeleport(core, p);
        }
    }

    public void setBuildWorld(Player p) {
        core.getConfig().set("bauwelt.location", Locs.serialize(p.getLocation()));
        core.saveConfig();
        Msg.send(p, "<green>Bauwelt-Punkt gesetzt.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        RegionService.Region r = core.regions().find(core.getConfig().getString("bauwelt.fountain-region", "bauwelt-brunnen"));
        if (r == null || !r.contains(e.getTo())) return;
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = fountainCooldown.get(p.getUniqueId());
        if (last != null && now - last < 5000) return;
        fountainCooldown.put(p.getUniqueId(), now);
        toBuildWorld(p);
    }

    // ---------- Tutorial ----------

    private record Station(Location loc, String title, String text) {}

    private List<Station> stations() {
        List<Station> out = new ArrayList<>();
        for (String raw : core.getConfig().getStringList("tutorial.stations")) {
            String[] parts = raw.split(";", 3);
            if (parts.length < 3) continue;
            Location l = Locs.parse(parts[0]);
            if (l != null) out.add(new Station(l, parts[1], parts[2]));
        }
        return out;
    }

    public void start(Player p) {
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) {
            Msg.send(p, "<red>Die Tour gibt es nur in Survival.");
            return;
        }
        if (stations().isEmpty()) {
            Msg.send(p, "<gray>Die Tour wird gerade eingerichtet. Schau solange in die Hilfe: <aqua>/hilfe");
            return;
        }
        touring.put(p.getUniqueId(), 0);
        showStation(p);
    }

    public void next(Player p) {
        Integer i = touring.get(p.getUniqueId());
        if (i == null) {
            start(p);
            return;
        }
        touring.put(p.getUniqueId(), i + 1);
        showStation(p);
    }

    public void stop(Player p) {
        if (touring.remove(p.getUniqueId()) != null) Msg.send(p, "<gray>Tour beendet. Mit <aqua>/tutorial</aqua> startest du sie neu.");
    }

    private void showStation(Player p) {
        List<Station> list = stations();
        int i = touring.getOrDefault(p.getUniqueId(), 0);
        if (i >= list.size()) {
            finish(p);
            return;
        }
        Station s = list.get(i);
        p.teleport(s.loc());
        p.showTitle(Title.title(Msg.parse("<gold><b>" + Msg.escape(s.title())), Msg.parse("<gray>Station " + (i + 1) + " von " + list.size()),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        p.sendMessage(Component.empty());
        p.sendMessage(Msg.parse("<gold><b>" + Msg.escape(s.title()) + "</b> <dark_gray>(" + (i + 1) + "/" + list.size() + ")"));
        p.sendMessage(Msg.parse("<white>" + Msg.escape(s.text())));
        p.sendMessage(Msg.parse("<click:run_command:'/tutorial weiter'><green>[" + (i + 1 < list.size() ? "Weiter" : "Abschließen")
                + "]</click>  <click:run_command:'/tutorial stop'><gray>[Beenden]</click>"));
    }

    private void finish(Player p) {
        touring.remove(p.getUniqueId());
        Teleports.spawn(core, p);
        Msg.send(p, "<green><b>Tour geschafft!</b> <gray>Alles Weitere findest du jederzeit in <aqua>/hilfe</aqua>.");
        if (finished.add(p.getUniqueId())) {
            long coins = core.getConfig().getLong("tutorial.reward-coins", 500);
            long tokens = core.getConfig().getLong("tutorial.reward-tokens", 3);
            if (coins > 0) core.economy().deposit(p.getUniqueId(), coins);
            if (tokens > 0) addon.jobs().addTokens(p.getUniqueId(), tokens);
            Msg.send(p, "<gold>Belohnung: +" + Msg.coins(coins) + " Coins <light_purple>+" + tokens + " Tokens");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
            save();
        }
    }

    /** /kp tutorial add <Titel>|<Text> | list | clear */
    public void admin(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase() : "";
        List<String> raw = new ArrayList<>(core.getConfig().getStringList("tutorial.stations"));
        switch (sub) {
            case "add" -> {
                String rest = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";
                int bar = rest.indexOf('|');
                if (bar < 1) {
                    Msg.send(p, "<red>Nutzung: /kp tutorial add <Titel>|<Text>");
                    return;
                }
                raw.add(Locs.serialize(p.getLocation()) + ";" + rest.substring(0, bar).trim() + ";" + rest.substring(bar + 1).trim());
                core.getConfig().set("tutorial.stations", raw);
                core.saveConfig();
                Msg.send(p, "<green>Station " + raw.size() + " gesetzt (an deiner Position).");
            }
            case "clear" -> {
                core.getConfig().set("tutorial.stations", new ArrayList<String>());
                core.saveConfig();
                Msg.send(p, "<green>Alle Stationen gelöscht.");
            }
            case "list" -> {
                Msg.send(p, "<gold>Tour-Stationen: <white>" + raw.size());
                for (int i = 0; i < raw.size(); i++) {
                    String[] parts = raw.get(i).split(";", 3);
                    Msg.send(p, "<gray>" + (i + 1) + ". <white>" + (parts.length > 1 ? parts[1] : "?"));
                }
            }
            default -> Msg.send(p, "<white>/kp tutorial add <Titel>|<Text> <gray>Station an deiner Position\n"
                    + "<white>/kp tutorial list <gray>· <white>/kp tutorial clear\n<white>/kp bauwelt setzen <gray>Bauwelt-Punkt hier");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        touring.remove(e.getPlayer().getUniqueId());
        fountainCooldown.remove(e.getPlayer().getUniqueId());
    }

    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        List<String> ids = new ArrayList<>();
        for (UUID id : finished) ids.add(id.toString());
        yml.set("finished", ids);
        try {
            addon.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("intro.yml: " + e.getMessage());
        }
    }
}
