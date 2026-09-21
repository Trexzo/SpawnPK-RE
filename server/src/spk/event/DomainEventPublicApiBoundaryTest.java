package spk.event;

import java.lang.reflect.*;
import java.util.*;

public final class DomainEventPublicApiBoundaryTest {
    private static final Class<?>[] API_TYPES={
        DomainEventBus.class,
        DomainEventBus.Event.class,
        DomainEventBus.Cancellable.class,
        DomainEventBus.Priority.class,
        DomainEventBus.Listener.class,
        DomainEventBus.Subscription.class
    };

    private static final Set<String> FORBIDDEN_SIMPLE_NAMES=
        new HashSet<>(
            Arrays.asList(
                "Socket",
                "ServerSocket",
                "IsaacCipher",
                "ClientPacketProbe",
                "ClientRequest",
                "ClientRequestMetadata",
                "ServerPacketWriter",
                "World",
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
                    api.getName()+" is not public"
                );

            for(Method method:api.getDeclaredMethods()){
                if(!Modifier.isPublic(method.getModifiers()))
                    continue;

                String location=
                    api.getName()+"#"+method.getName();

                inspectName(
                    method.getName(),
                    location,
                    violations
                );

                inspect(
                    method.getGenericReturnType(),
                    location+" return",
                    violations
                );

                Type[] parameters=
                    method.getGenericParameterTypes();

                for(int i=0;i<parameters.length;i++)
                    inspect(
                        parameters[i],
                        location+" param["+i+"]",
                        violations
                    );

                for(Class<?> exceptionType:
                        method.getExceptionTypes())
                    inspectClass(
                        exceptionType,
                        location+" throws",
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
                        " ctor param["+i+"]",
                        violations
                    );
            }

            for(Field field:api.getDeclaredFields()){
                if(!Modifier.isPublic(field.getModifiers()))
                    continue;

                inspectName(
                    field.getName(),
                    api.getName()+"#"+field.getName(),
                    violations
                );

                inspect(
                    field.getGenericType(),
                    api.getName()+"#"+field.getName()+
                    " field",
                    violations
                );
            }
        }

        if(!violations.isEmpty())
            throw new AssertionError(
                "public domain-event API boundary violations="+
                violations
            );

        System.out.println(
            "DOMAIN_EVENT_PUBLIC_API_BOUNDARY_PASS "+
            "types="+API_TYPES.length+" "+
            "spkLocalLeak=false "+
            "networkLeak=false "+
            "transportTypeLeak=false "+
            "opcodeIdentity=false "+
            "widgetIdentity=false "+
            "sceneIndex=false "+
            "playerIndex=false"
        );
    }

    private static void inspectName(
        String name,
        String location,
        List<String> violations
    ){
        String lower=
            name.toLowerCase(Locale.ROOT);

        if(lower.contains("opcode")||
           lower.contains("schema")||
           lower.contains("packet")||
           lower.contains("widget")||
           lower.contains("sceneindex")||
           lower.contains("playerindex")||
           lower.contains("protocolindex")||
           lower.contains("isaac")||
           lower.contains("socket"))
            violations.add(
                location+
                " exposes raw transport/presentation identity"
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

        if(type instanceof TypeVariable<?>)
            for(Type bound:
                    ((TypeVariable<?>)type)
                        .getBounds())
                inspect(
                    bound,
                    location,
                    violations
                );
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
                " -> internal runtime type "+name
            );

        if(name.startsWith("java.net.")||
           name.startsWith("java.io.")||
           name.startsWith("java.nio.channels."))
            violations.add(
                location+
                " -> transport/network type "+name
            );

        if(FORBIDDEN_SIMPLE_NAMES.contains(simple))
            violations.add(
                location+
                " -> forbidden runtime/transport type "+
                name
            );
    }

    private DomainEventPublicApiBoundaryTest(){}
}
