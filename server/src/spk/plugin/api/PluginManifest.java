package spk.plugin.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.TreeSet;

/** Immutable plugin identity, compatibility and dependency declaration. */
public final class PluginManifest {
    private final String id;
    private final String version;
    private final int apiVersion;
    private final java.util.List<String> dependencies;

    public PluginManifest(
        String id,
        String version,
        int apiVersion,
        Collection<String> dependencies
    ){
        this.id=canonicalId(id);
        this.version=requireText(
            version,
            "version"
        );

        if(apiVersion<=0)
            throw new IllegalArgumentException(
                "apiVersion"
            );

        this.apiVersion=apiVersion;

        TreeSet<String> normalized=
            new TreeSet<>();

        if(dependencies!=null)
            for(String dependency:dependencies){
                String clean=
                    canonicalId(dependency);

                if(clean.equals(this.id))
                    throw new IllegalArgumentException(
                        "plugin cannot depend on itself: "+
                        this.id
                    );

                normalized.add(clean);
            }

        this.dependencies=
            Collections.unmodifiableList(
                new ArrayList<>(normalized)
            );
    }

    public PluginManifest(
        String id,
        String version
    ){
        this(
            id,
            version,
            PluginApiVersion.CURRENT,
            Collections.<String>emptyList()
        );
    }

    public String id(){
        return id;
    }

    public String version(){
        return version;
    }

    public int apiVersion(){
        return apiVersion;
    }

    public java.util.List<String> dependencies(){
        return dependencies;
    }

    private static String canonicalId(
        String value
    ){
        String clean=
            requireText(
                value,
                "id"
            ).toLowerCase(
                Locale.ROOT
            );

        for(int i=0;i<clean.length();i++){
            char ch=clean.charAt(i);
            boolean accepted=
                ch>='a'&&ch<='z'||
                ch>='0'&&ch<='9'||
                ch=='.'||
                ch=='_'||
                ch=='-';

            if(!accepted)
                throw new IllegalArgumentException(
                    "plugin id character: "+
                    ch
                );
        }

        return clean;
    }

    private static String requireText(
        String value,
        String name
    ){
        String clean=
            value==null
                ?""
                :value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                name
            );

        return clean;
    }

    @Override public String toString(){
        return "PluginManifest{id="+id+
            ",version="+version+
            ",apiVersion="+apiVersion+
            ",dependencies="+dependencies+
            "}";
    }
}
