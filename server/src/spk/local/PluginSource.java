package spk.local;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable server-owned plugin source request. */
final class PluginSource {
    private final Path path;
    private final String entrypoint;
    private final String scriptText;

    private PluginSource(
        Path path,
        String entrypoint,
        String scriptText
    ){
        this.path=
            Objects.requireNonNull(
                path,
                "path"
            ).toAbsolutePath()
                .normalize();

        String clean=
            entrypoint==null
                ?null
                :entrypoint.trim();

        this.entrypoint=
            clean==null||clean.isEmpty()
                ?null
                :clean;
        this.scriptText=scriptText;
    }

    static PluginSource of(
        Path path,
        String entrypoint
    ){
        return new PluginSource(
            path,
            entrypoint,
            null
        );
    }

    /**
     * Path-only script source used for loader capability/routing checks.
     * Executing loaders may require an admitted immutable snapshot.
     */
    static PluginSource script(
        Path path
    ){
        return new PluginSource(
            path,
            null,
            null
        );
    }

    static PluginSource scriptSnapshot(
        Path path,
        String scriptText
    ){
        return new PluginSource(
            path,
            null,
            Objects.requireNonNull(
                scriptText,
                "scriptText"
            )
        );
    }

    Path path(){
        return path;
    }

    String entrypoint(){
        return entrypoint;
    }

    boolean hasEntrypoint(){
        return entrypoint!=null;
    }

    boolean hasScriptSnapshot(){
        return scriptText!=null;
    }

    String requireScriptText(){
        if(scriptText==null)
            throw new IllegalArgumentException(
                "plugin script source snapshot is required: "+
                path
            );

        return scriptText;
    }

    String requireEntrypoint(){
        if(entrypoint==null)
            throw new IllegalArgumentException(
                "plugin entrypoint is required for source: "+
                path
            );

        return entrypoint;
    }
}
