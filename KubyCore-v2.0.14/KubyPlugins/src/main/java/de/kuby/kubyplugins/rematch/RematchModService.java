package de.kuby.kubyplugins.rematch;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Erkennt die Client-Mod „Kuby Rematch“ (Fabric, {@code rematch/mod}).
 * Die Mod schickt beim Betreten auf {@code kuby:rematch_hello}: VarInt Protokoll, String Mod-Version.
 * KubyCore merkt sich das und antwortet auf {@code kuby:rematch_welcome}: VarInt Protokoll, Bool kompatibel, String Text.
 * Strings wie in Minecraft: VarInt-Länge in Bytes, dann UTF-8.
 */
public final class RematchModService implements PluginMessageListener, Listener {
    public static final String HELLO = "kuby:rematch_hello";
    public static final String WELCOME = "kuby:rematch_welcome";
    /** Muss zu {@code KubyRematch.PROTOCOL} in der Mod passen. */
    public static final int PROTOCOL = 1;

    public record ModInfo(int protocol, String version, long since) {
        public boolean compatible() { return protocol == PROTOCOL; }
    }

    private final KubyPlugins plugin;
    private final Map<UUID, ModInfo> clients = new ConcurrentHashMap<>();

    public RematchModService(KubyPlugins plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, HELLO, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, WELCOME);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.registerKpSub("rematchmod", (p, args) -> list(p));
    }

    /** Mod-Daten des Spielers oder null, wenn er die Mod nicht hat. */
    public ModInfo info(Player p) { return clients.get(p.getUniqueId()); }

    /** Ob der Spieler eine passende Mod-Version hat (Voraussetzung für den Rematch-Modus). */
    public boolean hasMod(Player p) {
        ModInfo i = clients.get(p.getUniqueId());
        return i != null && i.compatible();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!HELLO.equals(channel)) return;
        Hello h;
        try {
            h = decodeHello(message);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Rematch-Mod: ungültiger Handshake von " + player.getName() + ": " + e.getMessage());
            return;
        }
        ModInfo info = new ModInfo(h.protocol(), h.version(), System.currentTimeMillis());
        clients.put(player.getUniqueId(), info);
        plugin.getLogger().info("Rematch-Mod erkannt: " + player.getName() + " (Version " + h.version()
                + ", Protokoll " + h.protocol() + (info.compatible() ? ")" : ", passt NICHT zu " + PROTOCOL + ")"));
        String text = info.compatible()
                ? "§aKuby Rematch " + h.version() + " ist mit dem Server verbunden."
                : "§cKuby Rematch " + h.version() + " passt nicht zum Server (Protokoll " + h.protocol()
                  + " statt " + PROTOCOL + "). Bitte die Mod aktualisieren.";
        player.sendPluginMessage(plugin, WELCOME, encodeWelcome(PROTOCOL, info.compatible(), text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { clients.remove(e.getPlayer().getUniqueId()); }

    private void list(Player p) {
        int n = 0;
        Msg.send(p, "<gold>Rematch-Mod online:");
        for (Player o : Bukkit.getOnlinePlayers()) {
            ModInfo i = clients.get(o.getUniqueId());
            if (i == null) continue;
            n++;
            Msg.send(p, (i.compatible() ? "<green>" : "<red>") + o.getName() + " <gray>Version " + i.version()
                    + ", Protokoll " + i.protocol());
        }
        if (n == 0) Msg.send(p, "<gray>Niemand hat die Mod.");
    }

    // ---- Kodierung (wie Minecrafts ByteBufCodecs) ----

    public record Hello(int protocol, String version) { }

    public static Hello decodeHello(byte[] b) {
        int[] pos = {0};
        int protocol = readVarInt(b, pos);
        String version = readString(b, pos, 64);
        return new Hello(protocol, version);
    }

    public static byte[] encodeHello(int protocol, String version) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarInt(out, protocol);
        writeString(out, version);
        return out.toByteArray();
    }

    public static byte[] encodeWelcome(int protocol, boolean compatible, String message) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarInt(out, protocol);
        out.write(compatible ? 1 : 0);
        writeString(out, message);
        return out.toByteArray();
    }

    static int readVarInt(byte[] b, int[] pos) {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            if (pos[0] >= b.length) throw new IllegalArgumentException("VarInt zu kurz");
            int x = b[pos[0]++] & 0xFF;
            value |= (x & 0x7F) << shift;
            if ((x & 0x80) == 0) return value;
        }
        throw new IllegalArgumentException("VarInt zu lang");
    }

    static String readString(byte[] b, int[] pos, int maxChars) {
        int len = readVarInt(b, pos);
        if (len < 0 || len > maxChars * 3 || pos[0] + len > b.length) throw new IllegalArgumentException("String-Länge " + len);
        String s = new String(b, pos[0], len, StandardCharsets.UTF_8);
        pos[0] += len;
        if (s.length() > maxChars) throw new IllegalArgumentException("String zu lang");
        return s;
    }

    static void writeVarInt(ByteArrayOutputStream out, int v) {
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }

    static void writeString(ByteArrayOutputStream out, String s) {
        byte[] u = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, u.length);
        out.writeBytes(u);
    }
}
