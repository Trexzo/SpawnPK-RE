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

    static ContentPlayer player(
        WorldPlayer worldPlayer,
        ServerPacketWriter writer
    ){
        return new PlayerAdapter(
            worldPlayer,
            writer
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
        private final ServerPacketWriter writer;

        PlayerAdapter(
            WorldPlayer worldPlayer
        ){
            this(
                worldPlayer,
                null
            );
        }

        PlayerAdapter(
            WorldPlayer worldPlayer,
            ServerPacketWriter writer
        ){
            this.worldPlayer=Objects.requireNonNull(
                worldPlayer,
                "worldPlayer"
            );
            this.writer=writer;
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

        @Override public String grantItem(
            int itemId,
            int amount
        ){
            if(writer==null)
                throw new UnsupportedOperationException(
                    "item grant unavailable"
                );

            try{
                return worldPlayer.bank()
                    .spawnItem(
                        itemId,
                        amount,
                        writer
                    );
            }catch(IOException error){
                throw new ContentPresentationException(
                    "content item grant publication failed",
                    error
                );
            }
        }

        @Override public String switchPrayerBook(
            ContentPrayerBook book
        ){
            Objects.requireNonNull(
                book,
                "book"
            );

            if(writer==null)
                throw new UnsupportedOperationException(
                    "prayer book mutation unavailable"
                );

            try{
                return worldPlayer.prayers()
                    .switchBook(
                        book==ContentPrayerBook.NORMAL
                            ?"normal"
                            :"curses",
                        writer
                    );
            }catch(IOException error){
                throw new ContentPresentationException(
                    "content prayer book publication failed",
                    error
                );
            }
        }

        @Override public String switchSpellBook(
            ContentSpellBook book
        ){
            Objects.requireNonNull(
                book,
                "book"
            );

            if(writer==null)
                throw new UnsupportedOperationException(
                    "spell book mutation unavailable"
                );

            String token;

            switch(book){
                case MODERN:
                    token="modern";
                    break;
                case ANCIENT:
                    token="ancient";
                    break;
                case LUNAR:
                    token="lunar";
                    break;
                default:
                    throw new AssertionError(
                        "Unhandled ContentSpellBook "+
                        book
                    );
            }

            try{
                return worldPlayer.magic()
                    .switchBook(
                        token,
                        writer
                    );
            }catch(IOException error){
                throw new ContentPresentationException(
                    "content spell book publication failed",
                    error
                );
            }
        }

        @Override public String deactivatePrayers(){
            if(writer==null)
                throw new UnsupportedOperationException(
                    "prayer deactivation unavailable"
                );

            try{
                return worldPlayer.prayers()
                    .deactivateAll(
                        writer
                    );
            }catch(IOException error){
                throw new ContentPresentationException(
                    "content prayer deactivation publication failed",
                    error
                );
            }
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
        private final ContentDialoguePresentation dialogue;

        PresentationAdapter(
            ServerPacketWriter writer
        ){
            this.writer=
                Objects.requireNonNull(
                    writer,
                    "writer"
                );
            this.dialogue=
                new DialogueAdapter(
                    this.writer
                );
        }

        @Override public ContentDialoguePresentation
            dialogue(){
            return dialogue;
        }

        @Override public String applicationFixture(
            String fixtureName
        ){
            try{
                return ApplicationUiFixtureService.run(
                    fixtureName,
                    writer
                );
            }catch(IOException e){
                throw new ContentPresentationException(
                    "content presentation applicationFixture failed",
                    e
                );
            }
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

    private static final class DialogueAdapter
        implements ContentDialoguePresentation {

        private final ServerPacketWriter writer;

        DialogueAdapter(
            ServerPacketWriter writer
        ){
            this.writer=
                Objects.requireNonNull(
                    writer,
                    "writer"
                );
        }

        @Override public void statement(
            List<String> lines
        ){
            write(
                "dialogueStatement",
                ()->
                    StandardDialoguePresentationAdapter
                        .openStatement(
                            writer,
                            lines
                        )
            );
        }

        @Override public void namedNpc(
            int npcDefinitionId,
            String speakerName,
            List<String> lines
        ){
            write(
                "dialogueNamedNpc",
                ()->
                    StandardDialoguePresentationAdapter
                        .openNamedNpc(
                            writer,
                            npcDefinitionId,
                            speakerName,
                            lines
                        )
            );
        }

        @Override public void twoOptions(
            String title,
            List<String> options
        ){
            write(
                "dialogueTwoOptions",
                ()->
                    StandardDialoguePresentationAdapter
                        .openTwoOptions(
                            writer,
                            title,
                            options
                        )
            );
        }

        @Override public void close(){
            write(
                "dialogueClose",
                ()->
                    StandardDialoguePresentationAdapter
                        .close(
                            writer
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
