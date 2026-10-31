package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Optional;

/**
 * P5.49: read actual provider directory-identity evidence at the
 * G21.171 World-save boundary. This test does NOT relax G21.171,
 * mint a synthetic fileKey, authorize an unsupported provider,
 * or make World account publication crash-durable.
 *
 * A passing Windows capability-negative path means that the actual
 * World save was correctly refused. It emphatically does NOT mean
 * that a positive Windows World account save was certified.
 */
public final class PlayableWorldAncestryProviderEvidenceTest {
    private PlayableWorldAncestryProviderEvidenceTest(){}

    private static void require(boolean good,String detail){
        if(!good)throw new AssertionError(
            "P549_WORLD_ANCESTRY_EVIDENCE "+detail);
    }

    private static String safePath(Path path){
        // Include the exact failed component in CI evidence without
        // printing opaque filesystem file IDs or account contents.
        return path.toAbsolutePath().normalize().toString()
            .replace(' ', '_');
    }

    private static boolean observeAncestry(Path account)
        throws IOException{
        boolean safe=true;
        int index=0;
        for(Path dir=account.getParent();dir!=null;
                dir=dir.getParent()){
            BasicFileAttributes a=Files.readAttributes(
                dir,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            boolean accepted=a.isDirectory()&&
                !a.isSymbolicLink()&&a.fileKey()!=null;
            System.out.println(
                "P549_ANCESTOR_OBSERVED"+
                " depth="+(index++)+
                " path="+safePath(dir)+
                " directory="+a.isDirectory()+
                " symbolicLink="+a.isSymbolicLink()+
                " fileKeyPresent="+(a.fileKey()!=null)+
                " acceptedByG21171="+accepted);
            safe&=accepted;
        }
        return safe;
    }

    private static void expectG21171NoAccount(
        FilePlayerRepository repository,PlayerSnapshot offered,
        Path account, String circumstance
    )throws Exception{
        require(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            circumstance+" unexpectedly had a preexisting account");
        try{
            repository.saveForWorld(offered);
            throw new AssertionError(
                circumstance+" unexpectedly published a World account");
        }catch(IOException expected){
            require(expected.toString().contains(
                    "G21.171 WORLD_TEMP_ANCESTRY_UNSAFE_NO_AUTHORITY"),
                circumstance+" refused with unexpected code "+expected);
        }
        require(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            circumstance+" World publisher created account despite veto");
    }

    private static PlayerSnapshot snapshot(String account){
        return PlayerSnapshotCodec.capture(
            account,new WorldPlayer());
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory(
            Path.of(System.getProperty("user.dir"))
                .toAbsolutePath().normalize(),
            "spk-p549-ancestry-");
        String normalAccount="p549-safe-profile";
        Path normal=root.resolve(normalAccount+".properties");
        boolean symlinkProved=false;
        boolean symlinkUnavailable=false;
        try{
            boolean ancestrySafe=observeAncestry(normal);
            FilePlayerRepository normalRepo=new FilePlayerRepository(
                ignored->normal);
            PlayerSnapshot ordinary=snapshot(normalAccount);
            if(ancestrySafe){
                // A successful preflight is not enough by itself:
                // exercise the ACTUAL World save under its publication
                // lock, and reload the exact resulting account.
                MailboxAccountPublicationCoordinator
                    .requireSafeWorldTempAncestry(normal,true);
                normalRepo.saveForWorld(ordinary);
                Optional<PlayerSnapshot> loaded=
                    normalRepo.load(normalAccount);
                require(Files.isRegularFile(
                        normal,LinkOption.NOFOLLOW_LINKS)&&
                    loaded.isPresent()&&
                    loaded.get().username().equalsIgnoreCase(
                        normalAccount),
                    "supported provider failed actual World file roundtrip");
                System.out.println(
                    "P549_NORMAL_WORLD_SAVE_POSITIVE_PASS"+
                    " providerIdentityAvailable=true"+
                    " actualWorldFileRoundTrip=true");
            }else{
                // Windows JDK providers can expose null fileKey even
                // for real directories. Only the actual fail-closed
                // World-save result is accepted in this branch.
                expectG21171NoAccount(
                    normalRepo,ordinary,normal,
                    "missing directory identity");
                System.out.println(
                    "P549_NORMAL_WORLD_SAVE_NEGATIVE_PASS"+
                    " providerIdentityAvailable=false"+
                    " actualWorldSaveDeniedG21171=true"+
                    " noAccountPublished=true"+
                    " positiveWindowsWorldSaveNotCertified=true");
            }

            // A regular file must never be accepted as a directory
            // component. This test is independent of fileKey support.
            Path fileAncestor=root.resolve("blocked-file-ancestor");
            Files.write(fileAncestor,new byte[]{1,2,3,4});
            String blockedName="p549-blocked-profile";
            Path blocked=fileAncestor.resolve(
                blockedName+".properties");
            expectG21171NoAccount(
                new FilePlayerRepository(ignored->blocked),
                snapshot(blockedName),blocked,
                "regular-file ancestor");
            System.out.println(
                "P549_REGULAR_FILE_ANCESTOR_NEGATIVE_PASS"+
                " actualWorldSaveDeniedG21171=true");

            // Provider may forbid making a test symlink (common on
            // Windows CI). Report capability, never fake this result.
            Path real=root.resolve("real-directory");
            Files.createDirectory(real);
            Path link=root.resolve("redirect-directory");
            boolean created=false;
            try{
                Files.createSymbolicLink(link,real.getFileName());
                created=true;
            }catch(UnsupportedOperationException|
                  SecurityException|IOException unavailable){
                symlinkUnavailable=true;
                System.out.println(
                    "P549_SYMLINK_FIXTURE_UNAVAILABLE"+
                    " noSymlinkRejectionClaim=true"+
                    " exception="+
                    unavailable.getClass().getSimpleName());
            }
            if(created){
                String redirectedName="p549-redirected-profile";
                Path redirected=link.resolve(
                    redirectedName+".properties");
                expectG21171NoAccount(
                    new FilePlayerRepository(ignored->redirected),
                    snapshot(redirectedName),redirected,
                    "symlinked directory ancestor");
                require(!Files.exists(real.resolve(
                        redirectedName+".properties")),
                    "symlinked account path was followed for publication");
                symlinkProved=true;
                System.out.println(
                    "P549_SYMLINK_ANCESTOR_NEGATIVE_PASS"+
                    " actualWorldSaveDeniedG21171=true"+
                    " targetUnchanged=true");
            }
            require(symlinkProved||symlinkUnavailable,
                "symlink capability outcome unclassified");
            System.out.println(
                "P549_WORLD_ANCESTRY_EVIDENCE_PASS"+
                " normalPositiveSupported="+ancestrySafe+
                " normalNegativeUnsupported="+(!ancestrySafe)+
                " regularFileAncestorRejected=true"+
                " symlinkAncestorRejected="+symlinkProved+
                " symlinkFixtureUnavailable="+symlinkUnavailable+
                " productionG21171Unmodified=true"+
                " sourceReceiptDurabilityUnverified=true");
        }finally{
            try(java.util.stream.Stream<Path> walk=Files.walk(root)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException error){
                        throw new IllegalStateException(
                            "P549 cleanup failed "+p,error);
                    }
                });
            }
        }
    }
}
