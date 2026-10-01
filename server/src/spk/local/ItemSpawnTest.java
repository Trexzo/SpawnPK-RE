package spk.local;

import java.io.ByteArrayOutputStream;

public final class ItemSpawnTest {
    public static void main(String[] args) throws Exception {
        baselineSpawn();
        quantityExactness();
        publicationAtomicity();

        System.out.println(
            "V41_GENERIC_ITEM_SPAWN_PASS "+
            "catalog="+ItemCatalog.count()+" "+
            "anyKnownId=true "+
            "bloodrend28526=true "+
            "unknownRejected=true "+
            "stackableExactMoved=true "+
            "maxQuantityRejected=true "+
            "nonStackablePartialCapacity=true "+
            "spawnPublicationAtomic=true "+
            "spawnOpenBankMirrorAtomic=true"
        );
    }

    private static void baselineSpawn()
        throws Exception
    {
        BankState bank=new BankState();
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter w=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        String a=
            bank.spawnItem(
                28526,
                1,
                w
            );
        if(!a.startsWith("ITEM_SPAWN_OK"))
            throw new AssertionError(a);

        BankState.Stack s=
            bank.inventoryAt(0);
        if(s==null||
           s.itemId!=28526||
           s.qty!=1)
            throw new AssertionError(
                "Bloodrend spawn="+s
            );

        String b=
            bank.spawnItem(
                28512,
                2,
                w
            );
        if(!b.startsWith("ITEM_SPAWN_OK"))
            throw new AssertionError(b);

        if(bank.inventoryAt(1)==null||
           bank.inventoryAt(2)==null)
            throw new AssertionError(
                "generic nonstack slots"
            );

        String c=
            bank.spawnItem(
                999999,
                1,
                w
            );
        if(!c.startsWith("REJECTED_UNKNOWN_ITEM"))
            throw new AssertionError(c);
    }

    private static void quantityExactness()
        throws Exception
    {
        BankState nearMax=
            new BankState();
        ByteArrayOutputStream nearMaxWire=
            new ByteArrayOutputStream();
        ServerPacketWriter nearMaxWriter=
            writer(
                nearMaxWire,
                11
            );

        String seed=
            nearMax.spawnItem(
                995,
                1,
                nearMaxWriter
            );
        require(
            seed.startsWith("ITEM_SPAWN_OK"),
            "near-MAX seed"
        );

        int coinSlot=
            find(
                nearMax,
                995
            );
        require(
            coinSlot>=0,
            "near-MAX coin slot"
        );

        nearMax.inventoryAt(coinSlot).qty=
            Integer.MAX_VALUE-2;

        String partial=
            nearMax.spawnItem(
                995,
                5,
                nearMaxWriter
            );

        require(
            partial.startsWith("ITEM_SPAWN_OK"),
            "near-MAX partial result="+partial
        );
        require(
            partial.contains("requested=5")&&
            partial.contains("moved=2"),
            "near-MAX moved diagnostic="+partial
        );
        require(
            nearMax.inventoryAt(coinSlot).qty==
                Integer.MAX_VALUE,
            "near-MAX quantity="+
            nearMax.inventoryAt(coinSlot).qty
        );

        int wireBeforeMax=
            nearMaxWire.size();

        String rejected=
            nearMax.spawnItem(
                995,
                1,
                nearMaxWriter
            );

        require(
            rejected.startsWith(
                "REJECTED_INVENTORY_QTY_LIMIT"
            ),
            "MAX destination result="+rejected
        );
        require(
            nearMax.inventoryAt(coinSlot).qty==
                Integer.MAX_VALUE,
            "MAX rejection mutated quantity"
        );
        require(
            nearMaxWire.size()==wireBeforeMax,
            "MAX rejection emitted packet"
        );

        BankState nonStack=
            new BankState();
        ServerPacketWriter nonStackWriter=
            writer(
                new ByteArrayOutputStream(),
                21
            );

        String fill=
            nonStack.spawnItem(
                28512,
                26,
                nonStackWriter
            );
        require(
            fill.startsWith("ITEM_SPAWN_OK")&&
            fill.contains("moved=26"),
            "nonstack fill="+fill
        );

        String partialSlots=
            nonStack.spawnItem(
                28526,
                5,
                nonStackWriter
            );

        require(
            partialSlots.startsWith("ITEM_SPAWN_OK"),
            "nonstack partial result="+partialSlots
        );
        require(
            partialSlots.contains("requested=5")&&
            partialSlots.contains("moved=2"),
            "nonstack partial diagnostic="+partialSlots
        );
        require(
            nonStack.inventoryCount(28526)==2&&
            nonStack.inventorySlots()==28,
            "nonstack partial postimage count="+
            nonStack.inventoryCount(28526)+
            " slots="+nonStack.inventorySlots()
        );
    }

    private static void publicationAtomicity()
        throws Exception
    {
        BankState closed=
            new BankState();
        ServerPacketWriter closedGood=
            writer(
                new ByteArrayOutputStream(),
                31
            );

        boolean closedFailed=false;
        try{
            closed.spawnItem(
                995,
                7,
                failingWriter(41)
            );
        }catch(java.io.IOException expected){
            closedFailed=true;
        }

        require(
            closedFailed,
            "closed spawn publication did not fail"
        );
        require(
            closed.inventoryCount(995)==0,
            "closed failed spawn mutated canonical inventory"
        );

        String closedRetry=
            closed.spawnItem(
                995,
                7,
                closedGood
            );

        require(
            closedRetry.startsWith("ITEM_SPAWN_OK")&&
            closedRetry.contains("moved=7")&&
            closed.inventoryCount(995)==7,
            "closed retry="+closedRetry+
            " count="+closed.inventoryCount(995)
        );

        BankState open=
            new BankState();
        ServerPacketWriter openGood=
            writer(
                new ByteArrayOutputStream(),
                51
            );
        open.open(openGood);

        boolean openFailed=false;
        try{
            open.spawnItem(
                995,
                9,
                failingWriter(61)
            );
        }catch(java.io.IOException expected){
            openFailed=true;
        }

        require(
            openFailed,
            "open-Bank spawn publication did not fail"
        );
        require(
            open.inventoryCount(995)==0,
            "open-Bank failed spawn mutated canonical inventory"
        );

        String openRetry=
            open.spawnItem(
                995,
                9,
                openGood
            );

        require(
            openRetry.startsWith("ITEM_SPAWN_OK")&&
            openRetry.contains("moved=9")&&
            open.inventoryCount(995)==9,
            "open-Bank retry="+openRetry+
            " count="+open.inventoryCount(995)
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire,
        int seed
    ){
        return new ServerPacketWriter(
            wire,
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

    private ItemSpawnTest(){}
}
