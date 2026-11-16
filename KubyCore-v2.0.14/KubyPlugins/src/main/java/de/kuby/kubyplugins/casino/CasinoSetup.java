package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CasinoService;
import de.kuby.kubyplugins.service.RegionService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casino einrichten mit Werkzeugen im Inventar (/casino einrichten, nur Admins):
 * <ol>
 *   <li>Bereichs-Stab: Linksklick auf einen Block = Ecke 1, Rechtsklick = Ecke 2. Der Rahmen wird mit Partikeln gezeigt.</li>
 *   <li>Im Chat bestätigen: legt die Region "casino" an (geschützt; Musik, Casino-Scoreboard und Rundgang hängen daran).</li>
 *   <li>Spawn-Feder: Rechtsklick an der Stelle, an der Spieler mit /casino ankommen (mit Blickrichtung), dann bestätigen.</li>
 * </ol>
 * Liegen beide Ecken fast auf einer Höhe (Boden angeklickt), wird der Bereich automatisch nach unten und oben erweitert.
 */
public final class CasinoSetup implements Listener {

    private enum Step { AREA, SPAWN }

    private static final class State {
        Step step = Step.AREA;
        Location a, b;
        Location spawn;
    }

    private final KubyPlugins plugin;
    private final NamespacedKey key;
    private final Map<UUID, State> states = new HashMap<>();

    public CasinoSetup(KubyPlugins plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "kp_casino_setup");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::preview, 20L, 10L);
    }

    // =================================================================================================================
    // Befehl
    // =================================================================================================================

    /** /casino einrichten [ok|spawn|abbrechen|neu] */
    public void command(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins können das Casino einrichten.");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        State st = states.get(p.getUniqueId());
        switch (sub) {
            case "ok" -> confirmArea(p, st);
            case "spawn" -> confirmSpawn(p, st);
            case "abbrechen", "stop" -> {
                if (st == null) {
                    Msg.send(p, "<gray>Es läuft keine Einrichtung.");
                } else {
                    finish(p);
                    Msg.send(p, "<gray>Casino-Einrichtung abgebrochen.");
                }
            }
            case "neu" -> {
                if (st != null) {
                    st.a = null;
                    st.b = null;
                    st.step = Step.AREA;
                }
                start(p);
            }
            default -> start(p);
        }
    }

    private void start(Player p) {
        State st = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        removeTools(p);
        if (st.step == Step.AREA) give(p, areaTool());
        else give(p, spawnTool());
        Sfx.play(plugin, p, Sfx.Kind.OPEN);
        Msg.send(p, "<#F2C94C><b>CASINO EINRICHTEN</b>");
        RegionService.Region old = plugin.regions().find(CasinoService.REGION);
        if (old != null) {
            Msg.send(p, "<gray>Es gibt schon einen Casino-Bereich (" + size(old) + "). Wenn du einen neuen bestätigst, ersetzt er den alten.");
        }
        if (st.step == Step.AREA) {
            Msg.send(p, "<gray>1. Nimm den <#F2C94C>Casino-Bereich</#F2C94C>-Stab: <white>Linksklick</white> auf einen Block = Ecke 1, "
                    + "<white>Rechtsklick</white> = Ecke 2 (gegenüberliegende Ecken).");
            Msg.send(p, "<gray>2. Im Chat bestätigen. 3. Dann den Spawnpunkt für Spieler setzen.");
        } else {
            spawnHint(p);
        }
        Msg.send(p, "<click:run_command:'/casino einrichten abbrechen'><dark_gray>[Abbrechen]</click>");
    }

    // =================================================================================================================
    // Werkzeuge
    // =================================================================================================================

    private ItemStack tool(Material m, String id, String name, List<String> lore) {
        ItemStack it = Items.make(m, name, lore);
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack areaTool() {
        return tool(Material.BLAZE_ROD, "area", "<#F2C94C><b>Casino-Bereich",
                List.of("<gray>Linksklick auf Block: <white>Ecke 1", "<gray>Rechtsklick auf Block: <white>Ecke 2",
                        "<dark_gray>Danach im Chat bestätigen"));
    }

    private ItemStack spawnTool() {
        return tool(Material.FEATHER, "spawn", "<#7FE3FF><b>Casino-Spawn",
                List.of("<gray>Rechtsklick: <white>hier kommen Spieler an", "<gray>(deine Position und Blickrichtung)",
                        "<dark_gray>Danach im Chat bestätigen"));
    }

    private String toolId(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** Werkzeug in die Hand (freier Platz in der Hotbar bevorzugt). false, wenn das Inventar voll ist. */
    private static boolean give(Player p, ItemStack it) {
        var inv = p.getInventory();
        ItemStack held = inv.getItem(inv.getHeldItemSlot());
        if (held == null || held.getType().isAir()) {
            inv.setItem(inv.getHeldItemSlot(), it);
            return true;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) {
                inv.setItem(i, it);
                inv.setHeldItemSlot(i);
                return true;
            }
        }
        if (inv.firstEmpty() < 0) {
            Msg.send(p, "<red>Dein Inventar ist voll – mach einen Platz frei und gib <white>/casino einrichten</white> noch einmal ein.");
            return false;
        }
        inv.addItem(it);
        Msg.send(p, "<gray>Das Werkzeug liegt in deinem Inventar (Hotbar war voll).");
        return true;
    }

    private void removeTools(Player p) {
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) if (toolId(c[i]) != null) p.getInventory().setItem(i, null);
    }

    // =================================================================================================================
    // Klicks
    // =================================================================================================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        String id = toolId(e.getItem());
        if (id == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        State st = states.get(p.getUniqueId());
        if (st == null || !p.hasPermission("kuby.admin")) {
            removeTools(p);
            return;
        }
        Action a = e.getAction();
        if (id.equals("area")) {
            if (e.getClickedBlock() == null) {
                p.sendActionBar(Msg.parse("<gray>Klick auf einen Block (Linksklick Ecke 1, Rechtsklick Ecke 2)"));
                return;
            }
            Location at = e.getClickedBlock().getLocation();
            if (a == Action.LEFT_CLICK_BLOCK) st.a = at;
            else if (a == Action.RIGHT_CLICK_BLOCK) st.b = at;
            else return;
            Sfx.play(plugin, p, Sfx.Kind.PING);
            Msg.send(p, "<gray>Ecke " + (a == Action.LEFT_CLICK_BLOCK ? "1" : "2") + ": <white>" + at.getBlockX() + " / " + at.getBlockY() + " / " + at.getBlockZ());
            if (st.a != null && st.b != null) askArea(p, st);
        } else if (id.equals("spawn")) {
            if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
            Location l = p.getLocation().clone();
            // auf die Blockmitte, Blickrichtung auf 45° gerundet (sauberer Anblick beim Ankommen), leicht nach unten
            l.setX(l.getBlockX() + 0.5);
            l.setZ(l.getBlockZ() + 0.5);
            l.setYaw(Math.round(l.getYaw() / 45f) * 45f);
            l.setPitch(Math.max(-10f, Math.min(20f, l.getPitch())));
            RegionService.Region r = plugin.regions().find(CasinoService.REGION);
            if (r != null && !r.contains(l)) {
                Sfx.play(plugin, p, Sfx.Kind.ERROR);
                Msg.send(p, "<red>Der Spawnpunkt muss im Casino-Bereich liegen.");
                return;
            }
            st.spawn = l;
            Sfx.play(plugin, p, Sfx.Kind.PING);
            Msg.send(p, "<gray>Spawnpunkt: <white>" + l.getBlockX() + " / " + l.getBlockY() + " / " + l.getBlockZ() + "</white> mit Blick nach <white>"
                    + compass(l.getYaw()) + "</white>.  <click:run_command:'/casino einrichten spawn'><green><b>[Bestätigen]</b></green></click>");
        }
    }

    /** Linksklick mit dem Stab soll im Kreativmodus keinen Block abbauen. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        if (toolId(e.getPlayer().getInventory().getItemInMainHand()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (toolId(e.getItemDrop().getItemStack()) != null) {
            e.getItemDrop().remove();
            Msg.send(e.getPlayer(), "<gray>Werkzeug weggelegt. Abbrechen: <white>/casino einrichten abbrechen");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (states.remove(e.getPlayer().getUniqueId()) != null) removeTools(e.getPlayer());
    }

    // =================================================================================================================
    // Bestätigen
    // =================================================================================================================

    /** Höhe: Bodenecken (fast gleich hoch) werden 5 nach unten und 30 nach oben erweitert. */
    private static int[] yRange(Location a, Location b) {
        int lo = Math.min(a.getBlockY(), b.getBlockY()), hi = Math.max(a.getBlockY(), b.getBlockY());
        if (hi - lo < 6) {
            lo -= 5;
            hi = Math.max(hi, lo + 5) + 30;
        }
        return new int[]{lo, hi};
    }

    private void askArea(Player p, State st) {
        if (!st.a.getWorld().equals(st.b.getWorld())) {
            Msg.send(p, "<red>Beide Ecken müssen in derselben Welt liegen.");
            return;
        }
        int[] y = yRange(st.a, st.b);
        int dx = Math.abs(st.a.getBlockX() - st.b.getBlockX()) + 1, dz = Math.abs(st.a.getBlockZ() - st.b.getBlockZ()) + 1;
        boolean auto = Math.abs(st.a.getBlockY() - st.b.getBlockY()) < 6;
        Msg.send(p, "<gray>Bereich: <white>" + dx + " x " + dz + "</white> Blöcke, Höhe <white>" + y[0] + " bis " + y[1] + "</white>"
                + (auto ? " <dark_gray>(automatisch erweitert, weil beide Ecken fast gleich hoch sind)" : ""));
        Msg.send(p, "<click:run_command:'/casino einrichten ok'><green><b>[Bestätigen]</b></green></click>  "
                + "<click:run_command:'/casino einrichten neu'><yellow>[Neu setzen]</yellow></click>  "
                + "<click:run_command:'/casino einrichten abbrechen'><dark_gray>[Abbrechen]</dark_gray></click>");
    }

    private void confirmArea(Player p, State st) {
        if (st == null || st.a == null || st.b == null) {
            Msg.send(p, "<red>Setze erst beide Ecken mit dem Casino-Bereich-Stab. <gray>/casino einrichten");
            return;
        }
        if (!st.a.getWorld().equals(st.b.getWorld())) {
            Msg.send(p, "<red>Beide Ecken müssen in derselben Welt liegen.");
            return;
        }
        int[] y = yRange(st.a, st.b);
        World w = st.a.getWorld();
        Location lo = new Location(w, Math.min(st.a.getBlockX(), st.b.getBlockX()), y[0], Math.min(st.a.getBlockZ(), st.b.getBlockZ()));
        Location hi = new Location(w, Math.max(st.a.getBlockX(), st.b.getBlockX()), y[1], Math.max(st.a.getBlockZ(), st.b.getBlockZ()));
        plugin.regions().delete(CasinoService.REGION);
        String err = plugin.regions().create(CasinoService.REGION, lo, hi, "-");
        if (err != null) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>" + err);
            return;
        }
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        Msg.send(p, "<green>Casino-Bereich gespeichert.</green> <gray>Musik, Casino-Scoreboard und Rundgang gelten jetzt dort; bauen dürfen dort nur Admins.");
        st.step = Step.SPAWN;
        removeTools(p);
        give(p, spawnTool());
        spawnHint(p);
        // wer schon drinsteht, bekommt Musik und Scoreboard sofort
        for (Player o : w.getPlayers()) plugin.casinoMusic().update(o, o.getLocation());
    }

    private void spawnHint(Player p) {
        Msg.send(p, "<gray>Jetzt der Spawnpunkt: stell dich dahin, wo Spieler mit <white>/casino</white> ankommen sollen, schau in die "
                + "Richtung, in die sie schauen sollen, und mach mit der <#7FE3FF>Casino-Spawn</#7FE3FF>-Feder einen <white>Rechtsklick</white>.");
    }

    private void confirmSpawn(Player p, State st) {
        if (st == null || st.spawn == null) {
            Msg.send(p, "<red>Setze erst den Spawnpunkt mit der Casino-Spawn-Feder (Rechtsklick).");
            return;
        }
        plugin.casino().setSpawn(st.spawn);
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS_BIG);
        finish(p);
        Msg.send(p, "<green><b>Casino eingerichtet!</b></green> <gray>/casino bringt Spieler jetzt zum Spawnpunkt.");
        Msg.send(p, "<gray>Als Nächstes: <click:suggest_command:'/casino npc '><white>[Kasse/Jukebox-NPC]</white></click> "
                + "<click:suggest_command:'/casino blackjack tisch '><white>[Blackjack-Tisch]</white></click> "
                + "<click:suggest_command:'/casino poker-tisch '><white>[Poker-Tisch]</white></click> "
                + "<click:suggest_command:'/casino slots automat'><white>[Slot-Automat]</white></click> "
                + "<click:run_command:'/casino rundgang'><white>[Rundgang testen]</white></click>");
    }

    private void finish(Player p) {
        states.remove(p.getUniqueId());
        removeTools(p);
    }

    /**
     * CI-Test (/kubytest einrichten): der gleiche Ablauf wie mit den Werkzeugen – zwei Bodenecken, Bestätigen,
     * Spawnpunkt, Bestätigen. Gibt null zurück, wenn alles stimmt, sonst den Fehler.
     */
    public String test(Player p, Location a, Location b, Location spawn) {
        State st = new State();
        st.a = a;
        st.b = b;
        states.put(p.getUniqueId(), st);
        confirmArea(p, st);
        RegionService.Region r = plugin.regions().find(CasinoService.REGION);
        if (r == null) return "Region wurde nicht angelegt";
        if (r.y2() - r.y1() < 30) return "Höhe nicht erweitert (" + r.y1() + " bis " + r.y2() + ")";
        st.spawn = spawn;
        confirmSpawn(p, st);
        Location sp = plugin.casino().spawn();
        if (sp == null || sp.distanceSquared(spawn) > 0.01) return "Spawnpunkt nicht gespeichert";
        if (!plugin.casinoMusic().inCasino(sp)) return "Spawnpunkt liegt nicht im Casino";
        if (states.containsKey(p.getUniqueId())) return "Einrichtung nicht abgeschlossen";
        for (ItemStack it : p.getInventory().getContents()) if (toolId(it) != null) return "Werkzeug blieb im Inventar";
        return null;
    }

    // =================================================================================================================
    // Vorschau
    // =================================================================================================================

    /** Rahmen der Auswahl (bzw. des gespeicherten Bereichs) mit Partikeln, nur für den Einrichtenden. */
    private void preview() {
        for (Map.Entry<UUID, State> e : states.entrySet()) {
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p == null) continue;
            State st = e.getValue();
            if (st.a != null && st.b != null && st.a.getWorld().equals(p.getWorld())) {
                int[] y = yRange(st.a, st.b);
                box(p, Math.min(st.a.getBlockX(), st.b.getBlockX()), y[0], Math.min(st.a.getBlockZ(), st.b.getBlockZ()),
                        Math.max(st.a.getBlockX(), st.b.getBlockX()) + 1, y[1] + 1, Math.max(st.a.getBlockZ(), st.b.getBlockZ()) + 1,
                        Color.fromRGB(0xF2C94C));
            } else {
                for (Location c : new Location[]{st.a, st.b}) {
                    if (c != null && c.getWorld().equals(p.getWorld())) {
                        box(p, c.getBlockX(), c.getBlockY(), c.getBlockZ(), c.getBlockX() + 1, c.getBlockY() + 1, c.getBlockZ() + 1, Color.fromRGB(0xF2C94C));
                    }
                }
            }
            if (st.spawn != null && st.spawn.getWorld().equals(p.getWorld())) {
                p.spawnParticle(Particle.DUST, st.spawn.clone().add(0, 0.1, 0), 6, 0.2, 0.0, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(0x7FE3FF), 1.2f));
            }
        }
    }

    /** Kanten eines Quaders; nur Teile nahe beim Spieler (sonst sind es zu viele Partikel). */
    private static void box(Player p, double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
        Location me = p.getLocation();
        double[][] edges = {
                {x1, y1, z1, x2, y1, z1}, {x1, y1, z2, x2, y1, z2}, {x1, y1, z1, x1, y1, z2}, {x2, y1, z1, x2, y1, z2},
                {x1, y2, z1, x2, y2, z1}, {x1, y2, z2, x2, y2, z2}, {x1, y2, z1, x1, y2, z2}, {x2, y2, z1, x2, y2, z2},
                {x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1}, {x1, y1, z2, x1, y2, z2}, {x2, y1, z2, x2, y2, z2}};
        int budget = 600;
        for (double[] ed : edges) {
            double len = Math.sqrt(Math.pow(ed[3] - ed[0], 2) + Math.pow(ed[4] - ed[1], 2) + Math.pow(ed[5] - ed[2], 2));
            int n = (int) Math.ceil(len);
            for (int i = 0; i <= n && budget > 0; i++) {
                double t = n == 0 ? 0 : i / (double) n;
                double x = ed[0] + (ed[3] - ed[0]) * t, y = ed[1] + (ed[4] - ed[1]) * t, z = ed[2] + (ed[5] - ed[2]) * t;
                double dx = x - me.getX(), dy = y - me.getY(), dz = z - me.getZ();
                if (dx * dx + dy * dy + dz * dz > 48 * 48) continue;
                p.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
                budget--;
            }
        }
    }

    private static String size(RegionService.Region r) {
        return (r.x2() - r.x1() + 1) + " x " + (r.z2() - r.z1() + 1) + " Blöcke in " + r.world();
    }

    private static String compass(float yaw) {
        String[] d = {"Süden", "Südwesten", "Westen", "Nordwesten", "Norden", "Nordosten", "Osten", "Südosten"};
        int i = Math.floorMod(Math.round(yaw / 45f), 8);
        return d[i];
    }
}
