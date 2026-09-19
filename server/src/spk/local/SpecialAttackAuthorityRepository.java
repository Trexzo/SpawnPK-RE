package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** R7 metadata-only special-attack authority. It never promotes unknown formulas. */
final class SpecialAttackAuthorityRepository {
    static final class Entry {
        final int itemId; final String name,effectText,source,costText,damageMultiplierText,damageBoostText,metadataAuthority,presentationBinding,runtimeFormulaEffect;
        final boolean mentionsBurn,mentionsStun,mentionsDrain;
        Entry(int itemId,String name,String effectText,String source,String costText,String damageMultiplierText,String damageBoostText,boolean burn,boolean stun,boolean drain,String metadataAuthority,String presentationBinding,String runtimeFormulaEffect){
            this.itemId=itemId;this.name=name;this.effectText=effectText;this.source=source;this.costText=costText;this.damageMultiplierText=damageMultiplierText;this.damageBoostText=damageBoostText;this.mentionsBurn=burn;this.mentionsStun=stun;this.mentionsDrain=drain;this.metadataAuthority=metadataAuthority;this.presentationBinding=presentationBinding;this.runtimeFormulaEffect=runtimeFormulaEffect;
        }
        boolean formulaResolved(){return runtimeFormulaEffect!=null&&!runtimeFormulaEffect.contains("UNKNOWN_SERVER_AUTHORITY");}
        public String toString(){return "SpecialAuthority[item="+itemId+",name="+ItemAuthorityRepository.stripTags(name)+",meta="+metadataAuthority+",binding="+presentationBinding+",formula="+(formulaResolved()?"resolved":"unknown")+"]";}
    }
    private static final LinkedHashMap<Integer,Entry> BY_ID=load();
    static int count(){return BY_ID.size();}
    static Entry get(int itemId){return BY_ID.get(itemId);}
    static Collection<Entry> all(){return Collections.unmodifiableCollection(BY_ID.values());}
    static int formulaResolvedCount(){int n=0;for(Entry e:BY_ID.values())if(e.formulaResolved())n++;return n;}
    private static LinkedHashMap<Integer,Entry> load(){
        LinkedHashMap<Integer,Entry> m=new LinkedHashMap<>();
        try(InputStream in=SpecialAttackAuthorityRepository.class.getResourceAsStream("/spk/local/data/special_attack_authority_r7.tsv")){
            if(in==null)throw new IllegalStateException("missing special_attack_authority_r7.tsv");
            try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String s;boolean first=true;while((s=br.readLine())!=null){if(first){first=false;continue;}if(s.trim().isEmpty())continue;String[] p=s.split("\\t",-1);if(p.length<13)throw new IllegalStateException("bad special row fields="+p.length+" row="+s);int id=Integer.parseInt(p[0]);Entry e=new Entry(id,p[1],p[2],p[3],p[4],p[5],p[6],Boolean.parseBoolean(p[7]),Boolean.parseBoolean(p[8]),Boolean.parseBoolean(p[9]),p[10],p[11],p[12]);if(m.put(id,e)!=null)throw new IllegalStateException("duplicate special item "+id);}}
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
        return m;
    }
    private SpecialAttackAuthorityRepository(){}
}
