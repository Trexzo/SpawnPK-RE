package spk.local;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Exact-v308 social/chat presentation facade.
 *
 * Transport transforms, opcodes and legacy request suffixes remain internal
 * protocol compatibility details. This class does not own social policy.
 */
final class SocialChatPresentationPublisher {
    private static final String TRADE_REQUEST=":tradereq:";
    private static final String DUEL_REQUEST=":duelreq:";
    private static final String CLAN_WAR_REQUEST=":cwarreq:";
    private static final String WHIP_DDS_REQUEST=":whipddsreq:";
    private static final String WHIP_DUEL_REQUEST=":whipduelreq:";
    private static final String GAMBLE_REQUEST=":gambreq:";
    private static final String CHALLENGE_REQUEST=":chalreq:";

    private final ServerPacketWriter packets;

    SocialChatPresentationPublisher(ServerPacketWriter packets){
        this.packets=Objects.requireNonNull(packets,"packets");
    }

    void friendPresence(long nameKey,int status)throws IOException{
        if(status!=0&&status!=10&&status!=11)
            throw new IllegalArgumentException("friend status="+status);
        packets.fixed(50,new PacketPayloadWriter()
            .putI64BE(nameKey)
            .putU8(status)
            .toByteArray());
    }

    void playerInteractionOption(
        int slot,
        boolean secondarySlotFlag,
        String optionText
    )throws IOException{
        if(slot<1||slot>5)throw new IllegalArgumentException("slot="+slot);
        String text=optionText==null?"null":optionText;
        packets.varByte(104,new PacketPayloadWriter()
            .putU8Neg(slot)
            .putU8Add128(secondarySlotFlag?0:1)
            .putStringNl(text)
            .toByteArray());
    }

    void privateMessage(
        long senderNameKey,
        int messageId,
        int senderCode,
        String messageText
    )throws IOException{
        packets.varByte(196,new PacketPayloadWriter()
            .putI64BE(senderNameKey)
            .putI32BE(messageId)
            .putI32BE(senderCode)
            .putStringNl(messageText==null?"":messageText)
            .toByteArray());
    }

    void chatModes(
        int publicMode,
        int privateMode,
        int tradeMode
    )throws IOException{
        if(publicMode<0||publicMode>3)
            throw new IllegalArgumentException("publicMode="+publicMode);
        if(privateMode<0||privateMode>2)
            throw new IllegalArgumentException("privateMode="+privateMode);
        if(tradeMode<0||tradeMode>2)
            throw new IllegalArgumentException("tradeMode="+tradeMode);
        packets.fixed(206,new PacketPayloadWriter()
            .putU8(publicMode)
            .putU8(privateMode)
            .putU8(tradeMode)
            .toByteArray());
    }

    void fullIgnoreList(List<Long> ignoredNameKeys)throws IOException{
        Objects.requireNonNull(ignoredNameKeys,"ignoredNameKeys");
        PacketPayloadWriter payload=new PacketPayloadWriter();
        for(Long key:ignoredNameKeys)
            payload.putI64BE(Objects.requireNonNull(key,"ignoredNameKey"));
        packets.varShort(214,payload.toByteArray());
    }

    void friendServerStatus(int status)throws IOException{
        if(status<0||status>2)
            throw new IllegalArgumentException("friendServerStatus="+status);
        packets.fixed(221,new PacketPayloadWriter()
            .putU8(status)
            .toByteArray());
    }

    void serverMessage(String text)throws IOException{
        packets.varByte(253,new PacketPayloadWriter()
            .putStringNl(text==null?"":text)
            .toByteArray());
    }

    void tradeRequestMessage(String displayName)throws IOException{
        requestMessage(displayName,TRADE_REQUEST);
    }

    void duelRequestMessage(String displayName)throws IOException{
        requestMessage(displayName,DUEL_REQUEST);
    }

    void clanWarRequestMessage(String displayName)throws IOException{
        requestMessage(displayName,CLAN_WAR_REQUEST);
    }

    void whipDdsRequestMessage(String displayName)throws IOException{
        requestMessage(displayName,WHIP_DDS_REQUEST);
    }

    void whipDuelRequestMessage(String displayName)throws IOException{
        requestMessage(displayName,WHIP_DUEL_REQUEST);
    }

    void gambleRequestMessage(String displayName)throws IOException{
        requestMessage(displayName,GAMBLE_REQUEST);
    }

    void challengeRequestMessage(String displayName)throws IOException{
        requestMessage(displayName,CHALLENGE_REQUEST);
    }

    private void requestMessage(String displayName,String suffix)throws IOException{
        String name=Objects.requireNonNull(displayName,"displayName");
        serverMessage(name+suffix);
    }
}
