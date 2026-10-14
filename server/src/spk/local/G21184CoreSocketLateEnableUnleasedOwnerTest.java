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
 * Chat 1 G21.184: a session that was admitted while the experimental
 * same-JVM account lease was OFF is not retroactively registered when
 * an operator enables it. A second World can therefore independently
 * hydrate the same profile and later overwrite the first final save.
 *
 * NEGATIVE characterization only; deliberately no production fix or
 * general runtime feature-toggle safety claim.
 */
public final class G21184CoreSocketLateEnableUnleasedOwnerTest {
    private static final String USER="g21184-late";
    private static final String NS="core_late_enable";
    private static final String KEY="phase";
    private static final String DISK_KEY="extension."+NS+"."+KEY;

    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError("G21.184 "+message);
    }

    @FunctionalInterface private interface Condition{
        boolean ready()throws Exception;
    }

    private static void await(Condition condition,String label)
        throws Exception{
        final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8L);
        while(System.nanoTime()<deadline){
            if(condition.ready())return;
            Thread.sleep(10L);
        }
        check(condition.ready(),"timeout "+label);
    }

    private static long completed(World world){
        String m=world.persistence().metrics();
        String key=",completed=";
        int at=m.indexOf(key);
        check(at>=0,"missing completion counter");
        int first=at+key.length(),last=m.indexOf(',',first);
        check(last>first,"completion counter malformed");
        return Long.parseLong(m.substring(first,last));
    }

    private static String phase(WorldPlayer player){
        return player.snapshotExtensions().namespace(NS).get(KEY);
    }

    private static void change(World world,WorldPlayer owner,String value)
        throws Exception{
        world.submit(owner,owner.generation(),()->{
            owner.snapshotExtensions().replaceNamespace(
                NS,Collections.singletonMap(KEY,value));
        }).get(4L,TimeUnit.SECONDS);
        check(value.equals(phase(owner)),
            "World-owned mutation did not apply "+value);
    }

    private static String diskPhase(FilePlayerRepository repo)throws Exception{
        Optional<PlayerSnapshot> snapshot=repo.load(USER);
        check(snapshot.isPresent(),"account file was not published");
        return snapshot.get().values().get(DISK_KEY);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21184-late-enable-");
        String previousFile=System.getProperty("spk.local.accountFile");
        String previousFlag=System.getProperty(
            LocalJvmAccountSessionLease.PROPERTY);
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"false");

        World first=null,second=null;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Path secondary=LocalAccountProfiles.accountFile("src");
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "fixture named account unexpectedly exists");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "unexpected earlier token");
            FilePlayerRepository repository=
                new FilePlayerRepository(LocalAccountProfiles::accountFile);
            first=World.isolatedForTest(50L,repository);
            second=World.isolatedForTest(50L,repository);
            final World a=first,b=second;

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
                        "World A default-off socket login");
                    WorldPlayer ownerA=a.players().byName(USER);
                    await(()->completed(a)>=1L,
                        "World A initial BOOTSTRAP account");
                    check(Files.isRegularFile(
                        account,LinkOption.NOFOLLOW_LINKS),
                        "bootstrap file missing");
                    change(a,ownerA,"A_FINAL");
                    check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                        "default-off A unexpectedly leased account");

                    // Property activation AFTER A was already admitted
                    // cannot retroactively give it a session token.
                    System.setProperty(
                        LocalJvmAccountSessionLease.PROPERTY,"true");
                    try(Socket clientB=listenerB.login(USER)){
                        await(()->b.players().byName(USER)!=null,
                            "new opt-in World B socket login");
                        WorldPlayer ownerB=b.players().byName(USER);
                        check(ownerA!=ownerB&&
                              a.players().size()==1&&b.players().size()==1,
                            "independent duplicate World owners not present");
                        check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                            "World B did not acquire its own new token");
                        await(()->completed(b)>=1L,
                            "World B BOOTSTRAP snapshot");
                        change(b,ownerB,"B_FINAL");
                        check("A_FINAL".equals(phase(ownerA))&&
                              "B_FINAL".equals(phase(ownerB)),
                            "World owned state unexpectedly shared");

                        clientA.close();
                        await(()->a.players().size()==0,
                            "World A final unregister");
                        listenerA.waitCompleted();
                        await(()->"A_FINAL".equals(diskPhase(repository)),
                            "World A successful final file save");
                        check(b.players().byName(USER)==ownerB,
                            "later World B owner lost");
                        check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                            "B token released before its final save");
                    }

                    await(()->b.players().size()==0,
                        "World B final unregister");
                    listenerB.waitCompleted();
                }
            }

            check("B_FINAL".equals(diskPhase(repository)),
                "later independently captured B did not overwrite A");
            check(!"A_FINAL".equals(diskPhase(repository)),
                "A postimage unexpectedly survived later B save");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "B token not released on confirmed final save");
            check(first.persistence().failedCount()==0&&
                  second.persistence().failedCount()==0,
                "both successful account saves should be healthy");
            check(!Files.exists(secondary,LinkOption.NOFOLLOW_LINKS),
                "normal named profile created secondary src alias");
            first.close();
            second.close();
            check(first.closed()&&second.closed(),
                "both Worlds must close");
            boolean noLeaks=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noLeaks,"JVM publication lease/watch leaked");
            System.out.println(
                "G21184_LATE_ENABLE_DIAGNOSTICS"+
                " simultaneouslyLiveWorlds=2"+
                " firstAccountUnleasedAtLogin=true"+
                " enabledAfterFirstLogin=true"+
                " secondWorldAdmitted=true"+
                " independentNamedOwners=2"+
                " bothFinalSavesSuccessful=true"+
                " firstWorldPostimageOverwritten=true"+
                " secondWorldTokenReleased=true"+
                " zeroJvmPublicationLeaks="+noLeaks);
            System.out.println(
                "G21184_LATE_ENABLE_PASS"+
                " retroactiveExistingOwnerLease=false"+
                " dynamicOptInFullySafe=false"+
                " successfulStaleWriteHazardReproduced=true"+
                " safetyFixImplemented=false"+
                " defaultOffPolicyUnchanged=true"+
                " crossProcessFence=false crashDurable=false"+
                " rawWriterExclusion=false atomicCrashEpoch=false"+
                " independentProvenance=false"+
                " originalGraphicalClientProven=false"+
                " cleanup=false rollover=false pruning=false unlink=false"+
                " grant=false replay=false sessionRelease=false ack=false");
        }finally{
            if(second!=null&&!second.closed())second.close();
            if(first!=null&&!first.closed())first.close();
            if(previousFile==null)System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",previousFile);
            if(previousFlag==null)
                System.clearProperty(LocalJvmAccountSessionLease.PROPERTY);
            else
                System.setProperty(LocalJvmAccountSessionLease.PROPERTY,
                    previousFlag);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}
