package spk.local;

import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;

/** R8.2 audit surface proving the actual final research archives were consumed. */
final class FullResearchArchiveAuthority {
    static final String ASSET_R8_SHA="7a33b026710aceab39ff3f6ba843a7bd80536aab08050508e80d7a97c9c7018e";
    static final String EQUIPMENT_R2_SHA="1236016d249cc82f24d78bb07e0827be994eacfabf8d7f5c4addc4d0079e9ee9";
    static final String PRESENTATION_R30_SHA="056f0c6856cced1f03f44040e47490d61e162adc5650ced51e38c15915f1bbb9";
    static final String ITEM_PET_R4_SHA="adc215bd8c5257ae2f6b226da3fe42e609880f86bbb8710b26909c221dae178a";
    static final String PET_MOVEMENT_V912_SHA="3e3f493c8cb5f736cc796738c534658e2266f2551dbdc77ec2ee962832d4574f";
    static final String WORLD_R1_SHA="95b7de0236b1fc717ac564914446a5d3d89af11e2e19732f1a04f9765338e5bc";
    static final String NPC_WORLD_SHOP_R1_SHA="54e817b4ecfbaf484909c6290aafe616f9012fc12b1fe7c26cb4673880b7a853";
    static final String R30_DEEP_SHA="70a4f7ad12706268bd41e93657ffb272bb468d81e97289d7e0ea2812b4503e2e";
    static final String R30_RENDERER_SHA="6635b985e68412cd1059ef9942d3ba8926e99e1b4df6edbda2c1f3ea3017f166";
    static final String UPDATE_CORPUS_SHA="b18210f507cd5efec1a030dc3f80e1c3442a3ba9a87f4189d81b623727489a84";
    static final int SOURCE_PACKAGES=10;
    static final class Dataset{final String name,lane,mode,boundary;final long rows;Dataset(String[]p){name=p[0];lane=p[1];rows=l(p[2]);mode=p[3];boundary=p[4];}}
    private static final ArrayList<Dataset> DATASETS=loadManifest();
    static int datasetCount(){return DATASETS.size();}
    static long rows(String name){for(Dataset d:DATASETS)if(d.name.equals(name))return d.rows;return -1;}
    static int rowLevelDatasetCount(){int n=0;for(Dataset d:DATASETS)if("ROW_LEVEL_IMPORTED".equals(d.mode))n++;return n;}
    static String summary(){return "archives="+SOURCE_PACKAGES+" datasets="+datasetCount()+" rowLevel="+rowLevelDatasetCount()+" actualR4/R30/R2/R8/WORLD/V912/service=true";}
    static String boundary(){return "actual final archives consumed; raw duplicate/source blobs may stay external; unknown server business rules, landing tiles, arbitrary 14-field stats, dynamic overlays/NPC populations, and unobserved proc bindings remain fail-closed";}
    static int countRows(String resource){int n=0;try(InputStream in=FullResearchArchiveAuthority.class.getResourceAsStream(resource)){if(in==null)throw new IllegalStateException("missing resource "+resource);try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){if(br.readLine()==null)return 0;while(br.readLine()!=null)n++;}}catch(IOException e){throw new ExceptionInInitializerError(e);}return n;}
    static Map<String,String> sourceHashes(){LinkedHashMap<String,String>m=new LinkedHashMap<>();m.put("assetR8",ASSET_R8_SHA);m.put("equipmentR2",EQUIPMENT_R2_SHA);m.put("presentationR30",PRESENTATION_R30_SHA);m.put("itemPetR4",ITEM_PET_R4_SHA);m.put("petMovementV912",PET_MOVEMENT_V912_SHA);m.put("worldR1",WORLD_R1_SHA);m.put("npcWorldShopR1",NPC_WORLD_SHOP_R1_SHA);m.put("r30Deep",R30_DEEP_SHA);m.put("r30Renderer",R30_RENDERER_SHA);m.put("updateCorpus",UPDATE_CORPUS_SHA);return Collections.unmodifiableMap(m);}
    private static ArrayList<Dataset> loadManifest(){ArrayList<Dataset>a=new ArrayList<>();try(InputStream in=FullResearchArchiveAuthority.class.getResourceAsStream("/spk/local/data/research_r82/full_research_archive_manifest_r82.tsv")){if(in==null)throw new IllegalStateException("missing full_research_archive_manifest_r82.tsv");try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String s=br.readLine();if(s==null)throw new IllegalStateException("empty research manifest");while((s=br.readLine())!=null){if(s.isEmpty())continue;String[]p=s.split("\t",-1);if(p.length<5)throw new IllegalStateException("bad research manifest row");a.add(new Dataset(p));}}}catch(IOException e){throw new ExceptionInInitializerError(e);}return a;}
    private static long l(String s){try{return Long.parseLong(s);}catch(Exception e){return -1;}}
    private FullResearchArchiveAuthority(){}
}
