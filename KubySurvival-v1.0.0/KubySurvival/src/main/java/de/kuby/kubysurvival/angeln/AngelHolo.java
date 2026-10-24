package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.ui.Sym;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Hologramm an der Angel-Zone wie in der Referenz: großer Titel "ANGELZONE" im Blau-Verlauf, darunter grau
 * "Der beste Platz zum Angeln" und Zeilen mit kleinen Symbolen. Zwei Text-Displays ohne Hintergrund.
 */
public final class AngelHolo {

    private final NamespacedKey tag;

    public AngelHolo(KubySurvival addon) {
        this.tag = new NamespacedKey(addon, "angel_holo");
    }

    static Component line(Sym sym, Component text) {
        return Component.text().append(sym.component()).append(Component.text(" ")).append(text).build();
    }

    static Component body() {
        TextColor gray = TextColor.color(0xB8BEC4);
        TextComponent.Builder b = Component.text();
        b.append(Component.text("Der beste Platz zum Angeln", gray)).append(Component.newline()).append(Component.newline());
        b.append(line(Sym.ROD, Component.text("Hier in der Zone beißt es deutlich besser", NamedTextColor.WHITE)));
        b.append(Component.newline());
        b.append(line(Sym.GEM, Component.text("Seltene Fische und Schätze gibt es nur hier", NamedTextColor.WHITE)));
        b.append(Component.newline());
        b.append(line(Sym.BOOK, Component.text().append(Component.text("Dein Menü öffnest du mit ", NamedTextColor.WHITE))
                .append(Component.text("/angeln", NamedTextColor.YELLOW)).build()));
        b.append(Component.newline());
        b.append(line(Sym.ANVIL, Component.text("Dort kaufst du Upgrades für deine Rute", NamedTextColor.WHITE)));
        b.append(Component.newline());
        b.append(line(Sym.FISH, Component.text().append(Component.text("Deinen Fang verkaufst du direkt gegen ", NamedTextColor.WHITE))
                .append(Component.text("Coins", TextColor.color(0xFFAA00))).build()));
        b.append(Component.newline());
        b.append(line(Sym.SPARKLE, Component.text().append(Component.text("Je besser die Rute, desto fetter der Fang ", NamedTextColor.WHITE))
                .append(Sym.SPARKLE.component()).build()));
        return b.build();
    }

    /** Stellt das Hologramm einer Zone (neu) auf. */
    public void spawn(AngelService.Zone z) {
        remove(z);
        Location at = z.holo;
        if (at == null || at.getWorld() == null) return;
        World w = at.getWorld();
        Location bodyAt = at.clone();
        Location titleAt = at.clone().add(0, 1.95, 0);
        w.spawn(titleAt, TextDisplay.class, d -> {
            d.text(Msg.parse("<b><gradient:#9BEBFF:#4FB4FF:#2E7FE8>ANGELZONE</gradient></b>"));
            style(d, 3.2f, z.id);
        });
        w.spawn(bodyAt, TextDisplay.class, d -> {
            d.text(body());
            style(d, 1.15f, z.id);
        });
    }

    private void style(TextDisplay d, float scale, String id) {
        d.setBillboard(Display.Billboard.VERTICAL);
        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        d.setShadowed(true);
        d.setLineWidth(400);
        d.setPersistent(true);
        d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale),
                new AxisAngle4f()));
        d.getPersistentDataContainer().set(tag, PersistentDataType.STRING, id);
    }

    public void remove(AngelService.Zone z) {
        Location at = z.holo;
        if (at == null || at.getWorld() == null) return;
        for (TextDisplay d : at.getWorld().getNearbyEntitiesByType(TextDisplay.class, at, 6)) {
            if (z.id.equals(d.getPersistentDataContainer().get(tag, PersistentDataType.STRING))) d.remove();
        }
    }
}
