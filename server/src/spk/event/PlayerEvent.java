package spk.event;

import java.util.Locale;
import java.util.Objects;

/**
 * Public transport-agnostic base for semantic player domain events.
 *
 * <p>The player reference is a stable semantic owner key, never a protocol
 * player index, scene index, socket identity or mutable server entity object.</p>
 */
public abstract class PlayerEvent
    implements DomainEventBus.Event {

    private final String playerRef;

    protected PlayerEvent(
        String playerRef
    ){
        String normalized=
            Objects.requireNonNull(
                playerRef,
                "playerRef"
            ).trim().toLowerCase(
                Locale.ROOT
            );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "playerRef blank"
            );

        this.playerRef=normalized;
    }

    public final String playerRef(){
        return playerRef;
    }
}
