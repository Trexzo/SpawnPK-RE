package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * P5.12 atomic Wind Strike canonical rune cost, strictly isolated
 * from production MagicState and combat effects.
 */
public final class PlayableWindStrikeRuneTransactionTest {
    private static final int[] SEED={171,172,173,174};
    private static final int AIR=LocalLabWindStrikeRuneTransaction.AIR_RUNE;
    private static final int MIND=LocalLabWindStrikeRuneTransaction.MIND_RUNE;

    private PlayableWindStrikeRuneTransactionTest(){}

    private static void require(boolean ok,String what){
        if(!ok)throw new AssertionError(
            "P512_RUNE_TRANSACTION "+what);
    }

    private static final class Capture {
        final OutboundPacketQueue queue;
        final ServerPacketWriter writer;
        Capture(int size){
            queue=new OutboundPacketQueue(size);
            writer=new ServerPacketWriter(
                queue,new IsaacCipher(SEED.clone()));
        }
        byte[] drain()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.toByteArray();
        }
    }

    private static boolean exactInventory53(
        Capture capture,BankState bank
    )throws IOException{
        byte[] wire=capture.drain();
        int[] ids=new int[BankState.INVENTORY_CAPACITY];
        int[] qty=new int[BankState.INVENTORY_CAPACITY];
        for(int slot=0;slot<ids.length;slot++){
            BankState.Stack stack=bank.inventoryAt(slot);
            ids[slot]=stack==null?-1:stack.itemId;
            qty[slot]=stack==null?0:stack.qty;
        }
        byte[] expected=bank.previewRecipeInventoryPacket(ids,qty);
        if(wire.length!=expected.length+3)
            return false;
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        int opcode=((wire[0]&255)-cipher.nextInt())&255;
        int length=((wire[1]&255)<<8)|(wire[2]&255);
        return opcode==53&&length==expected.length&&
            Arrays.equals(
                Arrays.copyOfRange(wire,3,wire.length),expected);
    }

    private static void add(WorldPlayer player,int id,int qty)
        throws Exception{
        ByteArrayOutputStream sink=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            sink,new IsaacCipher(new int[]{12,13,14,15}));
        String result=player.bank().spawnItem(id,qty,writer);
        require(result!=null&&result.contains("OK"),
            "setup inventory add refused item="+id+
            " detail="+result);
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected args");
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,"p512-runecost");
        try{
            LocalLabWindStrikeRuneTransaction tx=
                new LocalLabWindStrikeRuneTransaction(player);
            Capture empty=new Capture(4096);
            String noAir=tx.settle(empty.writer);
            require("REJECTED_AIR_RUNE_OR_PROVIDER_MISSING".equals(noAir)&&
                empty.drain().length==0,
                "empty inventory created cast/runic bytes");

            add(player,AIR,2);
            Capture noMind=new Capture(4096);
            require("REJECTED_MIND_RUNE_MISSING".equals(
                    tx.settle(noMind.writer))&&
                noMind.drain().length==0&&
                player.bank().inventoryCount(AIR)==2,
                "missing mind rune spent air");

            add(player,MIND,2);
            String magicLast=player.magic().last();
            Capture a=new Capture(4096);
            String once=tx.settle(a.writer);
            require(once.startsWith("LOCAL_LAB_WIND_STRIKE_RUNES_SETTLED")&&
                once.contains("airSpent=1 mindSpent=1")&&
                player.bank().inventoryCount(AIR)==1&&
                player.bank().inventoryCount(MIND)==1&&
                exactInventory53(a,player.bank())&&
                player.magic().last().equals(magicLast),
                "first two-rune settlement or presentation missing");

            Capture b=new Capture(4096);
            require(tx.settle(b.writer).startsWith(
                    "LOCAL_LAB_WIND_STRIKE_RUNES_SETTLED")&&
                exactInventory53(b,player.bank())&&
                player.bank().inventoryCount(AIR)==0&&
                player.bank().inventoryCount(MIND)==0,
                "second settlement failed or created runes");
            Capture exhausted=new Capture(4096);
            require("REJECTED_AIR_RUNE_OR_PROVIDER_MISSING".equals(
                    tx.settle(exhausted.writer))&&
                exhausted.drain().length==0,
                "exhausted air cast accepted");

            // Only this explicitly reviewed provider is supported.
            player.equipment().setWeapon(
                LocalLabWindStrikeRuneTransaction.AIR_STAFF);
            add(player,MIND,3);
            Capture provider=new Capture(4096);
            String providerResult=tx.settle(provider.writer);
            require(providerResult.contains(
                    "usedAirStaff=true airSpent=0 mindSpent=1")&&
                exactInventory53(provider,player.bank())&&
                player.bank().inventoryCount(AIR)==0&&
                player.bank().inventoryCount(MIND)==2,
                "equipped air staff was consumed/ignored");

            // A full but valid queue must yield a retryable zero-byte
            // rejection and leave the inventory exactly unchanged.
            Capture filled=new Capture(1024);
            byte[] sentinel=new byte[1024];
            Arrays.fill(sentinel,(byte)0x5a);
            filled.queue.offer(sentinel);
            int beforeMind=player.bank().inventoryCount(MIND);
            require("REJECTED_WRITER_BACKPRESSURE_RETRYABLE".equals(
                    tx.settle(filled.writer))&&
                filled.queue.queuedBytes()==1024&&
                Arrays.equals(filled.drain(),sentinel)&&
                player.bank().inventoryCount(MIND)==beforeMind,
                "full queue created invisible rune consumption");

            Capture busy=new Capture(4096);
            busy.writer.beginBatch();
            require("REJECTED_WRITER_BACKPRESSURE_RETRYABLE".equals(
                    tx.settle(busy.writer))&&
                player.bank().inventoryCount(MIND)==beforeMind,
                "active packet batch admitted rune consumption");
            busy.writer.abortBatch();
            require(busy.drain().length==0,
                "busy writer leaked rune update");

            ByteArrayOutputStream directOut=
                new ByteArrayOutputStream();
            ServerPacketWriter direct=new ServerPacketWriter(
                directOut,new IsaacCipher(SEED.clone()));
            boolean refused=false;
            try{
                tx.settle(direct);
            }catch(IOException expected){
                refused=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "DIRECT_WRITER_NONATOMIC");
            }
            require(refused&&directOut.size()==0&&
                player.bank().inventoryCount(MIND)==beforeMind,
                "direct writer created unretractable spend");

            // A real retry after all three denials spends one rune.
            Capture retry=new Capture(4096);
            require(tx.settle(retry.writer).startsWith(
                    "LOCAL_LAB_WIND_STRIKE_RUNES_SETTLED")&&
                exactInventory53(retry,player.bank())&&
                player.bank().inventoryCount(MIND)==beforeMind-1,
                "retry did not settle one mind rune");

            PlayerLifecycleService life=
                new PlayerLifecycleService(player);
            require(life.applyDamage(500,20L,"P512_DEATH").died,
                "lethal fixture");
            int mindAtDeath=player.bank().inventoryCount(MIND);
            Capture dead=new Capture(4096);
            require("REJECTED_CASTER_DEAD".equals(
                    tx.settle(dead.writer))&&
                dead.drain().length==0&&
                player.bank().inventoryCount(MIND)==mindAtDeath,
                "dead player spent runes");
            require(life.tick(25L)==
                PlayerLifecycleService.TickResult.RESPAWNED,
                "expected tick25 respawn");

            Capture resumed=new Capture(4096);
            require(tx.settle(resumed.writer).startsWith(
                    "LOCAL_LAB_WIND_STRIKE_RUNES_SETTLED")&&
                exactInventory53(resumed,player.bank())&&
                player.bank().inventoryCount(MIND)==mindAtDeath-1,
                "post-respawn rune cost not restored");

            System.out.println(
                "P512_WIND_STRIKE_RUNE_TRANSACTION_PASS"+
                " canonicalRune556Mind558=true"+
                " twoCastsConsumeEachExactlyOnce=true"+
                " exhaustedOrMissingRejectedZeroBytes=true"+
                " equippedStaff1381InfiniteAir=true"+
                " queueBackpressureNoMutation=true"+
                " busyWriterNoMutation=true"+
                " directWriterDenied=true retryOnce=true"+
                " deadCannotSpend=true respawnRestoresSpend=true"+
                " exactInventoryPacket53PayloadValidated=true"+
                " productionSpellRoutingNotChanged=true"+
                " combinedRunesAndCombatEffectsUnknown=true");
        }finally{
            if(player.registered())
                world.unregisterPlayer(player,generation);
            if(!world.closed())world.close();
        }
    }
}
