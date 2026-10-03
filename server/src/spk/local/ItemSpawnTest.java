package spk.local;

import java.io.*;

public final class ItemSpawnTest {
    public static void main(String[] args) throws Exception {
        BankState bank=new BankState();
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(wire,new IsaacCipher(new int[]{1,2,3,4}));

        String a=bank.spawnItem(28526,1,w);
        if(!a.startsWith("ITEM_SPAWN_OK"))throw new AssertionError(a);
        BankState.Stack s=bank.inventoryAt(0);
        if(s==null||s.itemId!=28526||s.qty!=1)throw new AssertionError("Bloodrend spawn="+s);

        String b=bank.spawnItem(28512,2,w); // current catalog item; unknown stackability defaults non-stack
        if(!b.startsWith("ITEM_SPAWN_OK"))throw new AssertionError(b);
        if(bank.inventoryAt(1)==null||bank.inventoryAt(2)==null)throw new AssertionError("generic nonstack slots");

        String c=bank.spawnItem(999999,1,w);
        if(!c.startsWith("REJECTED_UNKNOWN_ITEM"))throw new AssertionError(c);

        publicationAtomicityAndExactQuantity();

        System.out.println("V41_GENERIC_ITEM_SPAWN_PASS catalog="+ItemCatalog.count()
                         +" anyKnownId=true bloodrend28526=true unknownRejected=true"
                         +" publicationAtomic=true exactMovedQuantity=true"
                         +" maxBoundary=true partialNonStackCapacity=true"
                         +" openBankMirrorAtomic=true");
    }

    private static void publicationAtomicityAndExactQuantity()
        throws Exception
    {
        java.lang.reflect.Field inventoryField=
            BankState.class.getDeclaredField(
                "inventory"
            );
        inventoryField.setAccessible(true);

        // Exact saturation boundary: no representable quantity remains.
        BankState maxed=new BankState();
        BankState.Stack[] maxedSlots=
            (BankState.Stack[])inventoryField.get(
                maxed
            );
        maxedSlots[0]=
            new BankState.Stack(
                995,
                Integer.MAX_VALUE
            );
        ByteArrayOutputStream maxedWire=
            new ByteArrayOutputStream();
        ServerPacketWriter maxedWriter=
            writer(
                maxedWire,
                11
            );
        int maxedBefore=maxedWire.size();

        String maxedResult=
            maxed.spawnItem(
                995,
                100,
                maxedWriter
            );

        if(!maxedResult.contains("moved=0")||
           maxed.inventoryCount(995)!=Integer.MAX_VALUE||
           maxedWire.size()!=maxedBefore)
            throw new AssertionError(
                "MAX spawn exactness result="+maxedResult+
                " count="+maxed.inventoryCount(995)+
                " wireDelta="+(maxedWire.size()-maxedBefore)
            );

        // Near-MAX request commits only the representable delta.
        BankState nearMax=new BankState();
        BankState.Stack[] nearMaxSlots=
            (BankState.Stack[])inventoryField.get(
                nearMax
            );
        nearMaxSlots[0]=
            new BankState.Stack(
                995,
                Integer.MAX_VALUE-3
            );
        ServerPacketWriter nearMaxWriter=
            writer(
                new ByteArrayOutputStream(),
                21
            );

        String nearMaxResult=
            nearMax.spawnItem(
                995,
                10,
                nearMaxWriter
            );

        if(!nearMaxResult.contains("moved=3")||
           nearMax.inventoryCount(995)!=Integer.MAX_VALUE)
            throw new AssertionError(
                "near-MAX spawn exactness result="+
                nearMaxResult+
                " count="+nearMax.inventoryCount(995)
            );

        // Preserve existing non-stackable partial-capacity behavior.
        BankState partial=new BankState();
        BankState.Stack[] partialSlots=
            (BankState.Stack[])inventoryField.get(
                partial
            );

        for(int i=0;i<partialSlots.length-1;i++)
            partialSlots[i]=
                new BankState.Stack(
                    100000+i,
                    1
                );

        ServerPacketWriter partialWriter=
            writer(
                new ByteArrayOutputStream(),
                31
            );

        String partialResult=
            partial.spawnItem(
                28526,
                3,
                partialWriter
            );

        if(!partialResult.contains("moved=1")||
           partial.inventoryCount(28526)!=1||
           partial.inventorySlots()!=
                partial.inventoryCapacity())
            throw new AssertionError(
                "non-stack partial capacity result="+
                partialResult+
                " count="+partial.inventoryCount(28526)+
                " occupied="+partial.inventorySlots()
            );

        // Failed publication while Bank is closed preserves exact preimage.
        BankState closedFailure=
            new BankState();

        boolean closedFailed=false;
        try{
            closedFailure.spawnItem(
                995,
                10,
                failingWriter(41)
            );
        }catch(java.io.IOException expected){
            closedFailed=true;
        }

        if(!closedFailed||
           closedFailure.inventoryCount(995)!=0)
            throw new AssertionError(
                "closed spawn publication failure mutated inventory"
            );

        String closedRetry=
            closedFailure.spawnItem(
                995,
                10,
                writer(
                    new ByteArrayOutputStream(),
                    51
                )
            );

        if(!closedRetry.contains("moved=10")||
           closedFailure.inventoryCount(995)!=10)
            throw new AssertionError(
                "closed spawn retry result="+closedRetry
            );

        // Open Bank mirror belongs to the same batch/failure boundary.
        BankState openFailure=
            new BankState();
        ServerPacketWriter openGood=
            writer(
                new ByteArrayOutputStream(),
                61
            );
        openFailure.open(
            openGood
        );

        boolean openFailed=false;
        try{
            openFailure.spawnItem(
                995,
                7,
                failingWriter(71)
            );
        }catch(java.io.IOException expected){
            openFailed=true;
        }

        if(!openFailed||
           openFailure.inventoryCount(995)!=0)
            throw new AssertionError(
                "open-bank spawn publication failure mutated inventory"
            );

        String openRetry=
            openFailure.spawnItem(
                995,
                7,
                openGood
            );

        if(!openRetry.contains("moved=7")||
           openFailure.inventoryCount(995)!=7)
            throw new AssertionError(
                "open-bank spawn retry result="+openRetry
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
}
