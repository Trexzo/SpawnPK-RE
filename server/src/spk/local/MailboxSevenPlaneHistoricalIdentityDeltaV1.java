package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * G21.144–156: purely in-memory comparison of TWO existing G21.129
 * physical reports from separate held-lease observations.
 *
 * This detects observable identity drift when a caller RETAINS an
 * older reference. It does not authenticate either reference, hold
 * a lease, read disk, exclude RAW writers or authorize anything.
 */
final class MailboxSevenPlaneHistoricalIdentityDeltaV1 {
    // G21.153: exact source reader UUID syntax (v1, v2, v3).
    // This verifies format only, NEVER source provenance/authenticity.
    private static final String CANONICAL_UUID=
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"+
        "[0-9a-f]{4}-[0-9a-f]{12}";

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
        // G21.150: a successful G21.129 physical read never emits a
        // zero-length, over-capacity or non-canonical SHA-256 source.
        // Identical in-memory corruption of BEFORE and AFTER must not
        // turn this impossible shape into a positive identity MATCH.
        // Do not re-read disk or trust these fields as signed evidence.
        final long maxBytes=label.endsWith("_ACCOUNT")?1048576L:
            label.endsWith("_EVIDENCE_LEAF")?512L:1024L;
        if(p.size<1L||p.size>maxBytes)
            throw unsafe("UNSAFE_PHYSICAL_SIZE_"+label);
        if(!p.sha256.matches("[0-9a-f]{64}"))
            throw unsafe("BAD_PHYSICAL_SHA_"+label);
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
        // G21.151: G21.129's real scanner admits at most 32 v1,
        // 32 v2 and 4 v3 leaves. A post-lease report may be altered
        // in memory, so even four identically enlarged maps must not
        // be accepted as a bounded original observation.
        if(o.evidenceLeafIdentities.size()>68)
            throw unsafe("EXCESS_EVIDENCE_LEAVES_"+label);
        final int[] counts=new int[3];
        final int[] maxima={32,32,4};
        // G21.154: unlike v1/v2 (variable bounded cohorts), the
        // original v3 result reader succeeds only with exactly ONE
        // leaf for each of four specific result kinds.
        final boolean[] v3Kinds=new boolean[4];
        String originalBasename=null;
        for(Map.Entry<String,MailboxSevenPlaneContinuousPhysicalWitnessV1.PhysicalIdentity>
                e:o.evidenceLeafIdentities.entrySet()){
            String name=e.getKey();
            if(name==null)
                throw unsafe("NULL_EVIDENCE_NAME_"+label);
            // Original leaf names are bounded plain filenames; a
            // slash, backslash or control character cannot be a
            // canonical v1/v2/v3 writer-evidence name.
            if(name.isEmpty()||name.length()>200)
                throw unsafe("BAD_EVIDENCE_NAME_"+label);
            for(int i=0;i<name.length();i++){
                char ch=name.charAt(i);
                if(ch=='/'||ch=='\\'||ch<32||ch==127)
                    throw unsafe("BAD_EVIDENCE_NAME_"+label);
            }
            // G21.152: the real G21.129 physical scanner has three
            // separate capacity-limited namespaces. A total of <=68
            // alone cannot authenticate these retained map entries.
            // Accept only source-family shapes here (UUID syntax,
            // exact names, portable byte provenance remain unproved).
            int at=name.lastIndexOf(".g211");
            if(at<1)
                throw unsafe("UNKNOWN_EVIDENCE_FAMILY_"+label);
            String basename=name.substring(0,at);
            if(originalBasename==null)
                originalBasename=basename;
            else if(!originalBasename.equals(basename))
                throw unsafe("MIXED_EVIDENCE_BASENAME_"+label);
            String suffix=name.substring(at);
            int family=-1;
            if(suffix.matches(
                    "\\.g21103-writer-attempt-v1-[A-Za-z0-9-]+\\.(begin|complete)"))
                family=0;
            else if(suffix.matches(
                    "\\.g21114-writer-kind-v2-[A-Za-z0-9-]+\\.(begin|complete)"))
                family=1;
            else if(suffix.matches(
                    "\\.g21120-commit-result-v3-[A-Za-z0-9-]+\\.result")||
                suffix.matches(
                    "\\.g21121-prepared-result-v3-[A-Za-z0-9-]+\\.result")||
                suffix.matches(
                    "\\.g21122-strict-prepared-result-v3-[A-Za-z0-9-]+\\.result")||
                suffix.matches(
                    "\\.g21123-world-terminal-result-v3-[A-Za-z0-9-]+\\.result"))
                family=2;
            if(family<0)
                throw unsafe("UNKNOWN_EVIDENCE_FAMILY_"+label);
            // G21.153: G21.152 recognized only a broad source-family
            // suffix. The original v1/v2/v3 readers all insist on
            // precisely 8-4-4-4-12 lowercase hexadecimal UUIDs.
            // Check UUID AFTER recognizing the unchanged stage/version
            // family so unsupported filenames retain their old veto.
            String versionMarker="-v"+(family+1)+"-";
            int markerAt=suffix.indexOf(versionMarker);
            int extensionAt=suffix.lastIndexOf('.');
            if(markerAt<0||extensionAt<=markerAt+versionMarker.length()||
               !suffix.substring(markerAt+versionMarker.length(),
                    extensionAt).matches(CANONICAL_UUID))
                throw unsafe("NONCANONICAL_EVIDENCE_UUID_"+label);
            if(++counts[family]>maxima[family])
                throw unsafe("EXCESS_EVIDENCE_FAMILY_"+label+"_V"+(family+1));
            if(family==2){
                final int kind=suffix.startsWith(".g21120-")?0:
                    suffix.startsWith(".g21121-")?1:
                    suffix.startsWith(".g21122-")?2:3;
                if(v3Kinds[kind])
                    throw unsafe("DUPLICATE_V3_RESULT_KIND_"+label);
                v3Kinds[kind]=true;
            }
            requireIdentity(label+"_EVIDENCE_LEAF",e.getValue());
        }
        if(counts[2]!=4)
            throw unsafe("MISSING_V3_RESULT_KIND_"+label);
        for(boolean kindObserved:v3Kinds)
            if(!kindObserved)
                throw unsafe("MISSING_V3_RESULT_KIND_"+label);
    }

    /**
     * G21.155–156: bind the filenames AND bounded decoded leaf
     * bytes of retained v3 portable to the four-kind physical map.
     * Both snapshots are controlled by the same caller after lease
     * release. Agreement is NOT content provenance or an epoch.
     */
    private static void requireV3PortableLeafBinding(
        String label,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation o
    )throws IOException{
        final String portable=o.v3.portable;
        // The producing G21.125 reader emits exactly 3 header lines,
        // 4 x (KIND, LEAF, BYTES_BASE64), and a final newline.
        final String[] lines=portable.split("\n",-1);
        if(lines.length!=16||
           !"SPK-G21125-FOUR-V3-LEAVES-V1".equals(lines[0])||
           !lines[1].matches("ACCOUNT=[a-z0-9_-]{1,64}")||
           !"COUNT=4".equals(lines[2])||
           !lines[15].isEmpty())
            throw unsafe("BAD_V3_PORTABLE_INVENTORY_"+label);
        final SortedSet<String> portableNames=new TreeSet<>();
        final String[] kindMarkers={
            ".g21120-commit-result-v3-",
            ".g21121-prepared-result-v3-",
            ".g21122-strict-prepared-result-v3-",
            ".g21123-world-terminal-result-v3-"
        };
        for(int kind=0;kind<4;kind++){
            int pos=3+3*kind;
            if(!("KIND="+kind).equals(lines[pos])||
               !lines[pos+1].startsWith("LEAF=")||
               !lines[pos+2].startsWith("BYTES_BASE64=")||
               lines[pos+2].length()<17)
                throw unsafe("BAD_V3_PORTABLE_INVENTORY_"+label);
            String name=lines[pos+1].substring(5);
            if(!name.contains(kindMarkers[kind])||
               !name.matches("[A-Za-z0-9_.-]{1,200}")||
               !portableNames.add(name))
                throw unsafe("BAD_V3_PORTABLE_INVENTORY_"+label);
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity physical=o.evidenceLeafIdentities.get(name);
            if(physical==null)
                throw unsafe("V3_PORTABLE_LEAF_MISMATCH_"+label);
            // G21.156: G21.125 serialized the ORIGINAL 1..512 raw
            // bytes as Base64; G21.129 separately hashed and sized
            // that same source. A coherent forgery of four retained
            // snapshots must not bypass this negative-only check.
            String encoded=lines[pos+2].substring("BYTES_BASE64=".length());
            if(encoded.isEmpty()||encoded.length()>684)
                throw unsafe("BAD_V3_PORTABLE_BASE64_"+label);
            final byte[] payload;
            try{
                payload=Base64.getDecoder().decode(encoded);
            }catch(IllegalArgumentException malformed){
                throw unsafe("BAD_V3_PORTABLE_BASE64_"+label);
            }
            if(payload.length<1||payload.length>512||
               !Base64.getEncoder().encodeToString(payload).equals(encoded))
                throw unsafe("BAD_V3_PORTABLE_BASE64_"+label);
            if(payload.length!=physical.size)
                throw unsafe("V3_PORTABLE_PHYSICAL_SIZE_MISMATCH_"+label);
            try{
                byte[] hash=MessageDigest.getInstance("SHA-256")
                    .digest(payload);
                final char[] digits="0123456789abcdef".toCharArray();
                StringBuilder hex=new StringBuilder(64);
                for(byte b:hash){
                    hex.append(digits[(b>>>4)&15]);
                    hex.append(digits[b&15]);
                }
                if(!hex.toString().equals(physical.sha256))
                    throw unsafe("V3_PORTABLE_PHYSICAL_SHA_MISMATCH_"+label);
            }catch(NoSuchAlgorithmException unavailable){
                throw unsafe("SHA256_UNAVAILABLE_"+label);
            }
        }
        int physicalV3=0;
        for(String name:o.evidenceLeafIdentities.keySet())
            if(name.contains(".g2112"))physicalV3++;
        if(physicalV3!=4||portableNames.size()!=4)
            throw unsafe("V3_PORTABLE_LEAF_MISMATCH_"+label);
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
        requireV3PortableLeafBinding(label+"_BEFORE",r.before);
        requireV3PortableLeafBinding(label+"_AFTER",r.after);
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
        // G21.149: G21.145's uncertainty must cover EVERY retained
        // source observation, including historical BEFORE. G21.147
        // already vetoed any concrete internal BEFORE/AFTER drift.
        // The discarded "missingKeys" in requireReport must not
        // silently turn a historical BEFORE-only null fileKey into
        // a positive MATCH after the lease has been released.
        // Inherit only uncertainty here; this cannot authenticate
        // either snapshot or promote any recovery permission.
        compareOne(historical.before,historical.after,
            new TreeSet<>(),uncertain);
        compareOne(historical.after,current.before,changed,uncertain);
        compareOne(historical.after,current.after,changed,uncertain);
        return new Report(changed,uncertain);
    }

    private MailboxSevenPlaneHistoricalIdentityDeltaV1(){}
}
