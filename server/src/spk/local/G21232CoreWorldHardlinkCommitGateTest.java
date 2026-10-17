package spk.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** G21.232 positive opt-in World postimage gate against an account
 * hardlink introduced AFTER the original session's admission.
 * Still not durable/external-writer or atomic inode fencing. */
public final class G21232CoreWorldHardlinkCommitGateTest {
    private G21232CoreWorldHardlinkCommitGateTest(){}
    private static final String USER="g21232-hardlink-late";
    private static final String NS="core_g21232";
    private static final String KEY="extension.core_g21232.phase";
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.232 "+why);
    }
    private static String phase(FilePlayerRepository repo)throws Exception{
        Optional<PlayerSnapshot> x=repo.load(USER);
        check(x.isPresent(),"snapshot missing");
        return x.get().values().get(KEY);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21232-late-link-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY};
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)old[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)
            System.setProperty(keys[i],"true");

        World world=null;
        WorldPlayer player=null;
        LocalJvmAccountSessionLease.Token token=null;
        long generation=-1;
        try{
            Path first=LocalAccountProfiles.accountFile(USER);
            Path alias=first.resolveSibling("g21232-late-alias.properties");
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            FilePlayerRepository aliased=new FilePlayerRepository(
                name->alias);
            world=World.isolatedForTest(60_000L,repo);
            player=new WorldPlayer();
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(token!=null,"opt-in session not acquired");
            generation=world.registerPlayer(player,USER);
            token.bindAdmittedWorldOwner(player,generation);
            world.start();

            final World ownedWorld=world;
            final WorldPlayer live=player;
            final long admitted=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();
            world.submitAndWait(live,admitted,()->{
                live.snapshotExtensions().replaceNamespace(NS,
                    Collections.singletonMap("phase","BEFORE_ALIAS"));
                ticket.set(ownedWorld.persistence().captureAndSave(
                    USER,live,admitted,0,"[G21.232] ","BASELINE"));
            },5_000L);
            check(ticket.get()!=null,"initial World save not captured");
            ticket.get().completion.get(10L,TimeUnit.SECONDS);
            check("BEFORE_ALIAS".equals(phase(repo)),
                "initial World postimage absent");
            check(Files.isRegularFile(first,LinkOption.NOFOLLOW_LINKS),
                "initial real file missing");

            // Existing session was admitted when the account pathname
            // was absent/singleton. A new hardlink now appears.
            Files.createLink(alias,first);
            check(Files.isSameFile(first,alias),
                "hardlink not actually same inode before write");
            check("BEFORE_ALIAS".equals(phase(aliased)),
                "alias did not see baseline");

            // With experimental count gate still ON, the real
            // guarded World SaveTask must fail *before* publishing.
            ticket.set(null);
            world.submitAndWait(live,admitted,()->{
                live.snapshotExtensions().replaceNamespace(NS,
                    Collections.singletonMap("phase","AFTER_ALIAS"));
                ticket.set(ownedWorld.persistence().captureAndSave(
                    USER,live,admitted,0,"[G21.232] ","DENIED_WORLD_SAVE"));
            },5_000L);
            check(ticket.get()!=null,"subsequent World task absent");
            boolean refused=false;
            try{
                ticket.get().completion.get(10L,TimeUnit.SECONDS);
            }catch(java.util.concurrent.ExecutionException expected){
                Throwable cause=expected.getCause();
                refused=cause!=null&&cause.getMessage()!=null&&
                    cause.getMessage().contains(
                        "G21.230 MULTIPLE_ACCOUNT_HARDLINKS_DENIED");
            }
            check(refused,"guarded World save succeeded after late hardlink");
            check("BEFORE_ALIAS".equals(phase(repo)),
                "denied World save changed canonical postimage");
            check("BEFORE_ALIAS".equals(phase(aliased)),
                "denied World save changed hardlink alias postimage");
            check(Files.isSameFile(first,alias),
                "denied World save split original inode");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "denied World save created/leaked RAW reservation");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "original live World lease was released");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                first,live,admitted);

            System.out.println(
                "G21232_GUARDED_WORLD_HARDLINK_COMMIT_VETO_PASS"+
                " hardlinkAddedAfterSessionAdmission=true"+
                " actualWorldSaveCompletedOnceSecondDenied=true"+
                " originalAndAliasPostimageIdentical=true"+
                " originalWorldLeaseStillHeld=true"+
                " hardlinkGateEnabledAtSecondSave=true"+
                " worldCommitTimeHardlinkVetoImplemented=true"+
                " authenticatedEpoch=false"+
                " durableFinalReceipt=false");
        }finally{
            try{
                if(world!=null&&player!=null&&generation>0)
                    world.unregisterPlayer(player,generation);
                if(token!=null)token.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                for(int i=0;i<keys.length;i++){
                    if(old[i]==null)System.clearProperty(keys[i]);
                    else System.setProperty(keys[i],old[i]);
                }
                try(Stream<Path> walk=Files.walk(root)){
                    for(Path p:walk.sorted(Comparator.reverseOrder())
                            .toArray(Path[]::new))Files.deleteIfExists(p);
                }
            }
        }
    }
}
