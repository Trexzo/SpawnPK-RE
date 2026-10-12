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
 * G21.155: v3 portable four-leaf names must agree with physical
 * map even for four synthetically coordinated retained captures.
 * Six original NOFOLLOW source attrs are real; archive is test-only.
 */
public final class G21155MailboxV3PortablePhysicalBindingIntegrationTest {
    private static final String[] LEAVES={
        "g21155.properties.g21103-writer-attempt-v1-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21155.properties.g21114-writer-kind-v2-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21155.properties.g21120-commit-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result"
    };
    private static final String[] LABELS={
        "ACCOUNT","PREPARED_JOURNAL","COMMIT",
        "LEAF:"+LEAVES[0],"LEAF:"+LEAVES[1],"LEAF:"+LEAVES[2]
    };
    private static final long[] MAX={1048576L,1024L,1024L,512L,512L,512L};

    private static void check(boolean value,String message){
        if(!value)throw new AssertionError("G21.155 "+message);
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
        String text="G21155_TEST_ONLY_V1\n";
        return instantiate(
            MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterEvidencePortableArchiveV1.Verified.class},
            text,hash(text),null);
    }

    private static MailboxWriterKindEvidenceV2.Snapshot
        archiveV2()throws Exception{
        String text="G21155_TEST_ONLY_V2\n";
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
        String text=fixtureV3Portable("g21155.properties",sourceBody);
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

    private static String[] v3Names(){
        String base=LEAVES[2].substring(0,
            LEAVES[2].indexOf(".g21120-"));
        return new String[]{
            LEAVES[2],
            base+".g21121-prepared-result-v3-"+UUID+".result",
            base+".g21122-strict-prepared-result-v3-"+UUID+".result",
            base+".g21123-world-terminal-result-v3-"+UUID+".result"
        };
    }

    private static void negative(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[] all,
        Field mapField,Field portableField,Field shaField,
        int kind,int mode,int position
    )throws Exception{
        final int first=position<0?0:position;
        final int last=position<0?4:position+1;
        Map<?,?>[] oldMaps=new Map[4];
        String[] oldPortables=new String[4],oldHashes=new String[4];
        int done=0;
        boolean checkedVeto=false;
        String expected=position<0?
            (mode<=1?"V3_PORTABLE_LEAF_MISMATCH_":
                "BAD_V3_PORTABLE_INVENTORY_"):
            "INTERNAL_BEFORE_AFTER_DRIFT_";
        try{
            for(int i=first;i<last;i++){
                oldMaps[i]=(Map<?,?>)mapField.get(all[i]);
                oldPortables[i]=(String)portableField.get(all[i].v3);
                oldHashes[i]=(String)shaField.get(all[i].v3);
                String name=v3Names()[kind];
                String changedName=name.replace(UUID,OTHER_UUID);
                if(mode==0){
                    String modified=oldPortables[i].replace(name,
                        changedName);
                    check(!modified.equals(oldPortables[i]),
                        "expected portable LEAF was not present");
                    portableField.set(all[i].v3,modified);
                    shaField.set(all[i].v3,hash(modified));
                }else if(mode==1){
                    Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity> changed=
                        new TreeMap<>(all[i].evidenceLeafIdentities);
                    MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity prior=changed.remove(name);
                    check(prior!=null,"missing target physical v3 leaf");
                    changed.put(changedName,prior);
                    mapField.set(all[i],
                        java.util.Collections.unmodifiableMap(changed));
                }else{
                    String replaced=mode==2?
                        oldPortables[i].replace("COUNT=4\n","COUNT=5\n"):
                        oldPortables[i].replace(
                            "KIND=0\n","KIND=9\n");
                    check(!replaced.equals(oldPortables[i]),
                        "expected portable heading not present");
                    portableField.set(all[i].v3,replaced);
                    shaField.set(all[i].v3,hash(replaced));
                }
                done++;
            }
            try{
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    historical,current);
            }catch(IOException error){
                String diagnostic=String.valueOf(error.getMessage());
                checkedVeto=diagnostic.contains(
                    "HISTORICAL_IDENTITY_DELTA_UNSAFE_NO_AUTHORITY")&&
                    diagnostic.contains(expected);
            }
        }finally{
            for(int i=first;i<first+done;i++){
                portableField.set(all[i].v3,oldPortables[i]);
                shaField.set(all[i].v3,oldHashes[i]);
                mapField.set(all[i],oldMaps[i]);
            }
        }
        check(checkedVeto,"missing checked V3 portable/physical veto"+
            " mode="+mode+" kind="+kind+" position="+position);
        matched(historical,current);
    }

    private static void coordinatedAlternative(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[] all,
        Field mapField,Field portableField,Field shaField,
        int kind
    )throws Exception{
        Map<?,?>[] oldMaps=new Map[4];
        String[] oldPortables=new String[4],oldHashes=new String[4];
        int modified=0;
        try{
            for(int i=0;i<4;i++){
                oldMaps[i]=(Map<?,?>)mapField.get(all[i]);
                oldPortables[i]=(String)portableField.get(all[i].v3);
                oldHashes[i]=(String)shaField.get(all[i].v3);
                String oldName=v3Names()[kind];
                String newName=oldName.replace(UUID,OTHER_UUID);
                Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity> map=
                    new TreeMap<>(all[i].evidenceLeafIdentities);
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity identity=map.remove(oldName);
                check(identity!=null,"missing original v3 leaf");
                map.put(newName,identity);
                mapField.set(all[i],
                    java.util.Collections.unmodifiableMap(map));
                String newPortable=oldPortables[i].replace(
                    oldName,newName);
                check(!newPortable.equals(oldPortables[i]),
                    "v3 inventory did not contain name");
                portableField.set(all[i].v3,newPortable);
                shaField.set(all[i].v3,hash(newPortable));
                modified++;
            }
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Report result=
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    historical,current);
            check(result.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
                !result.historicalReportAuthenticated&&
                !result.grantAuthorized&&!result.cleanupAuthorized&&
                !result.replayAuthorized&&!result.releaseAuthorized,
                "coherently changed physical + portable sources "+
                    "must remain no-authority observational match");
        }finally{
            for(int i=0;i<modified;i++){
                portableField.set(all[i].v3,oldPortables[i]);
                shaField.set(all[i].v3,oldHashes[i]);
                mapField.set(all[i],oldMaps[i]);
            }
        }
        matched(historical,current);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21155-portable-binding-");
        try{
            BasicFileAttributes[] attrs=new BasicFileAttributes[6];
            String[] shas=new String[6];
            String[] sourceBodies=new String[6];
            for(int i=0;i<6;i++){
                Path file=root.resolve("physical-"+i);
                String body="G21155_NOFOLLOW_SOURCE_"+i+"\n";
                sourceBodies[i]=body;
                Files.write(file,body.getBytes(StandardCharsets.US_ASCII));
                attrs[i]=Files.readAttributes(file,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                check(attrs[i].isRegularFile()&&attrs[i].fileKey()!=null,
                    "original NOFOLLOW source unavailable");
                shas[i]=hash(body);
            }
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1=archiveV1();
            MailboxWriterKindEvidenceV2.Snapshot v2=archiveV2();
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation[] all=new
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation[4];
            for(int i=0;i<4;i++){
                MailboxFourBoundResultLeavesArchiveV1.Snapshot v3=
                    archiveV3(sourceBodies[5]);
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity[] ids=new
                    MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity[6];
                for(int j=0;j<6;j++)
                    ids[j]=physical(shas[j],attrs[j]);
                all[i]=observation(ids,v1,v2,v3,attrs,shas);
            }
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical=
                report(all[0],all[1]);
            MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current=
                report(all[2],all[3]);
            matched(historical,current);
            Field maps=field(
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation.class,"evidenceLeafIdentities");
            Field portable=field(
                MailboxFourBoundResultLeavesArchiveV1.Snapshot.class,
                "portable");
            Field sha=field(
                MailboxFourBoundResultLeavesArchiveV1.Snapshot.class,
                "sha256");
            int single=0,coordinated=0,positive=0;
            for(int kind=0;kind<4;kind++){
                for(int mode=0;mode<2;mode++){
                    for(int position=0;position<4;position++){
                        negative(historical,current,all,
                            maps,portable,sha,kind,mode,position);
                        single++;
                    }
                    negative(historical,current,all,maps,portable,sha,
                        kind,mode,-1);
                    coordinated++;
                }
                coordinatedAlternative(historical,current,all,
                    maps,portable,sha,kind);
                positive++;
            }
            for(int mode=2;mode<4;mode++){
                for(int position=0;position<4;position++){
                    negative(historical,current,all,
                        maps,portable,sha,0,mode,position);
                    single++;
                }
                negative(historical,current,all,maps,portable,sha,
                    0,mode,-1);
                coordinated++;
            }
            boolean zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            check(single==40&&coordinated==10&&positive==4&&
                zeroLeases,"v3 portable binding test counts/leases");
            System.out.println(
                "G21155_V3_PORTABLE_BINDING_DIAGNOSTICS"+
                " realNofollowSources=6 individualFailures="+single+
                " coordinatedFourCaptureFailures="+coordinated+
                " coherentMatchedAlternativeNames="+positive+
                " restoredHealthyMatch=true zeroJvmLeases="+zeroLeases);
            System.out.println(
                "G21155_V3_PORTABLE_BINDING_PASS"+
                " portableFourNamesBoundToPhysicalMap=true"+
                " malformedInventoriesVetoed=true"+
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
