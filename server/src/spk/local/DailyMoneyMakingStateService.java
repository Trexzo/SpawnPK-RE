package spk.local;

import java.util.Locale;
import java.util.Objects;

/**
 * Semantic Daily Money Making selection/tracking state above ObjectiveProgressService.
 *
 * This service deliberately does not duplicate objective progress, assignment,
 * rewards, reset schedules, teleport policy or client presentation identity.
 */
final class DailyMoneyMakingStateService {
    enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    static final class Snapshot {
        final Difficulty selectedDifficulty;
        final String trackedObjectiveKey;

        Snapshot(
            Difficulty selectedDifficulty,
            String trackedObjectiveKey
        ){
            this.selectedDifficulty=
                selectedDifficulty;
            this.trackedObjectiveKey=
                trackedObjectiveKey;
        }

        boolean hasTrackedObjective(){
            return trackedObjectiveKey!=null;
        }

        @Override public String toString(){
            return "DailyMoneyMakingSnapshot{"+
                "selectedDifficulty="+
                    selectedDifficulty+
                ",trackedObjectiveKey="+
                    trackedObjectiveKey+
                "}";
        }
    }

    private final ObjectiveProgressService objectives;

    private Difficulty selectedDifficulty;
    private String trackedObjectiveKey;

    DailyMoneyMakingStateService(
        ObjectiveProgressService objectives
    ){
        this.objectives=
            Objects.requireNonNull(
                objectives,
                "objectives"
            );
    }

    synchronized boolean selectDifficulty(
        Difficulty difficulty
    ){
        Objects.requireNonNull(
            difficulty,
            "difficulty"
        );

        if(selectedDifficulty==difficulty)
            return false;

        selectedDifficulty=difficulty;
        return true;
    }

    synchronized boolean trackObjective(
        String objectiveKey
    ){
        String normalized=
            ObjectiveDefinition
                .normalizeKey(
                    objectiveKey
                );

        if(objectives.get(normalized)==null)
            throw new IllegalArgumentException(
                "unknown objective key="+
                normalized
            );

        if(normalized.equals(
                trackedObjectiveKey))
            return false;

        trackedObjectiveKey=normalized;
        return true;
    }

    synchronized boolean clearTrackedObjective(){
        if(trackedObjectiveKey==null)
            return false;

        trackedObjectiveKey=null;
        return true;
    }

    synchronized Snapshot snapshot(){
        return new Snapshot(
            selectedDifficulty,
            trackedObjectiveKey
        );
    }

    synchronized ObjectiveProgressService.Snapshot
        trackedObjectiveProgress(){
        if(trackedObjectiveKey==null)
            return null;

        ObjectiveProgressService.Snapshot progress=
            objectives.get(
                trackedObjectiveKey
            );

        if(progress==null)
            throw new IllegalStateException(
                "tracked objective disappeared "+
                trackedObjectiveKey
            );

        return progress;
    }
}
