package spk.local;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class LegacyPersistenceTempCleanupTest {
    public static void main(String[] args)throws Exception{
        Path root=
            Files.createTempDirectory(
                "spk-legacy-temp-cleanup-"
            );

        String oldAccountFile=
            System.getProperty(
                "spk.local.accountFile"
            );

        try{
            testPrimaryFailureCleanup(root);
            testSecondaryFailureCleanup(root);

            System.out.println(
                "LEGACY_PERSISTENCE_TEMP_CLEANUP_PASS "+
                "primaryFailureCleanup=true "+
                "secondaryFailureCleanup=true "+
                "primarySuccess=true "+
                "secondarySuccess=true"
            );
        }finally{
            if(oldAccountFile==null)
                System.clearProperty(
                    "spk.local.accountFile"
                );
            else
                System.setProperty(
                    "spk.local.accountFile",
                    oldAccountFile
                );

            deleteTree(root);
        }
    }

    private static void testPrimaryFailureCleanup(
        Path root
    )throws Exception{
        Path target=
            root.resolve(
                "opensrc.properties"
            );

        makeUnreplaceableDirectory(
            target
        );

        System.setProperty(
            "spk.local.accountFile",
            target.toString()
        );

        Path tmp=
            tempSibling(target);

        expectIOException(
            ()->AccountStore.save(
                new BankState(),
                new EquipmentState(),
                new MovementState(),
                new PetState(),
                new PlayerState()
            )
        );

        if(Files.exists(tmp))
            throw new AssertionError(
                "primary failed save left temp file "+
                tmp
            );

        deleteTree(target);

        String result=
            AccountStore.save(
                new BankState(),
                new EquipmentState(),
                new MovementState(),
                new PetState(),
                new PlayerState()
            );

        if(!Files.isRegularFile(target)||
           Files.exists(tmp)||
           !result.startsWith(
                "ACCOUNT_SAVED"))
            throw new AssertionError(
                "primary successful save regressed result="+
                result+
                " file="+target+
                " tmp="+Files.exists(tmp)
            );
    }

    private static void testSecondaryFailureCleanup(
        Path root
    )throws Exception{
        Path primaryAnchor=
            root.resolve(
                "primary-anchor.properties"
            );

        System.setProperty(
            "spk.local.accountFile",
            primaryAnchor.toString()
        );

        Path target=
            root.resolve(
                LocalAccountProfiles.SECONDARY+
                ".properties"
            );

        Files.deleteIfExists(target);

        makeUnreplaceableDirectory(
            target
        );

        Path tmp=
            tempSibling(target);

        expectIOException(
            ()->LocalAccountProfiles.save(
                LocalAccountProfiles.SECONDARY,
                new BankState(),
                new EquipmentState(),
                new MovementState(),
                new PetState(),
                new PlayerState()
            )
        );

        if(Files.exists(tmp))
            throw new AssertionError(
                "secondary failed save left temp file "+
                tmp
            );

        deleteTree(target);

        String result=
            LocalAccountProfiles.save(
                LocalAccountProfiles.SECONDARY,
                new BankState(),
                new EquipmentState(),
                new MovementState(),
                new PetState(),
                new PlayerState()
            );

        if(!Files.isRegularFile(target)||
           Files.exists(tmp)||
           !result.startsWith(
                "ACCOUNT_SAVED"))
            throw new AssertionError(
                "secondary successful save regressed result="+
                result+
                " file="+target+
                " tmp="+Files.exists(tmp)
            );
    }

    private static void makeUnreplaceableDirectory(
        Path target
    )throws IOException{
        Files.createDirectories(target);

        Files.write(
            target.resolve("blocker"),
            new byte[]{1},
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
    }

    private static Path tempSibling(
        Path target
    ){
        return target.resolveSibling(
            target.getFileName().toString()+
            ".tmp"
        );
    }

    private static void expectIOException(
        IoAction action
    )throws Exception{
        boolean failed=false;

        try{
            action.run();
        }catch(IOException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "forced replacement failure unexpectedly succeeded"
            );
    }

    private static void deleteTree(
        Path root
    )throws IOException{
        if(root==null||
           !Files.exists(root))
            return;

        try(java.util.stream.Stream<Path> stream=
                Files.walk(root)){
            Iterator<Path> iterator=
                stream.sorted(
                    Comparator.reverseOrder()
                ).iterator();

            while(iterator.hasNext())
                Files.deleteIfExists(
                    iterator.next()
                );
        }
    }

    @FunctionalInterface
    private interface IoAction {
        void run()throws Exception;
    }
}