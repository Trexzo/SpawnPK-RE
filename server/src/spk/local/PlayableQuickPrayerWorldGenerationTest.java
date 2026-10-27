package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * P5.15: both Quick Prayer packet-batch paths require the exact
 * in-process WorldPlayer generation for the entire publication/commit.
 *
 * Legacy isolated constructors are covered by P5.9/P5.10 smoke;
 * this proves the actual production World-bound constructor contract.
 * No cross-JVM named-account lease, client ACK or native UI proof.
 */
public final class PlayableQuickPrayerWorldGenerationTest {
    private static final int[] SEED={231,232,233,234};
    private static final int THICK_SKIN=5609;
    private static final int CURSE_PROTECT_ITEM=22503;

    private PlayableQuickPrayerWorldGenerationTest(){}

    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(
            "P515_QUICK_PRAYER_WORLD_GENERATION "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=new OutboundPacketQueue(4096);
        final ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(SEED.clone()));
        byte[] drain()throws IOException{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            queue.drainTo(bytes,1<<20);
            return bytes.toByteArray();
        }
    }

    private static ServerPacketWriter setupWriter(){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{12,13,14,15})
        );
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected args");
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long first=world.registerPlayer(player,"p515-quick-prayer");
        long current=first;
        try{
            QuickPrayerSelectionService selected=
                new QuickPrayerSelectionService(
                    player,"LOCAL_LAB_POLICY_P515_QUICK_SELECTION");
            selected.beginEdit();
            selected.toggleDraft("normal:thick_skin");
            selected.confirmEdit();

            LocalQuickPrayerActivationService oldOn=
                new LocalQuickPrayerActivationService(
                    world,player,()->first,selected);
            LocalQuickPrayerDeactivationService oldOff=
                new LocalQuickPrayerDeactivationService(
                    world,player,()->first);

            Capture liveOn=new Capture();
            require(oldOn.activate(liveOn.writer).startsWith(
                    "QUICK_PRAYERS_ON")&&
                player.prayers().activeWidget(THICK_SKIN)&&
                liveOn.drain().length>0,
                "live first-generation ON");

            Capture liveOff=new Capture();
            require(oldOff.deactivate(liveOff.writer).startsWith(
                    "QUICK_PRAYERS_OFF")&&
                !player.prayers().activeWidget(THICK_SKIN)&&
                liveOff.drain().length>0,
                "live first-generation OFF");

            // Keep one confirmed active prayer so stale OFF could
            // incorrectly clear it if the World generation is ignored.
            Capture reOn=new Capture();
            require(oldOn.activate(reOn.writer).startsWith(
                    "QUICK_PRAYERS_ON")&&
                player.prayers().activeWidget(THICK_SKIN)&&
                reOn.drain().length>0,"setup reactivation");
            require(world.unregisterPlayer(player,first),
                "failed to revoke first generation");
            Capture detachedOn=new Capture();
            Capture detachedOff=new Capture();
            require("REJECTED_STALE_WORLD_OWNER".equals(
                    oldOn.activate(detachedOn.writer))&&
                "REJECTED_STALE_WORLD_OWNER".equals(
                    oldOff.deactivate(detachedOff.writer))&&
                detachedOn.drain().length==0&&
                detachedOff.drain().length==0&&
                player.prayers().activeWidget(THICK_SKIN),
                "detached Quick ON/OFF admitted publication or mutation");

            // ABA: re-register the identical player instance. The old
            // generation must not be revived by object identity.
            long second=world.registerPlayer(
                player,"p515-quick-prayer");
            current=second;
            require(second!=first&&
                world.players().owns(player,second),
                "World registration generation did not advance");
            Capture staleOff=new Capture();
            Capture staleOn=new Capture();
            require("REJECTED_STALE_WORLD_OWNER".equals(
                    oldOff.deactivate(staleOff.writer))&&
                "REJECTED_STALE_WORLD_OWNER".equals(
                    oldOn.activate(staleOn.writer))&&
                staleOff.drain().length==0&&
                staleOn.drain().length==0&&
                player.prayers().activeWidget(THICK_SKIN),
                "old generation revived after re-register");

            LocalQuickPrayerActivationService currentOn=
                new LocalQuickPrayerActivationService(
                    world,player,()->second,selected);
            LocalQuickPrayerDeactivationService currentOff=
                new LocalQuickPrayerDeactivationService(
                    world,player,()->second);
            Capture newOff=new Capture();
            require(currentOff.deactivate(newOff.writer).startsWith(
                    "QUICK_PRAYERS_OFF")&&
                !player.prayers().activeWidget(THICK_SKIN)&&
                newOff.drain().length>0,
                "fresh generation cannot clear old prayer");
            Capture newOn=new Capture();
            require(currentOn.activate(newOn.writer).startsWith(
                    "QUICK_PRAYERS_ON")&&
                player.prayers().activeWidget(THICK_SKIN)&&
                newOn.drain().length>0,
                "fresh generation cannot activate prayer");

            // Direct Curses state uses the same OFF service when its book
            // is selected. Stale World ownership must preserve its state.
            ServerPacketWriter setup=setupWriter();
            player.prayers().switchBook("curses",setup);
            PrayerDefinitionRepository.Def curse=
                PrayerDefinitionRepository.byWidget(
                    CURSE_PROTECT_ITEM);
            require(player.prayers().click(
                    curse,player.playerState(),setup)
                    .contains("enabled=true")&&
                player.prayers().activeWidget(CURSE_PROTECT_ITEM),
                "Curses baseline");
            Capture cursesOff=new Capture();
            require(currentOff.deactivate(cursesOff.writer).startsWith(
                    "QUICK_PRAYERS_OFF")&&
                !player.prayers().activeWidget(CURSE_PROTECT_ITEM)&&
                player.prayers().activeWidget(THICK_SKIN)&&
                cursesOff.drain().length>0,
                "current-gen Curses OFF changed other book");

            player.prayers().click(curse,player.playerState(),setup);
            require(player.prayers().activeWidget(CURSE_PROTECT_ITEM),
                "Curses reactivated before close");
            require(world.unregisterPlayer(player,second),
                "failed to revoke second generation");
            world.close();
            Capture closedOn=new Capture();
            Capture closedOff=new Capture();
            require("REJECTED_STALE_WORLD_OWNER".equals(
                    currentOn.activate(closedOn.writer))&&
                "REJECTED_STALE_WORLD_OWNER".equals(
                    currentOff.deactivate(closedOff.writer))&&
                closedOn.drain().length==0&&
                closedOff.drain().length==0&&
                player.prayers().activeWidget(CURSE_PROTECT_ITEM)&&
                player.prayers().activeWidget(THICK_SKIN),
                "closed World admitted Quick ON/OFF or altered book state");

            System.out.println(
                "P515_QUICK_PRAYER_WORLD_GENERATION_PASS"+
                " currentOnAndOff=true"+
                " detachedBothDeniedZeroBytes=true"+
                " reRegisteredSamePlayerOldGenDenied=true"+
                " freshGenerationBothRoutesWork=true"+
                " CursesOffPreservesOtherBook=true"+
                " closedWorldBothDeniedZeroBytes=true"+
                " existingQueueAtomicityPreserved=true"+
                " nativeV308AndCrossJvmLeaseUnverified=true");
        }finally{
            if(!world.closed()){
                if(world.players().owns(player,current))
                    world.unregisterPlayer(player,current);
                world.close();
            }
        }
    }
}
