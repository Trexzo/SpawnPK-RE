package spk.local;

import java.lang.reflect.*;
import java.util.*;
import spk.content.api.*;

public final class ContentHandlerSamCompatibilityTest {
    private static final class Contract {
        final Class<?> handler;
        final Class<?> context;
        final Class<?> result;

        Contract(
            Class<?> handler,
            Class<?> context,
            Class<?> result
        ){
            this.handler=handler;
            this.context=context;
            this.result=result;
        }
    }

    private static final Contract[] CONTRACTS={
        new Contract(
            ContentCommandHandler.class,
            ContentCommandContext.class,
            ContentResult.class
        ),
        new Contract(
            ContentObjectOptionHandler.class,
            ContentObjectOptionContext.class,
            ContentInteractionResult.class
        ),
        new Contract(
            ContentItemOptionHandler.class,
            ContentItemOptionContext.class,
            ContentInteractionResult.class
        ),
        new Contract(
            ContentItemOnNpcHandler.class,
            ContentItemOnNpcContext.class,
            ContentInteractionResult.class
        ),
        new Contract(
            ContentItemOnGroundItemHandler.class,
            ContentItemOnGroundItemContext.class,
            ContentInteractionResult.class
        ),
        new Contract(
            ContentItemOnItemHandler.class,
            ContentItemOnItemContext.class,
            ContentInteractionResult.class
        ),
        new Contract(
            ContentItemOnObjectHandler.class,
            ContentItemOnObjectContext.class,
            ContentInteractionResult.class
        ),
        new Contract(
            ContentItemOnPlayerHandler.class,
            ContentItemOnPlayerContext.class,
            ContentInteractionResult.class
        ),
        new Contract(
            ContentNpcOptionHandler.class,
            ContentNpcOptionContext.class,
            ContentNpcOptionResult.class
        )
    };

    public static void main(String[] args){
        ArrayList<String> violations=
            new ArrayList<>();

        for(Contract contract:CONTRACTS)
            inspect(
                contract,
                violations
            );

        if(!violations.isEmpty())
            throw new AssertionError(
                "content handler SAM compatibility violations="+
                violations
            );

        System.out.println(
            "CONTENT_HANDLER_SAM_COMPATIBILITY_PASS "+
            "handlers="+CONTRACTS.length+" "+
            "singleAbstractMethod=true "+
            "semanticSignature=true"
        );
    }

    private static void inspect(
        Contract contract,
        List<String> violations
    ){
        Class<?> handler=contract.handler;

        if(!handler.isInterface())
            violations.add(
                handler.getName()+
                " is not an interface"
            );

        if(!Modifier.isPublic(
                handler.getModifiers()))
            violations.add(
                handler.getName()+
                " is not public"
            );

        ArrayList<Method> abstractMethods=
            new ArrayList<>();

        for(Method method:
                handler.getDeclaredMethods()){
            int modifiers=method.getModifiers();

            if(Modifier.isPublic(modifiers)&&
               Modifier.isAbstract(modifiers)&&
               !Modifier.isStatic(modifiers)&&
               !method.isSynthetic()&&
               !method.isBridge())
                abstractMethods.add(method);
        }

        if(abstractMethods.size()!=1){
            violations.add(
                handler.getName()+
                " abstractMethodCount="+
                abstractMethods.size()
            );
            return;
        }

        Method method=abstractMethods.get(0);

        if(!"handle".equals(method.getName()))
            violations.add(
                handler.getName()+
                " SAM method changed name to "+
                method.getName()
            );

        Class<?>[] parameters=
            method.getParameterTypes();

        if(parameters.length!=1||
           parameters[0]!=contract.context)
            violations.add(
                handler.getName()+
                " context signature changed expected="+
                contract.context.getName()+
                " actual="+
                Arrays.toString(parameters)
            );

        if(method.getReturnType()!=contract.result)
            violations.add(
                handler.getName()+
                " result type changed expected="+
                contract.result.getName()+
                " actual="+
                method.getReturnType().getName()
            );
    }

    private ContentHandlerSamCompatibilityTest(){}
}
