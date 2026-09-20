package spk.local;

import java.io.IOException;
import java.util.*;
import spk.content.api.*;

/** Internal adapters from stable content APIs to authoritative LocalLab state. */
final class ContentRuntimeAdapters {
    static ContentPlayer player(
        WorldPlayer worldPlayer
    ){
        return new PlayerAdapter(
            worldPlayer
        );
    }

    static ContentPresentation presentation(
        ServerPacketWriter writer
    ){
        return new PresentationAdapter(
            writer
        );
    }

    private static final class PlayerAdapter
        implements ContentPlayer {

        private final WorldPlayer worldPlayer;
        private final PlayerState player;
        private final MovementState movement;
        private final PlayerStatusService statuses;

        PlayerAdapter(
            WorldPlayer worldPlayer
        ){
            this.worldPlayer=Objects.requireNonNull(
                worldPlayer,
                "worldPlayer"
            );
            this.player=worldPlayer.playerState();
            this.movement=worldPlayer.movement();
            this.statuses=
                new PlayerStatusService(
                    worldPlayer
                );
        }

        @Override public int skillLevel(
            ContentSkill skill
        ){
            return player.currentLevel(
                index(skill)
            );
        }

        @Override public int skillExperience(
            ContentSkill skill
        ){
            return player.xp(
                index(skill)
            );
        }

        @Override public Set<ContentSkill>
            restoreCombatSkillsAndSpecial(){
            int mask=
                player.restoreNurse();
            return skills(mask);
        }

        @Override public void clearTimedStatuses(){
            statuses.clearAll();
        }

        @Override public void setRunEnergy(
            int value
        ){
            movement.setRunEnergy(value);
        }

        @Override public Set<ContentSkill>
            syncMaintainedPetEffects(){
            return skills(
                player.syncScopesightMaintenance(
                    maintainedPetEffectActive()
                )
            );
        }

        @Override public boolean maintainedPetEffectActive(){
            PetState pet=
                worldPlayer.petState();

            return pet.active()&&
                pet.itemId()==
                    ScopesightPetProfile.ITEM_ID&&
                pet.npcId()==
                    ScopesightPetProfile.NPC_ID;
        }

        @Override public int runEnergy(){
            return movement.runEnergy();
        }

        @Override public int specialEnergy(){
            return player.specialEnergy();
        }

        @Override public int poison(){
            return player.poison();
        }

        @Override public int venom(){
            return player.venom();
        }

        @Override public int sicken(){
            return player.sicken();
        }

        private static int index(
            ContentSkill skill
        ){
            if(skill==null)
                throw new NullPointerException(
                    "skill"
                );
            return skillIndex(skill);
        }

        private static Set<ContentSkill> skills(
            int mask
        ){
            EnumSet<ContentSkill> result=
                EnumSet.noneOf(
                    ContentSkill.class
                );

            for(ContentSkill skill:
                    ContentSkill.values())
                if((mask&
                    (1<<skillIndex(skill)))!=0)
                    result.add(skill);

            return Collections.unmodifiableSet(
                result
            );
        }
    }

    private static final class PresentationAdapter
        implements ContentPresentation {

        private final ServerPacketWriter writer;

        PresentationAdapter(
            ServerPacketWriter writer
        ){
            this.writer=
                Objects.requireNonNull(
                    writer,
                    "writer"
                );
        }

        @Override public void skill(
            ContentSkill skill,
            int experience,
            int currentLevel
        ){
            write(
                "skill",
                ()->writer.fixed(
                    134,
                    BootstrapPackets.skill134(
                        skillIndex(skill),
                        experience,
                        currentLevel
                    )
                )
            );
        }

        @Override public void runEnergy(
            int energy
        ){
            write(
                "runEnergy",
                ()->writer.fixed(
                    110,
                    BootstrapPackets.runEnergy110(
                        energy
                    )
                )
            );
        }

        @Override public void specialEnergy(
            int percent
        ){
            write(
                "specialEnergy",
                ()->writer.varShort(
                    126,
                    BootstrapPackets.widgetText126(
                        149,
                        percent+"%"
                    )
                )
            );
        }

        @Override public void animationAndGfx(
            int animationId,
            int gfxId,
            int gfxHeight,
            int gfxDelay
        ){
            write(
                "animationAndGfx",
                ()->writer.varShort(
                    81,
                    CombatSync.player81AnimationAndGfx(
                        animationId,
                        gfxId,
                        gfxHeight,
                        gfxDelay
                    )
                )
            );
        }

        private void write(
            String operation,
            PacketWrite action
        ){
            try{
                action.run();
            }catch(IOException e){
                throw new ContentPresentationException(
                    "content presentation "+
                    operation+
                    " failed",
                    e
                );
            }
        }

        @FunctionalInterface
        private interface PacketWrite {
            void run()throws IOException;
        }
    }

    private static int skillIndex(
        ContentSkill skill
    ){
        if(skill==null)
            throw new NullPointerException(
                "skill"
            );

        switch(skill){
            case ATTACK: return 0;
            case DEFENCE: return 1;
            case STRENGTH: return 2;
            case HITPOINTS: return 3;
            case RANGED: return 4;
            case PRAYER: return 5;
            case MAGIC: return 6;
            default:
                throw new AssertionError(
                    "Unhandled ContentSkill "+skill
                );
        }
    }

    private ContentRuntimeAdapters(){}
}
