package spk.local;

import java.util.Objects;

/**
 * Exact-current v308 rs.P chat-text decoder.
 *
 * Client authority:
 * SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6.
 *
 * Each encoded byte is one direct index into the 66-character table. The
 * client decoder then sentence-cases the first lowercase character and the
 * first lowercase character after '.', '!' or '?'.
 */
final class ClientChatTextCodec {
    private static final char[] TABLE={
        ' ','e','t','a','o','i','h','n','s','r',
        'd','l','u','m','w','c','y','f','g','p',
        'b','v','k','x','j','q','z',
        '0','1','2','3','4','5','6','7','8','9',
        ' ','!','?','.',',',':',';','(',')','-',
        '&','*','\\','\'','@','#','+','=','\u00a3',
        '$','%','"','[',']','>','<','^','/','_'
    };

    private ClientChatTextCodec(){}

    static String decode(
        byte[] encoded
    ){
        Objects.requireNonNull(
            encoded,
            "encoded"
        );

        char[] out=
            new char[encoded.length];

        boolean sentenceStart=true;

        for(int i=0;i<encoded.length;i++){
            int index=
                encoded[i]&255;

            if(index>=TABLE.length)
                throw new IllegalArgumentException(
                    "chat table index="+
                    index+
                    " at="+i
                );

            char value=
                TABLE[index];

            if(sentenceStart&&
               value>='a'&&
               value<='z'){
                value=
                    (char)(value-32);
                sentenceStart=false;
            }

            out[i]=value;

            if(value=='.'||
               value=='!'||
               value=='?')
                sentenceStart=true;
        }

        return new String(out);
    }

    static String decodePublicWire(
        byte[] body,
        int offset
    ){
        Objects.requireNonNull(
            body,
            "body"
        );

        if(offset<0||
           offset>body.length)
            throw new IndexOutOfBoundsException(
                "offset="+offset+
                " length="+body.length
            );

        int length=
            body.length-offset;

        byte[] encoded=
            new byte[length];

        for(int i=0;i<length;i++)
            encoded[i]=
                (byte)(
                    ((body[
                        body.length-1-i
                    ]&255)-128)&255
                );

        return decode(encoded);
    }

    static int tableSize(){
        return TABLE.length;
    }
}
