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
 * G21.151: oversized or malformed retained evidence maps must fail closed
 * before historical comparison, including coordinated four-report edits.
 * Fixture objects are synthetic; filesystem attrs are real NOFOLLOW
 * samples. No World writer, grant or cleanup authority is used.
 */
public final class G21151MailboxEvidenceMapShapeIntegrationTest {
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
        if(!value)throw new AssertionError("G21.151 "+message);
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
        String text="G21151_TEST_ONLY_V1\n";
        return instantiate(
            MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterEvidencePortableArchiveV1.Verified.class},
            text,hash(text),null);
    }

    private static MailboxWriterKindEvidenceV2.Snapshot
        archiveV2()throws Exception{
        String text="G21151_TEST_ONLY_V2\n";
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

    private static final String[] INVALID_NAMES={
        "",
        "invalid/path",
        "invalid\\path",
        "invalid\nname",
        "invalid\tname",
        "invalid\u007fname",
        "x".repeat(201)
    };

    private static Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
        .PhysicalIdentity> expanded(
            Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
                .PhysicalIdentity> original,
            int variant
        ){
        Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
            .PhysicalIdentity> result=new TreeMap<>(original);
        MailboxSevenPlaneContinuousPhysicalWitnessV1.PhysicalIdentity
            sample=result.values().iterator().next();
        if(variant==INVALID_NAMES.length){
            int n=0;
            while(result.size()<69)
                result.put("g21151-extra-"+String.format(
                    java.util.Locale.ROOT,"%03d",n++),sample);
        }else{
            result.put(INVALID_NAMES[variant],sample);
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static void veto(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report old,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[] all,
        Field maps,int variant,int onlyPosition
    )throws Exception{
        Map<?,?>[] originals=new Map[4];
        int start=onlyPosition<0?0:onlyPosition;
        int end=onlyPosition<0?4:onlyPosition+1;
        String expected=variant==INVALID_NAMES.length?
            "EXCESS_EVIDENCE_LEAVES_":"BAD_EVIDENCE_NAME_";
        boolean rejected=false;
        int done=0;
        try{
            for(int i=start;i<end;i++){
                originals[i]=(Map<?,?>)maps.get(all[i]);
                maps.set(all[i],expanded(all[i].evidenceLeafIdentities,
                    variant));
                done++;
            }
            try{
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    old,current);
            }catch(IOException error){
                String m=String.valueOf(error.getMessage());
                rejected=m.contains(
                    "HISTORICAL_IDENTITY_DELTA_UNSAFE_NO_AUTHORITY")&&
                    m.contains(expected);
            }
        }finally{
            for(int i=start;i<start+done;i++)
                maps.set(all[i],originals[i]);
        }
        check(rejected,"unbounded/malformed map accepted variant="+
            variant+" position="+onlyPosition);
        matched(old,current);
    }

    private static void validBoundary(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report old,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[] all,
        Field maps
    )throws Exception{
        Map<?,?>[] originals=new Map[4];
        int done=0;
        try{
            for(int i=0;i<4;i++){
                originals[i]=(Map<?,?>)maps.get(all[i]);
                Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity> replacement=
                    new TreeMap<>(all[i].evidenceLeafIdentities);
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity sample=
                    replacement.values().iterator().next();
                // Retain the 68-entry shape-only negative control
                // while respecting G21.152's inherited scanner limits
                // (32 v1, 32 v2, 4 v3). These are still synthetic
                // names/attributes, not authenticated source records.
                String base=LEAVES[0].substring(0,
                    LEAVES[0].indexOf(".g211"));
                for(int n=1;n<32;n++){
                    String uuid=String.format(java.util.Locale.ROOT,
                        "%08x-0000-0000-0000-000000000000",n);
                    replacement.put(base+
                        ".g21103-writer-attempt-v1-"+uuid+
                        ".begin",sample);
                    replacement.put(base+
                        ".g21114-writer-kind-v2-"+uuid+
                        ".begin",sample);
                }
                check(replacement.size()==68,
                    "synthetic balanced 32/32/4 boundary incorrect");
                maps.set(all[i],
                    java.util.Collections.unmodifiableMap(replacement));
                done++;
            }
            // The total-size bound is necessary, NOT sufficient to
            // prove that 68 entries came from the real G21.129 scan.
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Report delta=
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    old,current);
            check(delta.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY &&
                !delta.historicalReportAuthenticated &&
                !delta.grantAuthorized&&!delta.cleanupAuthorized,
                "shape-only 68-entry boundary cannot confer authority");
        }finally{
            for(int i=0;i<done;i++)maps.set(all[i],originals[i]);
        }
        matched(old,current);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21151-map-shape-");
        try{
            BasicFileAttributes[] attrs=new BasicFileAttributes[6];
            String[] shas=new String[6];
            String[] sourceBodies=new String[6];
            for(int i=0;i<6;i++){
                Path source=root.resolve("source-"+i);
                String body="G21151_NOFOLLOW_SOURCE_"+i+"\n";
                sourceBodies[i]=body;
                Files.write(source,body.getBytes(StandardCharsets.US_ASCII));
                attrs[i]=Files.readAttributes(source,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                check(attrs[i].isRegularFile()&&attrs[i].fileKey()!=null,
                    "NOFOLLOW fixture unavailable");
                shas[i]=hash(body);
            }
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1=
                archiveV1();
            MailboxWriterKindEvidenceV2.Snapshot v2=archiveV2();
            MailboxFourBoundResultLeavesArchiveV1.Snapshot v3=
                archiveV3(sourceBodies[5]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation[] all=
                new MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation[4];
            for(int position=0;position<4;position++){
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity[] ids=
                    new MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity[6];
                for(int plane=0;plane<6;plane++)
                    ids[plane]=physical(shas[plane],attrs[plane]);
                all[position]=observation(ids,v1,v2,v3,attrs,shas);
            }
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report old=
                report(all[0],all[1]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current=
                report(all[2],all[3]);
            matched(old,current);
            Field maps=field(
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation.class,"evidenceLeafIdentities");
            int singles=0,coordinated=0;
            for(int variant=0;variant<=INVALID_NAMES.length;variant++){
                for(int position=0;position<4;position++){
                    veto(old,current,all,maps,variant,position);
                    singles++;
                }
                veto(old,current,all,maps,variant,-1);
                coordinated++;
            }
            validBoundary(old,current,all,maps);
            boolean zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0 &&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            check(singles==32&&coordinated==8&&zeroLeases,
                "missing map cases or leaked lease");
            System.out.println(
                "G21151_EVIDENCE_MAP_DIAGNOSTICS"+
                " realNofollowSources=6 positions=4"+
                " singleReportInvalidMaps="+singles+
                " coordinatedFourReportInvalidMaps="+coordinated+
                " acceptedShapeOnlyBoundary68=true"+
                " restoredHealthyMatch=true zeroJvmLeases="+zeroLeases);
            System.out.println(
                "G21151_EVIDENCE_MAP_PASS"+
                " oversizedMap69Vetoed=true malformedNamesVetoed=true"+
                " originalG21129MaxLeaves=68"+
                " independentAuth=false rawWriterFence=false"+
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
