package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * G21.132: negative-only UUID join of the actual immutable v1,
 * typed v2 and four v3 result-leaf NAMES ALREADY captured inside the
 * same G21.129/130/131 physical lease. This does NOT authenticate
 * publication callbacks, file provenance, independent pins, future
 * writer generations or establish a transaction/settlement epoch.
 *
 * We deliberately do not call lock-owning G21.119/124/125 rich
 * semantic verifiers while a nested G21.100 FileLock is held.
 */
final class MailboxSevenPlaneImmutableUuidCohortV1 {
    private static final String UUID=
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"+
        "[0-9a-f]{4}-[0-9a-f]{12}";
    private static final MailboxWriterKindEvidenceV2.Kind[] RESULT_KINDS={
        MailboxWriterKindEvidenceV2.Kind.DISK_COMMIT,
        MailboxWriterKindEvidenceV2.Kind.PREPARED_JOURNAL,
        MailboxWriterKindEvidenceV2.Kind.STRICT_PREPARED,
        MailboxWriterKindEvidenceV2.Kind.WORLD_TERMINAL
    };
    private static final String[] RESULT_PREFIXES={
        ".g21120-commit-result-v3-",
        ".g21121-prepared-result-v3-",
        ".g21122-strict-prepared-result-v3-",
        ".g21123-world-terminal-result-v3-"
    };

    static final class Report {
        final MailboxSevenPlaneContinuousPhysicalWitnessV1.Report physical;
        final boolean exactFourV1V2V3UuidCohortObserved;
        final boolean v3ResultPayloadAuthorAuthenticated=false;
        final boolean realCallbackExecutionProved=false;
        final boolean rawOrDefaultWritersExcluded=false;
        final boolean historicalAndFutureWritersFenced=false;
        final boolean independentPinAuthenticationProved=false;
        final boolean atomicRecoveryEpochProved=false;
        final boolean richSemanticCohortWithinLeaseProved=false;
        final boolean rolloverAuthorized=false;
        final boolean pruningAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Report(
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report witness
        ){
            physical=witness;
            exactFourV1V2V3UuidCohortObserved=true;
        }
    }
    private static IOException veto(String reason){
        return new IOException(
            "G21.132 IMMUTABLE_UUID_COHORT_UNSAFE_NO_GRANT "+reason+
            " epoch=false cleanup=false rollover=false grant=false");
    }
    private static String exactName(
        String candidate,String prefix,String tailPattern
    )throws IOException{
        Matcher match=Pattern.compile(
            Pattern.quote(prefix)+"("+UUID+")"+tailPattern)
            .matcher(candidate);
        if(!match.matches())throw veto("UNEXPECTED_EVIDENCE_NAME");
        return match.group(1);
    }
    private static int flag(String stage){
        return "begin".equals(stage)?1:2;
    }
    private static Map<String,Integer> parseV1(
        String account,String portable
    )throws IOException{
        Map<String,Integer> flags=new HashMap<>();
        String prefix=account+".properties.g21103-writer-attempt-v1-";
        int entries=0;
        for(String line:portable.split("\\n")){
            if(!line.startsWith("RECORD|"))continue;
            String[] columns=line.split("\\|",3);
            if(columns.length!=3)throw veto("MALFORMED_V1_RECORD");
            String name=columns[1];
            String uuid=exactName(name,prefix,"\\.(?:begin|complete)");
            String stage=name.substring(name.lastIndexOf('.')+1);
            int prior=flags.getOrDefault(uuid,0);
            if((prior&flag(stage))!=0)throw veto("DUPLICATE_V1_STAGE");
            flags.put(uuid,prior|flag(stage));
            entries++;
        }
        if(entries!=8||flags.size()!=4)
            throw veto("NOT_FOUR_V1_PAIRS");
        for(int f:flags.values())if(f!=3)
            throw veto("INCOMPLETE_V1_PAIR");
        return flags;
    }
    private static Map<String,MailboxWriterKindEvidenceV2.Kind>
        parseV2(String account,String portable,Map<String,Integer> v1)
        throws IOException{
        Map<String,Integer> flags=new HashMap<>();
        Map<String,MailboxWriterKindEvidenceV2.Kind> kinds=
            new HashMap<>();
        String prefix=account+".properties.g21114-writer-kind-v2-";
        int leaves=0;
        for(String line:portable.split("\\n")){
            if(!line.startsWith("LEAF|"))continue;
            String[] parts=line.split("\\|",3);
            if(parts.length!=3)throw veto("MALFORMED_V2_LEAF");
            String name=parts[1];
            String uuid=exactName(name,prefix,"\\.(?:begin|complete)");
            if(!v1.containsKey(uuid))throw veto("V2_UUID_NOT_IN_V1");
            String stage=name.substring(name.lastIndexOf('.')+1);
            String payload;
            try{
                payload=new String(Base64.getDecoder().decode(parts[2]),
                    StandardCharsets.US_ASCII);
            }catch(IllegalArgumentException malformed){
                throw veto("BAD_V2_BASE64");
            }
            String kindText=null;
            for(String l:payload.split("\\n"))
                if(l.startsWith("KIND="))kindText=l.substring(5);
            MailboxWriterKindEvidenceV2.Kind kind;
            try{
                kind=MailboxWriterKindEvidenceV2.Kind.valueOf(kindText);
            }catch(IllegalArgumentException|NullPointerException malformed){
                throw veto("UNKNOWN_V2_KIND");
            }
            if(!payload.contains("\nACCOUNT="+account+"\n")||
               !payload.contains("\nATTEMPT="+uuid+"\n")||
               !payload.contains("\nSTAGE="+
                   stage.toUpperCase(java.util.Locale.ROOT)+"\n"))
                throw veto("V2_PAYLOAD_FILENAME_DIVERGENCE");
            if(kind==MailboxWriterKindEvidenceV2.Kind.COOPERATING_DIAGNOSTIC)
                throw veto("DIAGNOSTIC_IS_NOT_BUSINESS_KIND");
            MailboxWriterKindEvidenceV2.Kind prior=kinds.putIfAbsent(
                uuid,kind);
            if(prior!=null&&prior!=kind)
                throw veto("V2_UUID_KIND_DISAGREEMENT");
            int priorFlags=flags.getOrDefault(uuid,0);
            if((priorFlags&flag(stage))!=0)
                throw veto("DUPLICATE_V2_STAGE");
            flags.put(uuid,priorFlags|flag(stage));
            leaves++;
        }
        if(leaves!=8||flags.size()!=4||!flags.keySet().equals(
                v1.keySet()))throw veto("V1_V2_ATTEMPT_SET_MISMATCH");
        for(int f:flags.values())if(f!=3)
            throw veto("INCOMPLETE_V2_PAIR");
        Set<MailboxWriterKindEvidenceV2.Kind> unique=
            new HashSet<>(kinds.values());
        if(unique.size()!=4||unique.size()!=RESULT_KINDS.length||
            !unique.containsAll(java.util.Arrays.asList(RESULT_KINDS)))
            throw veto("MISSING_OR_DUPLICATE_REAL_KIND");
        return kinds;
    }
    private static Map<String,MailboxWriterKindEvidenceV2.Kind>
        parseV3(String account,String portable)
        throws IOException{
        String[] lines=portable.split("\\n");
        if(lines.length<15||
           !"SPK-G21125-FOUR-V3-LEAVES-V1".equals(lines[0])||
           !("ACCOUNT="+account).equals(lines[1])||
           !"COUNT=4".equals(lines[2]))
            throw veto("INVALID_V3_HEADER");
        Map<String,MailboxWriterKindEvidenceV2.Kind> result=
            new HashMap<>();
        Set<Integer> indexes=new HashSet<>();
        int found=0;
        String basename=account+".properties";
        for(int i=3;i<lines.length;){
            if(lines[i].isEmpty()){i++;continue;}
            if(i+2>=lines.length||
               !lines[i].startsWith("KIND=")||
               !lines[i+1].startsWith("LEAF=")||
               !lines[i+2].startsWith("BYTES_BASE64="))
                throw veto("MALFORMED_V3_ENTRY");
            int index;
            try{
                index=Integer.parseInt(lines[i].substring(5));
            }catch(NumberFormatException malformed){
                throw veto("UNKNOWN_V3_KIND");
            }
            if(index<0||index>=4||!indexes.add(index))
                throw veto("REPEATED_OR_UNKNOWN_V3_KIND");
            String uuid=exactName(lines[i+1].substring(5),
                basename+RESULT_PREFIXES[index],"\\.result");
            String payload=lines[i+2].substring("BYTES_BASE64=".length());
            if(payload.isEmpty())throw veto("MISSING_V3_RESULT_BYTES");
            try{
                if(Base64.getDecoder().decode(payload).length==0)
                    throw veto("EMPTY_V3_RESULT_BYTES");
            }catch(IllegalArgumentException bad){
                throw veto("BAD_V3_BASE64");
            }
            if(result.putIfAbsent(uuid,RESULT_KINDS[index])!=null)
                throw veto("DUPLICATE_V3_UUID");
            found++;i+=3;
        }
        if(found!=4||indexes.size()!=4||result.size()!=4)
            throw veto("NOT_FOUR_V3_RESULT_UUIDS");
        return result;
    }
    private static void requireOneObservation(
        String account,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation observed
    )throws IOException{
        Map<String,Integer> original=parseV1(
            account,observed.v1.portable);
        Map<String,MailboxWriterKindEvidenceV2.Kind> kinds=
            parseV2(account,observed.v2.portable,original);
        Map<String,MailboxWriterKindEvidenceV2.Kind> results=
            parseV3(account,observed.v3.portable);
        if(!kinds.equals(results))
            throw veto("V3_RESULT_UUID_KIND_MISMATCH");
    }
    /**
     * G21.134 shares the SAME pure, in-memory UUID validation with
     * callers that obtained a pinned physical witness using an
     * additional inside-lease semantic reader. No new lock.
     */
    static void requireCapturedUuidCohort(
        String account,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report physical
    )throws IOException{
        requireOneObservation(account,physical.before);
        requireOneObservation(account,physical.after);
    }

    /**
     * All physical bytes were captured under ONE G21.100 lease by
     * G21.129/130/131. This only analyzes those in-memory snapshots
     * AFTER the existing method returns and releases the lock.
     * No new callback, lock acquisition, publication or mutation.
     */
    static Report comparePinnedImmutableUuidCohort(
        FilePlayerRepository.PathResolver resolver,String account,
        String pinnedV3,String v1Portable,String pinnedV1,
        String pinnedAccount,String pinnedJournal,String pinnedCommit,
        String pinnedV2,long acquisitionMillis,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses callback
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report physical=
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .compareWithContinuousLease(
                    resolver,account,pinnedV3,v1Portable,pinnedV1,
                    pinnedAccount,pinnedJournal,pinnedCommit,
                    pinnedV2,acquisitionMillis,callback);
        requireCapturedUuidCohort(account,physical);
        return new Report(physical);
    }
    private MailboxSevenPlaneImmutableUuidCohortV1(){}
}
