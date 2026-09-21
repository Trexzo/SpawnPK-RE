package spk.local;

import java.io.*;
import java.nio.file.*;

/** Offline proof of opensrc account persistence without involving the network. */
public final class AccountPersistenceTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("spk-account-test-");
        Path file=dir.resolve("opensrc.properties");
        System.setProperty("spk.local.accountFile",file.toString());
        try {
            BankState bank=new BankState();
            EquipmentState equipment=new EquipmentState();
            MovementState movement=new MovementState();
            movement.setPersistentRun(true);
            movement.setRunEnergy(73);
            equipment.set(EquipmentSlot.HEAD,27034);
            equipment.set(EquipmentSlot.FEET,28701);

            ByteArrayOutputStream wire=new ByteArrayOutputStream();
            ServerPacketWriter w=new ServerPacketWriter(wire,new IsaacCipher(new int[]{1,2,3,4}));
            String spawn=bank.spawnItem(24023,1,w);
            if(!spawn.startsWith("ITEM_SPAWN_OK"))throw new AssertionError(spawn);

            PlayerState player=new PlayerState();
            if(!player.setCompSelectors(new int[]{1,2,3,4,5,6})) throw new AssertionError("comp selector setup");
            if(!player.setCharacterAppearance(
                    CharacterDesignProfile.FEMALE,
                    new int[]{45,-1,56,61,67,70,79},
                    new int[]{11,15,14,5,23}))
                throw new AssertionError("character design setup");
            String saved=AccountStore.save(bank,equipment,movement,new PetState(),player);
            if(!Files.isRegularFile(file))throw new AssertionError("account file missing");

            BankState bank2=new BankState();
            EquipmentState equipment2=new EquipmentState();
            MovementState movement2=new MovementState();
            PlayerState player2=new PlayerState();
            String loaded=AccountStore.load(bank2,equipment2,movement2,new PetState(),player2);
            if(equipment2.itemAt(EquipmentSlot.HEAD)!=27034)throw new AssertionError("head not persisted");
            if(equipment2.itemAt(EquipmentSlot.FEET)!=28701)throw new AssertionError("feet not persisted");
            if(equipment2.itemAt(EquipmentSlot.WEAPON)!=28526)throw new AssertionError("weapon not persisted");
            if(bank2.inventoryAt(0)==null || bank2.inventoryAt(0).itemId!=24023)throw new AssertionError("inventory 24023 not persisted");
            if(!movement2.persistentRun() || movement2.runEnergy()!=73)throw new AssertionError("run state not persisted");
            if(bank2.bankSlots()!=bank.bankSlots())throw new AssertionError("bank slots not persisted");
            if(player2.currentLevel(PlayerState.HITPOINTS)!=99 || player2.currentLevel(PlayerState.PRAYER)!=99) throw new AssertionError("player vitals not persisted");
            if(!java.util.Arrays.equals(player2.compSelectors(),new int[]{1,2,3,4,5,6})) throw new AssertionError("comp selectors not persisted");
            if(player2.characterGender()!=CharacterDesignProfile.FEMALE) throw new AssertionError("character gender not persisted");
            if(!java.util.Arrays.equals(player2.characterKits(),new int[]{45,-1,56,61,67,70,79})) throw new AssertionError("character kits not persisted");
            if(!java.util.Arrays.equals(player2.characterColours(),new int[]{11,15,14,5,23})) throw new AssertionError("character colours not persisted");
            System.out.println("V54_ACCOUNT_PERSISTENCE_PASS username=opensrc equipment=true inventory=true bank=true runToggle=true runEnergy=73 hp99=true prayer99=true compSelectors=true characterDesign=true fileAtomic=true saved="+saved+" loaded="+loaded);
        } finally {
            System.clearProperty("spk.local.accountFile");
            try { Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(IOException ignored){}}); } catch(IOException ignored){}
        }
    }
}
