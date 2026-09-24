package spk.local;

import java.lang.reflect.*;
import java.util.*;
import spk.content.api.*;

public final class ContentRegistrarAuthorityCoverageTest {
    public interface InheritedRegistrarExtension {
        ContentRegistration inheritedAuthorityProbe(int marker);
    }

    public interface InheritedRegistrarChild
        extends ContentRegistrar,
                InheritedRegistrarExtension {
    }

    private static final Set<String> EXPECTED=
        Collections.unmodifiableSet(
            new TreeSet<>(
                Arrays.asList(
                    "command(java.lang.String,int,spk.content.api.ContentCommandHandler)->spk.content.api.ContentRegistration",
                    "dialogue(java.lang.String,int,spk.content.api.ContentDialogueHandler)->spk.content.api.ContentRegistration",
                    "itemOnGroundItem(int,int,int,spk.content.api.ContentItemOnGroundItemHandler)->spk.content.api.ContentRegistration",
                    "itemOnItem(int,int,int,spk.content.api.ContentItemOnItemHandler)->spk.content.api.ContentRegistration",
                    "itemOnNpc(int,int,int,spk.content.api.ContentItemOnNpcHandler)->spk.content.api.ContentRegistration",
                    "itemOnObject(int,int,int,spk.content.api.ContentItemOnObjectHandler)->spk.content.api.ContentRegistration",
                    "itemOnPlayer(int,int,spk.content.api.ContentItemOnPlayerHandler)->spk.content.api.ContentRegistration",
                    "itemOption(int,int,int,spk.content.api.ContentItemOptionHandler)->spk.content.api.ContentRegistration",
                    "npcOption(int,int,int,spk.content.api.ContentNpcOptionHandler)->spk.content.api.ContentRegistration",
                    "objectOption(int,int,int,spk.content.api.ContentObjectOptionHandler)->spk.content.api.ContentRegistration"
                )
            )
        );

    public static void main(String[] args){
        ArrayList<String> violations=
            new ArrayList<>();

        Class<?> registrar=
            ContentRegistrar.class;

        if(!registrar.isInterface())
            violations.add(
                registrar.getName()+
                " is not an interface"
            );

        if(!Modifier.isPublic(
                registrar.getModifiers()))
            violations.add(
                registrar.getName()+
                " is not public"
            );

        TreeSet<String> actual=
            registrarSignatures(
                registrar,
                violations
            );

        if(!actual.equals(EXPECTED)){
            TreeSet<String> missing=
                new TreeSet<>(EXPECTED);
            missing.removeAll(actual);

            TreeSet<String> unexpected=
                new TreeSet<>(actual);
            unexpected.removeAll(EXPECTED);

            violations.add(
                "registrar authority coverage changed missing="+
                missing+
                " unexpected="+
                unexpected+
                " actual="+
                actual
            );
        }

        assertInheritedRegistrarExtensionDetected(
            violations
        );

        if(!violations.isEmpty())
            throw new AssertionError(
                "content registrar authority coverage violations="+
                violations
            );

        System.out.println(
            "CONTENT_REGISTRAR_AUTHORITY_COVERAGE_PASS "+
            "methods="+EXPECTED.size()+" "+
            "returnsRegistration=true "+
            "failClosed=true "+
            "inheritedSurface=true "+
            "fullyQualified=true"
        );
    }

    private static TreeSet<String> registrarSignatures(
        Class<?> registrar,
        List<String> violations
    ){
        TreeSet<String> actual=
            new TreeSet<>();

        for(Method method:
                registrar.getMethods()){
            int modifiers=
                method.getModifiers();

            if(!Modifier.isPublic(modifiers)||
               !Modifier.isAbstract(modifiers)||
               Modifier.isStatic(modifiers)||
               method.isSynthetic()||
               method.isBridge())
                continue;

            if(method.getReturnType()!=
                    ContentRegistration.class)
                violations.add(
                    method.getName()+
                    " return type changed to "+
                    method.getReturnType()
                        .getName()
                );

            actual.add(
                signature(method)
            );
        }

        return actual;
    }

    private static void assertInheritedRegistrarExtensionDetected(
        List<String> violations
    ){
        ArrayList<String> syntheticViolations=
            new ArrayList<>();

        TreeSet<String> actual=
            registrarSignatures(
                InheritedRegistrarChild.class,
                syntheticViolations
            );

        String inherited=
            "inheritedAuthorityProbe(int)->"+
            "spk.content.api.ContentRegistration";

        if(!syntheticViolations.isEmpty()||
           !actual.contains(inherited)||
           actual.size()!=EXPECTED.size()+1)
            violations.add(
                "inherited registrar method escaped guard "+
                "actual="+actual+
                " syntheticViolations="+
                syntheticViolations
            );
    }

    private static String signature(
        Method method
    ){
        StringBuilder result=
            new StringBuilder();

        result.append(
            method.getName()
        );
        result.append('(');

        Class<?>[] parameters=
            method.getParameterTypes();

        for(int i=0;i<parameters.length;i++){
            if(i>0)
                result.append(',');

            result.append(
                parameters[i].getName()
            );
        }

        result.append(")->");
        result.append(
            method.getReturnType()
                .getName()
        );

        return result.toString();
    }

    private ContentRegistrarAuthorityCoverageTest(){}
}
