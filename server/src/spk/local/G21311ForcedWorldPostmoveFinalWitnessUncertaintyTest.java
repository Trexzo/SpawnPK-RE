package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** G21.311: real forced World save, witness appears AFTER account move. */
public final class G21311ForcedWorldPostmoveFinalWitnessUncertaintyTest {
    private G21311ForcedWorldPostmoveFinalWitnessUncertaintyTest(){}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.311 "+reason);
    }
    private static PlayerSnapshot snapshot(String user,String phase){
        WorldPlayer p=new WorldPlayer();
        p.snapshotExtensions().replaceNamespace(
            "core_g21311",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(user,p);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21311-postmove-witness-");
        String key="spk.local.accountFile",prior=System.getProperty(key);
        String flag=FilePlayerRepository
            .EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY;
        String old=System.getProperty(flag);
        System.setProperty(key,root.resolve("opensrc.properties").toString());
        System.setProperty(flag,"true");
        World world=null;WorldPlayer owner=null;long generation=-1;
        try{
            String user="g21311-selected";
            Path account=LocalAccountProfiles.accountFile(user);
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(account);
            AtomicReference<byte[]> moved=new AtomicReference<>();
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                a->{},a->{},a->{},a->{},a->{},
                a->{
                    check(user.equals(a),"wrong exact account move");
                    moved.set(Files.readAllBytes(account));
                    Files.write(witness,"UNTRUSTED_AFTER_WORLD_MOVE".getBytes(
                        StandardCharsets.US_ASCII),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                });
            repository.save(snapshot(user,"BEFORE"));
            byte[] oldAccount=Files.readAllBytes(account);
            world=World.isolatedForTest(60_000L,repository);
            owner=new WorldPlayer();
            generation=world.registerPlayer(owner,user);
            world.start();
            final World live=world;
            final WorldPlayer player=owner;
            final long registered=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();
            world.submitAndWait(owner,generation,()->{
                player.snapshotExtensions().replaceNamespace(
                    "core_g21311",
                    Collections.singletonMap("phase","AFTER"));
                ticket.set(live.persistence().captureAndSave(
                    user,player,registered,0,"[G21.311] ",
                    "NEGATIVE_FINAL_POSTMOVE"));
            },5000L);
            check(ticket.get()!=null,"World final ticket absent");
            boolean uncertain=false;
            try{
                ticket.get().completion.get(20L,TimeUnit.SECONDS);
            }catch(ExecutionException failed){
                Throwable cause=failed.getCause();
                uncertain=cause!=null&&String.valueOf(
                    cause.getMessage()).contains(
                    "G21.271 FORCED_WORLD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW");
            }
            check(uncertain,
                "real already-moved account incorrectly reported confirmed");
            check(moved.get()!=null&&
                !Arrays.equals(oldAccount,moved.get())&&
                Arrays.equals(moved.get(),Files.readAllBytes(account)),
                "uncertain moved account was rolled back or overwritten");
            Optional<PlayerSnapshot> observed=repository.load(user);
            check(observed.isPresent()&&"AFTER".equals(
                observed.get().values().get(
                    "extension.core_g21311.phase")),
                "actual forced World postimage not retained");
            check(Files.isRegularFile(witness,
                    LinkOption.NOFOLLOW_LINKS)&&
                repository.forcedWorldPostimageCountForTest()==0,
                "late negative was removed or counted as success");
            boolean denied=false;
            try{
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(account);
            }catch(IOException expected){denied=true;}
            check(denied,"late witness lost restart denial");
            System.out.println(
                "G21311_REAL_FORCED_WORLD_POSTMOVE_FINAL_WITNESS_UNCERTAIN_PASS"+
                " genuineWorldSaveTask=true"+
                " accountAlreadyMoved=true"+
                " lateUntrustedWitnessRetained=true"+
                " completionExceptionalUncertain=true"+
                " noRollbackOrGrant=true"+
                " noAuthenticatedReceipt=true");
        }finally{
            if(world!=null){
                if(owner!=null&&generation>0L)
                    world.unregisterPlayer(owner,generation);
                world.close();
            }
            if(prior==null)System.clearProperty(key);
            else System.setProperty(key,prior);
            if(old==null)System.clearProperty(flag);
            else System.setProperty(flag,old);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }
}
