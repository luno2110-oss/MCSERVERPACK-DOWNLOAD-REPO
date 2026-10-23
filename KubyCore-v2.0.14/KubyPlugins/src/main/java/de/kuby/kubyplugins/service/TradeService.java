package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handel von Hand zu Hand im 6-Reihen-GUI.
 * Links (Spalten 0-3) legt jeder sein Angebot, rechts (Spalten 5-8) sieht man das Angebot des Partners.
 * Die mittlere Spalte ist der Timer: Haben beide angenommen, füllt sich die Spalte in 5 Sekunden mit grünem Glas.
 * Jede Änderung am Angebot oder ein Abbruch stoppt den Timer.
 */
public final class TradeService {

    public static final int CONFIRM_SLOT = 45;
    public static final int STATUS_SLOT = 49;
    public static final int CANCEL_SLOT = 53;
    private static final int TIMER_SECONDS = 5;
    private static final long REQUEST_TIMEOUT_MS = 60_000L;

    public static final class TradeHolder implements InventoryHolder {
        public final TradeSession session;
        public final UUID owner;
        Inventory inv;

        TradeHolder(TradeSession session, UUID owner) {
            this.session = session;
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() { return inv; }
    }

    public static final class TradeSession {
        final UUID a;
        final UUID b;
        public final TradeHolder ha;
        public final TradeHolder hb;
        public boolean confirmA;
        public boolean confirmB;
        public boolean closing;
        int countdown = -1;
        BukkitTask timer;

        TradeSession(UUID a, UUID b) {
            this.a = a;
            this.b = b;
            this.ha = new TradeHolder(this, a);
            this.hb = new TradeHolder(this, b);
        }

        TradeHolder holder(UUID id) { return id.equals(a) ? ha : hb; }
        Inventory inv(UUID id) { return holder(id).inv; }
        UUID other(UUID id) { return id.equals(a) ? b : a; }
        boolean confirmed(UUID id) { return id.equals(a) ? confirmA : confirmB; }
        boolean bothConfirmed() { return confirmA && confirmB; }
    }

    private record Request(UUID from, long at) {}

    private final KubyPlugins plugin;
    private final Map<UUID, Request> requests = new ConcurrentHashMap<>();
    private final Map<UUID, TradeSession> sessions = new ConcurrentHashMap<>();

    public TradeService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public boolean inTrade(UUID id) {
        return sessions.containsKey(id);
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String request(Player from, Player to) {
        if (from.equals(to)) return "Du kannst nicht mit dir selbst handeln.";
        if (busy(from.getUniqueId()) || busy(to.getUniqueId())) return "Einer der beiden ist gerade beschäftigt.";
        requests.put(to.getUniqueId(), new Request(from.getUniqueId(), System.currentTimeMillis()));
        Msg.send(to, "<gold>" + from.getName() + " <gray>möchte mit dir handeln. Annehmen mit <white>/trade accept");
        Sfx.play(plugin, to, Sfx.Kind.PING);
        return null;
    }

    public synchronized String accept(Player to) {
        Request r = requests.remove(to.getUniqueId());
        if (r == null) return "Keine Handelsanfrage.";
        if (System.currentTimeMillis() - r.at() > REQUEST_TIMEOUT_MS) return "Die Anfrage ist abgelaufen.";
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) return "Der Anfragende ist offline.";
        if (busy(from.getUniqueId()) || busy(to.getUniqueId())) return "Einer der beiden ist gerade beschäftigt.";
        start(from, to);
        return null;
    }

    private boolean busy(UUID id) {
        return inTrade(id) || plugin.pvp().isDueling(id);
    }

    private void start(Player a, Player b) {
        TradeSession s = new TradeSession(a.getUniqueId(), b.getUniqueId());
        s.ha.inv = Bukkit.createInventory(s.ha, 54, Msg.parse("<dark_gray>Tausch mit " + b.getName()));
        s.hb.inv = Bukkit.createInventory(s.hb, 54, Msg.parse("<dark_gray>Tausch mit " + a.getName()));
        sessions.put(a.getUniqueId(), s);
        sessions.put(b.getUniqueId(), s);
        refresh(s);
        a.openInventory(s.ha.inv);
        b.openInventory(s.hb.inv);
        Sfx.play(plugin, a, Sfx.Kind.TRADE);
        Sfx.play(plugin, b, Sfx.Kind.TRADE);
    }

    /** Spiegelt das Partner-Angebot, den Timer, Bestätigungen und die Buttons. */
    private void refresh(TradeSession s) {
        for (UUID id : List.of(s.a, s.b)) {
            UUID other = s.other(id);
            Inventory mine = s.inv(id);
            Inventory theirs = s.inv(other);

            for (int row = 0; row < 5; row++) {
                mine.setItem(row * 9 + 4, timerPane(s, row));
                for (int c = 0; c < 4; c++) {
                    ItemStack it = theirs.getItem(row * 9 + c);
                    mine.setItem(row * 9 + 5 + c, it == null ? null : it.clone());
                }
            }
            for (int slot = 45; slot < 54; slot++) mine.setItem(slot, Items.filler());

            boolean mineOk = s.confirmed(id);
            boolean theirOk = s.confirmed(other);

            mine.setItem(CONFIRM_SLOT, mineOk
                    ? Items.make(Material.LIME_CONCRETE, "<green>✔ Angenommen", List.of("<gray>Warte auf den Timer oder Abbruch"))
                    : Items.make(Material.LIME_CONCRETE, "<green>✔ Annehmen", List.of("<gray>Klick: Handel annehmen")));

            List<String> status = new ArrayList<>();
            status.add("<gray>Du: " + (mineOk ? "<green>angenommen" : "<red>offen"));
            status.add("<gray>Partner: " + (theirOk ? "<green>angenommen" : "<red>offen"));
            if (s.countdown >= 0) status.add("<yellow>Abschluss in " + s.countdown + " s");
            status.add("<dark_gray>Änderungen setzen Annahmen zurück");
            mine.setItem(STATUS_SLOT, Items.make(Material.PAPER, "<white>Status", status));

            mine.setItem(CANCEL_SLOT, Items.make(Material.RED_CONCRETE, "<red>✘ Abbrechen", List.of("<gray>Jederzeit möglich")));
        }
    }

    private static ItemStack timerPane(TradeSession s, int row) {
        if (s.countdown < 0) return Items.filler();
        int filled = TIMER_SECONDS - s.countdown;
        return row < filled
                ? Items.make(Material.LIME_STAINED_GLASS_PANE, " ", List.of())
                : Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    /** Wird nach jeder Änderung am Angebot aufgerufen. Stoppt einen laufenden Timer. */
    public synchronized void changed(TradeSession s) {
        if (s.closing) return;
        stopTimer(s);
        s.confirmA = false;
        s.confirmB = false;
        refresh(s);
    }

    public synchronized void confirm(Player p) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s == null || s.closing) return;
        if (p.getUniqueId().equals(s.a)) s.confirmA = true;
        else s.confirmB = true;
        if (s.bothConfirmed() && s.timer == null) startCountdown(s);
        else refresh(s);
    }

    public synchronized void cancel(Player p) {
        TradeSession s = sessions.get(p.getUniqueId());
        if (s != null) abort(s, "<gray>Handel abgebrochen.");
    }

    private void startCountdown(TradeSession s) {
        s.countdown = TIMER_SECONDS;
        s.timer = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(s), 20L, 20L);
        notifyBoth(s, "<gold>Tausch wird abgeschlossen in <white>" + s.countdown + "<gold>... <gray>(Abbrechen jederzeit)");
        refresh(s);
    }

    private synchronized void tick(TradeSession s) {
        if (s.closing || !s.bothConfirmed() || s.timer == null) {
            stopTimer(s);
            return;
        }
        s.countdown--;
        for (UUID id : List.of(s.a, s.b)) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) Sfx.play(plugin, pl, Sfx.Kind.TICK, 0.6f + (TIMER_SECONDS - s.countdown) * 0.2f);
        }
        if (s.countdown <= 0) {
            complete(s);
            return;
        }
        notifyBoth(s, "<gold>Tausch wird abgeschlossen in <white>" + s.countdown + "<gold>... <gray>(Abbrechen jederzeit)");
        refresh(s);
    }

    private void stopTimer(TradeSession s) {
        if (s.timer != null) {
            s.timer.cancel();
            s.timer = null;
        }
        s.countdown = -1;
    }

    private void notifyBoth(TradeSession s, String text) {
        for (UUID id : List.of(s.a, s.b)) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) pl.sendActionBar(Msg.parse(text));
        }
    }

    /** Bricht ab und gibt jedem seine Angebote zurück. Mehrfacher Aufruf ist harmlos. */
    public synchronized void abort(TradeSession s, String message) {
        if (s.closing) return;
        s.closing = true;
        stopTimer(s);
        sessions.remove(s.a);
        sessions.remove(s.b);
        giveOffer(s.a, takeOffer(s.inv(s.a)));
        giveOffer(s.b, takeOffer(s.inv(s.b)));
        for (UUID id : List.of(s.a, s.b)) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) {
                Msg.send(pl, message);
                pl.closeInventory();
            }
        }
    }

    private void complete(TradeSession s) {
        stopTimer(s);
        s.closing = true;
        sessions.remove(s.a);
        sessions.remove(s.b);
        List<ItemStack> fromA = takeOffer(s.inv(s.a));
        List<ItemStack> fromB = takeOffer(s.inv(s.b));
        giveOffer(s.b, fromA);
        giveOffer(s.a, fromB);
        for (UUID id : List.of(s.a, s.b)) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) {
                Msg.send(pl, "<green>Tausch abgeschlossen!");
                Sfx.play(plugin, pl, Sfx.Kind.SUCCESS);
                pl.closeInventory();
            }
        }
        plugin.battlepass().progress(s.a, "trade", 1);
        plugin.battlepass().progress(s.b, "trade", 1);
    }

    private static List<ItemStack> takeOffer(Inventory inv) {
        List<ItemStack> items = new ArrayList<>();
        for (int row = 0; row < 5; row++) {
            for (int c = 0; c < 4; c++) {
                int slot = row * 9 + c;
                ItemStack it = inv.getItem(slot);
                if (it != null && !it.getType().isAir()) items.add(it.clone());
                inv.setItem(slot, null);
            }
        }
        return items;
    }

    /** Ins Inventar, was nicht passt, oder bei Offline-Spielern direkt in die Abholbox. */
    private void giveOffer(UUID id, List<ItemStack> items) {
        Player pl = Bukkit.getPlayer(id);
        for (ItemStack it : items) {
            if (pl == null) {
                plugin.abholbox().add(id, it);
            } else {
                pl.getInventory().addItem(it).values().forEach(rest -> plugin.abholbox().add(id, rest));
            }
        }
    }
}
