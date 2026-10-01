package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.SortedMap;

public final class CosmeticDedicatedSlotTest {
    public static void main(String[] args)throws Exception{
        baselineDedicatedSlot();
        publicationAtomicity();

        System.out.println(
            "V511_COSMETIC_DEDICATED_SLOT_PASS "+
            "cosmetic27454=true "+
            "ammo11212x420=true "+
            "coexist=true "+
            "persistence=true "+
            "cosmeticEquipPublicationAtomic=true "+
            "cosmeticReplaceSlotStable=true "+
            "cosmeticOpenBankMirrorAtomic=true "+
            "cosmeticUnequipAtomicCompatible=true "+
            "stackableNativeCosmeticApplicable=false"
        );
    }

    private static void baselineDedicatedSlot()
        throws Exception
    {
        BankState b=new BankState();
        EquipmentState e=new EquipmentState();
        PlayerState p=new PlayerState();

        e.setStack(
            EquipmentSlot.AMMO,
            11212,
            420
        );

        ByteArrayOutputStream out=
            new ByteArrayOutputStream();
        ServerPacketWriter w=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    new int[]{2,3,4,5}
                )
            );

        String add=
            b.spawnItem(
                27454,
                1,
                w
            );
        if(!add.startsWith("ITEM_SPAWN_OK"))
            throw new AssertionError(add);

        int slot=find(b,27454);
        if(slot<0)
            throw new AssertionError(
                "collection icon slot"
            );

        String eq=
            b.equipCosmeticFromInventory(
                slot,
                27454,
                p.cosmetic(),
                w
            );

        if(!eq.startsWith("COSMETIC_EQUIP_OK"))
            throw new AssertionError(eq);

        p.syncEquipmentPresentation(e);

        if(p.nativeIconItemId()!=27454||
           e.itemAt(EquipmentSlot.AMMO)!=11212||
           e.quantityAt(EquipmentSlot.AMMO)!=420)
            throw new AssertionError(
                "dedicated state"
            );

        SortedMap<String,String> props=
            PersistenceSchemaTestSupport
                .capturePlayer(p);
        PlayerState p2=
            new PlayerState();

        PersistenceSchemaTestSupport
            .restorePlayer(
                p2,
                props
            );

        if(p2.nativeIconItemId()!=27454)
            throw new AssertionError(
                "persist cosmetic"
            );
    }

    private static void publicationAtomicity()
        throws Exception
    {
        assertCurrentNativeIconsNonStackable();
        closedEquipAndReplacement();
        openBankEquip();
    }

    private static void closedEquipAndReplacement()
        throws Exception
    {
        BankState bank=
            new BankState();
        CosmeticState cosmetic=
            new CosmeticState();
        ServerPacketWriter good=
            writer(11);

        require(
            bank.spawnItem(
                27454,
                1,
                good
            ).startsWith("ITEM_SPAWN_OK"),
            "initial cosmetic spawn"
        );

        int firstSlot=
            find(
                bank,
                27454
            );
        require(
            firstSlot>=0,
            "initial cosmetic slot"
        );

        boolean firstFailed=false;
        try{
            bank.equipCosmeticFromInventory(
                firstSlot,
                27454,
                cosmetic,
                failingWriter(21)
            );
        }catch(java.io.IOException expected){
            firstFailed=true;
        }

        require(
            firstFailed,
            "initial cosmetic publication did not fail"
        );
        require(
            !cosmetic.active()&&
            bank.inventoryCount(27454)==1,
            "failed initial cosmetic equip mutated canonical state"
        );

        String firstRetry=
            bank.equipCosmeticFromInventory(
                firstSlot,
                27454,
                cosmetic,
                good
            );

        require(
            firstRetry.startsWith(
                "COSMETIC_EQUIP_OK"
            )&&
            cosmetic.itemId()==27454&&
            bank.inventoryCount(27454)==0,
            "initial cosmetic retry result="+
            firstRetry
        );

        require(
            bank.spawnItem(
                24187,
                1,
                good
            ).startsWith("ITEM_SPAWN_OK"),
            "replacement cosmetic spawn"
        );

        int replacementSlot=
            find(
                bank,
                24187
            );

        require(
            replacementSlot>=0,
            "replacement cosmetic slot"
        );

        boolean replacementFailed=false;
        try{
            bank.equipCosmeticFromInventory(
                replacementSlot,
                24187,
                cosmetic,
                failingWriter(31)
            );
        }catch(java.io.IOException expected){
            replacementFailed=true;
        }

        require(
            replacementFailed,
            "replacement publication did not fail"
        );
        require(
            cosmetic.itemId()==27454&&
            bank.inventoryCount(24187)==1&&
            bank.inventoryCount(27454)==0,
            "failed replacement mutated cosmetic/inventory preimage"
        );

        String replacementRetry=
            bank.equipCosmeticFromInventory(
                replacementSlot,
                24187,
                cosmetic,
                good
            );

        require(
            replacementRetry.startsWith(
                "COSMETIC_EQUIP_OK"
            ),
            "replacement retry result="+
            replacementRetry
        );
        require(
            cosmetic.itemId()==24187&&
            bank.inventoryCount(24187)==0&&
            bank.inventoryCount(27454)==1&&
            bank.inventoryAt(replacementSlot)!=null&&
            bank.inventoryAt(replacementSlot).itemId==27454,
            "replacement retry did not return old cosmetic to consumed slot"
        );

        int beforeUnequipOld=
            bank.inventoryCount(24187);
        int beforeUnequipReturned=
            bank.inventoryCount(27454);

        boolean unequipFailed=false;
        try{
            bank.unequipCosmeticToInventory(
                cosmetic,
                failingWriter(41)
            );
        }catch(java.io.IOException expected){
            unequipFailed=true;
        }

        require(
            unequipFailed,
            "cosmetic unequip publication did not fail"
        );
        require(
            cosmetic.itemId()==24187&&
            bank.inventoryCount(24187)==beforeUnequipOld&&
            bank.inventoryCount(27454)==beforeUnequipReturned,
            "failed cosmetic unequip retired cosmetic or mutated inventory"
        );

        String unequipRetry=
            bank.unequipCosmeticToInventory(
                cosmetic,
                good
            );

        require(
            unequipRetry.startsWith(
                "COSMETIC_UNEQUIP_OK"
            )&&
            !cosmetic.active()&&
            bank.inventoryCount(24187)==beforeUnequipOld+1&&
            bank.inventoryCount(27454)==beforeUnequipReturned,
            "cosmetic unequip retry result="+
            unequipRetry
        );
    }

    private static void openBankEquip()
        throws Exception
    {
        BankState bank=
            new BankState();
        CosmeticState cosmetic=
            new CosmeticState();
        ServerPacketWriter good=
            writer(51);

        require(
            bank.spawnItem(
                27454,
                1,
                good
            ).startsWith("ITEM_SPAWN_OK"),
            "open-Bank cosmetic spawn"
        );

        int slot=find(bank,27454);
        require(
            slot>=0,
            "open-Bank cosmetic slot"
        );

        bank.open(good);

        boolean failed=false;
        try{
            bank.equipCosmeticFromInventory(
                slot,
                27454,
                cosmetic,
                failingWriter(61)
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        require(
            failed,
            "open-Bank cosmetic publication did not fail"
        );
        require(
            !cosmetic.active()&&
            bank.inventoryCount(27454)==1,
            "open-Bank failed cosmetic equip mutated canonical state"
        );

        String retry=
            bank.equipCosmeticFromInventory(
                slot,
                27454,
                cosmetic,
                good
            );

        require(
            retry.startsWith("COSMETIC_EQUIP_OK")&&
            cosmetic.itemId()==27454&&
            bank.inventoryCount(27454)==0,
            "open-Bank cosmetic retry result="+retry
        );
    }

    private static void assertCurrentNativeIconsNonStackable(){
        int[] icons={
            10556,10557,10558,10559,
            24184,24185,24187,27454,
            24239,27393,23631,26125,
            27560,27427
        };

        for(int id:icons)
            require(
                ItemCatalog.isNativePlayerIcon(id)&&
                !BankState.isStackable(id),
                "native cosmetic stackability changed id="+id+
                " name="+ItemCatalog.name(id)
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
        BankState bank,
        int itemId
    ){
        for(int slot=0;
            slot<bank.inventoryCapacity();
            slot++){
            BankState.Stack stack=
                bank.inventoryAt(slot);

            if(stack!=null&&
               stack.itemId==itemId)
                return slot;
        }

        return -1;
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private CosmeticDedicatedSlotTest(){}
}
