package spk.local;

import java.util.*;

/** Immutable semantic conversion recipe definition. */
final class RecipeDefinition {
    enum AssetKind {
        ITEM,
        CURRENCY,
        GENERIC
    }

    static final class RecipeIngredient {
        final AssetKind kind;
        final String semanticKey;
        final long quantity;

        RecipeIngredient(
            AssetKind kind,
            String semanticKey,
            long quantity
        ){
            this.kind=Objects.requireNonNull(kind,"kind");
            this.semanticKey=normalizeAssetKey(semanticKey);
            if(quantity<=0)
                throw new IllegalArgumentException(
                    "ingredient quantity="+quantity
                );
            this.quantity=quantity;
        }

        String identityKey(){
            return kind.name()+":"+semanticKey;
        }
    }

    static final class RecipeOutput {
        final AssetKind kind;
        final String semanticKey;
        final long quantity;

        RecipeOutput(
            AssetKind kind,
            String semanticKey,
            long quantity
        ){
            this.kind=Objects.requireNonNull(kind,"kind");
            this.semanticKey=normalizeAssetKey(semanticKey);
            if(quantity<=0)
                throw new IllegalArgumentException(
                    "output quantity="+quantity
                );
            this.quantity=quantity;
        }

        String identityKey(){
            return kind.name()+":"+semanticKey;
        }
    }

    final RecipeId id;
    final List<RecipeIngredient> inputs;
    final List<RecipeOutput> outputs;
    final AtomicTransactionService.SourceAuthority sourceAuthority;

    RecipeDefinition(
        RecipeId id,
        List<RecipeIngredient> inputs,
        List<RecipeOutput> outputs,
        AtomicTransactionService.SourceAuthority sourceAuthority
    ){
        this.id=Objects.requireNonNull(id,"id");
        this.inputs=immutableInputs(inputs);
        this.outputs=immutableOutputs(outputs);
        this.sourceAuthority=
            Objects.requireNonNull(
                sourceAuthority,
                "sourceAuthority"
            );
    }

    private static List<RecipeIngredient> immutableInputs(
        List<RecipeIngredient> values
    ){
        if(values==null)
            throw new NullPointerException("inputs");
        if(values.isEmpty())
            throw new IllegalArgumentException("inputs empty");

        ArrayList<RecipeIngredient> copy=new ArrayList<>();
        HashSet<String> identities=new HashSet<>();

        for(RecipeIngredient value:values){
            RecipeIngredient ingredient=
                Objects.requireNonNull(value,"ingredient");

            if(!identities.add(ingredient.identityKey()))
                throw new IllegalArgumentException(
                    "duplicate recipe input "+
                    ingredient.identityKey()
                );

            copy.add(ingredient);
        }

        return Collections.unmodifiableList(copy);
    }

    private static List<RecipeOutput> immutableOutputs(
        List<RecipeOutput> values
    ){
        if(values==null)
            throw new NullPointerException("outputs");
        if(values.isEmpty())
            throw new IllegalArgumentException("outputs empty");

        ArrayList<RecipeOutput> copy=new ArrayList<>();
        HashSet<String> identities=new HashSet<>();

        for(RecipeOutput value:values){
            RecipeOutput output=
                Objects.requireNonNull(value,"output");

            if(!identities.add(output.identityKey()))
                throw new IllegalArgumentException(
                    "duplicate recipe output "+
                    output.identityKey()
                );

            copy.add(output);
        }

        return Collections.unmodifiableList(copy);
    }

    static String normalizeAssetKey(String value){
        if(value==null)
            throw new NullPointerException("semanticKey");

        String normalized=value.trim().toLowerCase(Locale.ROOT);

        if(normalized.isEmpty())
            throw new IllegalArgumentException("semanticKey blank");
        if(normalized.length()>160)
            throw new IllegalArgumentException("semanticKey too long");

        for(int i=0;i<normalized.length();i++){
            char c=normalized.charAt(i);
            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||c=='_'||c=='-'||c==':';
            if(!ok)
                throw new IllegalArgumentException(
                    "invalid semanticKey="+value
                );
        }

        return normalized;
    }
}
