package spk.local;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R74 OFFLINE ONLY: quiesce ordinary World commands when an isolated test
 * captures its terminal logout state. Subsequent producer submission and
 * already-queued command execution both consult this gate.
 *
 * The trusted final-release action is a test-only marker. A World callback
 * still validates the R72 canonical final save, R64 active owner, and
 * exact World generation; this marker is NOT real login authentication.
 * No production World, LocalSession or socket installs this policy.
 */
final class R74OfflineCommandIngressFence
        implements WorldCommandInbox.OfflineCommandAdmission {
    private final World world;
    private final ConcurrentHashMap<EntityId,Long> quiesced=
        new ConcurrentHashMap<>();

    R74OfflineCommandIngressFence(World world){
        this.world=Objects.requireNonNull(world,"world");
    }

    void installForTest(){
        world.commands().installOfflineCommandAdmissionForTest(this);
    }

    void quiesceInsideWorldActor(WorldPlayer player,long generation){
        Objects.requireNonNull(player,"player");
        if(!world.pulse().inExecutionContext()||
           !world.players().owns(player,generation))
            throw new IllegalStateException(
                "R74_QUIESCE_DENIED not current World actor"
            );
        Long prior=quiesced.putIfAbsent(player.id(),generation);
        if(prior!=null)
            throw new IllegalStateException(
                "R74_QUIESCE_DENIED already quiesced"
            );
    }

    boolean isQuiesced(WorldPlayer player,long generation){
        Long stored=quiesced.get(player.id());
        return stored!=null && stored.longValue()==generation;
    }

    @Override public boolean permits(
        WorldPlayer player,long generation,
        WorldCommandInbox.Action action
    ){
        Long stored=quiesced.get(player.id());
        if(stored==null||stored.longValue()!=generation)
            return !(action instanceof TrustedFinalRelease);
        if(!(action instanceof TrustedFinalRelease))
            return false;
        TrustedFinalRelease trusted=(TrustedFinalRelease)action;
        return trusted.owner==player &&
            trusted.generation==generation &&
            trusted.fence==this;
    }

    WorldCommandInbox.Action trustedFinalReleaseForTest(
        WorldPlayer player,long generation,
        WorldCommandInbox.Action action
    ){
        if(!isQuiesced(player,generation))
            throw new IllegalStateException(
                "R74_RELEASE_DENIED owner not quiesced"
            );
        return new TrustedFinalRelease(
            this,player,generation,Objects.requireNonNull(action,"action")
        );
    }

    private static final class TrustedFinalRelease
            implements WorldCommandInbox.Action {
        private final R74OfflineCommandIngressFence fence;
        private final WorldPlayer owner;
        private final long generation;
        private final WorldCommandInbox.Action action;

        TrustedFinalRelease(
            R74OfflineCommandIngressFence fence,
            WorldPlayer owner,long generation,
            WorldCommandInbox.Action action
        ){
            this.fence=fence;
            this.owner=owner;
            this.generation=generation;
            this.action=action;
        }

        @Override public void run()throws Exception{
            action.run();
        }
    }
}
