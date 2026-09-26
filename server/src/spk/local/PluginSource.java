package spk.local;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable server-owned plugin source request. */
final class PluginSource {
    private final Path path;
    private final String entrypoint;

    private PluginSource(
        Path path,
        String entrypoint
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
    }

    static PluginSource of(
        Path path,
        String entrypoint
    ){
        return new PluginSource(
            path,
            entrypoint
        );
    }

    static PluginSource script(
        Path path
    ){
        return new PluginSource(
            path,
            null
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

    String requireEntrypoint(){
        if(entrypoint==null)
            throw new IllegalArgumentException(
                "plugin entrypoint is required for source: "+
                path
            );

        return entrypoint;
    }
}
