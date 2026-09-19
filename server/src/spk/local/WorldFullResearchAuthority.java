package spk.local;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Detailed WORLD R1 metadata beyond the active region/collision repositories. */
final class WorldFullResearchAuthority {
    static final int EFFECTIVE_UNIQUE_REGIONS=1279,FINAL_REGION_ROWS=1283,STATIC_PLACEMENTS=2162982,COLLIDABLE_PLACEMENTS=1316740;
    static final int ADJACENCY_ROWS=2002,COMPONENTS=72,UNRESOLVED_GAPS=54,NAMED_CUSTOM_REGIONS=68,CUSTOM_GROUPS=46;
    static final class Usage{final int regionId;final String name,group,classification,evidence,status,rationale;Usage(String[]p){regionId=i(p[0]);name=u(p[5]);group=u(p[6]);classification=u(p[7]);evidence=u(p[8]);status=u(p[15]);rationale=u(p[16]);}}
    private static final HashMap<Integer,Usage> USAGE=loadUsage();
    static Usage usage(int regionId){return USAGE.get(regionId);}
    static String regionSummary(int regionId){Usage u=usage(regionId);return u==null?"region="+regionId+" worldR1=unknown":"region="+regionId+" usage="+u.status+" class="+u.classification+" name="+n(u.name)+" group="+n(u.group)+" rationale="+u.rationale;}
    static String summary(){return "regions="+EFFECTIVE_UNIQUE_REGIONS+" adjacency="+ADJACENCY_ROWS+" components="+COMPONENTS+" staticObjects="+STATIC_PLACEMENTS+" collidable="+COLLIDABLE_PLACEMENTS+" unresolved="+UNRESOLVED_GAPS+" customRegions="+NAMED_CUSTOM_REGIONS+" groups="+CUSTOM_GROUPS;}
    private static HashMap<Integer,Usage>loadUsage(){HashMap<Integer,Usage>m=new HashMap<>();try(InputStream in=WorldFullResearchAuthority.class.getResourceAsStream("/spk/local/data/research_r82/world_usage_classification_r82.tsv")){if(in==null)throw new IllegalStateException("missing world usage");try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){br.readLine();String s;while((s=br.readLine())!=null){if(s.isEmpty())continue;String[]p=s.split("\t",-1);if(p.length<17)throw new IllegalStateException("bad world usage row fields="+p.length);Usage u=new Usage(p);m.put(u.regionId,u);}}}catch(IOException e){throw new ExceptionInInitializerError(e);}return m;}
    private static int i(String s){try{return Integer.parseInt(s);}catch(Exception e){return -1;}}private static String n(String s){return s==null||s.isEmpty()?"none":s;}private static String u(String s){return s==null?"":s.replace("\\n","\n").replace("\\t","\t");}
    private WorldFullResearchAuthority(){}
}
