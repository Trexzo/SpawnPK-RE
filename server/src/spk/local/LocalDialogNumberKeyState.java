package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Runtime sidecar used by the LocalLab client helper for classic-dialog
 * 1..9 keyboard shortcuts. This is presentation state only.
 */
final class LocalDialogNumberKeyState {
    private final Path file;

    LocalDialogNumberKeyState(){
        this(Paths.get(
            "server",
            "data",
            "locallab_dialog_keys.properties"
        ));
    }

    LocalDialogNumberKeyState(Path file){
        this.file=Objects.requireNonNull(file,"file");
    }

    Path file(){
        return file;
    }

    void publish(int... widgets){
        try{
            Path parent=file.getParent();
            if(parent!=null)
                Files.createDirectories(parent);

            StringBuilder ids=new StringBuilder();

            if(widgets!=null){
                for(int i=0;i<widgets.length&&i<9;i++){
                    if(i>0)ids.append(',');
                    ids.append(widgets[i]);
                }
            }

            String body=
                "active=true\n"+
                "widgets="+ids+"\n"+
                "updated="+System.currentTimeMillis()+"\n";

            Path tmp=file.resolveSibling(
                file.getFileName().toString()+".tmp"
            );

            Files.write(
                tmp,
                body.getBytes(StandardCharsets.UTF_8)
            );

            try{
                Files.move(
                    tmp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );
            }catch(AtomicMoveNotSupportedException ex){
                Files.move(
                    tmp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
        }catch(Throwable t){
            System.err.println(
                "LOCALLAB_DIALOG_NUMBER_KEYS_STATE_WRITE_FAILED "+
                t
            );
        }
    }

    void clear(){
        try{
            if(Files.exists(file)){
                Files.write(
                    file,
                    "active=false\nwidgets=\n".getBytes(
                        StandardCharsets.UTF_8
                    )
                );
            }
        }catch(Throwable t){
            System.err.println(
                "LOCALLAB_DIALOG_NUMBER_KEYS_STATE_CLEAR_FAILED "+
                t
            );
        }
    }
}
