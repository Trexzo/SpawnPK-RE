package spk.local;

import java.io.*;

public final class CombatTargetValidityTest {
    public static void main(String[] args)throws Exception{
        testNpcValidityAndCancellation();
        testPlayerValidityReasons();
        testPlayerDeathCancelsActiveAttack();

        System.out.println(
            "COMBAT_TARGET_VALIDITY_PASS "+
            "pvmExplicit=true "+
            "pvpExplicit=true "+
            "deathCancellation=true "+
            "disconnectPlaneSelfReasons=true"
        );
    }

    private static void testNpcValidityAndCancellation()
        throws Exception{
        NpcEntity invalid=
            new NpcEntity(
                77,
                7605,
                3090,
                3490
            );

        CombatTargetValidator.Result acquireInvalid=
            CombatTargetValidator.acquireNpc(
                invalid
            );

        if(acquireInvalid.valid||
           acquireInvalid.reason!=
                CombatTargetValidator.Reason.NPC_NOT_COMBAT_TARGET)
            throw new AssertionError(
                "invalid PvM target accepted "+
                acquireInvalid
            );

        MovementState movement=
            new MovementState();
        NpcEntity dummy=
            new NpcEntity(
                77,
                CombatTargetRepository.PVM_DUMMY_DEF,
                movement.x()+1,
                movement.y()
            );

        CombatState state=new CombatState();
        CombatEngine engine=
            new CombatEngine(
                state,
                new DevAuthorityWorkbench(),
                CombatDamageRules.localLabFallback()
            );

        String requested=
            engine.request(
                dummy,
                movement,
                4151,
                System.currentTimeMillis()
            );

        if(!requested.startsWith("TARGET_"))
            throw new AssertionError(
                "valid PvM target rejected "+
                requested
            );

        NpcRegistry empty=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        String cancelled=
            engine.tick(
                movement,
                empty,
                new EquipmentState(),
                writer(),
                1L
            );

        if(cancelled==null||
           !cancelled.startsWith(
                "TARGET_CLEARED_NULL_TARGET"))
            throw new AssertionError(
                "missing PvM visibility cancellation "+
                cancelled
            );

        if(state.active())
            throw new AssertionError(
                "PvM combat state remained active after target disappeared"
            );

        CombatState mismatchState=
            new CombatState();
        CombatTargetRepository.Target target=
            CombatTargetRepository.forDefinition(
                CombatTargetRepository.PVM_DUMMY_DEF
            );
        mismatchState.target(
            dummy,
            target,
            System.currentTimeMillis(),
            false
        );

        NpcEntity changed=
            new NpcEntity(
                dummy.sceneIndex,
                CombatTargetRepository.PLAYER_DUMMY_DEF,
                dummy.x,
                dummy.y
            );

        CombatTargetValidator.Result changedResult=
            CombatTargetValidator.activeNpc(
                changed,
                mismatchState
            );

        if(changedResult.valid||
           changedResult.reason!=
                CombatTargetValidator.Reason.NPC_DEFINITION_CHANGED)
            throw new AssertionError(
                "definition replacement was not rejected "+
                changedResult
            );
    }

    private static void testPlayerValidityReasons(){
        World world=World.isolatedForTest(600L);

        WorldPlayer owner=new WorldPlayer();
        WorldPlayer target=new WorldPlayer();

        world.registerPlayer(owner,"owner");
        world.registerPlayer(target,"target");

        try{
            CombatTargetValidator.Result valid=
                CombatTargetValidator.player(
                    owner,
                    target,
                    null
                );

            if(!valid.valid)
                throw new AssertionError(
                    "registered living player rejected "+
                    valid
                );

            CombatTargetValidator.Result self=
                CombatTargetValidator.player(
                    owner,
                    owner,
                    null
                );

            if(self.valid||
               self.reason!=
                    CombatTargetValidator.Reason.SELF_TARGET)
                throw new AssertionError(
                    "self target reason wrong "+
                    self
                );

            target.lifecycle().markDead(
                10L,
                5L,
                "TEST"
            );

            CombatTargetValidator.Result dead=
                CombatTargetValidator.player(
                    owner,
                    target,
                    null
                );

            if(dead.valid||
               dead.reason!=
                    CombatTargetValidator.Reason.TARGET_DEAD)
                throw new AssertionError(
                    "dead player target accepted "+
                    dead
                );

            target.lifecycle().markRespawned();

            java.util.Properties moved=
                new java.util.Properties();
            moved.setProperty(
                "movement.worldX",
                Integer.toString(
                    target.movement().x()
                )
            );
            moved.setProperty(
                "movement.worldY",
                Integer.toString(
                    target.movement().y()
                )
            );
            moved.setProperty(
                "movement.plane",
                "1"
            );
            target.movement().loadAccountProperties(
                moved
            );

            CombatTargetValidator.Result plane=
                CombatTargetValidator.player(
                    owner,
                    target,
                    null
                );

            if(plane.valid||
               plane.reason!=
                    CombatTargetValidator.Reason.PLANE_MISMATCH)
                throw new AssertionError(
                    "plane mismatch accepted "+
                    plane
                );

            world.unregisterPlayer(target);

            CombatTargetValidator.Result gone=
                CombatTargetValidator.player(
                    owner,
                    target,
                    null
                );

            if(gone.valid||
               gone.reason!=
                    CombatTargetValidator.Reason.TARGET_UNREGISTERED)
                throw new AssertionError(
                    "unregistered target accepted "+
                    gone
                );
        }finally{
            world.unregisterPlayer(owner);
            world.unregisterPlayer(target);
            world.close();
        }
    }

    private static void testPlayerDeathCancelsActiveAttack()
        throws Exception{
        World world=World.isolatedForTest(600L);

        WorldPlayer owner=new WorldPlayer();
        WorldPlayer target=new WorldPlayer();

        world.registerPlayer(owner,"owner");
        world.registerPlayer(target,"target");

        ServerPacketWriter ownerWriter=writer();
        ServerPacketWriter targetWriter=writer();

        Player81WorldSync.Context ownerSync=
            Player81WorldSync.register(
                ownerWriter,
                world,
                owner,
                new DevAuthorityWorkbench()
            );

        Player81WorldSync.Context targetSync=
            Player81WorldSync.register(
                targetWriter,
                world,
                target,
                new DevAuthorityWorkbench()
            );

        try{
            Player81WorldSync.transformForTest(
                ownerSync,
                BootstrapPackets.player81Idle()
            );
            Player81WorldSync.transformForTest(
                targetSync,
                BootstrapPackets.player81Idle()
            );

            if(ownerSync.clientIndexFor(target)<0)
                throw new AssertionError(
                    "target not visible to owner fixture"
                );

            LocalPlayerInteractionHandler interactions=
                new LocalPlayerInteractionHandler(
                    world,
                    owner,
                    owner.movement(),
                    owner.equipment()
                );

            String requested=
                interactions.handleResolved(
                    new PlayerAction(
                        128,
                        ownerSync.clientIndexFor(target),
                        1
                    ),
                    target,
                    ownerSync
                );

            if(requested==null||
               !requested.contains(
                    "PLAYER_ATTACK_REQUEST"))
                throw new AssertionError(
                    "active PvP attack not acquired "+
                    requested
                );

            if(interactions.activeAttack()==null)
                throw new AssertionError(
                    "active PvP target missing"
                );

            target.lifecycle().markDead(
                20L,
                5L,
                "TEST_LETHAL"
            );

            String cancelled=
                interactions.prepareTick(
                    21L,
                    ownerSync
                );

            if(cancelled==null||
               !cancelled.contains(
                    "PLAYER_ATTACK_CANCELLED")||
               !cancelled.contains(
                    "TARGET_DEAD"))
                throw new AssertionError(
                    "dead PvP target did not cancel "+
                    cancelled
                );

            if(interactions.activeAttack()!=null)
                throw new AssertionError(
                    "active PvP target survived death cancellation"
                );
        }finally{
            Player81WorldSync.unregister(
                ownerWriter
            );
            Player81WorldSync.unregister(
                targetWriter
            );
            world.unregisterPlayer(owner);
            world.unregisterPlayer(target);
            world.close();
        }
    }

    private static ServerPacketWriter writer(){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(
                new int[]{1,2,3,4}
            )
        );
    }
}
