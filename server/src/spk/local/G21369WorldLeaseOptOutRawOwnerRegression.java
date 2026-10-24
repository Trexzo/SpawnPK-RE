package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * R41: a RAW write reserved while the JVM lease flag is enabled
 * must remain exclusive against NEW World session admission even
 * if the World lease flag is toggled OFF while RAW still holds it.
 * No global process epoch, OS lock substitute or crash recovery.
 */
public final class G21369WorldLeaseOptOutRawOwnerRegression {
    private static final String NAMED="r41-named";
    private static final String REPO="r41-repository";
    private G21369WorldLeaseOptOutRawOwnerRegression(){}
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("R41_WORLD_OPT_OUT "+why);
    }

    private static void heldRawWins(
        World world,String username,Path file
    )throws Exception{
        byte[] unchanged=Files.readAllBytes(file);
        try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                LocalJvmAccountSessionLease.reserveRawWrite(
                    file,username)){
            check(raw!=null,"RAW reservation was not acquired");
            // First, only the session lease flag is toggled OFF while
            // RAW writer still owns the exact account pathname.
            System.setProperty(
                LocalJvmAccountSessionLease.PROPERTY,"false");
            for(boolean alsoDisableRaw:new boolean[]{false,true}){
                if(alsoDisableRaw)
                    System.setProperty(
                        LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                        "false");
                boolean denied=false;
                try{
                    LocalJvmAccountSessionLease.Token unexpected=
                        LocalJvmAccountSessionLease.acquire(
                            world.persistence(),username);
                    if(unexpected!=null)
                        unexpected.releaseAfterInitializationFailure();
                }catch(IllegalStateException expected){
                    denied=String.valueOf(expected.getMessage())
                        .contains(
                            "R41_UNLEASED_SESSION_DENIED_HELD_RAW_WRITER");
                }
                check(denied,
                    "disabled lease flag admitted RAW-owned World");
                check(Arrays.equals(unchanged,Files.readAllBytes(file)),
                    "rejected World admission changed account bytes");
                check(LocalJvmAccountSessionLease
                        .rawWriteReservationsForTest()==1&&
                    LocalJvmAccountSessionLease.activeCountForTest()==0&&
                    LocalJvmAccountSessionLease
                        .pendingSessionAdmissionsForTest()==0,
                    "rejected session stole RAW ownership");

                // The opt-out remains permissible for an account
                // unrelated to the held exact file path.
                String independent="r41-other-"+username;
                LocalJvmAccountSessionLease.Token unrelated=
                    LocalJvmAccountSessionLease.acquire(
                        world.persistence(),independent);
                check(unrelated==null,
                    "default-off unrelated account gained lease");
            }
        }finally{
            System.setProperty(
                LocalJvmAccountSessionLease.PROPERTY,"true");
            System.setProperty(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
                "true");
        }
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "RAW reservation leaked after release");
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"false");
        System.setProperty(
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"false");
        try{
            LocalJvmAccountSessionLease.Token unleased=
                LocalJvmAccountSessionLease.acquire(
                    world.persistence(),username);
            check(unleased==null,
                "unowned historical session opt-out unexpectedly leased");
        }finally{
            System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
            System.setProperty(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r41-session-opt-out-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY};
        String[] prior=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            prior[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"true");
        System.setProperty(keys[3],"false");
        World world=null;
        try{
            WorldPlayer state=new WorldPlayer();
            AccountStore.save(state.bank(),state.equipment(),
                state.movement(),state.petState(),state.playerState());
            LocalAccountProfiles.save(NAMED,state.bank(),
                state.equipment(),state.movement(),state.petState(),
                state.playerState());
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repo.save(PlayerSnapshotCodec.capture(
                REPO,new WorldPlayer()));
            world=World.isolatedForTest(60_000L,repo);

            String[] usernames={AccountStore.CANONICAL_USERNAME,
                NAMED,REPO};
            Path[] selected={AccountStore.accountFile(),
                LocalAccountProfiles.accountFile(NAMED),
                LocalAccountProfiles.accountFile(REPO)};
            for(int i=0;i<usernames.length;i++){
                check(Files.isRegularFile(selected[i],
                    LinkOption.NOFOLLOW_LINKS),
                    "genuine account file missing");
                heldRawWins(world,usernames[i],selected[i]);
            }
            check(LocalJvmAccountSessionLease.activeCountForTest()==0&&
                LocalJvmAccountSessionLease
                    .pendingSessionAdmissionsForTest()==0&&
                LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "test leaked same-JVM reservations");
            System.out.println(
                "R41_WORLD_LEASE_OPT_OUT_RAW_OWNER_PASS"+
                " heldPrimaryNamedRepositoryRawProtected=true"+
                " jvmFlagOffWorldSessionDenied=true"+
                " bothParticipationFlagsOffWorldSessionDenied=true"+
                " unrelatedAccountUnleasedAllowed=true"+
                " healthyPostReleaseDefaultOffAllowed=true"+
                " oldFileBytesUnchanged=true"+
                " reservationAccountingPreserved=true"+
                " sameJvmOnly=true globalOwnerEpoch=false"+
                " durableFinalSaveRecovery=false");
        }finally{
            if(world!=null)world.close();
            for(int i=0;i<keys.length;i++){
                if(prior[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],prior[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path file:walk.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
    }
}
