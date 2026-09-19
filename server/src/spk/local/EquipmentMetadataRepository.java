package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * v5.2.3 bulk equipment-slot reconciliation.
 *
 * Resolution order is intentionally evidence weighted:
 *   1) exact anchors from equipment_slots.tsv;
 *   2) exact item->appearance-position observations from the production V9 corpus;
 *   3) SpawnPK equipClone / fullClone / clone lineage;
 *   4) exact ambiguity overrides where the item name alone cannot distinguish a slot;
 *   5) conservative semantic families from the current item name/action vocabulary.
 *
 * The semantic layer exists because the exact current client does not store a universal
 * server equipment-slot integer in rs.d.k. It is a reconstruction aid, not a claim that
 * the production server's private equipment definition table has been recovered.
 */
final class EquipmentMetadataRepository {
    static final int WEAPON_APPEARANCE_SLOT = EquipmentSlot.WEAPON.appearanceIndex;

    enum Coverage { NONE, FULL_HELM, FULL_BODY }

    static final class Meta {
        final int itemId;
        final EquipmentSlot slot;
        final int appearanceSlot;
        final boolean twoHanded;
        final String equipAction;
        final EquipmentPoseProfile pose;
        final Coverage coverage;
        final String evidence;

        Meta(int itemId, EquipmentSlot slot, boolean twoHanded, String equipAction,
             EquipmentPoseProfile pose, Coverage coverage, String evidence) {
            if (slot == null) throw new IllegalArgumentException("slot");
            this.itemId = itemId;
            this.slot = slot;
            this.appearanceSlot = slot.appearanceIndex;
            this.twoHanded = twoHanded;
            this.equipAction = equipAction == null ? (slot == EquipmentSlot.WEAPON ? "Wield" : "Wear") : equipAction;
            this.pose = pose == null ? EquipmentPoseProfile.DEFAULT_HUMAN : pose;
            this.coverage = coverage == null ? Coverage.NONE : coverage;
            this.evidence = evidence == null ? "" : evidence;
        }
    }

    private static final Map<Integer,Meta> EXPLICIT = load();
    private static final Map<Integer,ProductionSlot> PRODUCTION_APPEARANCE = loadProductionAppearance();
    private static final Map<Integer,EquipmentSlot> AMBIGUITY = ambiguitySlots();


    private static final class ProductionSlot {
        final EquipmentSlot slot;
        final int appearanceIndex;
        final int observations;
        final String evidence;
        ProductionSlot(EquipmentSlot slot, int appearanceIndex, int observations, String evidence) {
            this.slot=slot; this.appearanceIndex=appearanceIndex; this.observations=observations; this.evidence=evidence;
        }
    }

    private EquipmentMetadataRepository() {}

    static Meta resolve(int itemId) {
        if (!ItemDefinitionRepository.exists(itemId)) return null;
        return resolveInternal(itemId, new LinkedHashSet<>(), false);
    }

    static boolean isResolved(int itemId) { return resolve(itemId) != null; }

    static int productionObservedCount() { return PRODUCTION_APPEARANCE.size(); }

    static int currentActionCandidateCount() {
        int n=0;
        for (ItemCatalog.Meta item : ItemCatalog.all()) if (ItemCatalog.canWieldOrWear(item.id)) n++;
        return n;
    }

    static int currentActionResolvedCount() {
        int n=0;
        for (ItemCatalog.Meta item : ItemCatalog.all())
            if (ItemCatalog.canWieldOrWear(item.id) && resolve(item.id) != null) n++;
        return n;
    }

    /**
     * The exact client only sends opcode 41 after an actual Wield/Wear/Equip menu action.
     * Base-cache items do not all have their action arrays mirrored into LocalLab items.tsv,
     * so this trusted packet path may use the conservative semantic classifier even when
     * the local metadata row itself has no copied action string.
     */
    static Meta resolveForClientEquipAction(int itemId) {
        Meta m=resolve(itemId);
        if (m != null) return m;
        ItemCatalog.Meta item=ItemDefinitionRepository.get(itemId);
        if (item == null) return null;
        EquipmentSlot semantic=semanticSlot(item);
        if (semantic == null) return null;
        return semanticMeta(itemId,item,semantic,"CLIENT_OPCODE41_PROVES_EQUIPABLE");
    }

    /** Rehydrate metadata for an item already stored in a known equipment slot. */
    static Meta resolveKnownSlot(int itemId, EquipmentSlot knownSlot) {
        if (itemId < 0 || knownSlot == null) return null;
        Meta m=resolve(itemId);
        if (m != null && m.slot == knownSlot) return m;
        ItemCatalog.Meta item=ItemDefinitionRepository.get(itemId);
        if (item == null) return null;
        return semanticMeta(itemId,item,knownSlot,"KNOWN_EQUIPMENT_SLOT_"+knownSlot.name());
    }

    static String resolutionEvidence(int itemId) {
        Meta m = resolve(itemId);
        return m == null ? "UNRESOLVED_AFTER_BULK_RECONCILIATION" : m.evidence;
    }

    static boolean hidesHairOrBeard(int itemId) {
        Meta m = resolve(itemId);
        return m != null && m.coverage == Coverage.FULL_HELM;
    }

    static boolean hidesArms(int itemId) {
        Meta m = resolve(itemId);
        return m != null && m.coverage == Coverage.FULL_BODY;
    }

    private static Meta resolveInternal(int itemId, Set<Integer> seen, boolean lineageContext) {
        if (!seen.add(itemId)) return null;

        Meta exact = EXPLICIT.get(itemId);
        if (exact != null) return withPose(exact, itemId, exact.evidence);

        ItemCatalog.Meta item = ItemDefinitionRepository.get(itemId);
        if (item == null) return null;

        ProductionSlot observed = PRODUCTION_APPEARANCE.get(itemId);
        if (observed != null) {
            // Production packet-81 position is authoritative for the SLOT, but it does
            // not tell us secondary server semantics such as full-helm/full-body
            // coverage or whether a weapon is two-handed.  Preserve those properties
            // from a compatible SpawnPK clone/equipClone/fullClone lineage when the
            // lineage resolves to the same slot.  This is important for e.g. Ultimate
            // slayer helmet 27034: HEAD is directly production-observed, while its
            // equipClone 21724 supplies FULL_HELM coverage.
            Meta observedMeta = semanticMeta(itemId, item, observed.slot,
                observed.evidence+"+OBS="+observed.observations);
            Meta lineage = compatibleLineage(item, seen, observed.slot);
            if (lineage != null) {
                boolean twoHanded = observedMeta.twoHanded || lineage.twoHanded;
                Coverage coverage = observedMeta.coverage != Coverage.NONE ? observedMeta.coverage : lineage.coverage;
                EquipmentPoseProfile pose = observedMeta.pose != EquipmentPoseProfile.DEFAULT_HUMAN
                    ? observedMeta.pose : lineage.pose;
                return new Meta(itemId, observed.slot, twoHanded,
                    observed.slot == EquipmentSlot.WEAPON ? "Wield" : "Wear",
                    pose, coverage,
                    observedMeta.evidence+"+COMPATIBLE_LINEAGE="+lineage.evidence);
            }
            return observedMeta;
        }

        int[] parents = { item.equipClone, item.fullClone, item.clone };
        String[] labels = { "EQUIPCLONE", "FULLCLONE", "CLONE" };
        for (int n=0; n<parents.length; n++) {
            int parent=parents[n];
            if (parent < 0) continue;
            Meta base = resolveInternal(parent, new LinkedHashSet<>(seen), true);
            if (base != null) {
                EquipmentPoseProfile pose = inheritedPose(itemId, base);
                return new Meta(itemId, base.slot, base.twoHanded,
                    base.slot == EquipmentSlot.WEAPON ? "Wield" : "Wear",
                    pose, base.coverage,
                    "INHERITED_FROM_"+parent+"_VIA_"+labels[n]+"+"+base.evidence);
            }
        }

        EquipmentSlot forced = AMBIGUITY.get(itemId);
        if (forced != null) return semanticMeta(itemId, item, forced, "CURRENT_ITEM_AMBIGUITY_OVERRIDE");

        boolean explicitEquipAction=item.hasAction("Wear") || item.hasAction("Wield") || item.hasAction("Equip");
        EquipmentSlot semantic = (explicitEquipAction || lineageContext) ? semanticSlot(item) : null;
        if (semantic != null) return semanticMeta(itemId, item, semantic, "BULK_SEMANTIC_SLOT_"+semantic.name());
        return null;
    }


    /**
     * Return the first clone/equipClone/fullClone lineage record that agrees with an
     * already-authoritative slot.  Disagreeing lineage is deliberately ignored: the
     * production appearance position wins and we never copy cross-slot properties.
     */
    private static Meta compatibleLineage(ItemCatalog.Meta item, Set<Integer> seen, EquipmentSlot authoritativeSlot) {
        int[] parents = { item.equipClone, item.fullClone, item.clone };
        String[] labels = { "EQUIPCLONE", "FULLCLONE", "CLONE" };
        for (int n=0; n<parents.length; n++) {
            int parent=parents[n];
            if (parent < 0) continue;
            Meta base = resolveInternal(parent, new LinkedHashSet<>(seen), true);
            if (base != null && base.slot == authoritativeSlot) {
                EquipmentPoseProfile pose = inheritedPose(item.id, base);
                return new Meta(item.id, base.slot, base.twoHanded,
                    base.slot == EquipmentSlot.WEAPON ? "Wield" : "Wear",
                    pose, base.coverage,
                    "INHERITED_FROM_"+parent+"_VIA_"+labels[n]+"+"+base.evidence);
            }
        }
        return null;
    }

    private static Meta semanticMeta(int itemId, ItemCatalog.Meta item, EquipmentSlot slot, String evidence) {
        boolean twoHanded = slot == EquipmentSlot.WEAPON && inferTwoHanded(item.name);
        Coverage coverage = inferCoverage(item.name, slot);
        String action = slot == EquipmentSlot.WEAPON ? "Wield" : "Wear";
        EquipmentPoseProfile pose = poseFor(itemId, slot);
        return new Meta(itemId, slot, twoHanded, action, pose, coverage,
            evidence+"+NAME="+normalizeEvidence(item.name));
    }

    private static EquipmentPoseProfile inheritedPose(int itemId, Meta base) {
        if (base.slot != EquipmentSlot.WEAPON) return EquipmentPoseProfile.DEFAULT_HUMAN;
        // Evidence precedence matters here: an exact/direct/canonical child pose wins,
        // then an independently recovered scythe profile; otherwise a proven clone
        // parent's pose is stronger than the broad v5.5 semantic family fallback.
        WeaponPoseRepository.Resolution ownObserved = WeaponPoseRepository.resolve(itemId);
        if (ownObserved != null) return ownObserved.pose;
        if (isScythe(itemId)) return EquipmentPoseProfile.SCYTHE_SPAWNPK_FAMILY;
        if (base.pose != EquipmentPoseProfile.DEFAULT_HUMAN) return base.pose;
        WeaponPoseRepository.Resolution semantic = WeaponPoseRepository.resolveSemanticFamily(itemId);
        return semantic == null ? EquipmentPoseProfile.DEFAULT_HUMAN : semantic.pose;
    }

    private static Meta withPose(Meta base, int itemId, String evidence) {
        EquipmentPoseProfile pose = poseFor(itemId, base.slot);
        if (pose == base.pose) return base;
        return new Meta(itemId, base.slot, base.twoHanded, base.equipAction, pose, base.coverage, evidence);
    }

    private static EquipmentPoseProfile poseFor(int itemId, EquipmentSlot slot) {
        if (slot != EquipmentSlot.WEAPON) return EquipmentPoseProfile.DEFAULT_HUMAN;
        WeaponPoseRepository.Resolution observed = WeaponPoseRepository.resolve(itemId);
        if (observed != null) return observed.pose;
        // Keep the independently recovered SpawnPK scythe-family fallback for
        // unobserved/current custom scythes such as Bloodrend itself.
        if (isScythe(itemId)) return EquipmentPoseProfile.SCYTHE_SPAWNPK_FAMILY;
        WeaponPoseRepository.Resolution semantic = WeaponPoseRepository.resolveSemanticFamily(itemId);
        if (semantic != null) return semantic.pose;
        return EquipmentPoseProfile.DEFAULT_HUMAN;
    }

    private static boolean isScythe(int id) {
        if (id==21566 || id==23202 || id==27485 || id==28525 || id==28526) return true;
        ItemCatalog.Meta m=ItemDefinitionRepository.get(id);
        return m != null && lower(m.name).contains("scythe");
    }

    /**
     * Exact-current custom ambiguities that cannot be distinguished reliably from
     * their display name alone. Most other current equipables are handled by the
     * semantic families below and clone inheritance.
     */
    private static Map<Integer,EquipmentSlot> ambiguitySlots() {
        Map<Integer,EquipmentSlot> m=new HashMap<>();
        // OSRS/public base-slot authority for identical-name pairs.
        m.put(542, EquipmentSlot.LEGS);   // Monk's robe (bottom)
        m.put(544, EquipmentSlot.CHEST);  // Monk's robe top

        // Current custom sets with deliberately generic display names.
        m.put(12855, EquipmentSlot.HEAD); // Hunter's honour (public OSRS head slot)
        m.put(12856, EquipmentSlot.HEAD); // Rogue's revenge (paired BH head cosmetic)
        m.put(20520, EquipmentSlot.LEGS); // Elder chaos robe (set bottom)
        m.put(20535, EquipmentSlot.LEGS); // Bloodmancer garb/robe/hood/boots set bottom
        m.put(20552, EquipmentSlot.CHEST); // Tuxedo torso
        m.put(21097, EquipmentSlot.HEAD); // Chicken head
        m.put(21109, EquipmentSlot.CHEST); // Armadyl d'hide torso
        m.put(21116, EquipmentSlot.CHEST); // Bandos d'hide torso
        m.put(21122, EquipmentSlot.CHEST); // Ancient d'hide torso
        m.put(21103, EquipmentSlot.CHEST); // Decorative armour: torso model has 2 worn components
        m.put(21104, EquipmentSlot.LEGS);  // Decorative armour: leg model
        m.put(21105, EquipmentSlot.HEAD);  // Decorative armour: head model
        m.put(22294, EquipmentSlot.CAPE);  // Master salvation back/aura cosmetic in SpawnPK's 11-slot layout
        m.put(22295, EquipmentSlot.CAPE);  // Master corruption back/aura cosmetic
        m.put(25401, EquipmentSlot.CHEST); // Xerician robe; robe bottom is a separate item
        m.put(28028, EquipmentSlot.LEGS);  // Elder chaos robe (or)

        // Lantern families are hand/offhand wearables in the current custom corpus.
        int[] lanterns={9065,21276,22183,22184,22185,23948,23949,23950,27411,27548,27549,27550,28712,28831};
        for (int id:lanterns) m.put(id,EquipmentSlot.SHIELD);

        // Cape/back cosmetics whose names are not normal cape vocabulary.
        m.put(21773,EquipmentSlot.CAPE); // Jolly parrot shoulder/back cosmetic
        m.put(27245,EquipmentSlot.CAPE); // Scroll sack; standard cape-slot item
        m.put(28802,EquipmentSlot.CAPE); // Giant boulder back cosmetic (equipClone crate-with-Zanik visual family)

        // Exotic rune pouch is an Equip-action, non-appearance accessory; use ammunition/accessory slot.
        m.put(27475,EquipmentSlot.AMMO);
        return Collections.unmodifiableMap(m);
    }

    private static EquipmentSlot semanticSlot(ItemCatalog.Meta item) {
        String n=" "+lower(item.name).replace('_',' ')+" ";

        // Non-weapon equipment must win before Wield fallback; several shields/books
        // use Wield and Infernal/max-cape variants can expose misleading action text.
        if (has(n,"helmet"," helm ","hood","coif"," hat "," mask ","crown","partyhat","party hat","tiara","headband","halo","beret","bonnet","mitre","fedora"," fez ","headgear","goggles","eye patch","eyepatch","bandana","snelm","cowl","faceguard","wreath","afro"," kasa ","headdress"," ears ","earmuffs")) return EquipmentSlot.HEAD;
        if (has(n," cape ","cloak","wings","backpack","accumulator","attractor","assembler","tokhaar-kal","tokhaar kal","bonesack","bone sack","shoulder monkey","shoulder parrot")) return EquipmentSlot.CAPE;
        if (has(n,"amulet","necklace","stole","symbol","pendant","scarf","gorget","torque","bow tie")) return EquipmentSlot.AMULET;
        if (has(n,"shield","defender","deflector"," ward ","buckler","bulwark","kiteshield","sq shield"," book ","tome"," kite ")) return EquipmentSlot.SHIELD;
        if (has(n,"platelegs","plateskirt","legguards","tassets"," chaps","trousers"," pants","shorts","leggings"," skirt"," kilt","bottom","pantaloons","tights","greaves","chausses","breeches","robe legs","robeskirt","robebottom","robe bottom","leatherskirt"," legs "," armour 2 "," armor 2 ")) return EquipmentSlot.LEGS;
        if (has(n,"platebody","chestguard","chestplate"," chest "," body ","torso","robe top","robetop","leathertop"," top ","shirt","jacket","tunic","chainbody","hauberk","brassard","cuirass","apron"," vest ","gown","labcoat","garb"," armour 1 "," armor 1 ")) return EquipmentSlot.CHEST;
        if (has(n,"gloves","gauntlets","vambraces","vamb","bracers","bracelet"," cuffs","manacles"," paws","wraps")) return EquipmentSlot.HANDS;
        if (has(n,"boots"," boot ","shoes","sandals","slippers","flippers"," feet "," socks")) return EquipmentSlot.FEET;
        if (has(n," ring ")) return EquipmentSlot.RING;
        if (has(n,"quiver")) return EquipmentSlot.AMMO;

        // SpawnPK update history explicitly describes icons and their blessing variants
        // as occupying the ammunition slot. Keep this before generic weapon fallback.
        if (has(n," icon","blessing"," rune pouch")) return EquipmentSlot.AMMO;
        if (has(n," arrow"," arrows"," bolt"," bolts"," ammo"," dart pack")) return EquipmentSlot.AMMO;

        // Generic robe is the torso only after explicit robe-bottom/legs cases above.
        if (has(n," robe "," robes ")) return EquipmentSlot.CHEST;

        // Strong weapon noun families. Some current customs expose Wear rather than Wield,
        // so names are used independently of the action string.
        if (has(n,"scythe","sword","longsword","godsword","scimitar","dagger","spear","hasta","halberd","pickaxe","battleaxe","greataxe"," war axe"," axe "," bow ","crossbow","c'bow","staff","sceptre","scepter"," wand","whip","maul","mace","hammer","flail","rapier","sabre","trident","bludgeon"," claws","sickle","flamberg","flamberge","keris","cannon","lance","javelin","thrownaxe","blowpipe","blade","cudgel","atlatl","salamander","pooplatl","korasi","balmung","nunchaku","sai","hand fan","spade","baguette")) return EquipmentSlot.WEAPON;

        // Action fallback is last, and only Wield is strong enough to imply weapon.
        if (item.hasAction("Wield")) return EquipmentSlot.WEAPON;
        return null;
    }

    private static boolean inferTwoHanded(String name) {
        String n=" "+lower(name)+" ";
        if (n.contains("crossbow") || n.contains("c'bow")) return n.contains("karil") || n.contains("ballista");
        return has(n,"scythe","godsword","2h sword","two-handed","halberd"," spear"," maul","ballista","longbow","shortbow"," bow ","twisted bow","zaryte bow","dark bow","flamberg","flamberge");
    }

    private static Coverage inferCoverage(String name, EquipmentSlot slot) {
        String n=lower(name);
        if (slot==EquipmentSlot.HEAD && (n.contains("full helm") || n.contains("faceguard") || n.contains("plate helm") || n.contains("great helm"))) return Coverage.FULL_HELM;
        if (slot==EquipmentSlot.CHEST && (n.contains("platebody") || n.contains("chestplate") || n.contains("plate body"))) return Coverage.FULL_BODY;
        return Coverage.NONE;
    }

    private static boolean has(String n,String... needles) {
        for (String x:needles) if (n.contains(x)) return true;
        return false;
    }
    private static String lower(String s) { return s==null?"":s.toLowerCase(Locale.ROOT); }
    private static String normalizeEvidence(String s) { return lower(s).replace(' ','_').replace('\t','_'); }

    private static Map<Integer,ProductionSlot> loadProductionAppearance() {
        Map<Integer,ProductionSlot> out = new HashMap<>();
        try (InputStream raw=EquipmentMetadataRepository.class.getResourceAsStream("/spk/local/production_appearance_slots.tsv")) {
            if (raw == null) throw new IllegalStateException("missing embedded /spk/local/production_appearance_slots.tsv");
            try (BufferedReader br=new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8))) {
                String line;
                while ((line=br.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0)=='#') continue;
                    String[] p=line.split("\\t",-1);
                    if (p.length < 5) throw new IllegalStateException("bad production appearance row: "+line);
                    int id=Integer.parseInt(p[0]);
                    EquipmentSlot slot=EquipmentSlot.valueOf(p[1]);
                    int appearanceIndex=Integer.parseInt(p[2]);
                    int observations=Integer.parseInt(p[3]);
                    if (slot.appearanceIndex != appearanceIndex)
                        throw new IllegalStateException("appearance position mismatch for "+id+": "+line);
                    ProductionSlot prior=out.put(id,new ProductionSlot(slot,appearanceIndex,observations,p[4]));
                    if (prior != null && prior.slot != slot)
                        throw new IllegalStateException("conflicting production slot for "+id);
                }
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<Integer,Meta> load() {
        Map<Integer,Meta> out = new HashMap<>();
        try (InputStream raw=EquipmentMetadataRepository.class.getResourceAsStream("/spk/local/equipment_slots.tsv")) {
            if (raw == null) throw new IllegalStateException("missing embedded /spk/local/equipment_slots.tsv");
            try (BufferedReader br=new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8))) {
                String line;
                while ((line=br.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0)=='#') continue;
                    String[] p=line.split("\\t",-1);
                    if (p.length < 5) throw new IllegalStateException("bad equipment row: "+line);
                    int id=Integer.parseInt(p[0]);
                    EquipmentSlot slot=EquipmentSlot.valueOf(p[1]);
                    boolean twoHanded="1".equals(p[2]);
                    Coverage coverage=Coverage.valueOf(p[3]);
                    EquipmentPoseProfile pose=poseFor(id,slot);
                    out.put(id,new Meta(id,slot,twoHanded,slot==EquipmentSlot.WEAPON?"Wield":"Wear",pose,coverage,p[4]));
                }
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        return Collections.unmodifiableMap(out);
    }
}
