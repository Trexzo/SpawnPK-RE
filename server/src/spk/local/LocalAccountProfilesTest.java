package spk.local;

import java.nio.file.*;
import java.util.*;

public final class LocalAccountProfilesTest {
    public static void main(String[] args)throws Exception{
        Path dir=
            Files.createTempDirectory(
                "spk-v5123-accounts-"
            );
        String old=
            System.getProperty(
                "spk.local.accountFile"
            );

        try{
            Path primary=
                dir.resolve(
                    "opensrc.properties"
                );
            System.setProperty(
                "spk.local.accountFile",
                primary.toString()
            );

            verifyLegacySecondaryProfile(
                dir,
                primary
            );
            verifyGeneralProfiles(
                dir
            );

            System.out.println(
                "V5123_LOCAL_ACCOUNT_PROFILES_PASS "+
                "separateSrcFile=true "+
                "atomicSchemaCompatible=true "+
                "legacyStateRoundTrip=true "+
                "arbitraryPersistent=true "+
                "generalStateRoundTrip=true "+
                "deterministicProfilePath=true "+
                "distinctProfilePath=true "+
                "pathTraversalSafe=true"
            );
        }finally{
            if(old==null)
                System.clearProperty(
                    "spk.local.accountFile"
                );
            else
                System.setProperty(
                    "spk.local.accountFile",
                    old
                );

            try(java.util.stream.Stream<Path> st=
                    Files.walk(dir)){
                st.sorted(
                    java.util.Comparator
                        .reverseOrder()
                ).forEach(
                    path->{
                        try{
                            Files.deleteIfExists(
                                path
                            );
                        }catch(Exception ignored){}
                    }
                );
            }
        }
    }

    private static void verifyLegacySecondaryProfile(
        Path dir,
        Path primary
    )throws Exception{
        BankState bank=new BankState();
        EquipmentState equipment=
            new EquipmentState();
        MovementState movement=
            new MovementState();
        PetState pet=
            new PetState();
        PlayerState player=
            new PlayerState();

        equipment.setWeapon(21566);
        movement.setPersistentRun(true);
        movement.setRunEnergy(73);

        String saved=
            LocalAccountProfiles.save(
                "src",
                bank,
                equipment,
                movement,
                pet,
                player
            );
        Path src=
            dir.resolve(
                "src.properties"
            );

        if(!Files.isRegularFile(src)||
           Files.exists(primary))
            throw new AssertionError(
                "wrong legacy profile path saved="+
                saved
            );

        Properties props=
            new Properties();

        try(java.io.InputStream input=
                Files.newInputStream(src)){
            props.load(input);
        }

        if(!"src".equals(
                props.getProperty(
                    "username"
                )))
            throw new AssertionError(
                "legacy username property"
            );

        BankState loadedBank=
            new BankState();
        EquipmentState loadedEquipment=
            new EquipmentState();
        MovementState loadedMovement=
            new MovementState();
        PetState loadedPet=
            new PetState();
        PlayerState loadedPlayer=
            new PlayerState();

        String loaded=
            LocalAccountProfiles.load(
                "src",
                loadedBank,
                loadedEquipment,
                loadedMovement,
                loadedPet,
                loadedPlayer
            );

        if(loadedEquipment.weapon()!=21566||
           !loadedMovement.persistentRun()||
           loadedMovement.runEnergy()!=73)
            throw new AssertionError(
                "legacy round trip failed "+
                loaded
            );

        if(!LocalAccountProfiles
                .accountFile("src")
                .equals(
                    src.toAbsolutePath()
                        .normalize()
                ))
            throw new AssertionError(
                "src path"
            );
    }

    private static void verifyGeneralProfiles(
        Path dir
    )throws Exception{
        if(!LocalAccountProfiles
                .isPersistent("Alice")||
           LocalAccountProfiles
                .isPersistent("   "))
            throw new AssertionError(
                "general persistence eligibility"
            );

        Path root=
            dir.resolve("profiles")
                .toAbsolutePath()
                .normalize();

        Path alice=
            LocalAccountProfiles
                .accountFile("Alice");
        Path aliceAgain=
            LocalAccountProfiles
                .accountFile(" alice ");
        Path bob=
            LocalAccountProfiles
                .accountFile("Bob");
        Path hostile=
            LocalAccountProfiles
                .accountFile(
                    "../alice/../../escape"
                );

        if(!alice.equals(aliceAgain))
            throw new AssertionError(
                "normalized profile path changed "+
                alice+
                " vs "+
                aliceAgain
            );

        if(alice.equals(bob))
            throw new AssertionError(
                "distinct usernames collided"
            );

        for(Path path:
                Arrays.asList(
                    alice,
                    bob,
                    hostile
                )){
            if(!root.equals(path.getParent()))
                throw new AssertionError(
                    "profile escaped canonical root path="+
                    path+
                    " root="+root
                );

            String leaf=
                path.getFileName()
                    .toString();

            if(!leaf.matches(
                    "profile-[0-9a-f]{64}\\.properties"
                ))
                throw new AssertionError(
                    "unsafe profile filename "+
                    leaf
                );
        }

        BankState bank=
            new BankState();
        EquipmentState equipment=
            new EquipmentState();
        MovementState movement=
            new MovementState();
        PetState pet=
            new PetState();
        PlayerState player=
            new PlayerState();

        equipment.setWeapon(4151);
        movement.setPersistentRun(true);
        movement.setRunEnergy(41);

        String saved=
            LocalAccountProfiles.save(
                "Alice",
                bank,
                equipment,
                movement,
                pet,
                player
            );

        if(!Files.isRegularFile(alice))
            throw new AssertionError(
                "general profile not saved "+
                saved
            );

        Properties props=
            new Properties();

        try(java.io.InputStream input=
                Files.newInputStream(alice)){
            props.load(input);
        }

        if(!"alice".equals(
                props.getProperty(
                    "username"
                )))
            throw new AssertionError(
                "general username property"
            );

        BankState loadedBank=
            new BankState();
        EquipmentState loadedEquipment=
            new EquipmentState();
        MovementState loadedMovement=
            new MovementState();
        PetState loadedPet=
            new PetState();
        PlayerState loadedPlayer=
            new PlayerState();

        String loaded=
            LocalAccountProfiles.load(
                "ALICE",
                loadedBank,
                loadedEquipment,
                loadedMovement,
                loadedPet,
                loadedPlayer
            );

        if(loadedEquipment.weapon()!=4151||
           !loadedMovement.persistentRun()||
           loadedMovement.runEnergy()!=41)
            throw new AssertionError(
                "general round trip failed "+
                loaded
            );
    }
}
