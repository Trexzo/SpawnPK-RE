package spk.event;

/**
 * Semantic fact that one exact-current registered player reached an
 * authoritative logical World tick.
 */
public final class PlayerTickEvent
    extends PlayerEvent {

    private final long tick;

    public PlayerTickEvent(
        String playerRef,
        long tick
    ){
        super(playerRef);

        if(tick<=0L)
            throw new IllegalArgumentException(
                "tick="+tick
            );

        this.tick=tick;
    }

    public long tick(){
        return tick;
    }
}
