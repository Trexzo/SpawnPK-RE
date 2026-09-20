package spk.local;

import java.util.*;

/**
 * Protocol-independent Daily Challenge assignment layer over
 * ObjectiveProgressService.
 *
 * The exact client challenge id is retained only as the adapter identity used by
 * claimchallenge/infochallenge. Assignment generation, reset schedules, rewards,
 * attribution and persistence remain external server policy.
 */
final class DailyChallengeAssignmentService {
    static final class AssignmentSpec {
        final int clientChallengeId;
        final String challengeKey;
        final String label;
        final ObjectiveDefinition objective;
        final long initialProgress;
        final boolean claimed;
        final String sourceAuthority;

        AssignmentSpec(
            int clientChallengeId,
            String challengeKey,
            String label,
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

            if(label==null||
               label.trim().isEmpty())
                throw new IllegalArgumentException(
                    "label"
                );

            this.label=label;
            this.objective=
                Objects.requireNonNull(
                    objective,
                    "objective"
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
        }
    }

    static final class Snapshot {
        final int clientChallengeId;
        final String challengeKey;
        final String label;
        final String objectiveKey;
        final long progress;
        final long goal;
        final boolean complete;
        final boolean claimed;
        final String sourceAuthority;

        Snapshot(
            Assignment assignment,
            ObjectiveProgressService.Snapshot progress
        ){
            this.clientChallengeId=
                assignment.clientChallengeId;
            this.challengeKey=
                assignment.challengeKey;
            this.label=
                assignment.label;
            this.objectiveKey=
                assignment.objectiveKey;
            this.progress=
                progress.progress;
            this.goal=
                progress.goal;
            this.complete=
                progress.complete;
            this.claimed=
                progress.claimed;
            this.sourceAuthority=
                assignment.sourceAuthority;
        }

        @Override public String toString(){
            return "DailyChallengeSnapshot{"+
                "clientChallengeId="+
                    clientChallengeId+
                ",challengeKey="+
                    challengeKey+
                ",objectiveKey="+
                    objectiveKey+
                ",progress="+progress+
                ",goal="+goal+
                ",complete="+complete+
                ",claimed="+claimed+
                ",sourceAuthority="+
                    sourceAuthority+
                "}";
        }
    }

    private static final class Assignment {
        final int clientChallengeId;
        final String challengeKey;
        final String label;
        final String objectiveKey;
        final String sourceAuthority;

        Assignment(
            AssignmentSpec spec
        ){
            this.clientChallengeId=
                spec.clientChallengeId;
            this.challengeKey=
                spec.challengeKey;
            this.label=
                spec.label;
            this.objectiveKey=
                spec.objective.key;
            this.sourceAuthority=
                spec.sourceAuthority;
        }
    }

    private ObjectiveProgressService progress=
        new ObjectiveProgressService();

    private LinkedHashMap<Integer,Assignment>
        byClientId=
            new LinkedHashMap<>();

    private HashMap<String,Integer>
        byChallengeKey=
            new HashMap<>();

    private HashMap<String,Integer>
        byObjectiveKey=
            new HashMap<>();

    synchronized int size(){
        return byClientId.size();
    }

    synchronized Snapshot assign(
        AssignmentSpec spec
    ){
        Objects.requireNonNull(
            spec,
            "spec"
        );

        if(byClientId.containsKey(
                spec.clientChallengeId))
            throw new IllegalStateException(
                "duplicate client challenge id "+
                spec.clientChallengeId
            );

        if(byChallengeKey.containsKey(
                spec.challengeKey))
            throw new IllegalStateException(
                "duplicate challenge key "+
                spec.challengeKey
            );

        if(byObjectiveKey.containsKey(
                spec.objective.key))
            throw new IllegalStateException(
                "duplicate objective key "+
                spec.objective.key
            );

        progress.define(
            spec.objective,
            spec.initialProgress,
            spec.claimed
        );

        Assignment assignment=
            new Assignment(spec);

        byClientId.put(
            assignment.clientChallengeId,
            assignment
        );

        byChallengeKey.put(
            assignment.challengeKey,
            assignment.clientChallengeId
        );

        byObjectiveKey.put(
            assignment.objectiveKey,
            assignment.clientChallengeId
        );

        return snapshotOf(
            assignment
        );
    }

    /**
     * Atomically replace the complete assigned challenge set.
     *
     * The caller decides when such a replacement represents a daily reset,
     * reroll, account restore or another server policy event.
     */
    synchronized List<Snapshot> replaceAll(
        Collection<AssignmentSpec> specs
    ){
        Objects.requireNonNull(
            specs,
            "specs"
        );

        ObjectiveProgressService nextProgress=
            new ObjectiveProgressService();

        LinkedHashMap<Integer,Assignment>
            nextByClientId=
                new LinkedHashMap<>();

        HashMap<String,Integer>
            nextByChallengeKey=
                new HashMap<>();

        HashMap<String,Integer>
            nextByObjectiveKey=
                new HashMap<>();

        for(AssignmentSpec spec:specs){
            Objects.requireNonNull(
                spec,
                "spec"
            );

            if(nextByClientId.containsKey(
                    spec.clientChallengeId))
                throw new IllegalStateException(
                    "duplicate client challenge id "+
                    spec.clientChallengeId
                );

            if(nextByChallengeKey.containsKey(
                    spec.challengeKey))
                throw new IllegalStateException(
                    "duplicate challenge key "+
                    spec.challengeKey
                );

            if(nextByObjectiveKey.containsKey(
                    spec.objective.key))
                throw new IllegalStateException(
                    "duplicate objective key "+
                    spec.objective.key
                );

            nextProgress.define(
                spec.objective,
                spec.initialProgress,
                spec.claimed
            );

            Assignment assignment=
                new Assignment(spec);

            nextByClientId.put(
                assignment.clientChallengeId,
                assignment
            );

            nextByChallengeKey.put(
                assignment.challengeKey,
                assignment.clientChallengeId
            );

            nextByObjectiveKey.put(
                assignment.objectiveKey,
                assignment.clientChallengeId
            );
        }

        progress=nextProgress;
        byClientId=nextByClientId;
        byChallengeKey=nextByChallengeKey;
        byObjectiveKey=nextByObjectiveKey;

        return snapshot();
    }

    synchronized void clearAssignments(){
        replaceAll(
            Collections.emptyList()
        );
    }

    synchronized ObjectiveProgressService.ProgressResult
        advance(
            int clientChallengeId,
            long amount
        ){
        Assignment assignment=
            requireAssignment(
                clientChallengeId
            );

        return progress.advance(
            assignment.objectiveKey,
            amount
        );
    }

    synchronized boolean markClaimed(
        int clientChallengeId
    ){
        Assignment assignment=
            requireAssignment(
                clientChallengeId
            );

        return progress.markClaimed(
            assignment.objectiveKey
        );
    }

    synchronized Snapshot get(
        int clientChallengeId
    ){
        Assignment assignment=
            byClientId.get(
                clientChallengeId
            );

        return assignment==null
            ?null
            :snapshotOf(
                assignment
            );
    }

    synchronized Snapshot getByChallengeKey(
        String challengeKey
    ){
        String normalized=
            ObjectiveDefinition
                .normalizeKey(
                    challengeKey
                );

        Integer clientId=
            byChallengeKey.get(
                normalized
            );

        return clientId==null
            ?null
            :get(clientId);
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Assignment assignment:
                byClientId.values())
            out.add(
                snapshotOf(
                    assignment
                )
            );

        out.sort(
            Comparator.comparingInt(
                value->
                    value.clientChallengeId
            )
        );

        return Collections.unmodifiableList(
            out
        );
    }

    synchronized int completedUnclaimedCount(){
        int count=0;

        for(Assignment assignment:
                byClientId.values()){
            ObjectiveProgressService.Snapshot state=
                progress.get(
                    assignment.objectiveKey
                );

            if(state.complete&&
               !state.claimed)
                count++;
        }

        return count;
    }

    private Snapshot snapshotOf(
        Assignment assignment
    ){
        ObjectiveProgressService.Snapshot state=
            progress.get(
                assignment.objectiveKey
            );

        if(state==null)
            throw new IllegalStateException(
                "missing objective state "+
                assignment.objectiveKey
            );

        return new Snapshot(
            assignment,
            state
        );
    }

    private Assignment requireAssignment(
        int clientChallengeId
    ){
        Assignment assignment=
            byClientId.get(
                clientChallengeId
            );

        if(assignment==null)
            throw new IllegalArgumentException(
                "unknown client challenge id="+
                clientChallengeId
            );

        return assignment;
    }
}
