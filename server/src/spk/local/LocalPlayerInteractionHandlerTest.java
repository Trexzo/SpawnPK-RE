package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalPlayerInteractionHandlerTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        ServerPacketWriter w1=null;
        ServerPacketWriter w2=null;

        try{
            WorldPlayer p1=new WorldPlayer();
            WorldPlayer p2=new WorldPlayer();

            world.registerPlayer(p1,"alpha");
            world.registerPlayer(p2,"beta");

            // Put beta one cardinal tile east of alpha.
            String move=p2.movement().accept(
                new MovementRequest(
                    164,
                    false,
                    new int[]{MovementState.INITIAL_X+1},
                    new int[]{MovementState.INITIAL_Y},
                    new byte[0]
                )
            );
            if(!move.startsWith("ACCEPTED"))
                throw new AssertionError("target move setup="+move);
            p2.movement().advance();

            ByteArrayOutputStream wire1=new ByteArrayOutputStream();
            ByteArrayOutputStream wire2=new ByteArrayOutputStream();

            w1=new ServerPacketWriter(
                wire1,new IsaacCipher(new int[]{1,2,3,4}));
            w2=new ServerPacketWriter(
                wire2,new IsaacCipher(new int[]{5,6,7,8}));

            Player81WorldSync.Context sync1=
                Player81WorldSync.register(
                    w1,world,p1,new DevAuthorityWorkbench());
            Player81WorldSync.register(
                w2,world,p2,new DevAuthorityWorkbench());

            // Populate alpha's per-view remote-player mapping.
            Player81WorldSync.transformForTest(
                sync1,
                BootstrapPackets.player81Idle()
            );

            int targetIndex=sync1.clientIndexFor(p2);
            if(targetIndex<0)
                throw new AssertionError("remote target not visible");

            LocalPlayerInteractionHandler h=
                new LocalPlayerInteractionHandler(
                    world,p1,p1.movement(),p1.equipment());

            PlayerAction attack=new PlayerAction(1,targetIndex);
            String attackRequest=h.handleResolved(attack,p2,sync1);
            if(attackRequest==null||
               !attackRequest.contains("V5131_PLAYER_ATTACK_REQUEST")||
               !attackRequest.contains("damage=DEFERRED_SERVER_FORMULA_AUTHORITY"))
                throw new AssertionError("attack request="+attackRequest);

            if(h.activeAttack()==null||!h.activeAttack().equals(p2.id()))
                throw new AssertionError("attack target not owned by coordinator");

            Integer interaction=h.movementInteractionTarget(sync1);
            if(interaction==null||interaction.intValue()!=32768+targetIndex)
                throw new AssertionError("movement interaction target="+interaction);

            int before=wire1.size();
            String presentation=h.tickAttack(1L,w1,sync1);
            if(presentation==null||
               !presentation.contains("V5131_PLAYER_ATTACK_PRESENTATION")||
               !presentation.contains("damage=DEFERRED_FORMULA_AUTHORITY"))
                throw new AssertionError("attack presentation="+presentation);
            if(wire1.size()<=before)
                throw new AssertionError("attack presentation emitted no packet");

            PlayerAction follow=new PlayerAction(2,targetIndex);
            String followResult=h.handleResolved(follow,p2,sync1);
            if(followResult==null||
               !followResult.contains("V5131_PLAYER_FOLLOW_REQUEST"))
                throw new AssertionError("follow route="+followResult);
            if(h.activeFollow()==null||!h.activeFollow().equals(p2.id())||
               h.activeAttack()!=null)
                throw new AssertionError("follow state replacement");

            PlayerAction trade=new PlayerAction(3,targetIndex);
            String tradeResult=h.handleResolved(trade,p2,sync1);
            if(tradeResult==null||
               !tradeResult.contains("V5141_PLAYER_TRADE_DISPATCH")||
               !tradeResult.contains("reason=ALREADY_ADJACENT"))
                throw new AssertionError("trade dispatch="+tradeResult);
            if(h.activeTrade()!=null)
                throw new AssertionError("adjacent trade should dispatch immediately");

            h.handleResolved(attack,p2,sync1);
            LocalPlayerInteractionHandler.Cancellation cancelled=h.cancelActive();
            if(!cancelled.hadFacingInteraction||cancelled.hadTrade)
                throw new AssertionError("cancel flags");
            if(h.hasActive())
                throw new AssertionError("cancel did not clear active state");

            System.out.println(
                "LOCAL_PLAYER_INTERACTION_HANDLER_PASS attack=true follow=true trade=true presentation=true cancel=true damageAuthorityDeferred=true");
        }finally{
            if(w1!=null)Player81WorldSync.unregister(w1);
            if(w2!=null)Player81WorldSync.unregister(w2);
            for(WorldPlayer p:world.players().snapshot())
                world.unregisterPlayer(p);
            world.close();
        }
    }
}
