package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * G21.144–148: purely in-memory comparison of TWO existing G21.129
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

    /**
     * Read-only defensive preflight. The original G21.129 scanner
     * creates complete observations, but a caller may retain or
     * corrupt an in-memory report after its lease has ended.
     * No identity authentication is inferred from this shape check.
     * A null fileKey is VALID shape and handled as UNCERTAIN by
     * G21.145, not as a malformed report.
     */
    private static void requireIdentity(
        String label,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.PhysicalIdentity p
    )throws IOException{
        if(p==null||p.sha256==null||p.created==null||
           p.modified==null||p.size<0L)
            throw unsafe("MALFORMED_IDENTITY_"+label);
    }

    /**
     * G21.148: Check only consistency of the bytes in a retained
     * in-memory portable archive against its accompanying digest.
     * Both may be controlled by the SAME actor; this does not
     * authenticate external provenance or reproduce disk evidence.
     */
    private static void requirePortableSelfHash(
        String label,String portable,String claimedSha,int maxLength
    )throws IOException{
        if(portable==null||portable.isEmpty()||
           portable.length()>maxLength||claimedSha==null)
            throw unsafe("BAD_PORTABLE_LENGTH_OR_ENCODING_"+label);
        for(int i=0;i<portable.length();i++){
            if(portable.charAt(i)>127)
                throw unsafe("BAD_PORTABLE_LENGTH_OR_ENCODING_"+label);
        }
        if(!claimedSha.matches("[0-9a-f]{64}"))
            throw unsafe("PORTABLE_SHA_MISMATCH_"+label);
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256")
                .digest(portable.getBytes(StandardCharsets.US_ASCII));
            StringBuilder hex=new StringBuilder(64);
            final char[] digits="0123456789abcdef".toCharArray();
            for(byte b:digest){
                hex.append(digits[(b>>>4)&15]);
                hex.append(digits[b&15]);
            }
            if(!claimedSha.equals(hex.toString()))
                throw unsafe("PORTABLE_SHA_MISMATCH_"+label);
        }catch(NoSuchAlgorithmException impossible){
            throw unsafe("SHA256_UNAVAILABLE_"+label);
        }
    }

    private static void requireObservation(
        String label,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation o
    )throws IOException{
        if(o==null)
            throw unsafe("MISSING_OBSERVATION_"+label);
        if(o.v1==null||o.v2==null||o.v3==null||
           o.v1.portable==null||o.v1.sha256==null||
           o.v2.portable==null||o.v2.sha256==null||
           o.v3.portable==null||o.v3.sha256==null)
            throw unsafe("MALFORMED_PORTABLE_ARCHIVE_"+label);
        requireIdentity(label+"_ACCOUNT",o.accountIdentity);
        requireIdentity(label+"_PREPARED_JOURNAL",o.journalIdentity);
        requireIdentity(label+"_COMMIT",o.commitIdentity);
        if(!o.accountIdentity.sha256.equals(o.accountSha)||
           !o.journalIdentity.sha256.equals(o.journalSha)||
           !o.commitIdentity.sha256.equals(o.commitSha))
            throw unsafe("INCONSISTENT_SIDECAR_SHA_"+label);
        if(o.evidenceLeafIdentities==null)
            throw unsafe("NULL_EVIDENCE_MAP_"+label);
        for(Map.Entry<String,MailboxSevenPlaneContinuousPhysicalWitnessV1.PhysicalIdentity>
                e:o.evidenceLeafIdentities.entrySet()){
            if(e.getKey()==null)
                throw unsafe("NULL_EVIDENCE_NAME_"+label);
            requireIdentity(label+"_EVIDENCE_LEAF",e.getValue());
        }
    }

    private static void requireReport(
        String label,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report r
    )throws IOException{
        if(r==null)
            throw unsafe("NULL_REPORT_"+label);
        if(!r.sevenPhysicalPinsMatched||
           !r.threePhysicalIdentitiesStableAcrossCaptures||
           !r.evidenceLeafMetadataStableAcrossCaptures||
           !r.cooperatingCrossJvmWritesExcludedDuringLease)
            throw unsafe("UNVERIFIED_REPORT_FLAGS_"+label);
        requireObservation(label+"_BEFORE",r.before);
        requireObservation(label+"_AFTER",r.after);

        // G21.147: successful G21.129 reports originally assert
        // stable original BEFORE/AFTER physical evidence. Shape
        // validation alone cannot detect two separately well-formed
        // but contradictory in-memory snapshots when a caller has
        // changed one of them after the held lease returned.
        // Reuse EXACTLY the same bounded source/portable comparator;
        // do not trust a mutable success flag as the only evidence.
        SortedSet<String> internalDrift=new TreeSet<>();
        SortedSet<String> missingKeys=new TreeSet<>();
        compareOne(r.before,r.after,internalDrift,missingKeys);
        if(!internalDrift.isEmpty())
            throw unsafe("INTERNAL_BEFORE_AFTER_DRIFT_"+label);
        // Preserve the earlier G21.147 checked diagnostic for a
        // change confined to one capture. G21.148 independently
        // recalculates each bounded portable SHA AFTER that check:
        // identical forged before+after data with an unchanged
        // digest cannot hide behind the stable pair comparison.
        requirePortableSelfHash(
            label+"_BEFORE_V1",r.before.v1.portable,
            r.before.v1.sha256,32768);
        requirePortableSelfHash(
            label+"_AFTER_V1",r.after.v1.portable,
            r.after.v1.sha256,32768);
        requirePortableSelfHash(
            label+"_BEFORE_V2",r.before.v2.portable,
            r.before.v2.sha256,32768);
        requirePortableSelfHash(
            label+"_AFTER_V2",r.after.v2.portable,
            r.after.v2.sha256,32768);
        requirePortableSelfHash(
            label+"_BEFORE_V3",r.before.v3.portable,
            r.before.v3.sha256,4000);
        requirePortableSelfHash(
            label+"_AFTER_V3",r.after.v3.portable,
            r.after.v3.sha256,4000);
        // Null fileKey is not a concrete observed difference.
        // Cross-report compare() still yields UNCERTAIN via G21.145.
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
        requireReport("HISTORICAL",historical);
        requireReport("CURRENT",current);
        SortedSet<String> changed=new TreeSet<>();
        SortedSet<String> uncertain=new TreeSet<>();
        compareOne(historical.after,current.before,changed,uncertain);
        compareOne(historical.after,current.after,changed,uncertain);
        return new Report(changed,uncertain);
    }

    private MailboxSevenPlaneHistoricalIdentityDeltaV1(){}
}
