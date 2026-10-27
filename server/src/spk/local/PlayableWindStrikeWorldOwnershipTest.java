package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * P5.17: the already isolated Wind Strike RuneTransaction must
 * authorize the exact World and player generation from before
 * preflight through packet queue admission and semantic commit.
 *
 * This is NOT live combat. No target hit, XP or damage is attached.
 */
public final class PlayableWindStrikeWorldOwnershipTest {
    private static final int AIR=556;
    private static final int MIND=558;
    private static final int[] SEED={251,252,253,254};

    private PlayableWindStrikeWorldOwnershipTest(){}

    private static void require(boolean ok,String detail){
        if(!ok)throw new AssertionError(
            "P517_WIND_STRIKE_WORLD "+detail);
    }

    private static final class Capture {
        final OutboundPacketQueue queue;
        final ServerPacketWriter writer;
        Capture(int capacity){
            queue=new OutboundPacketQueue(capacity);
            writer=new ServerPacketWriter(
                queue,new IsaacCipher(SEED.clone()));
        }
        byte[] drain()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.toByteArray();
        }
    }

    private static void add(WorldPlayer player,int id,int qty)
        throws IOException{
        ServerPacketWriter writer=new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{23,24,25,26}));
        String result=player.bank().spawnItem(id,qty,writer);
        require(result!=null&&result.contains("OK"),
            "setup item refused id="+id+" result="+result);
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected args");

        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long first=world.registerPlayer(player,"p517-runes");
        long current=first;
        try{
            add(player,AIR,4);
            add(player,MIND,4);
            LocalLabWindStrikeRuneTransaction old=
                new LocalLabWindStrikeRuneTransaction(
                    world,player,()->first);

            Capture valid=new Capture(4096);
            String initial=old.settle(valid.writer);
            require(initial.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_RUNES_SETTLED")&&
                player.bank().inventoryCount(AIR)==3&&
                player.bank().inventoryCount(MIND)==3&&
                valid.drain().length>0,
                "live generation did not spend exact two runes");

            require(world.unregisterPlayer(player,first),
                "first generation unregister refused");

            Capture detached=new Capture(4096);
            require("REJECTED_STALE_WORLD_OWNER".equals(
                    old.settle(detached.writer))&&
                detached.drain().length==0&&
                player.bank().inventoryCount(AIR)==3&&
                player.bank().inventoryCount(MIND)==3,
                "detached session consumed runes or wrote packets");

            long second=world.registerPlayer(player,"p517-runes");
            current=second;
            require(second!=first&&
                world.players().owns(player,second),
                "same player ABA did not rotate generation");
            Capture oldGeneration=new Capture(4096);
            require("REJECTED_STALE_WORLD_OWNER".equals(
                    old.settle(oldGeneration.writer))&&
                oldGeneration.drain().length==0&&
                player.bank().inventoryCount(AIR)==3&&
                player.bank().inventoryCount(MIND)==3,
                "re-registered same player revived prior owner");

            LocalLabWindStrikeRuneTransaction fresh=
                new LocalLabWindStrikeRuneTransaction(
                    world,player,()->second);

            // Retain existing queue backpressure semantics when the
            // World owner is actually valid.
            Capture saturated=new Capture(1024);
            byte[] sentinel=new byte[1024];
            Arrays.fill(sentinel,(byte)0x5a);
            saturated.queue.offer(sentinel);
            require(saturated.queue.queuedBytes()==1024,
                "failed to fill queue");
            require("REJECTED_WRITER_BACKPRESSURE_RETRYABLE"
                    .equals(fresh.settle(saturated.writer))&&
                Arrays.equals(saturated.drain(),sentinel)&&
                player.bank().inventoryCount(AIR)==3&&
                player.bank().inventoryCount(MIND)==3,
                "current-gen full queue consumed runes");

            Capture allowed=new Capture(4096);
            require(fresh.settle(allowed.writer).startsWith(
                    "LOCAL_LAB_WIND_STRIKE_RUNES_SETTLED")&&
                allowed.drain().length>0&&
                player.bank().inventoryCount(AIR)==2&&
                player.bank().inventoryCount(MIND)==2,
                "fresh World generation unable to spend");

            // The same player object has no authority on an unrelated
            // World, regardless of its numeric generation value.
            World unrelated=World.isolatedForTest(60_000L);
            try{
                LocalLabWindStrikeRuneTransaction crossWorld=
                    new LocalLabWindStrikeRuneTransaction(
                        unrelated,player,()->second);
                Capture wrongWorld=new Capture(4096);
                require("REJECTED_STALE_WORLD_OWNER".equals(
                        crossWorld.settle(wrongWorld.writer))&&
                    wrongWorld.drain().length==0&&
                    player.bank().inventoryCount(AIR)==2&&
                    player.bank().inventoryCount(MIND)==2,
                    "unregistered unrelated World admitted spend");
            }finally{
                unrelated.close();
            }

            // An old generation cannot spend after a closed World.
            require(world.unregisterPlayer(player,second),
                "second generation unregister refused");
            world.close();
            Capture closed=new Capture(4096);
            require("REJECTED_STALE_WORLD_OWNER".equals(
                    fresh.settle(closed.writer))&&
                closed.drain().length==0&&
                player.bank().inventoryCount(AIR)==2&&
                player.bank().inventoryCount(MIND)==2,
                "closed World consumed runes");

            System.out.println(
                "P517_WIND_STRIKE_WORLD_OWNER_PASS"+
                " firstGenerationSpend=true"+
                " detachedDeniedZeroBytes=true"+
                " samePlayerABAOldGenerationDenied=true"+
                " freshGenerationCanSpend=true"+
                " queueBackpressureStillAtomic=true"+
                " unrelatedWorldDenied=true"+
                " closedWorldDeniedZeroBytes=true"+
                " productionCombatStillDisconnected=true"+
                " noNativeClientGuiOrCrossJvmLeaseProof=true");
        }finally{
            if(!world.closed()){
                if(world.players().owns(player,current))
                    world.unregisterPlayer(player,current);
                world.close();
            }
        }
    }
}
