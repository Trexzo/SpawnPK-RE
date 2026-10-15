package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/** G21.255: held G21.39 lock cannot hang forced RAW beyond chosen wait. */
public final class G21255ForcedRawPublicationLockWaitDeadlineTest {
    private G21255ForcedRawPublicationLockWaitDeadlineTest(){}
    private static void check(boolean pass,String why){
        if(!pass)throw new AssertionError("G21.255 "+why);
    }
    private static void noTemp(Path file)throws IOException{
        try(Stream<Path> children=Files.list(file.getParent())){
            String prefix=file.getFileName().toString()+".g21246-raw-";
            check(children.noneMatch(p->p.getFileName().toString()
                .startsWith(prefix)),
                "abandoned opt-in RAW temp");
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21255-bounded-lock-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY,
            LocalOptInRawTemp.PUBLICATION_WAIT_PROPERTY};
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)old[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length-1;i++)
            System.setProperty(keys[i],"true");
        System.setProperty(
            LocalOptInRawTemp.PUBLICATION_WAIT_PROPERTY,"150");
        CountDownLatch entered=new CountDownLatch(1);
        CountDownLatch resume=new CountDownLatch(1);
        Thread holder=null;
        try{
            WorldPlayer player=new WorldPlayer();
            AccountStore.save(player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            Path file=AccountStore.accountFile();
            byte[] oldBytes=Files.readAllBytes(file);
            AtomicReference<Throwable> lockError=new AtomicReference<>();
            holder=new Thread(()->{
                try{
                    MailboxAccountPublicationCoordinator
                        .withExclusivePublication(file,()->{
                            entered.countDown();
                            try{
                                if(!resume.await(10L,TimeUnit.SECONDS))
                                    throw new IOException(
                                        "G21.255 test release timeout");
                            }catch(InterruptedException ex){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "G21.255 test lock interrupted",ex);
                            }
                            return null;
                        });
                }catch(Throwable problem){lockError.set(problem);}
            },"g21255-owned-publisher-lock");
            holder.start();
            check(entered.await(10L,TimeUnit.SECONDS),
                "G21.39 publication lock never acquired");
            long start=System.nanoTime();
            boolean denied=false;
            try{
                AccountStore.save(player.bank(),player.equipment(),
                    player.movement(),player.petState(),
                    player.playerState());
            }catch(IOException expected){
                denied=String.valueOf(expected.getMessage()).contains(
                    "RECOVERY_PUBLICATION_BUSY_NO_GRANT");
            }
            long elapsedMillis=TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime()-start);
            check(denied,"contending forced RAW did not fail closed");
            check(elapsedMillis<4000L,
                "forced writer exceeded bounded wait "+elapsedMillis);
            check(Arrays.equals(oldBytes,Files.readAllBytes(file)),
                "bounded refusal changed old account bytes");
            noTemp(file);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW reservation retained after lock-timeout");
            resume.countDown();
            holder.join(10_000L);
            check(!holder.isAlive()&&lockError.get()==null,
                "publication holder not released "+lockError.get());
            AccountStore.save(player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            noTemp(file);

            System.setProperty(
                LocalOptInRawTemp.PUBLICATION_WAIT_PROPERTY,"no-timeout");
            byte[] healthy=Files.readAllBytes(file);
            boolean invalid=false;
            try{
                AccountStore.save(player.bank(),player.equipment(),
                    player.movement(),player.petState(),
                    player.playerState());
            }catch(IOException expected){
                invalid=String.valueOf(expected.getMessage()).contains(
                    "INVALID_FORCED_RAW_PUBLICATION_LOCK_WAIT");
            }
            check(invalid,"invalid lock deadline was not rejected");
            check(Arrays.equals(healthy,Files.readAllBytes(file)),
                "bad lock deadline changed healthy account");
            noTemp(file);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW reservation retained after invalid config");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "G21.39 JVM publication lease leaked");
            System.out.println(
                "G21255_FORCED_RAW_PUBLICATION_LOCK_DEADLINE_PASS"+
                " genuineHeldG2139Monitor=true"+
                " boundedBusyDenial=true"+
                " oldSnapshotUnchanged=true"+
                " succeedsAfterLockRelease=true"+
                " invalidDeadlineFailsClosed=true"+
                " ownedTempAndReservationsClean=true"+
                " originalDurableOwnerEpochUnknown=true");
        }finally{
            resume.countDown();
            if(holder!=null&&holder.isAlive())holder.join(3_000L);
            for(int i=0;i<keys.length;i++){
                if(old[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],old[i]);
            }
            try(Stream<Path> stream=Files.walk(root)){
                for(Path p:(Iterable<Path>)stream.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
