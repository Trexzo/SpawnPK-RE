package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Repository-driven pet item -> NPC mapping recovered from the current V9.02 corpus.
 *
 * Exact normalized-name winners remain the strongest bulk tier. v5.5 additionally
 * promotes narrowly inferable rows (single approximate winner, equal-cardinality
 * numbered variant families, and explicitly evidenced preferred candidates) while
 * preserving provenance. Remaining ambiguous rows stay fail-closed. Ancient guardian
 * 20776 -> 3098 carries stronger passive production Drop->follower correlation provenance.
 */
final class PetDefinitionRepository {
    static final class Def {
        final int itemId,npcId,standAnim,walkAnim,turn180Anim,turn90CWAnim,turn90CCWAnim,size;
        final String itemName,npcName,models,provenance;
        Def(int itemId,int npcId,String itemName,String npcName,int standAnim,int walkAnim,
            int turn180Anim,int turn90CWAnim,int turn90CCWAnim,int size,String models,String provenance) {
            this.itemId=itemId; this.npcId=npcId; this.itemName=itemName; this.npcName=npcName;
            this.standAnim=standAnim; this.walkAnim=walkAnim; this.turn180Anim=turn180Anim;
            this.turn90CWAnim=turn90CWAnim; this.turn90CCWAnim=turn90CCWAnim; this.size=size;
            this.models=models; this.provenance=provenance;
        }
        @Override public String toString(){return "PetDef{"+itemId+"->"+npcId+" "+itemName+" / "+npcName+" provenance="+provenance+"}";}
    }

    private static final Map<Integer,Def> BY_ITEM = new LinkedHashMap<>();
    private static final Map<Integer,String> AMBIGUOUS = new LinkedHashMap<>();
    static {
        try {
            loadMappings(resolveData("pet_mappings.tsv"));
            loadCustomMappings();
            loadAmbiguous(resolveData("pet_ambiguous.tsv"));
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private PetDefinitionRepository() {}

    static Def get(int itemId){ return BY_ITEM.get(itemId); }
    static boolean isMapped(int itemId){ return BY_ITEM.containsKey(itemId); }
    static boolean isAmbiguous(int itemId){ return AMBIGUOUS.containsKey(itemId); }
    static String ambiguousEvidence(int itemId){ return AMBIGUOUS.get(itemId); }
    static int count(){ return BY_ITEM.size(); }
    static int ambiguousCount(){ return AMBIGUOUS.size(); }
    static Collection<Def> all(){ return Collections.unmodifiableCollection(BY_ITEM.values()); }

    private static Path resolveData(String name) throws IOException {
        Path p=Paths.get("server","data",name);
        if(Files.isRegularFile(p)) return p;
        p=Paths.get("data",name);
        if(Files.isRegularFile(p)) return p;
        InputStream in=PetDefinitionRepository.class.getResourceAsStream("/spk/local/"+name);
        if(in!=null){
            Path tmp=Files.createTempFile("spk-"+name.replace('.','-'),".tmp");
            Files.copy(in,tmp,StandardCopyOption.REPLACE_EXISTING); in.close(); tmp.toFile().deleteOnExit(); return tmp;
        }
        throw new FileNotFoundException(name);
    }

    private static void loadMappings(Path p) throws IOException {
        try(BufferedReader r=Files.newBufferedReader(p,StandardCharsets.UTF_8)){
            String line=r.readLine(); // header
            while((line=r.readLine())!=null){
                if(line.isBlank()) continue;
                String[] a=line.split("\\t",-1);
                if(a.length<12) throw new IOException("bad pet mapping row: "+line);
                Def d=new Def(pi(a[0]),pi(a[1]),a[2],a[3],pi(a[4]),pi(a[5]),pi(a[6]),pi(a[7]),pi(a[8]),pi(a[9]),a[10],a[11]);
                if(BY_ITEM.put(d.itemId,d)!=null) throw new IOException("duplicate pet item "+d.itemId);
            }
        }
    }
    private static void loadCustomMappings() throws IOException {
        for (CustomAssetAuthoringRepository.Asset asset :
            CustomAssetAuthoringRepository.all()) {
            if (asset.kind != CustomAssetAuthoringRepository.Kind.PET) continue;

            Def d = new Def(
                asset.itemId,
                asset.npcId,
                asset.name,
                asset.name,
                asset.standAnim,
                asset.walkAnim,
                asset.walkAnim,
                asset.walkAnim,
                asset.walkAnim,
                asset.npcSize,
                String.valueOf(asset.modelId),
                asset.provenance
            );

            Def previous = BY_ITEM.put(d.itemId, d);
            if (previous != null) {
                BY_ITEM.put(previous.itemId, previous);
                throw new IOException(
                    "custom pet item collision: authored=" + d + " existing=" + previous
                );
            }
        }
    }

    private static void loadAmbiguous(Path p) throws IOException {
        try(BufferedReader r=Files.newBufferedReader(p,StandardCharsets.UTF_8)){
            String line=r.readLine();
            while((line=r.readLine())!=null){
                if(line.isBlank()) continue;
                String[] a=line.split("\\t",-1);
                if(a.length<5) throw new IOException("bad ambiguous pet row: "+line);
                AMBIGUOUS.put(pi(a[0]),"item="+a[0]+" name="+a[1]+" topScore="+a[2]+" candidates="+a[3]+" provenance="+a[4]);
            }
        }
    }
    private static int pi(String s){ return Integer.parseInt(s.trim()); }
}
