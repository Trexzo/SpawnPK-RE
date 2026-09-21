package spk.local;

import java.lang.reflect.Modifier;
import java.util.*;
import spk.content.api.ContentProvenance;

public final class ContentProvenanceVocabularyTest {
    private static final Set<String> EXPECTED=
        Collections.unmodifiableSet(
            new TreeSet<>(
                Arrays.asList(
                    "EXACT_CURRENT_CLIENT",
                    "EXACT_CURRENT_CACHE",
                    "LOCAL_RUNTIME_PROVEN",
                    "HISTORICAL_CORROBORATION",
                    "INFERENCE",
                    "UNKNOWN_SERVER_AUTHORITY",
                    "CUSTOM_LOCALLAB"
                )
            )
        );

    public static void main(String[] args){
        ArrayList<String> violations=
            new ArrayList<>();

        Class<?> type=
            ContentProvenance.class;

        if(!type.isEnum())
            violations.add(
                type.getName()+
                " is not an enum"
            );

        if(!Modifier.isPublic(
                type.getModifiers()))
            violations.add(
                type.getName()+
                " is not public"
            );

        TreeSet<String> actual=
            new TreeSet<>();

        for(ContentProvenance value:
                ContentProvenance.values())
            actual.add(value.name());

        if(!actual.equals(EXPECTED)){
            TreeSet<String> missing=
                new TreeSet<>(EXPECTED);
            missing.removeAll(actual);

            TreeSet<String> unexpected=
                new TreeSet<>(actual);
            unexpected.removeAll(EXPECTED);

            violations.add(
                "provenance vocabulary changed missing="+
                missing+
                " unexpected="+
                unexpected+
                " actual="+
                actual
            );
        }

        if(!violations.isEmpty())
            throw new AssertionError(
                "content provenance vocabulary violations="+
                violations
            );

        System.out.println(
            "CONTENT_PROVENANCE_VOCABULARY_PASS "+
            "labels="+EXPECTED.size()+" "+
            "exact=true ordinalIndependent=true"
        );
    }

    private ContentProvenanceVocabularyTest(){}
}
