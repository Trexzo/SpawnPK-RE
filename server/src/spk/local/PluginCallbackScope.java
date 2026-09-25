package spk.local;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import spk.content.api.*;

final class PluginCallbackScope
    implements AutoCloseable {

    static final class AdmissionException
        extends IllegalStateException {

        AdmissionException(
            String message
        ){
            super(message);
        }
    }

    @FunctionalInterface
    interface CheckedLeaseFunction<T> {
        T apply(Lease lease)
            throws Exception;
    }

    static final class Lease
        implements AutoCloseable {

        private final PluginCallbackScope owner;
        private volatile boolean open=true;

        Lease(
            PluginCallbackScope owner
        ){
            this.owner=owner;
        }

        void requireUsable(){
            owner.requireUsable(this);
        }

        @Override public void close(){
            synchronized(this){
                if(!open)
                    return;

                open=false;
            }

            owner.releaseLease(
                this
            );
        }
    }

    private volatile BooleanSupplier worldExecution;
    private volatile boolean active;
    private volatile boolean closing;
    private volatile boolean closed;
    private int inFlight;
    private Runnable quiescenceListener;

    PluginCallbackScope(
        BooleanSupplier worldExecution
    ){
        this.worldExecution=
            Objects.requireNonNull(
                worldExecution,
                "worldExecution"
            );
    }

    synchronized void activate(){
        if(closing||closed)
            throw new IllegalStateException(
                "plugin callback scope closed"
            );

        active=true;
    }

    synchronized void beginClose(){
        active=false;
        closing=true;
    }

    synchronized boolean quiescent(){
        return inFlight==0;
    }

    void onQuiescent(
        Runnable listener
    ){
        Objects.requireNonNull(
            listener,
            "listener"
        );

        boolean runNow=false;

        synchronized(this){
            if(closed)
                runNow=true;
            else if(quiescenceListener!=null&&
                    quiescenceListener!=listener)
                throw new IllegalStateException(
                    "plugin callback quiescence listener already assigned"
                );
            else if(closing&&inFlight==0)
                runNow=true;
            else
                quiescenceListener=listener;
        }

        if(runNow)
            listener.run();
    }

    void awaitQuiescent(){
        boolean interrupted=false;

        synchronized(this){
            while(inFlight>0)
                try{
                    wait();
                }catch(InterruptedException ignored){
                    interrupted=true;
                }
        }

        if(interrupted)
            Thread.currentThread()
                .interrupt();
    }

    synchronized void finishClose(){
        if(closed)
            return;

        if(inFlight!=0)
            throw new IllegalStateException(
                "plugin callback scope still in flight"
            );

        active=false;
        closing=true;
        closed=true;
        worldExecution=null;
        quiescenceListener=null;
        notifyAll();
    }

    @Override public void close(){
        beginClose();
        awaitQuiescent();
        finishClose();
    }

    <T> T call(
        ClassLoader loader,
        CheckedLeaseFunction<T> action
    )throws Exception{
        Objects.requireNonNull(
            action,
            "action"
        );

        Lease lease=openLease();

        try{
            return PluginThreadContext.call(
                loader,
                ()->action.apply(
                    lease
                )
            );
        }finally{
            lease.close();
        }
    }

    <T> T callUnchecked(
        ClassLoader loader,
        Function<Lease,T> action
    ){
        Objects.requireNonNull(
            action,
            "action"
        );

        Lease lease=openLease();

        try{
            return PluginThreadContext
                .callUnchecked(
                    loader,
                    ()->action.apply(
                        lease
                    )
                );
        }finally{
            lease.close();
        }
    }

    ContentCommandContext commandContext(
        ContentCommandContext delegate,
        Lease lease
    ){
        return new LeasedCommandContext(
            Objects.requireNonNull(
                delegate,
                "delegate"
            ),
            requireLease(lease)
        );
    }

    ContentActionContext actionContext(
        ContentActionContext delegate,
        Lease lease
    ){
        return new LeasedActionContext(
            Objects.requireNonNull(
                delegate,
                "delegate"
            ),
            requireLease(lease)
        );
    }

    ContentDialogueContext dialogueContext(
        ContentDialogueContext delegate,
        Lease lease
    ){
        return new LeasedDialogueContext(
            Objects.requireNonNull(
                delegate,
                "delegate"
            ),
            requireLease(lease)
        );
    }

    ContentItemOnPlayerContext itemOnPlayerContext(
        ContentItemOnPlayerContext delegate,
        Lease lease
    ){
        return new LeasedItemOnPlayerContext(
            Objects.requireNonNull(
                delegate,
                "delegate"
            ),
            requireLease(lease)
        );
    }

    private synchronized Lease openLease(){
        if(!active||closing||closed)
            throw new AdmissionException(
                "plugin callback scope inactive"
            );

        BooleanSupplier execution=
            worldExecution;

        if(execution==null||
           !execution.getAsBoolean())
            throw new AdmissionException(
                "plugin callback requires World execution context"
            );

        inFlight++;
        return new Lease(this);
    }

    private void releaseLease(
        Lease lease
    ){
        Runnable listener=null;

        synchronized(this){
            if(lease==null||
               lease.owner!=this)
                return;

            if(inFlight<=0)
                throw new IllegalStateException(
                    "plugin callback scope in-flight underflow"
                );

            inFlight--;

            if(closing&&
               inFlight==0&&
               quiescenceListener!=null){
                listener=quiescenceListener;
                quiescenceListener=null;
            }

            notifyAll();
        }

        if(listener!=null)
            listener.run();
    }

    private Lease requireLease(
        Lease lease
    ){
        if(lease==null||
           lease.owner!=this)
            throw new IllegalArgumentException(
                "foreign plugin callback lease"
            );

        lease.requireUsable();
        return lease;
    }

    private void requireUsable(
        Lease lease
    ){
        if(closed)
            throw new IllegalStateException(
                "plugin callback scope inactive"
            );

        if(lease==null||
           lease.owner!=this||
           !lease.open)
            throw new IllegalStateException(
                "plugin callback lease closed"
            );

        BooleanSupplier execution=
            worldExecution;

        if(execution==null||
           !execution.getAsBoolean())
            throw new IllegalStateException(
                "plugin callback facade requires World execution context"
            );
    }

    private static final class LeasedCommandContext
        implements ContentCommandContext {

        private final ContentCommandContext delegate;
        private final Lease lease;
        private final ContentPlayer player;
        private final ContentPresentation presentation;

        LeasedCommandContext(
            ContentCommandContext delegate,
            Lease lease
        ){
            this.delegate=delegate;
            this.lease=lease;
            player=
                new LeasedPlayer(
                    delegate.player(),
                    lease
                );
            presentation=
                new LeasedPresentation(
                    delegate.presentation(),
                    lease
                );
        }

        @Override public String rawCommand(){
            return delegate.rawCommand();
        }

        @Override public String commandName(){
            return delegate.commandName();
        }

        @Override public List<String> arguments(){
            return delegate.arguments();
        }

        @Override public ContentPlayer player(){
            lease.requireUsable();
            return player;
        }

        @Override public ContentPresentation presentation(){
            lease.requireUsable();
            return presentation;
        }
    }

    private static final class LeasedActionContext
        implements ContentActionContext {

        private final ContentActionContext delegate;
        private final Lease lease;
        private final ContentPlayer player;

        LeasedActionContext(
            ContentActionContext delegate,
            Lease lease
        ){
            this.delegate=delegate;
            this.lease=lease;
            player=
                new LeasedPlayer(
                    delegate.player(),
                    lease
                );
        }

        @Override public String actionKey(){
            return delegate.actionKey();
        }

        @Override public ContentPlayer player(){
            lease.requireUsable();
            return player;
        }
    }

    private static final class LeasedDialogueContext
        implements ContentDialogueContext {

        private final ContentDialogueContext delegate;
        private final Lease lease;
        private final ContentPlayer player;

        LeasedDialogueContext(
            ContentDialogueContext delegate,
            Lease lease
        ){
            this.delegate=delegate;
            this.lease=lease;
            player=
                new LeasedPlayer(
                    delegate.player(),
                    lease
                );
        }

        @Override public String dialogueKey(){
            return delegate.dialogueKey();
        }

        @Override public String nodeKey(){
            return delegate.nodeKey();
        }

        @Override public ContentDialogueIntent intent(){
            return delegate.intent();
        }

        @Override public ContentPlayer player(){
            lease.requireUsable();
            return player;
        }
    }

    private static final class LeasedItemOnPlayerContext
        implements ContentItemOnPlayerContext {

        private final ContentItemOnPlayerContext delegate;
        private final Lease lease;
        private final ContentPlayer target;

        LeasedItemOnPlayerContext(
            ContentItemOnPlayerContext delegate,
            Lease lease
        ){
            this.delegate=delegate;
            this.lease=lease;
            target=
                new LeasedPlayer(
                    delegate.target(),
                    lease
                );
        }

        @Override public int itemId(){
            return delegate.itemId();
        }

        @Override public ContentPlayer target(){
            lease.requireUsable();
            return target;
        }
    }

    private static final class LeasedPlayer
        implements ContentPlayer {

        private final ContentPlayer delegate;
        private final Lease lease;

        LeasedPlayer(
            ContentPlayer delegate,
            Lease lease
        ){
            this.delegate=
                Objects.requireNonNull(
                    delegate,
                    "delegate"
                );
            this.lease=lease;
        }

        private void require(){
            lease.requireUsable();
        }

        @Override public int skillLevel(
            ContentSkill skill
        ){
            require();
            return delegate.skillLevel(skill);
        }

        @Override public int skillExperience(
            ContentSkill skill
        ){
            require();
            return delegate.skillExperience(skill);
        }

        @Override public Set<ContentSkill>
            restoreCombatSkillsAndSpecial(){
            require();
            return delegate
                .restoreCombatSkillsAndSpecial();
        }

        @Override public void clearTimedStatuses(){
            require();
            delegate.clearTimedStatuses();
        }

        @Override public void setRunEnergy(
            int value
        ){
            require();
            delegate.setRunEnergy(value);
        }

        @Override public Set<ContentSkill>
            syncMaintainedPetEffects(){
            require();
            return delegate
                .syncMaintainedPetEffects();
        }

        @Override public boolean
            maintainedPetEffectActive(){
            require();
            return delegate
                .maintainedPetEffectActive();
        }

        @Override public String grantItem(
            int itemId,
            int amount
        ){
            require();
            return delegate.grantItem(
                itemId,
                amount
            );
        }

        @Override public String switchPrayerBook(
            ContentPrayerBook book
        ){
            require();
            return delegate.switchPrayerBook(book);
        }

        @Override public String switchSpellBook(
            ContentSpellBook book
        ){
            require();
            return delegate.switchSpellBook(book);
        }

        @Override public String deactivatePrayers(){
            require();
            return delegate.deactivatePrayers();
        }

        @Override public int worldX(){
            require();
            return delegate.worldX();
        }

        @Override public int worldY(){
            require();
            return delegate.worldY();
        }

        @Override public int plane(){
            require();
            return delegate.plane();
        }

        @Override public int runEnergy(){
            require();
            return delegate.runEnergy();
        }

        @Override public int specialEnergy(){
            require();
            return delegate.specialEnergy();
        }

        @Override public int poison(){
            require();
            return delegate.poison();
        }

        @Override public int venom(){
            require();
            return delegate.venom();
        }

        @Override public int sicken(){
            require();
            return delegate.sicken();
        }
    }

    private static final class LeasedPresentation
        implements ContentPresentation {

        private final ContentPresentation delegate;
        private final Lease lease;

        LeasedPresentation(
            ContentPresentation delegate,
            Lease lease
        ){
            this.delegate=
                Objects.requireNonNull(
                    delegate,
                    "delegate"
                );
            this.lease=lease;
        }

        private void require(){
            lease.requireUsable();
        }

        @Override public ContentDialoguePresentation dialogue(){
            require();
            return new LeasedDialoguePresentation(
                delegate.dialogue(),
                lease
            );
        }

        @Override public String applicationFixture(
            String fixtureName
        ){
            require();
            return delegate.applicationFixture(
                fixtureName
            );
        }

        @Override public void skill(
            ContentSkill skill,
            int experience,
            int currentLevel
        ){
            require();
            delegate.skill(
                skill,
                experience,
                currentLevel
            );
        }

        @Override public void runEnergy(
            int energy
        ){
            require();
            delegate.runEnergy(energy);
        }

        @Override public void specialEnergy(
            int percent
        ){
            require();
            delegate.specialEnergy(percent);
        }

        @Override public void animationAndGfx(
            int animationId,
            int gfxId,
            int gfxHeight,
            int gfxDelay
        ){
            require();
            delegate.animationAndGfx(
                animationId,
                gfxId,
                gfxHeight,
                gfxDelay
            );
        }
    }

    private static final class LeasedDialoguePresentation
        implements ContentDialoguePresentation {

        private final ContentDialoguePresentation delegate;
        private final Lease lease;

        LeasedDialoguePresentation(
            ContentDialoguePresentation delegate,
            Lease lease
        ){
            this.delegate=
                Objects.requireNonNull(
                    delegate,
                    "delegate"
                );
            this.lease=lease;
        }

        private void require(){
            lease.requireUsable();
        }

        @Override public void statement(
            List<String> lines
        ){
            require();
            delegate.statement(lines);
        }

        @Override public void namedNpc(
            int npcDefinitionId,
            String speakerName,
            List<String> lines
        ){
            require();
            delegate.namedNpc(
                npcDefinitionId,
                speakerName,
                lines
            );
        }

        @Override public void twoOptions(
            String title,
            List<String> options
        ){
            require();
            delegate.twoOptions(
                title,
                options
            );
        }

        @Override public void close(){
            require();
            delegate.close();
        }
    }
}
