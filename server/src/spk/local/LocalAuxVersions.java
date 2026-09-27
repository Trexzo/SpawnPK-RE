package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

final class LocalAuxVersions {
    static final int MAX_ACCEPTED_LENGTH=
        16_384;

    static byte[] read(
        Path path,
        byte[] fallback
    ){
        Objects.requireNonNull(
            path,
            "path"
        );
        Objects.requireNonNull(
            fallback,
            "fallback"
        );

        try{
            if(!Files.isRegularFile(path))
                return fallback.clone();

            try(InputStream in=
                    Files.newInputStream(
                        path
                    )){
                return read(
                    in,
                    fallback
                );
            }
        }catch(IOException|
               SecurityException ignored){
            return fallback.clone();
        }
    }

    static byte[] read(
        InputStream in,
        byte[] fallback
    ){
        Objects.requireNonNull(
            in,
            "in"
        );
        Objects.requireNonNull(
            fallback,
            "fallback"
        );

        try{
            byte[] data=
                new byte[
                    MAX_ACCEPTED_LENGTH
                ];
            int total=0;

            while(total<
                    MAX_ACCEPTED_LENGTH){
                int count=
                    in.read(
                        data,
                        total,
                        MAX_ACCEPTED_LENGTH-
                            total
                    );

                if(count<0)
                    return total==0
                        ?fallback.clone()
                        :Arrays.copyOf(
                            data,
                            total
                        );

                if(count==0){
                    int value=in.read();

                    if(value<0)
                        return total==0
                            ?fallback.clone()
                            :Arrays.copyOf(
                                data,
                                total
                            );

                    data[total++]=
                        (byte)value;
                    continue;
                }

                total+=count;
            }

            // The established policy accepts only lengths strictly smaller than
            // 16,384 bytes. Reaching the ceiling is sufficient to reject without
            // reading any further caller-owned bytes.
            return fallback.clone();
        }catch(IOException|
               SecurityException ignored){
            return fallback.clone();
        }
    }

    private LocalAuxVersions(){}
}
