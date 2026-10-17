package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.233 deterministic publication-race proof: a hardlink can appear
 * INSIDE the World file publication critical section after earlier
 * pre-temp checks. G21.232 second check must veto exact replacement.
 */
public final class G21233CoreInsideWorldPublicationHardlinkRaceTest {
    private G21233CoreInsideWorldPublicationHardlinkRaceTest(){}
    private static final String USER="g21233-inside-lock";
    private static final String NS="core_g21233";
    private static final String KEY="extension.core_g21233.phase";
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.233 "+reason);
    }
    private static String phase(FilePlayerRepository repo)throws Exception{
        Optional<PlayerSnapshot> x=repo.load(USER);
        check(x.isPresent(),"snapshot missing");
        return x.get().values().get(KEY);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21233-inline-link-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY};
        String[] prior=new String[keys.length];
        for(int i=0;i<keys.length;i++)prior[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");

        World world=null;
        WorldPlayer player=null;
        LocalJvmAccountSessionLease.Token token=null;
        long generation=-1L;
        try{
            Path canonical=LocalAccountProfiles.accountFile(USER);
            Path alias=canonical.resolveSibling(
                "g21233-inside-lock-alias.properties");
            AtomicBoolean armed=new AtomicBoolean();
            AtomicBoolean linked=new AtomicBoolean();
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                ignored->{},
                ignored->{
                    if(armed.compareAndSet(true,false)){
                        check(Files.isRegularFile(
                            canonical,LinkOption.NOFOLLOW_LINKS),
                            "canonical snapshot missing in callback");
                        Files.createLink(alias,canonical);
                        linked.set(true);
                    }
                });
            FilePlayerRepository aliasRepo=new FilePlayerRepository(
                ignored->alias);
            world=World.isolatedForTest(60_000L,repository);
            player=new WorldPlayer();
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),USER);
            check(token!=null,"opt-in owner missing");
            generation=world.registerPlayer(player,USER);
            token.bindAdmittedWorldOwner(player,generation);
            world.start();
            final World live=world;
            final WorldPlayer owner=player;
            final long admitted=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();

            world.submitAndWait(owner,admitted,()->{
                owner.snapshotExtensions().replaceNamespace(NS,
                    Collections.singletonMap("phase","BASELINE"));
                ticket.set(live.persistence().captureAndSave(
                    USER,owner,admitted,0,"[G21.233] ","FIRST_SAVE"));
            },5_000L);
            ticket.get().completion.get(10L,TimeUnit.SECONDS);
            check("BASELINE".equals(phase(repository)),
                "first World postimage missing");
            check(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "test alias existed before second publication");

            armed.set(true);
            ticket.set(null);
            world.submitAndWait(owner,admitted,()->{
                owner.snapshotExtensions().replaceNamespace(NS,
                    Collections.singletonMap("phase","SECOND_ATTEMPT"));
                ticket.set(live.persistence().captureAndSave(
                    USER,owner,admitted,0,"[G21.233] ",
                    "SECOND_SAVE_WITH_IN_LOCK_RACE"));
            },5_000L);
            check(ticket.get()!=null,"second World SaveTask missing");
            boolean refused=false;
            try{
                ticket.get().completion.get(10L,TimeUnit.SECONDS);
            }catch(java.util.concurrent.ExecutionException failure){
                Throwable why=failure.getCause();
                refused=why!=null&&why.getMessage()!=null&&
                    why.getMessage().contains(
                        "G21.230 MULTIPLE_ACCOUNT_HARDLINKS_DENIED");
            }
            check(linked.get(),"in-publication hook never created link");
            check(refused,"World postimage committed after hardlink race");
            check("BASELINE".equals(phase(repository))&&
                    "BASELINE".equals(phase(aliasRepo)),
                "denied World publication modified either pathname");
            check(Files.isSameFile(canonical,alias),
                "refused publication separated hardlink inode");
            check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                "failed World worker released original lease");
            LocalJvmAccountSessionLease.requireCurrentWorldOwnerForTest(
                canonical,owner,admitted);
            System.out.println(
                "G21233_INSIDE_WORLD_PUBLICATION_HARDLINK_VETO_PASS"+
                " actualWorldPublishHookAfterPrecheck=true"+
                " realHardlinkCreatedInsideWorldPublicationLock=true"+
                " finalNofollowHardlinkCheckRejected=true"+
                " accountAliasPreservedOriginalPostimage=true"+
                " originalJVMOwnerStillHeld=true"+
                " externalBypassWriterFenced=false"+
                " fullyAtomicIdentityEpoch=false"+
                " crashRecovery=false");
        }finally{
            try{
                if(world!=null&&player!=null&&generation>0L)
                    world.unregisterPlayer(player,generation);
                if(token!=null)token.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                for(int i=0;i<keys.length;i++){
                    if(prior[i]==null)System.clearProperty(keys[i]);
                    else System.setProperty(keys[i],prior[i]);
                }
                try(Stream<Path> walk=Files.walk(root)){
                    for(Path p:walk.sorted(Comparator.reverseOrder())
                            .toArray(Path[]::new))Files.deleteIfExists(p);
                }
            }
        }
    }
}
