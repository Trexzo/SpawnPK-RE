package spk.local;

import java.io.ByteArrayOutputStream;

public final class G101NormalDuelProposalIntegrationTest {
    private static final String A="g101-a";
    private static final String B="g101-b";
    private static final String C="g101-c";

    public static void main(String[] args)throws Exception{
        boolean exactSelector25754=false;
        boolean exactModes3=false;
        boolean exactInvite25763=false;
        boolean worldOwned=false;
        boolean onlineTargetRequired=false;
        boolean modeRequired=false;
        boolean standardProposal=false;
        boolean whipMetadataOnly=false;
        boolean whipDdsMetadataOnly=false;
        boolean participantExclusive=false;
        boolean selectorLifecycle=false;
        boolean challengeGrammar=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer a=
            new WorldPlayer();
        WorldPlayer b=
            new WorldPlayer();
        WorldPlayer c=
            new WorldPlayer();

        world.registerPlayer(a,A);
        world.registerPlayer(b,B);
        world.registerPlayer(c,C);

        try{
            LocalLabDuelRuntime runtime=
                world.localDuels();

            worldOwned=
                runtime!=null&&
                runtime==world.localDuels();

            exactSelector25754=
                NormalDuelPresentation
                    .SELECTOR_ROOT==25754;

            NormalDuelPresentation.Input standard=
                NormalDuelPresentation
                    .resolveSelectorWidget(
                        NormalDuelPresentation
                            .STANDARD_WIDGET
                    );
            NormalDuelPresentation.Input whip=
                NormalDuelPresentation
                    .resolveSelectorWidget(
                        NormalDuelPresentation
                            .WHIP_WIDGET
                    );
            NormalDuelPresentation.Input whipDds=
                NormalDuelPresentation
                    .resolveSelectorWidget(
                        NormalDuelPresentation
                            .WHIP_DDS_WIDGET
                    );
            NormalDuelPresentation.Input invite=
                NormalDuelPresentation
                    .resolveSelectorWidget(
                        NormalDuelPresentation
                            .INVITE_WIDGET
                    );
            NormalDuelPresentation.Input close=
                NormalDuelPresentation
                    .resolveSelectorWidget(
                        NormalDuelPresentation
                            .CLOSE_WIDGET
                    );

            exactModes3=
                standard!=null&&
                standard.mode==
                    NormalDuelPresentation
                        .DuelMode.STANDARD&&
                whip!=null&&
                whip.mode==
                    NormalDuelPresentation
                        .DuelMode.WHIP_ONLY&&
                whipDds!=null&&
                whipDds.mode==
                    NormalDuelPresentation
                        .DuelMode.WHIP_DDS_ONLY;

            exactInvite25763=
                NormalDuelPresentation
                    .INVITE_WIDGET==25763&&
                invite!=null&&
                invite.kind==
                    NormalDuelPresentation
                        .InputKind.INVITE;

            challengeGrammar=
                ":duelreq:".equals(
                    NormalDuelPresentation
                        .challengeSuffix(
                            NormalDuelPresentation
                                .DuelMode.STANDARD
                        )
                )&&
                ":whipduelreq:".equals(
                    NormalDuelPresentation
                        .challengeSuffix(
                            NormalDuelPresentation
                                .DuelMode.WHIP_ONLY
                        )
                )&&
                ":whipddsreq:".equals(
                    NormalDuelPresentation
                        .challengeSuffix(
                            NormalDuelPresentation
                                .DuelMode.WHIP_DDS_ONLY
                        )
                );

            require(
                exactSelector25754&&
                exactModes3&&
                exactInvite25763&&
                challengeGrammar&&
                worldOwned,
                "exact Duel authority setup failed"
            );

            LocalDuelUiHandler aUi=
                new LocalDuelUiHandler(
                    a,
                    runtime
                );

            try{
                aUi.open(
                    "missing-player",
                    writer(1)
                );
            }catch(IllegalStateException expected){
                onlineTargetRequired=
                    !aUi.isOpen();
            }

            require(
                onlineTargetRequired,
                "offline Duel target opened selector"
            );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            LocalDuelUiHandler.Result opened=
                aUi.open(
                    B,
                    writer(
                        wire,
                        11
                    )
                );

            selectorLifecycle=
                opened.status==
                    LocalDuelUiHandler.Status.OPENED&&
                aUi.isOpen()&&
                B.equalsIgnoreCase(
                    aUi.targetRef()
                )&&
                wire.size()>0;

            require(
                selectorLifecycle,
                "Duel selector did not open"
            );

            LocalDuelUiHandler.Result missingMode=
                aUi.handle(
                    invite
                );

            modeRequired=
                missingMode.status==
                    LocalDuelUiHandler
                        .Status.MODE_REQUIRED&&
                runtime.size()==0&&
                aUi.isOpen();

            require(
                modeRequired,
                "Duel Invite without mode mutated proposal state"
            );

            LocalDuelUiHandler.Result selected=
                aUi.handle(
                    standard
                );

            require(
                selected.status==
                    LocalDuelUiHandler
                        .Status.MODE_SELECTED&&
                selected.mode==
                    NormalDuelPresentation
                        .DuelMode.STANDARD&&
                ":duelreq:".equals(
                    selected.challengeSuffix
                ),
                "standard Duel mode selection failed"
            );

            LocalDuelUiHandler.Result proposed=
                aUi.handle(
                    invite
                );

            DuelSessionService.Snapshot openA=
                runtime.openFor(A);
            DuelSessionService.Snapshot openB=
                runtime.openFor(B);

            standardProposal=
                proposed.status==
                    LocalDuelUiHandler.Status.PROPOSED&&
                proposed.proposal!=null&&
                proposed.proposal.state==
                    DuelSessionService.State.PROPOSED&&
                "standard".equals(
                    proposed.proposal.duelTypeKey
                )&&
                proposed.proposal==
                    proposed.proposal&&
                openA!=null&&
                openB!=null&&
                openA.challengeId.equals(
                    openB.challengeId
                )&&
                runtime.size()==1&&
                !aUi.isOpen();

            require(
                standardProposal,
                "standard Duel proposal failed"
            );

            LocalDuelUiHandler cUi=
                new LocalDuelUiHandler(
                    c,
                    runtime
                );

            cUi.open(
                B,
                writer(21)
            );
            cUi.handle(
                standard
            );

            try{
                cUi.handle(
                    invite
                );
            }catch(IllegalStateException expected){
                participantExclusive=
                    runtime.size()==1&&
                    runtime.openFor(C)==null&&
                    runtime.openFor(B)!=null;
            }

            require(
                participantExclusive,
                "Duel participant exclusivity failed"
            );

            cUi.close();

            runtime.duels()
                .cancelOpen(
                    proposed.proposal.challengeId,
                    A
                );

            LocalLabDuelRuntime.ProposalResult
                whipProposal=
                    runtime.propose(
                        A,
                        B,
                        NormalDuelPresentation
                            .DuelMode.WHIP_ONLY
                    );

            whipMetadataOnly=
                "whip-only".equals(
                    whipProposal.snapshot
                        .duelTypeKey
                )&&
                ":whipduelreq:".equals(
                    whipProposal.challengeSuffix
                )&&
                unrestricted(
                    whipProposal.snapshot.rules
                );

            require(
                whipMetadataOnly,
                "Whip Duel mode invented restriction policy"
            );

            runtime.duels()
                .cancelOpen(
                    whipProposal.snapshot
                        .challengeId,
                    A
                );

            LocalLabDuelRuntime.ProposalResult
                whipDdsProposal=
                    runtime.propose(
                        A,
                        B,
                        NormalDuelPresentation
                            .DuelMode.WHIP_DDS_ONLY
                    );

            whipDdsMetadataOnly=
                "whip-dds-only".equals(
                    whipDdsProposal.snapshot
                        .duelTypeKey
                )&&
                ":whipddsreq:".equals(
                    whipDdsProposal
                        .challengeSuffix
                )&&
                unrestricted(
                    whipDdsProposal.snapshot.rules
                );

            require(
                whipDdsMetadataOnly,
                "Whip+DDS Duel mode invented restriction policy"
            );

            runtime.duels()
                .cancelOpen(
                    whipDdsProposal.snapshot
                        .challengeId,
                    A
                );

            aUi.open(
                B,
                writer(31)
            );

            LocalDuelUiHandler.Result closed=
                aUi.handle(
                    close
                );
            LocalDuelUiHandler.Result afterClose=
                aUi.handle(
                    standard
                );

            selectorLifecycle=
                selectorLifecycle&&
                closed.status==
                    LocalDuelUiHandler.Status.CLOSED&&
                !aUi.isOpen()&&
                afterClose.status==
                    LocalDuelUiHandler
                        .Status.CLOSED_UI_NOOP;

            require(
                selectorLifecycle,
                "Duel selector close lifecycle failed"
            );

            System.out.println(
                "G101_NORMAL_DUEL_PROPOSAL_PASS"+
                " exactSelector25754="+
                    exactSelector25754+
                " exactModes3="+exactModes3+
                " exactInvite25763="+
                    exactInvite25763+
                " worldOwned="+worldOwned+
                " onlineTargetRequired="+
                    onlineTargetRequired+
                " modeRequired="+modeRequired+
                " standardProposal="+
                    standardProposal+
                " whipMetadataOnly="+
                    whipMetadataOnly+
                " whipDdsMetadataOnly="+
                    whipDdsMetadataOnly+
                " participantExclusive="+
                    participantExclusive+
                " selectorLifecycle="+
                    selectorLifecycle+
                " challengeGrammar="+
                    challengeGrammar+
                " stakeClaim=false"+
                " restrictionClaim=false"+
                " arenaClaim=false"+
                " winnerClaim=false"+
                " rewardClaim=false"+
                " originalSpawnpkPolicyClaim=false"
            );
        }finally{
            world.close();
        }
    }

    private static boolean unrestricted(
        MatchRules rules
    ){
        return rules!=null&&
            rules.teamMode==
                MatchRules.TeamMode.TEAMS&&
            rules.spellPolicy==
                MatchRules.SpellPolicy.UNRESTRICTED&&
            rules.prayerPolicy==
                MatchRules.PrayerPolicy.UNRESTRICTED&&
            rules.freezePolicy==
                MatchRules.RestrictionPolicy.ALLOWED&&
            rules.interferencePolicy==
                MatchRules.RestrictionPolicy.ALLOWED&&
            rules.winConditionKind==
                MatchRules.WinConditionKind.CALLER_RESOLVED&&
            LocalLabDuelRuntime.AUTHORITY.equals(
                rules.sourceAuthority
            );
    }

    private static ServerPacketWriter writer(
        int seed
    ){
        return writer(
            new ByteArrayOutputStream(),
            seed
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream out,
        int seed
    ){
        return new ServerPacketWriter(
            out,
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
        );
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

    private G101NormalDuelProposalIntegrationTest(){}
}
