package de.kp.jumprun.holo;

/**
 * Die drei Seiten der Bestenliste. Jede Kategorie hat eine eigene Akzentfarbe.
 */
public enum Category {

    BEST_TIME("Beste Zeit", "#3dff6b"),
    COMPLETIONS("Am meisten abgeschlossen", "#ff8a1f"),
    FAILS("Meisten Fails", "#22d3ee");

    private final String title;
    private final String color;

    Category(String title, String color) {
        this.title = title;
        this.color = color;
    }

    public String title() {
        return title;
    }

    public String color() {
        return color;
    }

    public Category next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
