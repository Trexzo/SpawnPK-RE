package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * R7 read-only 339-row pet-named item research corpus. Candidate mappings stay
 * candidates; normal pet lifecycle continues to consume PetDefinitionRepository.
 */
final class PetResearchAuthorityRepository {
    static final class Entry {
        final int itemId;final String itemName;final boolean authoritativeNpcMapping;final String mappingCertainty;final int[] candidateNpcIds;final String effectText,effectTextProvenance,itemCandidateSource;
        Entry(int itemId,String itemName,boolean authoritativeNpcMapping,String mappingCertainty,int[] candidateNpcIds,String effectText,String effectTextProvenance,String itemCandidateSource){this.itemId=itemId;this.itemName=itemName;this.authoritativeNpcMapping=authoritativeNpcMapping;this.mappingCertainty=mappingCertainty;this.candidateNpcIds=candidateNpcIds;this.effectText=effectText;this.effectTextProvenance=effectTextProvenance;this.itemCandidateSource=itemCandidateSource;}
        public String toString(){return "PetResearch[item="+itemId+",name="+ItemAuthorityRepository.stripTags(itemName)+",certainty="+mappingCertainty+",authoritativeNpc="+authoritativeNpcMapping+",candidates="+Arrays.toString(candidateNpcIds)+"]";}
    }
    private static final LinkedHashMap<Integer,Entry> BY_ID=load();
    static int count(){return BY_ID.size();}
    static Entry get(int itemId){return BY_ID.get(itemId);}
    static Collection<Entry> all(){return Collections.unmodifiableCollection(BY_ID.values());}
    static int authoritativeNpcCount(){int n=0;for(Entry e:BY_ID.values())if(e.authoritativeNpcMapping)n++;return n;}
    static int withEffectText(){int n=0;for(Entry e:BY_ID.values())if(e.effectText!=null&&!e.effectText.isEmpty())n++;return n;}
    private static LinkedHashMap<Integer,Entry> load(){
        LinkedHashMap<Integer,Entry> m=new LinkedHashMap<>();
        try(InputStream in=PetResearchAuthorityRepository.class.getResourceAsStream("/spk/local/data/pet_research_authority_r7.tsv")){
            if(in==null)throw new IllegalStateException("missing pet_research_authority_r7.tsv");
            try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String s;boolean first=true;while((s=br.readLine())!=null){if(first){first=false;continue;}if(s.trim().isEmpty())continue;String[] p=s.split("\\t",-1);if(p.length<8)throw new IllegalStateException("bad pet row fields="+p.length+" row="+s);int id=Integer.parseInt(p[0]);Entry e=new Entry(id,p[1],Boolean.parseBoolean(p[2]),p[3],ids(p[4]),p[5],p[6],p[7]);if(m.put(id,e)!=null)throw new IllegalStateException("duplicate pet item "+id);}}
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
        return m;
    }
    private static int[] ids(String s){if(s==null||s.isEmpty())return new int[0];String[] p=s.split(",");int[] a=new int[p.length];for(int i=0;i<p.length;i++)a[i]=Integer.parseInt(p[i]);return a;}
    private PetResearchAuthorityRepository(){}
}
