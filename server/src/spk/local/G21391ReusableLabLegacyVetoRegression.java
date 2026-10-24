package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** R63: real default-off legacy RAW/World/session paths must refuse
 * EVERY detached LAB namespace, including an orphan without HEAD. */
public final class G21391ReusableLabLegacyVetoRegression {
    private G21391ReusableLabLegacyVetoRegression(){}
    private static final String CODE=
        "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION";
    @FunctionalInterface interface Action{void run()throws IOException;}
    private static void check(boolean b,String message){
        if(!b)throw new AssertionError("R63_LAB_VETO "+message);
    }
    private static boolean has(Throwable error,String fragment){
        for(Throwable e=error;e!=null;e=e.getCause())
            if(String.valueOf(e.getMessage()).contains(fragment))return true;
        return false;
    }
    private static void denied(String name,Path account,Action action)
        throws Exception{
        byte[] original=Files.readAllBytes(account);
        boolean veto=false;
        try{action.run();}
        catch(IOException error){veto=has(error,CODE);}
        check(veto,name+" did not reject retained LAB evidence");
        check(Arrays.equals(original,Files.readAllBytes(account)),
            name+" changed real account bytes");
        check(!Files.exists(account.resolveSibling(
                account.getFileName()+".tmp"),LinkOption.NOFOLLOW_LINKS),
            name+" stranded a legacy RAW temp");
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            name+" leaked RAW reservations");
    }
    private static void exercise(
        String name,Path selected,Action raw,Action world,Action session
    )throws Exception{
        raw.run();
        check(Files.isRegularFile(selected,LinkOption.NOFOLLOW_LINKS),
            name+" no healthy RAW baseline");
        for(String suffix:new String[]{
            ".g21385-image-orphan",
            ".g21386-intent-orphan",
            ".g21387-head-orphan"
        }){
            Path marker=selected.resolveSibling(
                selected.getFileName()+suffix);
            Files.write(marker,"NEGATIVE".getBytes(StandardCharsets.US_ASCII),
                StandardOpenOption.CREATE_NEW);
            denied(name+" raw "+suffix,selected,raw);
            denied(name+" world "+suffix,selected,world);
            denied(name+" session "+suffix,selected,session);
            check(Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS),
                name+" LAB evidence auto-removed");
            Files.delete(marker); // disposable fixture only, not recovery
        }
    }
    private static void late(
        Path account,Action save,boolean after
    )throws Exception{
        save.run();
        byte[] original=Files.readAllBytes(account);
        AtomicBoolean reached=new AtomicBoolean();
        LocalNegativeAccountHeadPrototype.RawPublicationProbe callback=file->{
            check(file.equals(account),"wrong account at fault boundary");
            Files.write(file.resolveSibling(file.getFileName()+
                (after?".g21386-receipt-orphan":
                    ".g21385-image-orphan")),
                new byte[]{1},StandardOpenOption.CREATE_NEW);
            reached.set(true);
        };
        try{
            if(after)
                LocalNegativeAccountHeadPrototype
                    .setAfterRawMoveForTest(callback);
            else
                LocalNegativeAccountHeadPrototype
                    .setBeforeRawMoveForTest(callback);
            boolean veto=false;
            try{save.run();}
            catch(IOException failed){
                veto=has(failed,after
                    ?"R54_NEGATIVE_HEAD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW"
                    :CODE);
            }
            check(veto,"in-flight LAB artifact got normal RAW ACK");
            check(reached.get(),"fault injection not reached");
            if(!after)
                check(Arrays.equals(original,Files.readAllBytes(account)),
                    "prepublication refusal changed original account");
        }finally{
            LocalNegativeAccountHeadPrototype
                .setBeforeRawMoveForTest(null);
            LocalNegativeAccountHeadPrototype
                .setAfterRawMoveForTest(null);
        }
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "inflight RAW lease leaked");
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"args");
        Path root=Files.createTempDirectory("r63-lab-veto-");
        String[] props={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY
        };
        String[] previous=new String[props.length];
        for(int i=0;i<props.length;i++){
            previous[i]=System.getProperty(props[i]);
            System.clearProperty(props[i]);
        }
        try{
            WorldPlayer player=new WorldPlayer();
            FilePlayerRepository repo=new FilePlayerRepository(
                name->root.resolve(name+".properties"));
            String standard="r63-repository";
            exercise(standard,root.resolve(standard+".properties"),
                ()->repo.save(PlayerSnapshotCodec.capture(
                    standard,new WorldPlayer())),
                ()->repo.saveForWorld(PlayerSnapshotCodec.capture(
                    standard,new WorldPlayer())),
                ()->repo.loadForWorldSession(standard));

            System.setProperty(props[0],
                root.resolve("opensrc.properties").toString());
            Path primary=AccountStore.accountFile();
            FilePlayerRepository primaryRepo=
                new FilePlayerRepository(name->primary);
            exercise("primary",primary,
                ()->AccountStore.save(
                    player.bank(),player.equipment(),player.movement(),
                    player.petState(),player.playerState()),
                ()->primaryRepo.saveForWorld(PlayerSnapshotCodec.capture(
                    "opensrc",new WorldPlayer())),
                ()->primaryRepo.loadForWorldSession("opensrc"));

            String named="r63-named";
            Path namedPath=LocalAccountProfiles.accountFile(named);
            FilePlayerRepository namedRepo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            exercise("named",namedPath,
                ()->LocalAccountProfiles.save(named,
                    player.bank(),player.equipment(),player.movement(),
                    player.petState(),player.playerState()),
                ()->namedRepo.saveForWorld(PlayerSnapshotCodec.capture(
                    named,new WorldPlayer())),
                ()->namedRepo.loadForWorldSession(named));

            String strictName="r63-strict";
            Path strictPath=root.resolve(strictName+".properties");
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(
                    name->root.resolve(name+".properties"));
            exercise("strict",strictPath,
                ()->strict.saveStrict(PlayerSnapshotCodec.capture(
                    strictName,new WorldPlayer())),
                ()->repo.saveForWorld(PlayerSnapshotCodec.capture(
                    strictName,new WorldPlayer())),
                ()->repo.loadForWorldSession(strictName));

            String pre="r63-late-pre";
            late(root.resolve(pre+".properties"),
                ()->repo.save(PlayerSnapshotCodec.capture(
                    pre,new WorldPlayer())),false);
            String post="r63-late-post";
            late(root.resolve(post+".properties"),
                ()->repo.save(PlayerSnapshotCodec.capture(
                    post,new WorldPlayer())),true);

            // A complete, syntactically valid LAB HEAD is also only
            // evidence for legacy veto, never a real World grant.
            Path laboratory=root.resolve("r63-lab.properties");
            System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,
                "true");
            System.setProperty(
                LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
            LocalReusableAccountEpochLab.initialize(laboratory,
                "LAB".getBytes(StandardCharsets.US_ASCII));
            System.clearProperty(LocalCrossJvmAccountSessionLock.PROPERTY);
            System.clearProperty(
                LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY);
            check(!Files.exists(laboratory,LinkOption.NOFOLLOW_LINKS),
                "LAB initialized legacy account file");
            boolean labDenied=false;
            try{repo.save(PlayerSnapshotCodec.capture(
                    "r63-lab",new WorldPlayer()));}
            catch(IOException error){labDenied=has(error,CODE);}
            check(labDenied,"full LAB HEAD permitted legacy RAW");
            check(!Files.exists(laboratory,LinkOption.NOFOLLOW_LINKS),
                "LAB evidence caused legacy publication");

            String healthy="r63-healthy";
            repo.save(PlayerSnapshotCodec.capture(
                healthy,new WorldPlayer()));
            check(repo.loadForWorldSession(healthy).isPresent(),
                "healthy separate account refused");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "live World lease leaked");
            System.out.println("R63_LEGACY_REUSABLE_LAB_EVIDENCE_VETO_PASS"+
                " rawWriters=4 worldSaves=4 sessionLoads=4"+
                " namespaceOrphans=3"+
                " lateRawPrePost=true"+
                " genuineCompleteLabHeadDenied=true"+
                " unrelatedDefaultOffAccount=true"+
                " liveAdmissionAuthority=false");
        }finally{
            LocalNegativeAccountHeadPrototype
                .setBeforeRawMoveForTest(null);
            LocalNegativeAccountHeadPrototype
                .setAfterRawMoveForTest(null);
            for(int i=0;i<props.length;i++){
                if(previous[i]==null)System.clearProperty(props[i]);
                else System.setProperty(props[i],previous[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}
