package spk.local;

import java.io.InputStream;
import java.nio.file.*;
import java.util.Properties;

/**
 * R51 FAULT INJECTOR. Intended exclusively for a disposable child JVM
 * created by the Python real-server acceptance test, never a live server.
 *
 * The caller MUST opt in, pass a file inside its own spk-r51 temp directory,
 * and use the canonical only-test-account/opensrc.properties path. Only after
 * the genuine AtomicAccountPublisher has force(true)'d the adjacent stage
 * does this child intentionally call Runtime.halt(71), before ATOMIC_MOVE.
 *
 * No original GUI/client, real account, owner JVM or host system process is
 * involved. A power loss or a fully crash-atomic game transaction is NOT tested.
 */
public final class R51InterruptedAccountPublisherChild {
    private static final int INJECTED_EXIT=71;

    public static void main(String[] args)throws Exception{
        if(args.length!=1||
           !Boolean.getBoolean("spk.local.r51InterruptPublisherTestOnly"))
            throw new IllegalArgumentException(
                "R51 child requires explicit private test opt-in and file");

        Path file=Paths.get(args[0]).toAbsolutePath().normalize();
        Path real=file.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path parent=real.getParent();
        if(parent==null||
           Files.isSymbolicLink(real)||
           !Files.isRegularFile(real,LinkOption.NOFOLLOW_LINKS)||
           !parent.getFileName().toString().equals("only-test-account")||
           parent.getParent()==null||
           !parent.getParent().getFileName().toString()
                .startsWith("spk-r51-ambiguous-stage-")||
           !parent.toRealPath().startsWith(
                Paths.get(System.getProperty("java.io.tmpdir")).toRealPath()))
            throw new IllegalArgumentException(
                "R51 child refused non-private-test account path");

        Properties newer=new Properties();
        try(InputStream input=Files.newInputStream(
                real,LinkOption.NOFOLLOW_LINKS)){
            newer.load(input);
        }
        if(!"opensrc".equals(newer.getProperty("username"))||
           !"opensrc.properties".equals(real.getFileName().toString()))
            throw new IllegalArgumentException(
                "R51 child refused unexpected canonical test profile");

        // Deliberately plausible but *uncommitted* alternate account state.
        // Its presence may never authorize promotion/replay or cleanup.
        newer.setProperty("bank.0","995,100099,0");
        newer.setProperty("r51.uncommitted.test.marker","UNPUBLISHED");

        AtomicAccountPublisher.publish(
            real,
            newer,
            "R51 private test: intentionally uncommitted stage",
            (staged,target)->{
                if(!Files.isRegularFile(staged,LinkOption.NOFOLLOW_LINKS)||
                   !staged.getParent().equals(parent)||
                   !staged.getFileName().toString().startsWith(
                       ".opensrc.properties.r16-"))
                    throw new java.io.IOException(
                        "R51 fault injector staged-file invariant failed");
                // This replacement callback runs only AFTER channel.force(true)
                // has completed, by AtomicAccountPublisher contract.
                System.out.println(
                    "R51_TEST_ONLY_FORCED_STAGE_BEFORE_ATOMIC_REPLACE"+
                    " exit="+INJECTED_EXIT);
                System.out.flush();
                Runtime.getRuntime().halt(INJECTED_EXIT);
                throw new java.io.IOException(
                    "R51 unexpected return from Runtime.halt");
            });
        throw new AssertionError("R51 interrupted publisher unexpectedly completed");
    }

    private R51InterruptedAccountPublisherChild(){}
}
