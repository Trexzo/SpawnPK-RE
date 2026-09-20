package spk.local;

import java.io.IOException;

/**
 * Combat packet/presentation boundary.
 *
 * This adapter owns client-visible attack/hit/projectile/sound publication only.
 * Target validation, timing, damage calculation and lifecycle are deliberately
 * outside this class.
 */
final class CombatPresentationAdapter {
    static final class ActorPublication {
        final boolean actorGfxPublished;

        ActorPublication(boolean actorGfxPublished){
            this.actorGfxPublished=actorGfxPublished;
        }
    }

    static final class RuntimePublication {
        final String projectilePublication;
        final boolean soundPublished;

        RuntimePublication(
            String projectilePublication,
            boolean soundPublished
        ){
            this.projectilePublication=
                projectilePublication==null
                    ?"NONE"
                    :projectilePublication;
            this.soundPublished=soundPublished;
        }
    }

    static final class HitPublication {
        final boolean published;
        final int damage;
        final String log;

        HitPublication(
            boolean published,
            int damage,
            String log
        ){
            this.published=published;
            this.damage=damage;
            this.log=log;
        }
    }

    ActorPublication publishActor(
        int attackAnimation,
        int actorGfx,
        NpcEntity target,
        ServerPacketWriter writer
    )throws IOException{
        if(target==null)
            throw new NullPointerException("target");
        if(writer==null)
            throw new NullPointerException("writer");

        boolean gfxPublished=false;

        if(attackAnimation>=0){
            if(actorGfx>=0){
                writer.varShort(
                    81,
                    CombatSync.player81AnimationGfxAndInteraction(
                        attackAnimation,
                        actorGfx,
                        0,
                        0,
                        target.sceneIndex
                    )
                );
                gfxPublished=true;
            }else{
                writer.varShort(
                    81,
                    CombatSync.player81AnimationAndInteraction(
                        attackAnimation,
                        target.sceneIndex
                    )
                );
            }
        }else{
            writer.varShort(
                81,
                CombatSync.player81InteractionOnly(
                    target.sceneIndex
                )
            );
        }

        return new ActorPublication(
            gfxPublished
        );
    }

    void publishTargetGfx(
        NpcEntity target,
        int targetGfx,
        NpcRegistry npcs,
        ServerPacketWriter writer
    )throws IOException{
        if(targetGfx<0)return;

        npcs.sendMask(
            target,
            NpcSyncEncoder.Mask.gfx(
                targetGfx,
                0,
                0
            ),
            writer
        );
    }

    HitPublication publishHit(
        CombatHitScheduler.ScheduledHit hit,
        NpcRegistry npcs,
        ServerPacketWriter writer
    )throws IOException{
        if(hit==null)
            return new HitPublication(
                false,
                0,
                "M2_HIT_NONE"
            );

        NpcEntity target=
            npcs.scene(
                hit.targetSceneIndex
            );

        if(target==null||
           target.definitionId!=
                hit.targetDefinitionId){
            return new HitPublication(
                false,
                0,
                "M2_HIT_CANCELLED_TARGET_CHANGED scene="+
                    hit.targetSceneIndex+
                    " def="+hit.targetDefinitionId+
                    " scheduledTick="+hit.scheduledTick
            );
        }

        NpcSyncEncoder.Mask hitMask=
            NpcSyncEncoder.Mask.singleHit(
                hit.damage,
                hit.hitType,
                hit.hp,
                hit.hpMax
            );

        if(hit.targetGfx>=0)
            hitMask=hitMask.withGfx(
                hit.targetGfx,
                0,
                0
            );

        npcs.sendMask(
            target,
            hitMask,
            writer
        );

        return new HitPublication(
            true,
            hit.damage,
            "M2_HIT_PUBLISHED scene="+
                hit.targetSceneIndex+
                " def="+hit.targetDefinitionId+
                " damage="+hit.damage+
                " scheduledTick="+hit.scheduledTick+
                " damageAuthority="+hit.damageAuthority+
                " damageFormula="+hit.damageFormula
        );
    }

    RuntimePublication publishRuntimeEffects(
        boolean runtimeBasic,
        int projectileId,
        V913WeaponRuntimeAuthority.Profile runtime,
        MovementState movement,
        NpcEntity target,
        SceneUpdatePublisher scene,
        ServerPacketWriter writer
    )throws IOException{
        String projectilePublication="NONE";

        if(runtimeBasic&&projectileId>=0){
            projectilePublication=
                V913LiveProjectilePublisher.publish(
                    runtime,
                    movement,
                    target,
                    scene
                );
        }

        boolean soundPublished=false;

        if(runtimeBasic&&
           runtime!=null&&
           runtime.hasBasicSound()){
            writer.fixed(
                174,
                new PacketPayloadWriter()
                    .putU16BE(runtime.soundId)
                    .putU16BE(runtime.soundParam2)
                    .putU16BE(runtime.soundParam3)
                    .toByteArray()
            );
            soundPublished=true;
        }

        return new RuntimePublication(
            projectilePublication,
            soundPublished
        );
    }
}
