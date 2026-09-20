package spk.local;

import java.util.*;

/** Deterministic immutable-definition recipe registry. */
final class RecipeCatalog {
    private final LinkedHashMap<RecipeId,RecipeDefinition> definitions=
        new LinkedHashMap<>();

    synchronized void define(RecipeDefinition definition){
        Objects.requireNonNull(definition,"definition");

        if(definitions.containsKey(definition.id))
            throw new IllegalStateException(
                "duplicate recipe id="+definition.id
            );

        definitions.put(definition.id,definition);
    }

    synchronized RecipeDefinition get(RecipeId id){
        return definitions.get(
            Objects.requireNonNull(id,"id")
        );
    }

    synchronized RecipeDefinition get(String id){
        return get(RecipeId.of(id));
    }

    synchronized RecipeDefinition require(RecipeId id){
        RecipeDefinition definition=get(id);

        if(definition==null)
            throw new IllegalArgumentException(
                "unknown recipe id="+id
            );

        return definition;
    }

    synchronized int size(){
        return definitions.size();
    }

    synchronized List<RecipeDefinition> all(){
        ArrayList<RecipeDefinition> out=
            new ArrayList<>(definitions.values());

        out.sort(Comparator.comparing(value->value.id));
        return Collections.unmodifiableList(out);
    }
}
