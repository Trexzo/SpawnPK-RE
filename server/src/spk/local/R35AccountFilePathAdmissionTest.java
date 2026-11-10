package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * R35: distinguish genuinely missing persistent accounts from unsafe
 * existing paths. All fixtures are newly created in a disposable directory.
 * No owner accounts, sockets, real client, or Windows Firewall access.
 */
public final class R35AccountFilePathAdmissionTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("spawnpk-r35-path-");
        boolean symlinksChecked=false;
        try {
            Path missing=root.resolve("missing").resolve("opensrc.properties");
            FilePlayerRepository missingRepo=new FilePlayerRepository(
                ignored->missing);
            check(!missingRepo.load("opensrc").isPresent(),
                "actually absent account may use fresh defaults");

            Path directory=root.resolve("occupied-account.properties");
            Files.createDirectories(directory);
            FilePlayerRepository directoryRepo=new FilePlayerRepository(
                ignored->directory);
            expectPathRejection(directoryRepo,
                "directory used as account must not be mistaken for missing");
            expectStrictWorldRejection(directoryRepo);

            // An actual regular file with invalid snapshot contents is still
            // handled by existing schema validation, not misclassified absent.
            Path malformed=root.resolve("invalid-account.properties");
            byte[] malformedBytes="format.version=2147483647\\nusername=opensrc\\n"
                .replace("\\n","\n").getBytes(StandardCharsets.US_ASCII);
            Files.write(malformed,malformedBytes);
            FilePlayerRepository malformedRepo=new FilePlayerRepository(
                ignored->malformed);
            try {
                malformedRepo.load("opensrc");
                throw new AssertionError("R35 future-version snapshot admitted");
            } catch(IOException expected) {
                check(!expected.getMessage().contains("R35_ACCOUNT_PATH_REJECTED"),
                    "ordinary file must reach original schema validation");
            }
            check(java.util.Arrays.equals(Files.readAllBytes(malformed),
                malformedBytes),"malformed file bytes not altered by read");

            // Windows runners can deny symlink creation without developer-mode
            // privileges. Linux and symlink-capable Windows exercise both
            // leaf-link and parent-link refusal; record skip transparently.
            Path target=root.resolve("real-account-target.properties");
            byte[] targetBytes="SENTINEL: DO NOT READ OUTSIDE PROFILE DIRECTORY"
                .getBytes(StandardCharsets.US_ASCII);
            Files.write(target,targetBytes);
            Path link=root.resolve("linked-account.properties");
            try {
                Files.createSymbolicLink(link,target);
                symlinksChecked=true;
            } catch(UnsupportedOperationException | SecurityException |
                    java.nio.file.FileSystemException noLinkPrivilege) {
                // Not an accepted symlink: platform could not create fixture.
            }
            if(symlinksChecked){
                FilePlayerRepository linkRepo=new FilePlayerRepository(
                    ignored->link);
                expectPathRejection(linkRepo,
                    "existing leaf symlink must not be followed");
                expectStrictWorldRejection(linkRepo);
                Path broken=root.resolve("dangling-account.properties");
                Files.createSymbolicLink(broken,
                    root.resolve("nonexistent-target.properties"));
                expectPathRejection(new FilePlayerRepository(
                    ignored->broken),
                    "dangling link must not be called a missing account");

                Path physical=root.resolve("physical");
                Files.createDirectories(physical);
                Path parentLink=root.resolve("alias-dir");
                Files.createSymbolicLink(parentLink,physical);
                FilePlayerRepository ancestorRepo=new FilePlayerRepository(
                    ignored->parentLink.resolve("opensrc.properties"));
                expectPathRejection(ancestorRepo,
                    "symlink account parent must fail closed");
            }
            check(java.util.Arrays.equals(Files.readAllBytes(target),
                targetBytes),"target file unchanged by all refusal checks");
            System.out.println("R35_ACCOUNT_PATH_ADMISSION_PASS assertions="
                +assertions+" absentAllowed=true"
                +" existingDirectoryRejected=true"
                +" regularFileSchemaStillValidated=true"
                +" symlinkCasesChecked="+symlinksChecked
                +" noProfileOverwrites=true noOwnerDataTouched=true");
        } finally {
            try(Stream<Path> paths=Files.walk(root)){
                paths.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException e){throw new IllegalStateException(
                        "R35 test-owned file cleanup failure",e);}
                });
            }
        }
    }

    private static void expectPathRejection(FilePlayerRepository repo,
        String why) throws Exception {
        try {
            Optional<PlayerSnapshot> snapshot=repo.load("opensrc");
            throw new AssertionError("R35 "+why+" value="+snapshot);
        }catch(IOException expected) {
            check(expected.getMessage().contains("R35_ACCOUNT_PATH_REJECTED"),
                why+" must carry precise path-refusal marker");
        }
    }

    private static void expectStrictWorldRejection(FilePlayerRepository repo)
            throws Exception {
        try(World world=World.isolatedForTest(600,repo)){
            try{
                LocalAccountLifecycle.loadStrict(
                    new LocalAccountLifecycle.Selection("opensrc",true),
                    new WorldPlayer(),world.persistence(),
                    value->true,"[r35-path] ");
                throw new AssertionError("R35 strict World admission succeeded");
            }catch(IllegalStateException expected){
                check(expected.getMessage().contains(
                    "persistent profile load failed"),
                    "strict lifecycle refuses path error");
            }
            check(world.players().size()==0,
                "path refusal must not register a WorldPlayer");
            check(world.tickTargetsSnapshot().isEmpty(),
                "path refusal must not attach world tick target");
        }
    }

    private static void check(boolean valid,String reason){
        assertions++;
        if(!valid)throw new AssertionError("R35 "+reason);
    }

    private R35AccountFilePathAdmissionTest(){}
}
