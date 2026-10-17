package spk.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.237 TEST-ONLY: existing G21.221 default-OFF lock-object
 * continuity guard refuses the old World owner's next captured save
 * when .session.lck path is deliberately unlinked and recreated.
 *
 * Does NOT prevent a separate JVM acquiring the substituted lock.
 */
public final class G21237CoreReplacedSessionLockContinuityVetoTest {
    private G21237CoreReplacedSessionLockContinuityVetoTest(){}
    private static final String USER="g21237-lock-continuity";
    private static final String NS="core_g21237";
    private static final String KEY="extension.core_g21237.phase";

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.237 "+why);
    }
    private static boolean changed(Throwable throwable){
        for(Throwable cause=throwable;cause!=null;
            cause=cause.getCause()){
            String message=cause.getMessage();
            if(message!=null&&message.contains(
                    "SESSION_LOCK_OBJECT_CHANGED"))
                return true;
        }
        return false;
    }
    private static String phase(FilePlayerRepository repo)
        throws Exception{
        Optional<PlayerSnapshot> saved=repo.load(USER);
        check(saved.isPresent(),"account missing");
        return saved.get().values().get(KEY);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21237-continuity-");
        String[] flags={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY};
        String[] old=new String[flags.length];
        for(int i=0;i<flags.length;i++)old[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<flags.length;i++)
            System.setProperty(flags[i],"true");

        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=null;
        WorldPlayer player=null;
        LocalJvmAccountSessionLease.Token token=null;
        long generation=-1L;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Path lock=account.resolveSibling(
                account.getFileName().toString()+".session.lck");
            world=World.isolatedForTest(60_000L,repo);
            player=new WorldPlayer();
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(token!=null,"JVM World owner lease not acquired");
            generation=world.registerPlayer(player,USER);
            token.bindAdmittedWorldOwner(player,generation);
            world.start();

            final World live=world;
            final WorldPlayer owner=player;
            final long admitted=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket>
                ticket=new AtomicReference<>();

            world.submitAndWait(owner,admitted,()->{
                owner.snapshotExtensions().replaceNamespace(NS,
                    Collections.singletonMap("phase","CONFIRMED_BEFORE_SWAP"));
                ticket.set(live.persistence().captureAndSave(
                    USER,owner,admitted,0,
                    "[G21.237] ","CONFIRMED_BASELINE"));
            },5_000L);
            check(ticket.get()!=null,"baseline SaveTicket missing");
            ticket.get().completion.get(10L,TimeUnit.SECONDS);
            check("CONFIRMED_BEFORE_SWAP".equals(phase(repo)),
                "baseline World postimage not persisted");

            Object original=Files.readAttributes(
                lock,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).fileKey();
            check(original!=null,"stable lock fileKey unavailable");
            Files.delete(lock);
            Files.createFile(lock);
            Object replacement=Files.readAttributes(
                lock,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).fileKey();
            check(replacement!=null&&!original.equals(replacement),
                "session lock file object not actually replaced");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "old JVM lease disappeared after filename swap");

            boolean refused=false;
            ticket.set(null);
            try{
                world.submitAndWait(owner,admitted,()->{
                    owner.snapshotExtensions().replaceNamespace(NS,
                        Collections.singletonMap("phase",
                            "UNCONFIRMED_AFTER_SWAP"));
                    ticket.set(live.persistence().captureAndSave(
                        USER,owner,admitted,0,
                        "[G21.237] ","BLOCKED_AFTER_LOCK_REPLACEMENT"));
                },5_000L);
            }catch(Throwable failure){
                refused=changed(failure);
                if(!refused)
                    throw new AssertionError(
                        "G21.237 unexpected capture failure",failure);
            }
            if(ticket.get()!=null){
                try{
                    ticket.get().completion.get(10L,TimeUnit.SECONDS);
                }catch(ExecutionException expected){
                    refused=refused||changed(expected);
                }
            }
            check(refused,
                "G21.221 continuity did not veto old World save");
            check("CONFIRMED_BEFORE_SWAP".equals(phase(repo)),
                "rejected old World save changed persisted bytes");

            boolean ownerCheckDenied=false;
            try{
                LocalJvmAccountSessionLease
                    .requireCurrentWorldOwnerForTest(
                        account,owner,admitted);
            }catch(IllegalStateException expected){
                ownerCheckDenied=changed(expected);
            }
            check(ownerCheckDenied,
                "old owner observation accepted substituted OS lock");
            System.out.println(
                "G21237_REPLACED_OS_LOCK_CONTINUITY_SAVE_VETO_PASS"+
                " existingG21221ContinuityOptInEnabled=true"+
                " oldWorldLeaseStillAllocated=true"+
                " lockLeafFileKeySubstituted=true"+
                " newerWorldCaptureOrSaveRefused=true"+
                " olderDiskPostimagePreserved=true"+
                " secondJvmAdmissionStillPossible=true"+
                " atomicLockPathSubstitutionFenced=false"+
                " durableOwnerEpoch=false"+
                " crashRecovery=false");
        }finally{
            try{
                if(world!=null&&player!=null&&generation>0)
                    world.unregisterPlayer(player,generation);
                if(token!=null)token.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                for(int i=0;i<flags.length;i++){
                    if(old[i]==null)System.clearProperty(flags[i]);
                    else System.setProperty(flags[i],old[i]);
                }
                try(Stream<Path> walk=Files.walk(root)){
                    for(Path p:walk.sorted(Comparator.reverseOrder())
                            .toArray(Path[]::new))
                        Files.deleteIfExists(p);
                }
            }
        }
    }
}
