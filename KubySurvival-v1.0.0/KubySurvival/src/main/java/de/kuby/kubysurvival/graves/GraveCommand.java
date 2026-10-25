package de.kuby.kubysurvival.graves;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;

/**
 * /grab: deine Gräber mit Welt, Koordinaten, Restzeit und Inhalt (Kopf wie die Hilfe-Seiten der Referenz).
 * Klick auf ein Grab richtet deinen Kompass darauf aus.
 */
public final class GraveCommand implements CommandExecutor {

    private static final int W = 390;
    private static final TextColor TITLE = TextColor.color(0xD2D2DA), GRAY = TextColor.color(0xA0A0AC),
            LIGHT = TextColor.color(0xE4E4EA), GOLD = TextColor.color(0xF8D35A), GREEN = TextColor.color(0x6CE86E),
            RED = TextColor.color(0xFF6A6A);

    private final KubyPlugins core;
    private final GraveService graves;

    public GraveCommand(KubyPlugins core, GraveService graves) {
        this.core = core;
        this.graves = graves;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) {
            GraveService.send(p, "<red>Gräber gibt es nur in Survival.");
            return true;
        }
        open(p);
        return true;
    }

    public void open(Player p) {
        List<GraveService.Grave> list = graves.of(p.getUniqueId());
        Canvas c = new Canvas(W);
        c.box(0, 0, W, 51, KitData.Box.GRAY);
        c.smallIcon(13, 16, KitData.SmallIcon.H_GRAVE);
        c.text(41, 15, "DEINE GRÄBER", TITLE, true);
        c.text(41, 28, "Welt, Koordinaten und Restzeit", GRAY, false);
        c.textRight(378, 21, list.size() == 1 ? "1 Grab" : list.size() + " Gräber", GRAY, false);
        int y = 53;
        if (list.isEmpty()) {
            c.box(0, y, W, 43, KitData.Box.GRAY);
            c.text(9, y + 10, "KEIN GRAB", TITLE, true);
            c.text(12, y + 24, "Du hast gerade kein Grab. Stirbst du, entsteht dort ein Grabstein mit deinen Sachen.", LIGHT, false);
        }
        for (int i = 0; i < list.size() && i < 6; i++) {
            GraveService.Grave g = list.get(i);
            Location l = g.at;
            boolean here = l.getWorld() != null && l.getWorld().equals(p.getWorld());
            String dist = here ? " · " + (int) Math.round(l.distance(p.getLocation())) + " Blöcke entfernt" : "";
            c.box(0, y, W, 43, KitData.Box.GRAY);
            c.smallIcon(8, y + 12, KitData.SmallIcon.H_GRAVE);
            c.text(32, y + 10, GraveService.worldName(l.getWorld()) + "  " + l.getBlockX() + " / " + l.getBlockY() + " / " + l.getBlockZ(),
                    LIGHT, true);
            c.text(32, y + 24, g.count() + " Items" + (g.xp > 0 ? " · " + g.xp + " XP" : "") + dist, GRAY, false);
            long left = g.left();
            c.textRight(378, y + 10, "noch " + GraveService.time(left), left < 300_000 ? RED : GREEN, true);
            c.textRight(378, y + 24, here ? "Kompass ausrichten ›" : "andere Welt", here ? GOLD : GRAY, false);
            if (here) {
                c.action(0, y, W, 43, pl -> {
                    pl.setCompassTarget(l);
                    GraveService.send(pl, "Dein Kompass zeigt jetzt auf das Grab in <white>" + l.getBlockX() + " / " + l.getBlockY()
                            + " / " + l.getBlockZ() + "</white>.");
                    pl.closeDialog();
                }, "Kompass auf dieses Grab ausrichten");
            }
            y += 46;
        }
        UiDialog.show(p, c, 120);
    }
}
