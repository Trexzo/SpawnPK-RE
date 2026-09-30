package spk.local;

import java.util.*;

/**
 * Concrete Clan Wars runtime composition.
 *
 * Existing ClanWarDefinition/ClanWarChallenge own exact-current semantic
 * challenge/configuration state. This service composes an accepted challenge
 * into the reusable MatchSession + WorldInstance foundations.
 *
 * Admission, enforcement, scoring attribution, winner policy and rewards remain
 * caller-owned.
 */
final class ClanWarSessionService {
    enum Lifecycle {
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    @FunctionalInterface
    interface MatchRulesResolver {
        MatchRules resolve(
            ClanWarDefinition definition
        );
    }

    static final class Snapshot {
        final ClanWarChallenge.ChallengeId challengeId;
        final ClanAggregate.ClanId challengerClanId;
        final ClanAggregate.ClanId challengedClanId;
        final ClanWarDefinition definition;
        final MatchId matchId;
        final WorldInstanceId instanceId;
        final MatchTeamId challengerTeamId;
        final MatchTeamId challengedTeamId;
        final List<String> challengerParticipants;
        final List<String> challengedParticipants;
        final Lifecycle lifecycle;
        final String policyAuthority;

        Snapshot(
            Entry entry
        ){
            this.challengeId=
                entry.challengeId;
            this.challengerClanId=
                entry.challengerClanId;
            this.challengedClanId=
                entry.challengedClanId;
            this.definition=
                entry.definition;
            this.matchId=entry.matchId;
            this.instanceId=
                entry.instanceId;
            this.challengerTeamId=
                entry.challengerTeamId;
            this.challengedTeamId=
                entry.challengedTeamId;
            this.challengerParticipants=
                immutable(
                    entry.challengerParticipants
                );
            this.challengedParticipants=
                immutable(
                    entry.challengedParticipants
                );
            this.lifecycle=
                entry.lifecycle;
            this.policyAuthority=
                entry.policyAuthority;
        }

        boolean terminal(){
            return lifecycle==
                    Lifecycle.COMPLETED||
                lifecycle==
                    Lifecycle.CANCELLED;
        }

        MatchTeamId teamForClan(
            ClanAggregate.ClanId clanId
        ){
            ClanAggregate.ClanId checked=
                Objects.requireNonNull(
                    clanId,
                    "clanId"
                );

            if(challengerClanId.equals(
                    checked))
                return challengerTeamId;

            if(challengedClanId.equals(
                    checked))
                return challengedTeamId;

            return null;
        }
    }

    private static final class Entry {
        final ClanWarChallenge.ChallengeId challengeId;
        final ClanAggregate.ClanId challengerClanId;
        final ClanAggregate.ClanId challengedClanId;
        final ClanWarDefinition definition;
        final MatchId matchId;
        final WorldInstanceId instanceId;
        final MatchTeamId challengerTeamId=
            MatchTeamId.of(
                "clan_wars:challenger"
            );
        final MatchTeamId challengedTeamId=
            MatchTeamId.of(
                "clan_wars:challenged"
            );
        final LinkedHashSet<String>
            challengerParticipants=
                new LinkedHashSet<>();
        final LinkedHashSet<String>
            challengedParticipants=
                new LinkedHashSet<>();
        final String policyAuthority;

        Lifecycle lifecycle=
            Lifecycle.ACTIVE;
        MatchSessionService.CompositionLease
            childLease;

        Entry(
            ClanWarChallenge.Snapshot challenge,
            ClanAggregate.Snapshot challenger,
            ClanAggregate.Snapshot challenged,
            MatchId matchId,
            WorldInstanceId instanceId,
            String policyAuthority
        ){
            this.challengeId=
                challenge.id();
            this.challengerClanId=
                challenger.id();
            this.challengedClanId=
                challenged.id();
            this.definition=
                challenge.definition();
            this.matchId=matchId;
            this.instanceId=instanceId;
            this.policyAuthority=
                policyAuthority;

            for(ClanAggregate.MemberId member:
                    challenger.members().keySet())
                challengerParticipants.add(
                    PartyService.requireRef(
                        member.value()
                    )
                );

            for(ClanAggregate.MemberId member:
                    challenged.members().keySet())
                challengedParticipants.add(
                    PartyService.requireRef(
                        member.value()
                    )
                );
        }

        Snapshot snapshot(){
            return new Snapshot(this);
        }
    }

    private final MatchSessionService matches;
    private final WorldInstanceService instances;
    private final MatchRulesResolver rulesResolver;
    private final String policyAuthority;

    private final LinkedHashMap<
        ClanWarChallenge.ChallengeId,
        Entry
    > sessions=
        new LinkedHashMap<>();

    ClanWarSessionService(
        MatchSessionService matches,
        WorldInstanceService instances,
        MatchRulesResolver rulesResolver,
        String policyAuthority
    ){
        this.matches=
            Objects.requireNonNull(
                matches,
                "matches"
            );
        this.instances=
            Objects.requireNonNull(
                instances,
                "instances"
            );
        this.rulesResolver=
            Objects.requireNonNull(
                rulesResolver,
                "rulesResolver"
            );
        this.policyAuthority=
            requireAuthority(
                policyAuthority
            );
    }

    synchronized Snapshot startAccepted(
        ClanWarChallenge challenge,
        ClanAggregate.Snapshot challengerClan,
        ClanAggregate.Snapshot challengedClan,
        MatchId matchId,
        WorldInstanceId instanceId
    ){
        ClanWarChallenge.Snapshot
            challengeSnapshot=
                Objects.requireNonNull(
                    challenge,
                    "challenge"
                ).snapshot();

        if(challengeSnapshot.state()!=
                ClanWarChallenge.State.ACCEPTED)
            throw new IllegalStateException(
                "Clan War challenge not accepted "+
                challengeSnapshot.id()+
                " state="+
                challengeSnapshot.state()
            );

        ClanAggregate.Snapshot challenger=
            Objects.requireNonNull(
                challengerClan,
                "challengerClan"
            );
        ClanAggregate.Snapshot challenged=
            Objects.requireNonNull(
                challengedClan,
                "challengedClan"
            );

        if(!challengeSnapshot
                .challenger()
                .equals(
                    challenger.id()))
            throw new IllegalArgumentException(
                "challenger clan snapshot mismatch"
            );

        if(!challengeSnapshot
                .challenged()
                .equals(
                    challenged.id()))
            throw new IllegalArgumentException(
                "challenged clan snapshot mismatch"
            );

        if(sessions.containsKey(
                challengeSnapshot.id()))
            throw new IllegalStateException(
                "Clan War session already exists "+
                challengeSnapshot.id()
            );

        MatchId checkedMatchId=
            Objects.requireNonNull(
                matchId,
                "matchId"
            );
        WorldInstanceId checkedInstanceId=
            Objects.requireNonNull(
                instanceId,
                "instanceId"
            );

        Entry entry=
            new Entry(
                challengeSnapshot,
                challenger,
                challenged,
                checkedMatchId,
                checkedInstanceId,
                policyAuthority
            );

        if(entry.challengerParticipants
                .isEmpty()||
           entry.challengedParticipants
                .isEmpty())
            throw new IllegalStateException(
                "Clan War requires both clans to have participants"
            );

        HashSet<String> overlap=
            new HashSet<>(
                entry.challengerParticipants
            );

        overlap.retainAll(
            entry.challengedParticipants
        );

        if(!overlap.isEmpty())
            throw new IllegalStateException(
                "Clan War participant overlap "+
                overlap
            );

        MatchRules rules=
            Objects.requireNonNull(
                rulesResolver.resolve(
                    entry.definition
                ),
                "resolved MatchRules"
            );

        if(rules.teamMode!=
                MatchRules.TeamMode.TEAMS)
            throw new IllegalArgumentException(
                "Clan War MatchRules must use TEAMS"
            );

        if(!policyAuthority.equals(
                rules.sourceAuthority))
            throw new IllegalArgumentException(
                "Clan War MatchRules authority "+
                rules.sourceAuthority+
                " does not match gameplay policy authority "+
                policyAuthority
            );

        withCompositionOwnership(
            ()->{
                if(matches.get(
                        checkedMatchId)!=null)
                    throw new IllegalStateException(
                        "Clan War match id already exists "+
                        checkedMatchId
                    );

                if(instances.get(
                        checkedInstanceId)!=null)
                    throw new IllegalStateException(
                        "Clan War instance id already exists "+
                        checkedInstanceId
                    );

            matches.create(
                entry.matchId,
                rules
            );
            matches.addTeam(
                entry.matchId,
                entry.challengerTeamId
            );
            matches.addTeam(
                entry.matchId,
                entry.challengedTeamId
            );
    
            for(String participant:
                    entry.challengerParticipants)
                matches.join(
                    entry.matchId,
                    entry.challengerTeamId,
                    participant
                );
    
            for(String participant:
                    entry.challengedParticipants)
                matches.join(
                    entry.matchId,
                    entry.challengedTeamId,
                    participant
                );
    
            instances.create(
                entry.instanceId,
                entry.matchId.toString(),
                policyAuthority
            );
    
            for(String participant:
                    entry.challengerParticipants)
                instances.attach(
                    entry.instanceId,
                    participant
                );
    
            for(String participant:
                    entry.challengedParticipants)
                instances.attach(
                    entry.instanceId,
                    participant
                );
    
            matches.attachInstance(
                entry.matchId,
                entry.instanceId
            );
            matches.markReady(
                entry.matchId
            );
            instances.activate(
                entry.instanceId
            );
            matches.activate(
                entry.matchId
            );

            entry.childLease=
                matches.acquireWorldInstanceCompositionLease(
                    instances,
                    entry.matchId,
                    entry.instanceId,
                    childLeaseLabel(
                        entry.matchId
                    )
                );
    
    
            }
        );

        sessions.put(
            entry.challengeId,
            entry
        );

        return entry.snapshot();
    }

    synchronized Snapshot adjustClanScore(
        ClanWarChallenge.ChallengeId challengeId,
        ClanAggregate.ClanId clanId,
        String counterKey,
        long delta
    ){
        Entry entry=
            requireActive(
                challengeId
            );

        MatchTeamId teamId=
            teamForClan(
                entry,
                clanId
            );

        matches.adjustTeamScoreOwned(
            entry.matchId,
            teamId,
            counterKey,
            delta,
            requireChildLease(
                entry
            )
        );

        return entry.snapshot();
    }

    synchronized Snapshot complete(
        ClanWarChallenge.ChallengeId challengeId,
        ClanAggregate.ClanId winnerClanId,
        String outcomeKey,
        String decisionAuthority
    ){
        Entry entry=
            requireActive(
                challengeId
            );

        MatchTeamId winner=
            teamForClan(
                entry,
                winnerClanId
            );

        MatchSession.Result result=
            new MatchSession.Result(
                outcomeKey,
                winner,
                requireAuthority(
                    decisionAuthority
                )
            );

        withCompositionOwnership(
            ()->{
                preflightOwnedInstance(
                    entry
                );

                MatchSessionService.CompositionLease lease=
                    requireChildLease(
                        entry
                    );

                matches.completeOwned(
                    entry.matchId,
                    result,
                    lease
                );
                closeOwnedInstance(
                    entry,
                    lease
                );

                entry.lifecycle=
                    Lifecycle.COMPLETED;

                matches.releaseWorldInstanceCompositionLease(
                    instances,
                    entry.matchId,
                    entry.instanceId,
                    lease
                );
                entry.childLease=null;
            }
        );

        return entry.snapshot();
    }

    synchronized Snapshot cancel(
        ClanWarChallenge.ChallengeId challengeId,
        String reasonKey
    ){
        Entry entry=
            requireActive(
                challengeId
            );
        String reason=
            MatchRules.normalizeKey(
                reasonKey,
                "reasonKey"
            );

        withCompositionOwnership(
            ()->{
                preflightOwnedInstance(
                    entry
                );

                MatchSessionService.CompositionLease lease=
                    requireChildLease(
                        entry
                    );

                matches.cancelOwned(
                    entry.matchId,
                    reason,
                    lease
                );
                closeOwnedInstance(
                    entry,
                    lease
                );

                entry.lifecycle=
                    Lifecycle.CANCELLED;

                matches.releaseWorldInstanceCompositionLease(
                    instances,
                    entry.matchId,
                    entry.instanceId,
                    lease
                );
                entry.childLease=null;
            }
        );

        return entry.snapshot();
    }

    synchronized Snapshot get(
        ClanWarChallenge.ChallengeId challengeId
    ){
        Entry entry=
            sessions.get(
                Objects.requireNonNull(
                    challengeId,
                    "challengeId"
                )
            );

        return entry==null
            ?null
            :entry.snapshot();
    }

    synchronized int size(){
        return sessions.size();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Entry> ordered=
            new ArrayList<>(
                sessions.values()
            );

        ordered.sort(
            Comparator.comparing(
                value->
                    value.challengeId
                        .value()
            )
        );

        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Entry entry:ordered)
            out.add(
                entry.snapshot()
            );

        return Collections.unmodifiableList(
            out
        );
    }

    private void withCompositionOwnership(
        MatchSessionService.MatchInstanceCompositionAction action
    ){
        try{
            matches.withWorldInstanceCompositionOwnership(
                instances,
                action
            );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "unexpected Clan War composition ownership failure",
                failure
            );
        }
    }

    private Entry requireActive(
        ClanWarChallenge.ChallengeId challengeId
    ){
        Entry entry=
            sessions.get(
                Objects.requireNonNull(
                    challengeId,
                    "challengeId"
                )
            );

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown Clan War session "+
                challengeId
            );

        if(entry.lifecycle!=
                Lifecycle.ACTIVE)
            throw new IllegalStateException(
                "Clan War session terminal "+
                challengeId+
                " state="+entry.lifecycle
            );

        return entry;
    }

    private static MatchTeamId teamForClan(
        Entry entry,
        ClanAggregate.ClanId clanId
    ){
        ClanAggregate.ClanId checked=
            Objects.requireNonNull(
                clanId,
                "clanId"
            );

        if(entry.challengerClanId
                .equals(checked))
            return entry.challengerTeamId;

        if(entry.challengedClanId
                .equals(checked))
            return entry.challengedTeamId;

        throw new IllegalArgumentException(
            "clan not part of Clan War "+
            checked
        );
    }

    private static String childLeaseLabel(
        MatchId matchId
    ){
        return "clan-war:"+
            Objects.requireNonNull(
                matchId,
                "matchId"
            );
    }

    private static MatchSessionService.CompositionLease
        requireChildLease(
            Entry entry
        ){
        if(entry.childLease==null)
            throw new IllegalStateException(
                "Clan War child lease missing "+
                entry.matchId
            );

        return entry.childLease;
    }

    private void preflightOwnedInstance(
        Entry entry
    ){
        WorldInstanceService.Snapshot
            instance=
                instances.get(
                    entry.instanceId
                );

        if(instance==null||
           instance.lifecycle!=
                WorldInstanceService
                    .Lifecycle.ACTIVE)
            throw new IllegalStateException(
                "Clan War instance not active "+
                entry.instanceId
            );

        LinkedHashSet<String> expected=
            new LinkedHashSet<>(
                entry.challengerParticipants
            );

        expected.addAll(
            entry.challengedParticipants
        );

        LinkedHashSet<String> actual=
            new LinkedHashSet<>(
                instance.participants
            );

        if(!expected.equals(actual))
            throw new IllegalStateException(
                "Clan War instance participant drift expected="+
                expected+
                " actual="+actual
            );
    }

    private void closeOwnedInstance(
        Entry entry,
        MatchSessionService.CompositionLease lease
    ){
        instances.beginClosingOwned(
            entry.instanceId,
            lease
        );

        for(String participant:
                entry.challengerParticipants)
            instances.detachOwned(
                entry.instanceId,
                participant,
                lease
            );

        for(String participant:
                entry.challengedParticipants)
            instances.detachOwned(
                entry.instanceId,
                participant,
                lease
            );

        instances.closeOwned(
            entry.instanceId,
            lease
        );
    }

    private static List<String> immutable(
        Collection<String> values
    ){
        return Collections.unmodifiableList(
            new ArrayList<>(
                values
            )
        );
    }

    private static String requireAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "authority"
            );

        String normalized=
            value.trim();

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "authority blank"
            );

        return normalized;
    }
}
