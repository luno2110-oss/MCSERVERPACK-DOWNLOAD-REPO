package de.kuby.kubysurvival.quests;

import de.kuby.kubyplugins.gui.GuiMenu;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /sidequests: alle Figuren mit Stand, laufende Nebenquest, Wegweisung und Abbrechen. */
public final class SideQuestMenu extends GuiMenu {

    private final KubySurvival addon;

    public SideQuestMenu(KubySurvival addon) {
        super(addon.core(), Msg.parse("<gold>Nebenquests"), 5);
        this.addon = addon;
    }

    @Override
    protected void build(Player p) {
        SideQuestService side = addon.sideQuests();
        UUID id = p.getUniqueId();
        QuestService.Def run = side.running(id);
        SideQuestService.Figure runFig = side.runningFigure(id);

        if (run != null && runFig != null) {
            set(4, Items.make(run.icon(), "<gold>Läuft: <white>" + run.name(), List.of(
                    "<gray>Bei <white>" + runFig.name(),
                    "<white>" + run.task(),
                    QuestMenu.bar(side.runningProgress(id), run.amount()), "",
                    "<gray>Belohnung: " + QuestService.rewardText(run.coins(), run.tokens(), run.xp()))), null);
        } else {
            set(4, Items.make(Material.MAP, "<gold>Nebenquests", List.of(
                    "<gray>Sprich Figuren in der Welt an.", "<gray>Jede hat mehrere Aufgaben,", "<gray>die nacheinander kommen.", "",
                    "<gray>Klick auf eine Figur: Wegweisung dorthin.")), null);
        }

        List<SideQuestService.Figure> figs = side.figures();
        for (int i = 0; i < figs.size() && i < 14; i++) {
            SideQuestService.Figure f = figs.get(i);
            int done = side.finished(id, f.id());
            int total = f.steps().size();
            Location l = side.location(f.id());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Aufgaben: <white>" + done + "/" + total);
            if (done < total) lore.add("<gray>Als Nächstes: <white>" + f.steps().get(done).name());
            lore.add("");
            if (l == null) {
                lore.add("<red>Noch nicht aufgestellt");
            } else {
                lore.add("<gray>Ort: <white>" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ());
                if (l.getWorld() == p.getWorld()) lore.add("<gray>Entfernung: <white>" + Math.round(l.distance(p.getLocation())) + " m");
            }
            boolean running = runFig != null && runFig.id().equals(f.id());
            if (running) lore.add("<green>Hier läuft deine Nebenquest");
            else if (done >= total) lore.add("<green>✔ Alles erledigt");
            if (l != null) lore.add("<yellow>Klick: Wegweisung hierhin");
            int slot = 19 + i + (i >= 7 ? 2 : 0);
            set(slot, Items.make(done >= total ? Material.EMERALD : running ? Material.WRITABLE_BOOK : Material.VILLAGER_SPAWN_EGG,
                    (done >= total ? "<green>" : "<gold>") + f.name(), lore), e -> {
                if (l == null) return;
                side.setNavTarget(id, f.id());
                Msg.send(p, "<green>Wegweisung zu <white>" + f.name() + "<green> ist an. <gray>(" + l.getBlockX() + ", " + l.getBlockZ() + ")");
                p.closeInventory();
            });
        }

        boolean nav = side.nav(id);
        set(38, Items.make(nav ? Material.COMPASS : Material.GRAY_DYE, "<white>Wegweisung: " + (nav ? "<green>an" : "<red>aus"), List.of(
                "<gray>Pfeil in der Aktionsleiste", "<gray>zur richtigen Figur.", "", "<yellow>Klick zum Umschalten")), e -> {
            side.toggleNav(id);
            open(p);
        });
        if (run != null) {
            set(42, Items.make(Material.BARRIER, "<red>Nebenquest abbrechen", List.of(
                    "<gray>Der Fortschritt geht verloren,", "<gray>die Aufgabe kannst du neu annehmen.", "", "<yellow>Klick")), e -> {
                side.cancel(id);
                Msg.send(p, "<gray>Nebenquest abgebrochen.");
                open(p);
            });
        }
        addBack(p);
    }
}
