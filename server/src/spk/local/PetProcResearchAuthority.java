package spk.local;

import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.regex.*;

/** R8.2 actual Item+Pet R4 tuple closure. Static candidates remain candidates. */
final class PetProcResearchAuthority {
    static final class Row{
        final int index;final String itemIds,family,npcIds,procSemantics,procTrigger,petBodyAnimation,startOrActorGfx,projectileOrTravelGfx,impactGfx,targetStatusVisual,ownerGfx,clientNativeControl,sound,confidence,bindingStatus,minimalRuntimeNeeded,notes;
        final Set<Integer> parsedItemIds;
        Row(int index,String[]p){this.index=index;itemIds=u(p[0]);family=u(p[1]);npcIds=u(p[2]);procSemantics=u(p[5]);procTrigger=u(p[6]);petBodyAnimation=u(p[7]);startOrActorGfx=u(p[8]);projectileOrTravelGfx=u(p[9]);impactGfx=u(p[10]);targetStatusVisual=u(p[11]);ownerGfx=u(p[12]);clientNativeControl=u(p[13]);sound=u(p[14]);confidence=u(p[15]);bindingStatus=u(p[16]);minimalRuntimeNeeded=u(p[17]);notes=u(p[18]);parsedItemIds=parseIds(itemIds);}
        int firstAnimation(){return firstNumeric(petBodyAnimation);}int firstGfx(){return firstNumeric(startOrActorGfx);}
        boolean previewable(){return firstAnimation()>=0||firstGfx()>=0;}
        boolean runtimeProven(){return confidence.contains("RUNTIME")||bindingStatus.contains("SOLVED")||confidence.contains("EXACT_CLIENT");}
        public String toString(){return "#"+index+" "+family+" items="+itemIds+" anim="+n(petBodyAnimation)+" gfx="+n(startOrActorGfx)+" proj="+n(projectileOrTravelGfx)+" impact="+n(impactGfx)+" native="+n(clientNativeControl)+" confidence="+confidence+" binding="+bindingStatus;}
    }
    private static final ArrayList<Row> ROWS=load();
    static int count(){return ROWS.size();}static Row byIndex(int oneBased){return oneBased<1||oneBased>ROWS.size()?null:ROWS.get(oneBased-1);}static List<Row> all(){return Collections.unmodifiableList(ROWS);}
    static int componentCount(){return FullResearchArchiveAuthority.countRows("/spk/local/data/research_r82/pet_proc_component_evidence_r82.tsv");}
    static int animationTimingCount(){return FullResearchArchiveAuthority.countRows("/spk/local/data/research_r82/pet_animation_timing_exact_r82.tsv");}
    static int gfxTimingCount(){return FullResearchArchiveAuthority.countRows("/spk/local/data/research_r82/pet_gfx_timing_exact_r82.tsv");}
    static int variantFamilyCount(){return FullResearchArchiveAuthority.countRows("/spk/local/data/research_r82/pet_variant_families_r82.tsv");}
    static int specialRendererCount(){return FullResearchArchiveAuthority.countRows("/spk/local/data/research_r82/pet_special_renderer_authority_r82.tsv");}
    static int remainingRuntimeCount(){return FullResearchArchiveAuthority.countRows("/spk/local/data/research_r82/pet_runtime_remaining_tests_r82.tsv");}
    static int runtimeProvenCount(){int n=0;for(Row r:ROWS)if(r.runtimeProven())n++;return n;}
    static Row findForPetItem(int itemId){for(Row r:ROWS)if(r.parsedItemIds.contains(itemId))return r;return null;}
    static String summary(Row r){return r==null?"petProc=none | tuples=23 components=47 animTiming=101 gfxTiming=55":""+r;}
    static String corpusSummary(){return "tuples="+count()+" components="+componentCount()+" animTiming="+animationTimingCount()+" gfxTiming="+gfxTimingCount()+" variants="+variantFamilyCount()+" renderers="+specialRendererCount()+" runtimeRemaining="+remainingRuntimeCount();}
    private static ArrayList<Row> load(){ArrayList<Row>a=new ArrayList<>();try(InputStream in=PetProcResearchAuthority.class.getResourceAsStream("/spk/local/data/research_r82/pet_proc_tuple_closure_r82.tsv")){if(in==null)throw new IllegalStateException("missing actual R4 pet tuple closure");try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String s=br.readLine();int idx=0;while((s=br.readLine())!=null){if(s.isEmpty())continue;String[]p=s.split("\t",-1);if(p.length<19)throw new IllegalStateException("bad R4 pet tuple row fields="+p.length);a.add(new Row(++idx,p));}}}catch(IOException e){throw new ExceptionInInitializerError(e);}return a;}
    private static Set<Integer>parseIds(String s){LinkedHashSet<Integer>o=new LinkedHashSet<>();Matcher m=Pattern.compile("\\d+").matcher(s==null?"":s);while(m.find())try{o.add(Integer.parseInt(m.group()));}catch(Exception ignored){}return o;}
    private static int firstNumeric(String s){Matcher m=Pattern.compile("(?<!\\d)(\\d{2,6})(?!\\d)").matcher(s==null?"":s);return m.find()?Integer.parseInt(m.group(1)):-1;}
    private static String n(String s){return s==null||s.isEmpty()?"none":s;}private static String u(String s){return s==null?"":s.replace("\\n","\n").replace("\\t","\t").replace("\\\\","\\");}
    private PetProcResearchAuthority(){}
}
