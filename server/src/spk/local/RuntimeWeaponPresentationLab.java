package spk.local;

import java.io.IOException;

/**
 * R7.2 session-local presentation-only lab for V9.13 runtime-proven basic weapon rows.
 *
 * R8.1 promotes packet 117 only for the two self-contained bows that were directly
 * live-probed end-to-end in V9.13 (Webweaver and Scorching bow (i)). Other projectile
 * rows remain fail-closed. It still does not apply damage, special formulas, rune costs
 * or legality.
 */
final class RuntimeWeaponPresentationLab {
    static final String PROJECTILE_POLICY=V913LiveProjectilePublisher.POLICY+"_OTHERS_SUPPRESSED";
    static final String PRE_ANIMATION_POLICY="NOT_SEQUENCED_UNTIL_ORDERING_DELAY_PROVEN";

    static boolean previewSafe(V913WeaponRuntimeAuthority.Profile p){
        return p!=null && p.directBasicAttack && p.attackAnimation>=0;
    }

    static String summary(V913WeaponRuntimeAuthority.Profile p){
        if(p==null)return "runtimeProfile=NONE";
        return "item="+p.itemId+" "+p.name+" basic="+p.directBasicAttack+" anim="+p.attackAnimation+
            " actorGfx="+p.actorGfx+" projectile="+p.projectileId+" targetGfx="+p.targetGfx+
            " speed="+(p.speedTicks>0?p.speedTicks:"UNRESOLVED")+
            " projectileGeometry="+(p.hasProjectileGeometry()?p.projectileStartHeight+"/"+p.projectileEndHeight+"/"+p.projectileSlope+"/"+p.projectileStartDistance:"UNRESOLVED")+
            " sound="+(p.hasBasicSound()?p.soundId+"/"+p.soundParam2+"/"+p.soundParam3:"NONE")+
            " pre="+(p.preAnimation>=0?p.preAnimation:"NONE")+" evidence="+p.evidence;
    }

    static String preview(V913WeaponRuntimeAuthority.Profile p,NpcRegistry npcs,MovementState movement,SceneUpdatePublisher scene,ServerPacketWriter w)throws IOException{
        if(p==null)return "REJECTED no runtime weapon profile selected";
        if(!p.directBasicAttack)return "REJECTED profile is not a direct basic attack: "+p.itemId+" "+p.name;
        if(p.attackAnimation<0)return "REJECTED runtime profile has no exact attack animation: "+p.itemId+" "+p.name;
        NpcEntity target=npcs.scene(NpcRegistry.PVM_DUMMY_INDEX);
        if(target==null)target=npcs.scene(NpcRegistry.PLAYER_DUMMY_INDEX);
        if(target==null)return "REJECTED HOME combat dummy not visible";

        if(p.actorGfx>=0)w.varShort(81,CombatSync.player81AnimationGfxAndInteraction(p.attackAnimation,p.actorGfx,0,0,target.sceneIndex));
        else w.varShort(81,CombatSync.player81AnimationAndInteraction(p.attackAnimation,target.sceneIndex));

        String targetFx="NONE";
        if(p.targetGfx>=0)targetFx=npcs.devNpcGfx(target.sceneIndex,p.targetGfx,0,0,w);

        String projectileResult=V913LiveProjectilePublisher.publish(p,movement,target,scene);

        boolean sound=false;
        if(p.hasBasicSound()){
            PacketPayloadWriter payload=new PacketPayloadWriter().putU16BE(p.soundId).putU16BE(p.soundParam2).putU16BE(p.soundParam3);
            w.fixed(174,payload.toByteArray());sound=true;
        }

        return "OK presentationOnly=true item="+p.itemId+" targetScene="+target.sceneIndex+" anim="+p.attackAnimation+
            " actorGfx="+(p.actorGfx>=0?p.actorGfx:"NONE")+" targetGfx="+(p.targetGfx>=0?p.targetGfx:"NONE")+
            " targetFxResult={"+targetFx+"} sound="+(sound?p.soundId:"NONE")+
            " projectile="+p.projectileId+":"+projectileResult+" projectilePolicy="+PROJECTILE_POLICY+" pre="+p.preAnimation+":"+PRE_ANIMATION_POLICY+
            " damage=NONE combatStateMutation=false";
    }

    private RuntimeWeaponPresentationLab(){}
}
