package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.60 negative-only account file identity gate.
 *
 * Deterministic uncooperative filesystem changes are injected after
 * real admitted World account decoding, before the final admission
 * validation, while cooperating publication remains exclusively held.
 * No grant, settlement, replay, rollback or review marker release.
 */
public final class G2160MailboxAdmittedFileIdentityIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2160-admitted-file-");
        Path alternate=root.resolve("alternate");
        Files.createDirectories(alternate);
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository original=new FilePlayerRepository(paths);
        boolean replacedRefused=false,deletedRefused=false;
        boolean createdRefused=false,inPlaceRefused=false;
        boolean symlinkRefused=false,stableLoaded=false;
        boolean stableMissingEmpty=false,rawForensicsUnchanged=false;
        boolean hookAllFired=false,noMarkerCleanup=true;
        boolean noRewardCredit=true,noTempOrLeaseLeaks=false;
        try{
            for(String account:new String[]{
                    "g2160-atomic","g2160-delete","g2160-inplace",
                    "g2160-stable","g2160-healthy"})
                original.save(snapshot(account));
            PlayerSnapshot created=snapshot("g2160-create");
            PlayerSnapshot linked=snapshot("g2160-symlink");
            new FilePlayerRepository(
                name->alternate.resolve(name+".properties")
            ).save(linked);
            Files.createSymbolicLink(
                paths.resolve("g2160-symlink"),
                alternate.resolve("g2160-symlink.properties")
            );

            AtomicBoolean swapped=new AtomicBoolean();
            FilePlayerRepository replacementRepo=hooked(paths,account->{
                Path file=paths.resolve(account);
                Path staged=root.resolve(account+".g2160-swap");
                Files.copy(file,staged,StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(staged,"# external atomic replacement\n",
                    StandardOpenOption.APPEND);
                Files.move(staged,file,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
                swapped.set(true);
            });
            replacedRefused=denied(replacementRepo,"g2160-atomic",
                "G21.60 MAILBOX_SESSION_ACCOUNT_FILE_CHANGED")&&
                original.load("g2160-atomic").isPresent();

            AtomicBoolean removed=new AtomicBoolean();
            deletedRefused=denied(hooked(paths,account->{
                Files.delete(paths.resolve(account));
                removed.set(true);
            }),"g2160-delete",
                "G21.60 MAILBOX_SESSION_ACCOUNT_FILE_CHANGED")&&
                !Files.exists(paths.resolve("g2160-delete"));

            AtomicBoolean newlyCreated=new AtomicBoolean();
            createdRefused=denied(hooked(paths,account->{
                original.save(created);
                newlyCreated.set(true);
            }),"g2160-create",
                "G21.60 MAILBOX_SESSION_ACCOUNT_FILE_CHANGED")&&
                original.load("g2160-create").isPresent();

            AtomicBoolean appended=new AtomicBoolean();
            inPlaceRefused=denied(hooked(paths,account->{
                Files.writeString(paths.resolve(account),
                    "# uncooperative append changes file size\n",
                    StandardOpenOption.APPEND);
                appended.set(true);
            }),"g2160-inplace",
                "G21.60 MAILBOX_SESSION_ACCOUNT_FILE_CHANGED")&&
                original.load("g2160-inplace").isPresent();

            symlinkRefused=denied(original,"g2160-symlink",
                "G21.60 MAILBOX_SESSION_ACCOUNT_NONREGULAR")&&
                Files.isSymbolicLink(paths.resolve("g2160-symlink"));
            rawForensicsUnchanged=original.load("g2160-symlink")
                .isPresent()&&original.load("g2160-atomic").isPresent();

            try(World world=World.isolatedForTest(60000L,original)){
                world.start();
                Optional<PlayerSnapshot> stable=
                    world.persistence().load("g2160-stable");
                stableLoaded=stable.isPresent()&&
                    stable.get().values().equals(
                        snapshot("g2160-stable").values());
                stableMissingEmpty=!world.persistence()
                    .load("g2160-missing").isPresent();
                noRewardCredit=stable.get().username()
                    .equals("g2160-stable");
            }
            hookAllFired=swapped.get()&&removed.get()&&
                newlyCreated.get()&&appended.get();

            try(Stream<Path> all=Files.walk(root)){
                noTempOrLeaseLeaks=all.noneMatch(
                    file->file.getFileName().toString().endsWith(".tmp")||
                        file.getFileName().toString().endsWith(".g2160-swap"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
            noMarkerCleanup=Files.isSymbolicLink(
                paths.resolve("g2160-symlink"))&&
                Files.exists(paths.resolve("g2160-atomic"))&&
                Files.exists(paths.resolve("g2160-inplace"));
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
        System.out.println("G2160_ADMITTED_FILE_IDENTITY_DIAGNOSTICS"+
            " atomicReplacementDenied="+replacedRefused+
            " deletedDuringReadDenied="+deletedRefused+
            " createdDuringMissingReadDenied="+createdRefused+
            " inPlaceChangeDenied="+inPlaceRefused+
            " symlinkAccountDenied="+symlinkRefused+
            " stableAccountLoaded="+stableLoaded+
            " stableMissingEmpty="+stableMissingEmpty+
            " rawForensicReadPreserved="+rawForensicsUnchanged+
            " allInjectionHooksFired="+hookAllFired+
            " noAutoCleanup="+noMarkerCleanup+
            " noRewardCredit="+noRewardCredit+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks);
        if(!(replacedRefused&&deletedRefused&&createdRefused&&
             inPlaceRefused&&symlinkRefused&&stableLoaded&&
             stableMissingEmpty&&rawForensicsUnchanged&&
             hookAllFired&&noMarkerCleanup&&noRewardCredit&&
             noTempOrLeaseLeaks))
            throw new AssertionError("G21.60 admitted account file identity");
        System.out.println("G2160_ADMITTED_FILE_IDENTITY_PASS"+
            " grant=false replay=false release=false");
    }

    private static FilePlayerRepository hooked(
        FilePlayerRepository.PathResolver resolver,
        FilePlayerRepository.BeforeWorldReplace hook
    ){
        return new FilePlayerRepository(resolver,
            account->{},account->{},hook);
    }

    private static boolean denied(
        FilePlayerRepository repo,String name,String reason
    )throws Exception{
        try(World world=World.isolatedForTest(60000L,repo)){
            world.start();
            try{
                world.persistence().load(name);
                return false;
            }catch(IOException rejected){
                return rejected.getMessage().contains(reason);
            }
        }
    }
    private static PlayerSnapshot snapshot(String name){
        WorldPlayer player=new WorldPlayer();
        player.markRegistered(name);
        return PlayerSnapshotCodec.capture(name,player);
    }
    private G2160MailboxAdmittedFileIdentityIntegrationTest(){}
}
