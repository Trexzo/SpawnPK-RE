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
 * G21.156: Base64-decoded v3 leaf bytes, length and SHA must agree
 * with the independent physical metadata retained in the same
 * observation. Four-capture synthetic mutations never grant access.
 */
public final class G21156MailboxV3PortableBytesShaSizeIntegrationTest {
    private static final String[] LEAVES={
        "g21156.properties.g21103-writer-attempt-v1-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21156.properties.g21114-writer-kind-v2-ffffffff-ffff-ffff-ffff-ffffffffffff.begin",
        "g21156.properties.g21120-commit-result-v3-ffffffff-ffff-ffff-ffff-ffffffffffff.result"
    };
    private static final String[] LABELS={
        "ACCOUNT","PREPARED_JOURNAL","COMMIT",
        "LEAF:"+LEAVES[0],"LEAF:"+LEAVES[1],"LEAF:"+LEAVES[2]
    };
    private static final long[] MAX={1048576L,1024L,1024L,512L,512L,512L};

    private static void check(boolean value,String message){
        if(!value)throw new AssertionError("G21.156 "+message);
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
        String text="G21156_TEST_ONLY_V1\n";
        return instantiate(
            MailboxWriterEvidencePortableArchiveV1.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterEvidencePortableArchiveV1.Verified.class},
            text,hash(text),null);
    }

    private static MailboxWriterKindEvidenceV2.Snapshot
        archiveV2()throws Exception{
        String text="G21156_TEST_ONLY_V2\n";
        return instantiate(
            MailboxWriterKindEvidenceV2.Snapshot.class,
            new Class<?>[]{String.class,String.class,
                MailboxWriterKindEvidenceV2.Report.class},
            text,hash(text),null);
    }


    /**
     * Synthetic shape-only v3 portable inventory. Physical fixture
     * source bytes are NOT authenticated by this string. Use actual
     * G21.125 KIND/LEAF/BYTES_BASE64 formatting so G21.156 can
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
        String text=fixtureV3Portable("g21156.properties",sourceBody);
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
    private static String encodedOf(String value){
        return java.util.Base64.getEncoder().encodeToString(
            value.getBytes(StandardCharsets.US_ASCII));
    }
    private static String replacedBase64(
        String portable,String name,String encoded
    ){
        String marker="LEAF="+name+"\nBYTES_BASE64=";
        int at=portable.indexOf(marker);
        check(at>=0,"real v3 portable fixture missing target "+name);
        int start=at+marker.length();
        int end=portable.indexOf('\n',start);
        check(end>start&&portable.indexOf(marker,at+1)<0,
            "missing or duplicate target portable bytes line");
        return portable.substring(0,start)+encoded+
            portable.substring(end);
    }
    private static String corruptedEncoding(
        String original,int mode
    ){
        final byte[] real=java.util.Base64.getDecoder()
            .decode(original);
        if(mode==0){
            byte[] changed=real.clone();
            changed[0]=(byte)(changed[0]^1);
            return java.util.Base64.getEncoder()
                .encodeToString(changed);
        }
        if(mode==1){
            byte[] larger=java.util.Arrays.copyOf(
                real,real.length+1);
            larger[larger.length-1]=(byte)'Q';
            return java.util.Base64.getEncoder()
                .encodeToString(larger);
        }
        if(mode==2)return "@@@@@@@@";
        if(mode==3)return java.util.Base64.getEncoder()
            .encodeToString(new byte[513]);
        if(mode==4){
            if(original.endsWith("="))
                return original.replaceFirst("=+$","");
            return original+"=";
        }
        if(mode==5)return java.util.Base64.getEncoder()
            .encodeToString(new byte[514]);
        throw new IllegalArgumentException("unknown malformed mode");
    }
    private static void negative(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[] all,
        Field portableField,Field shaField,int kind,int mode,
        int position
    )throws Exception{
        int first=position<0?0:position;
        int last=position<0?4:position+1;
        String[] oldPortables=new String[4],oldHashes=new String[4];
        int changed=0;
        boolean vetoed=false;
        String diagnostic=position>=0?"INTERNAL_BEFORE_AFTER_DRIFT_":
            mode==0?"V3_PORTABLE_PHYSICAL_SHA_MISMATCH_":
            mode==1?"V3_PORTABLE_PHYSICAL_SIZE_MISMATCH_":
                "BAD_V3_PORTABLE_BASE64_";
        try{
            for(int i=first;i<last;i++){
                oldPortables[i]=(String)portableField.get(all[i].v3);
                oldHashes[i]=(String)shaField.get(all[i].v3);
                String name=v3Names()[kind];
                String prefix="LEAF="+name+"\nBYTES_BASE64=";
                int at=oldPortables[i].indexOf(prefix);
                check(at>=0,"fixture portable source absent");
                int start=at+prefix.length();
                int end=oldPortables[i].indexOf('\n',start);
                check(end>start,"empty fixture Base64");
                String original=oldPortables[i].substring(start,end);
                String newEncoded=corruptedEncoding(original,mode);
                check(!newEncoded.equals(original),
                    "mutation did not alter encoded bytes");
                String mutated=replacedBase64(
                    oldPortables[i],name,newEncoded);
                portableField.set(all[i].v3,mutated);
                shaField.set(all[i].v3,hash(mutated));
                changed++;
            }
            try{
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    historical,current);
            }catch(IOException failure){
                String why=String.valueOf(failure.getMessage());
                vetoed=why.contains(
                    "HISTORICAL_IDENTITY_DELTA_UNSAFE_NO_AUTHORITY")&&
                    why.contains(diagnostic);
            }
        }finally{
            for(int i=first;i<first+changed;i++){
                portableField.set(all[i].v3,oldPortables[i]);
                shaField.set(all[i].v3,oldHashes[i]);
            }
        }
        check(vetoed,"missing checked v3 byte veto kind="+kind+
            " mode="+mode+" position="+position);
        matched(historical,current);
    }
    private static void coordinatedValidAlternate(
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report historical,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Report current,
        MailboxSevenPlaneContinuousPhysicalWitnessV1.Observation[] all,
        Field maps,Field portableField,Field shaField,
        int kind,BasicFileAttributes alternateAttrs,String alternateBody
    )throws Exception{
        Map<?,?>[] beforeMaps=new Map[4];
        String[] beforePortable=new String[4],beforeSha=new String[4];
        int finished=0;
        try{
            for(int i=0;i<4;i++){
                beforeMaps[i]=(Map<?,?>)maps.get(all[i]);
                beforePortable[i]=(String)portableField.get(all[i].v3);
                beforeSha[i]=(String)shaField.get(all[i].v3);
                String leaf=v3Names()[kind];
                Map<String,MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity> altered=
                    new TreeMap<>(all[i].evidenceLeafIdentities);
                check(altered.containsKey(leaf),
                    "valid alternative target leaf absent");
                altered.put(leaf,physical(
                    hash(alternateBody),alternateAttrs));
                maps.set(all[i],
                    java.util.Collections.unmodifiableMap(altered));
                String portable=replacedBase64(beforePortable[i],
                    leaf,encodedOf(alternateBody));
                portableField.set(all[i].v3,portable);
                shaField.set(all[i].v3,hash(portable));
                finished++;
            }
            MailboxSevenPlaneHistoricalIdentityDeltaV1.Report observed=
                MailboxSevenPlaneHistoricalIdentityDeltaV1.compare(
                    historical,current);
            check(observed.status==
                MailboxSevenPlaneHistoricalIdentityDeltaV1.Status
                    .MATCHED_OBSERVED_METADATA_NO_AUTHORITY&&
                observed.changedSources.isEmpty()&&
                !observed.historicalReportAuthenticated&&
                !observed.independentPinAuthenticationProved&&
                !observed.atomicCrossFileEpochProved&&
                !observed.cleanupAuthorized&&!observed.grantAuthorized&&
                !observed.sessionAuthorized&&!observed.releaseAuthorized&&
                !observed.clientAckAuthorized,
                "coherent unsigned replacement may match shape but "+
                    "cannot obtain authority");
        }finally{
            for(int i=0;i<finished;i++){
                maps.set(all[i],beforeMaps[i]);
                portableField.set(all[i].v3,beforePortable[i]);
                shaField.set(all[i].v3,beforeSha[i]);
            }
        }
        matched(historical,current);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21156-v3-physical-");
        try{
            BasicFileAttributes[] attrs=new BasicFileAttributes[6];
            String[] shas=new String[6];
            String[] sourceBodies=new String[6];
            for(int i=0;i<6;i++){
                Path target=root.resolve("physical-"+i);
                String source="G21156_NOFOLLOW_SOURCE_"+i+"\n";
                sourceBodies[i]=source;
                Files.write(target,
                    source.getBytes(StandardCharsets.US_ASCII));
                attrs[i]=Files.readAttributes(target,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                check(attrs[i].isRegularFile()&&attrs[i].fileKey()!=null,
                    "real NOFOLLOW physical fixture missing");
                shas[i]=hash(source);
            }
            String alternateBody="G21156_ALT_PHYSICAL_BYTES\n";
            Path alternate=root.resolve("alternate-v3");
            Files.write(alternate,
                alternateBody.getBytes(StandardCharsets.US_ASCII));
            BasicFileAttributes alternateAttrs=Files.readAttributes(
                alternate,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            check(alternateAttrs.isRegularFile()&&
                alternateAttrs.fileKey()!=null,
                "alternative NOFOLLOW fixture unavailable");
            MailboxWriterEvidencePortableArchiveV1.Snapshot v1=
                archiveV1();
            MailboxWriterKindEvidenceV2.Snapshot v2=
                archiveV2();
            MailboxSevenPlaneContinuousPhysicalWitnessV1
                .Observation[] all=
                new MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .Observation[4];
            for(int n=0;n<4;n++){
                MailboxFourBoundResultLeavesArchiveV1.Snapshot v3=
                    archiveV3(sourceBodies[5]);
                MailboxSevenPlaneContinuousPhysicalWitnessV1
                    .PhysicalIdentity[] ids=
                    new MailboxSevenPlaneContinuousPhysicalWitnessV1
                        .PhysicalIdentity[6];
                for(int i=0;i<6;i++)
                    ids[i]=physical(shas[i],attrs[i]);
                all[n]=observation(ids,v1,v2,v3,attrs,shas);
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
            int single=0,coordinated=0,positives=0;
            for(int kind=0;kind<4;kind++){
                for(int mode=0;mode<6;mode++){
                    for(int position=0;position<4;position++){
                        negative(historical,current,all,portable,sha,
                            kind,mode,position);
                        single++;
                    }
                    negative(historical,current,all,portable,sha,
                        kind,mode,-1);
                    coordinated++;
                }
                coordinatedValidAlternate(historical,current,all,
                    maps,portable,sha,kind,alternateAttrs,alternateBody);
                positives++;
            }
            boolean zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0;
            check(single==96&&coordinated==24&&positives==4&&
                zeroLeases,"missing 124 cases or outstanding leases");
            System.out.println("G21156_V3_BYTES_SHA_SIZE_DIAGNOSTICS"+
                " realNofollowSources=7 v3Kinds=4 mutationModes=6"+
                " singleCaptureRejections="+single+
                " coordinatedFourCaptureRejections="+coordinated+
                " coherentUnsignedPositiveControls="+positives+
                " restoredHealthyMatch=true zeroJvmLeases="+zeroLeases);
            System.out.println("G21156_V3_BYTES_SHA_SIZE_PASS"+
                " canonicalBase64Bound512=true"+
                " physicalSizeAndShaMatched=true"+
                " independentlyAuthenticated=false"+
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
