package spk.local;

import java.lang.reflect.*;
import java.util.*;
import spk.content.api.*;

public final class ContentHandlerSamCompatibilityTest {
    public interface InheritedExtraAbstractMethod {
        void audit();
    }

    public interface InheritedExtraHandler
        extends ContentCommandHandler,
                InheritedExtraAbstractMethod {
    }

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

        assertInheritedSecondAbstractMethodRejected(
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
            "semanticSignature=true "+
            "inheritedExtraRejected=true"
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

        LinkedHashMap<String,Method> abstractMethods=
            new LinkedHashMap<>();

        for(Method method:
                handler.getMethods()){
            int modifiers=method.getModifiers();

            if(!Modifier.isPublic(modifiers)||
               !Modifier.isAbstract(modifiers)||
               Modifier.isStatic(modifiers)||
               method.isSynthetic()||
               method.isBridge()||
               isPublicObjectMethod(method))
                continue;

            abstractMethods.put(
                erasedSignature(method),
                method
            );
        }

        if(abstractMethods.size()!=1){
            violations.add(
                handler.getName()+
                " abstractMethodCount="+
                abstractMethods.size()+
                " methods="+
                abstractMethods.keySet()
            );
            return;
        }

        String expectedSignature=
            "handle("+
            contract.context.getName()+
            ")";

        if(!abstractMethods.containsKey(
                expectedSignature)){
            violations.add(
                handler.getName()+
                " SAM signature changed expected="+
                expectedSignature+
                " actual="+
                abstractMethods.keySet()
            );
            return;
        }

        Method method;

        try{
            method=handler.getMethod(
                "handle",
                contract.context
            );
        }catch(NoSuchMethodException error){
            violations.add(
                handler.getName()+
                " expected handle method missing"
            );
            return;
        }

        if(method.getReturnType()!=contract.result)
            violations.add(
                handler.getName()+
                " result type changed expected="+
                contract.result.getName()+
                " actual="+
                method.getReturnType().getName()
            );
    }

    private static void assertInheritedSecondAbstractMethodRejected(
        List<String> violations
    ){
        ArrayList<String> synthetic=
            new ArrayList<>();

        inspect(
            new Contract(
                InheritedExtraHandler.class,
                ContentCommandContext.class,
                ContentResult.class
            ),
            synthetic
        );

        if(synthetic.size()!=1||
           !synthetic.get(0).contains(
               "abstractMethodCount=2"))
            violations.add(
                "inherited second abstract method escaped SAM guard "+
                synthetic
            );
    }

    private static String erasedSignature(
        Method method
    ){
        StringBuilder result=
            new StringBuilder(
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

        result.append(')');
        return result.toString();
    }

    private static boolean isPublicObjectMethod(
        Method method
    ){
        try{
            Method objectMethod=
                Object.class.getMethod(
                    method.getName(),
                    method.getParameterTypes()
                );

            return Modifier.isPublic(
                objectMethod.getModifiers()
            );
        }catch(NoSuchMethodException ignored){
            return false;
        }
    }

    private ContentHandlerSamCompatibilityTest(){}
}
