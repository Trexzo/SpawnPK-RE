package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * G21.133: Negative-only, opt-in, in-memory result payload witness.
 * Reads two G21.132 verified seven-plane snapshots captured under the
 * existing single G21.100 lease. No nested lock, mutation or grant.
 */
final class MailboxSevenPlaneV3PayloadCohortV1 {
    private static final String UUID=
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"+
        "[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String[] PREFIX={
        ".g21120-commit-result-v3-",
        ".g21121-prepared-result-v3-",
        ".g21122-strict-prepared-result-v3-",
        ".g21123-world-terminal-result-v3-"
    };
    private static final String[] PROTOCOL={
        "SPK-G21120-COMMIT-RESULT-V3",
        "SPK-G21121-PREPARED-RESULT-V3",
        "SPK-G21122-STRICT-PREPARED-V3",
        "SPK-G21123-WORLD-TERMINAL-RESULT-V3"
    };
    private static final String[] KIND={
        "DISK_COMMIT","PREPARED_JOURNAL",
        "STRICT_PREPARED","WORLD_TERMINAL"
    };
    private static final String[][] FIELDS={
        {"TERMINAL_SHA256","COMMIT_BYTES_SHA256"},
        {"PREPARED_SHA256","TERMINAL_SHA256","JOURNAL_BYTES_SHA256"},
        {"PREPARED_SHA256","SNAPSHOT_VERSION","ACCOUNT_BYTES_SHA256"},
        {"PREPARED_SHA256","TERMINAL_SHA256",
            "SNAPSHOT_VERSION","TERMINAL_FILE_BYTES_SHA256"}
    };
    static final class Report {
        final MailboxSevenPlaneImmutableUuidCohortV1.Report uuid;
        final boolean canonicalFourV3PayloadsObserved=true;
        final boolean observedPhysicalDigestBindingsMatched=true;
        final boolean actualCallbackAuthenticationProved=false;
        final boolean independentPinAuthenticationProved=false;
        final boolean historicalOrFutureWritersFenced=false;
        final boolean rawOrDefaultWriterFenced=false;
        final boolean atomicCrossFileEpochProved=false;
        final boolean hardwarePowerLossProved=false;
        final boolean rolloverAuthorized=false;
        final boolean pruningAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Report(MailboxSevenPlaneImmutableUuidCohortV1.Report w){
            uuid=w;
        }
    }
    private static IOException veto(String message){
        return new IOException(
            "G21.133 V3_PAYLOAD_COHORT_UNSAFE_NO_GRANT "+message+
            " epoch=false cleanup=false grant=false");
    }
    private static String field(String line,String expected)
        throws IOException{
        String prefix=expected+"=";
        if(!line.startsWith(prefix))
            throw veto("MISSING_OR_REORDERED_FIELD_"+expected);
        return line.substring(prefix.length());
    }
    private static boolean sha(String value){
        return value!=null&&value.matches("[0-9a-f]{64}");
    }
    private static Map<String,String> decode(
        String account,int kind,String leaf,String encoded
    )throws IOException{
        if(kind<0||kind>=4)throw veto("UNKNOWN_RESULT_NAMESPACE");
        String prefix=account+".properties"+PREFIX[kind];
        if(!leaf.startsWith(prefix)||
           !leaf.substring(prefix.length()).matches(UUID+"\\.result"))
            throw veto("RESULT_FILENAME_UUID_OR_KIND_CHANGED");
        String uuid=leaf.substring(prefix.length(),leaf.length()-7);
        byte[] bytes;
        try{bytes=Base64.getDecoder().decode(encoded);}
        catch(IllegalArgumentException bad){
            throw veto("INVALID_BASE64_RESULT_PAYLOAD");
        }
        if(bytes.length<1||bytes.length>512||
           !Base64.getEncoder().encodeToString(bytes).equals(encoded))
            throw veto("NONCANONICAL_BASE64_RESULT");
        String body=new String(bytes,StandardCharsets.US_ASCII);
        if(!Arrays.equals(bytes,body.getBytes(StandardCharsets.US_ASCII)))
            throw veto("NONASCII_RESULT_PAYLOAD");
        String[] lines=body.split("\n",-1);
        if(lines.length!=FIELDS[kind].length+6||
           !PROTOCOL[kind].equals(lines[0])||
           !("ACCOUNT="+account).equals(lines[1])||
           !("ATTEMPT="+uuid).equals(lines[2])||
           !("KIND="+KIND[kind]).equals(lines[3])||
           !"STAGE=POST_CONFIRMED".equals(lines[lines.length-2])||
           !lines[lines.length-1].isEmpty())
            throw veto("NONCANONICAL_RESULT_ACCOUNT_UUID_KIND_OR_STAGE");
        Map<String,String> attributes=new HashMap<>();
        for(int i=0;i<FIELDS[kind].length;i++){
            String key=FIELDS[kind][i];
            String value=field(lines[4+i],key);
            if("SNAPSHOT_VERSION".equals(key)){
                if(!Integer.toString(PlayerSnapshot.CURRENT_VERSION)
                        .equals(value))
                    throw veto("NONCANONICAL_SNAPSHOT_VERSION");
            }else if(!sha(value)){
                throw veto("NONCANONICAL_RESULT_HASH_"+key);
            }
            attributes.put(key,value);
        }
        attributes.put("UUID",uuid);
        return attributes;
    }
    private static Map<Integer,Map<String,String>> parse(
        String account,String portable
    )throws IOException{
        String[] rows=portable.split("\n",-1);
        if(rows.length!=16||
           !"SPK-G21125-FOUR-V3-LEAVES-V1".equals(rows[0])||
           !("ACCOUNT="+account).equals(rows[1])||
           !"COUNT=4".equals(rows[2])||
           !rows[15].isEmpty())
            throw veto("INVALID_FOUR_RESULT_ARCHIVE_ENVELOPE");
        Map<Integer,Map<String,String>> kinds=new HashMap<>();
        Set<String> uuids=new HashSet<>();
        for(int n=0;n<4;n++){
            int at=3+n*3;
            String idx=field(rows[at],"KIND");
            if(!idx.matches("[0-3]"))throw veto("INVALID_KIND_INDEX");
            int kind=Integer.parseInt(idx);
            Map<String,String> rec=decode(
                account,kind,field(rows[at+1],"LEAF"),
                field(rows[at+2],"BYTES_BASE64"));
            if(kinds.putIfAbsent(kind,rec)!=null)
                throw veto("DUPLICATE_KIND");
            if(!uuids.add(rec.get("UUID")))
                throw veto("DUPLICATE_UUID");
        }
        if(kinds.size()!=4)throw veto("MISSING_KIND");
        return kinds;
    }
    private static void equal(String actual,String expected,String name)
        throws IOException{
        if(expected==null||!expected.equals(actual))throw veto(name);
    }
    private static void checkOne(
        String account,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation sample
    )throws IOException{
        Map<Integer,Map<String,String>> all=
            parse(account,sample.v3.portable);
        Map<String,String> commit=all.get(0);
        Map<String,String> journal=all.get(1);
        Map<String,String> strict=all.get(2);
        Map<String,String> world=all.get(3);
        String prepared=strict.get("PREPARED_SHA256");
        String terminal=world.get("TERMINAL_SHA256");
        equal(journal.get("PREPARED_SHA256"),prepared,
            "JOURNAL_PREPARED_HASH_DIFFERENT");
        equal(world.get("PREPARED_SHA256"),prepared,
            "WORLD_PREPARED_HASH_DIFFERENT");
        equal(journal.get("TERMINAL_SHA256"),terminal,
            "JOURNAL_TERMINAL_HASH_DIFFERENT");
        equal(commit.get("TERMINAL_SHA256"),terminal,
            "COMMIT_TERMINAL_HASH_DIFFERENT");
        equal(journal.get("JOURNAL_BYTES_SHA256"),sample.journalSha,
            "JOURNAL_RAW_BYTES_NOT_PINNED");
        equal(commit.get("COMMIT_BYTES_SHA256"),sample.commitSha,
            "COMMIT_RAW_BYTES_NOT_PINNED");
        equal(world.get("TERMINAL_FILE_BYTES_SHA256"),
            sample.accountSha,"TERMINAL_RAW_BYTES_NOT_PINNED");
        // G21.122 historical strict PREPARED account digest cannot
        // equal the CURRENT terminal account digest; never assert it.
    }
    /**
     * G21.134 pure in-memory validation of an already-captured
     * physical witness; does NOT acquire or open another lease.
     */
    static void requireCapturedFourPayloads(
        String account,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report physical
    )throws IOException{
        checkOne(account,physical.before);
        checkOne(account,physical.after);
    }

    /**
     * Pure projection of two already validated immutable record
     * content fields. Call ONLY after requireCapturedFourPayloads.
     * Historical strict PREPARED bytes are not current account bytes.
     */
    static String[] capturedCanonicalHashes(
        String account,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation sample
    )throws IOException{
        Map<Integer,Map<String,String>> data=
            parse(account,sample.v3.portable);
        return new String[]{
            data.get(2).get("PREPARED_SHA256"),
            data.get(3).get("TERMINAL_SHA256")
        };
    }

    static Report comparePinnedFourPayloads(
        FilePlayerRepository.PathResolver resolver,String account,
        String pinnedV3,String v1Portable,String pinnedV1,
        String pinnedAccount,String pinnedJournal,String pinnedCommit,
        String pinnedV2,long acquisitionMillis,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        MailboxSevenPlaneImmutableUuidCohortV1.Report prior=
            MailboxSevenPlaneImmutableUuidCohortV1
                .comparePinnedImmutableUuidCohort(
                    resolver,account,pinnedV3,v1Portable,pinnedV1,
                    pinnedAccount,pinnedJournal,pinnedCommit,
                    pinnedV2,acquisitionMillis,between);
        requireCapturedFourPayloads(account,prior.physical);
        return new Report(prior);
    }
    private MailboxSevenPlaneV3PayloadCohortV1(){}
}
