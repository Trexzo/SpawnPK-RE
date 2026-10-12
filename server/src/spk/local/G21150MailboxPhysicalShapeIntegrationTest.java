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
 * G21.150: malformed retained in-memory source digests and impossible
 * sizes must fail closed before internal or historical comparisons.
 * Fixture objects are synthetic; filesystem attrs are real NOFOLLOW
 * samples. No World writer, grant or cleanup authority is used.
 */
public final class G21150MailboxPhysicalShapeIntegrationTest {
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
        if(!value)throw new AssertionError("G21.150 "+message);
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
        String text="G21150_TEST_ONLY_V1\n";
        return instantiate(
            MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterEvidencePortableArchiveV1.Verified.class},
            text,hash(text),null);
    }

    private static MailboxWriterKindEvidenceV2.Snapshot
        archiveV2()throws Exception{
        String text="G21150_TEST_ONLY_V2\n";
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

    /**
     * Position -1 corrupts all FOUR captures identically: the old
     * preflight could then return MATCH, not even internal drift.
     * Positions 0..3 exercise one capture at a time. Sidecar SHA
     * fields are also mutated so old sidecar validation cannot mask
     * the new checked physical-digest diagnostic.
     */
    private static void negative(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report old,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[] all,
        MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity[][] identities,
        int plane,int variant,int position
    )throws Exception{
        String name=variant<2?"sha256":"size";
        Field property=field(
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity.class,name);
        String sidecarName=plane==0?"accountSha":
            plane==1?"journalSha":"commitSha";
        Field sidecar=variant<2&&plane<3?
            field(MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation.class,sidecarName):null;
        Object[] originalValues=new Object[4];
        Object[] originalSidecars=new Object[4];
        int from=position<0?0:position;
        int to=position<0?4:position+1;
        String expected=variant<2?"BAD_PHYSICAL_SHA_":
            "UNSAFE_PHYSICAL_SIZE_";
        Object invalid=variant==0?"INVALID_DIGEST":
            variant==1?
                "ABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD":
                Long.valueOf(variant==2?0L:MAX[plane]+1L);
        int mutations=0;
        boolean denied=false;
        try{
            for(int i=from;i<to;i++){
                originalValues[i]=property.get(identities[i][plane]);
                property.set(identities[i][plane],invalid);
                if(sidecar!=null){
                    originalSidecars[i]=sidecar.get(all[i]);
                    sidecar.set(all[i],invalid);
                }
                mutations++;
            }
            try{
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    old,current);
            }catch(IOException expectedVeto){
                String message=String.valueOf(
                    expectedVeto.getMessage());
                denied=message.contains(
                    "HISTORICAL_IDENTITY_DELTA_UNSAFE_NO_AUTHORITY")&&
                    message.contains(expected);
            }
        }finally{
            for(int i=from;i<from+mutations;i++){
                property.set(identities[i][plane],originalValues[i]);
                if(sidecar!=null)
                    sidecar.set(all[i],originalSidecars[i]);
            }
        }
        check(denied,"malformed physical shape not checked: "+
            LABELS[plane]+" variant="+variant+" position="+position);
        matched(old,current);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21150-physical-shape-");
        try{
            BasicFileAttributes[] attrs=new BasicFileAttributes[6];
            String[] shas=new String[6];
            String[] sourceBodies=new String[6];
            for(int i=0;i<6;i++){
                Path source=root.resolve("source-"+i);
                String body="G21150_REAL_NOFOLLOW_SOURCE_"+i+"\n";
                sourceBodies[i]=body;
                Files.write(source,body.getBytes(StandardCharsets.US_ASCII));
                attrs[i]=Files.readAttributes(source,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                check(attrs[i].isRegularFile() &&
                    attrs[i].fileKey()!=null &&
                    attrs[i].size()>0L &&
                    attrs[i].size()<=MAX[i],
                    "real NOFOLLOW attribute fixture rejected");
                shas[i]=hash(body);
            }
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1=
                archiveV1();
            MailboxWriterKindEvidenceV2.Snapshot v2=archiveV2();
            MailboxFourBoundResultLeavesArchiveV1.Snapshot v3=
                archiveV3(sourceBodies[5]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity[][] identities=
                new MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity[4][6];
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation[] all=
                new MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation[4];
            for(int position=0;position<4;position++){
                for(int plane=0;plane<6;plane++)
                    identities[position][plane]=
                        physical(shas[plane],attrs[plane]);
                all[position]=observation(identities[position],
                    v1,v2,v3,attrs,shas);
            }
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report old=
                report(all[0],all[1]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current=
                report(all[2],all[3]);
            matched(old,current);
            int singles=0,coordinated=0;
            for(int plane=0;plane<6;plane++){
                for(int variant=0;variant<4;variant++){
                    for(int position=0;position<4;position++){
                        negative(old,current,all,identities,
                            plane,variant,position);
                        singles++;
                    }
                    negative(old,current,all,identities,
                        plane,variant,-1);
                    coordinated++;
                }
            }
            boolean zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0 &&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            check(singles==96&&coordinated==24&&zeroLeases,
                "physical shape regression counts or leaked lease");
            System.out.println(
                "G21150_PHYSICAL_SHAPE_DIAGNOSTICS"+
                " realNofollowSources=6 capturePositions=4"+
                " perCaptureMalformedChecks="+singles+
                " coordinatedFourCaptureCorruptions="+coordinated+
                " allRestoredReportsMatch=true zeroJvmLeases="+
                    zeroLeases);
            System.out.println(
                "G21150_PHYSICAL_SHAPE_PASS"+
                " canonicalSha256Validated=true"+
                " accountLimit=1048576 sidecarLimit=1024"+
                " evidenceLeafLimit=512"+
                " changedOrForgedReportNoMatch=true"+
                " independentAuth=false writerFence=false"+
                " atomicEpoch=false rollover=false pruning=false"+
                " cleanup=false unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path source:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(source);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        run();
    }
}
