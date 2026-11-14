package de.kuby.kubyplugins.beta;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragen des Beta-Feedback-Formulars: 7 Seiten, je Seite Sterne-Fragen (1 bis 6, 6 = am besten, oder „nicht getestet“,
 * Pflicht) und Textfragen (freiwillig). Die Schlüssel bleiben stabil, damit alte Antworten bei neuen Fragen lesbar
 * bleiben.
 */
public final class FeedbackQuestions {

    public enum Type { STARS, TEXT }

    /** Eine Frage: Schlüssel, Text, Art, Hinweis (bei Sternen: was 6 bedeutet). */
    public record Question(String key, String text, Type type, String hint) {}

    /** Eine Seite: Titel, kurze Beschreibung, Fragen. */
    public record Page(String title, String sub, List<Question> questions) {}

    private static final List<Page> PAGES = new ArrayList<>();

    private static Question s(String key, String text, String hint) {
        return new Question(key, text, Type.STARS, hint);
    }

    private static Question t(String key, String text) {
        return new Question(key, text, Type.TEXT, null);
    }

    static {
        PAGES.add(new Page("Allgemein", "Dein Gesamteindruck vom Server", List.of(
                s("gesamt", "Gesamteindruck vom Server", "6 = großartig"),
                s("spass", "Wie viel Spaß hat dir das Spielen gemacht?", "6 = sehr viel"),
                s("einstieg", "War der Einstieg (Einführung, erste Schritte) verständlich?", "6 = sofort klar"),
                s("hub", "Hub und Modus-Auswahl (Kompass, NPCs, /modus)", "6 = sehr gut"),
                s("leistung", "Lief der Server flüssig (kein Lag, keine Ruckler)?", "6 = immer flüssig"),
                s("weiterspielen", "Würdest du nach der Beta weiterspielen?", "6 = auf jeden Fall"),
                t("gut", "Was hat dir am besten gefallen?"))));
        PAGES.add(new Page("Wirtschaft", "Geld, Preise, Handel und Belohnungen", List.of(
                s("eco_sauber", "Hat das Economy-System sauber funktioniert (Kontostand, Überweisungen)?", "6 = fehlerfrei"),
                s("eco_shop", "Shop und Verkaufen: Preise fair und verständlich?", "6 = sehr fair"),
                s("eco_markt", "Markt, Aufträge und Handel zwischen Spielern", "6 = sehr gut"),
                s("eco_boerse", "Börse und Investments", "6 = sehr gut"),
                s("eco_juwelen", "Juwelen und Juwelenshop (Preise, Angebote)", "6 = sehr fair"),
                s("eco_belohnung", "Belohnungen (Tageskalender, Spielzeit, Battlepass, Quests) fair?", "6 = sehr fair"),
                s("eco_modi", "Getrennte Kontostände je Modus verständlich?", "6 = völlig klar"),
                t("eco_fehler", "Gab es Geld-Fehler, doppelte Auszahlungen oder Exploits? Wo genau?"))));
        PAGES.add(new Page("UI und Anzeigen", "Menüs, Grafiken, Texte, Leisten", List.of(
                s("ui_menues", "Waren die Menüs verständlich und gut zu bedienen?", "6 = sehr gut"),
                s("ui_grafik", "Gefallen dir die Grafiken und Icons?", "6 = sehr schön"),
                s("ui_lesbar", "Waren alle Texte gut lesbar (nichts abgeschnitten oder überlappend)?", "6 = alles lesbar"),
                s("ui_fehler", "Gab es Probleme mit der UI oder anderen Anzeigen?", "6 = keine Probleme"),
                s("ui_leiste", "Info-Leiste oben und Scoreboard", "6 = sehr hilfreich"),
                s("ui_navigation", "Navigation (Zurück-Knöpfe, Seiten, Reiter)", "6 = sehr gut"),
                s("ui_pack", "Hat das Resource Pack ohne Probleme geladen?", "6 = ohne Probleme"),
                t("ui_text", "Welche Anzeige war kaputt, verwirrend oder hat gefehlt?"))));
        PAGES.add(new Page("Sound und Effekte", "Klänge, Partikel, Animationen", List.of(
                s("sfx_klick", "Klänge bei Klicks, Käufen und Belohnungen", "6 = sehr passend"),
                s("sfx_fehlend", "Haben SFX gefehlt?", "6 = nichts gefehlt"),
                s("sfx_laut", "War die Lautstärke angenehm?", "6 = genau richtig"),
                s("sfx_partikel", "Partikel und Effekte (Funken, Titel, Feuerwerk)", "6 = sehr schön"),
                s("sfx_musik", "Musik und Atmosphäre (z. B. Casino)", "6 = sehr gut"),
                s("sfx_cutscene", "Cutscenes und Kamerafahrten", "6 = sehr gut"),
                t("sfx_text", "Wo hat ein Sound oder Effekt gefehlt oder gestört?"))));
        PAGES.add(new Page("Modi", "SMP, Survival, Skyblock und der Wechsel", List.of(
                s("modus_smp", "SMP (PvP, Trophäen, Kopfgeld, Duelle)", "6 = sehr gut"),
                s("modus_survival", "Survival (Grundstücke, Jobs, Casino, Angeln)", "6 = sehr gut"),
                s("modus_skyblock", "Skyblock (Insel, Generatoren, One-Block, Combo)", "6 = sehr gut"),
                s("modus_wechsel", "Lief der Wechsel zwischen den Modi reibungslos?", "6 = immer"),
                s("modus_inventar", "Wurden Inventar, Position und Stand je Modus richtig getrennt?", "6 = immer richtig"),
                s("modus_sozial", "Teams, Freunde, Nachrichten und Chat", "6 = sehr gut"),
                t("modus_text", "Welcher Modus braucht am meisten Arbeit und warum?"))));
        PAGES.add(new Page("Features", "Die einzelnen Systeme", List.of(
                s("f_angeln", "Angeln (Fische, Minispiel, Angelpass, Fisch-Markt)", "6 = sehr gut"),
                s("f_casino", "Casino (Roulette, Blackjack, Slots, Crash, Poker)", "6 = sehr gut"),
                s("f_quests", "Quests und Events", "6 = sehr gut"),
                s("f_jobs", "Jobs und Token-Shop", "6 = sehr gut"),
                s("f_cookie", "Cookie Clicker", "6 = sehr gut"),
                s("f_kosmetik", "Kosmetik (Hüte, Spuren, Titel)", "6 = sehr gut"),
                s("f_kisten", "Kisten und Schlüssel (Crates, Cutscenes)", "6 = sehr gut"),
                t("f_fehlt", "Welches Feature fehlt dir oder würdest du dir wünschen?"))));
        PAGES.add(new Page("Technik und Abschluss", "Bugs, Stabilität und letzte Worte", List.of(
                s("t_bugs", "Bist du auf Bugs gestoßen?", "6 = keine Bugs"),
                s("t_stabil", "War der Server stabil (keine Kicks, Abstürze, Rücksetzer)?", "6 = immer stabil"),
                s("t_befehle", "Waren Befehle und Hilfe (/hilfe, /guide) verständlich?", "6 = sehr verständlich"),
                s("t_support", "Support und Team erreichbar und hilfreich?", "6 = sehr hilfreich"),
                s("t_empfehlen", "Würdest du den Server Freunden empfehlen?", "6 = auf jeden Fall"),
                t("t_bugtext", "Beschreibe gefundene Bugs (wo, was, wie nachstellbar)"),
                t("t_sonstiges", "Sonstiges: Wünsche, Lob, Kritik"))));
    }

    private FeedbackQuestions() {
    }

    public static List<Page> pages() {
        return PAGES;
    }

    public static List<Question> all() {
        List<Question> l = new ArrayList<>();
        for (Page p : PAGES) l.addAll(p.questions());
        return l;
    }

    public static Question byKey(String key) {
        for (Question q : all()) if (q.key().equals(key)) return q;
        return null;
    }
}
