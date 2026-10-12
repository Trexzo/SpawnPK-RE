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
 * G21.153: reject noncanonical retained v1/v2/v3 evidence UUIDs,
 * including four coherently altered in-memory observations. Physical
 * attributes come from real NOFOLLOW files; the snapshots are
 * synthetic read-only fixtures, not authenticated historical inputs.
 */
public final class G21153MailboxEvidenceCanonicalUuidIntegrationTest {
    private static final String[] LEAVES={
        "g21153.properties.g21103-writer-attempt-v1-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21153.properties.g21114-writer-kind-v2-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21153.properties.g21120-commit-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result"
    };
    private static final String[] LABELS={
        "ACCOUNT","PREPARED_JOURNAL","COMMIT",
        "LEAF:"+LEAVES[0],"LEAF:"+LEAVES[1],"LEAF:"+LEAVES[2]
    };
    private static final long[] MAX={1048576L,1024L,1024L,512L,512L,512L};

    private static void check(boolean value,String message){
        if(!value)throw new AssertionError("G21.153 "+message);
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
        String text="G21153_TEST_ONLY_V1\n";
        return instantiate(
            MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterEvidencePortableArchiveV1.Verified.class},
            text,hash(text),null);
    }

    private static MailboxWriterKindEvidenceV2.Snapshot
        archiveV2()throws Exception{
        String text="G21153_TEST_ONLY_V2\n";
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
        String text=fixtureV3Portable("g21153.properties",sourceBody);
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

    private static final String BASE_UUID=
        "ffffffff-ffff-ffff-ffff-ffffffffffff";
    private static final String VALID_OTHER_UUID=
        "01234567-89ab-cdef-0123-456789abcdef";
    private static final String[] BAD_UUIDS={
        "test",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "0000000-0000-0000-0000-000000000001",
        "gggggggg-0000-0000-0000-000000000001",
        "ABCDEF12-ABCD-ABCD-ABCD-ABCDEF123456",
        "00000000--0000-0000-0000-000000000001",
        "000000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000"
    };

    private static Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
        .PhysicalIdentity> substituted(
            Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity> original,
            int family,String replacementUuid
        ){
        Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity> changed=new TreeMap<>(original);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.PhysicalIdentity
            source=changed.remove(LEAVES[family]);
        check(source!=null,"missing original leaf to replace");
        String altered=LEAVES[family].replace(BASE_UUID,replacementUuid);
        check(!changed.containsKey(altered),"duplicate replaced leaf");
        changed.put(altered,source);
        return java.util.Collections.unmodifiableMap(changed);
    }

    private static void malformed(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[] captures,
        Field maps,int family,int variant,int position
    )throws Exception{
        Map<?,?>[] originals=new Map[4];
        int from=position<0?0:position;
        int to=position<0?4:position+1;
        int modified=0;
        boolean expectedVeto=false;
        try{
            for(int i=from;i<to;i++){
                originals[i]=(Map<?,?>)maps.get(captures[i]);
                maps.set(captures[i],substituted(
                    captures[i].evidenceLeafIdentities,
                    family,BAD_UUIDS[variant]));
                modified++;
            }
            try{
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    historical,current);
            }catch(IOException veto){
                String diagnostic=String.valueOf(veto.getMessage());
                expectedVeto=diagnostic.contains(
                    "HISTORICAL_IDENTITY_DELTA_UNSAFE_NO_AUTHORITY")&&
                    diagnostic.contains("NONCANONICAL_EVIDENCE_UUID_");
            }
        }finally{
            for(int i=from;i<from+modified;i++)
                maps.set(captures[i],originals[i]);
        }
        check(expectedVeto,"missing checked UUID veto family="+family+
            " variant="+variant+" position="+position);
        matched(historical,current);
    }

    private static void validAlternative(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[] captures,
        Field maps,int family
    )throws Exception{
        Map<?,?>[] originals=new Map[4];
        // G21.155: a valid alternative v3 leaf identifier requires
        // BOTH the physical map and its v3 portable inventory to
        // describe the same key. The archive is still synthetic.
        Field portableField=field(
            MailboxFourBoundResultLeavesArchiveV1.Snapshot.class,
            "portable");
        Field shaField=field(
            MailboxFourBoundResultLeavesArchiveV1.Snapshot.class,
            "sha256");
        MailboxFourBoundResultLeavesArchiveV1.Snapshot archive=
            captures[0].v3;
        String priorPortable=(String)portableField.get(archive);
        String priorSha=(String)shaField.get(archive);
        int count=0;
        try{
            for(int i=0;i<4;i++){
                originals[i]=(Map<?,?>)maps.get(captures[i]);
                maps.set(captures[i],substituted(
                    captures[i].evidenceLeafIdentities,
                    family,VALID_OTHER_UUID));
                count++;
            }
            if(family==2){
                String changed=priorPortable.replace(
                    LEAVES[2],LEAVES[2].replace(
                        BASE_UUID,VALID_OTHER_UUID));
                check(!changed.equals(priorPortable),
                    "portable v3 alternative key was not updated");
                portableField.set(archive,changed);
                shaField.set(archive,hash(changed));
            }
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Report delta=
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    historical,current);
            check(delta.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
                !delta.historicalReportAuthenticated&&
                !delta.grantAuthorized&&!delta.cleanupAuthorized&&
                !delta.replayAuthorized&&!delta.sessionAuthorized,
                "canonical alternate UUID shape unexpectedly rejected "+
                    family);
        }finally{
            portableField.set(archive,priorPortable);
            shaField.set(archive,priorSha);
            for(int i=0;i<count;i++)
                maps.set(captures[i],originals[i]);
        }
        matched(historical,current);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21153-uuid-shape-");
        try{
            BasicFileAttributes[] attrs=new BasicFileAttributes[6];
            String[] shas=new String[6];
            String[] sourceBodies=new String[6];
            for(int i=0;i<6;i++){
                Path source=root.resolve("source-"+i);
                String body="G21153_REAL_NOFOLLOW_"+i+"\n";
                sourceBodies[i]=body;
                Files.write(source,body.getBytes(StandardCharsets.US_ASCII));
                attrs[i]=Files.readAttributes(source,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                check(attrs[i].isRegularFile()&&attrs[i].fileKey()!=null,
                    "real NOFOLLOW fixture unavailable");
                shas[i]=hash(body);
            }
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1=archiveV1();
            MailboxWriterKindEvidenceV2.Snapshot v2=archiveV2();
            MailboxFourBoundResultLeavesArchiveV1.Snapshot v3=archiveV3(sourceBodies[5]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation[] captures=
                    new MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .Observation[4];
            for(int j=0;j<4;j++){
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity[] physical=
                    new MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity[6];
                for(int i=0;i<6;i++)
                    physical[i]=physical(shas[i],attrs[i]);
                captures[j]=observation(physical,v1,v2,v3,attrs,shas);
            }
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical=
                report(captures[0],captures[1]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current=
                report(captures[2],captures[3]);
            matched(historical,current);
            Field maps=field(
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation.class,"evidenceLeafIdentities");
            int individual=0,coordinated=0,validAlternates=0;
            for(int family=0;family<3;family++){
                for(int variant=0;variant<BAD_UUIDS.length;variant++){
                    for(int position=0;position<4;position++){
                        malformed(historical,current,captures,maps,
                            family,variant,position);
                        individual++;
                    }
                    malformed(historical,current,captures,maps,
                        family,variant,-1);
                    coordinated++;
                }
                validAlternative(historical,current,captures,maps,family);
                validAlternates++;
            }
            boolean zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            check(individual==96&&coordinated==24&&
                validAlternates==3&&zeroLeases,
                "case totals or unexpected active lease");
            System.out.println("G21153_CANONICAL_UUID_DIAGNOSTICS"+
                " nofollowSources=6 sourceFamilies=3"+
                " invalidUuidVariants=8 singleCaptureVetoes="+individual+
                " coordinatedFourCaptureVetoes="+coordinated+
                " validCanonicalAlternates="+validAlternates+
                " originalSnapshotsRestored=true zeroJvmLeases="+
                zeroLeases);
            System.out.println("G21153_CANONICAL_UUID_PASS"+
                " lowercaseHex8_4_4_4_12Required=true"+
                " inheritedSyntheticFixturesCanonical=true"+
                " independentSourceAuth=false rawWriterFence=false"+
                " atomicEpoch=false rollover=false pruning=false"+
                " cleanup=false unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        run();
    }
}
