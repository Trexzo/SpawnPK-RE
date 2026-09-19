package spk.local;

import java.io.IOException;

/**
 * LOCAL/CUSTOM nurse command adapter.
 *
 * This is explicitly LocalLab-owned gameplay behavior, not recovered original-
 * server authority. It preserves the existing state transition and presentation.
 */
final class LocalNurseCommandHandler {
    private final PlayerState playerState;
    private final MovementState movement;

    LocalNurseCommandHandler(PlayerState playerState,MovementState movement){
        this.playerState=java.util.Objects.requireNonNull(playerState,"playerState");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
    }

    Result handle(
        String[] p,
        String rawCommand,
        boolean scopesightActive,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(p==null||p.length==0||!p[0].equalsIgnoreCase("nurse"))return null;

        int changed=playerState.restoreNurse();
        movement.setRunEnergy(100);
        changed|=playerState.syncScopesightMaintenance(scopesightActive);
        publishSkillMask(changed,serverPackets);

        serverPackets.fixed(110,BootstrapPackets.runEnergy110(100));
        serverPackets.varShort(126,BootstrapPackets.widgetText126(149,"100%"));
        serverPackets.varShort(81,CombatSync.player81AnimationAndGfx(10184,1310,0,0));

        return new Result(
            "V58_NURSE command="+rawCommand+
            " hp="+playerState.currentLevel(PlayerState.HITPOINTS)+
            " prayer="+playerState.currentLevel(PlayerState.PRAYER)+
            " ranged="+playerState.currentLevel(PlayerState.RANGED)+
            " magic="+playerState.currentLevel(PlayerState.MAGIC)+
            " special="+playerState.specialEnergy()+
            " run="+movement.runEnergy()+
            " poison="+playerState.poison()+
            " venom="+playerState.venom()+
            " sicken="+playerState.sicken()+
            " anim=10184 gfx=1310 gfxHeight=0 gfxDelay=0 scopesightActive="+scopesightActive,
            "NURSE"
        );
    }

    static final class Result {
        final String logText;
        final String saveReason;

        Result(String logText,String saveReason){
            this.logText=logText;
            this.saveReason=saveReason;
        }
    }

    private void publishSkillMask(int mask,ServerPacketWriter serverPackets)throws IOException{
        for(int skill=0;skill<PlayerState.COMBAT_SKILL_COUNT;skill++){
            if((mask&(1<<skill))!=0){
                serverPackets.fixed(
                    134,
                    BootstrapPackets.skill134(
                        skill,
                        playerState.xp(skill),
                        playerState.currentLevel(skill)
                    )
                );
            }
        }
    }
}
