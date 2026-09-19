package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** R7 read-only import of the R25 605-row ammo/item classification corpus. */
final class AmmoAuthorityRepository {
    static final class Entry {
        final int itemId;
        final String name,classification,explicitStackable,rawCacheStackable,effectiveAuthority,note;
        Entry(int itemId,String name,String classification,String explicitStackable,String rawCacheStackable,String effectiveAuthority,String note){
            this.itemId=itemId;this.name=name;this.classification=classification;this.explicitStackable=explicitStackable;
            this.rawCacheStackable=rawCacheStackable;this.effectiveAuthority=effectiveAuthority;this.note=note;
        }
        public String toString(){return "AmmoAuthority[item="+itemId+",name="+ItemAuthorityRepository.stripTags(name)+",class="+classification+",effective="+effectiveAuthority+"]";}
    }
    private static final LinkedHashMap<Integer,Entry> BY_ID=load();
    static int count(){return BY_ID.size();}
    static Entry get(int itemId){return BY_ID.get(itemId);}
    static Collection<Entry> all(){return Collections.unmodifiableCollection(BY_ID.values());}
    static Map<String,Integer> classificationCounts(){LinkedHashMap<String,Integer> m=new LinkedHashMap<>();for(Entry e:BY_ID.values())m.put(e.classification,m.getOrDefault(e.classification,0)+1);return m;}
    private static LinkedHashMap<Integer,Entry> load(){
        LinkedHashMap<Integer,Entry> m=new LinkedHashMap<>();
        try(InputStream in=AmmoAuthorityRepository.class.getResourceAsStream("/spk/local/data/ammo_authority_r7.tsv")){
            if(in==null)throw new IllegalStateException("missing ammo_authority_r7.tsv");
            try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
                String s;boolean first=true;while((s=br.readLine())!=null){if(first){first=false;continue;}if(s.trim().isEmpty())continue;String[] p=s.split("\\t",-1);if(p.length<7)throw new IllegalStateException("bad ammo row fields="+p.length+" row="+s);int id=Integer.parseInt(p[0]);Entry e=new Entry(id,p[1],p[2],p[3],p[4],p[5],p[6]);if(m.put(id,e)!=null)throw new IllegalStateException("duplicate ammo item "+id);}
            }
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
        return m;
    }
    private AmmoAuthorityRepository(){}
}
