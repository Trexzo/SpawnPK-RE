package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Weapon pose authority.
 *
 * The original V9.02 direct observations remain strongest. R2 adds item-specific
 * production-root/clone/semantic candidate tuples for hundreds of current weapon
 * rows; those are imported with their authority label intact and never rewritten
 * as direct production proof.
 */
final class WeaponPoseRepository {
    static final class Resolution {
        final EquipmentPoseProfile pose;
        final String evidence;
        Resolution(EquipmentPoseProfile pose,String evidence){this.pose=pose;this.evidence=evidence;}
    }

    private static final Map<Integer,Resolution> DIRECT = new LinkedHashMap<>();
    private static final Map<Integer,Resolution> R2 = new LinkedHashMap<>();
    private static final Map<String,Resolution> FAMILY = new LinkedHashMap<>();
    private static final Set<String> CONFLICTING_FAMILIES = new HashSet<>();

    static {
        try (BufferedReader reader = openData("weapon_poses.tsv")) { loadDirect(reader); }
        catch (IOException e) { throw new ExceptionInInitializerError(e); }
        try (BufferedReader reader = openData("weapon_pose_r2.tsv")) { loadR2(reader); }
        catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }

    private WeaponPoseRepository() {}

    static Resolution resolve(int itemId) {
        Resolution direct=DIRECT.get(itemId);
        if(direct!=null) return direct;
        Resolution r2=R2.get(itemId);
        if(r2!=null) return r2;
        ItemCatalog.Meta item=ItemDefinitionRepository.get(itemId);
        if(item==null) return null;
        String key=canonical(item.name);
        if(key.isEmpty() || CONFLICTING_FAMILIES.contains(key)) return null;
        Resolution family=FAMILY.get(key);
        if(family==null) return null;
        return new Resolution(family.pose,"V54_CANONICAL_WEAPON_FAMILY="+key+"+"+family.evidence);
    }

    static Resolution resolveSemanticFamily(int itemId) {
        ItemCatalog.Meta item=ItemDefinitionRepository.get(itemId);
        if(item==null || item.name==null) return null;
        String n=" "+item.name.toLowerCase(Locale.ROOT).replace('_',' ')+" ";
        if(n.contains(" whip") || n.contains("whip ") || n.contains("tentacle"))
            return inferred(EquipmentPoseProfile.WHIP_FAMILY,"WHIP_TENTACLE",itemId);
        if(n.contains(" staff") || n.contains("staff ") || n.contains(" wand") || n.contains("wand ")
            || n.contains("trident") || n.contains("sceptre") || n.contains("scepter"))
            return inferred(EquipmentPoseProfile.STAFF_MAGIC_FAMILY,"STAFF_WAND_TRIDENT_SCEPTRE",itemId);
        if(n.contains(" maul") || n.contains("maul "))
            return inferred(EquipmentPoseProfile.MAUL_HEAVY_FAMILY,"ORDINARY_MAUL",itemId);
        if(n.contains("godsword") || n.contains("2h sword") || n.contains("two-handed sword"))
            return inferred(EquipmentPoseProfile.TWO_HANDED_SWORD_FAMILY,"2H_GODSWORD",itemId);
        return null;
    }

    private static Resolution inferred(EquipmentPoseProfile p,String family,int itemId){
        return new Resolution(p,"V55_INFERRED_FROM_PRODUCTION_CONSENSUS_FAMILY="+family+"+ITEM="+itemId+"+"+p.evidence);
    }

    static int directCount(){return DIRECT.size();}
    static int r2Count(){return R2.size();}
    static int familyCount(){return FAMILY.size();}
    static int conflictingFamilyCount(){return CONFLICTING_FAMILIES.size();}

    private static void loadDirect(BufferedReader r) throws IOException {
        Map<String,Resolution> candidates=new LinkedHashMap<>();
        String line=r.readLine();
        while((line=r.readLine())!=null){
            if(line.isBlank()) continue;
            String[] a=line.split("\\t",-1);
            if(a.length<14) throw new IOException("bad weapon pose row: "+line);
            int id=pi(a[0]); int events=pi(a[2]); int dominant=pi(a[3]); int local=pi(a[4]); int remote=pi(a[5]); String provenance=a[13];
            EquipmentPoseProfile pose=new EquipmentPoseProfile("V902_OBSERVED_WEAPON_"+id,
                pi(a[6]),pi(a[7]),pi(a[8]),pi(a[9]),pi(a[10]),pi(a[11]),pi(a[12]),EquipmentPoseProfile.ALL_FIELDS_MASK,
                provenance+"+ITEM="+id+"+EVENTS="+events+"+DOMINANT="+dominant+"+LOCAL="+local+"+REMOTE="+remote);
            Resolution res=new Resolution(pose,pose.evidence);
            if(DIRECT.put(id,res)!=null) throw new IOException("duplicate weapon pose item "+id);
            String key=canonical(a[1]);
            if(key.isEmpty() || CONFLICTING_FAMILIES.contains(key)) continue;
            Resolution old=candidates.get(key);
            if(old==null)candidates.put(key,res);
            else if(!Arrays.equals(old.pose.toArray(),pose.toArray())){candidates.remove(key);CONFLICTING_FAMILIES.add(key);}
        }
        FAMILY.putAll(candidates);
    }

    private static void loadR2(BufferedReader r)throws IOException{
        String line=r.readLine();
        while((line=r.readLine())!=null){
            if(line.isBlank())continue;
            String[] a=line.split("\\t",-1);
            if(a.length<11)throw new IOException("bad R2 weapon pose row: "+line);
            int id=pi(a[0]);
            // Direct V9.02 rows already win through DIRECT and need not be duplicated.
            if(DIRECT.containsKey(id))continue;
            String authority=a[9], ev=a[10];
            EquipmentPoseProfile pose=new EquipmentPoseProfile("R2_WEAPON_"+id,
                pi(a[2]),pi(a[3]),pi(a[4]),pi(a[5]),pi(a[6]),pi(a[7]),pi(a[8]),EquipmentPoseProfile.ALL_FIELDS_MASK,
                "R2_POSE_AUTHORITY="+authority+"+"+ev);
            if(R2.put(id,new Resolution(pose,pose.evidence))!=null)throw new IOException("duplicate R2 pose item "+id);
        }
    }

    static String canonical(String s){
        if(s==null) return "";
        String n=s.toLowerCase(Locale.ROOT).replaceAll("@[a-z0-9]+@", "").replace('_',' ').trim();
        String prev;
        do {prev=n;n=n.replaceFirst("\\s*\\((?:or|i|e|dyed|broken|uncharged|charged|inactive|active)\\)\\s*$","").trim();}
        while(!n.equals(prev));
        return n.replaceAll("\\s+"," ");
    }

    private static BufferedReader openData(String name) throws IOException {
        Path p=Paths.get("server","data",name);
        if(Files.isRegularFile(p)) return Files.newBufferedReader(p,StandardCharsets.UTF_8);
        p=Paths.get("data",name);
        if(Files.isRegularFile(p)) return Files.newBufferedReader(p,StandardCharsets.UTF_8);
        InputStream in=WeaponPoseRepository.class.getResourceAsStream("/spk/local/"+name);
        if(in==null){ClassLoader cl=WeaponPoseRepository.class.getClassLoader();if(cl!=null) in=cl.getResourceAsStream("spk/local/"+name);}
        if(in!=null) return new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        throw new FileNotFoundException(name+" (checked server/data, data, and classpath /spk/local)");
    }
    private static int pi(String s){return Integer.parseInt(s.trim());}
}
