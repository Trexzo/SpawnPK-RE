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

/** G21.231 TEST ONLY: new hardlink AFTER original session admission
 * lets its later owned World save replace only one pathname postimage,
 * even when the experimental link-count admission gate remains ON. */
public final class G21231CorePostAdmissionHardlinkWorldPublicationGapTest {
    private G21231CorePostAdmissionHardlinkWorldPublicationGapTest(){}
    private static final String USER="g21231-hardlink-late";
    private static final String NS="core_g21231";
    private static final String KEY="extension.core_g21231.phase";
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.231 "+why);
    }
    private static String phase(FilePlayerRepository repo)throws Exception{
        Optional<PlayerSnapshot> x=repo.load(USER);
        check(x.isPresent(),"snapshot missing");
        return x.get().values().get(KEY);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21231-late-link-");
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
            Path alias=first.resolveSibling("g21231-late-alias.properties");
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
                    USER,live,admitted,0,"[G21.231] ","BASELINE"));
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

            // Historical parent #2712 proves the original all-ON
            // late-hardlink publication gap. G21.232's descendant
            // preserves this negative only with a deliberate
            // TEST-ONLY gate opt-out around the second World save.
            System.setProperty(
                LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
                "false");
            try{
                ticket.set(null);
                world.submitAndWait(live,admitted,()->{
                    live.snapshotExtensions().replaceNamespace(NS,
                        Collections.singletonMap("phase","AFTER_ALIAS"));
                    ticket.set(ownedWorld.persistence().captureAndSave(
                        USER,live,admitted,0,"[G21.231] ","SECOND_WORLD_SAVE"));
                },5_000L);
                check(ticket.get()!=null,"subsequent World task absent");
                ticket.get().completion.get(10L,TimeUnit.SECONDS);
            }finally{
                System.setProperty(
                    LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
                    "true");
            }
            check("AFTER_ALIAS".equals(phase(repo)),
                "original World pathname not replaced with newer state");
            check("BEFORE_ALIAS".equals(phase(aliased)),
                "hardlink alias did not retain old World postimage");
            check(!Files.isSameFile(first,alias),
                "second publication unexpectedly retained same inode");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "original live World lease was released");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                first,live,admitted);

            System.out.println(
                "G21231_LATE_HARDLINK_WORLD_PUBLICATION_SPLIT_PASS"+
                " hardlinkAddedAfterSessionAdmission=true"+
                " actualWorldSaveCompletedTwice=true"+
                " originalPathNewerAliasPathOlder=true"+
                " originalWorldLeaseStillHeld=true"+
                " hardlinkGateOffDuringSecondSaveOnly=true"+
                " worldCommitTimeHardlinkVetoImplemented=false"+
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
