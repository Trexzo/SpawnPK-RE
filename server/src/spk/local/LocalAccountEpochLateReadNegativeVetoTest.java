package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * R161: R153/R154 account-local evidence appears after a genuine
 * FilePlayerRepository session byte-read but before its second negative
 * veto. Neither a complete untrusted HEAD nor an orphan object may
 * permit a legacy account snapshot (or empty account) to hydrate.
 * No real TCP session, owner epoch, repair or final-save ACK.
 */
public final class LocalAccountEpochLateReadNegativeVetoTest {
    enum Evidence { HEAD_AND_OBJECT, ORPHAN_OBJECT_ONLY }
    private static int cases;
    private LocalAccountEpochLateReadNegativeVetoTest(){}

    private static void check(boolean value,String message){
        if(!value)throw new AssertionError("R161 "+message);
    }

    private static String leafAccount(Path account){
        String leaf=account.getFileName().toString();
        check(leaf.endsWith(".properties"),"account filename");
        return leaf.substring(0,leaf.length()-11);
    }

    private static byte[] snapshot(Path account){
        return ("R161 immutable negative during session read "+
            leafAccount(account)).getBytes(StandardCharsets.US_ASCII);
    }

    private static void inject(Path account,Evidence evidence)
        throws IOException{
        byte[] image=snapshot(account);
        if(evidence==Evidence.HEAD_AND_OBJECT){
            LocalAccountEpochHeadCodec.Record record=
                new LocalAccountEpochHeadCodec.Record(
                    leafAccount(account),1,1,161,
                    LocalAccountEpochHeadCodec.State.ACTIVE,
                    LocalAccountEpochHeadCodec.ZERO_SHA,
                    LocalAccountEpochHeadCodec.digestSnapshot(image));
            LocalAccountEpochNegativeHeadPublication.publish(
                account,record,image);
        }else{
            LocalAccountEpochSnapshotObjects.publish(account,image);
        }
    }

    private static boolean matches(Throwable error,String token){
        for(Throwable e=error;e!=null;e=e.getCause())
            if(String.valueOf(e.getMessage()).contains(token))return true;
        return false;
    }

    private static void runCase(Path root,boolean accountExists,
        Evidence evidence)throws Exception{
        String name="r161case"+cases;
        Path account=root.resolve(name+".properties");
        FilePlayerRepository seed=new FilePlayerRepository(
            requested->root.resolve(requested+".properties"));
        byte[] before=null;
        Object originalKey=null;
        if(accountExists){
            seed.save(PlayerSnapshotCodec.capture(
                name,new WorldPlayer()));
            before=Files.readAllBytes(account);
            originalKey=Files.readAttributes(account,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                .fileKey();
        }
        final AtomicInteger probes=new AtomicInteger();
        FilePlayerRepository observed=new FilePlayerRepository(
            requested->root.resolve(requested+".properties"),
            ignored->{},ignored->{},
            observedName->{
                check(name.equals(observedName),
                    "read seam changed selected account");
                probes.incrementAndGet();
                inject(account,evidence);
            });
        final String denial=evidence==Evidence.HEAD_AND_OBJECT?
            "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY":
            "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION";
        boolean denied=false;
        try{
            observed.loadForWorldSession(name);
        }catch(IOException failed){
            denied=matches(failed,denial);
        }
        check(probes.get()==1&&denied,
            "late evidence was not caught by session recheck");
        Path head=LocalAccountEpochNegativeHeadPublication.headPath(account);
        check(Files.exists(head,LinkOption.NOFOLLOW_LINKS)==
            (evidence==Evidence.HEAD_AND_OBJECT),
            "unexpected HEAD postimage");
        String digest=LocalAccountEpochHeadCodec.digestSnapshot(
            snapshot(account));
        LocalAccountEpochSnapshotObjects.verify(account,digest,null);
        check(accountExists?
            Arrays.equals(before,Files.readAllBytes(account))&&
                originalKey.equals(Files.readAttributes(account,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                    .fileKey()):
            !Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "failed session admission mutated legacy account");

        // A second genuine repository instance must also refuse the
        // retained negative evidence, without an injection callback.
        boolean deniedAgain=false;
        try{
            seed.loadForWorldSession(name);
        }catch(IOException refused){
            deniedAgain=matches(refused,denial);
        }
        check(deniedAgain,"fresh repository accepted retained evidence");
        cases++;
        System.out.println("R161_LATE_READ_NEGATIVE_ADMISSION_DENIED_PASS"+
            " case="+cases+" existed="+accountExists+
            " evidence="+evidence+
            " afterReadProbeExactlyOnce=true"+
            " originalAccountUnchanged=true"+
            " freshRepositoryAlsoDenied=true"+
            " noPositiveEpochOrFinalAck=true");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected CLI arguments");
        Path root=Files.createTempDirectory("r161-late-negative-read-");
        try{
            for(boolean accountExists:new boolean[]{false,true})
                for(Evidence evidence:Evidence.values())
                    runCase(root,accountExists,evidence);
            String unaffected="r161-healthy";
            FilePlayerRepository repo=new FilePlayerRepository(
                name->root.resolve(name+".properties"));
            repo.save(PlayerSnapshotCodec.capture(
                unaffected,new WorldPlayer()));
            check(repo.loadForWorldSession(unaffected).isPresent(),
                "foreign negative sidecar blocked independent healthy account");
            check(cases==4,"expected four late-evidence cases");
            System.out.println("R161_LATE_NEGATIVE_READ_MATRIX_PASS"+
                " cases=4 absentAndExistingAccount=true"+
                " HEADAndOrphanVeto=true foreignAccountIsolated=true"+
                " noOriginalClientTcpOrPowerLossProof=true");
        }finally{
            // Only disposable regression fixture files, not real
            // negative forensic evidence, are deleted.
            try(Stream<Path> walk=Files.walk(root)){
                for(Path item:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(item);
            }
        }
    }
}
