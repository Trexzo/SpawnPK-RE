package spk.local;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * G21.144: purely in-memory comparison of TWO existing G21.129
 * physical reports from separate held-lease observations.
 *
 * This detects observable identity drift when a caller RETAINS an
 * older reference. It does not authenticate either reference, hold
 * a lease, read disk, exclude RAW writers or authorize anything.
 */
final class MailboxSevenPlaneHistoricalIdentityDeltaV1 {
    enum Status {
        MATCHED_OBSERVED_METADATA_NO_AUTHORITY,
        CHANGED_OBSERVED_METADATA_NO_AUTHORITY
    }

    static final class Report {
        final Status status;
        final SortedSet<String> changedSources;
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

        private Report(SortedSet<String> changed){
            changedSources=Collections.unmodifiableSortedSet(
                new TreeSet<>(changed));
            status=changedSources.isEmpty()
                ?Status.MATCHED_OBSERVED_METADATA_NO_AUTHORITY
                :Status.CHANGED_OBSERVED_METADATA_NO_AUTHORITY;
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
        SortedSet<String> changed
    ){
        if(a==null||b==null||
            !a.sameMetadata(b)||!a.sha256.equals(b.sha256))
            changed.add(label);
    }

    private static void compareOne(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation old,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation now,
        SortedSet<String> changed
    ){
        comparePhysical("ACCOUNT",old.accountIdentity,
            now.accountIdentity,changed);
        comparePhysical("PREPARED_JOURNAL",old.journalIdentity,
            now.journalIdentity,changed);
        comparePhysical("COMMIT",old.commitIdentity,
            now.commitIdentity,changed);
        for(Map.Entry<String,MailboxSevenPlaneContinuousPhysicalWitnessV1.PhysicalIdentity>
                e:old.evidenceLeafIdentities.entrySet()){
            comparePhysical("LEAF:"+e.getKey(),e.getValue(),
                now.evidenceLeafIdentities.get(e.getKey()),changed);
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
        compareOne(historical.after,current.before,changed);
        compareOne(historical.after,current.after,changed);
        return new Report(changed);
    }

    private MailboxSevenPlaneHistoricalIdentityDeltaV1(){}
}
