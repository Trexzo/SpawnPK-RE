package spk.local;

/**
 * Exact-current semantic classification for text carried by opcode 103.
 *
 * ClientPacketProbe owns only wire framing/text decoding. Any client-specific
 * command alias that represents a different semantic intent is classified here
 * at the runtime/domain routing boundary.
 */
final class ClientCommandSemanticRouter {
    private static final String DIALOGUE_OPTION_PREFIX=
        "dialogueoption ";

    static int dialogueOptionIndex(
        String text
    ){
        if(text==null)
            return -1;

        if(text.length()!=
                DIALOGUE_OPTION_PREFIX.length()+1||
           !text.startsWith(
                DIALOGUE_OPTION_PREFIX))
            return -1;

        char value=
            text.charAt(
                DIALOGUE_OPTION_PREFIX.length()
            );

        return value>='1'&&
            value<='5'
                ?value-'0'
                :-1;
    }

    private ClientCommandSemanticRouter(){}
}
