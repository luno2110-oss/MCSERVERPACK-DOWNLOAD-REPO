package de.kuby.kubyplugins.ui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;

// ERZEUGT von tools/build_sym.py - nicht von Hand ändern.

/** Kleine farbige Symbole (8 x 8, Schrift kuby:sym) für Chat, Aktionsleiste und Hologramme. */
public enum Sym {
    FISH('\ue000'),
    ROD('\ue001'),
    GEM('\ue002'),
    BOOK('\ue003'),
    ANVIL('\ue004'),
    COIN('\ue005'),
    SPARKLE('\ue006'),
    CHEST('\ue007'),
    STAR('\ue008'),
    HEART('\ue009'),
    CLOCK('\ue00a'),
    CHIP('\ue00b'),
    DICE('\ue00c'),
    GIFT('\ue00d'),
    ARROW('\ue00e'),
    CROWN('\ue00f'),
    PINKGEM('\ue010'),
    CLOVER('\ue011'),
    MUSIC('\ue012');

    public static final Key FONT = Key.key("kuby", "sym");

    public final char c;

    Sym(char c) {
        this.c = c;
    }

    /** Das Symbol als Komponente (weiß, damit die Farben stimmen). */
    public Component component() {
        return Component.text(String.valueOf(c)).font(FONT).color(NamedTextColor.WHITE);
    }

    /** Ohne Schatten (für helle Hintergründe). */
    public Component flat() {
        return component().shadowColor(ShadowColor.none());
    }

    /** MiniMessage-Text des Symbols. */
    public String mini() {
        return "<white><font:kuby:sym>" + c + "</font></white>";
    }
}
