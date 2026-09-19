package spk.local;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Exact static interaction/UI contracts from NPC/WORLD/SHOP Research R1. */
final class ServiceResearchAuthority {
    static final int RECOVERY_MATRIX_ROWS=20,ROUTER_ROWS=16,SERVICE_KEY_ROWS=9,PRODUCTION_OBJECT_EXAMPLES=7;
    static final class Router{final String family,slot,opcode,role,notes;Router(String[]p){family=p[0];slot=p[1];opcode=p[2];role=p[3];notes=p[4];}public String toString(){return family+" option"+slot+" -> C2S"+opcode+" "+role;}}
    private static final ArrayList<Router> ROUTERS=loadRouters();
    static String routerSummary(){return "routers="+ROUTERS.size()+" npc=155/72/17/21/18 object=132/252/70/234/228 widgetItem=145/117/43/129/135/176";}
    static String bloodSummary(){return "S2C126 keys fountain=10/11/12 poolShop=37 fuser=13 enchant=38/71; UI/protocol exact, recipes/costs/catalog/server effects unresolved";}
    static String shopSummary(){return "shop root3824 framework exact; SHOP_TAB_STATE=S2C250 subtype17; widget item option5/6=Buy100/Buy1000; stock/prices/currency/restock server-owned";}
    static String summary(){return "matrix="+RECOVERY_MATRIX_ROWS+" routers="+ROUTER_ROWS+" serviceKeys="+SERVICE_KEY_ROWS+" prodObjectExamples="+PRODUCTION_OBJECT_EXAMPLES;}
    private static ArrayList<Router>loadRouters(){ArrayList<Router>a=new ArrayList<>();try(InputStream in=ServiceResearchAuthority.class.getResourceAsStream("/spk/local/data/research_r82/service_interaction_router_r82.tsv")){if(in==null)throw new IllegalStateException("missing service router");try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){br.readLine();String s;while((s=br.readLine())!=null){if(s.isEmpty())continue;String[]p=s.split("\t",-1);if(p.length<5)throw new IllegalStateException("bad router row");a.add(new Router(p));}}}catch(IOException e){throw new ExceptionInInitializerError(e);}return a;}
    private ServiceResearchAuthority(){}
}
