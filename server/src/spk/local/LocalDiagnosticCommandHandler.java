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
    private final NativeItemLibraryService itemLibrary;

    LocalDiagnosticCommandHandler(
        World world,
        NativeItemLibraryService itemLibrary
    ){
        this.world=java.util.Objects.requireNonNull(world,"world");
        this.itemLibrary=java.util.Objects.requireNonNull(itemLibrary,"itemLibrary");
    }

    boolean itemLibrarySearchWillOpen(
        String[] p
    ){
        if(p==null||p.length==0||
           !p[0].equalsIgnoreCase("igsearch"))
            return false;

        return ItemAuthorityRepository.byExactName(
            joinTokens(
                p,
                1
            )
        )!=null;
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

        if(command.equalsIgnoreCase("igsearch")){
            String q=joinTokens(p,1);
            String r=itemLibrary.searchExact(serverPackets,q);
            System.out.println(tag+"V5150_ITEM_LIBRARY_IGSEARCH query=\""+q+"\" result="+r+
                " route=EXACT_CURRENT_C2S103");
            return true;
        }

        return false;
    }

    String itemLibraryOpen(
        int item,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(item<0||
           ItemAuthorityRepository.get(item)==null)
            return "V5150_ITEM_LIBRARY_DEV_OPEN result=REJECTED_UNKNOWN_ITEM syntax=::itemlib <itemId|exact name>";

        String result=
            itemLibrary.open(
                serverPackets,
                item
            );

        return "V5150_ITEM_LIBRARY_DEV_OPEN result="+
            result+
            " opener=LOCAL_DEV_ONLY nativeRoot=47500 normalRequest=igsearch";
    }

    String equipStr(
        int item,
        ServerPacketWriter serverPackets
    )throws IOException{
        ItemAuthorityRepository.Entry entry=
            ItemAuthorityRepository.get(
                item
            );

        // Exact current client Ctrl-hover requests equipstr <id> and then waits
        // for server-fed numeric key24 data. This runtime effect refuses to
        // invent those 14 values but still clears the client's Loading state.
        serverPackets.varShort(
            126,
            new PacketPayloadWriter()
                .putStringNl(
                    "RESET_HOVER_EQUIPMENT"
                )
                .putU16BELowAdd128(
                    0
                )
                .toByteArray()
        );

        String relation=
            entry==null
                ?""
                :entry.relationSummary;
        String mechanics=
            entry==null
                ?""
                :entry.mechanicsSummary;

        return "V5181_EQUIPSTR_FAIL_CLOSED item="+
            item+
            " known="+
            (entry!=null)+
            " resetHover=true"+
            " numeric14=UNRESOLVED_SERVER_AUTHORITY"+
            " relation=["+
            clip(
                relation,
                100
            )+
            "] mechanics=["+
            clip(
                mechanics,
                100
            )+
            "]";
    }

    String engineSummary(
        String username,
        String loginAlias,
        boolean persistentAccount,
        SceneUpdatePublisher scenePublisher
    ){
        return "V5123_ENGINE "+
            BuildInfo.summary()+
            " account="+username+
            " loginAlias="+loginAlias+
            " persistent="+persistentAccount+
            " "+world.summary()+
            " metrics="+world.metrics()+
            " sceneBase="+
            (scenePublisher==null
                ?"none"
                :scenePublisher.context().currentChunkX()+
                    ","+
                    scenePublisher.context().currentChunkY())+
            " npcDefinitions="+
            EffectiveNpcDefinitionRepository.count()+
            " groundActionExceptions="+
            GroundItemActionRepository.exceptionCount()+
            " miniDefinitions="+
            MiniPetDefinitionRepository.count()+
            " itemAuthority="+
            ItemAuthorityRepository.count()+
            " worldRegions="+
            WorldRegionAuthorityRepository.count()+
            " worldAuthorityMode=DATA_ONLY";
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

    private static String clip(String s,int n){
        if(s==null)return "";
        String x=s.replace('\n',' ').replace('\r',' ').replaceAll("\\s+"," ").trim();
        return x.length()<=n?x:x.substring(0,Math.max(0,n-3))+"...";
    }
}
