package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Bounded reader for caller-controlled JAR manifest main attributes.
 *
 * Only the main section is authority for the plugin Class-Path policy. The
 * reader stops at the first terminating blank line, so later named sections
 * are never consumed. Expanded main-section bytes are bounded independently
 * of ZIP metadata.
 */
final class BoundedManifestMain {
    static final int MAX_MAIN_SECTION_BYTES=
        64*1024;

    static Attributes readMainAttributes(
        JarFile jar,
        Path archive
    )throws IOException{
        JarEntry entry=
            jar.getJarEntry(
                "META-INF/MANIFEST.MF"
            );

        if(entry==null)
            return null;

        byte[] main;

        try(InputStream input=
                jar.getInputStream(
                    entry
                )){
            main=
                readMainSection(
                    input,
                    archive
                );
        }

        Manifest manifest=
            new Manifest(
                new ByteArrayInputStream(
                    main
                )
            );

        return manifest
            .getMainAttributes();
    }

    private static byte[] readMainSection(
        InputStream input,
        Path archive
    )throws IOException{
        ByteArrayOutputStream output=
            new ByteArrayOutputStream(
                Math.min(
                    8192,
                    MAX_MAIN_SECTION_BYTES
                )
            );
        byte[] buffer=
            new byte[8192];
        boolean lineHasContent=false;
        boolean pendingCr=false;

        for(;;){
            int read=
                input.read(
                    buffer
                );

            if(read<0)
                return output
                    .toByteArray();

            for(int index=0;
                index<read;
                index++){
                int value=
                    buffer[index]&0xff;

                if(pendingCr){
                    if(value=='\n'){
                        append(
                            output,
                            value,
                            archive
                        );
                        pendingCr=false;

                        if(!lineHasContent)
                            return output
                                .toByteArray();

                        lineHasContent=false;
                        continue;
                    }

                    pendingCr=false;

                    if(!lineHasContent)
                        return output
                            .toByteArray();

                    lineHasContent=false;
                }

                append(
                    output,
                    value,
                    archive
                );

                if(value=='\r'){
                    pendingCr=true;
                }else if(value=='\n'){
                    if(!lineHasContent)
                        return output
                            .toByteArray();

                    lineHasContent=false;
                }else{
                    lineHasContent=true;
                }
            }
        }
    }

    private static void append(
        ByteArrayOutputStream output,
        int value,
        Path archive
    ){
        if(output.size()>=
                MAX_MAIN_SECTION_BYTES)
            throw new IllegalArgumentException(
                "plugin manifest main section exceeds "+
                MAX_MAIN_SECTION_BYTES+
                " bytes: "+
                archive
            );

        output.write(
            value
        );
    }

    private BoundedManifestMain(){}
}
