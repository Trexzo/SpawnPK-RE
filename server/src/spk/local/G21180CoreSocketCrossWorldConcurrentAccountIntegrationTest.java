package spk.local;

import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Chat 1 G21.180: two independently running Worlds can both admit the
 * same named account. Sequential, individually successful final saves
 * then demonstrate last-writer-wins loss of the first World's state.
 *
 * This is a negative cross-World ownership/durability characterization,
 * NOT cross-process exclusion, a recovery fix, or original-client proof.
 */
public final class G21180CoreSocketCrossWorldConcurrentAccountIntegrationTest {
    private static final String USER="g21180-shared";
    private static final String NS="core_cross_world";
    private static final String KEY="phase";
    private static final String DISK_KEY="extension."+NS+"."+KEY;

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.180 "+reason);
    }

    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }

    private static void await(Condition condition,String reason)
        throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8L);
        while(System.nanoTime()<until){
            if(condition.ready())return;
            Thread.sleep(10L);
        }
        check(condition.ready(),"timed out "+reason);
    }

    private static long completed(World world){
        String metrics=world.persistence().metrics();
        String key=",completed=";
        int at=metrics.indexOf(key);
        check(at>=0,"missing completed worker metric");
        int start=at+key.length(),end=metrics.indexOf(',',start);
        check(end>start,"malformed worker metric");
        return Long.parseLong(metrics.substring(start,end));
    }

    private static String phase(WorldPlayer owner){
        return owner.snapshotExtensions().namespace(NS).get(KEY);
    }

    private static void change(World world,WorldPlayer owner,String value)
        throws Exception{
        world.submit(owner,owner.generation(),()->{
            owner.snapshotExtensions().replaceNamespace(
                NS,Collections.singletonMap(KEY,value));
        }).get(4L,TimeUnit.SECONDS);
        check(value.equals(phase(owner)),
            "exact World-owned mutation missing: "+value);
    }

    private static String diskPhase(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> stored=repository.load(USER);
        check(stored.isPresent(),"account file unexpectedly missing");
        return stored.get().values().get(DISK_KEY);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21180-cross-world-");
        String old=System.getProperty("spk.local.accountFile");
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        World worldA=null,worldB=null;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Path secondary=LocalAccountProfiles.accountFile("src");
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "account must start absent");
            FilePlayerRepository repository=
                new FilePlayerRepository(LocalAccountProfiles::accountFile);
            worldA=World.isolatedForTest(50L,repository);
            worldB=World.isolatedForTest(50L,repository);
            final World a=worldA,b=worldB;

            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listenerA=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(a);
                G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listenerB=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(b)){
                try(Socket clientA=listenerA.login(USER)){
                    await(()->a.players().byName(USER)!=null,
                        "World A socket login");
                    WorldPlayer ownerA=a.players().byName(USER);
                    await(()->completed(a)>=1L,
                        "World A initial BOOTSTRAP persistence");
                    check(Files.isRegularFile(
                            account,LinkOption.NOFOLLOW_LINKS),
                        "World A bootstrap account was not published");

                    try(Socket clientB=listenerB.login(USER)){
                        await(()->b.players().byName(USER)!=null,
                            "simultaneous World B socket login");
                        WorldPlayer ownerB=b.players().byName(USER);
                        check(ownerA!=ownerB,
                            "separate Worlds unexpectedly share player owner");
                        check(a.players().size()==1&&b.players().size()==1,
                            "each World should contain one independent owner");
                        await(()->completed(b)>=1L,
                            "World B BOOTSTRAP persistence");
                        check(phase(ownerA)==null&&phase(ownerB)==null,
                            "fresh concurrent owners already have a mutation");

                        change(a,ownerA,"WORLD_A_FINAL");
                        change(b,ownerB,"WORLD_B_FINAL");
                        check("WORLD_A_FINAL".equals(phase(ownerA))&&
                              "WORLD_B_FINAL".equals(phase(ownerB)),
                            "one World mutation unexpectedly affected the other");

                        // Both players are still online. The first
                        // disconnected World is written successfully.
                        clientA.close();
                        await(()->a.players().size()==0,
                            "World A session unregister");
                        listenerA.waitCompleted();
                        await(()->"WORLD_A_FINAL".equals(
                                diskPhase(repository)),
                            "World A final save to shared account");
                        check(b.players().byName(USER)==ownerB&&
                              "WORLD_B_FINAL".equals(phase(ownerB)),
                            "World B ownership changed while A saved");
                        check(b.persistence()
                                .failedFinalSaveAdmissionVetoCount()==0,
                            "World B negative veto was unexpectedly armed");
                    }

                    // Exiting B's socket now publishes its older,
                    // independently captured state over A's postimage.
                    await(()->b.players().size()==0,
                        "World B session unregister");
                    listenerB.waitCompleted();
                }
            }

            check("WORLD_B_FINAL".equals(diskPhase(repository)),
                "later World B did not overwrite A's snapshot");
            check(!"WORLD_A_FINAL".equals(diskPhase(repository)),
                "World A postimage unexpectedly survived later B save");
            check(!Files.exists(secondary,LinkOption.NOFOLLOW_LINKS),
                "ordinary named account unexpectedly wrote src profile");
            check(a.persistence().failedCount()==0&&
                  b.persistence().failedCount()==0,
                "two successful final saves were marked failed");
            a.close();
            b.close();
            check(a.closed()&&b.closed(),
                "both Worlds must be fully closed");
            boolean noLeaks=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noLeaks,"publication lease or local watch leaked");
            System.out.println(
                "G21180_CROSS_WORLD_DIAGNOSTICS"+
                " independentWorlds=2 successfulConcurrentTcpLogins=2"+
                " concurrentNamedOwners=2 worldAFinalCommitted=true"+
                " worldBFinalCommitted=true worldAPostimageLost=true"+
                " sameWorldDuplicateGuardNotApplicable=true"+
                " zeroJvmLeases="+noLeaks);
            System.out.println(
                "G21180_CROSS_WORLD_PASS"+
                " crossWorldConcurrentAccountCurrentlyAllowed=true"+
                " sequentialLastWriterWins=true"+
                " crossWorldSessionOwnershipFence=false"+
                " worldLifetimeFailureNegativeIsGlobal=false"+
                " durableCrossWorldIntent=false"+
                " automaticRecovery=false"+
                " originalGraphicalClientProven=false"+
                " rawWriterExclusion=false atomicCrashEpoch=false"+
                " independentProvenance=false"+
                " cleanup=false rollover=false pruning=false unlink=false"+
                " grant=false replay=false sessionRelease=false ack=false");
        }finally{
            if(worldB!=null&&!worldB.closed())worldB.close();
            if(worldA!=null&&!worldA.closed())worldA.close();
            if(old==null)System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",old);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
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
