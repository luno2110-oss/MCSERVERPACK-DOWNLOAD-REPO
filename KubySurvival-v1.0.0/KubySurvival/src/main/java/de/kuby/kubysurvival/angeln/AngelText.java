package de.kuby.kubysurvival.angeln;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Zahlen und Texte im Angel-Stil: 1.561, 15,6 kg, 5.000$, ★★★☆☆, 1 Std 48 Min. */
public final class AngelText {

    private static final DecimalFormatSymbols DE = DecimalFormatSymbols.getInstance(Locale.GERMANY);

    private AngelText() {
    }

    public static String num(long v) {
        return new DecimalFormat("#,##0", DE).format(v);
    }

    public static String money(long v) {
        return num(v) + "$";
    }

    /** Eine Nachkommastelle, ohne ",0" bei ganzen Zahlen. */
    public static String dec(double v) {
        return new DecimalFormat("#,##0.#", DE).format(v);
    }

    /** Gewicht: unter 10 kg zwei Nachkommastellen (0,43 kg, 3,41 kg), sonst eine (15,6 kg). */
    public static String kg(double kg) {
        DecimalFormat f = new DecimalFormat(kg < 10 ? "#,##0.00" : "#,##0.0", DE);
        return f.format(kg) + " kg";
    }

    public static String stars(int n) {
        return "★".repeat(Math.max(0, n)) + "☆".repeat(Math.max(0, 5 - n));
    }

    /** "1 Std 48 Min", "48 Min", "1 Min". */
    public static String duration(long seconds) {
        long m = Math.max(1, (seconds + 59) / 60);
        long h = m / 60;
        m %= 60;
        if (h > 0) return h + " Std " + m + " Min";
        return m + " Min";
    }
}
