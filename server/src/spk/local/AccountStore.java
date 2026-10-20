package spk.local;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.Properties;

/**
 * Tiny localhost-only persistent account store.
 *
 * The LocalLab has one canonical development account (opensrc). The real client
 * may still submit the historical login alias "localtest"; both aliases resolve
 * to the same local account. No production credentials or remote data are used.
 *
 * Legacy file I/O remains here for compatibility tests. Schema-v1 gameplay
 * encoding is owned by PlayerSnapshotSchemaV1.
 */
final class AccountStore {
    static final String CANONICAL_USERNAME = "opensrc";
    static final int FORMAT_VERSION = PlayerSnapshot.CURRENT_VERSION;

    private AccountStore() {}

    static Path accountFile() {
        String override = System.getProperty("spk.local.accountFile", "").trim();
        if (!override.isEmpty()) return Paths.get(override).toAbsolutePath().normalize();
        Path rootData = Paths.get("server", "data");
        Path dir = Files.isDirectory(rootData) ? rootData.resolve("accounts") : Paths.get("data", "accounts");
        return dir.resolve(CANONICAL_USERNAME + ".properties").toAbsolutePath().normalize();
    }

    static String load(BankState bank, EquipmentState equipment, MovementState movement) throws IOException {
        return load(bank,equipment,movement,null);
    }

    static String load(BankState bank, EquipmentState equipment, MovementState movement, PetState pet) throws IOException {
        return load(bank,equipment,movement,pet,null);
    }

    static String load(BankState bank, EquipmentState equipment, MovementState movement, PetState pet, PlayerState player) throws IOException {
        Path file = accountFile();
        if (!Files.isRegularFile(file)) return "NEW_ACCOUNT_DEFAULTS file=" + file;

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) { properties.load(in); }

        PlayerSnapshot snapshot =
            PlayerSnapshot.fromLegacyProperties(
                CANONICAL_USERNAME,
                properties
            );

        PlayerSnapshotSchemaV1.apply(
            snapshot.values(),
            bank,
            equipment,
            movement,
            pet,
            player
        );

        return "ACCOUNT_LOADED file="+file+" equipment="+equipment.occupiedSlots()
             +" inventory="+bank.inventorySlots()+" bank="+bank.bankSlots()
             +" runEnabled="+movement.persistentRun()+" runEnergy="+movement.runEnergy()
             +(pet==null?"":" pet="+(pet.active()?(pet.itemId()+"->"+pet.npcId()):"none"))
             +(player==null?"":" hp="+player.currentLevel(PlayerState.HITPOINTS)+" prayer="+player.currentLevel(PlayerState.PRAYER)+" comp="+player.compSelectorSummary());
    }

    static String save(BankState bank, EquipmentState equipment, MovementState movement) throws IOException {
        return save(bank,equipment,movement,null);
    }

    static String save(BankState bank, EquipmentState equipment, MovementState movement, PetState pet) throws IOException {
        return save(bank,equipment,movement,pet,null);
    }

    static String save(BankState bank, EquipmentState equipment, MovementState movement, PetState pet, PlayerState player) throws IOException {
        return saveGuarded(bank,equipment,movement,pet,player,account->{});
    }

    /** G21.245 package-only deterministic PRE-move legacy writer test seam. */
    static String saveWithPreMoveHookForTest(
        BankState bank,EquipmentState equipment,MovementState movement,
        PetState pet,PlayerState player,
        FilePlayerRepository.BeforeWorldReplace beforeMove
    )throws IOException{
        return saveGuarded(bank,equipment,movement,pet,player,beforeMove);
    }

    private static String saveGuarded(
        BankState bank,EquipmentState equipment,MovementState movement,
        PetState pet,PlayerState player,
        FilePlayerRepository.BeforeWorldReplace beforeMove
    )throws IOException{
        java.util.Objects.requireNonNull(beforeMove,"beforeMove");
        Path file = accountFile();
        // G21.212: PRIMARY uses exactly the same optional cooperating
        // in-JVM RAW write reservation as named profiles/repository.
        // Capture a SINGLE resolved account path and claim the lease
        // BEFORE legacy fixed .tmp or parent-directory mutation.
        try(LocalJvmAccountSessionLease.RawWriteReservation reservation=
                LocalJvmAccountSessionLease.reserveRawWrite(
                    file,CANONICAL_USERNAME)){
            return saveSelectedFile(
                file,bank,equipment,movement,pet,player,reservation,beforeMove);
        }
    }

    private static String saveSelectedFile(
        Path file,BankState bank,EquipmentState equipment,
        MovementState movement,PetState pet,PlayerState player,
        LocalJvmAccountSessionLease.RawWriteReservation reservation,
        FilePlayerRepository.BeforeWorldReplace beforeMove
    )throws IOException{
        Files.createDirectories(file.getParent());

        PlayerSnapshot snapshot =
            new PlayerSnapshot(
                FORMAT_VERSION,
                CANONICAL_USERNAME,
                PlayerSnapshotSchemaV1.capture(
                    bank,
                    equipment,
                    movement,
                    pet,
                    player,
                    0
                )
            );

        Properties properties =
            snapshot.toLegacyProperties();

        properties.setProperty(
            "saved.at",
            Instant.now().toString()
        );

        final boolean safeTemp=LocalOptInRawTemp.enabled();
        final LocalOptInRawTemp.AccountPreimage accountPreimage=
            LocalOptInRawTemp.forcePostimageEnabled()
                ?LocalOptInRawTemp.captureAccountPreimage(file):null;
        Path tmp = safeTemp
            ?LocalOptInRawTemp.create(file)
            :file.resolveSibling(file.getFileName().toString()+".tmp");
        final java.nio.file.attribute.BasicFileAttributes tempIdentity=
            safeTemp?LocalOptInRawTemp.observe(tmp):null;
        boolean completed = false;
        try {
            try (OutputStream out = safeTemp
                    ?Files.newOutputStream(
                        tmp,StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        LinkOption.NOFOLLOW_LINKS)
                    :Files.newOutputStream(
                        tmp,StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
                properties.store(out, "SpawnPK LocalLab localhost account state");
            }
            final byte[] sealedTemp=safeTemp
                ?LocalOptInRawTemp.seal(tmp,tempIdentity):null;
            beforeMove.run(CANONICAL_USERNAME);
            if(safeTemp)
                LocalOptInRawTemp.requireSealed(
                    tmp,tempIdentity,sealedTemp);
            if(reservation!=null){
                reservation.requireOriginalAccountObjectBeforePublication();
                reservation.requireNoFinalNegativeWitnessBeforePublication();
            }
            // R47: retain the exact new inode chosen for publication.
            final LocalJvmAccountSessionLease.RawWriteReservation
                .StagedRawAccountObject stagedRawObject=reservation==null
                    ?null:reservation.captureStagedRawAccountObject(tmp);
            if(LocalOptInRawTemp.forcePostimageEnabled()){
                LocalOptInRawTemp.forcePublish(
                    tmp,file,tempIdentity,sealedTemp,reservation,
                    CANONICAL_USERNAME,accountPreimage);
            }else try {
                if(reservation!=null){
                    reservation.requireLockObjectContinuity();
                    reservation.requireNoFinalNegativeWitnessBeforePublication();
                }
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                if(reservation!=null){
                    reservation.requireLockObjectContinuity();
                    reservation.requireNoFinalNegativeWitnessBeforePublication();
                }
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            if(reservation!=null){
                reservation.requireNoFinalNegativeWitnessAfterPublication();
                reservation.requirePublishedRawAccountObjectStillCurrent(
                    stagedRawObject);
                reservation.requirePublishedRawAccountBytesStillCurrent(
                    stagedRawObject);
            }
            completed = true;
        } finally {
            if (!completed &&
                (!safeTemp||LocalOptInRawTemp.stillOwns(tmp,tempIdentity)))
                Files.deleteIfExists(tmp);
        }

        return "ACCOUNT_SAVED file="+file+" equipment="+equipment.occupiedSlots()
             +" inventory="+bank.inventorySlots()+" bank="+bank.bankSlots()
             +" runEnabled="+movement.persistentRun()+" runEnergy="+movement.runEnergy()
             +(pet==null?"":" pet="+(pet.active()?(pet.itemId()+"->"+pet.npcId()):"none"))
             +(player==null?"":" hp="+player.currentLevel(PlayerState.HITPOINTS)+" prayer="+player.currentLevel(PlayerState.PRAYER)+" comp="+player.compSelectorSummary());
    }

}