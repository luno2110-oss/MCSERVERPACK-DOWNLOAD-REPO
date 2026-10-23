package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.HubSettingsService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Hub-Editor: Border, Zeit, Wetter, Musik und Begrüßung. */
public final class HubEditorMenu extends GuiMenu {

    public HubEditorMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>Hub-Editor"), 3);
    }

    @Override
    protected void build(Player p) {
        HubSettingsService h = plugin.hubSettings();
        for (int i = 0; i < 27; i++) set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);

        set(10, Items.make(Material.WHITE_STAINED_GLASS, "<white>Worldborder: <aqua>" + (int) h.border(), List.of(
                "<gray>Linksklick: +10", "<gray>Rechtsklick: -10")), e -> {
            double next = h.border() + (e.isRightClick() ? -10 : 10);
            next = Math.max(20, Math.min(next, plugin.getConfig().getDouble("worldbuilder.max-size", 1000)));
            h.set("border-size", next);
            h.applyAll();
            tick(p);
        });

        set(12, Items.make(Material.CLOCK, "<yellow>Tageszeit", List.of(
                "<gray>Aktuell: <white>" + h.time() + " Ticks",
                "<yellow>Klick: nächste Zeit")), e -> {
            int idx = HubSettingsService.TIMES.indexOf(h.time());
            h.set("time", HubSettingsService.TIMES.get((idx + 1) % HubSettingsService.TIMES.size()));
            h.applyAll();
            tick(p);
        });
        set(13, Items.make(h.timeLock() ? Material.IRON_BARS : Material.REDSTONE, "<yellow>Zeit sperren: "
                + (h.timeLock() ? "<green>an" : "<red>aus"), List.of("<yellow>Klick: umschalten")), e -> {
            h.set("time-lock", !h.timeLock());
            h.applyAll();
            tick(p);
        });

        set(14, Items.make(Material.WATER_BUCKET, "<blue>Wetter: <white>" + h.weather(), List.of(
                "<yellow>Klick: nächstes Wetter")), e -> {
            int idx = HubSettingsService.WEATHER.indexOf(h.weather());
            h.set("weather", HubSettingsService.WEATHER.get((idx + 1) % HubSettingsService.WEATHER.size()));
            h.applyAll();
            tick(p);
        });
        set(15, Items.make(h.weatherLock() ? Material.IRON_BARS : Material.REDSTONE, "<blue>Wetter sperren: "
                + (h.weatherLock() ? "<green>an" : "<red>aus"), List.of("<yellow>Klick: umschalten")), e -> {
            h.set("weather-lock", !h.weatherLock());
            h.applyAll();
            tick(p);
        });

        set(16, Items.make(Material.NOTE_BLOCK, "<light_purple>Musik", List.of(
                "<gray>Aktuell: <white>" + (h.music().isBlank() ? "keine" : h.music()),
                "<yellow>Klick: Sound-Key eingeben", "<gray>'aus' schaltet ab")), e -> {
            plugin.prompts().ask(p, "Sound-Key eingeben (z. B. minecraft:music.game) oder 'aus':", text -> {
                h.set("music", text.equalsIgnoreCase("aus") ? "" : text.trim());
                Msg.send(p, "<green>Musik gespeichert.");
                open(p);
            });
            p.closeInventory();
        });

        set(22, Items.make(Material.WRITABLE_BOOK, "<green>Begrüßungstext", List.of(
                "<gray>Aktuell: " + h.joinText(),
                "<yellow>Klick: neuen Text eingeben")), e -> {
            plugin.prompts().ask(p, "Begrüßungstext eingeben (MiniMessage erlaubt):", text -> {
                h.set("join-text", text);
                Msg.send(p, "<green>Begrüßung gespeichert.");
                open(p);
            });
            p.closeInventory();
        });

        set(26, Items.make(Material.ARROW, "<gray>Schließen", List.of()), e -> p.closeInventory());
    }

    private void tick(Player p) {
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
        open(p);
    }
}
