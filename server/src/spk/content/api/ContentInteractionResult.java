package spk.content.api;

import java.util.Objects;

/** Semantic interaction decision returned by content without transport details. */
public final class ContentInteractionResult {
    private final String outcome;

    private ContentInteractionResult(String outcome){
        this.outcome=Objects.requireNonNull(
            outcome,
            "outcome"
        );
    }

    public static ContentInteractionResult handled(
        String outcome
    ){
        return new ContentInteractionResult(
            outcome
        );
    }

    public String outcome(){
        return outcome;
    }

    @Override public String toString(){
        return "ContentInteractionResult{outcome="+
            outcome+"}";
    }
}
