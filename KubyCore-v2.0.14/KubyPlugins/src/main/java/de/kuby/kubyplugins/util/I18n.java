package de.kuby.kubyplugins.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Englisch für Spieler, die es eingestellt haben (Einstellungen → Sprache). Der Code bleibt deutsch; übersetzt wird beim
 * Ausliefern über Vorlagen aus lang/en.tsv: je Zeile „deutsche Vorlage TAB englische Vorlage“, {} steht für einen Wert
 * (im Englischen auch {0}, {1} … zum Umstellen). Die Vorlagen kommen aus tools/i18n_extract.py (Ketten wie
 * "Du hast " + n + " Coins" → „Du hast {} Coins“) und werden auch innerhalb längerer Texte gefunden.
 *
 * <p>Wo übersetzt wird: Chat-Meldungen je Empfänger (Msg.send, Benachrichtigungen), und im Sprach-Kontext des handelnden
 * Spielers (Befehle, Klicks, Menüs) Item-Namen und -Beschreibungen, Menütitel, Dialogtexte und Tooltips.
 */
public final class I18n {

    private I18n() {
    }

    private record Tpl(String de, Pattern pattern, List<String> en, int[] order, int weight, String key, boolean word) {}

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_]{3,16}");

    private static volatile List<Tpl> templates = List.of();
    private static volatile Aho index = new Aho(List.of());
    /** Großgeschriebene Überschriften („SPIELZEIT“ aus „Spielzeit“): ganze Texte ohne Werte, in Großbuchstaben. */
    private static volatile Map<String, String> upper = Map.of();
    private static volatile Predicate<UUID> english = id -> false;
    private static volatile Predicate<String> names = s -> false;
    private static final ThreadLocal<Boolean> CONTEXT = new ThreadLocal<>();
    private static final int CACHE = 20_000;
    private static final Map<String, String> cache = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> e) {
            return size() > CACHE;
        }
    });

    // =================================================================================================================
    // Laden
    // =================================================================================================================

    /** Wer Englisch eingestellt hat (aus den Spieler-Einstellungen). */
    public static void resolver(Predicate<UUID> isEnglish) {
        english = isEnglish;
    }

    /** Spielernamen: ein einzelnes Wort, das so heißt wie ein Spieler („Hecht“, „Stern“), bleibt unübersetzt. */
    public static void names(Predicate<String> isName) {
        names = isName;
        cache.clear();
    }

    /** Lädt die Vorlagen (Datei aus dem Plugin, danach Ergänzungen/Korrekturen aus dem Datenordner). Gibt die Anzahl zurück. */
    public static int load(List<InputStream> sources, java.util.logging.Logger log) {
        Map<String, String> map = new LinkedHashMap<>();
        for (InputStream in : sources) {
            if (in == null) continue;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int tab = line.indexOf('\t');
                    if (tab <= 0) continue;
                    map.put(unescape(line.substring(0, tab)), unescape(line.substring(tab + 1)));
                }
            } catch (IOException e) {
                log.warning("Übersetzung konnte nicht gelesen werden: " + e.getMessage());
            }
        }
        List<Tpl> list = new ArrayList<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            Tpl t = compile(e.getKey(), e.getValue());
            if (t == null) {
                log.warning("Übersetzung passt nicht zur Vorlage (Anzahl {}): " + e.getKey());
                continue;
            }
            list.add(t);
        }
        list.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        Map<String, String> up = new HashMap<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            String de = e.getKey();
            if (de.contains("{}") || de.contains("<") || de.equals(de.toUpperCase(java.util.Locale.ROOT))) continue;
            up.putIfAbsent(de.strip().toUpperCase(java.util.Locale.ROOT), e.getValue().strip().toUpperCase(java.util.Locale.ROOT));
        }
        for (String de : map.keySet()) up.remove(de);   // eigene Vorlagen in Großbuchstaben gehen vor
        upper = up;
        templates = list;
        List<String> keys = new ArrayList<>();
        for (Tpl t : list) keys.add(t.key());
        index = new Aho(keys);
        cache.clear();
        return list.size();
    }

    private static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static Tpl compile(String de, String en) {
        // Leerzeichen am Rand der deutschen Vorlage gehören zum Satzbau drumherum: im Englischen übernehmen
        String lead = de.substring(0, de.length() - de.stripLeading().length());
        String trail = de.substring(de.stripTrailing().length());
        if (!lead.isEmpty() && (en.isEmpty() || !Character.isWhitespace(en.charAt(0)))) en = lead + en;
        if (!trail.isEmpty() && (en.isEmpty() || !Character.isWhitespace(en.charAt(en.length() - 1)))) en = en + trail;
        String[] lits = de.split("\\{}", -1);
        int holes = lits.length - 1;
        // Englisch: Teile und Reihenfolge der Werte ({} der Reihe nach oder {0}, {1} …)
        List<String> parts = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        Matcher m = Pattern.compile("\\{(\\d*)}").matcher(en);
        int last = 0, seq = 0;
        while (m.find()) {
            parts.add(en.substring(last, m.start()));
            order.add(m.group(1).isEmpty() ? seq++ : Integer.parseInt(m.group(1)));
            last = m.end();
        }
        parts.add(en.substring(last));
        if (order.size() != holes) return null;
        for (int o : order) if (o < 0 || o >= holes) return null;
        StringBuilder rx = new StringBuilder();
        String key = "";
        int weight = 0;
        for (int i = 0; i < lits.length; i++) {
            if (i > 0) rx.append("(.*?)");
            rx.append(Pattern.quote(lits[i]));
            weight += lits[i].length();
            if (letters(lits[i]) > letters(key)) key = lits[i];
        }
        if (key.isEmpty()) return null;
        int[] ord = order.stream().mapToInt(Integer::intValue).toArray();
        boolean word = holes == 0 && WORD.matcher(de).matches();
        return new Tpl(de, Pattern.compile(rx.toString(), Pattern.DOTALL), parts, ord, weight, key, word);
    }

    private static int letters(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) n++;
        return n;
    }

    public static int size() {
        return templates.size();
    }

    // =================================================================================================================
    // Kontext
    // =================================================================================================================

    public static boolean english(UUID id) {
        return id != null && english.test(id);
    }

    public static boolean english(Player p) {
        return p != null && english(p.getUniqueId());
    }

    /** Sprache des Spielers, der gerade handelt (Befehl, Klick, Menü), sonst Deutsch. */
    public static boolean context() {
        return Boolean.TRUE.equals(CONTEXT.get());
    }

    /** Führt r im Sprach-Kontext von p aus (verschachtelt erlaubt). */
    public static void as(Player p, Runnable r) {
        as(p, () -> {
            r.run();
            return null;
        });
    }

    public static <T> T as(Player p, Supplier<T> s) {
        Boolean before = CONTEXT.get();
        CONTEXT.set(english(p));
        try {
            return s.get();
        } finally {
            if (before == null) CONTEXT.remove();
            else CONTEXT.set(before);
        }
    }

    /** Setzt den Kontext für die Dauer eines Ereignisses (Gegenstück: clear). */
    public static void enter(Player p) {
        CONTEXT.set(english(p));
    }

    public static void clear() {
        CONTEXT.remove();
    }

    // =================================================================================================================
    // Übersetzen
    // =================================================================================================================

    /** Text im aktuellen Kontext (unverändert, wenn Deutsch). */
    public static String ctx(String text) {
        return context() ? tr(text) : text;
    }

    /** Text für einen Empfänger. */
    public static String forPlayer(Player p, String text) {
        return english(p) ? tr(text) : text;
    }

    /** Übersetzt alle Vorlagen in text ins Englische. */
    public static String tr(String text) {
        if (text == null || text.isEmpty() || templates.isEmpty()) return text;
        String hit = cache.get(text);
        if (hit != null) return hit;
        String out = translate(text, 0);
        if (out.equals(text) && caps(text)) out = upper.getOrDefault(text, text);
        cache.put(text, out);
        return out;
    }

    private static String translate(String text, int depth) {
        if (depth > 4 || text.isEmpty()) return text;
        List<Tpl> cands = index.candidates(text, templates);
        if (cands.isEmpty()) return text;
        List<int[]> cmds = commands(text);
        // Treffer sammeln (längere Vorlagen zuerst), ohne Überlappung
        List<int[]> spans = new ArrayList<>();
        List<String> repl = new ArrayList<>();
        for (Tpl t : cands) {
            if (t.word() && names.test(t.de())) continue;
            Matcher m = t.pattern().matcher(text);
            int from = 0;
            while (from <= text.length() && m.find(from)) {
                int s = m.start(), e = m.end();
                if (e == s) break;
                if (!free(spans, s, e) || !boundary(text, s, e) || inside(cmds, s) || (t.word() && compound(text, s, e))) {
                    from = s + 1;
                    continue;
                }
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < t.en().size(); i++) {
                    b.append(t.en().get(i));
                    if (i < t.order().length) {
                        int g = t.order()[i] + 1;
                        // Werte in Klick-Befehlen (/job waehlen Verzauberer) bleiben, wie sie sind
                        b.append(inside(cmds, m.start(g)) ? m.group(g) : translate(m.group(g), depth + 1));
                    }
                }
                spans.add(new int[]{s, e});
                repl.add(b.toString());
                from = e;
            }
        }
        if (spans.isEmpty()) return text;
        Integer[] idx = new Integer[spans.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Integer.compare(spans.get(a)[0], spans.get(b)[0]));
        StringBuilder out = new StringBuilder();
        int pos = 0;
        for (int i : idx) {
            int[] sp = spans.get(i);
            out.append(text, pos, sp[0]).append(repl.get(i));
            pos = sp[1];
        }
        out.append(text.substring(pos));
        return out.toString();
    }

    private static boolean caps(String s) {
        int up = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLowerCase(c)) return false;
            if (Character.isUpperCase(c)) up++;
        }
        return up >= 2;
    }

    private static boolean free(List<int[]> spans, int s, int e) {
        for (int[] sp : spans) if (s < sp[1] && sp[0] < e) return false;
        return true;
    }

    /** Einzelwort als Teil eines zusammengesetzten Namens („Farm-Kasse“, „Kasse_2“): bleibt, wie es ist. */
    private static boolean compound(String text, int s, int e) {
        return (s > 0 && (text.charAt(s - 1) == '-' || text.charAt(s - 1) == '_'))
                || (e < text.length() && (text.charAt(e) == '-' || text.charAt(e) == '_'));
    }

    /** Bereiche von Klick- und Einfüge-Tags (<click:run_command:'…'>): deren Inhalt ist ein Befehl, kein Text. */
    private static List<int[]> commands(String text) {
        List<int[]> res = null;
        int i = 0;
        while (true) {
            int c = text.indexOf("<click:", i), n = text.indexOf("<insert:", i);
            int s = c < 0 ? n : n < 0 ? c : Math.min(c, n);
            if (s < 0) return res;
            char quote = 0;
            int e = s + 1;
            for (; e < text.length(); e++) {
                char ch = text.charAt(e);
                if (quote != 0) {
                    if (ch == '\\') e++;
                    else if (ch == quote) quote = 0;
                } else if (ch == '\'' || ch == '"') {
                    quote = ch;
                } else if (ch == '>') {
                    break;
                }
            }
            if (res == null) res = new ArrayList<>();
            res.add(new int[]{s, e});
            i = e + 1;
        }
    }

    private static boolean inside(List<int[]> ranges, int pos) {
        if (ranges == null) return false;
        for (int[] r : ranges) if (pos > r[0] && pos < r[1]) return true;
        return false;
    }

    /** Ganze Wörter: vor dem Anfang und nach dem Ende kein Buchstabe, wenn die Vorlage dort mit einem beginnt/endet. */
    private static boolean boundary(String text, int s, int e) {
        if (s > 0 && Character.isLetterOrDigit(text.charAt(s)) && Character.isLetterOrDigit(text.charAt(s - 1))) return false;
        return e >= text.length() || !Character.isLetterOrDigit(text.charAt(e - 1)) || !Character.isLetterOrDigit(text.charAt(e));
    }

    /** Übersetzt die Texte einer Komponente (inkl. Tooltips) – für Tooltips und fertige Komponenten. */
    public static Component tr(Component c) {
        if (c == null || templates.isEmpty()) return c;
        Component out = c;
        if (c instanceof TextComponent t && !t.content().isEmpty()) {
            String s = tr(t.content());
            if (!s.equals(t.content())) out = t.content(s);
        }
        HoverEvent<?> h = out.hoverEvent();
        if (h != null && h.action() == HoverEvent.Action.SHOW_TEXT) {
            out = out.hoverEvent(HoverEvent.showText(tr((Component) h.value())));
        }
        if (!out.children().isEmpty()) {
            List<Component> kids = new ArrayList<>(out.children().size());
            for (Component k : out.children()) kids.add(tr(k));
            out = out.children(kids);
        }
        return out;
    }

    /** Komponente im aktuellen Kontext. */
    public static Component ctx(Component c) {
        return context() ? tr(c) : c;
    }

    // =================================================================================================================
    // Suche nach Kandidaten (Aho-Corasick über das längste Textstück jeder Vorlage)
    // =================================================================================================================

    private static final class Aho {
        private final List<Map<Character, Integer>> next = new ArrayList<>();
        private final List<Integer> fail = new ArrayList<>();
        private final List<List<Integer>> out = new ArrayList<>();

        Aho(List<String> keys) {
            node();
            for (int k = 0; k < keys.size(); k++) {
                int s = 0;
                for (char c : keys.get(k).toCharArray()) {
                    Integer n = next.get(s).get(c);
                    if (n == null) {
                        n = node();
                        next.get(s).put(c, n);
                    }
                    s = n;
                }
                out.get(s).add(k);
            }
            ArrayDeque<Integer> q = new ArrayDeque<>();
            for (int n : next.get(0).values()) {
                fail.set(n, 0);
                q.add(n);
            }
            while (!q.isEmpty()) {
                int s = q.poll();
                for (Map.Entry<Character, Integer> e : next.get(s).entrySet()) {
                    int t = e.getValue();
                    q.add(t);
                    int f = fail.get(s);
                    while (f != 0 && !next.get(f).containsKey(e.getKey())) f = fail.get(f);
                    Integer g = next.get(f).get(e.getKey());
                    fail.set(t, g != null && g != t ? g : 0);
                    out.get(t).addAll(out.get(fail.get(t)));
                }
            }
        }

        private int node() {
            next.add(new HashMap<>());
            fail.add(0);
            out.add(new ArrayList<>());
            return next.size() - 1;
        }

        /** Vorlagen, deren Schlüssel im Text vorkommt, in der Reihenfolge der Liste (längere zuerst). */
        List<Tpl> candidates(String text, List<Tpl> all) {
            java.util.BitSet hit = new java.util.BitSet(all.size());
            int s = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                while (s != 0 && !next.get(s).containsKey(c)) s = fail.get(s);
                Integer n = next.get(s).get(c);
                s = n == null ? 0 : n;
                for (int k : out.get(s)) hit.set(k);
            }
            List<Tpl> res = new ArrayList<>();
            for (int k = hit.nextSetBit(0); k >= 0; k = hit.nextSetBit(k + 1)) res.add(all.get(k));
            return res;
        }
    }
}
