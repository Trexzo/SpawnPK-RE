package spk.local;

import java.util.function.Supplier;

final class PluginThreadContext {
    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    interface CheckedAction {
        void run() throws Exception;
    }

    static <T> T call(
        ClassLoader loader,
        CheckedSupplier<T> action
    )throws Exception{
        Thread thread=
            Thread.currentThread();
        ClassLoader previous=
            thread.getContextClassLoader();

        if(previous!=loader)
            thread.setContextClassLoader(
                loader
            );

        try{
            return action.get();
        }finally{
            if(previous!=loader)
                thread.setContextClassLoader(
                    previous
                );
        }
    }

    static void run(
        ClassLoader loader,
        CheckedAction action
    )throws Exception{
        call(
            loader,
            ()->{
                action.run();
                return null;
            }
        );
    }

    static <T> T callUnchecked(
        ClassLoader loader,
        Supplier<T> action
    ){
        Thread thread=
            Thread.currentThread();
        ClassLoader previous=
            thread.getContextClassLoader();

        if(previous!=loader)
            thread.setContextClassLoader(
                loader
            );

        try{
            return action.get();
        }finally{
            if(previous!=loader)
                thread.setContextClassLoader(
                    previous
                );
        }
    }

    static void runUnchecked(
        ClassLoader loader,
        Runnable action
    ){
        callUnchecked(
            loader,
            ()->{
                action.run();
                return null;
            }
        );
    }

    private PluginThreadContext(){}
}
