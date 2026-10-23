package de.kp.jumprun.command;

import de.kp.jumprun.KPJumpRun;
import de.kp.jumprun.editor.EditorStick;
import de.kp.jumprun.gui.ConfigGui;
import de.kp.jumprun.gui.EditorHolder;
import de.kp.jumprun.holo.Holo;
import de.kp.jumprun.holo.HoloManager;
import de.kp.jumprun.util.Text;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * /kp parkour setup [name]  – gibt den Editor Stick
 * Weitere Unterbefehle (editor, holo, ...) folgen in späteren Teilen.
 */
public final class KpCommand implements CommandExecutor, TabCompleter {

    private static final String DEFAULT_ARENA = "default";

    private final KPJumpRun plugin;

    public KpCommand(KPJumpRun plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("kuby.admin")) {
            Text.send(sender, "<red>Dafür hast du keine Berechtigung.</red>");
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("parkour")) {
            Text.send(sender, "<gray>Nutze: <white>/kp parkour setup [name]</white></gray>");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "setup" -> giveStick(sender, args);
            case "holo" -> holo(sender, args);
            case "editor" -> editor(sender, args);
            case "npc" -> npc(sender, args);
            case "config" -> config(sender);
            default -> Text.send(sender, "<gray>Dieser Unterbefehl kommt in einem späteren Teil.</gray>");
        }
        return true;
    }

    private void giveStick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Dieser Befehl ist nur für Spieler.</red>");
            return;
        }

        String name = args.length >= 3 ? args[2].toLowerCase() : DEFAULT_ARENA;
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            Text.send(player, "<red>Ungültiger Name. Erlaubt sind a-z, 0-9, _ und -.</red>");
            return;
        }

        plugin.arenas().getOrCreate(name);
        plugin.arenas().save();

        ItemStack stick = plugin.editorStick().create(name, EditorStick.Mode.START_FINISH);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stick);
        for (ItemStack item : leftover.values()) {
            Location drop = player.getLocation();
            player.getWorld().dropItemNaturally(drop, item);
        }

        Text.send(player, "Du hast den <yellow>Editor Stick</yellow> für Arena <white>" + name + "</white> erhalten.");
    }

    private void editor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Dieser Befehl ist nur für Spieler.</red>");
            return;
        }

        String name = args.length >= 3 ? args[2].toLowerCase() : DEFAULT_ARENA;
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            Text.send(player, "<red>Ungültiger Name. Erlaubt sind a-z, 0-9, _ und -.</red>");
            return;
        }

        plugin.arenas().getOrCreate(name);
        plugin.arenas().save();
        plugin.editorGui().open(player, EditorHolder.Screen.MAIN, name);
    }

    private void config(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Dieser Befehl ist nur für Spieler.</red>");
            return;
        }
        plugin.configGui().open(player, ConfigGui.Screen.MAIN, null);
    }

    private void npc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Dieser Befehl ist nur für Spieler.</red>");
            return;
        }

        String action = args.length >= 3 ? args[2].toLowerCase() : "";
        switch (action) {
            case "create" -> {
                String arena = args.length >= 4 ? args[3].toLowerCase() : DEFAULT_ARENA;
                if (!arena.matches("[a-z0-9_-]{1,32}")) {
                    Text.send(player, "<red>Ungültiger Name. Erlaubt sind a-z, 0-9, _ und -.</red>");
                    return;
                }
                plugin.arenas().getOrCreate(arena);
                plugin.arenas().save();
                plugin.rewardNpc().spawn(player.getLocation(), arena);
                Text.send(player, "Belohnungs-NPC für Arena <white>" + arena + "</white> erstellt.");
            }
            case "center" -> {
                int moved = plugin.rewardNpc().centerNear(player.getLocation(), 4);
                Text.send(player, "<white>" + moved + "</white> NPC(s) auf die Blockmitte gesetzt.");
            }
            case "remove" -> {
                int removed = plugin.rewardNpc().removeNear(player.getLocation(), 4);
                Text.send(player, "<white>" + removed + "</white> NPC(s) in der Nähe entfernt.");
            }
            default -> Text.send(player, "<gray>Nutze: <white>/kp parkour npc <create [arena]|center|remove></white></gray>");
        }
    }

    private static final String HOLO_USAGE = "<gray>Nutze: <white>/kp parkour holo <create|delete|list|tp|move|arena|scale|time> [name] [wert]</white></gray>";

    private void holo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Text.send(sender, HOLO_USAGE);
            return;
        }

        HoloManager holos = plugin.holos();
        String action = args[2].toLowerCase();

        if (action.equals("list")) {
            if (holos.all().isEmpty()) {
                Text.send(sender, "<gray>Noch keine Hologramme vorhanden.</gray>");
            } else {
                Text.send(sender, "<gray>Hologramme: <white>" + String.join(", ", holos.all().keySet()) + "</white></gray>");
            }
            return;
        }

        if (args.length < 4) {
            Text.send(sender, HOLO_USAGE);
            return;
        }
        String name = args[3].toLowerCase();

        if (action.equals("create")) {
            if (!(sender instanceof Player player)) {
                Text.send(sender, "<red>Dieser Befehl ist nur für Spieler.</red>");
                return;
            }
            if (!name.matches("[a-z0-9_-]{1,32}")) {
                Text.send(sender, "<red>Ungültiger Name. Erlaubt sind a-z, 0-9, _ und -.</red>");
                return;
            }
            if (holos.get(name) != null) {
                Text.send(sender, "<red>Ein Hologramm mit diesem Namen existiert bereits.</red>");
                return;
            }
            String arena = args.length >= 5 ? args[4].toLowerCase() : "default";
            holos.create(name, arena, player.getLocation().add(0, 2.5, 0));
            Text.send(sender, "Hologramm <white>" + name + "</white> erstellt, Arena <white>" + arena + "</white>.");
            return;
        }

        Holo holo = holos.get(name);
        if (holo == null) {
            Text.send(sender, "<red>Kein Hologramm mit dem Namen " + name + " gefunden.</red>");
            return;
        }

        switch (action) {
            case "delete" -> {
                holos.delete(name);
                Text.send(sender, "Hologramm <white>" + name + "</white> gelöscht.");
            }
            case "tp" -> {
                if (sender instanceof Player player) {
                    player.teleport(holo.location().clone().add(0, 1, 0));
                }
            }
            case "move" -> {
                if (sender instanceof Player player) {
                    holos.move(holo, player.getLocation().add(0, 2.5, 0));
                    Text.send(sender, "Hologramm <white>" + name + "</white> verschoben.");
                }
            }
            case "arena" -> {
                if (args.length < 5) {
                    Text.send(sender, HOLO_USAGE);
                    return;
                }
                holo.arena(args[4].toLowerCase());
                holos.save();
                Text.send(sender, "Arena für <white>" + name + "</white> ist jetzt <white>" + holo.arena() + "</white>.");
            }
            case "scale" -> {
                Double value = parseDouble(sender, args, 4, 0.5, 4.0);
                if (value == null) {
                    return;
                }
                holo.scale(value);
                holos.rebuild(holo);
                Text.send(sender, "Größe von <white>" + name + "</white>: <white>" + value + "</white>.");
            }
            case "time" -> {
                Double value = parseDouble(sender, args, 4, 3, 60);
                if (value == null) {
                    return;
                }
                holo.seconds(value.intValue());
                holos.save();
                Text.send(sender, "Anzeigedauer von <white>" + name + "</white>: <white>" + value.intValue() + " s</white>.");
            }
            default -> Text.send(sender, HOLO_USAGE);
        }
    }

    private Double parseDouble(CommandSender sender, String[] args, int index, double min, double max) {
        if (args.length <= index) {
            Text.send(sender, HOLO_USAGE);
            return null;
        }
        try {
            double value = Double.parseDouble(args[index].replace(',', '.'));
            if (value < min || value > max) {
                Text.send(sender, "<red>Wert muss zwischen " + min + " und " + max + " liegen.</red>");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            Text.send(sender, "<red>Ungültige Zahl: " + args[index] + "</red>");
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("parkour");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("parkour")) {
            return List.of("setup", "editor", "holo", "npc", "config");
        }
        return List.of();
    }
}
