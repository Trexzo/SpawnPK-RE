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
    static final int FORMAT_VERSION = 1;

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

        int version = parseInt(properties.getProperty("format.version"), -1);
        if (version != FORMAT_VERSION)
            throw new IOException("unsupported account format version="+version+" file="+file);

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
        Path file = accountFile();
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

        Path tmp = file.resolveSibling(file.getFileName().toString()+".tmp");
        try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            properties.store(out, "SpawnPK LocalLab localhost account state");
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }

        return "ACCOUNT_SAVED file="+file+" equipment="+equipment.occupiedSlots()
             +" inventory="+bank.inventorySlots()+" bank="+bank.bankSlots()
             +" runEnabled="+movement.persistentRun()+" runEnergy="+movement.runEnergy()
             +(pet==null?"":" pet="+(pet.active()?(pet.itemId()+"->"+pet.npcId()):"none"))
             +(player==null?"":" hp="+player.currentLevel(PlayerState.HITPOINTS)+" prayer="+player.currentLevel(PlayerState.PRAYER)+" comp="+player.compSelectorSummary());
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }
}
