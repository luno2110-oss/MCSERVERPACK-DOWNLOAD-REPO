package de.kuby.kubysurvival.angeln;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Angel-Daten eines Spielers. */
public final class AngelProfile {

    /** Ein Tagesauftrag. */
    public static final class Order {
        public String type;
        /** Gewässer oder Seltenheit (je nach Typ), sonst leer. */
        public String param = "";
        public int goal;
        public int done;
        public long money;
        public int points;
        public boolean finished;

        public Order copy() {
            Order o = new Order();
            o.type = type;
            o.param = param;
            o.goal = goal;
            o.done = done;
            o.money = money;
            o.points = points;
            o.finished = finished;
            return o;
        }
    }

    public long points;
    public int catches;
    public int weekCatches;
    public String week = "";
    public final Map<String, Integer> caught = new HashMap<>();
    public final Map<String, Double> best = new HashMap<>();
    public long earned;
    public int streak;
    public int bestStreak;
    public long lastCatch;
    public int treasures;
    public final Map<Upgrade, Integer> upgrades = new EnumMap<>(Upgrade.class);
    public boolean autoSell;
    public int doubleCatch;
    public final Map<Bait, Integer> baits = new EnumMap<>(Bait.class);
    public Bait active;
    public final Set<Water> pagesDone = EnumSet.noneOf(Water.class);
    public String orderDay = "";
    public final List<Order> orders = new ArrayList<>();
    public boolean bonusDone;
    /** Händler: Stunde (z. B. 2026-10-23T19) und schon zum vollen Preis verkauft. */
    public String dealerHour = "";
    public long dealerSold;

    public int level() {
        return AngelService.levelOf(points);
    }

    public int upgrade(Upgrade u) {
        return upgrades.getOrDefault(u, 0);
    }

    public int bait(Bait b) {
        return baits.getOrDefault(b, 0);
    }

    public int caughtSpecies() {
        int n = 0;
        for (FishSpecies s : FishSpecies.ALL) if (caught.getOrDefault(s.id(), 0) > 0) n++;
        return n;
    }

    public int caughtSpecies(Water w) {
        int n = 0;
        for (FishSpecies s : FishSpecies.of(w)) if (caught.getOrDefault(s.id(), 0) > 0) n++;
        return n;
    }

    public int ordersDone() {
        int n = 0;
        for (Order o : orders) if (o.finished) n++;
        return n;
    }
}
