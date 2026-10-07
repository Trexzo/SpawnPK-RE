package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class G111PkRatingsReadOnlyIntegrationTest {
    private static final String A="g111-a";
    private static final String B="g111-b";
    private static final String C="g111-c";
    private static final int[] SEED={11,12,13,14};

    private static final class Bridge
        implements LocalCommandDispatcher.SessionBridge
    {
        final LocalPkRatingsUiHandler handler;
        PkRatingsService.Snapshot snapshot;
        boolean opened;

        Bridge(
            LocalPkRatingsUiHandler handler
        ){
            this.handler=handler;
        }

        @Override public SceneUpdatePublisher scenePublisher(){
            return null;
        }

        @Override public void replaceScenePublisher(
            SceneUpdatePublisher replacement
        ){}

        @Override public void saveAccount(
            String tag,
            String reason
        ){}

        @Override public void openDevPanel(
            ServerPacketWriter serverPackets
        )throws IOException{}

        @Override public boolean openPkRatings(
            ServerPacketWriter serverPackets
        )throws IOException{
            snapshot=handler.open(
                serverPackets
            );
            opened=true;
            return true;
        }

        @Override public void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        ){}
    }

    private static final class Feed {
        int root;
        int clearCount;
        int appendCount;
        boolean subtype16=true;
        boolean fontZero=true;
        boolean allNonselectable=true;
        final ArrayList<String> text=
            new ArrayList<>();
    }

    public static void main(String[] args)throws Exception{
        boolean exactRoot40403=false;
        boolean exactSubtype16=false;
        boolean maxRows50=false;
        boolean commandRoute=false;
        boolean onlineRoster=false;
        boolean registrationOrder=false;
        boolean readOnly=false;
        boolean selectableRows=false;
        boolean clientFeed=false;
        boolean fontPolicyLocal=false;

        World world=
            World.isolatedForTest(
                60_000L
            );

        world.registerPlayer(
            new WorldPlayer(),
            A
        );
        world.registerPlayer(
            new WorldPlayer(),
            B
        );
        world.registerPlayer(
            new WorldPlayer(),
            C
        );

        try{
            LocalPkRatingsUiHandler handler=
                new LocalPkRatingsUiHandler(
                    world
                );
            Bridge bridge=
                new Bridge(
                    handler
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        SEED.clone()
                    )
                );

            commandRoute=
                LocalCommandDispatcher
                    .isPkRatingsRoute(
                        new String[]{"pkratings"}
                    )&&
                LocalCommandDispatcher
                    .isPkRatingsRoute(
                        new String[]{"pkrating"}
                    )&&
                !LocalCommandDispatcher
                    .isPkRatingsRoute(
                        new String[]{"pkratings","extra"}
                    );

            require(
                commandRoute,
                "PK Ratings command route authority failed"
            );

            require(
                LocalCommandDispatcher
                    .dispatchPkRatingsCommand(
                        new String[]{"pkratings"},
                        bridge,
                        writer,
                        "[g111] "
                    ),
                "PK Ratings command was not handled"
            );

            require(
                bridge.opened&&
                bridge.snapshot!=null,
                "PK Ratings handler did not publish snapshot"
            );

            PkRatingsService.Snapshot snapshot=
                bridge.snapshot;

            exactRoot40403=
                PkRatingsPresentation
                    .RATINGS_ROOT==40403;
            exactSubtype16=
                PkRatingsPresentation
                    .APPLICATION_SUBTYPE==16;
            maxRows50=
                PkRatingsPresentation
                    .MAX_ROWS==50&&
                PkRatingsService
                    .MAX_ROWS==50;

            onlineRoster=
                snapshot.rows.size()==4&&
                "LocalLab PK Ratings (not ranked)"
                    .equals(
                        snapshot.rows
                            .get(0)
                            .displayText
                    );

            registrationOrder=
                ("Online: "+A)
                    .equals(
                        snapshot.rows
                            .get(1)
                            .displayText
                    )&&
                ("Online: "+B)
                    .equals(
                        snapshot.rows
                            .get(2)
                            .displayText
                    )&&
                ("Online: "+C)
                    .equals(
                        snapshot.rows
                            .get(3)
                            .displayText
                    );

            selectableRows=false;
            for(PkRatingsService.Row row:
                    snapshot.rows)
                if(row.selectable)
                    selectableRows=true;

            readOnly=
                !selectableRows&&
                snapshot.policyAuthority==
                    AtomicTransactionService
                        .SourceAuthority
                        .CUSTOM_LOCALLAB;

            fontPolicyLocal=
                LocalPkRatingsUiHandler
                    .FONT_INDEX==0;

            Feed feed=
                decode(
                    wire.toByteArray()
                );

            exactRoot40403=
                exactRoot40403&&
                feed.root==
                    PkRatingsPresentation
                        .RATINGS_ROOT;
            exactSubtype16=
                exactSubtype16&&
                feed.subtype16;
            selectableRows=
                selectableRows||
                !feed.allNonselectable;
            fontPolicyLocal=
                fontPolicyLocal&&
                feed.fontZero;

            clientFeed=
                feed.clearCount==1&&
                feed.appendCount==
                    snapshot.rows.size()&&
                feed.text.size()==
                    snapshot.rows.size()&&
                feed.text.get(0).equals(
                    "LocalLab PK Ratings (not ranked)"
                )&&
                feed.text.get(1).equals(
                    "Online: "+A
                )&&
                feed.text.get(2).equals(
                    "Online: "+B
                )&&
                feed.text.get(3).equals(
                    "Online: "+C
                );

            require(
                exactRoot40403&&
                exactSubtype16&&
                maxRows50&&
                onlineRoster&&
                registrationOrder&&
                readOnly&&
                selectableRows&&
                clientFeed&&
                fontPolicyLocal,
                "G11.1 read-only PK Ratings postimage failed"
            );

            System.out.println(
                "G111_PK_RATINGS_READ_ONLY_PASS"+
                " exactRoot40403="+exactRoot40403+
                " exactSubtype16="+exactSubtype16+
                " maxRows50="+maxRows50+
                " commandRoute="+commandRoute+
                " onlineRoster="+onlineRoster+
                " registrationOrder="+registrationOrder+
                " readOnly="+readOnly+
                " selectableRows="+selectableRows+
                " clientFeed="+clientFeed+
                " fontPolicyLocal="+fontPolicyLocal+
                " ratingFormulaClaim=false"+
                " rankingOrderClaim=false"+
                " rowSelectionClaim=false"+
                " navigationClaim=false"+
                " rewardClaim=false"+
                " persistenceClaim=false"+
                " originalCommandClaim=false"+
                " originalSpawnpkPolicyClaim=false"
            );
        }finally{
            world.close();
        }
    }

    private static Feed decode(
        byte[] wire
    ){
        Feed feed=new Feed();
        IsaacCipher decoder=
            new IsaacCipher(
                SEED.clone()
            );

        int p=0;

        require(
            wire.length>=3,
            "PK Ratings wire truncated before root"
        );

        int rootOpcode=
            ((wire[p++]&255)-
                decoder.nextInt())&
            255;

        require(
            rootOpcode==97,
            "PK Ratings first opcode="+
                rootOpcode
        );

        feed.root=
            ((wire[p++]&255)<<8)|
            (wire[p++]&255);

        while(p<wire.length){
            int opcode=
                ((wire[p++]&255)-
                    decoder.nextInt())&
                255;

            require(
                opcode==
                    ApplicationPacket250Writer
                        .OPCODE,
                "PK Ratings application opcode="+
                    opcode
            );

            require(
                p<wire.length,
                "PK Ratings application length missing"
            );

            int length=wire[p++]&255;

            require(
                length>=3&&
                p+length<=wire.length,
                "PK Ratings application length="+
                    length
            );

            int end=p+length;
            int subtype=
                ((wire[p++]&255)<<8)|
                (wire[p++]&255);

            if(subtype!=
                    PkRatingsPresentation
                        .APPLICATION_SUBTYPE)
                feed.subtype16=false;

            int operation=wire[p++]&255;

            if(operation==0){
                feed.clearCount++;
            }else if(operation==1){
                require(
                    p+2<=end,
                    "PK Ratings append truncated"
                );

                int font=wire[p++]&255;
                int selectable=wire[p++]&255;

                if(font!=
                        LocalPkRatingsUiHandler
                            .FONT_INDEX)
                    feed.fontZero=false;

                if(selectable!=0)
                    feed.allNonselectable=false;

                int start=p;
                while(p<end&&
                      (wire[p]&255)!=10)
                    p++;

                require(
                    p<end,
                    "PK Ratings append text terminator missing"
                );

                feed.text.add(
                    new String(
                        wire,
                        start,
                        p-start,
                        StandardCharsets
                            .ISO_8859_1
                    )
                );
                p++;
                feed.appendCount++;
            }else{
                throw new AssertionError(
                    "unexpected G11.1 PK Ratings operation="+
                    operation
                );
            }

            require(
                p==end,
                "PK Ratings frame trailing bytes="+
                    (end-p)
            );
        }

        return feed;
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private G111PkRatingsReadOnlyIntegrationTest(){}
}
