package spk.local;

import java.util.*;

/**
 * Exact-current Daily Challenges application layer over
 * DailyChallengeAssignmentService.
 *
 * The client proves a maximum 10-row challenge list plus opaque metadata fields
 * and semantic claim/info actions. Assignment generation, reset cadence,
 * attribution, rewards and persistence remain caller/server authority.
 */
final class DailyChallengeApplicationService {
    static final int MAX_ROWS=10;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final class AssignmentSpec {
        final int clientChallengeId;
        final String challengeKey;
        final String description;
        final String metadataA;
        final String metadataB;
        final ObjectiveDefinition objective;
        final long initialProgress;
        final boolean claimed;
        final String sourceAuthority;

        AssignmentSpec(
            int clientChallengeId,
            String challengeKey,
            String description,
            String metadataA,
            String metadataB,
            ObjectiveDefinition objective,
            long initialProgress,
            boolean claimed,
            String sourceAuthority
        ){
            if(clientChallengeId<0)
                throw new IllegalArgumentException(
                    "clientChallengeId="+
                    clientChallengeId
                );

            this.clientChallengeId=
                clientChallengeId;
            this.challengeKey=
                ObjectiveDefinition
                    .normalizeKey(
                        challengeKey
                    );

            if(description==null||
               description.trim().isEmpty())
                throw new IllegalArgumentException(
                    "description"
                );

            this.description=
                description.trim();
            this.metadataA=
                requireOpaque(
                    metadataA,
                    "metadataA"
                );
            this.metadataB=
                requireOpaque(
                    metadataB,
                    "metadataB"
                );
            this.objective=
                Objects.requireNonNull(
                    objective,
                    "objective"
                );

            if(initialProgress<0)
                throw new IllegalArgumentException(
                    "initialProgress="+
                    initialProgress
                );

            this.initialProgress=
                initialProgress;
            this.claimed=claimed;

            if(sourceAuthority==null||
               sourceAuthority.trim().isEmpty())
                throw new IllegalArgumentException(
                    "sourceAuthority"
                );

            this.sourceAuthority=
                sourceAuthority.trim();

            if(!this.sourceAuthority.equals(
                    objective.sourceAuthority))
                throw new IllegalArgumentException(
                    "Daily Challenge objective authority mismatch "+
                    objective.key
                );
        }

        DailyChallengeAssignmentService
            .AssignmentSpec delegateSpec(){
            return new DailyChallengeAssignmentService
                .AssignmentSpec(
                    clientChallengeId,
                    challengeKey,
                    description,
                    objective,
                    initialProgress,
                    claimed,
                    sourceAuthority
                );
        }

        private static String requireOpaque(
            String value,
            String label
        ){
            if(value==null)
                throw new NullPointerException(
                    label
                );

            return value;
        }
    }

    static final class ChallengeSnapshot {
        final int clientChallengeId;
        final String challengeKey;
        final String description;
        final String metadataA;
        final String metadataB;
        final String objectiveKey;
        final long current;
        final long target;
        final boolean complete;
        final boolean claimed;
        final String sourceAuthority;
        final String presentationAuthority;

        ChallengeSnapshot(
            AssignmentSpec spec,
            DailyChallengeAssignmentService
                .Snapshot delegate
        ){
            this.clientChallengeId=
                delegate.clientChallengeId;
            this.challengeKey=
                delegate.challengeKey;
            this.description=
                spec.description;
            this.metadataA=
                spec.metadataA;
            this.metadataB=
                spec.metadataB;
            this.objectiveKey=
                delegate.objectiveKey;
            this.current=
                delegate.progress;
            this.target=
                delegate.goal;
            this.complete=
                delegate.complete;
            this.claimed=
                delegate.claimed;
            this.sourceAuthority=
                delegate.sourceAuthority;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }
    }

    static final class PlayerSnapshot {
        final String playerRef;
        final List<ChallengeSnapshot> challenges;

        PlayerSnapshot(
            String playerRef,
            Collection<ChallengeSnapshot>
                challenges
        ){
            this.playerRef=playerRef;
            this.challenges=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        challenges
                    )
                );
        }

        ChallengeSnapshot challenge(
            String challengeKey
        ){
            String key=
                ObjectiveDefinition
                    .normalizeKey(
                        challengeKey
                    );

            for(ChallengeSnapshot challenge:
                    challenges)
                if(challenge.challengeKey
                        .equals(key))
                    return challenge;

            return null;
        }

        int completedUnclaimedCount(){
            int count=0;

            for(ChallengeSnapshot challenge:
                    challenges)
                if(challenge.complete&&
                   !challenge.claimed)
                    count++;

            return count;
        }
    }

    static final class ProgressResult {
        final boolean completedNow;
        final ChallengeSnapshot challenge;

        ProgressResult(
            boolean completedNow,
            ChallengeSnapshot challenge
        ){
            this.completedNow=
                completedNow;
            this.challenge=
                Objects.requireNonNull(
                    challenge,
                    "challenge"
                );
        }
    }

    static final class ClaimResult {
        final boolean changed;
        final ChallengeSnapshot challenge;

        ClaimResult(
            boolean changed,
            ChallengeSnapshot challenge
        ){
            this.changed=changed;
            this.challenge=
                Objects.requireNonNull(
                    challenge,
                    "challenge"
                );
        }
    }

    private static final class PlayerState {
        final String playerRef;
        final DailyChallengeAssignmentService
            assignments;
        final LinkedHashMap<String,AssignmentSpec>
            specsByKey;

        PlayerState(
            String playerRef,
            DailyChallengeAssignmentService
                assignments,
            LinkedHashMap<String,AssignmentSpec>
                specsByKey
        ){
            this.playerRef=playerRef;
            this.assignments=assignments;
            this.specsByKey=
                specsByKey;
        }
    }

    private final LinkedHashMap<String,PlayerState>
        players=
            new LinkedHashMap<>();

    synchronized PlayerSnapshot replaceAll(
        String playerRef,
        Collection<AssignmentSpec> specs
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        Objects.requireNonNull(
            specs,
            "specs"
        );

        if(specs.size()>MAX_ROWS)
            throw new IllegalArgumentException(
                "Daily Challenge rows="+
                specs.size()+
                " max="+MAX_ROWS
            );

        ArrayList<AssignmentSpec>
            checked=
                new ArrayList<>();

        LinkedHashMap<String,AssignmentSpec>
            nextSpecs=
                new LinkedHashMap<>();

        for(AssignmentSpec spec:specs){
            AssignmentSpec value=
                Objects.requireNonNull(
                    spec,
                    "assignment spec"
                );

            if(nextSpecs.put(
                    value.challengeKey,
                    value)!=null)
                throw new IllegalStateException(
                    "duplicate Daily Challenge key "+
                    value.challengeKey
                );

            checked.add(value);
        }

        DailyChallengeAssignmentService
            nextAssignments=
                new DailyChallengeAssignmentService();

        ArrayList<
            DailyChallengeAssignmentService
                .AssignmentSpec
        > delegateSpecs=
            new ArrayList<>();

        for(AssignmentSpec spec:checked)
            delegateSpecs.add(
                spec.delegateSpec()
            );

        // The delegate validates duplicate client/objective identities and
        // all objective-progress restore constraints before live player state
        // is swapped.
        nextAssignments.replaceAll(
            delegateSpecs
        );

        PlayerState next=
            new PlayerState(
                player,
                nextAssignments,
                nextSpecs
            );

        players.put(
            player,
            next
        );

        return snapshotOf(next);
    }

    synchronized ProgressResult
        recordValidatedProgress(
            String playerRef,
            String challengeKey,
            long amount
        ){
        if(amount<=0)
            throw new IllegalArgumentException(
                "amount="+amount
            );

        PlayerState state=
            requirePlayer(
                playerRef
            );

        AssignmentSpec spec=
            requireSpec(
                state,
                challengeKey
            );

        ObjectiveProgressService.ProgressResult
            result=
                state.assignments.advance(
                    spec.clientChallengeId,
                    amount
                );

        return new ProgressResult(
            result.completedNow,
            challengeSnapshot(
                state,
                spec
            )
        );
    }

    synchronized ChallengeSnapshot info(
        String playerRef,
        String challengeKey
    ){
        PlayerState state=
            requirePlayer(
                playerRef
            );

        return challengeSnapshot(
            state,
            requireSpec(
                state,
                challengeKey
            )
        );
    }

    /**
     * Call only after reward settlement succeeded outside this service.
     */
    synchronized ClaimResult
        confirmRewardSettledAndMarkClaimed(
            String playerRef,
            String challengeKey
        ){
        PlayerState state=
            requirePlayer(
                playerRef
            );

        AssignmentSpec spec=
            requireSpec(
                state,
                challengeKey
            );

        boolean changed=
            state.assignments.markClaimed(
                spec.clientChallengeId
            );

        return new ClaimResult(
            changed,
            challengeSnapshot(
                state,
                spec
            )
        );
    }

    synchronized PlayerSnapshot get(
        String playerRef
    ){
        PlayerState state=
            players.get(
                normalizePlayer(
                    playerRef
                )
            );

        return state==null
            ?null
            :snapshotOf(state);
    }

    synchronized int playerCount(){
        return players.size();
    }

    private PlayerSnapshot snapshotOf(
        PlayerState state
    ){
        ArrayList<ChallengeSnapshot>
            out=
                new ArrayList<>();

        for(
            DailyChallengeAssignmentService
                .Snapshot delegate:
            state.assignments.snapshot()
        ){
            AssignmentSpec spec=
                state.specsByKey.get(
                    delegate.challengeKey
                );

            if(spec==null)
                throw new IllegalStateException(
                    "Daily Challenge metadata missing "+
                    delegate.challengeKey
                );

            if(!spec.sourceAuthority.equals(
                    delegate.sourceAuthority))
                throw new IllegalStateException(
                    "Daily Challenge authority drift "+
                    delegate.challengeKey
                );

            out.add(
                new ChallengeSnapshot(
                    spec,
                    delegate
                )
            );
        }

        return new PlayerSnapshot(
            state.playerRef,
            out
        );
    }

    private ChallengeSnapshot challengeSnapshot(
        PlayerState state,
        AssignmentSpec spec
    ){
        DailyChallengeAssignmentService.Snapshot
            delegate=
                state.assignments
                    .getByChallengeKey(
                        spec.challengeKey
                    );

        if(delegate==null)
            throw new IllegalStateException(
                "Daily Challenge assignment disappeared "+
                spec.challengeKey
            );

        if(!spec.sourceAuthority.equals(
                delegate.sourceAuthority))
            throw new IllegalStateException(
                "Daily Challenge authority drift "+
                spec.challengeKey
            );

        return new ChallengeSnapshot(
            spec,
            delegate
        );
    }

    private PlayerState requirePlayer(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        PlayerState state=
            players.get(player);

        if(state==null)
            throw new IllegalArgumentException(
                "unknown Daily Challenge player "+
                player
            );

        return state;
    }

    private static AssignmentSpec requireSpec(
        PlayerState state,
        String challengeKey
    ){
        String key=
            ObjectiveDefinition
                .normalizeKey(
                    challengeKey
                );

        AssignmentSpec spec=
            state.specsByKey.get(key);

        if(spec==null)
            throw new IllegalArgumentException(
                "unknown Daily Challenge "+
                key+
                " player="+
                state.playerRef
            );

        return spec;
    }

    private static String normalizePlayer(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "playerRef"
            );

        String normalized=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "playerRef blank"
            );

        return normalized;
    }
}
