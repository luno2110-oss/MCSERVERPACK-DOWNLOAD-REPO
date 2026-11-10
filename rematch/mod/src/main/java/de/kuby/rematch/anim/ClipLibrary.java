package de.kuby.rematch.anim;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Alle geladenen Animationen nach Namen. Wird beim Laden der Ressourcen neu gefüllt. */
public final class ClipLibrary {
    private volatile Rig rig;
    private final Map<String, Clip> clips = new ConcurrentHashMap<>();

    public ClipLibrary(Rig rig) { this.rig = rig; }

    public Rig rig() { return rig; }

    public void replaceAll(Rig rig, Collection<Clip> list) {
        this.rig = rig;
        clips.clear();
        for (Clip c : list) clips.put(c.name, c);
    }

    public void put(Clip c) { clips.put(c.name, c); }

    public Clip get(String name) { return clips.get(name); }

    public Collection<String> names() { return new TreeMap<>(clips).keySet(); }
}
