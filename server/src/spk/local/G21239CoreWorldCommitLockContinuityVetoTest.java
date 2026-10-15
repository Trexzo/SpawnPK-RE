package spk.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.239 positive optional last-chance original OS lock object
 * continuity recheck inside actual World file publication callback.
 * No global atomic external-writer lock-path identity guarantee.
 */
public final class G21239CoreWorldCommitLockContinuityVetoTest {
    private G21239CoreWorldCommitLockContinuityVetoTest(){}
    private static final String USER="g21239-lock-late";
    private static final String NS="core_g21239";
    private static final String KEY="extension.core_g21239.phase";
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.239 "+why);
    }
    private static String state(FilePlayerRepository repo)throws Exception{
        Optional<PlayerSnapshot> found=repo.load(USER);
        check(found.isPresent(),"saved snapshot absent");
        return found.get().values().get(KEY);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21239-lock-late-");
        String[] flags={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY};
        String[] prior=new String[flags.length];
        for(int i=0;i<flags.length;i++)prior[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<flags.length;i++)System.setProperty(flags[i],"true");

        World world=null;
        WorldPlayer player=null;
        LocalJvmAccountSessionLease.Token token=null;
        long generation=-1L;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Path lock=account.resolveSibling(
                account.getFileName().toString()+".session.lck");
            AtomicBoolean armed=new AtomicBoolean(false);
            AtomicBoolean changed=new AtomicBoolean(false);
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                ignored->{},
                ignored->{
                    if(armed.compareAndSet(true,false)){
                        Object before=Files.readAttributes(
                            lock,BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS).fileKey();
                        check(before!=null,"old lock object missing");
                        Files.delete(lock);
                        Files.createFile(lock);
                        Object after=Files.readAttributes(
                            lock,BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS).fileKey();
                        check(after!=null&&!before.equals(after),
                            "session lock object not actually substituted");
                        changed.set(true);
                    }
                });
            world=World.isolatedForTest(60_000L,repo);
            player=new WorldPlayer();
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(token!=null,"true admitted JVM owner missing");
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
                    Collections.singletonMap("phase","BEFORE_LOCK_SWAP"));
                ticket.set(live.persistence().captureAndSave(
                    USER,owner,admitted,0,
                    "[G21.239] ","INITIAL_PUBLISH"));
            },5_000L);
            check(ticket.get()!=null,"baseline World SaveTask missing");
            ticket.get().completion.get(10L,TimeUnit.SECONDS);
            check("BEFORE_LOCK_SWAP".equals(state(repo)),
                "World baseline postimage not persisted");

            // G21.239 test: OS lock fileKey substitution occurs
            // INSIDE the existing guarded World publication callback,
            // AFTER the previous captured-owner worker check. The new
            // last-chance owner lock recheck must now deny publication.
            armed.set(true);
            ticket.set(null);
            world.submitAndWait(owner,admitted,()->{
                owner.snapshotExtensions().replaceNamespace(NS,
                    Collections.singletonMap("phase","AFTER_LOCK_SWAP"));
                ticket.set(live.persistence().captureAndSave(
                    USER,owner,admitted,0,
                    "[G21.239] ","SECOND_PUBLISH_LOCK_SWAP_VETO"));
            },5_000L);
            check(ticket.get()!=null,"second captured World ticket absent");
            boolean denied=false;
            try{
                ticket.get().completion.get(10L,TimeUnit.SECONDS);
            }catch(java.util.concurrent.ExecutionException expected){
                Throwable why=expected.getCause();
                denied=why!=null&&why.getMessage()!=null&&
                    why.getMessage().contains(
                        "G21.239 WORLD_COMMIT_SESSION_LOCK_OBJECT_CHANGED");
            }
            check(changed.get(),
                "test did not substitute lock object inside publication");
            check(denied,
                "World file replaced despite G21.239 last-chance veto");
            check("BEFORE_LOCK_SWAP".equals(state(repo)),
                "denied World publication replaced original postimage");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "original owner token disappeared after file-key veto");

            boolean continuityRejected=false;
            try{
                LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                    account,owner,admitted);
            }catch(IllegalStateException expected){
                continuityRejected=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "SESSION_LOCK_OBJECT_CHANGED");
            }
            check(continuityRejected,
                "subsequent owner check did not recognize substitution");
            System.out.println(
                "G21239_WORLD_PUBLICATION_LOCK_CONTINUITY_RECHECK_PASS"+
                " actualWorldSaveTaskFailedAtPublication=true"+
                " originalWorkerOwnerWitnessChecked=true"+
                " lockLeafSubstitutedAfterWorkerBeforeMove=true"+
                " lastChanceContinuityObservedNewFileKey=true"+
                " accountOlderPostimagePreserved=true"+
                " originalWorldTokenStillAllocated=true"+
                " atomicExternalSwapProtection=false"+
                " durableOwnerEpoch=false"+
                " crashRecovery=false");
        }finally{
            try{
                if(world!=null&&player!=null&&generation>0L)
                    world.unregisterPlayer(player,generation);
                if(token!=null)token.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                for(int i=0;i<flags.length;i++){
                    if(prior[i]==null)System.clearProperty(flags[i]);
                    else System.setProperty(flags[i],prior[i]);
                }
                try(Stream<Path> walk=Files.walk(root)){
                    for(Path path:walk.sorted(
                            Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(path);
                }
            }
        }
    }
}
