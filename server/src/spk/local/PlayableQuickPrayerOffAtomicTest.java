package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * P5.9 source gameplay integration for the Live LocalSession Quick OFF
 * policy: all current-book config36 changes + OFF token must commit
 * together, with no semantic mutation on backpressure or dead player.
 */
public final class PlayableQuickPrayerOffAtomicTest {
    private static final int[] SEED={131,132,133,134};
    private static final int THICK_SKIN=5609;
    private static final int BURST_STRENGTH=5610;
    private static final int CURSE_PROTECT_ITEM=22503;
    private static final int HP=PlayerState.HITPOINTS;

    private PlayableQuickPrayerOffAtomicTest(){}

    private static void require(boolean b,String message){
        if(!b)throw new AssertionError(
            "P59_ATOMIC_QUICK_OFF "+message);
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
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            queue.drainTo(bytes,1<<20);
            return bytes.toByteArray();
        }
    }

    private static void assertOffFrames(
        byte[] data,int... expectedVarps
    )throws IOException{
        IsaacCipher decoder=new IsaacCipher(SEED.clone());
        int pos=0;
        for(int varp:expectedVarps){
            require(pos+4<=data.length,
                "missing config36 for "+varp);
            int op=((data[pos++]&255)-decoder.nextInt())&255;
            require(op==36,"expected config36 got="+op);
            byte[] expected=BootstrapPackets.config36(varp,0);
            require(Arrays.equals(
                Arrays.copyOfRange(data,pos,pos+3),
                expected),"config36 body varp="+varp);
            pos+=3;
        }
        require(pos+3<=data.length,"missing OFF token");
        int op=((data[pos++]&255)-decoder.nextInt())&255;
        require(op==126,"expected control126 got="+op);
        int length=((data[pos++]&255)<<8)|(data[pos++]&255);
        byte[] expected=BootstrapPackets.widgetText126(
            1,"QUICK_PRAYERS_OFF");
        require(length==expected.length&&
            pos+length==data.length&&
            Arrays.equals(
                Arrays.copyOfRange(data,pos,pos+length),
                expected),"OFF token body or unexpected extra bytes");
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,"p59-quick-off");
        try{
            LocalQuickPrayerDeactivationService service=
                new LocalQuickPrayerDeactivationService(player);
            ByteArrayOutputStream setupBytes=
                new ByteArrayOutputStream();
            ServerPacketWriter setup=new ServerPacketWriter(
                setupBytes,new IsaacCipher(new int[]{4,5,6,7}));

            PrayerDefinitionRepository.Def skin=
                PrayerDefinitionRepository.byWidget(THICK_SKIN);
            PrayerDefinitionRepository.Def strength=
                PrayerDefinitionRepository.byWidget(BURST_STRENGTH);
            PrayerDefinitionRepository.Def curse=
                PrayerDefinitionRepository.byWidget(CURSE_PROTECT_ITEM);

            player.prayers().click(skin,player.playerState(),setup);
            player.prayers().click(strength,player.playerState(),setup);
            player.prayers().switchBook("curses",setup);
            player.prayers().click(curse,player.playerState(),setup);
            player.prayers().switchBook("normal",setup);
            require(player.prayers().activeCount()==3,
                "cross-book setup");

            Capture normal=new Capture(4096);
            String result=service.deactivate(normal.writer);
            require(result.startsWith("QUICK_PRAYERS_OFF")&&
                !player.prayers().activeWidget(THICK_SKIN)&&
                !player.prayers().activeWidget(BURST_STRENGTH)&&
                player.prayers().activeWidget(CURSE_PROTECT_ITEM)&&
                player.prayers().activeCount()==1,
                "normal OFF changed non-current curse");
            assertOffFrames(normal.drain(),83,84);

            Capture duplicate=new Capture(4096);
            require(service.deactivate(duplicate.writer).startsWith(
                "QUICK_PRAYERS_OFF"),
                "repeated empty OFF rejection");
            assertOffFrames(duplicate.drain());

            player.prayers().switchBook("curses",setup);
            Capture curseOff=new Capture(4096);
            require(service.deactivate(curseOff.writer).startsWith(
                "QUICK_PRAYERS_OFF")&&
                player.prayers().activeCount()==0,
                "curses OFF not applied");
            assertOffFrames(curseOff.drain(),83);

            player.prayers().switchBook("normal",setup);
            player.prayers().click(skin,player.playerState(),setup);

            // Saturate the actual 1024-byte minimum queue with a sentinel.
            // A non-admitted batch must keep both queue bytes and state.
            Capture blocked=new Capture(1024);
            byte[] sentinel=new byte[1024];
            Arrays.fill(sentinel,(byte)0x5a);
            blocked.queue.offer(sentinel);
            String declined=service.deactivate(blocked.writer);
            require("REJECTED_WRITER_BACKPRESSURE_RETRYABLE"
                    .equals(declined)&&
                player.prayers().activeWidget(THICK_SKIN)&&
                blocked.queue.queuedBytes()==1024&&
                Arrays.equals(blocked.drain(),sentinel),
                "backpressure changed bytes or prayer state");

            Capture busy=new Capture(4096);
            busy.writer.beginBatch();
            String busyResult=service.deactivate(busy.writer);
            require("REJECTED_WRITER_BACKPRESSURE_RETRYABLE"
                    .equals(busyResult)&&
                player.prayers().activeWidget(THICK_SKIN),
                "writer-with-open-batch changed state");
            busy.writer.abortBatch();
            require(busy.drain().length==0,
                "busy-writer rollback emitted bytes");

            ByteArrayOutputStream directBytes=
                new ByteArrayOutputStream();
            ServerPacketWriter direct=new ServerPacketWriter(
                directBytes,new IsaacCipher(SEED.clone()));
            boolean refused=false;
            try{
                service.deactivate(direct);
            }catch(IOException expected){
                refused=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "DIRECT_WRITER_NONATOMIC");
            }
            require(refused&&directBytes.size()==0&&
                player.prayers().activeWidget(THICK_SKIN),
                "direct writer changed prayer or wrote bytes");

            Capture recovered=new Capture(4096);
            require(service.deactivate(recovered.writer)
                    .startsWith("QUICK_PRAYERS_OFF")&&
                !player.prayers().activeWidget(THICK_SKIN),
                "retry after failed admission did not clear prayer");
            assertOffFrames(recovered.drain(),83);

            // A player killed with a still-active prayer may not
            // alter it through the Quick OFF bypass while waiting.
            player.prayers().click(skin,player.playerState(),setup);
            PlayerLifecycleService lifecycle=
                new PlayerLifecycleService(player);
            PlayerLifecycleService.DamageResult fatal=
                lifecycle.applyDamage(500,20L,"P59_DEATH");
            require(fatal.died&&player.lifecycle().dead()&&
                player.playerState().currentLevel(HP)==0,
                "death fixture");
            Capture dead=new Capture(4096);
            require("REJECTED_PLAYER_DEAD".equals(
                    service.deactivate(dead.writer))&&
                dead.drain().length==0&&
                player.prayers().activeWidget(THICK_SKIN),
                "dead Quick OFF bypass");
            require(lifecycle.tick(24L)==
                PlayerLifecycleService.TickResult.NONE&&
                player.lifecycle().dead(),
                "respawn before tick25");
            Capture stillDead=new Capture(4096);
            require("REJECTED_PLAYER_DEAD".equals(
                    service.deactivate(stillDead.writer))&&
                stillDead.drain().length==0,
                "early OFF while dead");

            require(lifecycle.tick(25L)==
                PlayerLifecycleService.TickResult.RESPAWNED&&
                player.lifecycle().alive(),
                "expected tick25 respawn");
            Capture alive=new Capture(4096);
            require(service.deactivate(alive.writer).startsWith(
                "QUICK_PRAYERS_OFF")&&
                !player.prayers().activeWidget(THICK_SKIN),
                "post-respawn OFF");
            assertOffFrames(alive.drain(),83);

            // The ordinary skill setter clamps to HP>=1; use actual
            // damage to deliberately construct contradictory ALIVE/HP0.
            player.prayers().click(skin,player.playerState(),setup);
            require(player.playerState().applyHitpointsDamage(99)==99&&
                player.lifecycle().alive()&&
                player.playerState().currentLevel(HP)==0,
                "ALIVE/HP0 fixture");
            Capture zeroHp=new Capture(4096);
            require("REJECTED_PLAYER_DEAD".equals(
                    service.deactivate(zeroHp.writer))&&
                player.prayers().activeWidget(THICK_SKIN)&&
                zeroHp.drain().length==0,
                "ALIVE/HP0 Quick OFF bypass");

            System.out.println(
                "P59_QUICK_PRAYER_OFF_ATOMIC_PASS"+
                " currentBookOnly=true twoConfig36And126Atomic=true"+
                " otherBookPreserved=true repeatedOffTokenOnly=true"+
                " cursesOff=true fullQueueZeroBytes=true"+
                " busyWriterRetryable=true directWriterDenied=true"+
                " retryStateCommit=true deadOffDenied=true"+
                " preRespawnDenied=true postRespawnOff=true"+
                " zeroHpAliveDenied=true"+
                " sourceOnlyNoOriginalClientGuiOrSocketAck=true"+
                " prayerEffectsOrDeathAutoClearNotClaimed=true");
        }finally{
            if(player.registered())
                world.unregisterPlayer(player,generation);
            if(!world.closed())world.close();
        }
    }
}
