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
 * Bounded reader for caller-controlled JAR manifest authority.
 *
 * Admission uses a verification-disabled JarFile so signature verifier setup
 * cannot parse the full manifest before these project resource caps run.
 *
 * The complete expanded manifest is retained only up to MAX_MANIFEST_BYTES and
 * parsed once with java.util.jar.Manifest, preserving fail-fast syntax
 * validation for both main and named sections. The main-section byte count is
 * tracked independently and capped at MAX_MAIN_SECTION_BYTES.
 */
final class BoundedManifestMain {
    /**
     * Project aggregate main-section cap. This intentionally exceeds the JAR
     * specification's required 65,535-byte single-header-value compatibility.
     */
    static final int MAX_MAIN_SECTION_BYTES=
        256*1024;

    /**
     * Project cap for the complete expanded manifest entry presented later to
     * URLClassLoader/JarFile runtime parsing.
     */
    static final int MAX_MANIFEST_BYTES=
        4*1024*1024;

    private static final String MANIFEST_ENTRY=
        "META-INF/MANIFEST.MF";

    static Attributes readMainAttributes(
        JarFile jar,
        Path archive
    )throws IOException{
        JarEntry entry=
            findManifestEntry(
                jar,
                archive
            );

        if(entry==null)
            return null;

        byte[] bytes;

        try(InputStream input=
                jar.getInputStream(
                    entry
                )){
            bytes=
                readBoundedManifest(
                    input,
                    archive
                );
        }

        final Manifest manifest;

        try{
            manifest=
                new Manifest(
                    new ByteArrayInputStream(
                        bytes
                    )
                );
        }catch(IOException invalid){
            throw new IllegalArgumentException(
                "plugin manifest syntax is invalid: "+
                archive,
                invalid
            );
        }

        return manifest
            .getMainAttributes();
    }

    private static JarEntry findManifestEntry(
        JarFile jar,
        Path archive
    ){
        JarEntry found=null;
        java.util.Enumeration<JarEntry> entries=
            jar.entries();

        while(entries.hasMoreElements()){
            JarEntry candidate=
                entries.nextElement();

            if(candidate.isDirectory())
                continue;

            String name=
                candidate.getName()
                    .replace(
                        '\\',
                        '/'
                    );

            if(!name.equalsIgnoreCase(
                    MANIFEST_ENTRY))
                continue;

            if(found!=null)
                throw new IllegalArgumentException(
                    "plugin archive contains ambiguous manifest authority: "+
                    archive
                );

            found=candidate;
        }

        return found;
    }

    private static byte[] readBoundedManifest(
        InputStream input,
        Path archive
    )throws IOException{
        ByteArrayOutputStream full=
            new ByteArrayOutputStream(
                8192
            );
        byte[] buffer=
            new byte[8192];
        int total=0;
        int mainBytes=0;
        boolean mainComplete=false;
        boolean lineHasContent=false;
        boolean pendingCr=false;

        for(;;){
            int read=
                input.read(
                    buffer
                );

            if(read<0)
                return full
                    .toByteArray();

            for(int index=0;
                index<read;
                index++){
                int value=
                    buffer[index]&0xff;

                total++;

                if(total>
                        MAX_MANIFEST_BYTES)
                    throw new IllegalArgumentException(
                        "plugin manifest exceeds "+
                        MAX_MANIFEST_BYTES+
                        " expanded bytes: "+
                        archive
                    );

                full.write(
                    value
                );

                if(mainComplete)
                    continue;

                mainBytes++;

                if(mainBytes>
                        MAX_MAIN_SECTION_BYTES)
                    throw new IllegalArgumentException(
                        "plugin manifest main section exceeds "+
                        MAX_MAIN_SECTION_BYTES+
                        " bytes: "+
                        archive
                    );

                if(pendingCr){
                    if(value=='\n'){
                        pendingCr=false;

                        if(!lineHasContent)
                            mainComplete=true;
                        else
                            lineHasContent=false;

                        continue;
                    }

                    pendingCr=false;

                    if(!lineHasContent){
                        mainComplete=true;
                        continue;
                    }

                    lineHasContent=false;
                }

                if(value=='\r'){
                    pendingCr=true;
                }else if(value=='\n'){
                    if(!lineHasContent)
                        mainComplete=true;
                    else
                        lineHasContent=false;
                }else{
                    lineHasContent=true;
                }
            }
        }
    }

    private BoundedManifestMain(){}
}
