package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * v4.1 localhost item catalogue.
 *
 * The readable id/name/tradeable layer is generated from the supplied items.json
 * (28,673 current client-known ids). Current SpawnPK i.bin metadata augments rows
 * where available with actions, clone/equipClone and explicit stackability.
 *
 * It is intentionally metadata, not a claim that every item's server mechanics are
 * reconstructed. The exact client/cache remains authority for models and item UI.
 */
final class ItemCatalog {
    static final class Meta {
        final int id;
        final String name;
        final boolean tradeable;
        final Boolean stackable; // null means not stated by current custom config
        final String[] actions;
        final int clone;
        final int equipClone;
        final int fullClone;

        Meta(int id, String name, boolean tradeable, Boolean stackable,
             String[] actions, int clone, int equipClone, int fullClone) {
            this.id=id; this.name=name; this.tradeable=tradeable; this.stackable=stackable;
            this.actions=actions; this.clone=clone; this.equipClone=equipClone; this.fullClone=fullClone;
        }

        boolean hasAction(String wanted) {
            for (String a : actions) if (a != null && a.equalsIgnoreCase(wanted)) return true;
            return false;
        }
    }

    private static final Map<Integer,Meta> ITEMS = load();

    private ItemCatalog() {}

    static int count() { return ITEMS.size(); }
    static Meta get(int id) { return ITEMS.get(id); }
    static boolean exists(int id) { return ITEMS.containsKey(id); }
    static Collection<Meta> all() { return ITEMS.values(); }
    static String name(int id) {
        Meta m=ITEMS.get(id);
        return m==null ? "unknown-"+id : m.name;
    }

    static boolean canWieldOrWear(int id) {
        return inheritedAction(id, "Wield", new HashSet<>())
            || inheritedAction(id, "Wear", new HashSet<>())
            || inheritedAction(id, "Equip", new HashSet<>());
    }

    static boolean inheritedHasAction(int id, String action) {
        return inheritedAction(id, action, new HashSet<>());
    }

    /**
     * Exact explicit metadata is followed through SpawnPK clone/equipClone. For
     * base-cache ids without an explicit current custom flag, a tiny set of
     * already-live-certified stackables is retained; all other unknowns default
     * non-stackable so ::item cannot silently manufacture an invalid huge stack.
     */
    static boolean isStackable(int id) {
        Boolean b=inheritedStackable(id,new HashSet<>());
        if (b != null) return b;
        if (isSemanticStackableAmmo(id)) return true;
        switch (id) {
            case 995:  // coins
            case 560:  // death runes
            case 565:  // blood runes
                return true;
            default:
                return false;
        }
    }

    static String stackabilityEvidence(int id) {
        Boolean b=inheritedStackable(id,new HashSet<>());
        if (b != null) return "CURRENT_CONFIG_EXPLICIT_"+(b?"STACKABLE":"NONSTACKABLE");
        if (isSemanticStackableAmmo(id)) return "CURRENT_ITEM_NAME_AMMO_STACK_RULE";
        if (id==995 || id==560 || id==565) return "LIVE_FIXTURE_KNOWN_STACKABLE";
        return "UNKNOWN_DEFAULT_NONSTACKABLE";
    }

    /**
     * Base-cache item rows do not carry a trustworthy final stackable bit in the
     * recovered V9 census.  Ammunition is different: its concrete current item
     * identity and Wield action provide a narrow semantic category whose inventory
     * representation is stack-based.  Keep this intentionally restricted to
     * projectile/ammunition nouns so unrelated custom items remain fail-closed.
     */
    private static boolean isSemanticStackableAmmo(int id) {
        Meta m=ITEMS.get(id);
        if(m==null || m.name==null) return false;
        String n=m.name.toLowerCase(Locale.ROOT).replace('_',' ').trim();
        boolean ammoName = n.endsWith(" arrow") || n.endsWith(" arrows")
            || n.endsWith(" bolt") || n.endsWith(" bolts")
            || n.endsWith(" dart") || n.endsWith(" darts")
            || n.endsWith(" knife") || n.endsWith(" knives")
            || n.endsWith(" javelin") || n.endsWith(" javelins")
            || n.endsWith(" thrownaxe") || n.endsWith(" thrownaxes")
            || n.endsWith(" throwing axe") || n.endsWith(" throwing axes")
            || n.equals("cannonball") || n.equals("cannonballs");
        return ammoName;
    }

    /**
     * SpawnPK native player-icon family. V9.08 proved these are transported as
     * the optional rs.a.k.bs appearance item after the ordinary 12 worn
     * components, then merged by the exact client as a 13th wearable model.
     *
     * Classify by clone/equipClone/fullClone ancestry to the four native icon
     * roots rather than by name, so ordinary AMMO (arrows/bolts/etc.) can never
     * leak into the bs presentation channel.
     */
    static boolean isNativePlayerIcon(int id) {
        return nativeIconRoot(id,new HashSet<>()) >= 0;
    }

    static String nativePlayerIconEvidence(int id) {
        int root=nativeIconRoot(id,new HashSet<>());
        return root<0 ? "NOT_NATIVE_ICON_FAMILY" : "V908_BS_NATIVE_ICON_LINEAGE_ROOT_"+root;
    }

    private static int nativeIconRoot(int id,Set<Integer> seen) {
        if(!seen.add(id)) return -1;
        if(id==10556 || id==10557 || id==10558 || id==10559) return id;
        Meta m=ITEMS.get(id);
        if(m==null) return -1;
        int r=m.clone>=0?nativeIconRoot(m.clone,seen):-1;
        if(r>=0)return r;
        r=m.equipClone>=0?nativeIconRoot(m.equipClone,seen):-1;
        if(r>=0)return r;
        return m.fullClone>=0?nativeIconRoot(m.fullClone,seen):-1;
    }

    /** Bloodrend -> Sanguine scythe -> Scythe of vitur clone family.
     * Name matching alone is insufficient because kits/non-equipment can contain
     * "scythe" in their name. Later family members must also expose Wield. */
    static boolean isScytheFamily(int id) {
        if (id==21566 || id==28525 || id==28526) return true;
        return inheritedAction(id,"Wield",new HashSet<>())
            && inheritedNameContains(id,"scythe",new HashSet<>());
    }

    private static Boolean inheritedStackable(int id, Set<Integer> seen) {
        if (!seen.add(id)) return null;
        Meta m=ITEMS.get(id);
        if (m==null) return null;
        if (m.stackable != null) return m.stackable;
        Boolean b = m.clone >= 0 ? inheritedStackable(m.clone,seen) : null;
        if (b != null) return b;
        b = m.equipClone >= 0 ? inheritedStackable(m.equipClone,seen) : null;
        if (b != null) return b;
        return m.fullClone >= 0 ? inheritedStackable(m.fullClone,seen) : null;
    }

    private static boolean inheritedAction(int id,String action,Set<Integer> seen) {
        if (!seen.add(id)) return false;
        Meta m=ITEMS.get(id);
        if (m==null) return false;
        if (m.hasAction(action)) return true;
        return (m.clone>=0 && inheritedAction(m.clone,action,seen))
            || (m.equipClone>=0 && inheritedAction(m.equipClone,action,seen))
            || (m.fullClone>=0 && inheritedAction(m.fullClone,action,seen));
    }

    private static boolean inheritedNameContains(int id,String needle,Set<Integer> seen) {
        if (!seen.add(id)) return false;
        Meta m=ITEMS.get(id);
        if (m==null) return false;
        if (m.name.toLowerCase(Locale.ROOT).contains(needle)) return true;
        return (m.clone>=0 && inheritedNameContains(m.clone,needle,seen))
            || (m.equipClone>=0 && inheritedNameContains(m.equipClone,needle,seen))
            || (m.fullClone>=0 && inheritedNameContains(m.fullClone,needle,seen));
    }

    private static Map<Integer,Meta> load() {
        Map<Integer,Meta> out=new HashMap<>();
        try (InputStream raw=ItemCatalog.class.getResourceAsStream("/spk/local/items.tsv")) {
            if (raw==null) throw new IllegalStateException("missing embedded /spk/local/items.tsv");
            try (BufferedReader br=new BufferedReader(new InputStreamReader(raw,StandardCharsets.UTF_8))) {
                String line;
                while ((line=br.readLine())!=null) {
                    if (line.isEmpty() || line.charAt(0)=='#') continue;
                    String[] p=line.split("\t",-1);
                    if (p.length<7) continue;
                    int id=Integer.parseInt(p[0]);
                    boolean tradeable="1".equals(p[2]);
                    Boolean stackable="1".equals(p[3])?Boolean.TRUE:"0".equals(p[3])?Boolean.FALSE:null;
                    String[] actions=p[4].isEmpty()?new String[0]:p[4].split("\\|",-1);
                    int clone=parseInt(p[5],-1), equipClone=parseInt(p[6],-1);
                    int fullClone=p.length>=8?parseInt(p[7],-1):-1;
                    out.put(id,new Meta(id,p[1],tradeable,stackable,actions,clone,equipClone,fullClone));
                }
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        return Collections.unmodifiableMap(out);
    }

    private static int parseInt(String s,int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }
}
