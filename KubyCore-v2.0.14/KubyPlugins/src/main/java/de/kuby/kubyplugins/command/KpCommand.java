package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.gui.JewelStoreEditorMenu;
import de.kuby.kubyplugins.gui.HubEditorMenu;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Locs;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** Operative Befehle für Admins und Teamler, alle beginnen mit /kp. */
public final class KpCommand implements CommandExecutor {

    private static final List<String> MODES = List.of(ModeService.HUB, ModeService.SMP, ModeService.SURVIVAL, ModeService.SKYBLOCK);
    private final KubyPlugins plugin;

    public KpCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("selftest") && !(sender instanceof Player)) {
            SelfTest.run(plugin, sender);
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Keine Berechtigung.");
            return true;
        }
        if (args.length == 0) {
            help(p);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setspawn" -> {
                String mode = modeArg(args, 1);
                if (mode == null) { Msg.send(p, "<red>Nutzung: /kp setspawn <hub|smp|survival>"); return true; }
                plugin.getConfig().set("modes." + mode + ".spawn", Locs.serialize(p.getLocation()));
                plugin.saveConfig();
                Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                Msg.send(p, "<green>Spawn von " + ModeService.label(mode) + " gesetzt.");
            }
            case "setworld" -> {
                String mode = modeArg(args, 1);
                if (mode == null || args.length < 3) { Msg.send(p, "<red>Nutzung: /kp setworld <hub|smp|survival> <welt>"); return true; }
                plugin.getConfig().set("modes." + mode + ".world", args[2]);
                plugin.getConfig().set("modes." + mode + ".spawn", null);
                plugin.saveConfig();
                Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                Msg.send(p, "<green>Welt von " + ModeService.label(mode) + " ist jetzt <white>" + args[2] + "<green>.");
            }
            case "mode" -> {
                String mode = modeArg(args, 1);
                if (mode == null) { Msg.send(p, "<red>Nutzung: /kp mode <hub|smp|survival>"); return true; }
                String err = plugin.modes().switchTo(p, mode);
                if (err != null) Msg.send(p, "<red>" + err);
            }
            case "xp" -> xp(p, args);
            case "create" -> create(p, args);
            case "item" -> item(p, args);
            case "model" -> model(p, args);
            case "module" -> module(p, args);
            case "edit" -> {
                String err = plugin.edit().enter(p);
                if (err != null) Msg.send(p, "<red>" + err);
            }
            case "region" -> region(p, args);
            case "shop" -> {
                if (args.length >= 3 && args[1].equalsIgnoreCase("editor")) {
                    String m = args[2].toLowerCase(Locale.ROOT);
                    if (m.equals("survival") || m.equals("smp")) new JewelStoreEditorMenu(plugin, m, 0).open(p);
                    else Msg.send(p, "<red>Nutzung: /kp shop editor <survival|smp>");
                } else {
                    Msg.send(p, "<red>Nutzung: /kp shop editor <survival|smp>");
                }
            }
            case "hub" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("editor")) new HubEditorMenu(plugin).open(p);
                else Msg.send(p, "<red>Nutzung: /kp hub editor");
            }
            case "marker" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
                    Msg.send(p, plugin.markers().removeNear(p) ? "<green>Marker entfernt." : "<red>Kein Marker in der Nähe.");
                } else {
                    Msg.send(p, "<red>Nutzung: /kp marker remove");
                }
            }
            case "border" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("scan")) {
                    boolean grew = plugin.worlds().scanAfterBuild(p.getWorld());
                    Msg.send(p, grew ? "<green>Worldborder erweitert." : "<gray>Keine Bebauung am Rand gefunden.");
                } else {
                    Msg.send(p, "<red>Nutzung: /kp border scan");
                }
            }
            case "homebonus" -> homeBonus(p, args);
            case "deko", "deco" -> deco(p, args);
            default -> {
                java.util.function.BiConsumer<Player, String[]> handler = plugin.kpSub(args[0].toLowerCase(Locale.ROOT));
                if (handler != null) handler.accept(p, args);
                else help(p);
            }
        }
        return true;
    }

    private static String modeArg(String[] args, int i) {
        if (args.length <= i) return null;
        String m = args[i].toLowerCase(Locale.ROOT);
        return MODES.contains(m) ? m : null;
    }

    private void item(Player p, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            Msg.send(p, "<gold>Custom Items:");
            for (var d : plugin.customItems().all()) Msg.send(p, "<gray>- <white>" + d.id() + " <dark_gray>(" + d.name() + ")");
            return;
        }
        if (args.length >= 4 && args[1].equalsIgnoreCase("give")) {
            var target = org.bukkit.Bukkit.getPlayerExact(args[2]);
            var def = plugin.customItems().get(args[3]);
            if (target == null) { Msg.send(p, "<red>Spieler nicht online."); return; }
            if (def == null) { Msg.send(p, "<red>Unbekanntes Custom Item. Siehe /kp item list"); return; }
            int amount = 1;
            if (args.length >= 5) {
                try { amount = Integer.parseInt(args[4]); } catch (NumberFormatException ignored) { }
            }
            var leftover = target.getInventory().addItem(plugin.customItems().create(def, amount));
            leftover.values().forEach(rest -> target.getWorld().dropItem(target.getLocation(), rest));
            Sfx.play(plugin, target, Sfx.Kind.SUCCESS);
            Msg.send(p, "<green>" + def.name() + " an " + target.getName() + " gegeben.");
            return;
        }
        Msg.send(p, "<red>Nutzung: /kp item give <spieler> <id> [menge] | /kp item list");
    }

    private void model(Player p, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("place") && args.length >= 3) {
            String err = plugin.models().place(p, args[2].toLowerCase(Locale.ROOT));
            if (err != null) Msg.send(p, "<red>" + err);
            else Msg.send(p, "<green>Modell gesetzt.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            Msg.send(p, "<gold>Gesetzte Modelle: <white>" + plugin.models().all().size());
            return;
        }
        Msg.send(p, "<red>Nutzung: /kp model place <id> | /kp model list");
        Msg.send(p, "<gray>Abbauen: auf das Modell linksklicken.");
    }

    private void help(Player p) {
        Msg.send(p, "<gold>/kp <dark_gray>- <gray>Admin-Befehle");
        Msg.send(p, "<gray>/kp setspawn <hub|smp|survival> <dark_gray>- <gray>Spawn an deiner Position setzen");
        Msg.send(p, "<gray>/kp setworld <hub|smp|survival> <welt> <dark_gray>- <gray>Welt eines Modus festlegen");
        Msg.send(p, "<gray>/kp mode <hub|smp|survival> <dark_gray>- <gray>Modus direkt wechseln");
        Msg.send(p, "<gray>/kp create <hub|smp|survival> <dark_gray>- <gray>Void-Welt mit Glasplattform erstellen");
        Msg.send(p, "<gray>/kp border scan <dark_gray>- <gray>Rand der Worldborder prüfen");
        Msg.send(p, "<gray>/kp module list <dark_gray>- <gray>Installierte Module");
        Msg.send(p, "<gray>/kp module store download <id> <quelle> <dark_gray>- <gray>Modul laden");
        Msg.send(p, "<gray>/kp edit <dark_gray>- <gray>Bearbeitungsmodus starten");
        Msg.send(p, "<gray>/kp region create|delete|list <dark_gray>- <gray>Schutzbereiche verwalten");
        Msg.send(p, "<gray>/kp hub editor <dark_gray>- <gray>Hub-Einstellungen");
        Msg.send(p, "<gray>/kp angel pos|zone ... <dark_gray>- <gray>Angelzonen verwalten");
        Msg.send(p, "<gray>/kp item give <spieler> <id> [menge] <dark_gray>- <gray>Custom Item geben");
        Msg.send(p, "<gray>/kp item list <dark_gray>- <gray>Custom Items anzeigen");
        Msg.send(p, "<gray>/kp model place <id> <dark_gray>- <gray>3D-Modell auf den anvisierten Block setzen");
        Msg.send(p, "<gray>/kp marker remove <dark_gray>- <gray>Marker in der Nähe entfernen");
        Msg.send(p, "<gray>/kp xp <give|take|set> <spieler> <betrag> <dark_gray>- <gray>XP verwalten");
        Msg.send(p, "<gray>/kp homebonus <spieler> <vote|verify> <dark_gray>- <gray>Homebonus vergeben");
        Msg.send(p, "<gray>/kp deko add <skin-spieler> [name] | remove <dark_gray>- <gray>Deko-Figur (Schalter Deko-NPCs)");
    }

    /** Deko-Figuren: Mannequin mit dem Skin eines Spielers, ohne Funktion. Wer "Deko-NPCs" ausschaltet, sieht sie nicht. */
    private void deco(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("remove") || sub.equals("entfernen")) {
            org.bukkit.entity.Entity e = plugin.displaySettings().nearestDeco(p.getLocation(), 4);
            if (e == null) {
                Msg.send(p, "<red>Keine Deko-Figur in der Nähe (4 Blöcke).");
                return;
            }
            e.remove();
            Msg.send(p, "<green>Deko-Figur entfernt.");
            return;
        }
        if (!sub.equals("add") || args.length < 3) {
            Msg.send(p, "<red>Nutzung: /kp deko add <skin-spieler> [name] <gray>oder <red>/kp deko remove");
            return;
        }
        String skin = args[2];
        String name = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : null;
        org.bukkit.Location at = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        at.setYaw(Math.round(p.getLocation().getYaw() / 45f) * 45f + 180f);
        at.setPitch(0);
        org.bukkit.entity.Mannequin m = at.getWorld().spawn(at, org.bukkit.entity.Mannequin.class, e -> {
            e.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile().name(skin).build());
            e.setImmovable(true);
            e.setDescription(null);
            e.setAI(false);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.setCollidable(false);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            if (name != null) {
                e.customName(Msg.parse(name));
                e.setCustomNameVisible(true);
            } else {
                e.setCustomNameVisible(false);
            }
        });
        plugin.displaySettings().markDeco(m);
        Msg.send(p, "<green>Deko-Figur mit dem Skin von <white>" + Msg.escape(skin) + " <green>gesetzt. <gray>Entfernen: /kp deko remove");
    }

    private void xp(Player p, String[] args) {
        if (args.length < 4) {
            Msg.send(p, "<red>Nutzung: /kp xp <give|take|set> <spieler> <betrag>");
            return;
        }
        var target = org.bukkit.Bukkit.getOfflinePlayerIfCached(args[2]);
        if (target == null) {
            Msg.send(p, "<red>Spieler unbekannt.");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Ungültiger Betrag.");
            return;
        }
        var st = plugin.stats().get(target.getUniqueId());
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "give" -> st.xp += amount;
            case "take" -> st.xp = Math.max(0, st.xp - amount);
            case "set" -> st.xp = Math.max(0, amount);
            default -> {
                Msg.send(p, "<red>Nutzung: /kp xp <give|take|set> <spieler> <betrag>");
                return;
            }
        }
        plugin.stats().save();
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        Msg.send(p, "<green>XP von " + args[2] + " ist jetzt <white>" + st.xp + "<green>.");
    }

    private void homeBonus(Player p, String[] args) {
        if (args.length < 3) {
            Msg.send(p, "<red>Nutzung: /kp homebonus <spieler> <vote|verify>");
            return;
        }
        var target = org.bukkit.Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            Msg.send(p, "<red>Spieler unbekannt.");
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "vote" -> plugin.homes().grantVote(target.getUniqueId());
            case "verify" -> plugin.homes().grantVerify(target.getUniqueId());
            default -> {
                Msg.send(p, "<red>Nutzung: /kp homebonus <spieler> <vote|verify>");
                return;
            }
        }
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        Msg.send(p, "<green>Homebonus " + args[2] + " für " + args[1] + " vergeben.");
    }

    private void create(Player p, String[] args) {
        String mode = modeArg(args, 1);
        if (mode == null) {
            Msg.send(p, "<red>Nutzung: /kp create <hub|smp|survival> [modul]");
            return;
        }
        if (mode.equals(ModeService.HUB)) {
            String id = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "hub";
            if (!plugin.modules().exists(id)) {
                Sfx.play(plugin, p, Sfx.Kind.ERROR);
                Msg.send(p, "<red>Modul " + id + " ist nicht installiert. Siehe /kp module list");
                return;
            }
            plugin.modules().applyHub(id);
        }
        String err = plugin.worlds().create(mode);
        if (err != null) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>" + err);
            return;
        }
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS_BIG);
        Msg.send(p, "<green>Welt von " + ModeService.label(mode) + " erstellt: <white>" + plugin.modes().worldOf(mode));
    }

    private void module(Player p, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            Msg.send(p, "<gold>Installierte Module: <white>" + String.join(", ", plugin.modules().installed()));
            return;
        }
        if (args.length >= 5 && args[1].equalsIgnoreCase("store") && args[2].equalsIgnoreCase("download")) {
            plugin.modules().download(p, args[3].toLowerCase(Locale.ROOT), args[4]);
            return;
        }
        Msg.send(p, "<red>Nutzung: /kp module list");
        Msg.send(p, "<red>Nutzung: /kp module store download <modul-id> <GITHUB|DRIVE|MEDIAFIRE>");
    }

    private void region(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, "<red>Nutzung: /kp region <create|delete|list>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 3) {
                    Msg.send(p, "<red>Nutzung: /kp region create <id>");
                    return;
                }
                var c = plugin.edit().corners(p.getUniqueId());
                String err = c == null ? "Setze erst beide Ecken mit der Blaze Rod im Bearbeitungsmodus."
                        : plugin.regions().create(args[2], c[0], c[1], "-");
                if (err != null) {
                    Sfx.play(plugin, p, Sfx.Kind.ERROR);
                    Msg.send(p, "<red>" + err);
                } else {
                    Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
                    Msg.send(p, "<green>Region " + args[2].toLowerCase(Locale.ROOT) + " erstellt.");
                }
            }
            case "delete" -> {
                if (args.length < 3) {
                    Msg.send(p, "<red>Nutzung: /kp region delete <id>");
                    return;
                }
                Msg.send(p, plugin.regions().delete(args[2]) ? "<green>Region gelöscht." : "<red>Region nicht gefunden.");
            }
            case "list" -> {
                var all = plugin.regions().all();
                if (all.isEmpty()) Msg.send(p, "<gray>Keine Regionen.");
                for (var r : all) Msg.send(p, "<gray>- <white>" + r.id() + " <dark_gray>(" + r.world() + ")");
            }
            default -> Msg.send(p, "<red>Nutzung: /kp region <create|delete|list>");
        }
    }
}
