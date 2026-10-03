package spk.local;

import java.io.ByteArrayOutputStream;

public final class DoppelRemoveDyeTest {
    public static void main(String[] args)throws Exception{
        baselineRoundTrip();
        publicationAtomicity();

        System.out.println(
            "V57_DOPPELGANGER_DYE_ROUNDTRIP_PASS "+
            "remove28807_to3241_plus28824=true "+
            "reapply28824_on3241_to28807=true "+
            "slotStable=true "+
            "transformPublicationAtomic=true "+
            "splitPublicationAtomic=true "+
            "combinePublicationAtomic=true "+
            "openBankTransformMirrorAtomic=true "+
            "splitExtraStackableApplicable="+
            BankState.isStackable(28824)
        );
    }

    private static void baselineRoundTrip()
        throws Exception
    {
        BankState b=new BankState();
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();
        ServerPacketWriter w=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    new int[]{17,19,23,29}
                )
            );

        req(
            b.spawnItem(28807,1,w)
                .startsWith("ITEM_SPAWN_OK"),
            "spawn dyed"
        );

        int slot=find(b,28807);
        req(slot>=0,"slot");

        String r=
            b.splitInventoryOne(
                slot,
                28807,
                3241,
                28824,
                w
            );

        req(
            r.startsWith("INVENTORY_SPLIT_OK"),
            r
        );

        int dye=find(b,28824);
        req(dye>=0,"dye returned");
        req(
            b.inventoryAt(slot).itemId==3241,
            "base same slot"
        );

        r=
            b.combineInventoryOne(
                dye,
                28824,
                slot,
                3241,
                28807,
                w
            );

        req(
            r.startsWith("INVENTORY_COMBINE_OK"),
            r
        );
        req(
            b.inventoryAt(slot).itemId==28807,
            "redyed same regular slot"
        );
        req(
            find(b,28824)<0,
            "dye consumed"
        );
    }

    private static void publicationAtomicity()
        throws Exception
    {
        transformFailureRetry(false);
        splitAndCombineFailureRetry();
        transformFailureRetry(true);
    }

    private static void transformFailureRetry(
        boolean openBank
    )throws Exception{
        BankState bank=new BankState();
        ServerPacketWriter good=
            writer(
                openBank
                    ?31
                    :41
            );

        String spawned=
            bank.spawnItem(
                24016,
                1,
                good
            );
        req(
            spawned.startsWith("ITEM_SPAWN_OK"),
            "transform spawn "+spawned
        );

        int slot=find(bank,24016);
        req(slot>=0,"transform slot");

        if(openBank)
            bank.open(good);

        boolean failed=false;
        try{
            bank.transformInventoryOne(
                slot,
                24016,
                24017,
                failingWriter(
                    openBank
                        ?51
                        :61
                )
            );
        }catch(java.io.IOException expected){
            failed=true;
        }

        req(
            failed,
            "transform publication did not fail openBank="+
            openBank
        );
        req(
            bank.inventoryAt(slot)!=null&&
            bank.inventoryAt(slot).itemId==24016&&
            find(bank,24017)<0,
            "failed transform mutated canonical preimage openBank="+
            openBank
        );

        String retry=
            bank.transformInventoryOne(
                slot,
                24016,
                24017,
                good
            );

        req(
            retry.startsWith("INVENTORY_TRANSFORM_OK"),
            "transform retry "+retry
        );
        req(
            bank.inventoryAt(slot)!=null&&
            bank.inventoryAt(slot).itemId==24017&&
            find(bank,24016)<0,
            "transform retry did not commit exactly once openBank="+
            openBank
        );
    }

    private static void splitAndCombineFailureRetry()
        throws Exception
    {
        BankState bank=new BankState();
        ServerPacketWriter good=writer(71);

        req(
            !BankState.isStackable(28824),
            "Doppel dye unexpectedly stackable; add stack-merge split coverage"
        );

        String spawned=
            bank.spawnItem(
                28807,
                1,
                good
            );
        req(
            spawned.startsWith("ITEM_SPAWN_OK"),
            "split fixture spawn "+spawned
        );

        int dyedSlot=find(bank,28807);
        req(dyedSlot>=0,"dyed slot");

        boolean splitFailed=false;
        try{
            bank.splitInventoryOne(
                dyedSlot,
                28807,
                3241,
                28824,
                failingWriter(81)
            );
        }catch(java.io.IOException expected){
            splitFailed=true;
        }

        req(
            splitFailed,
            "split publication did not fail"
        );
        req(
            bank.inventoryAt(dyedSlot)!=null&&
            bank.inventoryAt(dyedSlot).itemId==28807&&
            find(bank,3241)<0&&
            find(bank,28824)<0,
            "failed split mutated canonical preimage"
        );

        String splitRetry=
            bank.splitInventoryOne(
                dyedSlot,
                28807,
                3241,
                28824,
                good
            );

        req(
            splitRetry.startsWith("INVENTORY_SPLIT_OK"),
            "split retry "+splitRetry
        );

        int regularSlot=find(bank,3241);
        int dyeSlot=find(bank,28824);
        req(
            regularSlot==dyedSlot&&
            dyeSlot>=0,
            "split retry placement"
        );

        boolean combineFailed=false;
        try{
            bank.combineInventoryOne(
                dyeSlot,
                28824,
                regularSlot,
                3241,
                28807,
                failingWriter(91)
            );
        }catch(java.io.IOException expected){
            combineFailed=true;
        }

        req(
            combineFailed,
            "combine publication did not fail"
        );
        req(
            bank.inventoryAt(regularSlot)!=null&&
            bank.inventoryAt(regularSlot).itemId==3241&&
            bank.inventoryAt(dyeSlot)!=null&&
            bank.inventoryAt(dyeSlot).itemId==28824&&
            find(bank,28807)<0,
            "failed combine mutated canonical preimage"
        );

        String combineRetry=
            bank.combineInventoryOne(
                dyeSlot,
                28824,
                regularSlot,
                3241,
                28807,
                good
            );

        req(
            combineRetry.startsWith("INVENTORY_COMBINE_OK"),
            "combine retry "+combineRetry
        );
        req(
            bank.inventoryAt(regularSlot)!=null&&
            bank.inventoryAt(regularSlot).itemId==28807&&
            find(bank,28824)<0&&
            find(bank,3241)<0,
            "combine retry did not commit exact postimage"
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
            BankState.Stack s=
                b.inventoryAt(i);
            if(s!=null&&s.itemId==id)
                return i;
        }
        return -1;
    }

    private static void req(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private DoppelRemoveDyeTest(){}
}
