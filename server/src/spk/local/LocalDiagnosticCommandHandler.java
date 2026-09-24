package spk.local;

import java.io.IOException;

/**
 * Research/diagnostic command family extracted from the network session.
 *
 * The caller already executes on the authoritative WorldPulse thread. This
 * handler deliberately owns only diagnostics/browser-style commands; gameplay
 * mutation commands remain outside this class.
 */
final class LocalDiagnosticCommandHandler {
    private final World world;
    private final EquipmentState equipment;
    private final MovementState movement;
    private final PrayerState prayers;
    private final MagicState magic;
    private final CombatStyleState combatStyles;
    private final NativeItemLibraryService itemLibrary;

    LocalDiagnosticCommandHandler(
        World world,
        EquipmentState equipment,
        MovementState movement,
        PrayerState prayers,
        MagicState magic,
        CombatStyleState combatStyles,
        NativeItemLibraryService itemLibrary
    ){
        this.world=java.util.Objects.requireNonNull(world,"world");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.prayers=java.util.Objects.requireNonNull(prayers,"prayers");
        this.magic=java.util.Objects.requireNonNull(magic,"magic");
        this.combatStyles=java.util.Objects.requireNonNull(combatStyles,"combatStyles");
        this.itemLibrary=java.util.Objects.requireNonNull(itemLibrary,"itemLibrary");
    }

    boolean handle(
        String[] p,
        ServerPacketWriter serverPackets,
        String tag,
        String username,
        String loginAlias,
        boolean persistentAccount,
        SceneUpdatePublisher scenePublisher
    )throws IOException{
        if(p==null||p.length==0)return false;
        String command=p[0];

        if(command.equalsIgnoreCase("equipstr")){
            int item=p.length>=2?parseInt(p[1],-1):-1;
            ItemAuthorityRepository.Entry e=ItemAuthorityRepository.get(item);
            // Exact current client Ctrl-hover requests equipstr <id> and then waits
            // for server-fed numeric key24 data. R8.1 refuses to invent those 14
            // numbers, but it also must not leave the client stuck on Loading.
            serverPackets.varShort(126,new PacketPayloadWriter()
                .putStringNl("RESET_HOVER_EQUIPMENT")
                .putU16BELowAdd128(0)
                .toByteArray());
            String relation=e==null?"":e.relationSummary;
            String mechanics=e==null?"":e.mechanicsSummary;
            System.out.println(tag+"V5181_EQUIPSTR_FAIL_CLOSED item="+item+" known="+(e!=null)+
                " resetHover=true numeric14=UNRESOLVED_SERVER_AUTHORITY relation=["+clip(relation,100)+
                "] mechanics=["+clip(mechanics,100)+"]");
            return true;
        }

        if(command.equalsIgnoreCase("igsearch")){
            String q=joinTokens(p,1);
            String r=itemLibrary.searchExact(serverPackets,q);
            System.out.println(tag+"V5150_ITEM_LIBRARY_IGSEARCH query=\""+q+"\" result="+r+
                " route=EXACT_CURRENT_C2S103");
            return true;
        }

        if(command.equalsIgnoreCase("itemlib")){
            int item=equipment.weapon();
            if(p.length>=2){
                int parsed=parseInt(p[1],Integer.MIN_VALUE);
                if(parsed!=Integer.MIN_VALUE)item=parsed;
                else{
                    ItemAuthorityRepository.Entry e=ItemAuthorityRepository.byExactName(joinTokens(p,1));
                    item=e==null?-1:e.itemId;
                }
            }
            if(item<0||ItemAuthorityRepository.get(item)==null){
                System.out.println(tag+"V5150_ITEM_LIBRARY_DEV_OPEN result=REJECTED_UNKNOWN_ITEM syntax=::itemlib <itemId|exact name>");
                return true;
            }
            String r=itemLibrary.open(serverPackets,item);
            System.out.println(tag+"V5150_ITEM_LIBRARY_DEV_OPEN result="+r+
                " opener=LOCAL_DEV_ONLY nativeRoot=47500 normalRequest=igsearch");
            return true;
        }

        if(command.equalsIgnoreCase("engine")){
            System.out.println(tag+"V5123_ENGINE "+BuildInfo.summary()+" account="+username+
                " loginAlias="+loginAlias+" persistent="+persistentAccount+" "+world.summary()+
                " metrics="+world.metrics()+" sceneBase="+
                (scenePublisher==null?"none":scenePublisher.context().currentChunkX()+","+scenePublisher.context().currentChunkY())+
                " npcDefinitions="+EffectiveNpcDefinitionRepository.count()+
                " groundActionExceptions="+GroundItemActionRepository.exceptionCount()+
                " miniDefinitions="+MiniPetDefinitionRepository.count()+
                " itemAuthority="+ItemAuthorityRepository.count()+
                " worldRegions="+WorldRegionAuthorityRepository.count()+
                " worldAuthorityMode=DATA_ONLY");
            return true;
        }

        return false;
    }

    private static String joinTokens(String[] p,int start){
        if(p==null||start>=p.length)return "";
        StringBuilder b=new StringBuilder();
        for(int i=start;i<p.length;i++){
            if(i>start)b.append(' ');
            b.append(p[i]);
        }
        return b.toString();
    }

    private static int parseInt(String s,int fallback){
        try{return Integer.parseInt(s);}catch(Exception e){return fallback;}
    }

    private static String clip(String s,int n){
        if(s==null)return "";
        String x=s.replace('\n',' ').replace('\r',' ').replaceAll("\\s+"," ").trim();
        return x.length()<=n?x:x.substring(0,Math.max(0,n-3))+"...";
    }
}
