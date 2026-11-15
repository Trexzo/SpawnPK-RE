package spk.local;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * R75 OFFLINE, NON-AUTHORIZING retirement-safety audit.
 *
 * R74's precise account/ledger/RELEASED/ACK comparison is necessary
 * evidence but NOT sufficient for retiring an R73 write-intent blocker.
 * Several existing writer/owner paths remain outside R73. A free R60
 * OS lock proves only cooperative-lock quiescence at one instant.
 *
 * This is a read-only explanatory audit, NOT a cleanup or recovery API.
 * Every result blocks write-intent retirement, login, replay and release.
 * A repeat observation may detect changes, but does not prove that no
 * unobserved ABA write or noncooperative operation occurred between reads.
 */
final class R75OfflineRetirementSafetyAudit {
    enum Status {
        BLOCK_UNRESOLVED_ACCOUNT_EVIDENCE,
        BLOCK_STALE_RECONCILIATION_OBSERVATION,
        BLOCK_UNFENCED_WRITER_AND_OWNER_PATHS
    }

    /**
     * Concrete source-level ingress points verified in
     * R75OfflineRetirementSafetyAuditTest. Not an exhaustive call graph.
     */
    static final class Gap {
        final String source;
        final String operation;
        final String missingGuarantee;
        Gap(String source,String operation,String missingGuarantee){
            this.source=source;
            this.operation=operation;
            this.missingGuarantee=missingGuarantee;
        }
    }

    static final class Review {
        final Status status;
        final R74OfflineReconciliationReview.Status r74;
        final UUID observationId;
        final long generation;
        final String reason;
        final List<Gap> knownGaps;

        Review(Status status,R74OfflineReconciliationReview.Review r74,
               String reason){
            this.status=status;
            this.r74=r74.status;
            this.observationId=r74.observationId;
            this.generation=r74.accountGeneration;
            this.reason=reason;
            this.knownGaps=GAPS;
        }
        boolean loginAuthorized(){return false;}
        boolean retirementAuthorized(){return false;}
        boolean recoveryAuthorized(){return false;}
        boolean replayAuthorized(){return false;}
        boolean quarantineReleased(){return false;}
        boolean markersModified(){return false;}
        boolean allWritersQuiescent(){return false;}
        boolean productionAuthenticated(){return false;}
    }

    private static final List<Gap> GAPS=Collections.unmodifiableList(
        Arrays.asList(
            new Gap("WorldPlayerPersistence.java",
                "repository.save(snapshot)",
                "normal default I/O worker does not install R73"),
            new Gap("LocalAccountLifecycle.java",
                "repository.save(snapshot)",
                "compatibility direct-save route lacks R73 preflight"),
            new Gap("FilePlayerRepository.java",
                "AtomicAccountPublisher.publish",
                "canonical file writer does not require R60/R73"),
            new Gap("AccountStore.java",
                "AtomicAccountPublisher.publish",
                "historical primary-account writer lacks R73"),
            new Gap("LocalAccountProfiles.java",
                "AtomicAccountPublisher.publish",
                "secondary-account writer lacks R73"),
            new Gap("PetAccessoryPersistence.java",
                "AtomicAccountPublisher.publish",
                "accessory mutation writes account without R73"),
            new Gap("R64OfflineSessionOwnership.java",
                "replaceOwner(",
                "original held-R60 owner transition bypasses R73 evidence"),
            new Gap("R63AccountSaveAdmission.java",
                "saveIfCurrent(",
                "R63 generation save does not require R73 intent")
        )
    );

    private final R74OfflineReconciliationReview reconciler;
    R75OfflineRetirementSafetyAudit(
        Path journal,Path accounts,Path generations,
        Path sessions,Path evidence
    ){
        reconciler=new R74OfflineReconciliationReview(
            journal,accounts,generations,sessions,evidence
        );
    }

    Review inspect(String username){
        return classify(reconciler.inspect(
            Objects.requireNonNull(username,"username")
        ));
    }

    /**
     * A new read-only R74 observation is compared with a prior one.
     * It never returns AUTHORIZED. Comparison is not a durable lease,
     * retirement certificate, or absence-of-writers proof.
     */
    Review recheck(String username,Review previous){
        Objects.requireNonNull(previous,"previous");
        R74OfflineReconciliationReview.Review current=
            reconciler.inspect(Objects.requireNonNull(username,"username"));
        if(current.status!=previous.r74||
           current.accountGeneration!=previous.generation||
           !Objects.equals(current.observationId,previous.observationId))
            return new Review(
                Status.BLOCK_STALE_RECONCILIATION_OBSERVATION,
                current,
                "R75_STALE_OBSERVATION_CHANGED_AFTER_PRIOR_REVIEW"
            );
        return classify(current);
    }

    private static Review classify(
        R74OfflineReconciliationReview.Review current
    ){
        if(current.status!=
               R74OfflineReconciliationReview.Status
                   .BLOCK_CONSISTENT_BUT_QUARANTINED&&
           current.status!=
               R74OfflineReconciliationReview.Status
                   .BLOCK_CANCELLED_LATE_ACK_QUARANTINED)
            return new Review(
                Status.BLOCK_UNRESOLVED_ACCOUNT_EVIDENCE,
                current,
                "R75_R74_EVIDENCE_NOT_CONSISTENT"
            );
        return new Review(
            Status.BLOCK_UNFENCED_WRITER_AND_OWNER_PATHS,
            current,
            "R75_MATCHED_ACK_IS_NOT_SAFE_RETIREMENT"
        );
    }

    static List<Gap> knownSourceGaps(){return GAPS;}

}
