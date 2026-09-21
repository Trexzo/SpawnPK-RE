package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** R8.5 exact-current client application-protocol authority. Read-only; server outcomes remain external. */
final class ClientApplicationProtocolAuthority {
    static final String CLIENT_SHA256="854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6";
    static final String LEGACY_V307_CLIENT_SHA256="6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662";
    static final String R31_SHA256="ef4796cbfdcf5017ac56b549c8d6b2720ec7e52d57ced0940a52e7a84aeba898";
    static final String R4_SHA256="a6a86e356a27f9b7949455c72f530bea5b53338072f4ed8c17edd751ae38a773";
    static final String R5_SHA256="3f421df4e6d4b660fb86fd32f45b599c86bb9df73bd2fcacc57da042988ba75d";
    static final String ROOT="/spk/local/data/research_r84/";
    static final String ROOT85="/spk/local/data/research_r85/";
    static final String APP_INDEX=ROOT+"s2c250_application_index.csv";
    static final String OPS_R4=ROOT+"s2c250_operation_grammars_r4.csv";
    static final String OPS_R5=ROOT+"s2c250_operation_grammars_r5_additions.csv";
    static final String OPS_R6=ROOT85+"s2c250_operation_grammars_r6_remaining.csv";
    static final String VM=ROOT+"condition_vm_opcodes.csv";
    static final String SETTINGS=ROOT+"settings_exact_map.csv";
    static final String MENU=ROOT+"menu_action_semantic_routes.csv";
    static final String XPROD=ROOT+"x_producer_semantics.csv";
    static final String S2C126=ROOT+"s2c126_argument_control_routes.csv";
    static final String CLAN=ROOT+"clan_wars_widget_routes.csv";
    static final String GAMBLE=ROOT+"gambling_widget_routes.csv";
    static final int APP_SUBTYPES=count(APP_INDEX);
    static final int R4_OPERATION_ROWS=count(OPS_R4);
    static final int R5_OPERATION_ROWS=count(OPS_R5);
    static final int R6_OPERATION_ROWS=count(OPS_R6);
    static final int OPERATION_DECODED_SUBTYPES=distinctFirst(OPS_R4,OPS_R5,OPS_R6);
    static final int VM_OPCODES=count(VM), SETTINGS_MAPPED=count(SETTINGS), MENU_ROUTES=count(MENU), X_PRODUCERS=count(XPROD), S2C126_ARGUMENT_ROUTES=count(S2C126), CLAN_WARS_WIDGETS=count(CLAN), GAMBLING_WIDGETS=count(GAMBLE);
    static String summary(){return "R8.5 app250="+APP_SUBTYPES+" subtypes opDecoded="+OPERATION_DECODED_SUBTYPES+" opRows="+(R4_OPERATION_ROWS+R5_OPERATION_ROWS+R6_OPERATION_ROWS)+" vm="+VM_OPCODES+" settings="+SETTINGS_MAPPED+" menuRoutes="+MENU_ROUTES;}
    static String applicationSummary(){return "S2C250 VAR_BYTE -> u16_be subtype -> typed payload; subtypes="+APP_SUBTYPES+" operationDecoded="+OPERATION_DECODED_SUBTYPES+" rows="+(R4_OPERATION_ROWS+R5_OPERATION_ROWS+R6_OPERATION_ROWS)+"; all 43 outer handlers structurally closed; subtype20 external-receiver emitter intentionally disabled in LocalLab";}
    static String controlSummary(){return "S2C126 argRoutes="+S2C126_ARGUMENT_ROUTES+" | conditionVM opcodes="+VM_OPCODES+" | exact settings="+SETTINGS_MAPPED+" | X producers="+X_PRODUCERS+" | clanWars widgets="+CLAN_WARS_WIDGETS+" | gambling widgets="+GAMBLING_WIDGETS;}
    static String interactionSummary(){return "menu/C2S routes="+MENU_ROUTES+" | packet250 typed writer=43/43 structural authority | safe LocalLab publishers=all except subtype20 external receiver | fixture service=LOCAL_DEV_ONLY";}
    static String boundary(){return "client-facing wire/UI authority only; rewards, prices, RNG, matchmaking, validation, event choice, world business rules and gameplay outcomes remain server-owned/fail-closed";}
    private static int count(String resource){return rows(resource).size();}
    private static int distinctFirst(String... resources){HashSet<String>s=new HashSet<>();for(String r:resources)for(String[]x:rows(r))if(x.length>0)s.add(x[0]);return s.size();}
    private static List<String[]> rows(String resource){ArrayList<String[]> out=new ArrayList<>();try(InputStream in=ClientApplicationProtocolAuthority.class.getResourceAsStream(resource)){if(in==null)throw new IllegalStateException("missing resource "+resource);try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String s=br.readLine();if(s==null)return out;while((s=br.readLine())!=null)if(!s.isEmpty())out.add(parse(s));}}catch(IOException e){throw new ExceptionInInitializerError(e);}return out;}
    private static String[] parse(String s){ArrayList<String>a=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'){if(q&&i+1<s.length()&&s.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}else if(c==','&&!q){a.add(b.toString());b.setLength(0);}else b.append(c);}a.add(b.toString());return a.toArray(new String[0]);}
    private ClientApplicationProtocolAuthority(){}
}
