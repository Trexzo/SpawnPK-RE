package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * R2 presentation-first attack authority imported into LocalLab.
 *
 * attackAnimation/authority are sourced from the exact-current R2 resolver.
 * speed/range rows marked LOCAL_HARNESS_* are deliberately mechanics fallbacks,
 * not claims about SpawnPK production server timing/formulas.
 */
final class WeaponAttackAuthorityRepository {
    static final class Row {
        final int itemId;
        final String itemName;
        final CombatWeaponProfile.DamageClass damageClass;
        final int attackAnimation;
        final String attackAuthority;
        final int speedTicks;
        final String speedAuthority;
        final int range;
        final String rangeAuthority;
        final int projectileId;
        final int gfxId;
        final String evidence;
        Row(int itemId,String itemName,CombatWeaponProfile.DamageClass damageClass,int attackAnimation,
            String attackAuthority,int speedTicks,String speedAuthority,int range,String rangeAuthority,
            int projectileId,int gfxId,String evidence){
            this.itemId=itemId;this.itemName=itemName;this.damageClass=damageClass;this.attackAnimation=attackAnimation;
            this.attackAuthority=attackAuthority;this.speedTicks=speedTicks;this.speedAuthority=speedAuthority;
            this.range=range;this.rangeAuthority=rangeAuthority;this.projectileId=projectileId;this.gfxId=gfxId;this.evidence=evidence;
        }
    }

    private static final LinkedHashMap<Integer,Row> ROWS=load();
    private WeaponAttackAuthorityRepository(){}
    static Row resolve(int itemId){return ROWS.get(itemId);}
    static Collection<Row> all(){return Collections.unmodifiableCollection(ROWS.values());}
    static int count(){return ROWS.size();}
    static int provenRuntimeCount(){int n=0;for(Row r:ROWS.values())if("PROVEN_RUNTIME".equals(r.attackAuthority))n++;return n;}

    private static LinkedHashMap<Integer,Row> load(){
        LinkedHashMap<Integer,Row> out=new LinkedHashMap<>();
        try(BufferedReader r=openData("weapon_attack_r2.tsv")){
            String line=r.readLine();
            while((line=r.readLine())!=null){
                if(line.isBlank())continue;
                String[] a=line.split("\\t",-1);
                if(a.length<12)throw new IOException("bad weapon attack row: "+line);
                int id=pi(a[0]);
                CombatWeaponProfile.DamageClass dc;
                try{dc=CombatWeaponProfile.DamageClass.valueOf(a[2]);}
                catch(Exception e){dc=CombatWeaponProfile.DamageClass.UNKNOWN;}
                Row row=new Row(id,a[1],dc,pi(a[3]),a[4],pi(a[5]),a[6],pi(a[7]),a[8],pi(a[9]),pi(a[10]),a[11]);
                if(out.put(id,row)!=null)throw new IOException("duplicate R2 weapon attack item "+id);
            }
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
        return out;
    }

    private static BufferedReader openData(String name)throws IOException{
        Path p=Paths.get("server","data",name);
        if(Files.isRegularFile(p))return Files.newBufferedReader(p,StandardCharsets.UTF_8);
        p=Paths.get("data",name);
        if(Files.isRegularFile(p))return Files.newBufferedReader(p,StandardCharsets.UTF_8);
        InputStream in=WeaponAttackAuthorityRepository.class.getResourceAsStream("/spk/local/"+name);
        if(in==null){ClassLoader cl=WeaponAttackAuthorityRepository.class.getClassLoader();if(cl!=null)in=cl.getResourceAsStream("spk/local/"+name);}
        if(in!=null)return new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        throw new FileNotFoundException(name+" (checked server/data, data, and classpath /spk/local)");
    }
    private static int pi(String s){return Integer.parseInt(s.trim());}
}
