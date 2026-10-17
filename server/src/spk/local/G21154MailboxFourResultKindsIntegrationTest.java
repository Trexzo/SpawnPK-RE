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
 * G21.154: retained successful physical observations must have one
 * each of four original v3 result kinds. Fixture is synthetic,
 * backed by six actual NOFOLLOW source attributes; no live grant.
 */
public final class G21154MailboxFourResultKindsIntegrationTest {
    private static final String[] LEAVES={
        "g21154.properties.g21103-writer-attempt-v1-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21154.properties.g21114-writer-kind-v2-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21154.properties.g21120-commit-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result"
    };
    private static final String[] LABELS={
        "ACCOUNT","PREPARED_JOURNAL","COMMIT",
        "LEAF:"+LEAVES[0],"LEAF:"+LEAVES[1],"LEAF:"+LEAVES[2]
    };
    private static final long[] MAX={1048576L,1024L,1024L,512L,512L,512L};

    private static void check(boolean value,String message){
        if(!value)throw new AssertionError("G21.154 "+message);
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
        String text="G21154_TEST_ONLY_V1\n";
        return instantiate(
            MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterEvidencePortableArchiveV1.Verified.class},
            text,hash(text),null);
    }

    private static MailboxWriterKindEvidenceV2.Snapshot
        archiveV2()throws Exception{
        String text="G21154_TEST_ONLY_V2\n";
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
        String text=fixtureV3Portable("g21154.properties",sourceBody);
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
        "ffffffff-ffff-ffff-ffff-ffffffffffff";
    private static final String OTHER_UUID=
        "01234567-89ab-cdef-0123-456789abcdef";

    private static String[] allFourNames(){
        final String basename=LEAVES[2].substring(0,
            LEAVES[2].indexOf(".g21120-"));
        return new String[]{
            LEAVES[2],
            basename+".g21121-prepared-result-v3-"+UUID+".result",
            basename+".g21122-strict-prepared-result-v3-"+UUID+".result",
            basename+".g21123-world-terminal-result-v3-"+UUID+".result"
        };
    }

    private static Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
        .PhysicalIdentity> altered(
            Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity> original,
            int kind,boolean duplicated
        ){
        final String[] kinds=allFourNames();
        Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity> changed=new TreeMap<>(original);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.PhysicalIdentity
            removed=changed.remove(kinds[kind]);
        check(removed!=null,"fixture missing requested v3 kind "+kind);
        if(duplicated){
            // Remain at exactly four v3 leaves but duplicate a
            // different kind with a second canonical UUID.
            int other=(kind+1)%4;
            String newName=kinds[other].replace(UUID,OTHER_UUID);
            check(!changed.containsKey(newName),
                "duplicate-kind test accidentally overwrote source");
            changed.put(newName,removed);
        }
        return java.util.Collections.unmodifiableMap(changed);
    }

    private static void negative(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[] captures,
        Field maps,int kind,boolean duplicate,int position
    )throws Exception{
        Map<?,?>[] restore=new Map[4];
        int start=position<0?0:position;
        int end=position<0?4:position+1;
        String expected=duplicate?"DUPLICATE_V3_RESULT_KIND_":
            "MISSING_V3_RESULT_KIND_";
        int modified=0;
        boolean checkedVeto=false;
        try{
            for(int i=start;i<end;i++){
                restore[i]=(Map<?,?>)maps.get(captures[i]);
                maps.set(captures[i],altered(
                    captures[i].evidenceLeafIdentities,
                    kind,duplicate));
                modified++;
            }
            try{
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    historical,current);
            }catch(IOException problem){
                String why=String.valueOf(problem.getMessage());
                checkedVeto=why.contains(
                    "HISTORICAL_IDENTITY_DELTA_UNSAFE_NO_AUTHORITY")&&
                    why.contains(expected);
            }
        }finally{
            for(int i=start;i<start+modified;i++)
                maps.set(captures[i],restore[i]);
        }
        check(checkedVeto,"v3 result kind was not checked kind="+kind+
            " duplicate="+duplicate+" position="+position);
        matched(historical,current);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21154-result-kinds-");
        try{
            BasicFileAttributes[] attrs=new BasicFileAttributes[6];
            String[] shas=new String[6];
            String[] sourceBodies=new String[6];
            for(int i=0;i<6;i++){
                Path file=root.resolve("physical-"+i);
                String text="G21154_NOFOLLOW_SOURCE_"+i+"\n";
                sourceBodies[i]=text;
                Files.write(file,text.getBytes(StandardCharsets.US_ASCII));
                attrs[i]=Files.readAttributes(file,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                check(attrs[i].isRegularFile()&&attrs[i].fileKey()!=null,
                    "missing NOFOLLOW source attrs");
                shas[i]=hash(text);
            }
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1=archiveV1();
            MailboxWriterKindEvidenceV2.Snapshot v2=archiveV2();
            MailboxFourBoundResultLeavesArchiveV1.Snapshot v3=archiveV3(sourceBodies[5]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation[] captures=
                    new MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .Observation[4];
            for(int index=0;index<4;index++){
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity[] identities=
                    new MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity[6];
                for(int i=0;i<6;i++)
                    identities[i]=physical(shas[i],attrs[i]);
                captures[index]=observation(identities,
                    v1,v2,v3,attrs,shas);
                String[] kinds=allFourNames();
                int n=0;
                for(String name:kinds)
                    if(captures[index].evidenceLeafIdentities
                        .containsKey(name))n++;
                check(n==4,"baseline missing original four v3 kinds");
            }
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical=
                report(captures[0],captures[1]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current=
                report(captures[2],captures[3]);
            matched(historical,current);
            Field maps=field(
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation.class,"evidenceLeafIdentities");
            int singles=0,coordinated=0;
            for(int kind=0;kind<4;kind++){
                for(int mode=0;mode<2;mode++){
                    boolean duplicate=mode==1;
                    for(int position=0;position<4;position++){
                        negative(historical,current,captures,maps,
                            kind,duplicate,position);
                        singles++;
                    }
                    negative(historical,current,captures,maps,
                        kind,duplicate,-1);
                    coordinated++;
                }
            }
            boolean zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            check(singles==32&&coordinated==8&&zeroLeases,
                "expected 40 negative cases with zero leases");
            System.out.println(
                "G21154_FOUR_V3_RESULT_DIAGNOSTICS"+
                " realNofollowSources=6 originalDistinctV3Kinds=4"+
                " individuallyCorruptedCaptures="+singles+
                " coherentlyCorruptedFourCaptures="+coordinated+
                " restoredHealthyMatch=true zeroJvmLeases="+zeroLeases);
            System.out.println(
                "G21154_FOUR_V3_RESULT_PASS"+
                " exactlyFourResultKindsRequired=true"+
                " duplicateKindVetoed=true missingKindVetoed=true"+
                " independentProvenance=false rawWriterFence=false"+
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
        check(args.length==0,"unexpected args");
        run();
    }
}
