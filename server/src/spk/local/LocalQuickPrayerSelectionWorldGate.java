package spk.local;

import java.io.IOException;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * P5.16 world-generation gate for Quick Prayer selection/confirm clicks.
 *
 * The G121 root-opening arbiter remains independent; the edit and confirm
 * operations are now also fenced against a revoked in-process session.
 * This does not confer a cross-JVM named-account lease.
 */
final class LocalQuickPrayerSelectionWorldGate {
    private final World world;
    private final WorldPlayer owner;
    private final LongSupplier generation;
    private final QuickPrayerSelectionPresentation presentation;

    LocalQuickPrayerSelectionWorldGate(
        World world,
        WorldPlayer owner,
        LongSupplier generation,
        QuickPrayerSelectionPresentation presentation
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.owner=Objects.requireNonNull(owner,"owner");
        this.generation=Objects.requireNonNull(generation,"generation");
        this.presentation=Objects.requireNonNull(
            presentation,"presentation");
    }

    String handle(int widget)throws IOException{
        // Semantic editor is the sole owner of the confirmed revision;
        // retain World lifecycle ownership through toggle/confirm.
        final String[] response=new String[1];
        boolean current=world.withOpenPlayerOwnershipIfCurrent(
            owner,
            generation.getAsLong(),
            ()->{
                synchronized(owner.mutationLock()){
                    response[0]=presentation.handleWidget(widget);
                }
            }
        );
        return current
            ?response[0]
            :"REJECTED_STALE_WORLD_OWNER";
    }
}
