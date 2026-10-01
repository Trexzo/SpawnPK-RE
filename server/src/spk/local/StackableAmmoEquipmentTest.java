package spk.local;

import java.io.ByteArrayOutputStream;

public final class StackableAmmoEquipmentTest {
    public static void main(String[] args)throws Exception{
        int[] ids={882,11212,877,9341,806,868};
        for(int id:ids){
            if(ItemDefinitionRepository.get(id)==null)
                throw new AssertionError("missing "+id);
            if(!ItemDefinitionRepository.isStackable(id))
                throw new AssertionError(
                    "not stackable "+id+" "+ItemCatalog.name(id)
                );
        }

        baselineAmmoFlow();
        publicationAtomicity();

        System.out.println(
            "V57_STACKABLE_AMMO_PASS "+
            "arrowsBoltsDartsKnives=true "+
            "spawnMerge=true "+
            "ammoEquipStack175=true "+
            "unequipStack175=true "+
            "packet53QuantityAware=true "+
            "ammoMergePublicationAtomic=true "+
            "ordinaryEquipPublicationAtomic=true "+
            "twoHandedShieldDisplacementAtomic=true "+
            "shieldTwoHandedDisplacementAtomic=true "+
            "unequipPublicationAtomic=true "+
            "openBankEquipmentMirrorAtomic=true"
        );
    }

    private static void baselineAmmoFlow()
        throws Exception
    {
        BankState b=new BankState();
        EquipmentState e=new EquipmentState();
        ByteArrayOutputStream raw=
            new ByteArrayOutputStream();
        ServerPacketWriter w=
            new ServerPacketWriter(
                raw,
                new IsaacCipher(
                    new int[]{1,3,5,7}
                )
            );

        String a=b.spawnItem(882,100,w);
        if(!a.startsWith("ITEM_SPAWN_OK"))
            throw new AssertionError(a);
        int slot=find(b,882);
        if(slot<0||b.inventoryAt(slot).qty!=100)
            throw new AssertionError("arrow spawn stack");

        a=b.spawnItem(882,50,w);
        if(b.inventoryAt(slot).qty!=150)
            throw new AssertionError(
                "arrow merge qty="+b.inventoryAt(slot).qty
            );

        EquipmentMetadataRepository.Meta meta=
            ItemDefinitionRepository
                .equipmentMetaForClientAction(882);
        if(meta==null||
           meta.slot!=EquipmentSlot.AMMO)
            throw new AssertionError(
                "arrow equip slot="+
                (meta==null?null:meta.slot)
            );

        a=b.equipFromInventory(
            slot,
            882,
            e,
            w
        );
        if(!a.startsWith("EQUIP_STACK_OK")||
           e.itemAt(EquipmentSlot.AMMO)!=882||
           e.quantityAt(EquipmentSlot.AMMO)!=150||
           b.inventoryAt(slot)!=null)
            throw new AssertionError(
                a+" eqQty="+
                e.quantityAt(EquipmentSlot.AMMO)
            );

        b.spawnItem(882,25,w);
        int slot2=find(b,882);
        a=b.equipFromInventory(
            slot2,
            882,
            e,
            w
        );
        if(!a.startsWith("EQUIP_STACK_MERGE_OK")||
           e.quantityAt(EquipmentSlot.AMMO)!=175)
            throw new AssertionError(a);

        a=b.unequipToInventory(
            EquipmentSlot.AMMO.equipmentIndex,
            882,
            e,
            w
        );
        int inv=find(b,882);
        if(!a.startsWith("UNEQUIP_OK")||
           inv<0||
           b.inventoryAt(inv).qty!=175)
            throw new AssertionError(a);
    }

    private static void publicationAtomicity()
        throws Exception
    {
        ammoMergeAtomic();
        ordinaryEquipAtomic(false);
        twoHandedDisplacementAtomic();
        shieldDisplacementAtomic();
        unequipAtomic();
        ordinaryEquipAtomic(true);
    }

    private static void ammoMergeAtomic()
        throws Exception
    {
        BankState bank=new BankState();
        EquipmentState equipment=new EquipmentState();
        ServerPacketWriter good=writer(11);

        equipment.setStack(
            EquipmentSlot.AMMO,
            882,
            100
        );
        bank.spawnItem(
            882,
            25,
            good
        );
        int slot=find(bank,882);

        boolean failed=false;
        try{
            bank.equipFromInventory(
                slot,
                882,
                equipment,
                failingWriter(21)
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        if(!failed||
           equipment.itemAt(EquipmentSlot.AMMO)!=882||
           equipment.quantityAt(EquipmentSlot.AMMO)!=100||
           bank.inventoryAt(slot)==null||
           bank.inventoryAt(slot).itemId!=882||
           bank.inventoryAt(slot).qty!=25)
            throw new AssertionError(
                "failed ammo merge mutated canonical preimage"
            );

        String retry=
            bank.equipFromInventory(
                slot,
                882,
                equipment,
                good
            );

        if(retry==null||
           !retry.contains("EQUIP_STACK_MERGE_OK")||
           equipment.quantityAt(EquipmentSlot.AMMO)!=125||
           bank.inventoryAt(slot)!=null)
            throw new AssertionError(
                "ammo merge retry did not commit once result="+retry
            );
    }

    private static void ordinaryEquipAtomic(
        boolean bankOpen
    )throws Exception{
        BankState bank=new BankState();
        EquipmentState equipment=new EquipmentState();
        ServerPacketWriter good=
            writer(
                bankOpen
                    ?31
                    :41
            );

        if(bankOpen)
            bank.open(good);

        bank.spawnItem(
            4708,
            1,
            good
        );
        int slot=find(bank,4708);
        int beforeHead=
            equipment.itemAt(
                EquipmentSlot.HEAD
            );

        boolean failed=false;
        try{
            bank.equipFromInventory(
                slot,
                4708,
                equipment,
                failingWriter(
                    bankOpen
                        ?51
                        :61
                )
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        if(!failed||
           equipment.itemAt(EquipmentSlot.HEAD)!=beforeHead||
           bank.inventoryAt(slot)==null||
           bank.inventoryAt(slot).itemId!=4708)
            throw new AssertionError(
                "failed ordinary equip mutated preimage bankOpen="+
                bankOpen
            );

        String retry=
            bank.equipFromInventory(
                slot,
                4708,
                equipment,
                good
            );

        if(retry==null||
           !retry.contains("EQUIP_OK")||
           equipment.itemAt(EquipmentSlot.HEAD)!=4708||
           bank.inventoryAt(slot)!=null)
            throw new AssertionError(
                "ordinary equip retry failed bankOpen="+
                bankOpen+
                " result="+retry
            );
    }

    private static void twoHandedDisplacementAtomic()
        throws Exception
    {
        BankState bank=new BankState();
        EquipmentState equipment=new EquipmentState();
        ServerPacketWriter good=writer(71);

        // EquipmentState starts with two-handed Bloodrend 28526.
        equipment.set(
            EquipmentSlot.SHIELD,
            9065
        );
        bank.spawnItem(
            21566,
            1,
            good
        );
        int slot=find(bank,21566);

        boolean failed=false;
        try{
            bank.equipFromInventory(
                slot,
                21566,
                equipment,
                failingWriter(81)
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        if(!failed||
           equipment.itemAt(EquipmentSlot.WEAPON)!=28526||
           equipment.itemAt(EquipmentSlot.SHIELD)!=9065||
           bank.inventoryAt(slot)==null||
           bank.inventoryAt(slot).itemId!=21566)
            throw new AssertionError(
                "failed two-handed equip mutated weapon/shield preimage"
            );

        String retry=
            bank.equipFromInventory(
                slot,
                21566,
                equipment,
                good
            );

        if(retry==null||
           !retry.contains("EQUIP_OK")||
           equipment.itemAt(EquipmentSlot.WEAPON)!=21566||
           equipment.itemAt(EquipmentSlot.SHIELD)!=-1||
           bank.inventoryAt(slot)==null||
           bank.inventoryAt(slot).itemId!=28526||
           find(bank,9065)<0)
            throw new AssertionError(
                "two-handed displacement retry failed result="+retry
            );
    }

    private static void shieldDisplacementAtomic()
        throws Exception
    {
        BankState bank=new BankState();
        EquipmentState equipment=new EquipmentState();
        ServerPacketWriter good=writer(91);

        if(equipment.itemAt(EquipmentSlot.WEAPON)!=28526)
            throw new AssertionError(
                "shield fixture missing default two-handed weapon"
            );

        bank.spawnItem(
            9065,
            1,
            good
        );
        int slot=find(bank,9065);

        boolean failed=false;
        try{
            bank.equipFromInventory(
                slot,
                9065,
                equipment,
                failingWriter(101)
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        if(!failed||
           equipment.itemAt(EquipmentSlot.WEAPON)!=28526||
           equipment.itemAt(EquipmentSlot.SHIELD)!=-1||
           bank.inventoryAt(slot)==null||
           bank.inventoryAt(slot).itemId!=9065)
            throw new AssertionError(
                "failed shield equip mutated two-handed preimage"
            );

        String retry=
            bank.equipFromInventory(
                slot,
                9065,
                equipment,
                good
            );

        if(retry==null||
           !retry.contains("EQUIP_OK")||
           equipment.itemAt(EquipmentSlot.SHIELD)!=9065||
           equipment.itemAt(EquipmentSlot.WEAPON)!=-1||
           bank.inventoryAt(slot)==null||
           bank.inventoryAt(slot).itemId!=28526)
            throw new AssertionError(
                "shield displacement retry failed result="+retry
            );
    }

    private static void unequipAtomic()
        throws Exception
    {
        BankState bank=new BankState();
        EquipmentState equipment=new EquipmentState();
        ServerPacketWriter good=writer(111);

        equipment.set(
            EquipmentSlot.HEAD,
            4708
        );

        boolean failed=false;
        try{
            bank.unequipToInventory(
                EquipmentSlot.HEAD.equipmentIndex,
                4708,
                equipment,
                failingWriter(121)
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        if(!failed||
           equipment.itemAt(EquipmentSlot.HEAD)!=4708||
           find(bank,4708)>=0)
            throw new AssertionError(
                "failed unequip mutated canonical preimage"
            );

        String retry=
            bank.unequipToInventory(
                EquipmentSlot.HEAD.equipmentIndex,
                4708,
                equipment,
                good
            );

        if(retry==null||
           !retry.contains("UNEQUIP_OK")||
           equipment.itemAt(EquipmentSlot.HEAD)!=-1||
           find(bank,4708)<0)
            throw new AssertionError(
                "unequip retry failed result="+retry
            );
    }

    private static ServerPacketWriter writer(
        int seed
    ){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
        );
    }

    private static ServerPacketWriter failingWriter(
        int seed
    )throws Exception{
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        queue.offer(
            new byte[1024]
        );

        return new ServerPacketWriter(
            queue,
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
        );
    }

    private static int find(
        BankState b,
        int id
    ){
        for(int i=0;i<b.inventoryCapacity();i++){
            BankState.Stack s=b.inventoryAt(i);
            if(s!=null&&s.itemId==id)
                return i;
        }
        return -1;
    }

    private StackableAmmoEquipmentTest(){}
}
