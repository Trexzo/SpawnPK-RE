package spk.local;

import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;

/** R8 read-only WORLD R1 teleport/transition graph. Exact landing tiles remain server authority. */
final class WorldTransitionResearchAuthority {
    static final class TeleportItem{
        final int itemId,candidateCount;final String name,actions,hover,source,candidates;
        TeleportItem(String[]p){itemId=i(p[0]);name=u(p[1]);actions=u(p[2]);hover=u(p[3]);source=p[4];candidateCount=i(p[5]);candidates=u(p[6]);}
        public String toString(){return itemId+":"+name+" actions="+actions+" candidates="+(candidates.isEmpty()?"none":candidates)+" landingTile=UNKNOWN_SERVER_AUTHORITY";}
    }
    static final class Transition{
        final String sourceKind,sourceLabel,action,destinationKind,destinationRegionIds,destinationGroup,confidence,evidence;final int sourceId,sourceRegionId,x,y,plane;
        Transition(String[]p){sourceKind=p[0];sourceId=i(p[1]);sourceLabel=u(p[2]);sourceRegionId=i(p[3]);x=i(p[4]);y=i(p[5]);plane=i(p[6]);action=u(p[7]);destinationKind=p[8];destinationRegionIds=p[9];destinationGroup=p[10];confidence=p[11];evidence=u(p[12]);}
        public String toString(){return sourceKind+" "+sourceId+" "+sourceLabel+" @"+x+","+y+","+plane+" action="+action+" dest="+destinationKind+" regions="+(destinationRegionIds.isEmpty()?"unknown":destinationRegionIds)+" group="+(destinationGroup.isEmpty()?"none":destinationGroup)+" confidence="+confidence;}
    }
    private static final LinkedHashMap<Integer,TeleportItem> TELE=loadTele();
    private static final ArrayList<Transition> TRANS=loadTrans();
    static int teleportCount(){return TELE.size();}static int transitionCount(){return TRANS.size();}
    static TeleportItem teleport(int itemId){return TELE.get(itemId);}static List<Transition> transitionsForRegion(int regionId){ArrayList<Transition>o=new ArrayList<>();for(Transition t:TRANS)if(t.sourceRegionId==regionId)o.add(t);return o;}
    static String teleportSummary(int itemId){TeleportItem t=teleport(itemId);return t==null?"teleportItem="+itemId+" none":"teleportItem={"+t+"}";}
    static String regionSummary(int regionId){List<Transition>x=transitionsForRegion(regionId);if(x.isEmpty())return "region="+regionId+" transitionCandidates=0";return "region="+regionId+" transitionCandidates="+x.size()+" first={"+x.get(0)+"}";}
    static int teleportItemsWithRegionCandidates(){int n=0;for(TeleportItem t:TELE.values())if(t.candidateCount>0)n++;return n;}
    private static LinkedHashMap<Integer,TeleportItem> loadTele(){LinkedHashMap<Integer,TeleportItem>m=new LinkedHashMap<>();try(InputStream in=WorldTransitionResearchAuthority.class.getResourceAsStream("/spk/local/data/teleport_item_authority_r8.tsv")){if(in==null)throw new IllegalStateException("missing teleport_item_authority_r8.tsv");try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String s;boolean first=true;while((s=br.readLine())!=null){if(first){first=false;continue;}if(s.isEmpty())continue;String[]p=s.split("\\t",-1);if(p.length<7)throw new IllegalStateException("bad teleport row");TeleportItem t=new TeleportItem(p);m.put(t.itemId,t);}}}catch(IOException e){throw new ExceptionInInitializerError(e);}return m;}
    private static ArrayList<Transition> loadTrans(){ArrayList<Transition>a=new ArrayList<>();try(InputStream in=WorldTransitionResearchAuthority.class.getResourceAsStream("/spk/local/data/world_transition_authority_r8.tsv")){if(in==null)throw new IllegalStateException("missing world_transition_authority_r8.tsv");try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String s;boolean first=true;while((s=br.readLine())!=null){if(first){first=false;continue;}if(s.isEmpty())continue;String[]p=s.split("\\t",-1);if(p.length<13)throw new IllegalStateException("bad transition row fields="+p.length);a.add(new Transition(p));}}}catch(IOException e){throw new ExceptionInInitializerError(e);}return a;}
    private static int i(String s){try{return s==null||s.isEmpty()?-1:Integer.parseInt(s);}catch(Exception e){return -1;}}
    private static String u(String s){return s==null?"":s.replace("\\n","\n").replace("\\t","\t");}
    private WorldTransitionResearchAuthority(){}
}
