package h3d;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Nachbau der nötigsten Bukkit-Teile: merkt sich alle gespawnten Anzeigen mit ihrem Zustand. */
public final class Mock {

    public static final List<Ent> ENTITIES = new ArrayList<>();
    public static final List<Runnable> LATER = new ArrayList<>();
    private static Object world;
    private static Object server;
    private static Object plugin;

    public static final class Ent {
        public final String type;
        public Location loc;
        public final Map<String, Object> props = new LinkedHashMap<>();
        public final Map<String, Object> pdc = new HashMap<>();
        public boolean removed;
        public final UUID uuid = UUID.randomUUID();
        public Object proxy;

        Ent(String type, Location loc) {
            this.type = type;
            this.loc = loc;
        }
    }

    private static Object defaultFor(Class<?> r) {
        if (r == boolean.class) return false;
        if (r == int.class) return 0;
        if (r == long.class) return 0L;
        if (r == float.class) return 0f;
        if (r == double.class) return 0d;
        if (r == short.class) return (short) 0;
        if (r == byte.class) return (byte) 0;
        if (r == char.class) return (char) 0;
        if (r == List.class || r == java.util.Collection.class) return new ArrayList<>();
        if (r == java.util.Set.class) return new java.util.HashSet<>();
        return null;
    }

    private static String prop(String name) {
        String p = name.substring(3);
        return Character.toLowerCase(p.charAt(0)) + p.substring(1);
    }

    public static Object world() {
        if (world != null) return world;
        world = Proxy.newProxyInstance(Mock.class.getClassLoader(), new Class<?>[]{org.bukkit.World.class}, (pr, m, a) -> {
            switch (m.getName()) {
                case "spawn":
                case "spawnEntity": {
                    Location l = ((Location) a[0]).clone();
                    Class<?> cls = a[1] instanceof Class<?> c ? c : Entity.class;
                    Object e = entity(cls, l);
                    for (Object o : a) {
                        if (o instanceof Consumer<?> c) {
                            @SuppressWarnings("unchecked") Consumer<Object> cc = (Consumer<Object>) c;
                            cc.accept(e);
                        }
                    }
                    return e;
                }
                case "getNearbyEntities": {
                    List<Object> out = new ArrayList<>();
                    for (Ent en : ENTITIES) if (!en.removed) out.add(en.proxy);
                    return out;
                }
                case "getName":
                    return "world";
                case "getUID":
                    return new UUID(1, 1);
                case "equals":
                    return pr == a[0];
                case "hashCode":
                    return 1;
                case "toString":
                    return "MockWorld";
                default:
                    return defaultFor(m.getReturnType());
            }
        });
        return world;
    }

    public static org.bukkit.Server server() {
        if (server != null) return (org.bukkit.Server) server;
        Object scheduler = Proxy.newProxyInstance(Mock.class.getClassLoader(), new Class<?>[]{org.bukkit.scheduler.BukkitScheduler.class},
                (pr, m, a) -> {
                    if (m.getName().equals("runTaskLater") || m.getName().equals("runTask")) {
                        for (Object o : a) if (o instanceof Runnable r) LATER.add(r);
                    }
                    if (m.getReturnType() == org.bukkit.scheduler.BukkitTask.class) {
                        return Proxy.newProxyInstance(Mock.class.getClassLoader(), new Class<?>[]{org.bukkit.scheduler.BukkitTask.class},
                                (p2, m2, a2) -> defaultFor(m2.getReturnType()));
                    }
                    return defaultFor(m.getReturnType());
                });
        server = Proxy.newProxyInstance(Mock.class.getClassLoader(), new Class<?>[]{org.bukkit.Server.class}, (pr, m, a) -> {
            if (m.getName().equals("getScheduler")) return scheduler;
            if (m.getName().equals("getLogger")) return Logger.getLogger("mock");
            return defaultFor(m.getReturnType());
        });
        return (org.bukkit.Server) server;
    }

    public static org.bukkit.plugin.Plugin plugin() {
        if (plugin != null) return (org.bukkit.plugin.Plugin) plugin;
        plugin = Proxy.newProxyInstance(Mock.class.getClassLoader(), new Class<?>[]{org.bukkit.plugin.Plugin.class}, (pr, m, a) -> {
            if (m.getName().equals("getServer")) return server();
            if (m.getName().equals("getLogger")) return Logger.getLogger("mock");
            if (m.getName().equals("getName")) return "Kuby";
            return defaultFor(m.getReturnType());
        });
        return (org.bukkit.plugin.Plugin) plugin;
    }

    public static void runLater() {
        while (!LATER.isEmpty()) {
            List<Runnable> l = new ArrayList<>(LATER);
            LATER.clear();
            for (Runnable r : l) r.run();
        }
    }

    public static ItemMeta meta() {
        Map<String, Object> props = new HashMap<>();
        return (ItemMeta) Proxy.newProxyInstance(Mock.class.getClassLoader(),
                new Class<?>[]{ItemMeta.class, org.bukkit.inventory.meta.LeatherArmorMeta.class},
                (pr, m, a) -> {
                    String n = m.getName();
                    if (n.equals("setItemModel")) props.put("itemModel", a[0]);
                    if (n.equals("getItemModel")) return props.get("itemModel");
                    if (n.equals("setColor")) props.put("color", a[0]);
                    if (n.equals("getColor")) return props.get("color");
                    if (n.equals("clone")) return pr;
                    if (n.equals("hashCode")) return System.identityHashCode(pr);
                    if (n.equals("equals")) return pr == a[0];
                    return defaultFor(m.getReturnType());
                });
    }

    public static BlockData block(Material mat) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("material", mat);
        return (BlockData) Proxy.newProxyInstance(Mock.class.getClassLoader(), new Class<?>[]{BlockData.class,
                        org.bukkit.block.data.type.Switch.class, org.bukkit.block.data.Lightable.class,
                        org.bukkit.block.data.type.Slab.class, org.bukkit.block.data.Rotatable.class},
                (pr, m, a) -> {
                    String n = m.getName();
                    if (n.equals("getMaterial")) return mat;
                    if (n.equals("clone")) return pr;
                    if (n.equals("getAsString")) return mat.getKey() + props.toString();
                    if (n.equals("hashCode")) return System.identityHashCode(pr);
                    if (n.equals("equals")) return pr == a[0];
                    if (n.startsWith("set") && a != null && a.length == 1) {
                        props.put(prop(n), a[0]);
                        return null;
                    }
                    if (n.startsWith("is") && n.length() > 2) {
                        Object v = props.get(Character.toLowerCase(n.charAt(2)) + n.substring(3));
                        return v != null ? v : false;
                    }
                    if (n.startsWith("get") && n.length() > 3) {
                        Object v = props.get(prop(n));
                        if (v != null) return v;
                    }
                    if (n.equals("toString")) return "Block" + props;
                    return defaultFor(m.getReturnType());
                });
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> blockProps(BlockData d) {
        try {
            InvocationHandler h = Proxy.getInvocationHandler(d);
            // Zustand über einen Trick holen: toString liefert die Karte
            return Map.of("text", d.toString());
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Object entity(Class<?> cls, Location l) {
        Ent en = new Ent(cls.getSimpleName(), l);
        Map<String, Object> pdc = en.pdc;
        Object pdcProxy = Proxy.newProxyInstance(Mock.class.getClassLoader(),
                new Class<?>[]{org.bukkit.persistence.PersistentDataContainer.class}, (pr, m, a) -> {
                    switch (m.getName()) {
                        case "set":
                            pdc.put(a[0].toString(), a[2]);
                            return null;
                        case "get":
                            return pdc.get(a[0].toString());
                        case "getOrDefault":
                            return pdc.getOrDefault(a[0].toString(), a[2]);
                        case "has":
                            return pdc.containsKey(a[0].toString());
                        case "remove":
                            pdc.remove(a[0].toString());
                            return null;
                        default:
                            return defaultFor(m.getReturnType());
                    }
                });
        Object e = Proxy.newProxyInstance(Mock.class.getClassLoader(), new Class<?>[]{cls}, new InvocationHandler() {
            @Override
            public Object invoke(Object pr, Method m, Object[] a) {
                String n = m.getName();
                switch (n) {
                    case "getPersistentDataContainer":
                        return pdcProxy;
                    case "getLocation":
                        return en.loc.clone();
                    case "getWorld":
                        return world();
                    case "teleport":
                        en.loc = ((Location) a[0]).clone();
                        return true;
                    case "isValid":
                        return !en.removed;
                    case "isDead":
                        return en.removed;
                    case "remove":
                        en.removed = true;
                        return null;
                    case "getUniqueId":
                        return en.uuid;
                    case "getEntityId":
                        return ENTITIES.indexOf(en);
                    case "hashCode":
                        return System.identityHashCode(pr);
                    case "equals":
                        return pr == a[0];
                    case "toString":
                        return en.type + "@" + en.loc;
                    case "text":
                        if (a != null && a.length == 1) {
                            en.props.put("text", a[0]);
                            return null;
                        }
                        return en.props.getOrDefault("text", Component.empty());
                    case "getTransformation":
                        Object t = en.props.get("transformation");
                        return t != null ? t : new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf());
                    case "addPassenger":
                        return true;
                    case "getPassengers":
                        return new ArrayList<>();
                    case "setRotation":
                        en.loc.setYaw((Float) a[0]);
                        en.loc.setPitch((Float) a[1]);
                        return null;
                    default:
                        break;
                }
                if (n.startsWith("set") && a != null && a.length == 1) {
                    en.props.put(prop(n), a[0]);
                    return null;
                }
                if (n.startsWith("get") && n.length() > 3 && (a == null || a.length == 0)) {
                    Object v = en.props.get(prop(n));
                    if (v != null) return v;
                }
                if (n.startsWith("is") && n.length() > 2 && (a == null || a.length == 0)) {
                    Object v = en.props.get(Character.toLowerCase(n.charAt(2)) + n.substring(3));
                    if (v instanceof Boolean) return v;
                }
                return defaultFor(m.getReturnType());
            }
        });
        en.proxy = e;
        ENTITIES.add(en);
        return e;
    }

    // ---------- Ausgabe ----------

    private static JsonArray arr(float... v) {
        JsonArray a = new JsonArray();
        for (float f : v) a.add(f);
        return a;
    }

    public static JsonArray dump() {
        JsonArray out = new JsonArray();
        for (Ent en : ENTITIES) {
            if (en.removed) continue;
            JsonObject o = new JsonObject();
            o.addProperty("type", en.type);
            o.add("pos", arr((float) en.loc.getX(), (float) en.loc.getY(), (float) en.loc.getZ()));
            o.addProperty("yaw", en.loc.getYaw());
            o.addProperty("pitch", en.loc.getPitch());
            for (Map.Entry<String, Object> p : en.props.entrySet()) {
                Object v = p.getValue();
                String k = p.getKey();
                if (v instanceof Transformation t) {
                    JsonObject j = new JsonObject();
                    Vector3f tr = t.getTranslation(), sc = t.getScale();
                    Quaternionf l = t.getLeftRotation(), r = t.getRightRotation();
                    j.add("t", arr(tr.x, tr.y, tr.z));
                    j.add("l", arr(l.x, l.y, l.z, l.w));
                    j.add("s", arr(sc.x, sc.y, sc.z));
                    j.add("r", arr(r.x, r.y, r.z, r.w));
                    o.add("transformation", j);
                } else if (v instanceof Component c) {
                    o.add(k, GsonComponentSerializer.gson().serializeToTree(c));
                } else if (v instanceof ItemStack it) {
                    JsonObject j = new JsonObject();
                    j.addProperty("material", it.getType().getKey().toString());
                    if (it.hasItemMeta()) {
                        NamespacedKey key = it.getItemMeta().getItemModel();
                        if (key != null) j.addProperty("model", key.toString());
                        org.bukkit.Color col = ((org.bukkit.inventory.meta.LeatherArmorMeta) it.getItemMeta()).getColor();
                        if (col != null) j.addProperty("color", col.asRGB());
                    }
                    o.add("item", j);
                } else if (v instanceof BlockData bd) {
                    JsonObject j = new JsonObject();
                    j.addProperty("material", bd.getMaterial().getKey().toString());
                    j.addProperty("state", bd.toString());
                    o.add("block", j);
                } else if (v instanceof org.bukkit.Color c) {
                    o.addProperty(k, c.asARGB());
                } else if (v instanceof Number num) {
                    o.addProperty(k, num);
                } else if (v instanceof Boolean b) {
                    o.addProperty(k, b);
                } else if (v instanceof Enum<?> en2) {
                    o.addProperty(k, en2.name());
                } else if (v != null) {
                    o.addProperty(k, v.toString());
                }
            }
            JsonObject pd = new JsonObject();
            for (Map.Entry<String, Object> p : en.pdc.entrySet()) pd.addProperty(p.getKey(), String.valueOf(p.getValue()));
            o.add("pdc", pd);
            out.add(o);
        }
        return out;
    }

    private Mock() {
    }
}
