package de.kuby.kubyplugins.cinema;

/**
 * Drehbuch einer Cutscene: berechnet jedes Bild nur aus dem Tick (ohne Bukkit), damit es überall gleich aussieht und
 * sich auch ohne Server (Vorschau) abspielen lässt.
 */
public interface Script {

    /** Länge in Ticks. */
    int length();

    /** Bild für Tick t (0 .. length-1) in f schreiben. */
    void frame(int t, Frame f);

    /** Ab welchem Tick Schleichen die Szene überspringen darf (-1 = nie). */
    default int skippableFrom() {
        return -1;
    }
}
