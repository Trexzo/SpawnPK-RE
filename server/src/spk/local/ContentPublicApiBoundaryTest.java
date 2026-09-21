package spk.local;

import java.lang.reflect.*;
import java.util.*;
import spk.content.api.*;

public final class ContentPublicApiBoundaryTest {
    private static final Class<?>[] API_TYPES={
        ContentCommandContext.class,
        ContentCommandHandler.class,
        ContentInteractionResult.class,
        ContentItemOnNpcContext.class,
        ContentItemOnNpcHandler.class,
        ContentItemOnObjectContext.class,
        ContentItemOnObjectHandler.class,
        ContentItemOptionContext.class,
        ContentItemOptionHandler.class,
        ContentModule.class,
        ContentNpcOptionContext.class,
        ContentNpcOptionHandler.class,
        ContentNpcOptionResult.class,
        ContentNpcService.class,
        ContentObjectOptionContext.class,
        ContentObjectOptionHandler.class,
        ContentPlayer.class,
        ContentPresentation.class,
        ContentPresentationException.class,
        ContentProvenance.class,
        ContentRegistrar.class,
        ContentResult.class,
        ContentSkill.class
    };

    private static final Set<String> FORBIDDEN_SIMPLE_NAMES=
        new HashSet<>(
            Arrays.asList(
                "Socket",
                "IsaacCipher",
                "ClientPacketProbe",
                "ServerPacketWriter",
                "ClientRequest",
                "WorldPlayer",
                "NpcEntity",
                "NpcRegistry"
            )
        );

    public static void main(String[] args){
        ArrayList<String> violations=
            new ArrayList<>();

        for(Class<?> api:API_TYPES){
            if(!Modifier.isPublic(api.getModifiers()))
                violations.add(
                    api.getName()+
                    " is not public"
                );

            for(Method method:
                    api.getDeclaredMethods()){
                if(!Modifier.isPublic(
                        method.getModifiers()))
                    continue;

                String location=
                    api.getName()+
                    "#"+
                    method.getName();

                if("sceneIndex".equals(
                        method.getName()))
                    violations.add(
                        location+
                        " exposes viewer-local scene identity"
                    );

                if("protocolIndex".equals(
                        method.getName()))
                    violations.add(
                        location+
                        " exposes raw protocol index"
                    );

                String methodName=
                    method.getName()
                        .toLowerCase(Locale.ROOT);

                if("percentageText".equals(
                        method.getName())||
                   methodName.contains("widget"))
                    violations.add(
                        location+
                        " exposes raw widget presentation identity"
                    );

                if(methodName.equals("slot")||
                   methodName.contains("opcode")||
                   methodName.contains("schema")||
                   methodName.contains("packet"))
                    violations.add(
                        location+
                        " exposes raw transport identity"
                    );

                inspect(
                    method.getGenericReturnType(),
                    location+
                    " return",
                    violations
                );

                Type[] parameters=
                    method.getGenericParameterTypes();

                for(int i=0;i<parameters.length;i++)
                    inspect(
                        parameters[i],
                        location+
                        " param["+
                        i+
                        "]",
                        violations
                    );

                for(Class<?> exceptionType:
                        method.getExceptionTypes())
                    inspectClass(
                        exceptionType,
                        location+
                        " throws",
                        violations
                    );
            }

            for(Field field:
                    api.getDeclaredFields()){
                if(!Modifier.isPublic(
                        field.getModifiers()))
                    continue;

                inspect(
                    field.getGenericType(),
                    api.getName()+
                    "#"+
                    field.getName()+
                    " field",
                    violations
                );
            }

            for(Constructor<?> constructor:
                    api.getDeclaredConstructors()){
                if(!Modifier.isPublic(
                        constructor.getModifiers()))
                    continue;

                Type[] parameters=
                    constructor
                        .getGenericParameterTypes();

                for(int i=0;i<parameters.length;i++)
                    inspect(
                        parameters[i],
                        api.getName()+
                        " ctor param["+
                        i+
                        "]",
                        violations
                    );
            }
        }

        if(!violations.isEmpty())
            throw new AssertionError(
                "public content API boundary violations="+
                violations
            );

        System.out.println(
            "CONTENT_PUBLIC_API_BOUNDARY_PASS "+
            "types="+API_TYPES.length+" "+
            "spkLocalLeak=false "+
            "javaNetLeak=false "+
            "transportTypeLeak=false "+
            "sceneIndex=false "+
            "protocolIndex=false "+
            "widgetIdentity=false "+
            "javaIoLeak=false"
        );
    }

    private static void inspect(
        Type type,
        String location,
        List<String> violations
    ){
        if(type instanceof Class<?>){
            inspectClass(
                (Class<?>)type,
                location,
                violations
            );
            return;
        }

        if(type instanceof ParameterizedType){
            ParameterizedType parameterized=
                (ParameterizedType)type;

            inspect(
                parameterized.getRawType(),
                location,
                violations
            );

            for(Type argument:
                    parameterized
                        .getActualTypeArguments())
                inspect(
                    argument,
                    location,
                    violations
                );

            return;
        }

        if(type instanceof GenericArrayType){
            inspect(
                ((GenericArrayType)type)
                    .getGenericComponentType(),
                location,
                violations
            );
            return;
        }

        if(type instanceof WildcardType){
            WildcardType wildcard=
                (WildcardType)type;

            for(Type upper:
                    wildcard.getUpperBounds())
                inspect(
                    upper,
                    location,
                    violations
                );

            for(Type lower:
                    wildcard.getLowerBounds())
                inspect(
                    lower,
                    location,
                    violations
                );

            return;
        }

        if(type instanceof TypeVariable<?>){
            for(Type bound:
                    ((TypeVariable<?>)type)
                        .getBounds())
                inspect(
                    bound,
                    location,
                    violations
                );
        }
    }

    private static void inspectClass(
        Class<?> type,
        String location,
        List<String> violations
    ){
        if(type.isArray()){
            inspectClass(
                type.getComponentType(),
                location,
                violations
            );
            return;
        }

        if(type.isPrimitive()||
           type==Void.TYPE)
            return;

        String name=type.getName();
        String simple=type.getSimpleName();

        if(name.startsWith("spk.local."))
            violations.add(
                location+
                " -> internal runtime type "+
                name
            );

        if(name.startsWith("java.net.")||
           name.startsWith("java.nio.channels."))
            violations.add(
                location+
                " -> network type "+
                name
            );

        if(name.startsWith("java.io."))
            violations.add(
                location+
                " -> transport I/O type "+
                name
            );

        if(FORBIDDEN_SIMPLE_NAMES.contains(
                simple))
            violations.add(
                location+
                " -> forbidden transport/runtime type "+
                name
            );
    }
}
