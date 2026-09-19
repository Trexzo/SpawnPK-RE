package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Canonical non-pet HOME NPC placements reconstructed from the passive V9.06
 * area survey. Production scene indexes are retained only as provenance; the
 * LocalLab registry must allocate its own scene indexes.
 */
final class HomeNpcSpawnRepository {
    static final class Spawn {
        final int ordinal,npcDefinitionId,plane,anchorX,anchorY,minX,maxX,minY,maxY,presentationImgId,uniqueTiles,samples,duplicateObservedActors;
        final String name,classification,productionSceneIndexes,lifecycleScope,provenance;
        final boolean defaultReplay;

        Spawn(int ordinal,int npcDefinitionId,String name,int plane,int anchorX,int anchorY,
              int minX,int maxX,int minY,int maxY,String classification,int presentationImgId,
              int uniqueTiles,int samples,int duplicateObservedActors,String productionSceneIndexes,
              String lifecycleScope,boolean defaultReplay,String provenance) {
            this.ordinal=ordinal; this.npcDefinitionId=npcDefinitionId; this.name=name; this.plane=plane;
            this.anchorX=anchorX; this.anchorY=anchorY; this.minX=minX; this.maxX=maxX; this.minY=minY; this.maxY=maxY;
            this.classification=classification; this.presentationImgId=presentationImgId;
            this.uniqueTiles=uniqueTiles; this.samples=samples; this.duplicateObservedActors=duplicateObservedActors;
            this.productionSceneIndexes=productionSceneIndexes; this.lifecycleScope=lifecycleScope;
            this.defaultReplay=defaultReplay; this.provenance=provenance;
        }

        boolean staticObserved(){ return "STATIC".equals(classification); }
        boolean localWanderObserved(){ return "LOCAL_WANDER".equals(classification); }
        boolean withinSigned5SpawnRange(int playerX,int playerY){
            int dx=anchorX-playerX, dy=anchorY-playerY;
            return dx>=-16 && dx<=15 && dy>=-16 && dy<=15;
        }
        boolean containsObservedTile(int x,int y){ return x>=minX&&x<=maxX&&y>=minY&&y<=maxY; }

        @Override public String toString(){
            return "HomeNpcSpawn{def="+npcDefinitionId+",name="+name+",anchor="+anchorX+","+anchorY+
                ",box="+minX+".."+maxX+","+minY+".."+maxY+",class="+classification+
                ",defaultReplay="+defaultReplay+",prodIdx="+productionSceneIndexes+"}";
        }
    }

    private static final List<Spawn> ALL;
    private static final Map<Integer,List<Spawn>> BY_DEFINITION;
    static {
        try {
            ArrayList<Spawn> all=load(resolveData("home_npc_spawns_v906.tsv"));
            ALL=Collections.unmodifiableList(all);
            LinkedHashMap<Integer,List<Spawn>> m=new LinkedHashMap<>();
            for(Spawn s:all) m.computeIfAbsent(s.npcDefinitionId,k->new ArrayList<>()).add(s);
            for(Map.Entry<Integer,List<Spawn>> e:m.entrySet()) e.setValue(Collections.unmodifiableList(e.getValue()));
            BY_DEFINITION=Collections.unmodifiableMap(m);
        } catch(IOException e){ throw new ExceptionInInitializerError(e); }
    }
    private HomeNpcSpawnRepository(){}

    static List<Spawn> all(){ return ALL; }
    static int count(){ return ALL.size(); }
    static Spawn byOrdinal(int ordinal){ for(Spawn s:ALL) if(s.ordinal==ordinal) return s; return null; }
    static List<Spawn> byDefinition(int id){ return BY_DEFINITION.getOrDefault(id,Collections.emptyList()); }

    /** Default persistent HOME replay set; time/event-scoped observations are excluded. */
    static List<Spawn> defaultReplay(){
        ArrayList<Spawn> out=new ArrayList<>();
        for(Spawn s:ALL) if(s.defaultReplay) out.add(s);
        return Collections.unmodifiableList(out);
    }

    /**
     * NPC packet 65 initial-add offsets are signed 5-bit. This helper therefore
     * returns only canonical spawns representable relative to the current player.
     */
    static List<Spawn> nearbyDefaultReplay(int playerX,int playerY){
        ArrayList<Spawn> out=new ArrayList<>();
        for(Spawn s:ALL) if(s.defaultReplay && s.withinSigned5SpawnRange(playerX,playerY)) out.add(s);
        return out;
    }

    private static ArrayList<Spawn> load(Path p)throws IOException{
        ArrayList<Spawn> out=new ArrayList<>();
        HashSet<Integer> ordinals=new HashSet<>();
        try(BufferedReader r=Files.newBufferedReader(p,StandardCharsets.UTF_8)){
            String header=r.readLine();
            if(header==null || !header.startsWith("ordinal\t")) throw new IOException("bad HOME NPC header");
            String line;
            while((line=r.readLine())!=null){
                if(line.trim().isEmpty()) continue;
                String[] a=line.split("\\t",-1);
                if(a.length<19) throw new IOException("bad HOME NPC row: "+line);
                Spawn s=new Spawn(pi(a[0]),pi(a[1]),a[2],pi(a[3]),pi(a[4]),pi(a[5]),pi(a[6]),pi(a[7]),pi(a[8]),pi(a[9]),
                    a[10],pi(a[11]),pi(a[12]),pi(a[13]),pi(a[14]),a[15],a[16],Boolean.parseBoolean(a[17]),a[18]);
                if(!ordinals.add(s.ordinal)) throw new IOException("duplicate HOME NPC ordinal "+s.ordinal);
                out.add(s);
            }
        }
        return out;
    }

    private static Path resolveData(String name)throws IOException{
        Path p=Paths.get("server","data",name);
        if(Files.isRegularFile(p)) return p;
        p=Paths.get("data",name);
        if(Files.isRegularFile(p)) return p;
        InputStream in=HomeNpcSpawnRepository.class.getResourceAsStream("/spk/local/"+name);
        if(in!=null){
            Path tmp=Files.createTempFile("spk-"+name.replace('.','-'),".tmp");
            Files.copy(in,tmp,StandardCopyOption.REPLACE_EXISTING); in.close(); tmp.toFile().deleteOnExit(); return tmp;
        }
        throw new FileNotFoundException(name);
    }
    private static int pi(String s){ return Integer.parseInt(s.trim()); }
}
