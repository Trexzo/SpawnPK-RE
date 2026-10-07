package spk.local;

import java.util.Objects;

/**
 * World-owned LocalLab binding from exact normal-Duel mode selection to the
 * reusable protocol-independent DuelSessionService proposal lifecycle.
 *
 * G10.1 deliberately owns proposal state only. Stake/escrow, weapon
 * restrictions, arena behavior, accept/start, winner policy and rewards remain
 * external authority.
 */
final class LocalLabDuelRuntime {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G101_NORMAL_DUEL_PROPOSAL_V1";

    static final class ProposalResult {
        final NormalDuelPresentation.DuelMode mode;
        final String challengeSuffix;
        final DuelSessionService.Snapshot snapshot;

        ProposalResult(
            NormalDuelPresentation.DuelMode mode,
            DuelSessionService.Snapshot snapshot
        ){
            this.mode=Objects.requireNonNull(
                mode,
                "mode"
            );
            this.challengeSuffix=
                NormalDuelPresentation.challengeSuffix(
                    mode
                );
            this.snapshot=Objects.requireNonNull(
                snapshot,
                "snapshot"
            );
        }
    }

    private final World world;
    private final MatchSessionService matches=
        new MatchSessionService();
    private final WorldInstanceService instances=
        new WorldInstanceService();
    private final DuelSessionService duels=
        new DuelSessionService(
            matches,
            instances
        );

    private long challengeSequence=1L;

    LocalLabDuelRuntime(
        World world
    ){
        this.world=Objects.requireNonNull(
            world,
            "world"
        );
    }

    synchronized String requireOnlineTarget(
        String challengerRef,
        String challengedRef
    ){
        String challenger=
            PartyService.requireRef(
                challengerRef
            );
        String challenged=
            PartyService.requireRef(
                challengedRef
            );

        if(challenger.equals(challenged))
            throw new IllegalArgumentException(
                "Duel participants must differ"
            );

        WorldPlayer challengerPlayer=
            world.players().byName(
                challenger
            );
        WorldPlayer challengedPlayer=
            world.players().byName(
                challenged
            );

        if(challengerPlayer==null||
           !challengerPlayer.registered())
            throw new IllegalStateException(
                "Duel challenger not online "+
                challenger
            );

        if(challengedPlayer==null||
           !challengedPlayer.registered())
            throw new IllegalStateException(
                "Duel target not online "+
                challenged
            );

        return challengedPlayer.username();
    }

    synchronized ProposalResult propose(
        String challengerRef,
        String challengedRef,
        NormalDuelPresentation.DuelMode mode
    ){
        String challenger=
            PartyService.requireRef(
                challengerRef
            );
        String challenged=
            requireOnlineTarget(
                challenger,
                challengedRef
            );
        NormalDuelPresentation.DuelMode checkedMode=
            Objects.requireNonNull(
                mode,
                "mode"
            );

        long sequence=challengeSequence;
        DuelSessionService.ChallengeId challengeId=
            DuelSessionService.ChallengeId.of(
                "locallab:duel:g101:"+
                Long.toUnsignedString(
                    sequence
                )
            );

        DuelSessionService.Snapshot proposed=
            duels.propose(
                challengeId,
                challenger,
                challenged,
                duelTypeKey(
                    checkedMode
                ),
                rules(),
                AUTHORITY
            );

        challengeSequence=
            Math.addExact(
                sequence,
                1L
            );

        return new ProposalResult(
            checkedMode,
            proposed
        );
    }

    synchronized DuelSessionService.Snapshot openFor(
        String participantRef
    ){
        return duels.openFor(
            participantRef
        );
    }

    synchronized int size(){
        return duels.size();
    }

    DuelSessionService duels(){
        return duels;
    }

    MatchSessionService matches(){
        return matches;
    }

    WorldInstanceService instances(){
        return instances;
    }

    static String duelTypeKey(
        NormalDuelPresentation.DuelMode mode
    ){
        switch(Objects.requireNonNull(mode,"mode")){
            case STANDARD:
                return "standard";
            case WHIP_ONLY:
                return "whip-only";
            case WHIP_DDS_ONLY:
                return "whip-dds-only";
            default:
                throw new AssertionError(mode);
        }
    }

    private static MatchRules rules(){
        return new MatchRules(
            MatchRules.TeamMode.TEAMS,
            MatchRules.SpellPolicy.UNRESTRICTED,
            MatchRules.PrayerPolicy.UNRESTRICTED,
            MatchRules.RestrictionPolicy.ALLOWED,
            MatchRules.RestrictionPolicy.ALLOWED,
            MatchRules.WinConditionKind.CALLER_RESOLVED,
            MatchRules.NO_SCORE_TARGET,
            "normal-duel-proposal",
            AUTHORITY
        );
    }
}
