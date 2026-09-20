package spk.local;

import java.io.*;

public final class CombatPresentationAdapterTest {
    public static void main(String[] args)throws Exception{
        CombatPresentationAdapter adapter=
            new CombatPresentationAdapter();

        MovementState movement=
            new MovementState();

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        NpcEntity target=
            npcs.spawnMirroredNpc(
                CombatTargetRepository.PVM_DUMMY_DEF,
                movement.x()+1,
                movement.y(),
                null,
                movement,
                writer
            );

        int beforeActor=out.size();

        CombatPresentationAdapter.ActorPublication actor=
            adapter.publishActor(
                15552,
                -1,
                target,
                writer
            );

        if(actor.actorGfxPublished)
            throw new AssertionError(
                "actor gfx unexpectedly published"
            );

        if(out.size()<=beforeActor)
            throw new AssertionError(
                "actor attack packet not published"
            );

        int beforeActorGfx=out.size();

        CombatPresentationAdapter.ActorPublication actorGfx=
            adapter.publishActor(
                15552,
                4080,
                target,
                writer
            );

        if(!actorGfx.actorGfxPublished)
            throw new AssertionError(
                "actor gfx publication flag false"
            );

        if(out.size()<=beforeActorGfx)
            throw new AssertionError(
                "actor animation+gfx packet not published"
            );

        int beforeTargetGfx=out.size();

        adapter.publishTargetGfx(
            target,
            4080,
            npcs,
            writer
        );

        if(out.size()<=beforeTargetGfx)
            throw new AssertionError(
                "target gfx mask not published"
            );

        CombatHitScheduler.ScheduledHit hit=
            new CombatHitScheduler.ScheduledHit(
                target.sceneIndex,
                target.definitionId,
                10,
                1,
                255,
                255,
                -1,
                "CUSTOM_LOCALLAB",
                "TEST",
                7L
            );

        int beforeHit=out.size();

        CombatPresentationAdapter.HitPublication hitPublication=
            adapter.publishHit(
                hit,
                npcs,
                writer
            );

        if(!hitPublication.published||
           hitPublication.damage!=10||
           !hitPublication.log.startsWith(
                "M2_HIT_PUBLISHED"))
            throw new AssertionError(
                "hit publication mismatch "+
                hitPublication.log
            );

        if(out.size()<=beforeHit)
            throw new AssertionError(
                "hit mask not published"
            );

        int beforeInvalid=out.size();

        CombatPresentationAdapter.HitPublication invalid=
            adapter.publishHit(
                new CombatHitScheduler.ScheduledHit(
                    target.sceneIndex,
                    CombatTargetRepository.PLAYER_DUMMY_DEF,
                    10,
                    1,
                    255,
                    255,
                    -1,
                    "CUSTOM_LOCALLAB",
                    "TEST",
                    8L
                ),
                npcs,
                writer
            );

        if(invalid.published||
           !invalid.log.startsWith(
                "M2_HIT_CANCELLED_TARGET_CHANGED"))
            throw new AssertionError(
                "changed target hit not cancelled "+
                invalid.log
            );

        if(out.size()!=beforeInvalid)
            throw new AssertionError(
                "cancelled hit wrote packet bytes"
            );

        V913WeaponRuntimeAuthority.Profile soundRuntime=
            V913WeaponRuntimeAuthority.resolve(
                25001
            );

        if(soundRuntime==null||
           !soundRuntime.hasBasicSound())
            throw new AssertionError(
                "sound-bearing runtime fixture missing"
            );

        int beforeSound=out.size();

        CombatPresentationAdapter.RuntimePublication runtime=
            adapter.publishRuntimeEffects(
                true,
                -1,
                soundRuntime,
                movement,
                target,
                null,
                writer
            );

        if(!runtime.soundPublished)
            throw new AssertionError(
                "runtime sound not published"
            );

        if(!"NONE".equals(
                runtime.projectilePublication))
            throw new AssertionError(
                "unexpected projectile publication "+
                runtime.projectilePublication
            );

        if(out.size()<=beforeSound)
            throw new AssertionError(
                "runtime sound packet not published"
            );

        System.out.println(
            "COMBAT_PRESENTATION_ADAPTER_PASS "+
            "actor81=true "+
            "actorGfx81=true "+
            "targetGfx65=true "+
            "hit65=true "+
            "targetChangedFailClosed=true "+
            "sound174=true"
        );
    }
}
