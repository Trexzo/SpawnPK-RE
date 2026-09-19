package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Data-only world/region authority. It does not teleport players or replace the
 * certified HOME scene. Arrival tiles and dynamic server overlays remain
 * evidence-gated. */
final class WorldRegionAuthorityRepository {
    static final class Region {
        final int regionId,regionX,regionY,x0,y0,x1,y1,mapArchive,landArchive,placements,travelPlacements;
        final String name,group,classification,evidence,usageStatus,usageRationale,provenance;
        final boolean mapPresent,landPresent,terrainParseOk,objectParseOk,osrs;
        Region(String[] p){
            regionId=i(p[0]);regionX=i(p[1]);regionY=i(p[2]);x0=i(p[3]);y0=i(p[4]);x1=i(p[5]);y1=i(p[6]);
            name=u(p[7]);group=u(p[8]);classification=u(p[9]);evidence=u(p[10]);mapPresent=b(p[11]);landPresent=b(p[12]);
            terrainParseOk=b(p[13]);objectParseOk=b(p[14]);placements=i(p[15]);travelPlacements=i(p[16]);
            usageStatus=u(p[17]);usageRationale=u(p[18]);mapArchive=i(p[19]);landArchive=i(p[20]);osrs=b(p[21]);provenance=u(p[22]);
        }
        boolean contains(int x,int y){return x>=x0&&x<=x1&&y>=y0&&y<=y1;}
        boolean custom(){return !empty(name)||!empty(group)||classification.contains("SPAWNPK_STATIC_EVIDENCE");}
        public String toString(){return "Region{"+regionId+" "+x0+","+y0+".."+x1+","+y1+" name="+name+" group="+group+" usage="+usageStatus+"}";}
    }
    private static final LinkedHashMap<Integer,Region> BY_ID=load();
    static int count(){return BY_ID.size();}
    static Region get(int id){return BY_ID.get(id);}
    static Region forTile(int x,int y){return BY_ID.get(((x>>6)<<8)|(y>>6));}
    static int productionConfirmedCount(){int n=0;for(Region r:BY_ID.values())if("PRODUCTION_CONFIRMED".equals(r.usageStatus))n++;return n;}
    static int explicitCustomCount(){int n=0;for(Region r:BY_ID.values())if(r.custom())n++;return n;}
    static int fullyDecodedCount(){int n=0;for(Region r:BY_ID.values())if(r.mapPresent&&r.landPresent&&r.terrainParseOk&&r.objectParseOk)n++;return n;}
    private static LinkedHashMap<Integer,Region> load(){
        LinkedHashMap<Integer,Region> out=new LinkedHashMap<>();
        try(InputStream in=WorldRegionAuthorityRepository.class.getResourceAsStream("/spk/local/data/world_region_authority_r5.tsv")){
            if(in==null)throw new IllegalStateException("missing world_region_authority_r5.tsv");
            try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
                String h=br.readLine();if(h==null||!h.startsWith("region_id\t"))throw new IllegalStateException("bad world authority header");
                for(String line;(line=br.readLine())!=null;){String[] p=line.split("\t",-1);if(p.length<23)throw new IllegalStateException("bad world row fields="+p.length);Region r=new Region(p);if(out.put(r.regionId,r)!=null)throw new IllegalStateException("duplicate effective region id="+r.regionId);}
            }
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
        return out;
    }
    private static int i(String s){try{return s==null||s.isEmpty()?-1:Integer.parseInt(s);}catch(Exception e){return -1;}}
    private static boolean b(String s){return "true".equalsIgnoreCase(s)||"1".equals(s);}
    private static boolean empty(String s){return s==null||s.isEmpty();}
    private static String u(String s){StringBuilder b=new StringBuilder();boolean slash=false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(slash){if(c=='n')b.append('\n');else if(c=='t')b.append('\t');else b.append(c);slash=false;}else if(c=='\\')slash=true;else b.append(c);}if(slash)b.append('\\');return b.toString();}
    private WorldRegionAuthorityRepository(){}
}
