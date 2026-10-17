package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** G21.312: unrelated witness after forced World move is not global veto. */
public final class G21312ForcedWorldForeignPostmoveFinalWitnessControlTest {
    private G21312ForcedWorldForeignPostmoveFinalWitnessControlTest(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.312 "+why);
    }
    private static PlayerSnapshot snapshot(String name,String value){
        WorldPlayer p=new WorldPlayer();
        p.snapshotExtensions().replaceNamespace(
            "core_g21312",Collections.singletonMap("phase",value));
        return PlayerSnapshotCodec.capture(name,p);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21312-foreign-postmove-");
        String key="spk.local.accountFile",prior=System.getProperty(key);
        String flag=FilePlayerRepository
            .EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY;
        String old=System.getProperty(flag);
        System.setProperty(key,root.resolve("opensrc.properties").toString());
        System.setProperty(flag,"true");
        World world=null;WorldPlayer owner=null;long generation=-1L;
        try{
            String selected="g21312-selected",foreign="g21312-other";
            Path selectedPath=LocalAccountProfiles.accountFile(selected);
            Path foreignPath=LocalAccountProfiles.accountFile(foreign);
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(foreignPath);
            AtomicReference<byte[]> moved=new AtomicReference<>();
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                a->{},a->{},a->{},a->{},a->{},
                a->{
                    check(selected.equals(a),"wrong selected World");
                    moved.set(Files.readAllBytes(selectedPath));
                    Files.write(witness,"FOREIGN_NEGATIVE_NO_GRANT".getBytes(
                        StandardCharsets.US_ASCII),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                });
            repository.save(snapshot(selected,"BEFORE"));
            repository.save(snapshot(foreign,"UNCHANGED"));
            byte[] before=Files.readAllBytes(selectedPath);
            byte[] foreignBytes=Files.readAllBytes(foreignPath);
            world=World.isolatedForTest(60_000L,repository);
            owner=new WorldPlayer();
            generation=world.registerPlayer(owner,selected);
            world.start();
            final World live=world;
            final WorldPlayer player=owner;
            final long owned=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();
            world.submitAndWait(owner,generation,()->{
                player.snapshotExtensions().replaceNamespace(
                    "core_g21312",Collections.singletonMap(
                        "phase","PUBLISHED"));
                ticket.set(live.persistence().captureAndSave(
                    selected,player,owned,0,"[G21.312] ",
                    "UNRELATED_FINAL_WITNESS"));
            },5000L);
            check(ticket.get()!=null,"real World ticket absent");
            ticket.get().completion.get(20L,TimeUnit.SECONDS);
            check(moved.get()!=null&&
                !Arrays.equals(before,moved.get())&&
                Arrays.equals(moved.get(),Files.readAllBytes(selectedPath)),
                "selected forced World postimage unexpectedly vetoed");
            Optional<PlayerSnapshot> saved=repository.load(selected);
            check(saved.isPresent()&&"PUBLISHED".equals(
                saved.get().values().get("extension.core_g21312.phase")),
                "selected persisted phase missing");
            check(Arrays.equals(foreignBytes,
                    Files.readAllBytes(foreignPath))&&
                Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS)&&
                repository.forcedWorldPostimageCountForTest()==1,
                "foreign negative changed or selected count incorrect");
            check(!Files.exists(LocalFinalSaveNegativeWitness.witnessPath(
                    selectedPath),LinkOption.NOFOLLOW_LINKS),
                "clean selected acquired a witness");
            System.out.println(
                "G21312_REAL_FORCED_WORLD_FOREIGN_FINAL_WITNESS_CONTROL_PASS"+
                " realWorldSaveTask=true"+
                " selectedPostimageConfirmed=true"+
                " unrelatedNegativeRetained=true"+
                " noGlobalWriteVeto=true"+
                " noGrantOrAutoRecovery=true");
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
