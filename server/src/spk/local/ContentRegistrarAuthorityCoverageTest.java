package spk.local;

import java.lang.reflect.*;
import java.util.*;
import spk.content.api.*;

public final class ContentRegistrarAuthorityCoverageTest {
    private static final Set<String> EXPECTED=
        Collections.unmodifiableSet(
            new TreeSet<>(
                Arrays.asList(
                    "command(String,int,ContentCommandHandler)->ContentRegistration",
                    "itemOnGroundItem(int,int,int,ContentItemOnGroundItemHandler)->ContentRegistration",
                    "itemOnItem(int,int,int,ContentItemOnItemHandler)->ContentRegistration",
                    "itemOnNpc(int,int,int,ContentItemOnNpcHandler)->ContentRegistration",
                    "itemOnObject(int,int,int,ContentItemOnObjectHandler)->ContentRegistration",
                    "itemOnPlayer(int,int,ContentItemOnPlayerHandler)->ContentRegistration",
                    "itemOption(int,int,int,ContentItemOptionHandler)->ContentRegistration",
                    "npcOption(int,int,int,ContentNpcOptionHandler)->ContentRegistration",
                    "objectOption(int,int,int,ContentObjectOptionHandler)->ContentRegistration"
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

        if(!violations.isEmpty())
            throw new AssertionError(
                "content registrar authority coverage violations="+
                violations
            );

        System.out.println(
            "CONTENT_REGISTRAR_AUTHORITY_COVERAGE_PASS "+
            "methods="+EXPECTED.size()+" "+
            "returnsRegistration=true "+
            "failClosed=true"
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
                parameters[i].getSimpleName()
            );
        }

        result.append(")->");
        result.append(
            method.getReturnType()
                .getSimpleName()
        );

        return result.toString();
    }

    private ContentRegistrarAuthorityCoverageTest(){}
}
