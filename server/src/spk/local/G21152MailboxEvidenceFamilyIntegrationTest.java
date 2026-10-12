package spk.local;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * G21.152: per-family evidence counts, unknown writer families, and mixed
 * basenames must fail closed even when total retained leaves <=68.
 * Fixture objects are synthetic; filesystem attrs are real NOFOLLOW
 * samples. No World writer, grant or cleanup authority is used.
 */
public final class G21152MailboxEvidenceFamilyIntegrationTest {
    private static final String[] LEAVES={
        "g21150.properties.g21103-writer-attempt-v1-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21150.properties.g21114-writer-kind-v2-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21150.properties.g21120-commit-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result"
    };
    private static final String[] LABELS={
        "ACCOUNT","PREPARED_JOURNAL","COMMIT",
        "LEAF:"+LEAVES[0],"LEAF:"+LEAVES[1],"LEAF:"+LEAVES[2]
    };
    private static final long[] MAX={1048576L,1024L,1024L,512L,512L,512L};

    private static void check(boolean value,String message){
        if(!value)throw new AssertionError("G21.152 "+message);
    }

    private static String hash(String value)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(
            value.getBytes(StandardCharsets.US_ASCII));
        char[] chars="0123456789abcdef".toCharArray();
        StringBuilder result=new StringBuilder(64);
        for(byte b:bytes){
            result.append(chars[(b>>>4)&15]);
            result.append(chars[b&15]);
        }
        return result.toString();
    }

    private static <T> T instantiate(
        Class<T> type,Class<?>[] parameters,Object...args
    )throws Exception{
        Constructor<T> ctor=type.getDeclaredConstructor(parameters);
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }

    private static MailboxSevenPlaneContinuousPhysicalWitnessV1
        .PhysicalIdentity physical(
            String sha,BasicFileAttributes attrs
        )throws Exception{
        return instantiate(
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity.class,
            new Class<?>[]{String.class,BasicFileAttributes.class},
            sha,attrs);
    }

    private static MailboxWriterEvidencePortableArchiveV1.Snapshot
        archiveV1()throws Exception{
        String text="G21152_TEST_ONLY_V1\n";
        return instantiate(
            MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterEvidencePortableArchiveV1.Verified.class},
            text,hash(text),null);
    }

    private static MailboxWriterKindEvidenceV2.Snapshot
        archiveV2()throws Exception{
        String text="G21152_TEST_ONLY_V2\n";
        return instantiate(
            MailboxWriterKindEvidenceV2.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterKindEvidenceV2.Report.class},
            text,hash(text),null);
    }


    /**
     * Synthetic shape-only v3 portable inventory. Physical fixture
     * source bytes are NOT authenticated by this string. Use actual
     * G21.125 KIND/LEAF/BYTES_BASE64 formatting so G21.155 can
     * compare leaf names without changing inherited test purpose.
     */
    private static String fixtureV3Portable(String basename,String sourceBody){
        String uuid="ffffffff-ffff-ffff-ffff-ffffffffffff";
        String[] leaves={
            basename+".g21120-commit-result-v3-"+uuid+".result",
            basename+".g21121-prepared-result-v3-"+uuid+".result",
            basename+".g21122-strict-prepared-result-v3-"+uuid+".result",
            basename+".g21123-world-terminal-result-v3-"+uuid+".result"
        };
        String account=basename.endsWith(".properties")?
            basename.substring(0,basename.length()-11):basename;
        StringBuilder result=new StringBuilder(
            "SPK-G21125-FOUR-V3-LEAVES-V1\n"+
            "ACCOUNT="+account+"\nCOUNT=4\n");
        String encoded=java.util.Base64.getEncoder().encodeToString(
            sourceBody.getBytes(StandardCharsets.US_ASCII));
        for(int kind=0;kind<4;kind++)
            result.append("KIND=").append(kind).append('\n')
                .append("LEAF=").append(leaves[kind]).append('\n')
                .append("BYTES_BASE64=").append(encoded).append('\n');
        return result.toString();
    }

    private static MailboxFourBoundResultLeavesArchiveV1.Snapshot
        archiveV3(String sourceBody)throws Exception{
        String text=fixtureV3Portable("g21150.properties",sourceBody);
        return instantiate(
            MailboxFourBoundResultLeavesArchiveV1.Snapshot.class,
            new Class<?>[]{String.class,String.class},
            text,hash(text));
    }

    private static MailboxSevenPlaneContinuousPhysicalWitnessV1
        .Observation observation(
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity[] physical,
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1,
            MailboxWriterKindEvidenceV2.Snapshot v2,
            MailboxFourBoundResultLeavesArchiveV1.Snapshot v3,
            BasicFileAttributes[] attrs,String[] shas
        )throws Exception{
        Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity> leaves=new TreeMap<>();
        for(int i=3;i<6;i++)leaves.put(LEAVES[i-3],physical[i]);
        // Keep all four v3 kinds in synthetic retained reports.
        String basename=LEAVES[2].substring(0,
            LEAVES[2].indexOf(".g21120-"));
        String[] names={
            basename+".g21121-prepared-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result",
            basename+".g21122-strict-prepared-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result",
            basename+".g21123-world-terminal-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result"
        };
        for(String name:names)
            leaves.put(name,physical(shas[5],attrs[5]));
        return instantiate(
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation.class,
            new Class<?>[]{
                MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
                MailboxWriterKindEvidenceV2.Snapshot.class,
                MailboxFourBoundResultLeavesArchiveV1.Snapshot.class,
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity.class,
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity.class,
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity.class,
                Map.class
            },
            v1,v2,v3,physical[0],physical[1],physical[2],leaves);
    }

    private static MailboxSevenPlaneContinuousPhysicalWitnessV1.Report
        report(
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation a,
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation b
        )throws Exception{
        return instantiate(
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report.class,
            new Class<?>[]{
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation.class,
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation.class},
            a,b);
    }

    private static void matched(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report old,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current
    )throws Exception{
        MailboxSevenPlaneHistoricalIdentityDeltaV1.Report delta=
            MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(old,current);
        check(delta.status==
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                .MATCHED_OBSERVED_METADATA_NO_AUTHORITY &&
            delta.changedSources.isEmpty() &&
            delta.uncertainSources.isEmpty() &&
            !delta.cleanupAuthorized && !delta.grantAuthorized,
            "legitimate fixture should MATCH without authority");
    }

    private static Field field(Class<?> type,String name)
        throws Exception{
        Field found=type.getDeclaredField(name);
        found.setAccessible(true);
        return found;
    }

    private static final String UUID=
        "00000000-0000-0000-0000-000000000001";

    private static Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
        .PhysicalIdentity> mutate(
            Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity> original,
            int variant
        ){
        Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity> result=new TreeMap<>(original);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.PhysicalIdentity
            sample=result.values().iterator().next();
        String base=LEAVES[0].substring(0,
            LEAVES[0].indexOf(".g211"));
        if(variant<=2){
            int extra=variant==2?4:32;
            for(int n=1;n<=extra;n++){
                String uuid=String.format(java.util.Locale.ROOT,
                    "%08x-0000-0000-0000-000000000000",n);
                String tail=variant==0?
                    ".g21103-writer-attempt-v1-"+uuid+".begin":
                    variant==1?
                    ".g21114-writer-kind-v2-"+uuid+".complete":
                    (n==1?".g21120-commit-result-v3-":
                        n==2?".g21121-prepared-result-v3-":
                        n==3?".g21122-strict-prepared-result-v3-":
                            ".g21123-world-terminal-result-v3-")+
                            uuid+".result";
                result.put(base+tail,sample);
            }
        }else{
            String name=variant==3?
                base+".g21199-unknown-result-v3-"+UUID+".result":
                variant==4?
                base+".g21103-writer-attempt-v9-"+UUID+".begin":
                variant==5?
                "another-account.properties.g21103-writer-attempt-v1-"+
                    UUID+".begin":
                variant==6?
                base+".g21120-world-terminal-result-v3-"+UUID+
                    ".result":
                base+".g21114-writer-kind-v2-"+UUID+".done";
            result.put(name,sample);
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static void reject(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[] all,
        Field mapField,int variant,int position
    )throws Exception{
        Map<?,?>[] originals=new Map[4];
        int first=position<0?0:position;
        int last=position<0?4:position+1;
        int done=0;
        boolean vetoed=false;
        // G21.154: the original v3 source reader rejects a
        // repeated result kind before it could count five leaves.
        // With all four real kinds in the fixture, the old v3
        // over-capacity mutation reaches DUPLICATE first; it must
        // still fail closed with this more specific checked veto.
        String reason=variant<=1?"EXCESS_EVIDENCE_FAMILY_":
            variant==2?"DUPLICATE_V3_RESULT_KIND_":
            variant==5?"MIXED_EVIDENCE_BASENAME_":
                "UNKNOWN_EVIDENCE_FAMILY_";
        try{
            for(int i=first;i<last;i++){
                originals[i]=(Map<?,?>)mapField.get(all[i]);
                mapField.set(all[i],mutate(
                    all[i].evidenceLeafIdentities,variant));
                done++;
            }
            try{
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    historical,current);
            }catch(IOException expected){
                String message=String.valueOf(expected.getMessage());
                vetoed=message.contains(
                    "HISTORICAL_IDENTITY_DELTA_UNSAFE_NO_AUTHORITY")&&
                    message.contains(reason);
            }
        }finally{
            for(int i=first;i<first+done;i++)
                mapField.set(all[i],originals[i]);
        }
        check(vetoed,"family/quota not checked, variant="+variant+
            " position="+position);
        matched(historical,current);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21152-family-");
        try{
            BasicFileAttributes[] attrs=new BasicFileAttributes[6];
            String[] shas=new String[6];
            String[] sourceBodies=new String[6];
            for(int i=0;i<6;i++){
                Path target=root.resolve("source-"+i);
                String contents="G21152_REAL_NOFOLLOW_"+i+"\n";
                sourceBodies[i]=contents;
                Files.write(target,contents.getBytes(StandardCharsets.US_ASCII));
                attrs[i]=Files.readAttributes(target,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                check(attrs[i].isRegularFile()&&attrs[i].fileKey()!=null,
                    "missing actual NOFOLLOW fileKey");
                shas[i]=hash(contents);
            }
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1=archiveV1();
            MailboxWriterKindEvidenceV2.Snapshot v2=archiveV2();
            MailboxFourBoundResultLeavesArchiveV1.Snapshot v3=archiveV3(sourceBodies[5]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation[] all=
                new MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation[4];
            for(int index=0;index<4;index++){
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity[] identities=
                    new MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity[6];
                for(int plane=0;plane<6;plane++)
                    identities[plane]=physical(shas[plane],attrs[plane]);
                all[index]=observation(identities,v1,v2,v3,attrs,shas);
            }
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical=
                report(all[0],all[1]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current=
                report(all[2],all[3]);
            matched(historical,current);
            Field maps=field(
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation.class,"evidenceLeafIdentities");
            int singles=0,coordinated=0;
            for(int variant=0;variant<8;variant++){
                for(int position=0;position<4;position++){
                    reject(historical,current,all,maps,variant,position);
                    singles++;
                }
                reject(historical,current,all,maps,variant,-1);
                coordinated++;
            }
            boolean leasesZero=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(singles==32&&coordinated==8&&leasesZero,
                "expected 40 negative controls and no leases");
            System.out.println("G21152_EVIDENCE_FAMILY_DIAGNOSTICS"+
                " nofollowSources=6 singleCaptureVetoes="+singles+
                " fourCaptureVetoes="+coordinated+
                " quotasV1=32 quotasV2=32 quotasV3=4"+
                " originalSnapshotsRestored=true zeroJvmLeases="+
                leasesZero);
            System.out.println("G21152_EVIDENCE_FAMILY_PASS"+
                " independentlyBoundedFamilies=true"+
                " unknownNamespacesAndMixedBasenamesRejected=true"+
                " canonicalUuidProved=false independentAuth=false"+
                " rawWriterFence=false atomicEpoch=false"+
                " rollover=false pruning=false cleanup=false"+
                " unlink=false grant=false replay=false session=false"+
                " release=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}
