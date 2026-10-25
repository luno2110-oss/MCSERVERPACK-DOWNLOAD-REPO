package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dauer-Anzeige über der Hotbar nach dem Schalter "Actionbar" in /settings: Geld + XP (mit Zählanimation), nur Geld,
 * nur XP, alle Währungen, Koordinaten, Uhrzeit oder nichts. Kurze Meldungen ({@link #flash}, z. B. Monster-Warnung,
 * Verkaufswert) überdecken die Anzeige für ein paar Ticks. Im Handel wird die Anzeige nicht überschrieben.
 */
public final class HotbarDisplay {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final String[] DIRS_DE = {"Süd", "Südwest", "West", "Nordwest", "Nord", "Nordost", "Ost", "Südost"};
    private static final String[] DIRS_EN = {"South", "Southwest", "West", "Northwest", "North", "Northeast", "East", "Southeast"};

    private record Flash(Component text, long until, int priority) {}

    private final KubyPlugins plugin;
    private final Map<UUID, Flash> flashes = new HashMap<>();
    private long ticks;

    public HotbarDisplay(KubyPlugins plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    /** Kurze Meldung für ticks Ticks; eine laufende Meldung mit höherer Priorität bleibt stehen. */
    public void flash(Player p, Component text, int ticks, int priority) {
        Flash cur = flashes.get(p.getUniqueId());
        if (cur != null && cur.until() > this.ticks && cur.priority() > priority) return;
        flashes.put(p.getUniqueId(), new Flash(text, this.ticks + ticks, priority));
        p.sendActionBar(text);
    }

    private void tick() {
        ticks += 2;
        boolean enabled = plugin.getConfig().getBoolean("display.hotbar", true);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (plugin.trades().inTrade(id)) continue;
            Flash f = flashes.get(id);
            if (f != null) {
                if (f.until() > ticks) {
                    p.sendActionBar(f.text());
                    continue;
                }
                flashes.remove(id);
            }
            if (!enabled) continue;
            if (plugin.modes().isHub(p.getWorld())) continue;
            String mode = plugin.playerSettings() == null ? "money_xp" : plugin.playerSettings().choice(id, "actionbar");
            // Survival-Leiste oben zeigt Geld schon: dann nur Anzeigen ohne Geld (XP, Koordinaten, Uhrzeit)
            if (plugin.hotbarHidden(p) && (mode.equals("money_xp") || mode.equals("money") || mode.equals("all"))) continue;
            String mini = line(p, mode);
            if (mini != null) p.sendActionBar(Msg.parse(mini));
        }
    }

    private String line(Player p, String mode) {
        UUID id = p.getUniqueId();
        // Münz- und XP-Icon aus dem Pack KubyUI, ohne Pack nur Text.
        boolean icons = plugin.uiPack().active(p);
        String coin = icons ? "<font:kubyplugins:sb></font> " : "";
        String star = icons ? "<font:kubyplugins:sb></font> " : "<#ff9a1f>XP ";
        String sep = " <dark_gray>|</dark_gray> ";
        boolean en = plugin.playerSettings() != null && plugin.playerSettings().english(id);
        return switch (mode) {
            case "off" -> null;
            case "money" -> coin + plugin.moneyText(p);
            case "xp" -> star + "<#ff9a1f>" + Msg.coins(plugin.stats().get(id).xp);
            case "all" -> {
                StringBuilder sb = new StringBuilder(coin + plugin.moneyText(p));
                sb.append(sep).append("<#5FE3A0>").append(Msg.coins(plugin.jewels().balance(id))).append(en ? " jewels" : " Juwelen");
                if (plugin.hasStat("tokens")) sb.append(sep).append("<#D08CFF>").append(Msg.coins(plugin.statValue("tokens", id))).append(" Tokens");
                sb.append(sep).append(star).append("<#ff9a1f>").append(Msg.coins(plugin.stats().get(id).xp));
                yield sb.toString();
            }
            case "coords" -> {
                Location l = p.getLocation();
                int dir = Math.floorMod(Math.round(l.getYaw() / 45f), 8);
                yield "<gray>X <white>" + l.getBlockX() + " <gray>Y <white>" + l.getBlockY() + " <gray>Z <white>" + l.getBlockZ()
                        + sep + "<gray>" + (en ? DIRS_EN : DIRS_DE)[dir];
            }
            case "time" -> {
                ZonedDateTime now = ZonedDateTime.now(BERLIN);
                yield "<gray>" + (en ? "Time " : "Uhrzeit ") + "<white>" + String.format("%02d:%02d", now.getHour(), now.getMinute());
            }
            default -> coin + plugin.moneyText(p) + sep + star + "<#ff9a1f>" + Msg.coins(plugin.stats().get(id).xp);
        };
    }

    public void forget(UUID id) {
        flashes.remove(id);
    }
}
