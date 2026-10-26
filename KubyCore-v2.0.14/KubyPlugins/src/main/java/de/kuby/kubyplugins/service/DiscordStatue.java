package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Discord-Statue wie in der Referenz: Wumpus (Item-Modell kuby:deco/wumpus aus dem Grafik-Pack), darüber „DISCORD“
 * (fett, blau, mit Schatten). Ein Klick (rechts oder links) schreibt den Einladungslink und den Booster-Hinweis in den
 * Chat. Aufstellen mit /kp deko discord (auf den Sockel stellen, Blick zur Wand), entfernen mit /kp deko remove; wer
 * „Deko-NPCs“ ausschaltet, sieht sie nicht. Die drei Teile (Figur, Schrift, Klickfläche) tragen dieselbe Gruppen-ID.
 */
public final class DiscordStatue implements Listener {

    /** Pink des Booster-Tags (wie in der Referenz). */
    public static final String PINK = "#FF6BD6";
    /** „BOOSTER“ in Kapitälchen wie im Chat der Referenz. */
    public static final String BOOSTER = de.kuby.kubyplugins.util.SmallCaps.of("booster");
    private static final long COOLDOWN_MS = 2000;

    private final KubyPlugins plugin;
    private final NamespacedKey key;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public DiscordStatue(KubyPlugins plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "statue");
    }

    /** Stellt die Statue auf den Block, in dem feet liegt (Mitte); die Vorderseite zeigt nach yaw. */
    public void spawn(Location feet, float yaw) {
        Location base = feet.getBlock().getLocation().add(0.5, 0, 0.5);
        base.setYaw(yaw);
        base.setPitch(0);
        World w = base.getWorld();
        String group = "discord:" + UUID.randomUUID();
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("kuby", "deco/wumpus"));
        item.setItemMeta(meta);
        ItemDisplay figure = w.spawn(base.clone().add(0, 0.5, 0), ItemDisplay.class, e -> {
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            prepare(e, group);
        });
        TextDisplay sign = w.spawn(base.clone().add(0, 1.35, 0), TextDisplay.class, e -> {
            e.text(Component.text("DISCORD", NamedTextColor.BLUE, TextDecoration.BOLD));
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setBillboard(Display.Billboard.FIXED);
            prepare(e, group);
        });
        Interaction hit = w.spawn(base, Interaction.class, e -> {
            e.setInteractionWidth(0.9f);
            e.setInteractionHeight(1.1f);
            e.setResponsive(true);
            prepare(e, group);
        });
        for (Entity e : List.of(figure, sign, hit)) plugin.displaySettings().markDeco(e);
    }

    private void prepare(Entity e, String group) {
        e.setPersistent(true);
        e.setSilent(true);
        e.getPersistentDataContainer().set(key, PersistentDataType.STRING, group);
    }

    /** Entfernt die nächste Statue im Umkreis (alle drei Teile). False, wenn keine da ist. */
    public boolean remove(Location at, double range) {
        String group = null;
        double best = range * range;
        for (Entity e : at.getWorld().getNearbyEntities(at, range, range, range)) {
            String g = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (g == null) continue;
            double d = e.getLocation().distanceSquared(at);
            if (d <= best) {
                best = d;
                group = g;
            }
        }
        if (group == null) return false;
        List<Entity> parts = new ArrayList<>();
        for (Entity e : at.getWorld().getNearbyEntities(at, range + 3, range + 3, range + 3)) {
            if (group.equals(e.getPersistentDataContainer().get(key, PersistentDataType.STRING))) parts.add(e);
        }
        parts.forEach(Entity::remove);
        return true;
    }

    /** Teil einer Statue? */
    public boolean isStatue(Entity e) {
        return e.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    // =================================================================================================================
    // Klick und Nachricht
    // =================================================================================================================

    @EventHandler
    public void onUse(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Interaction) || !isStatue(e.getRightClicked())) return;
        e.setCancelled(true);
        clicked(e.getPlayer());
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent e) {
        if (!(e.getAttacked() instanceof Interaction) || !isStatue(e.getAttacked())) return;
        e.setCancelled(true);
        clicked(e.getPlayer());
    }

    private void clicked(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) return;
        lastClick.put(p.getUniqueId(), now);
        sendInfo(p);
        Sfx.play(plugin, p, Sfx.Kind.CLICK);
    }

    public void forget(UUID id) {
        lastClick.remove(id);
    }

    /** Die beiden Zeilen aus der Referenz: Einladungslink und Booster-Hinweis (auch für /discord). */
    public void sendInfo(Player p) {
        boolean en = plugin.playerSettings().english(p.getUniqueId());
        String link = plugin.getConfig().getString("discord.invite", "https://discord.gg/");
        String head = "<blue><b>DISCORD</b></blue> <dark_gray>▸</dark_gray> ";
        p.sendMessage(Msg.parse(head + "<white>" + (en ? "Click here: " : "Klicke hier: ") + "</white><click:open_url:'" + link
                + "'><blue><u>" + Msg.escape(link) + "</u></blue></click>"));
        p.sendMessage(Msg.parse(head + "<" + PINK + ">♥</" + PINK + "> <gray>" + (en ? "Server boosters wear the tag "
                : "Server-Booster tragen im Spiel den Tag ") + "[</gray><" + PINK + "><b>" + BOOSTER + "</b></" + PINK + "><gray>] — "
                + (en ? "link with " : "verlinken mit ") + "<yellow>/verify</yellow>.</gray>"));
    }
}
