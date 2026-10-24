package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Musik im Casino: Wer die Casino-Region betritt, hört seinen Song in Schleife, beim Verlassen hört er auf.
 * /music wählt den Song (nur im Casino). Die Songs sind die Schallplatten aus dem Spiel, damit es ohne Resource Pack geht.
 */
public final class CasinoMusic {

    private record Song(String name, Sound sound, int seconds) {}

    private static final List<Song> SONGS = List.of(
            new Song("Pigstep", Sound.MUSIC_DISC_PIGSTEP, 149),
            new Song("Otherside", Sound.MUSIC_DISC_OTHERSIDE, 195),
            new Song("Cat", Sound.MUSIC_DISC_CAT, 185),
            new Song("Mall", Sound.MUSIC_DISC_MALL, 197),
            new Song("Chirp", Sound.MUSIC_DISC_CHIRP, 185),
            new Song("Blocks", Sound.MUSIC_DISC_BLOCKS, 345),
            new Song("Wait", Sound.MUSIC_DISC_WAIT, 238),
            new Song("Strad", Sound.MUSIC_DISC_STRAD, 188),
            new Song("Far", Sound.MUSIC_DISC_FAR, 174),
            new Song("Creator", Sound.MUSIC_DISC_CREATOR, 176),
            new Song("Relic", Sound.MUSIC_DISC_RELIC, 218),
            new Song("Precipice", Sound.MUSIC_DISC_PRECIPICE, 299));
    private static final int OFF = -1;

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Integer> choice = new HashMap<>();
    private final Map<UUID, BukkitTask> loops = new HashMap<>();
    private final Map<UUID, Song> playing = new HashMap<>();
    /** Wer gerade im Casino steht (für die Begrüßung beim Betreten). */
    private final java.util.Set<UUID> present = new java.util.HashSet<>();

    public CasinoMusic(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "casino-music.yml");
        ConfigurationSection s = YamlConfiguration.loadConfiguration(file).getConfigurationSection("choice");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                try {
                    choice.put(UUID.fromString(key), s.getInt(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public boolean inCasino(Location l) {
        RegionService.Region r = plugin.regions().find(CasinoService.REGION);
        return r != null && r.contains(l);
    }

    /** Startet die Musik, wenn der Spieler im Casino steht, sonst stoppt sie. */
    public synchronized void update(Player p, Location at) {
        boolean inside = at != null && inCasino(at);
        if (inside && present.add(p.getUniqueId())) {
            de.kuby.kubyplugins.casino.CasinoText.send(p, "Dein Stand <#F2C94C>"
                    + de.kuby.kubyplugins.casino.CasinoText.chips(plugin.casino().chips(p.getUniqueId()))
                    + "</#F2C94C> <dark_gray>·</dark_gray> Kasse am Wechsel-NPC");
        } else if (!inside) {
            present.remove(p.getUniqueId());
        }
        boolean on = playing.containsKey(p.getUniqueId());
        if (inside && !on) start(p);
        if (!inside && on) stop(p);
    }

    /** Beim Ausloggen: nächstes Betreten zählt wieder als neu. */
    public synchronized void forget(Player p) {
        present.remove(p.getUniqueId());
    }

    public synchronized void stop(Player p) {
        BukkitTask task = loops.remove(p.getUniqueId());
        if (task != null) task.cancel();
        Song song = playing.remove(p.getUniqueId());
        if (song != null) p.stopSound(song.sound(), SoundCategory.RECORDS);
    }

    /** /music: Songauswahl, nur im Casino. */
    public void openMenu(Player p) {
        if (!inCasino(p.getLocation())) {
            Msg.send(p, "<red>Musik aussuchen geht nur im Casino.");
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        int current = choice.getOrDefault(p.getUniqueId(), 0);
        for (int i = 0; i < SONGS.size(); i++) {
            int index = i;
            String label = (i == current ? "<green>▶ " : "<white>") + SONGS.get(i).name();
            buttons.add(button(label, "Diesen Song spielen", pl -> choose(pl, index)));
        }
        buttons.add(button(current == OFF ? "<red>■ Musik aus" : "<gray>Musik aus", "Keine Musik im Casino", pl -> choose(pl, OFF)));
        DialogBase base = DialogBase.builder(Msg.parse("<red><b>CASINO-MUSIK</b>"))
                .canCloseWithEscape(true)
                .body(List.of(DialogBody.plainMessage(Msg.parse("<gray>Such dir einen Song aus. Er läuft, solange du im Casino bist."), 320)))
                .build();
        p.showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(buttons).columns(3).build())));
    }

    private synchronized void choose(Player p, int index) {
        choice.put(p.getUniqueId(), index);
        save();
        stop(p);
        if (index == OFF) {
            Msg.send(p, "<gray>Casino-Musik aus.");
        } else {
            Msg.send(p, "<green>Jetzt läuft: <white>" + SONGS.get(index).name());
            if (inCasino(p.getLocation())) start(p);
        }
    }

    private void start(Player p) {
        int index = choice.getOrDefault(p.getUniqueId(), 0);
        if (index == OFF || index < 0 || index >= SONGS.size()) return;
        Song song = SONGS.get(index);
        float volume = (float) plugin.getConfig().getDouble("casino.music.volume", 0.6);
        p.playSound(p, song.sound(), SoundCategory.RECORDS, volume, 1.0f);
        playing.put(p.getUniqueId(), song);
        loops.put(p.getUniqueId(), plugin.getServer().getScheduler().runTaskLater(plugin, () -> replay(p), song.seconds() * 20L));
    }

    private synchronized void replay(Player p) {
        loops.remove(p.getUniqueId());
        playing.remove(p.getUniqueId());
        if (p.isOnline() && inCasino(p.getLocation())) start(p);
    }

    private ActionButton button(String label, String tooltip, java.util.function.Consumer<Player> onClick) {
        return ActionButton.create(Msg.parse(label), Msg.parse("<gray>" + tooltip), 100,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) onClick.accept(pl);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()));
    }

    private synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> e : choice.entrySet()) out.set("choice." + e.getKey(), e.getValue());
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("casino-music.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
