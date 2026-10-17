package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.191: external-JVM cutpoint evidence for the UNUSED forced account
 * postimage publisher. Process kill is not machine power loss.
 * No automatic review intent retirement or real account login changes.
 */
public final class G21191CoreForcedPostimagePublicationCutpointTest {
    private static final byte[] OLD=
        "G21191_BASELINE_OLD\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NEW=
        "G21191_POSTIMAGE_NEW\n".getBytes(StandardCharsets.UTF_8);

    private G21191CoreForcedPostimagePublicationCutpointTest(){}

    private static void check(boolean value,String why){
        if(!value)throw new AssertionError("G21.191 "+why);
    }

    private static void await(Path ready,String mode)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15L);
        while(System.nanoTime()<until){
            if(Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS))
                return;
            Thread.sleep(10L);
        }
        check(Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS),
            "child cutpoint not reached mode="+mode);
    }

    private static void atCutpoint(
        LocalForcedAccountPostimagePrimitive.Stage stage,
        String mode,Path ready
    )throws IOException{
        boolean selected=
            ("before".equals(mode)&&stage==
                LocalForcedAccountPostimagePrimitive.Stage.TEMP_FILE_FORCED)||
            ("after".equals(mode)&&stage==
                LocalForcedAccountPostimagePrimitive.Stage
                    .ATOMIC_REPLACE_BEFORE_DIR_FORCE);
        if(!selected)return;
        Files.write(ready,("G21191_CUTPOINT_"+mode).getBytes(
            StandardCharsets.UTF_8));
        System.out.println(
            "G21191_CHILD_CUTPOINT mode="+mode+
            " stage="+stage);
        System.out.flush();
        try{
            Thread.sleep(120_000L);
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            throw new IOException(
                "G21.191 TEST_CUTPOINT_INTERRUPTED",interrupted);
        }
        throw new IOException(
            "G21.191 CHILD_NOT_FORCE_KILLED");
    }

    private static void writer(
        String mode,Path account,Path ready
    )throws Exception{
        LocalForcedAccountPostimagePrimitive.Receipt receipt=
            LocalForcedAccountPostimagePrimitive.publish(
                account,NEW,
                stage->atCutpoint(stage,mode,ready)
            );
        check("complete".equals(mode),
            "cutpoint writer returned without being killed");
        check(receipt.fileForced&&receipt.directoryForced&&
            receipt.exactReadback&&receipt.length==NEW.length&&
            account.toAbsolutePath().normalize().equals(receipt.file)&&
            receipt.sha256.length()==64,
            "full publisher missing forced verified receipt");
        System.out.println(
            "G21191_CHILD_FULL_RECEIPT"+
            " fileForce=true atomicReplace=true"+
            " directoryForce=true exactReadback=true"+
            " markerRetired=false");
    }

    private static void probe(String expected,Path account)
        throws Exception{
        byte[] wanted="old".equals(expected)?OLD:NEW;
        check(Arrays.equals(wanted,Files.readAllBytes(account)),
            "new JVM sees incorrect postimage expected="+expected);
        System.out.println(
            "G21191_NEW_JVM_PROBE_PASS"+
            " visible="+expected+
            " noAutomaticRecovery=true");
    }

    private static Process start(
        String mode,Path account,Path ready,Path output
    )throws Exception{
        String executable=System.getProperty("java.home")+
            java.io.File.separator+"bin"+java.io.File.separator+
            (System.getProperty("os.name","").toLowerCase()
                .contains("win")?"java.exe":"java");
        ProcessBuilder builder=new ProcessBuilder(
            executable,"-cp",System.getProperty("java.class.path"),
            G21191CoreForcedPostimagePublicationCutpointTest
                .class.getName(),
            "--child",mode,account.toString(),ready.toString()
        );
        builder.redirectErrorStream(true);
        builder.redirectOutput(output.toFile());
        return builder.start();
    }

    private static void requireEnded(
        Process child,Path transcript,String marker
    )throws Exception{
        if(!child.waitFor(20L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5L,TimeUnit.SECONDS);
            throw new AssertionError(
                "G21.191 child process timeout");
        }
        String text=new String(Files.readAllBytes(transcript),
            StandardCharsets.UTF_8);
        check(child.exitValue()==0,
            "child nonzero exit="+child.exitValue()+" output="+text);
        check(text.contains(marker),
            "child receipt/probe missing "+marker+" output="+text);
    }

    private static long tempCount(Path root,Path account)
        throws IOException{
        String prefix=account.getFileName().toString()+
            ".g21191-forced-";
        try(Stream<Path> files=Files.list(root)){
            return files.filter(p->p.getFileName().toString()
                .startsWith(prefix)&&
                p.getFileName().toString().endsWith(".tmp"))
                .count();
        }
    }

    private static void caseStudy(
        Path root,String mode
    )throws Exception{
        Path account=root.resolve("account-"+mode+".properties");
        Path ready=root.resolve("ready-"+mode);
        Path writerLog=root.resolve("writer-"+mode+".log");
        Path probeLog=root.resolve("probe-"+mode+".log");

        LocalForcedAccountPostimagePrimitive.Receipt initial=
            LocalForcedAccountPostimagePrimitive.publish(account,OLD);
        check(initial.fileForced&&initial.directoryForced&&
            initial.exactReadback,
            "initial baseline postimage lacked receipt");
        check(Arrays.equals(OLD,Files.readAllBytes(account)),
            "baseline account unexpected");

        Process child=start(mode,account,ready,writerLog);
        try{
            if(!"complete".equals(mode)){
                await(ready,mode);
                check(child.isAlive(),
                    "child terminated before intended kill");
                byte[] expected="before".equals(mode)?OLD:NEW;
                check(Arrays.equals(expected,
                    Files.readAllBytes(account)),
                    "cutpoint visible account bytes unexpected");
                child.destroyForcibly();
                check(child.waitFor(10L,TimeUnit.SECONDS),
                    "forced cutpoint JVM still active");
                check(child.exitValue()!=0,
                    "forced cutpoint exit looked graceful");
                check(Arrays.equals(expected,
                    Files.readAllBytes(account)),
                    "post-kill account bytes unexpectedly changed");
                if("before".equals(mode))
                    check(tempCount(root,account)==1,
                        "forced temp before rename not retained");
                else check(tempCount(root,account)==0,
                    "renamed temp unexpectedly left on disk");
            }else{
                requireEnded(child,writerLog,
                    "G21191_CHILD_FULL_RECEIPT");
                check(Arrays.equals(NEW,Files.readAllBytes(account)),
                    "completed publication missing new bytes");
            }

            Process probe=start(
                "probe-"+("before".equals(mode)?"old":"new"),
                account,ready,probeLog);
            requireEnded(probe,probeLog,
                "G21191_NEW_JVM_PROBE_PASS");
            check(!Files.exists(
                account.resolveSibling(
                    account.getFileName().toString()+
                    ".g21188-active-review"),
                LinkOption.NOFOLLOW_LINKS),
                "unused postimage publisher mutated review intent");
            System.out.println(
                "G21191_STAGE_PASS mode="+mode+
                " exactNewJvmPostimage=true"+
                " crashedBeforeReceipt="+
                    (!"complete".equals(mode)));
        }finally{
            if(child.isAlive()){
                child.destroyForcibly();
                child.waitFor(5L,TimeUnit.SECONDS);
            }
        }
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21191-publish-");
        try{
            caseStudy(root,"before");
            caseStudy(root,"after");
            caseStudy(root,"complete");
            check(MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0,
                "unrelated publication/watch resources leaked");
            System.out.println(
                "G21191_FORCED_PUBLICATION_DIAGNOSTICS"+
                " externalWriterProcesses=3"+
                " externalReadbackProcesses=3"+
                " forcedTempBeforeRenameKillRetained=true"+
                " atomicRenameBeforeDirForceKillVisible=true"+
                " completedFileAndDirectoryForced=true"+
                " exactCommittedPostimageReadback=true");
            System.out.println(
                "G21191_FORCED_PUBLICATION_PASS"+
                " standalonePrimitiveOnly=true"+
                " atomicRenameFallback=false"+
                " preRenameCutpointTested=true"+
                " postRenamePreDirectoryForceCutpointTested=true"+
                " fullyForcedReadbackTested=true"+
                " machinePowerLossTested=false"+
                " actualWorldSaveWired=false"+
                " accountEpochReceipt=false"+
                " reviewMarkerRetirement=false"+
                " uncooperativeRawWriterExcluded=false"+
                " manualRecoveryOnly=true"+
                " grants=false replay=false cleanup=false");
        }finally{
            // Only this disposable test root is cleaned up after
            // all child processes have exited. No real account or
            // ACTIVE review sidecar is touched.
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0])){
            Path account=Paths.get(args[2]);
            Path ready=Paths.get(args[3]);
            if(args[1].startsWith("probe-"))
                probe(args[1].substring("probe-".length()),account);
            else if("before".equals(args[1])||
                    "after".equals(args[1])||
                    "complete".equals(args[1]))
                writer(args[1],account,ready);
            else throw new IllegalArgumentException(
                "unexpected child mode");
        }else{
            check(args.length==0,"unexpected args");
            run();
        }
    }
}
