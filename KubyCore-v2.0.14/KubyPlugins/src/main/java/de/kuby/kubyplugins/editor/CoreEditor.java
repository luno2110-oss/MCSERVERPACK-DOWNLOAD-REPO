package de.kuby.kubyplugins.editor;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CrateService;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Kategorien des Kerns im Editor: Kisten, Modelle, Welt &amp; NPCs, Einstellungen. Addons tragen weitere ein. */
public final class CoreEditor {

    private CoreEditor() {
    }

    private static String plain(String mini) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(Msg.parse(mini));
    }

    private static final List<String> MODES = List.of("hub", "smp", "survival", "skyblock");

    public static void register(KubyPlugins plugin) {
        EditorService ed = plugin.editor();
        // Selbsttest: Übersicht und jede Kategorie (Seite 1, dazu mit wartendem „Sicher? Ja!“), Addon-Kategorien inklusive
        plugin.registerSelfTestCanvas("Editor Übersicht", () -> de.kuby.kubyplugins.staff.KadminScreens.render(
                EditorScreen.mainPage(plugin.editor().categories(), id -> p -> { })));
        for (String cat : List.of("kisten", "modelle", "welt", "einstellungen", "skyblock")) {
            plugin.registerSelfTestCanvas("Editor " + cat, () -> {
                EditorService.Category c = plugin.editor().category(cat);
                if (c == null) c = new EditorService.Category(cat, cat, "nicht installiert", Material.BARRIER, List::of);
                List<EditorService.Tool> t = c.tools().get();
                String wait = t.stream().filter(EditorService.Tool::confirm).findFirst().map(x -> cat + "/" + x.id()).orElse(null);
                return de.kuby.kubyplugins.staff.KadminScreens.render(plugin.editorScreen().categoryPage(c, 0, wait));
            });
        }
        ed.registerPlacer("crate", (p, at, v) -> plugin.crates().placeFromEditor(p, at, v));
        ed.registerPlacer("model", (p, at, v) -> plugin.models().placeAt(at, v, p));

        ed.registerCategory(new EditorService.Category("kisten", "Kisten",
                "Kisten als Block holen und hinstellen (Abbauen: Schleichen + Linksklick)", Material.CHEST, () -> {
            List<EditorService.Tool> out = new ArrayList<>();
            ConfigurationSection all = plugin.getConfig().getConfigurationSection("crates");
            if (all == null) return out;
            Map<String, String> placed = plugin.crates().places();
            for (String mode : all.getKeys(false)) {
                for (CrateService.Crate c : plugin.crates().crates(mode)) {
                    long n = placed.values().stream().filter(x -> x.equals(c.mode() + ":" + c.id())).count();
                    out.add(EditorService.Tool.give(c.mode() + "-" + c.id(), c.name() + " · " + ModeService.label(c.mode()),
                            c.rewards().size() + " Gewinne · " + n + "× aufgestellt", plugin.crates().blockOf(c),
                            p -> {
                                plugin.editor().give(p, plugin.crates().editorItem(c));
                                Msg.send(p, "<green>Rechtsklick auf einen Block stellt die Kiste hin.");
                            }));
                }
            }
            return out;
        }));

        ed.registerCategory(new EditorService.Category("modelle", "Modelle",
                "Eigene 3D-Modelle aus dem Pack hinstellen (Abbauen: Linksklick aufs Modell)", Material.ARMOR_STAND, () -> {
            List<EditorService.Tool> out = new ArrayList<>();
            for (var d : plugin.customItems().all()) {
                out.add(EditorService.Tool.give(d.id(), plain(d.name()), "Modell " + d.model(), d.material(), p -> {
                    plugin.editor().give(p, plugin.editor().placeable("model", d.id(), plugin.customItems().create(d, 1),
                            "<aqua><b>Modell: " + Msg.escape(plain(d.name())) + "</b>", List.of("<gray>ID: <white>" + d.id())));
                    Msg.send(p, "<green>Rechtsklick auf einen Block stellt das Modell hin.");
                }));
            }
            return out;
        }));

        ed.registerCategory(new EditorService.Category("welt", "Welt & NPCs", "Spawns, Modus-NPCs, Deko und Bearbeitungsmodus",
                Material.COMPASS, () -> {
            List<EditorService.Tool> out = new ArrayList<>();
            for (String m : MODES) {
                out.add(EditorService.Tool.of("spawn-" + m, "Spawn " + ModeService.label(m) + " hier setzen",
                        "Setzt den Spawn von " + ModeService.label(m) + " auf deine Position", Material.RED_BED,
                        p -> p.performCommand("kp setspawn " + m)));
            }
            for (String m : MODES) {
                if (m.equals("hub")) continue;
                out.add(EditorService.Tool.of("npc-" + m, "Modus-NPC " + ModeService.label(m) + " setzen",
                        "NPC in der Lobby, der nach " + ModeService.label(m) + " bringt", Material.PLAYER_HEAD,
                        p -> p.performCommand("kp modusnpc setzen " + m)));
            }
            out.add(EditorService.Tool.of("discord", "Discord-Statue setzen", "Wumpus mit Schild und Link an deiner Position",
                    Material.LIGHT_BLUE_CONCRETE, p -> p.performCommand("kp deko discord")));
            out.add(EditorService.Tool.of("deko-weg", "Deko in der Nähe entfernen", "Deko-Figur oder Statue im Umkreis von 4 Blöcken",
                    Material.BARRIER, p -> p.performCommand("kp deko remove")));
            out.add(EditorService.Tool.of("bearbeiten", "Bearbeitungsmodus", "Bauen und Abbauen in geschützten Bereichen",
                    Material.GOLDEN_PICKAXE, p -> p.performCommand("kp edit")));
            return out;
        }));

        ed.registerCategory(new EditorService.Category("einstellungen", "Einstellungen & Shops", "Hub-Einstellungen und Juwelenshops bearbeiten",
                Material.COMPARATOR, () -> List.of(
                EditorService.Tool.open("hub", "Hub-Einstellungen", "Zeit, Wetter, Lobby-Items und mehr", Material.NETHER_STAR,
                        p -> p.performCommand("kp hub editor")),
                EditorService.Tool.open("shop-survival", "Juwelenshop Survival", "Angebote und Preise bearbeiten", Material.EMERALD,
                        p -> p.performCommand("kp shop editor survival")),
                EditorService.Tool.open("shop-smp", "Juwelenshop SMP", "Angebote und Preise bearbeiten", Material.EMERALD,
                        p -> p.performCommand("kp shop editor smp")))));
    }
}
