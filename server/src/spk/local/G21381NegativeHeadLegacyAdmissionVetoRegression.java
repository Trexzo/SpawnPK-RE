package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/** R53: permanent negative HEAD blocks legacy RAW, World writes and
 * session hydration without enabling experimental publisher flags. */
public final class G21381NegativeHeadLegacyAdmissionVetoRegression {
    private G21381NegativeHeadLegacyAdmissionVetoRegression(){}
    @FunctionalInterface interface Operation{void run()throws IOException;}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("R53_NEGATIVE_HEAD "+reason);
    }
    private static void blocked(
        String name,Path account,Operation action
    )throws Exception{
        byte[] preimage=Files.readAllBytes(account);
        boolean veto=false;
        try{action.run();}
        catch(IOException error){
            for(Throwable cause=error;cause!=null;cause=cause.getCause())
                if(String.valueOf(cause.getMessage()).contains(
                    "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY"))
                    veto=true;
        }
        check(veto,name+" did not reject negative HEAD");
        check(Arrays.equals(preimage,Files.readAllBytes(account)),
            name+" mutated account on rejected operation");
        check(!Files.exists(account.resolveSibling(
                account.getFileName()+".tmp"),LinkOption.NOFOLLOW_LINKS),
            name+" allocated RAW temp");
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            name+" leaked RAW reservation");
    }
    private static void scenario(
        String name,Path account,Operation raw,Operation world,
        Operation session
    )throws Exception{
        raw.run();
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            name+" baseline account missing");
        LocalNegativeAccountHeadPrototype.NegativeObservation head=
            LocalNegativeAccountHeadPrototype.publishNegative(
                account,name,31L,"R53_NEGATIVE_ONLY".getBytes(
                    StandardCharsets.US_ASCII));
        check(!head.admissionAuthorized&&!head.releaseAuthorized,
            "HEAD fixture returned authorization");
        blocked(name+" RAW",account,raw);
        blocked(name+" World",account,world);
        blocked(name+" session",account,session);
        check(Files.isRegularFile(
            LocalNegativeAccountHeadPrototype.headPath(account),
            LinkOption.NOFOLLOW_LINKS),name+" HEAD was retired");
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"args");
        Path root=Files.createTempDirectory("r53-neg-head-veto-");
        String[] keys={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY
        };
        String[] before=new String[keys.length];
        for(int i=0;i<keys.length;i++){
            before[i]=System.getProperty(keys[i]);
            System.clearProperty(keys[i]);
        }
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        try{
            WorldPlayer player=new WorldPlayer();
            FilePlayerRepository repository=new FilePlayerRepository(
                u->root.resolve(u+".properties"));
            String repo="r53-repository";
            scenario(repo,root.resolve(repo+".properties"),
                ()->repository.save(PlayerSnapshotCodec.capture(
                    repo,new WorldPlayer())),
                ()->repository.saveForWorld(PlayerSnapshotCodec.capture(
                    repo,new WorldPlayer())),
                ()->repository.loadForWorldSession(repo));

            String primary="opensrc";
            Path primaryFile=AccountStore.accountFile();
            FilePlayerRepository primaryRepo=
                new FilePlayerRepository(u->primaryFile);
            scenario(primary,primaryFile,
                ()->AccountStore.save(
                    player.bank(),player.equipment(),player.movement(),
                    player.petState(),player.playerState()),
                ()->primaryRepo.saveForWorld(PlayerSnapshotCodec.capture(
                    primary,new WorldPlayer())),
                ()->primaryRepo.loadForWorldSession(primary));

            String named="r53-named";
            Path namedFile=LocalAccountProfiles.accountFile(named);
            FilePlayerRepository namedRepo=
                new FilePlayerRepository(LocalAccountProfiles::accountFile);
            scenario(named,namedFile,
                ()->LocalAccountProfiles.save(named,
                    player.bank(),player.equipment(),player.movement(),
                    player.petState(),player.playerState()),
                ()->namedRepo.saveForWorld(PlayerSnapshotCodec.capture(
                    named,new WorldPlayer())),
                ()->namedRepo.loadForWorldSession(named));

            String strictName="r53-strict";
            Path strictFile=root.resolve(strictName+".properties");
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(
                    u->root.resolve(u+".properties"));
            FilePlayerRepository strictRepo=
                new FilePlayerRepository(u->root.resolve(u+".properties"));
            scenario(strictName,strictFile,
                ()->strict.saveStrict(PlayerSnapshotCodec.capture(
                    strictName,new WorldPlayer())),
                ()->strictRepo.saveForWorld(PlayerSnapshotCodec.capture(
                    strictName,new WorldPlayer())),
                ()->strictRepo.loadForWorldSession(strictName));

            String malformed="r53-malformed";
            Path badAccount=root.resolve(malformed+".properties");
            Operation writeBad=()->repository.save(
                PlayerSnapshotCodec.capture(malformed,new WorldPlayer()));
            writeBad.run();
            Files.write(
                LocalNegativeAccountHeadPrototype.headPath(badAccount),
                "truncated".getBytes(StandardCharsets.US_ASCII),
                StandardOpenOption.CREATE_NEW);
            blocked("malformed RAW",badAccount,writeBad);
            blocked("malformed session",badAccount,()->
                repository.loadForWorldSession(malformed));
            String clean="r53-clean";
            repository.save(PlayerSnapshotCodec.capture(
                clean,new WorldPlayer()));
            check(repository.loadForWorldSession(clean).isPresent(),
                "foreign HEAD blocked healthy legacy account");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "JVM session token leak");
            System.out.println("R53_NEGATIVE_HEAD_LEGACY_ADMISSION_VETO_PASS"+
                " rawWriters=4 worldSavePaths=4 sessionReads=4"+
                " existingHeadsAlwaysNegative=true"+
                " malformedHeadNegative=true"+
                " foreignAccountControl=true"+
                " defaultOffChecks=true"+
                " noHeadAutoRecovery=true"+
                " liveTcpSessionWiringNotExercised=true"+
                " durableEpochNotProven=true");
        }finally{
            for(int i=0;i<keys.length;i++){
                if(before[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],before[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}
