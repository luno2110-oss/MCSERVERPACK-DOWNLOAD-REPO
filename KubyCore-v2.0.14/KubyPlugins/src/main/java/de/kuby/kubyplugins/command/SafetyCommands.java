package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.BackupService;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** /kp backup … und /kp alts … (Admins). */
public final class SafetyCommands {

    private final KubyPlugins plugin;

    public SafetyCommands(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private static String mb(long b) {
        return String.format(Locale.GERMANY, "%.1f MB", b / 1024.0 / 1024.0);
    }

    // =================================================================================================================
    // /kp backup
    // =================================================================================================================

    public void backup(Player p, String[] args) {
        BackupService b = plugin.backups();
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "jetzt", "now" -> {
                String err = b.run(args.length > 2 ? args[2] : "manuell", m -> Msg.send(p, "<green>" + Msg.escape(m)));
                Msg.send(p, err != null ? "<red>" + err : "<gray>Backup läuft … (Speichern, dann Packen im Hintergrund)");
            }
            case "liste", "list" -> {
                List<File> l = b.list();
                if (l.isEmpty()) {
                    Msg.send(p, "<gray>Noch keine Backups.");
                    return;
                }
                Msg.send(p, "<gold><b>Backups</b> <gray>(" + l.size() + ", neueste zuerst)");
                for (int i = 0; i < Math.min(15, l.size()); i++) {
                    File f = l.get(i);
                    Msg.send(p, "<white>" + (i + 1) + ". <gray>" + f.getName() + " <dark_gray>· " + mb(f.length())
                            + " <click:suggest_command:'/kp backup zurueck " + f.getName() + "'><yellow>[zurück]</click>");
                }
                if (l.size() > 15) Msg.send(p, "<gray>… und " + (l.size() - 15) + " ältere im Ordner " + b.folder().getPath());
            }
            case "zurueck", "zurück", "restore" -> {
                if (args.length < 3) {
                    Msg.send(p, "<red>/kp backup zurueck <Nr|Name> <gray>(Nr aus /kp backup liste)");
                    return;
                }
                File f = find(b, args[2]);
                if (f == null) {
                    Msg.send(p, "<red>Backup nicht gefunden. <gray>/kp backup liste");
                    return;
                }
                if (args.length < 4 || !args[3].equalsIgnoreCase("bestaetigen")) {
                    Msg.send(p, "<gold>⚠ Wiederherstellen von <white>" + f.getName() + "<gold>: <gray>Beim nächsten Neustart werden alle "
                            + "Kuby-Daten und die Spielerdaten auf diesen Stand gesetzt (der aktuelle Stand wird vorher gesichert). "
                            + "<click:run_command:'/kp backup zurueck " + f.getName() + " bestaetigen'><red><b>[Bestätigen]</b></click>");
                    return;
                }
                String err = b.schedule(f);
                Msg.send(p, err != null ? "<red>" + err : "<green>Vorgemerkt: <white>" + f.getName()
                        + "<green>. Jetzt den Server neu starten. <gray>Abbrechen: /kp backup abbrechen");
            }
            case "abbrechen", "cancel" -> Msg.send(p, b.cancel() ? "<green>Wiederherstellung abgebrochen." : "<gray>Es war nichts vorgemerkt.");
            default -> {
                List<File> l = b.list();
                long total = 0;
                for (File f : l) total += f.length();
                String next = new SimpleDateFormat("HH:mm").format(new Date(b.nextAt()));
                Msg.send(p, "<gold><b>Backups</b> <gray>" + (plugin.getConfig().getBoolean("backup.aktiv", true) ? "<green>an" : "<red>aus")
                        + " <gray>· alle " + plugin.getConfig().getLong("backup.intervall-minuten", 60) + " Min. (nächstes ca. " + next + ")");
                Msg.send(p, "<gray>Letztes: <white>" + b.last() + " <gray>· " + l.size() + " Backups, zusammen " + mb(total));
                String pend = b.pending();
                if (pend != null) Msg.send(p, "<red>Beim nächsten Start wird wiederhergestellt: <white>" + pend + " <gray>(/kp backup abbrechen)");
                Msg.send(p, "<gray>/kp backup jetzt [Grund] · liste · zurueck <Nr|Name> · abbrechen");
            }
        }
    }

    private static File find(BackupService b, String key) {
        List<File> l = b.list();
        try {
            int n = Integer.parseInt(key);
            return n >= 1 && n <= l.size() ? l.get(n - 1) : null;
        } catch (NumberFormatException ignored) {
            // Name
        }
        for (File f : l) if (f.getName().equalsIgnoreCase(key) || f.getName().equalsIgnoreCase(key + ".zip")) return f;
        return null;
    }

    // =================================================================================================================
    // /kp alts
    // =================================================================================================================

    @SuppressWarnings("deprecation")
    public void alts(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, "<gray>/kp alts <Spieler> · /kp alts erlauben <A> <B> · /kp alts verbieten <A> <B>");
            Msg.send(p, "<gray>Sperren (Kopfgeld, Trophäen, Versicherung, Willkommens-Juwelen): "
                    + (plugin.alts().enforce() ? "<green>an" : "<red>aus") + " <gray>(alts.sperren)");
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ((sub.equals("erlauben") || sub.equals("verbieten")) && args.length >= 4) {
            OfflinePlayer a = Bukkit.getOfflinePlayer(args[2]), c = Bukkit.getOfflinePlayer(args[3]);
            plugin.alts().allow(a.getUniqueId(), c.getUniqueId(), sub.equals("erlauben"));
            Msg.send(p, "<green>" + args[2] + " und " + args[3] + (sub.equals("erlauben")
                    ? " gelten nicht mehr als Zweitkonten (z. B. Geschwister)." : " werden wieder als verknüpft erkannt."));
            return;
        }
        OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
        List<String> lines = plugin.alts().describe(t.getUniqueId());
        if (lines.isEmpty()) {
            Msg.send(p, "<green>" + args[1] + " <gray>teilt mit niemandem eine IP.");
            return;
        }
        Msg.send(p, "<gold><b>Konten mit gleicher IP wie " + Msg.escape(args[1]) + "</b>");
        for (String l : lines) Msg.send(p, "<dark_gray>▸ " + l);
        Msg.send(p, "<gray>Geschwister o. Ä.: /kp alts erlauben " + args[1] + " <Name>");
    }
}
