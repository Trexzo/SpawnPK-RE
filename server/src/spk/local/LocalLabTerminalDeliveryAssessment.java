package spk.local;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pure read-only triage of a recorded DEFAULT post-settlement LocalLab
 * terminal. This translates callback outcomes into diagnostically precise
 * states but grants NOTHING. In particular, RETURNED is not a durable
 * mutation acknowledgement, and THREW can follow a partial mutation.
 *
 * The report is a process-local observation, NOT a restart-safe event key,
 * outbox record, or a source of original SpawnPK server authority.
 */
final class LocalLabTerminalDeliveryAssessment {
    enum Consumer { G1, G4, G14, G15, G16, G18 }

    enum Evidence {
        RETURNED_UNACKNOWLEDGED,
        THREW_MUTATION_UNCERTAIN,
        NOT_REACHED,
        SKIPPED_NO_CURRENT_RECIPIENT,
        NOT_OBSERVED
    }

    final EntityId npcId;
    final long deathTick;
    final LocalLabSettledDeathEnvelope.OriginStatus originStatus;
    final Map<Consumer,Evidence> consumers;

    private LocalLabTerminalDeliveryAssessment(
        LocalLabSequentialTerminalStageTrace.Report report,
        EnumMap<Consumer,Evidence> states
    ){
        npcId=report.npcId;
        deathTick=report.deathTick;
        originStatus=report.originStatus;
        consumers=Collections.unmodifiableMap(
            new EnumMap<Consumer,Evidence>(states)
        );
    }

    Evidence evidence(Consumer consumer){
        return consumers.get(Objects.requireNonNull(consumer,"consumer"));
    }

    // Never infer grant/ACK/replay authority from an observer callback.
    boolean hasDurableGrantAcknowledgement(){
        return false;
    }

    boolean safeToAutomaticallyReplay(){
        return false;
    }

    boolean hasMutationUncertainty(){
        return consumers.containsValue(Evidence.THREW_MUTATION_UNCERTAIN);
    }

    static LocalLabTerminalDeliveryAssessment from(
        LocalLabSequentialTerminalStageTrace.Report report
    ){
        LocalLabSequentialTerminalStageTrace.Report checked=
            Objects.requireNonNull(report,"report");
        EnumMap<Consumer,Evidence> states=
            new EnumMap<Consumer,Evidence>(Consumer.class);

        LocalLabSequentialTerminalStageTrace.G1Outcome g1=
            Objects.requireNonNull(checked.g1Outcome,"g1Outcome");
        switch(g1){
            case RETURNED:
                states.put(Consumer.G1,Evidence.RETURNED_UNACKNOWLEDGED);
                break;
            case THREW:
                states.put(Consumer.G1,Evidence.THREW_MUTATION_UNCERTAIN);
                break;
            case NOT_OBSERVED:
                states.put(Consumer.G1,Evidence.NOT_OBSERVED);
                break;
            default: throw new IllegalStateException("G1 outcome not mapped");
        }

        for(LocalLabSequentialTerminalStageTrace.Stage stage:
                LocalLabSequentialTerminalStageTrace.Stage.values()){
            LocalLabSequentialTerminalStageTrace.Outcome outcome=
                Objects.requireNonNull(checked.outcome(stage),"stage outcome");
            Evidence evidence;
            switch(outcome){
                case RETURNED:
                    evidence=Evidence.RETURNED_UNACKNOWLEDGED;
                    break;
                case THREW:
                    evidence=Evidence.THREW_MUTATION_UNCERTAIN;
                    break;
                case NOT_REACHED:
                    evidence=Evidence.NOT_REACHED;
                    break;
                case SKIPPED_NO_CURRENT_RECIPIENT:
                    evidence=Evidence.SKIPPED_NO_CURRENT_RECIPIENT;
                    break;
                default: throw new IllegalStateException("stage outcome not mapped");
            }
            states.put(Consumer.valueOf(stage.name()),evidence);
        }
        if(states.size()!=Consumer.values().length)
            throw new IllegalStateException(
                "TERMINAL_ASSESSMENT_INCOMPLETE_CONSUMER_SET");

        return new LocalLabTerminalDeliveryAssessment(checked,states);
    }

}
