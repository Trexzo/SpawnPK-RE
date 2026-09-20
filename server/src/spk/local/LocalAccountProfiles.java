package spk.local;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * v5.12.3 two-profile localhost persistence.
 *
 * opensrc remains byte-for-byte compatible with the historical AccountStore.
 * src uses an adjacent src.properties file with the same property schema and
 * atomic-save discipline. No credentials are stored here; this is gameplay state.
 *
 * Legacy profile I/O stays here as a compatibility adapter. Schema-v1 gameplay
 * encoding is owned by PlayerSnapshotSchemaV1.
 */
final class LocalAccountProfiles {
    static final String PRIMARY="opensrc";
    static final String SECONDARY="src";
    private static final int FORMAT_VERSION=PlayerSnapshot.CURRENT_VERSION;

    private LocalAccountProfiles(){}

    static boolean isPersistent(String username){
        return PRIMARY.equalsIgnoreCase(clean(username)) || SECONDARY.equalsIgnoreCase(clean(username));
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
        if(PRIMARY.equalsIgnoreCase(u)) return AccountStore.accountFile();
        if(!SECONDARY.equalsIgnoreCase(u)) throw new IllegalArgumentException("not a persistent local profile: "+username);
        Path primary=AccountStore.accountFile();
        Path parent=primary.getParent();
        if(parent==null) throw new IllegalStateException("primary account path has no parent: "+primary);
        return parent.resolve(SECONDARY+".properties").toAbsolutePath().normalize();
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

    private static String summary(String verb,Path file,String u,BankState bank,EquipmentState equipment,MovementState movement,PetState pet,PlayerState player){
        String petText=pet==null?"":(" pet="+(pet.active()?(pet.itemId()+"->"+pet.npcId()):"none"));
        String playerText=player==null?"":(" hp="+player.currentLevel(3)+" prayer="+player.currentLevel(5)+" comp="+player.compSelectorSummary());
        return verb+" file="+file+" profile="+u+" equipment="+equipment.occupiedSlots()+" inventory="+bank.inventorySlots()+" bank="+bank.bankSlots()+
            " runEnabled="+movement.persistentRun()+" runEnergy="+movement.runEnergy()+petText+playerText;
    }

    private static String clean(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT);}
}