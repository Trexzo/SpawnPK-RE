package spk.local;

import java.io.*;
import spk.content.api.*;

public final class ContentPresentationFailureBoundaryTest {
    public static void main(String[] args){
        OutputStream failing=new OutputStream(){
            @Override public void write(int b)throws IOException{
                throw new IOException("synthetic transport failure");
            }

            @Override public void write(
                byte[] b,
                int off,
                int len
            )throws IOException{
                throw new IOException("synthetic transport failure");
            }
        };

        ServerPacketWriter writer=
            new ServerPacketWriter(
                failing,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        ContentPresentation presentation=
            ContentRuntimeAdapters.presentation(
                writer
            );

        try{
            presentation.runEnergy(100);
            throw new AssertionError(
                "presentation failure did not propagate"
            );
        }catch(ContentPresentationException e){
            if(!(e.getCause() instanceof IOException))
                throw new AssertionError(
                    "cause="+e.getCause()
                );

            if(!e.getMessage().contains(
                    "runEnergy"
                ))
                throw new AssertionError(
                    "message="+e.getMessage()
                );
        }catch(Throwable t){
            throw new AssertionError(
                "raw transport failure escaped as "+
                t.getClass().getName(),
                t
            );
        }

        System.out.println(
            "CONTENT_PRESENTATION_FAILURE_BOUNDARY_PASS "+
            "rawIOException=false "+
            "semanticFailure=true "+
            "causeRetained=true"
        );
    }
}