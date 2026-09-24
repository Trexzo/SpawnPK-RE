package spk.local;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.ContentActionResult;
import spk.content.api.ContentModule;
import spk.content.api.ContentRegistrar;
import spk.content.api.ContentRegistration;
import spk.content.api.ContentResult;

/**
 * Regression for the module-install transaction boundary: a registrar is valid
 * only while ContentModule.register(...) is executing.
 */
public final class ContentRegistrarSealTest {
    public static void main(String[] args){
        World world=World.isolatedForTest(20L);

        try{
            ContentRegistry registry=world.content();

            AtomicReference<ContentRegistrar> retained=
                new AtomicReference<>();
            AtomicReference<ContentRegistration> initial=
                new AtomicReference<>();

            registry.installCustom(
                module(
                    "seal-committed",
                    registrar->{
                        retained.set(registrar);
                        initial.set(
                            registrar.command(
                                "sealinitial",
                                1,
                                context->
                                    ContentResult.handled(
                                        "INITIAL",
                                        null
                                    )
                            )
                        );
                    }
                )
            );

            require(
                retained.get()!=null&&
                initial.get()!=null&&
                initial.get().active(),
                "committed registration missing"
            );

            assertLateRejected(
                retained.get(),
                registry,
                "seallatecommitted"
            );

            registry.installCustom(
                module(
                    "seal-conflict-base",
                    registrar->
                        registrar.command(
                            "sealconflict",
                            1,
                            context->
                                ContentResult.handled(
                                    "BASE",
                                    null
                                )
                        )
                )
            );

            AtomicReference<ContentRegistrar> failedRegistrar=
                new AtomicReference<>();
            boolean conflict=false;

            try{
                registry.installCustom(
                    module(
                        "seal-conflict-other",
                        registrar->{
                            failedRegistrar.set(registrar);
                            registrar.command(
                                "sealconflict",
                                1,
                                context->
                                    ContentResult.handled(
                                        "CONFLICT",
                                        null
                                    )
                            );
                        }
                    )
                );
            }catch(IllegalStateException expected){
                conflict=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "content binding conflict"
                    );
            }

            require(
                conflict&&
                failedRegistrar.get()!=null,
                "failed-install fixture missing"
            );

            assertLateRejected(
                failedRegistrar.get(),
                registry,
                "seallatefailed"
            );

            AtomicReference<ContentRegistrar> concurrentRegistrar=
                new AtomicReference<>();
            AtomicReference<ContentRegistration> concurrentHandle=
                new AtomicReference<>();
            AtomicReference<Throwable> concurrentFailure=
                new AtomicReference<>();
            CountDownLatch registrarHeld=
                new CountDownLatch(1);
            CountDownLatch proceed=
                new CountDownLatch(1);

            registry.installCustom(
                module(
                    "seal-concurrent",
                    registrar->{
                        concurrentRegistrar.set(registrar);

                        Thread worker=
                            new Thread(
                                ()->{
                                    synchronized(registrar){
                                        registrarHeld.countDown();
                                        await(proceed);

                                        try{
                                            concurrentHandle.set(
                                                registrar.command(
                                                    "sealinflight",
                                                    1,
                                                    context->
                                                        ContentResult.handled(
                                                            "IN_FLIGHT",
                                                            null
                                                        )
                                                )
                                            );
                                        }catch(Throwable failure){
                                            concurrentFailure.set(
                                                failure
                                            );
                                        }
                                    }
                                },
                                "content-registrar-seal"
                            );

                        worker.start();
                        await(registrarHeld);
                        proceed.countDown();
                    }
                )
            );

            require(
                concurrentFailure.get()==null&&
                concurrentHandle.get()!=null&&
                concurrentHandle.get().active()&&
                registry.commandBinding(
                    "sealinflight"
                )!=null,
                "in-flight registration lost at seal boundary failure="+
                concurrentFailure.get()
            );

            assertLateRejected(
                concurrentRegistrar.get(),
                registry,
                "seallateconcurrent"
            );

            System.out.println(
                "CONTENT_REGISTRAR_SEAL_PASS "+
                "lateAfterCommitRejected=true "+
                "lateAfterRollbackRejected=true "+
                "inFlightBeforeSealCommitted=true "+
                "postInstallMutation=false"
            );
        }finally{
            world.close();
        }
    }

    private static void assertLateRejected(
        ContentRegistrar registrar,
        ContentRegistry registry,
        String command
    ){
        boolean rejected=false;

        try{
            registrar.command(
                command,
                1,
                context->
                    ContentResult.handled(
                        "SHOULD_NOT_REGISTER",
                        null
                    )
            );
        }catch(IllegalStateException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "content registrar closed"
                );
        }

        require(
            rejected&&
            registry.commandBinding(command)==null,
            "late registrar mutation accepted "+
            command
        );

        String action=command+":action";
        boolean actionRejected=false;

        try{
            registrar.action(
                action,
                1,
                context->
                    ContentActionResult.allow()
            );
        }catch(IllegalStateException expected){
            actionRejected=
                expected.getMessage()!=null&&
                expected.getMessage().contains(
                    "content registrar closed"
                );
        }

        require(
            actionRejected&&
            registry.actionBinding(action)==null,
            "late action registrar mutation accepted "+
            action
        );
    }

    private static void await(
        CountDownLatch latch
    ){
        boolean interrupted=false;

        for(;;){
            try{
                if(!latch.await(
                        5,
                        TimeUnit.SECONDS))
                    throw new AssertionError(
                        "registrar seal latch timeout"
                    );
                break;
            }catch(InterruptedException error){
                interrupted=true;
            }
        }

        if(interrupted)
            Thread.currentThread()
                .interrupt();
    }

    private interface ModuleBody{
        void register(ContentRegistrar registrar);
    }

    private static ContentModule module(
        String id,
        ModuleBody body
    ){
        return new ContentModule(){
            @Override public String id(){
                return id;
            }

            @Override public void register(
                ContentRegistrar registrar
            ){
                body.register(registrar);
            }
        };
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private ContentRegistrarSealTest(){}
}
