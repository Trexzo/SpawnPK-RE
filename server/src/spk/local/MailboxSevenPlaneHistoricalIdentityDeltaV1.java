package spk.local;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * G21.144–145: purely in-memory comparison of TWO existing G21.129
 * physical reports from separate held-lease observations.
 *
 * This detects observable identity drift when a caller RETAINS an
 * older reference. It does not authenticate either reference, hold
 * a lease, read disk, exclude RAW writers or authorize anything.
 */
final class MailboxSevenPlaneHistoricalIdentityDeltaV1 {
    enum Status {
        MATCHED_OBSERVED_METADATA_NO_AUTHORITY,
        CHANGED_OBSERVED_METADATA_NO_AUTHORITY,
        UNCERTAIN_MISSING_FILE_KEY_NO_AUTHORITY
    }

    static final class Report {
        final Status status;
        final SortedSet<String> changedSources;
        final SortedSet<String> uncertainSources;
        final boolean historicalReportAuthenticated=false;
        final boolean independentPinAuthenticationProved=false;
        final boolean rawOrDefaultWriterExclusionProved=false;
        final boolean historicalAndFutureWritersFenced=false;
        final boolean atomicCrossFileEpochProved=false;
        final boolean hardwarePowerLossProved=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean pruningAuthorized=false;
        final boolean rolloverAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;

        private Report(
            SortedSet<String> changed,SortedSet<String> uncertain
        ){
            changedSources=Collections.unmodifiableSortedSet(
                new TreeSet<>(changed));
            uncertainSources=Collections.unmodifiableSortedSet(
                new TreeSet<>(uncertain));
            // An observable content/identity drift is still CHANGED.
            // Missing fileKey is uncertainty, never proof of match.
            status=!changedSources.isEmpty()
                ?Status.CHANGED_OBSERVED_METADATA_NO_AUTHORITY
                :!uncertainSources.isEmpty()
                    ?Status.UNCERTAIN_MISSING_FILE_KEY_NO_AUTHORITY
                    :Status.MATCHED_OBSERVED_METADATA_NO_AUTHORITY;
        }
    }

    private static IOException unsafe(String reason){
        return new IOException(
            "G21.144 HISTORICAL_IDENTITY_DELTA_UNSAFE_NO_AUTHORITY "+
            reason+" epoch=false cleanup=false grant=false");
    }

    private static void comparePhysical(
        String label,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.PhysicalIdentity a,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.PhysicalIdentity b,
        SortedSet<String> changed,SortedSet<String> uncertain
    ){
        if(a==null||b==null){
            // Missing SOURCE is concrete drift, not merely absent
            // filesystem fileKey identity information.
            changed.add(label);
            return;
        }
        // Compare only objectively available attributes. When ONE
        // fileKey is missing, the old Objects.equals(null,nonNull)
        // result was incorrectly labelled concrete CHANGED drift.
        // That is unknown identity, not an observed identity change.
        boolean contentOrNonKeyMetadataChanged=
            a.size!=b.size||
            !a.created.equals(b.created)||
            !a.modified.equals(b.modified)||
            !a.sha256.equals(b.sha256);
        boolean bothIdentityKeysPresent=
            a.fileKey!=null&&b.fileKey!=null;
        if(contentOrNonKeyMetadataChanged||
           (bothIdentityKeysPresent&&
                !a.fileKey.equals(b.fileKey)))
            changed.add(label);
        // Neither null/null nor null/non-null is proof of an
        // unchanged original file identity. If other metadata
        // changed too, CHANGED takes status precedence.
        if(!bothIdentityKeysPresent)
            uncertain.add(label);
    }

    private static void compareOne(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation old,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation now,
        SortedSet<String> changed,SortedSet<String> uncertain
    ){
        comparePhysical("ACCOUNT",old.accountIdentity,
            now.accountIdentity,changed,uncertain);
        comparePhysical("PREPARED_JOURNAL",old.journalIdentity,
            now.journalIdentity,changed,uncertain);
        comparePhysical("COMMIT",old.commitIdentity,
            now.commitIdentity,changed,uncertain);
        for(Map.Entry<String,MailboxSevenPlaneContinuousPhysicalWitnessV1.PhysicalIdentity>
                e:old.evidenceLeafIdentities.entrySet()){
            comparePhysical("LEAF:"+e.getKey(),e.getValue(),
                now.evidenceLeafIdentities.get(e.getKey()),
                changed,uncertain);
        }
        for(String filename:now.evidenceLeafIdentities.keySet()){
            if(!old.evidenceLeafIdentities.containsKey(filename))
                changed.add("LEAF:"+filename);
        }
        if(!old.v1.sha256.equals(now.v1.sha256)||
           !old.v1.portable.equals(now.v1.portable))
            changed.add("PORTABLE_V1");
        if(!old.v2.sha256.equals(now.v2.sha256)||
           !old.v2.portable.equals(now.v2.portable))
            changed.add("PORTABLE_V2");
        if(!old.v3.sha256.equals(now.v3.sha256)||
           !old.v3.portable.equals(now.v3.portable))
            changed.add("PORTABLE_V3");
    }

    /**
     * Historical AFTER versus BOTH current BEFORE and current AFTER.
     * Both reports are read-only snapshots. This comparison does
     * NOT recheck the current filesystem or extend either OS lease.
     */
    static Report compare(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current
    )throws IOException{
        if(historical==null||current==null||
           historical.after==null||current.before==null||
           current.after==null)
            throw unsafe("NULL_OR_INCOMPLETE_REPORT");
        SortedSet<String> changed=new TreeSet<>();
        SortedSet<String> uncertain=new TreeSet<>();
        compareOne(historical.after,current.before,changed,uncertain);
        compareOne(historical.after,current.after,changed,uncertain);
        return new Report(changed,uncertain);
    }

    private MailboxSevenPlaneHistoricalIdentityDeltaV1(){}
}
