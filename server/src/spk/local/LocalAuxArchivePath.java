package spk.local;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

final class LocalAuxArchivePath {
    interface UserHomeSource {
        String get();
    }

    static Path resolve(
        String target
    ){
        return resolve(
            target,
            ()->System.getProperty(
                "user.home"
            )
        );
    }

    static Path resolve(
        String target,
        UserHomeSource homeSource
    ){
        Objects.requireNonNull(
            target,
            "target"
        );
        Objects.requireNonNull(
            homeSource,
            "homeSource"
        );

        final String name;

        if(target.endsWith(
                "/cache.zip"))
            name="cache.zip";
        else if(target.endsWith(
                "/sprites.zip"))
            name="sprites.zip";
        else if(target.endsWith(
                "/configs.zip"))
            name="configs.zip";
        else
            return null;

        final String home;

        try{
            home=
                homeSource.get();

            if(home==null||
               home.isEmpty())
                return null;

            return Paths.get(
                home,
                ".spawnpk",
                name
            );
        }catch(InvalidPathException|
               SecurityException expected){
            return null;
        }
    }

    private LocalAuxArchivePath(){}
}
