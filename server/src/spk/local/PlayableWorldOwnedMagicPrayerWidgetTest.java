package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * P5.14: direct magic and ordinary prayer clicks must be bound to the
 * current registered WorldPlayer generation; Home Teleport tokens must
 * not survive session detachment.
 *
 * No implied cross-JVM named-account lease or native client evidence.
 */
public final class PlayableWorldOwnedMagicPrayerWidgetTest {
    private static final int[] SEED={198,199,200,201};

    private PlayableWorldOwnedMagicPrayerWidgetTest(){}

    private static void require(boolean value,String message){
        if(!value)throw new AssertionError(
            "P514_WORLD_WIDGET "+message);
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long initial=world.registerPlayer(player,"p514-owner");
        try{
            LocalGameplayWidgetHandler bound=
                new LocalGameplayWidgetHandler(
                    world,player,()->initial);
            ByteArrayOutputStream wire=new ByteArrayOutputStream();
            ServerPacketWriter writer=new ServerPacketWriter(
                wire,new IsaacCipher(SEED.clone()));

            String prayer=bound.handle(5609,writer);
            require(prayer.contains("enabled=true")&&
                player.prayers().activeWidget(5609)&&
                wire.size()>0,
                "registered owner cannot activate normal prayer");

            String home=bound.handle(1195,writer);
            require(home.contains(
                    "ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell=Home Teleport"),
                "registered owner cannot trigger home teleport");
            String acceptedLast=player.magic().last();
            int beforeDetached=wire.size();
            require(world.unregisterPlayer(player,initial),
                "failed to unregister first World generation");

            // Even an earlier accepted Home Teleport token cannot be
            // consumed from a detached session.
            require(!bound.consumeAcceptedHomeTeleport(),
                "teleport token survived World detachment");
            String stalePrayer=bound.handle(5609,writer);
            String staleHome=bound.handle(1195,writer);
            require(stalePrayer.contains("REJECTED_STALE_WORLD_OWNER")&&
                staleHome.contains("REJECTED_STALE_WORLD_OWNER")&&
                player.prayers().activeWidget(5609)&&
                player.magic().last().equals(acceptedLast)&&
                wire.size()==beforeDetached&&
                !bound.consumeAcceptedHomeTeleport(),
                "detached owner mutated prayer/magic/wire or token");

            // Re-register the identical WorldPlayer under a NEW generation.
            // Previous handler must not gain authority simply because
            // player object identity is unchanged (ABA safety).
            long second=world.registerPlayer(player,"p514-owner");
            require(second!=initial&&
                world.players().owns(player,second),
                "WorldPlayer generation failed to rotate");
            int beforeStale=wire.size();
            require(bound.handle(1195,writer)
                    .contains("REJECTED_STALE_WORLD_OWNER")&&
                bound.handle(5609,writer)
                    .contains("REJECTED_STALE_WORLD_OWNER")&&
                wire.size()==beforeStale&&
                player.prayers().activeWidget(5609)&&
                player.magic().last().equals(acceptedLast),
                "stale generation revived after re-register");

            LocalGameplayWidgetHandler current=
                new LocalGameplayWidgetHandler(
                    world,player,()->second);
            String prayerOff=current.handle(5609,writer);
            require(prayerOff.contains("enabled=false")&&
                !player.prayers().activeWidget(5609),
                "new generation unable to toggle prayer");
            String homeCurrent=current.handle(1195,writer);
            require(homeCurrent.contains(
                    "ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell=Home Teleport")&&
                current.consumeAcceptedHomeTeleport()&&
                !current.consumeAcceptedHomeTeleport(),
                "new generation home teleport not exactly once");

            // Combat-style and unrelated widgets keep their distinct
            // routing paths instead of becoming accidental prayer/magic.
            require(current.handle(999999,writer)==null,
                "unrelated widget swallowed");

            // A valid generation in an already CLOSED World may not
            // authorize a future prayer/direct spell action.
            world.unregisterPlayer(player,second);
            world.close();
            int bytesClosed=wire.size();
            require(current.handle(5609,writer)
                    .contains("REJECTED_STALE_WORLD_OWNER")&&
                current.handle(1195,writer)
                    .contains("REJECTED_STALE_WORLD_OWNER")&&
                wire.size()==bytesClosed,
                "closed World accepted prayer or teleport");

            System.out.println(
                "P514_WORLD_OWNED_MAGIC_PRAYER_WIDGET_PASS"+
                " ownerPrayerOnOff=true"+
                " stalePrayerAndTeleportRejected=true"+
                " detachedTeleportTokenDropped=true"+
                " identicalPlayerNewGenerationABARejected=true"+
                " freshGenerationCanActivate=true"+
                " closedWorldDeniedZeroBytes=true"+
                " noNativeClientOrCrossJvmLeaseClaim=true");
        }finally{
            if(!world.closed()){
                if(world.players().owns(player,initial))
                    world.unregisterPlayer(player,initial);
                world.close();
            }
        }
    }
}
