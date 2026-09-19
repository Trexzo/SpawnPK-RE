package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** R8.3 exact-current client discovery authority. Read-only: no server-owned mechanics are inferred. */
final class ClientDiscoveryAuthority {
    static final String SOURCE_R2_SHA256="9150bbb90b2d73cfba32e6afd709ea0f01d9b4a75309a7fcaabecb000cee2ba6";
    static final String STATIC_R1_SHA256="cd6c456e04d9a811237ad9ed71c70f4a7c09dbf0a489f3b6dfaed12fd6e68e91";

    static final String ROOT="/spk/local/data/research_r83/";
    static final String EFFECTS=ROOT+"01_TIMED_POPUP_EFFECT_CATALOG.csv";
    static final String SPELLS=ROOT+"02_STANDARD_SPELL_FILTER_CATALOG.csv";
    static final String ROOMS=ROOT+"03_CONSTRUCTION_ROOM_CATALOG.csv";
    static final String PLANKS=ROOT+"04_CONSTRUCTION_PLANK_DISPLAY_PRICES.csv";
    static final String ACHIEVEMENTS=ROOT+"05_ACHIEVEMENT_CHAPTER_BOOTSTRAP.csv";
    static final String TASKS=ROOT+"06_TIMED_TASK_RULES.csv";
    static final String CUSTOM_MAGIC=ROOT+"07_CUSTOM_MAGIC_BUILDERS.csv";
    static final String SETTINGS=ROOT+"08_CLIENT_SETTINGS_SURFACE.csv";
    static final String COMMANDS=ROOT+"09_CLIENT_COMMAND_LITERAL_CENSUS.csv";
    static final String FEATURES=ROOT+"10_UI_FEATURE_ATLAS.csv";
    static final String CLAN_WARS=ROOT+"11_CLAN_WARS_STATIC_OPTIONS.csv";
    static final String CONTROLS=ROOT+"18_CONTROL_TOKEN_BEHAVIOR_MAP.csv";

    static final int TIMED_EFFECTS=count(EFFECTS), STANDARD_SPELLS=count(SPELLS), CONSTRUCTION_ROOMS=count(ROOMS),
        PLANK_DISPLAY_PRICES=count(PLANKS), ACHIEVEMENT_BOOTSTRAP=count(ACHIEVEMENTS), TIMED_TASK_RULES=count(TASKS),
        CUSTOM_MAGIC_BUILDERS=count(CUSTOM_MAGIC), CLIENT_SETTINGS=count(SETTINGS), COMMAND_LITERALS=count(COMMANDS),
        UI_FEATURES=count(FEATURES), CLAN_WARS_OPTIONS=count(CLAN_WARS), CONTROL_TOKENS=count(CONTROLS);

    static String summary(){
        return "R8.3 effects="+TIMED_EFFECTS+" spells="+STANDARD_SPELLS+" rooms="+CONSTRUCTION_ROOMS+
            " achievements="+ACHIEVEMENT_BOOTSTRAP+" tasks="+TIMED_TASK_RULES+" controls="+CONTROL_TOKENS+
            " ui="+UI_FEATURES;
    }
    static String taskAchievementSummary(){
        String mh=findByFirst(TASKS,"Monster hunter"), bh=findByFirst(TASKS,"Bounty hunter"), a=findByFirst(ACHIEVEMENTS,"0");
        return "tasks="+TIMED_TASK_RULES+" achievements="+ACHIEVEMENT_BOOTSTRAP+
            " monsterHunter={"+select(mh,0,2,3,4)+"} bountyHunter={"+select(bh,0,2,3,4,6)+"} chapter0={"+select(a,1,2,3,5,7)+"}";
    }
    static String magicConstructionSummary(){
        String air=findByFirst(SPELLS,"0"), miasmic=findByFirst(CUSTOM_MAGIC,"Miasmic barrage"), treasure=findByFirst(ROOMS,"22");
        return "spellFilter="+STANDARD_SPELLS+" customMagic="+CUSTOM_MAGIC_BUILDERS+" rooms="+CONSTRUCTION_ROOMS+
            " planks="+PLANK_DISPLAY_PRICES+" airStrike={"+select(air,1,2,3,4)+"} miasmic={"+select(miasmic,0,1,2,3)+"} treasure={"+select(treasure,1,2,4)+"}";
    }
    static String uiControlSummary(){
        String construction=findByFirst(CONTROLS,"CONSTRUCTION_BUILD_ON/OFF");
        String hg=findByFirst(FEATURES,"rs/l/e/a/o.class");
        return "controls="+CONTROL_TOKENS+" settings="+CLIENT_SETTINGS+" commands="+COMMAND_LITERALS+" features="+UI_FEATURES+
            " construction={"+select(construction,0,1,2)+"} hungerGames={"+select(hg,1,2,3)+"}";
    }
    static String minigameSummary(){return "clanWarsOptions="+CLAN_WARS_OPTIONS+" uiFeatureContracts="+UI_FEATURES+" server-selected match content/formulas remain external";}
    static String boundary(){
        return "exact current-client UI/data/control authority only; do not infer production rewards, assignment RNG, shop stock/prices, teleport destinations, encounter formulas, arbitrary equipment vectors, or other server business rules";
    }

    static String findByFirst(String resource,String key){
        for(String[] r:rows(resource))if(r.length>0&&key.equals(r[0]))return join(r);
        return "";
    }

    private static String select(String csv,int...idx){
        if(csv==null||csv.isEmpty())return "missing";
        String[] p=parse(csv);StringBuilder b=new StringBuilder();
        for(int i:idx){if(i<0||i>=p.length)continue;if(b.length()>0)b.append(" | ");b.append(p[i]);}
        return b.toString();
    }
    private static int count(String resource){return rows(resource).size();}
    private static List<String[]> rows(String resource){
        ArrayList<String[]> out=new ArrayList<>();
        try(InputStream in=ClientDiscoveryAuthority.class.getResourceAsStream(resource)){
            if(in==null)throw new IllegalStateException("missing resource "+resource);
            try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
                String s=br.readLine(); if(s==null)return out;
                while((s=br.readLine())!=null){if(!s.isEmpty())out.add(parse(s));}
            }
        }catch(IOException e){throw new ExceptionInInitializerError(e);}return out;
    }
    private static String join(String[]p){
        StringBuilder b=new StringBuilder();for(int i=0;i<p.length;i++){if(i>0)b.append(',');String s=p[i];boolean q=s.indexOf(',')>=0||s.indexOf('"')>=0;if(q)b.append('"');if(q)b.append(s.replace("\"","\"\""));else b.append(s);if(q)b.append('"');}return b.toString();
    }
    private static String[] parse(String s){
        ArrayList<String>a=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;
        for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'){if(q&&i+1<s.length()&&s.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}else if(c==','&&!q){a.add(b.toString());b.setLength(0);}else b.append(c);}a.add(b.toString());return a.toArray(new String[0]);
    }
    private ClientDiscoveryAuthority(){}
}
