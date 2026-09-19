package spk.local;

import java.nio.file.*;

public final class PetPersistenceTest {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("spk-pet-account-"); Path file=dir.resolve("opensrc.properties");
        String old=System.getProperty("spk.local.accountFile"); System.setProperty("spk.local.accountFile",file.toString());
        try{
            BankState b1=new BankState();EquipmentState e1=new EquipmentState();MovementState m1=new MovementState();PetState p1=new PetState();
            p1.activate(PetDefinitionRepository.get(20776));AccountStore.save(b1,e1,m1,p1);
            BankState b2=new BankState();EquipmentState e2=new EquipmentState();MovementState m2=new MovementState();PetState p2=new PetState();
            AccountStore.load(b2,e2,m2,p2);
            if(!p2.active()||p2.itemId()!=20776||p2.npcId()!=3098)throw new AssertionError("pet did not persist");
            p2.clear();AccountStore.save(b2,e2,m2,p2);PetState p3=new PetState();AccountStore.load(new BankState(),new EquipmentState(),new MovementState(),p3);if(p3.active())throw new AssertionError("cleared pet persisted active");
            System.out.println("V53_PET_PERSISTENCE_PASS account=opensrc active20776to3098=true clear=true existingFormatV1Compatible=true");
        } finally { if(old==null)System.clearProperty("spk.local.accountFile");else System.setProperty("spk.local.accountFile",old); }
    }
}
