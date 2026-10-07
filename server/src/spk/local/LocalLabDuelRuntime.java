package spk.local;

import java.util.*;

/**
 * World-owned LocalLab binding from exact normal-Duel mode selection to the
 * reusable protocol-independent DuelSessionService proposal lifecycle.
 *
 * G10.1 binds proposal state. G10.2 adds explicit challenged-player accept
 * and reusable MatchSession/WorldInstance start. G10.4 binds the canonical
 * verified PvP death identity to ACTIVE Duel completion. Stake/escrow, weapon
 * restrictions, arena behavior, rewards and original SpawnPK winner policy
 * remain external authority.
 */
final class LocalLabDuelRuntime {
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G101_NORMAL_DUEL_PROPOSAL_V1";
    static final String PVP_DEATH_AUTHORITY=
        "LOCAL_LAB_CANONICAL_PVP_DEATH";

    static final class PvpDeathResult {
        final boolean duelMatch;
        final boolean completedNow;
        final DuelSessionService.ChallengeId challengeId;
        final DuelSessionService.Snapshot snapshot;

        PvpDeathResult(
            boolean duelMatch,
            boolean completedNow,
            DuelSessionService.ChallengeId challengeId,
            DuelSessionService.Snapshot snapshot
        ){
            this.duelMatch=duelMatch;
            this.completedNow=completedNow;
            this.challengeId=challengeId;
            this.snapshot=snapshot;
        }
    }

    static final class StartResult {
        final DuelSessionService.Snapshot snapshot;
        final MatchSession match;
        final WorldInstanceService.Snapshot instance;

        StartResult(
            DuelSessionService.Snapshot snapshot,
            MatchSession match,
            WorldInstanceService.Snapshot instance
        ){
            this.snapshot=Objects.requireNonNull(
                snapshot,
                "snapshot"
            );
            this.match=Objects.requireNonNull(
                match,
                "match"
            );
            this.instance=Objects.requireNonNull(
                instance,
                "instance"
            );
        }
    }

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
    private long matchSequence=1L;
    private final LinkedHashMap<String,PvpDeathResult>
        pvpDeathByIdentity=
            new LinkedHashMap<>();

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

        synchronized(world.competitiveAdmissionLock()){
            if(world.localTournament()
                    .participantInActiveMatch(
                        challenger
                    )||
               world.localTournament()
                    .participantInActiveMatch(
                        challenged
                    ))
                throw new IllegalStateException(
                    "Duel proposal blocked by active Tournament match participants="+
                    challenger+","+challenged
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

            /*
             * requireOnlineTarget() is intentionally repeated after mutation.
             * If unregister crossed the first check while this runtime owned
             * its monitor, retire the just-created proposal before exposing it.
             */
            try{
                requireOnlineTarget(
                    challenger,
                    challenged
                );
            }catch(RuntimeException offline){
                duels.cancelOpen(
                    challengeId,
                    challenger
                );
                throw offline;
            }

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
    }

    synchronized DuelSessionService.Snapshot cancelOpen(
        String participantRef
    ){
        String participant=
            PartyService.requireRef(
                participantRef
            );

        DuelSessionService.Snapshot open=
            duels.openFor(
                participant
            );

        if(open==null||
           (open.state!=
                DuelSessionService.State.PROPOSED&&
            open.state!=
                DuelSessionService.State.ACCEPTED))
            throw new IllegalStateException(
                "G10.8 requires open PROPOSED/ACCEPTED Duel for "+
                participant
            );

        return duels.cancelOpen(
            open.challengeId,
            participant
        );
    }

    synchronized DuelSessionService.Snapshot decline(
        String challengedRef
    ){
        String challenged=
            PartyService.requireRef(
                challengedRef
            );

        DuelSessionService.Snapshot open=
            duels.openFor(
                challenged
            );

        if(open==null||
           open.state!=
                DuelSessionService.State.PROPOSED)
            throw new IllegalStateException(
                "G10.7 requires open PROPOSED Duel for "+
                challenged
            );

        if(!open.challengedRef.equals(
                challenged))
            throw new IllegalArgumentException(
                "only challenged player may decline Duel"
            );

        return duels.decline(
            open.challengeId,
            challenged
        );
    }

    synchronized StartResult acceptAndStart(
        String challengedRef
    ){
        String challenged=
            PartyService.requireRef(
                challengedRef
            );

        DuelSessionService.Snapshot open=
            duels.openFor(
                challenged
            );

        if(open==null||
           open.state!=
                DuelSessionService.State.PROPOSED)
            throw new IllegalStateException(
                "G10.2 requires open PROPOSED Duel for "+
                challenged
            );

        if(!open.challengedRef.equals(
                challenged))
            throw new IllegalArgumentException(
                "only challenged player may accept Duel"
            );

        duels.accept(
            open.challengeId,
            challenged
        );

        long sequence=matchSequence;
        MatchId matchId=
            MatchId.of(
                "locallab:duel:g102:match:"+
                Long.toUnsignedString(
                    sequence
                )
            );
        WorldInstanceId instanceId=
            WorldInstanceId.of(
                "locallab:duel:g102:instance:"+
                Long.toUnsignedString(
                    sequence
                )
            );

        DuelSessionService.Snapshot active=
            duels.startAccepted(
                open.challengeId,
                matchId,
                instanceId
            );

        matchSequence=
            Math.addExact(
                sequence,
                1L
            );

        MatchSession match=
            matches.get(
                active.matchId
            );
        WorldInstanceService.Snapshot instance=
            instances.get(
                active.instanceId
            );

        if(match==null||
           instance==null)
            throw new IllegalStateException(
                "G10.2 Duel child composition missing"
            );

        return new StartResult(
            active,
            match,
            instance
        );
    }

    synchronized boolean claimsCanonicalPvpDeath(
        String attackerRef,
        String victimRef,
        long deathSequence
    ){
        String attacker=
            PartyService.requireRef(
                attackerRef
            );
        String victim=
            PartyService.requireRef(
                victimRef
            );

        if(attacker.equals(victim))
            throw new IllegalArgumentException(
                "Duel PvP attacker/victim identical"
            );

        String deathKey=
            pvpDeathKey(
                attacker,
                victim,
                deathSequence
            );

        if(pvpDeathByIdentity.containsKey(
                deathKey))
            return true;

        DuelSessionService.Snapshot active=
            duels.openFor(
                attacker
            );

        return active!=null&&
            active.state==
                DuelSessionService.State.ACTIVE&&
            active.participant(victim);
    }

    synchronized PvpDeathResult recordCanonicalPvpDeath(
        String attackerRef,
        String victimRef,
        long deathSequence
    ){
        String attacker=
            PartyService.requireRef(
                attackerRef
            );
        String victim=
            PartyService.requireRef(
                victimRef
            );

        if(attacker.equals(victim))
            throw new IllegalArgumentException(
                "Duel PvP attacker/victim identical"
            );

        String deathKey=
            pvpDeathKey(
                attacker,
                victim,
                deathSequence
            );

        PvpDeathResult existing=
            pvpDeathByIdentity.get(
                deathKey
            );
        if(existing!=null)
            return existing;

        DuelSessionService.Snapshot active=
            duels.openFor(
                attacker
            );

        if(active==null||
           active.state!=
                DuelSessionService.State.ACTIVE||
           !active.participant(victim))
            return new PvpDeathResult(
                false,
                false,
                null,
                active
            );

        DuelSessionService.Snapshot completed=
            duels.complete(
                active.challengeId,
                attacker,
                "canonical-pvp-death",
                PVP_DEATH_AUTHORITY
            );

        PvpDeathResult result=
            new PvpDeathResult(
                true,
                true,
                active.challengeId,
                completed
            );

        pvpDeathByIdentity.put(
            deathKey,
            result
        );

        return result;
    }

    synchronized void retireCanonicalPvpDeath(
        String attackerRef,
        String victimRef,
        long deathSequence
    ){
        String attacker=
            PartyService.requireRef(
                attackerRef
            );
        String victim=
            PartyService.requireRef(
                victimRef
            );

        pvpDeathByIdentity.remove(
            pvpDeathKey(
                attacker,
                victim,
                deathSequence
            )
        );
    }

    synchronized int pvpDeathDedupeCount(){
        return pvpDeathByIdentity.size();
    }

    private static String pvpDeathKey(
        String attacker,
        String victim,
        long deathSequence
    ){
        if(deathSequence<=0L)
            throw new IllegalArgumentException(
                "deathSequence="+deathSequence
            );

        return attacker+"|"+
            victim+"|"+
            Long.toUnsignedString(
                deathSequence
            );
    }

    synchronized DuelSessionService.Snapshot
        cancelForUnregister(
            WorldPlayer player,
            long registeredGeneration
        ){
        WorldPlayer checkedPlayer=
            Objects.requireNonNull(
                player,
                "player"
            );
        if(registeredGeneration<=0L)
            throw new IllegalArgumentException(
                "registeredGeneration="+
                registeredGeneration
            );

        String participant=
            PartyService.requireRef(
                checkedPlayer.username()
            );

        DuelSessionService.Snapshot open=
            duels.openFor(
                participant
            );

        if(open==null)
            return null;

        switch(open.state){
            case PROPOSED:
            case ACCEPTED:
                return duels.cancelOpen(
                    open.challengeId,
                    participant
                );

            case ACTIVE:
                /*
                 * G10.4 authority predates disconnect cleanup: once this exact
                 * participant has already lethally killed the Duel opponent,
                 * the captured canonical death identity owns terminalization.
                 * Do not let a later attacker disconnect erase that result.
                 */
                if(hasPendingCanonicalAttackerDeath(
                        checkedPlayer,
                        registeredGeneration,
                        open
                    ))
                    return open;

                return duels.cancelActive(
                    open.challengeId,
                    "participant-disconnected"
                );

            case DECLINED:
            case CANCELLED:
            case COMPLETED:
                throw new IllegalStateException(
                    "terminal Duel retained participant index "+
                    participant+
                    " state="+open.state
                );

            default:
                throw new AssertionError(
                    open.state
                );
        }
    }

    private boolean hasPendingCanonicalAttackerDeath(
        WorldPlayer unregisteringPlayer,
        long registeredGeneration,
        DuelSessionService.Snapshot open
    ){
        String participant=
            PartyService.requireRef(
                unregisteringPlayer.username()
            );

        final String opponent;
        if(participant.equals(
                open.challengerRef))
            opponent=open.challengedRef;
        else if(participant.equals(
                open.challengedRef))
            opponent=open.challengerRef;
        else
            throw new IllegalStateException(
                "Duel participant index drift "+
                participant
            );

        WorldPlayer opponentPlayer=
            world.players().byName(
                opponent
            );

        if(opponentPlayer==null||
           !opponentPlayer.lifecycle().dead())
            return false;

        PlayerLifecycleState.DeathAttribution attribution=
            opponentPlayer.lifecycle()
                .deathAttribution();

        return attribution!=null&&
            "PLAYER_PVP".equals(
                attribution.context
            )&&
            unregisteringPlayer.id().equals(
                attribution.attackerId
            )&&
            attribution.attackerGeneration==
                registeredGeneration&&
            participant.equalsIgnoreCase(
                attribution.attackerUsername
            );
    }

    boolean participantHasOpenDuel(
        String participantRef
    ){
        return duels.openFor(
            participantRef
        )!=null;
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
