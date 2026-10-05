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
        String u=clean(username);
        if(PRIMARY.equalsIgnoreCase(u)) return AccountStore.load(bank,equipment,movement,pet,player);

        Path file=accountFile(u);
        if(!Files.isRegularFile(file)) return "ACCOUNT_DEFAULTS_NO_FILE file="+file+" profile="+u;

        Properties properties=new Properties();
        try(InputStream in=Files.newInputStream(file)){properties.load(in);}

        PlayerSnapshot snapshot=
            PlayerSnapshot.fromLegacyProperties(
                u,
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

        return summary("ACCOUNT_LOADED",file,u,bank,equipment,movement,pet,player);
    }

    static String save(String username,BankState bank,EquipmentState equipment,MovementState movement,PetState pet,PlayerState player)throws IOException{
        String u=clean(username);
        if(PRIMARY.equalsIgnoreCase(u)) return AccountStore.save(bank,equipment,movement,pet,player);

        Path file=accountFile(u);
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

        Path tmp=file.resolveSibling(file.getFileName().toString()+".tmp");
        boolean completed=false;
        try{
            try(OutputStream out=Files.newOutputStream(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){
                properties.store(out,"SpawnPK LocalLab localhost account state");
            }
            try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            completed=true;
        }finally{
            if(!completed)Files.deleteIfExists(tmp);
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

    private static String clean(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT);}
}
