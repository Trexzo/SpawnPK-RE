package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.SortedMap;

public final class G143DailyPvmTypedInfoIntegrationTest {
    private static final String PLAYER="daily-typed";
    private static final String CLAIM_ONLY_PLAYER="daily-typed-claim";

    public static void main(String[] args)throws Exception{
        boolean exactInfo=false;
        boolean localKeyOwned=false;
        boolean assignsOnInfo=false;
        boolean repeatedInfoStable=false;
        boolean persistedProgressVisible=false;
        boolean typedClaimDisabled=false;
        boolean claimNoAssignment=false;
        boolean unknownKeyFailClosed=false;
        boolean unknownKeyNoMutation=false;
        boolean chatFeedback=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();
        WorldPlayer claimOnlyPlayer=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                PLAYER
            );
        long claimOnlyGeneration=
            world.registerPlayer(
                claimOnlyPlayer,
                CLAIM_ONLY_PLAYER
            );

        try{
            LocalLabDailyChallengeRuntime daily=
                world.localLabDailyChallenges();

            LocalDailyChallengeCommandHandler handler=
                new LocalDailyChallengeCommandHandler(
                    player,
                    daily
                );
            LocalDailyChallengeCommandHandler claimOnlyHandler=
                new LocalDailyChallengeCommandHandler(
                    claimOnlyPlayer,
                    daily
                );

            DailyChallengeClientRequest info=
                ClientPacketProbe.dailyChallengeRequest(
                    "infochallenge "+
                    LocalLabDailyChallengeRuntime
                        .CHALLENGE_KEY
                );

            require(
                info!=null,
                "exact typed INFO not normalized"
            );

            exactInfo=
                info.action()==
                    DailyChallengeClientRequest.Action.INFO&&
                info.metadata().opcode==103&&
                info.metadata().provenance==
                    ClientRequestProvenance
                        .EXACT_CURRENT_CLIENT&&
                "VAR_BYTE_DAILY_CHALLENGE_ACTION_KEY_OPTIONAL_LF"
                    .equals(
                        info.metadata().schema
                    );

            localKeyOwned=
                LocalLabDailyChallengeRuntime
                    .CHALLENGE_KEY
                    .equals(
                        info.challengeKey()
                    );

            LocalDailyChallengeCommandHandler.Result first=
                handler.handle(info);

            DailyChallengeApplicationService.ChallengeSnapshot
                assigned=
                    daily.get(
                        PLAYER
                    ).challenge(
                        LocalLabDailyChallengeRuntime
                            .CHALLENGE_KEY
                    );

            assignsOnInfo=
                first!=null&&
                first.logText.contains(
                    "G143_DAILY_TYPED_REQUEST action=INFO"
                )&&
                first.logText.contains(
                    "assignedNow=true"
                )&&
                first.clientMessage.contains(
                    "Daily PvM: 0/3"
                )&&
                assigned.current==0L;

            LocalDailyChallengeCommandHandler.Result repeated=
                handler.handle(info);

            repeatedInfoStable=
                repeated!=null&&
                repeated.logText.contains(
                    "assignedNow=false"
                )&&
                daily.get(
                    PLAYER
                ).challenge(
                    LocalLabDailyChallengeRuntime
                        .CHALLENGE_KEY
                ).current==0L;

            daily.recordMonsterSpawnerFinalization(
                PLAYER,
                LocalLabMonsterSpawnerProvisioning
                    .NPC_DEFINITION_ID,
                1L
            );
            daily.recordMonsterSpawnerFinalization(
                PLAYER,
                LocalLabMonsterSpawnerProvisioning
                    .NPC_DEFINITION_ID,
                2L
            );

            LocalDailyChallengeCommandHandler.Result progressed=
                handler.handle(info);

            SortedMap<String,String> extension=
                player.snapshotExtensions()
                    .namespace(
                        LocalLabDailyChallengePersistence
                            .NAMESPACE
                    );

            persistedProgressVisible=
                progressed!=null&&
                progressed.clientMessage.contains(
                    "Daily PvM: 2/3"
                )&&
                "2".equals(
                    extension.get(
                        "progress"
                    )
                )&&
                "ACTIVE".equals(
                    extension.get(
                        "state"
                    )
                );

            DailyChallengeClientRequest claim=
                ClientPacketProbe.dailyChallengeRequest(
                    "claimchallenge "+
                    LocalLabDailyChallengeRuntime
                        .CHALLENGE_KEY
                );

            require(
                claim!=null,
                "exact typed CLAIM not normalized"
            );

            SortedMap<String,String> claimExtensionBefore=
                claimOnlyPlayer.snapshotExtensions()
                    .namespace(
                        LocalLabDailyChallengePersistence
                            .NAMESPACE
                    );

            LocalDailyChallengeCommandHandler.Result claimResult=
                claimOnlyHandler.handle(claim);

            SortedMap<String,String> claimExtensionAfter=
                claimOnlyPlayer.snapshotExtensions()
                    .namespace(
                        LocalLabDailyChallengePersistence
                            .NAMESPACE
                    );

            typedClaimDisabled=
                claimResult!=null&&
                claimResult.logText.contains(
                    "action=CLAIM"
                )&&
                claimResult.logText.contains(
                    "DISABLED_NO_REWARD_AUTHORITY"
                )&&
                claimResult.logText.contains(
                    "stateMutation=false"
                )&&
                claimResult.clientMessage.contains(
                    "not configured"
                );

            claimNoAssignment=
                daily.get(
                    CLAIM_ONLY_PLAYER
                )==null&&
                claimExtensionBefore.equals(
                    claimExtensionAfter
                )&&
                claimExtensionAfter.isEmpty();

            long beforeUnknown=
                daily.get(
                    PLAYER
                ).challenge(
                    LocalLabDailyChallengeRuntime
                        .CHALLENGE_KEY
                ).current;

            DailyChallengeClientRequest foreign=
                ClientPacketProbe.dailyChallengeRequest(
                    "infochallenge foreign-daily-key"
                );

            require(
                foreign!=null,
                "foreign typed INFO not normalized"
            );

            LocalDailyChallengeCommandHandler.Result foreignResult=
                handler.handle(foreign);

            long afterUnknown=
                daily.get(
                    PLAYER
                ).challenge(
                    LocalLabDailyChallengeRuntime
                        .CHALLENGE_KEY
                ).current;

            String foreignDiagnostic=
                LocalPendingRequestDispatcher
                    .dailyChallengeFailClosedDiagnostic(
                        foreign
                    );

            unknownKeyFailClosed=
                foreignResult==null&&
                foreignDiagnostic.contains(
                    "DAILY_CHALLENGE_KEY_ADAPTER_UNPROVEN"
                )&&
                foreignDiagnostic.contains(
                    "stateMutation=false"
                );

            unknownKeyNoMutation=
                beforeUnknown==2L&&
                afterUnknown==beforeUnknown;

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{1,2,3,4}
                    )
                );

            int wireBefore=
                wire.size();

            new SocialChatPresentationPublisher(
                writer
            ).serverMessage(
                progressed.clientMessage
            );

            chatFeedback=
                wire.size()>wireBefore;

            require(
                exactInfo&&
                localKeyOwned&&
                assignsOnInfo&&
                repeatedInfoStable&&
                persistedProgressVisible&&
                typedClaimDisabled&&
                claimNoAssignment&&
                unknownKeyFailClosed&&
                unknownKeyNoMutation&&
                chatFeedback,
                "G14.3 acceptance"
            );

            System.out.println(
                "G143_DAILY_TYPED_INFO_BRIDGE_PASS"+
                " exactInfo="+exactInfo+
                " localKeyOwned="+localKeyOwned+
                " assignsOnInfo="+assignsOnInfo+
                " repeatedInfoStable="+repeatedInfoStable+
                " persistedProgressVisible="+
                    persistedProgressVisible+
                " typedClaimDisabled="+
                    typedClaimDisabled+
                " claimNoAssignment="+
                    claimNoAssignment+
                " unknownKeyFailClosed="+
                    unknownKeyFailClosed+
                " unknownKeyNoMutation="+
                    unknownKeyNoMutation+
                " chatFeedback="+chatFeedback+
                " nativePresentationClaim=false"+
                " rewardPolicyClaim=false"+
                " resetPolicyClaim=false"+
                " originalServerPolicyClaim=false"
            );
        }finally{
            if(world.players().owns(
                    player,
                    generation))
                world.unregisterPlayer(
                    player,
                    generation
                );

            if(world.players().owns(
                    claimOnlyPlayer,
                    claimOnlyGeneration))
                world.unregisterPlayer(
                    claimOnlyPlayer,
                    claimOnlyGeneration
                );

            world.close();
        }
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G143DailyPvmTypedInfoIntegrationTest(){}
}
