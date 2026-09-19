package spk.local;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Actual V9.12 production observation summary; not a guessed follow policy. */
final class PetMovementResearchAuthority {
    private static final LinkedHashMap<String,String> M=load();
    static String get(String k){String v=M.get(k);return v==null?"":v;}
    static int samples(){return i(get("samples"));}static int movementEvents(){return i(get("actor_movement_events"));}static int reanchorCandidates(){return i(get("candidate_reanchors"));}
    static String summary(){return "V9.12 samples="+samples()+" movementEvents="+movementEvents()+" chain="+get("relationship_authority")+" spacing="+get("settled_spacing")+" reanchor="+get("reanchor_threshold");}
    private static LinkedHashMap<String,String>load(){LinkedHashMap<String,String>m=new LinkedHashMap<>();try(InputStream in=PetMovementResearchAuthority.class.getResourceAsStream("/spk/local/data/research_r82/pet_movement_production_summary_r82.tsv")){if(in==null)throw new IllegalStateException("missing pet movement summary");try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){br.readLine();String s;while((s=br.readLine())!=null){String[]p=s.split("\t",2);if(p.length==2)m.put(p[0],p[1]);}}}catch(IOException e){throw new ExceptionInInitializerError(e);}return m;}
    private static int i(String s){try{return Integer.parseInt(s);}catch(Exception e){return -1;}}
    private PetMovementResearchAuthority(){}
}
