package de.kuby.kubyplugins.bedrock;

import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Baut aus dem FormModel ein Cumulus-SimpleForm und sendet es (Floodgate oder Geyser). Klicks laufen im Haupt-Thread. */
final class FormSender {

    private FormSender() {
    }

    static boolean send(Plugin plugin, Player p, FormModel.Model m, Consumer<Player> onClose, boolean floodgate) throws Exception {
        UUID id = p.getUniqueId();
        List<FormModel.Button> buttons = m.buttons();
        SimpleForm.Builder b = SimpleForm.builder().title("§l" + m.title()).content(m.content());
        for (FormModel.Button bt : buttons) {
            if (bt.image() != null) b.button(bt.text(), FormImage.Type.PATH, bt.image());
            else b.button(bt.text());
        }
        b.validResultHandler(r -> Bukkit.getScheduler().runTask(plugin, () -> {
            Bedrock.answered(id);
            Player pl = Bukkit.getPlayer(id);
            int i = r.clickedButtonId();
            if (pl == null || i < 0 || i >= buttons.size()) return;
            FormModel.Button bt = buttons.get(i);
            if (bt.url() != null) {
                Msg.send(pl, "<aqua>Link: <white>" + Msg.escape(bt.url()));
                return;
            }
            if (bt.action() != null) de.kuby.kubyplugins.ui.UiClicks.runAction(pl, bt.action());
        }));
        b.closedOrInvalidResultHandler(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            Bedrock.answered(id);
            Player pl = Bukkit.getPlayer(id);
            if (pl != null && onClose != null) onClose.accept(pl);
        }));
        SimpleForm form = b.build();
        return floodgate ? FloodgateBridge.send(id, form) : GeyserBridge.send(id, form);
    }
}
