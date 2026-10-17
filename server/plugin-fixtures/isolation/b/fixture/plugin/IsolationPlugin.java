package fixture.plugin;

import fixture.privatepkg.Version;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import spk.content.api.ContentResult;
import spk.event.DomainEventBus;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginApiVersion;
import spk.plugin.api.PluginContext;
import spk.plugin.api.PluginManifest;

public final class IsolationPlugin
    implements Plugin {

    private final boolean constructorTccl=
        tccl();
    private volatile boolean manifestTccl;
    private volatile boolean enableTccl;
    private volatile boolean apiIdentity;
    private volatile boolean serverInternalDenied;
    private volatile String privateResource;
    private volatile String privateResourceEnumeration;
    private volatile boolean commandTccl;
    private volatile boolean throwTccl;
    private volatile boolean eventTccl;
    private volatile boolean taskTccl;
    private volatile boolean disableTccl;

    public IsolationPlugin(){}

    @Override public PluginManifest manifest(){
        manifestTccl=tccl();

        return new PluginManifest(
            "isolation.b",
            "1.0",
            PluginApiVersion.CURRENT,
            java.util.Collections
                .<String>emptyList()
        );
    }

    @Override public void enable(
        PluginContext context
    )throws Exception{
        enableTccl=tccl();
        apiIdentity=
            Plugin.class==
                Class.forName(
                    "spk.plugin.api.Plugin",
                    false,
                    getClass()
                        .getClassLoader()
                );
        serverInternalDenied=
            serverInternalDenied();
        privateResource=
            privateResource();
        privateResourceEnumeration=
            privateResourceEnumeration();

        context.content()
            .command(
                "isolationb",
                100,
                command->{
                    commandTccl=tccl();
                    return ContentResult
                        .handled(
                            Version.value(),
                            null
                        );
                }
            );

        context.content()
            .command(
                "isolationthrowb",
                100,
                command->{
                    throwTccl=tccl();
                    System.setProperty(
                        "spawnpk.fixture.isolation.b.throwTccl",
                        Boolean.toString(
                            throwTccl
                        )
                    );
                    throw new IllegalStateException(
                        "fixture-throw-"+
                        Version.value()
                    );
                }
            );

        context.events()
            .subscribe(
                ProbeEvent.class,
                DomainEventBus.Priority.NORMAL,
                event->eventTccl=tccl()
            );

        context.scheduler()
            .schedule(
                1L,
                ()->taskTccl=tccl()
            );
    }

    @Override public void disable(){
        disableTccl=tccl();
        System.setProperty(
            "spawnpk.fixture.isolation.b.disableTccl",
            Boolean.toString(
                disableTccl
            )
        );
    }

    public String report(){
        return Version.value()+
            "|constructor="+
            constructorTccl+
            "|manifest="+
            manifestTccl+
            "|enable="+
            enableTccl+
            "|apiIdentity="+
            apiIdentity+
            "|serverInternalDenied="+
            serverInternalDenied+
            "|resource="+
            privateResource+
            "|resourceEnumeration="+
            privateResourceEnumeration+
            "|command="+
            commandTccl+
            "|throw="+
            throwTccl+
            "|event="+
            eventTccl+
            "|task="+
            taskTccl+
            "|disable="+
            disableTccl;
    }

    private boolean tccl(){
        return Thread.currentThread()
                .getContextClassLoader()==
            getClass()
                .getClassLoader();
    }

    private String privateResource()
        throws Exception{
        ClassLoader contextLoader=
            Thread.currentThread()
                .getContextClassLoader();

        try(InputStream input=
                contextLoader
                    .getResourceAsStream(
                        "fixture/privatepkg/value.txt"
                    )){
            if(input==null)
                return "MISSING";

            return new String(
                input.readAllBytes(),
                StandardCharsets.UTF_8
            ).trim();
        }
    }

    private String privateResourceEnumeration()
        throws Exception{
        ClassLoader contextLoader=
            Thread.currentThread()
                .getContextClassLoader();
        Enumeration<URL> resources=
            contextLoader.getResources(
                "fixture/privatepkg/value.txt"
            );

        if(!resources.hasMoreElements())
            return "MISSING#0";

        URL first=
            resources.nextElement();
        String value;

        try(InputStream input=
                first.openStream()){
            value=
                new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
                ).trim();
        }

        int count=1;

        while(resources.hasMoreElements()){
            resources.nextElement();
            count++;
        }

        return value+
            "#"+
            count;
    }

    private boolean serverInternalDenied(){
        try{
            Class.forName(
                "spk.local.World",
                false,
                getClass()
                    .getClassLoader()
            );
            return false;
        }catch(ClassNotFoundException expected){
            return true;
        }
    }

    public static final class ProbeEvent
        implements DomainEventBus.Event {
        public ProbeEvent(){}
    }
}
