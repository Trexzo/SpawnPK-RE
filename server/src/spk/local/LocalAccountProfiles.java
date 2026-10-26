package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Local player-profile persistence.
 *
 * The historical opensrc/src profiles retain their exact legacy filenames for
 * compatibility. Every other non-empty local username receives a deterministic
 * storage file beneath accounts/profiles. The real username remains inside the
 * PlayerSnapshot; the SHA-256 filename is only a path-safe storage identity.
 *
 * No credentials are stored here; this is gameplay state only.
 *
 * Legacy profile I/O stays here as a compatibility adapter. Schema-v1 gameplay
 * encoding is owned by PlayerSnapshotSchemaV1.
 */
final class LocalAccountProfiles {
    static final String PRIMARY="opensrc";
    static final String SECONDARY="src";
    private static final String GENERAL_PROFILE_DIR="profiles";
    private static final String GENERAL_PROFILE_PREFIX="profile-";
    private static final int FORMAT_VERSION=PlayerSnapshot.CURRENT_VERSION;

    private LocalAccountProfiles(){}

    static boolean isPersistent(String username){
        return !clean(username).isEmpty();
    }

    static String chooseForLogin(World world,String loginAlias){
        String alias=clean(loginAlias);
        if(alias.isEmpty() || "localtest".equalsIgnoreCase(alias) || PRIMARY.equalsIgnoreCase(alias)){
            if(world==null || world.players().byName(PRIMARY)==null) return PRIMARY;
            if(world.players().byName(SECONDARY)==null) return SECONDARY;
            throw new IllegalStateException("LOCAL_PROFILE_SLOTS_FULL profiles="+PRIMARY+","+SECONDARY);
        }
        if(SECONDARY.equalsIgnoreCase(alias)){
            if(world!=null &&
               world.players().byName(SECONDARY)!=null)
                throw new IllegalStateException(
                    "DUPLICATE_LOGIN username="+
                    SECONDARY
                );
            return SECONDARY;
        }
        return alias;
    }

    static Path accountFile(String username){
        String u=clean(username);
        if(u.isEmpty())
            throw new IllegalArgumentException(
                "empty local profile"
            );

        if(PRIMARY.equalsIgnoreCase(u))
            return AccountStore.accountFile();

        Path primary=AccountStore.accountFile();
        Path parent=primary.getParent();
        if(parent==null)
            throw new IllegalStateException(
                "primary account path has no parent: "+
                primary
            );

        Path accountRoot=
            parent.toAbsolutePath().normalize();

        if(SECONDARY.equalsIgnoreCase(u))
            return accountRoot
                .resolve(SECONDARY+".properties")
                .toAbsolutePath()
                .normalize();

        Path profilesRoot=
            accountRoot
                .resolve(GENERAL_PROFILE_DIR)
                .toAbsolutePath()
                .normalize();

        Path file=
            profilesRoot
                .resolve(
                    GENERAL_PROFILE_PREFIX+
                    storageKey(u)+
                    ".properties"
                )
                .toAbsolutePath()
                .normalize();

        if(!file.getParent().equals(profilesRoot))
            throw new IllegalStateException(
                "profile path escaped canonical root username="+
                u+
                " path="+file+
                " root="+profilesRoot
            );

        return file;
    }

    static String load(String username,BankState bank,EquipmentState equipment,MovementState movement,PetState pet,PlayerState player)throws IOException{
        return loadSelected(username,bank,equipment,movement,pet,player,account->{});
    }

    /** R64: package-only deterministic mid-read evidence cutpoint. */
    static String loadWithPostReadHookForTest(
        String username,BankState bank,EquipmentState equipment,
        MovementState movement,PetState pet,PlayerState player,
        FilePlayerRepository.BeforeWorldReplace afterRead
    )throws IOException{
        return loadSelected(username,bank,equipment,movement,pet,player,afterRead);
    }

    private static String loadSelected(
        String username,BankState bank,EquipmentState equipment,
        MovementState movement,PetState pet,PlayerState player,
        FilePlayerRepository.BeforeWorldReplace afterRead
    )throws IOException{
        Objects.requireNonNull(afterRead,"afterRead");
        String u=clean(username);
        if(PRIMARY.equalsIgnoreCase(u))
            return AccountStore.loadWithPostReadHookForTest(
                bank,equipment,movement,pet,player,afterRead);

        Path file=accountFile(u);
        LocalFinalSaveNegativeWitness.requireNoNegativeWitness(file);
        if(!Files.isRegularFile(file)) return "ACCOUNT_DEFAULTS_NO_FILE file="+file+" profile="+u;

        Properties properties=new Properties();
        try(InputStream in=Files.newInputStream(file)){properties.load(in);}

        PlayerSnapshot snapshot=
            PlayerSnapshot.fromLegacyProperties(
                u,
                properties
            );

        afterRead.run(u);
        LocalFinalSaveNegativeWitness.requireNoNegativeWitness(file);
        if(!file.equals(accountFile(u)))
            throw new IOException("R64_NAMED_ACCOUNT_PATH_DRIFT_DENIED");
        PlayerSnapshotSchemaV1.apply(
            snapshot.values(),
            bank,
            equipment,
            movement,
            pet,
            player
        );

        return summary("ACCOUNT_LOADED",file,u,bank,equipment,movement,pet,player);
    }

    static String save(String username,BankState bank,EquipmentState equipment,MovementState movement,PetState pet,PlayerState player)throws IOException{
        return saveGuarded(username,bank,equipment,movement,pet,player,account->{});
    }

    /** G21.245: package-only named-profile PRE-move test seam. */
    static String saveWithPreMoveHookForTest(
        String username,BankState bank,EquipmentState equipment,
        MovementState movement,PetState pet,PlayerState player,
        FilePlayerRepository.BeforeWorldReplace beforeMove
    )throws IOException{
        return saveGuarded(username,bank,equipment,movement,pet,player,beforeMove);
    }

    private static String saveGuarded(
        String username,BankState bank,EquipmentState equipment,
        MovementState movement,PetState pet,PlayerState player,
        FilePlayerRepository.BeforeWorldReplace beforeMove
    )throws IOException{
        Objects.requireNonNull(beforeMove,"beforeMove");
        String u=clean(username);
        if(PRIMARY.equalsIgnoreCase(u))
            return AccountStore.saveWithPreMoveHookForTest(
                bank,equipment,movement,pet,player,beforeMove);

        Path file=accountFile(u);
        // G21.210: participate in the SAME optional in-JVM RAW write
        // reservation as FilePlayerRepository.save for named profiles.
        // Acquire before creating the historical fixed .tmp leaf.
        // PRIMARY still delegates to AccountStore.save unchanged.
        try(LocalJvmAccountSessionLease.RawWriteReservation reservation=
                LocalJvmAccountSessionLease.reserveRawWrite(file,u)){
            return saveNamedProfileFile(
                file,u,bank,equipment,movement,pet,player,reservation,beforeMove);
        }
    }

    private static String saveNamedProfileFile(
        Path file,String u,BankState bank,EquipmentState equipment,
        MovementState movement,PetState pet,PlayerState player,
        LocalJvmAccountSessionLease.RawWriteReservation reservation,
        FilePlayerRepository.BeforeWorldReplace beforeMove
    )throws IOException{
        Files.createDirectories(file.getParent());

        PlayerSnapshot snapshot=
            new PlayerSnapshot(
                FORMAT_VERSION,
                u,
                PlayerSnapshotSchemaV1.capture(
                    bank,
                    equipment,
                    movement,
                    pet,
                    player,
                    0
                )
            );

        Properties properties=
            snapshot.toLegacyProperties();

        properties.setProperty(
            "saved.at",
            Instant.now().toString()
        );

        final boolean safeTemp=LocalOptInRawTemp.enabled();
        final LocalOptInRawTemp.AccountPreimage accountPreimage=
            LocalOptInRawTemp.forcePostimageEnabled()
                ?LocalOptInRawTemp.captureAccountPreimage(file):null;
        Path tmp=safeTemp?LocalOptInRawTemp.create(file):
            file.resolveSibling(file.getFileName().toString()+".tmp");
        final java.nio.file.attribute.BasicFileAttributes tempIdentity=
            safeTemp?LocalOptInRawTemp.observe(tmp):null;
        boolean completed=false;
        try{
            try(OutputStream out=safeTemp
                    ?Files.newOutputStream(
                        tmp,StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        LinkOption.NOFOLLOW_LINKS)
                    :Files.newOutputStream(
                        tmp,StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)){
                properties.store(out,"SpawnPK LocalLab localhost account state");
            }
            final byte[] sealedTemp=safeTemp
                ?LocalOptInRawTemp.seal(tmp,tempIdentity):null;
            beforeMove.run(u);
            if(safeTemp)
                LocalOptInRawTemp.requireSealed(
                    tmp,tempIdentity,sealedTemp);
            if(reservation!=null){
                reservation.requireOriginalAccountObjectBeforePublication();
                reservation.requireNoFinalNegativeWitnessBeforePublication();
            }
            LocalNegativeAccountHeadPrototype
                .requireNoHeadBeforeRawMove(file);
            // R47: pin the staged named-account inode for postmove
            // positive-receipt exclusion if the optional gate is on.
            final LocalJvmAccountSessionLease.RawWriteReservation
                .StagedRawAccountObject stagedRawObject=reservation==null
                    ?null:reservation.captureStagedRawAccountObject(tmp);
            if(LocalOptInRawTemp.forcePostimageEnabled()){
                LocalOptInRawTemp.forcePublish(
                    tmp,file,tempIdentity,sealedTemp,reservation,
                    u,accountPreimage);
            }else try{
                if(reservation!=null){
                    reservation.requireLockObjectContinuity();
                    reservation.requireNoFinalNegativeWitnessBeforePublication();
                }
                Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            }catch(AtomicMoveNotSupportedException e){
                if(reservation!=null){
                    reservation.requireLockObjectContinuity();
                    reservation.requireNoFinalNegativeWitnessBeforePublication();
                }
                Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
            }
            if(reservation!=null){
                reservation.requireNoFinalNegativeWitnessAfterPublication();
                reservation.requirePublishedRawAccountObjectStillCurrent(
                    stagedRawObject);
                reservation.requirePublishedRawAccountBytesStillCurrent(
                    stagedRawObject);
            }
            LocalNegativeAccountHeadPrototype
                .requireNoHeadAfterRawMove(file);
            completed=true;
        }finally{
            if(!completed&&
               (!safeTemp||LocalOptInRawTemp.stillOwns(tmp,tempIdentity)))
                Files.deleteIfExists(tmp);
        }

        return summary("ACCOUNT_SAVED",file,u,bank,equipment,movement,pet,player);
    }

    private static String storageKey(String username){
        final byte[] digest;

        try{
            digest=
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                        username.getBytes(
                            StandardCharsets.UTF_8
                        )
                    );
        }catch(java.security.NoSuchAlgorithmException impossible){
            throw new IllegalStateException(
                "SHA-256 unavailable",
                impossible
            );
        }

        StringBuilder hex=
            new StringBuilder(
                digest.length*2
            );

        for(byte value:digest)
            hex.append(
                String.format(
                    Locale.ROOT,
                    "%02x",
                    value&255
                )
            );

        return hex.toString();
    }

    private static String summary(String verb,Path file,String u,BankState bank,EquipmentState equipment,MovementState movement,PetState pet,PlayerState player){
        String petText=pet==null?"":(" pet="+(pet.active()?(pet.itemId()+"->"+pet.npcId()):"none"));
        String playerText=player==null?"":(" hp="+player.currentLevel(3)+" prayer="+player.currentLevel(5)+" comp="+player.compSelectorSummary());
        return verb+" file="+file+" profile="+u+" equipment="+equipment.occupiedSlots()+" inventory="+bank.inventorySlots()+" bank="+bank.bankSlots()+
            " runEnabled="+movement.persistentRun()+" runEnergy="+movement.runEnergy()+petText+playerText;
    }

    // Shared canonical identity for account routing, snapshots and worker admission.
    static String canonicalProfile(String username){
        return username==null?"":username.trim().toLowerCase(Locale.ROOT);
    }
    private static String clean(String s){return canonicalProfile(s);}
}
