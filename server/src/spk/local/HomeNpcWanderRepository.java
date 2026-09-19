package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * V9.06-observed HOME NPC wander graph.
 *
 * Every edge joins two adjacent tiles that production actually traversed during
 * the passive area survey. The graph is made bidirectional only so a replay
 * cannot become trapped at the final edge of the short observation window;
 * the allowed tile set and adjacency remain evidence-derived.
 */
final class HomeNpcWanderRepository {
    static final class Edge {
        final int ordinal,npcDefinitionId,cadenceTicks,fromX,fromY,toX,toY,weight;
        final String name,provenance;
        Edge(int ordinal,int npcDefinitionId,String name,int cadenceTicks,int fromX,int fromY,int toX,int toY,int weight,String provenance){
            this.ordinal=ordinal; this.npcDefinitionId=npcDefinitionId; this.name=name; this.cadenceTicks=cadenceTicks;
            this.fromX=fromX; this.fromY=fromY; this.toX=toX; this.toY=toY; this.weight=weight; this.provenance=provenance;
        }
        @Override public String toString(){return "Edge{ord="+ordinal+",def="+npcDefinitionId+","+fromX+","+fromY+"->"+toX+","+toY+",w="+weight+",cad="+cadenceTicks+"}";}
    }

    private static final List<Edge> ALL;
    private static final Map<Integer,Integer> CADENCE;
    private static final Map<String,List<Edge>> BY_FROM;
    static {
        try {
            ArrayList<Edge> all=load(resolveData("home_npc_wander_edges_v906.tsv"));
            ALL=Collections.unmodifiableList(all);
            LinkedHashMap<Integer,Integer> cadence=new LinkedHashMap<>();
            LinkedHashMap<String,List<Edge>> byFrom=new LinkedHashMap<>();
            for(Edge e:all){
                Integer old=cadence.putIfAbsent(e.ordinal,e.cadenceTicks);
                if(old!=null && old.intValue()!=e.cadenceTicks) throw new IOException("mixed cadence for ordinal "+e.ordinal);
                byFrom.computeIfAbsent(key(e.ordinal,e.fromX,e.fromY),k->new ArrayList<>()).add(e);
            }
            for(List<Edge> list:byFrom.values()) list.sort(Comparator.comparingInt((Edge e)->e.toX).thenComparingInt(e->e.toY));
            for(Map.Entry<String,List<Edge>> e:byFrom.entrySet()) e.setValue(Collections.unmodifiableList(e.getValue()));
            CADENCE=Collections.unmodifiableMap(cadence);
            BY_FROM=Collections.unmodifiableMap(byFrom);
        } catch(IOException e){ throw new ExceptionInInitializerError(e); }
    }
    private HomeNpcWanderRepository(){}

    static List<Edge> all(){return ALL;}
    static int edgeCount(){return ALL.size();}
    static Set<Integer> wandererOrdinals(){return CADENCE.keySet();}
    static int cadenceTicks(int ordinal){return CADENCE.getOrDefault(ordinal,Integer.MAX_VALUE);}
    static List<Edge> outgoing(int ordinal,int x,int y){return BY_FROM.getOrDefault(key(ordinal,x,y),Collections.emptyList());}

    private static String key(int ordinal,int x,int y){return ordinal+":"+x+":"+y;}

    private static ArrayList<Edge> load(Path p)throws IOException{
        ArrayList<Edge> out=new ArrayList<>();
        try(BufferedReader r=Files.newBufferedReader(p,StandardCharsets.UTF_8)){
            String header=r.readLine();
            if(header==null || !header.startsWith("ordinal\t")) throw new IOException("bad HOME wander header");
            String line;
            while((line=r.readLine())!=null){
                if(line.trim().isEmpty()) continue;
                String[] a=line.split("\t",-1);
                if(a.length<10) throw new IOException("bad HOME wander row: "+line);
                Edge e=new Edge(pi(a[0]),pi(a[1]),a[2],pi(a[3]),pi(a[4]),pi(a[5]),pi(a[6]),pi(a[7]),pi(a[8]),a[9]);
                if(e.cadenceTicks<1 || e.cadenceTicks>6) throw new IOException("bad cadence "+e);
                if(e.weight<1) throw new IOException("bad weight "+e);
                if(Math.max(Math.abs(e.toX-e.fromX),Math.abs(e.toY-e.fromY))!=1) throw new IOException("non-adjacent wander edge "+e);
                HomeNpcSpawnRepository.Spawn s=findSpawn(e.ordinal);
                if(s==null || s.npcDefinitionId!=e.npcDefinitionId) throw new IOException("wander edge spawn mismatch "+e);
                if(!s.containsObservedTile(e.fromX,e.fromY) || !s.containsObservedTile(e.toX,e.toY)) throw new IOException("wander edge outside observed envelope "+e+" spawn="+s);
                out.add(e);
            }
        }
        return out;
    }

    private static HomeNpcSpawnRepository.Spawn findSpawn(int ordinal){
        for(HomeNpcSpawnRepository.Spawn s:HomeNpcSpawnRepository.all()) if(s.ordinal==ordinal) return s;
        return null;
    }

    private static Path resolveData(String name)throws IOException{
        Path p=Paths.get("server","data",name);
        if(Files.isRegularFile(p)) return p;
        p=Paths.get("data",name);
        if(Files.isRegularFile(p)) return p;
        InputStream in=HomeNpcWanderRepository.class.getResourceAsStream("/spk/local/"+name);
        if(in!=null){
            Path tmp=Files.createTempFile("spk-"+name.replace('.','-'),".tmp");
            Files.copy(in,tmp,StandardCopyOption.REPLACE_EXISTING); in.close(); tmp.toFile().deleteOnExit(); return tmp;
        }
        throw new FileNotFoundException(name);
    }
    private static int pi(String s){return Integer.parseInt(s.trim());}
}
