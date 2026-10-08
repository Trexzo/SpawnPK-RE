package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class G212MailboxInboxProjectionIntegrationTest {
    private static final int[] SEED={212,213,214,215};

    public static void main(String[] args)throws Exception{
        boolean emptyClearFinalize=false;
        boolean orderedRows=false;
        boolean semanticReadState=false;
        boolean exactSubtype31=false;
        boolean max35=false;
        boolean overLimitFailClosed=false;
        boolean duplicateFailClosed=false;
        boolean invalidSubjectFailClosed=false;
        boolean preflightNoWrite=false;
        boolean domainUnchanged=false;
        boolean noRootClaim=false;
        boolean noSettlementClaim=false;

        ByteArrayOutputStream empty=new ByteArrayOutputStream();
        MailboxInboxProjection.publish(
            writer(empty),
            Collections.emptyList()
        );

        byte[] emptyWire=empty.toByteArray();
        IsaacCipher emptyDecoder=new IsaacCipher(SEED.clone());
        int pos=0;
        pos=assert250(emptyWire,pos,emptyDecoder,new byte[]{0});
        pos=assert250(emptyWire,pos,emptyDecoder,new byte[]{5});

        emptyClearFinalize=pos==emptyWire.length;

        MailboxRewardDeliveryService service=
            new MailboxRewardDeliveryService(36);

        service.deliver(message("g212:first","First"));
        service.deliver(message("g212:second","Second"));
        service.markRead("g212:second");

        List<MailboxRewardDeliveryService.Snapshot> two=
            service.snapshot();

        ByteArrayOutputStream populated=new ByteArrayOutputStream();
        MailboxInboxProjection.publish(writer(populated),two);

        byte[] bytes=populated.toByteArray();
        IsaacCipher decoder=new IsaacCipher(SEED.clone());
        pos=0;
        pos=assert250(bytes,pos,decoder,new byte[]{0});
        pos=assert250(
            bytes,pos,decoder,appendBody(0,"First")
        );
        pos=assert250(
            bytes,pos,decoder,appendBody(1,"Second")
        );
        pos=assert250(bytes,pos,decoder,new byte[]{5});

        orderedRows=pos==bytes.length;
        exactSubtype31=orderedRows&&emptyClearFinalize;
        semanticReadState=
            two.get(0).readState==
                MailboxRewardDeliveryService.ReadState.UNREAD&&
            two.get(1).readState==
                MailboxRewardDeliveryService.ReadState.READ;

        for(int i=2;i<36;i++)
            service.deliver(
                message("g212:extra:"+i,"Row "+i)
            );

        List<MailboxRewardDeliveryService.Snapshot> all=
            service.snapshot();

        ByteArrayOutputStream maxWire=new ByteArrayOutputStream();
        MailboxInboxProjection.publish(
            writer(maxWire),
            all.subList(0,35)
        );

        bytes=maxWire.toByteArray();
        decoder=new IsaacCipher(SEED.clone());
        pos=assert250(bytes,0,decoder,new byte[]{0});

        for(int i=0;i<35;i++){
            MailboxRewardDeliveryService.Snapshot row=all.get(i);
            pos=assert250(
                bytes,pos,decoder,
                appendBody(
                    MailboxPresentationAdapter.readStateCode(
                        row.readState
                    ),
                    row.message.subject
                )
            );
        }

        pos=assert250(bytes,pos,decoder,new byte[]{5});
        max35=
            MailboxInboxProjection.CLIENT_VISIBLE_ROW_LIMIT==35&&
            pos==bytes.length&&
            service.capacity()==36;

        overLimitFailClosed=rejectsWithoutWriting(all);

        duplicateFailClosed=rejectsWithoutWriting(
            Arrays.asList(all.get(0),all.get(0))
        );

        MailboxRewardDeliveryService invalid=
            new MailboxRewardDeliveryService(4);

        invalid.deliver(message("g212:bad-lf","Bad\nsubject"));
        List<MailboxRewardDeliveryService.Snapshot> badLf=
            Arrays.asList(all.get(0),invalid.snapshot().get(0));

        boolean badNewline=rejectsWithoutWriting(badLf);

        invalid.deliver(
            message("g212:bad-unicode","Unicode Ω")
        );

        boolean badUnicode=rejectsWithoutWriting(
            Arrays.asList(all.get(0),invalid.snapshot().get(1))
        );

        char[] longSubject=new char[251];
        Arrays.fill(longSubject,'A');

        invalid.deliver(
            message("g212:bad-size",new String(longSubject))
        );

        boolean badLength=rejectsWithoutWriting(
            Arrays.asList(all.get(0),invalid.snapshot().get(2))
        );

        invalidSubjectFailClosed=
            badNewline&&badUnicode&&badLength;

        boolean nullRowRejected=rejectsWithoutWriting(
            Arrays.asList(all.get(0),null)
        );

        preflightNoWrite=
            overLimitFailClosed&&
            duplicateFailClosed&&
            invalidSubjectFailClosed&&
            nullRowRejected;

        domainUnchanged=
            service.size()==36&&
            service.unreadCount()==35&&
            service.get("g212:first").readState==
                MailboxRewardDeliveryService.ReadState.UNREAD&&
            service.get("g212:second").readState==
                MailboxRewardDeliveryService.ReadState.READ;

        noRootClaim=
            MailboxInboxProjection.class.getDeclaredFields().length==1&&
            exactSubtype31;

        noSettlementClaim=
            service.get("g212:first").claimState==
                MailboxRewardDeliveryService.ClaimState.EMPTY;

        require(
            emptyClearFinalize&&
            orderedRows&&
            semanticReadState&&
            exactSubtype31&&
            max35&&
            overLimitFailClosed&&
            duplicateFailClosed&&
            invalidSubjectFailClosed&&
            preflightNoWrite&&
            domainUnchanged&&
            noRootClaim&&
            noSettlementClaim,
            "G21.2 acceptance"
        );

        System.out.println(
            "G212_MAILBOX_INBOX_PROJECTION_PASS"+
            " emptyClearFinalize="+emptyClearFinalize+
            " orderedRows="+orderedRows+
            " semanticReadState="+semanticReadState+
            " exactSubtype31="+exactSubtype31+
            " max35="+max35+
            " overLimitFailClosed="+overLimitFailClosed+
            " duplicateFailClosed="+duplicateFailClosed+
            " invalidSubjectFailClosed="+invalidSubjectFailClosed+
            " preflightNoWrite="+preflightNoWrite+
            " domainUnchanged="+domainUnchanged+
            " noRootClaim="+noRootClaim+
            " noSettlementClaim="+noSettlementClaim+
            " persistenceClaim=false"
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream sink
    ){
        return new ServerPacketWriter(
            sink,
            new IsaacCipher(SEED.clone())
        );
    }

    private static RewardDeliveryMessage message(
        String id,
        String subject
    ){
        return new RewardDeliveryMessage(
            id,
            subject,
            "LocalLab G21.2 projection test",
            Collections.emptyList(),
            "CUSTOM_LOCALLAB_G212_FIXTURE"
        );
    }

    private static byte[] appendBody(
        int readState,
        String subject
    ){
        byte[] text=
            subject.getBytes(StandardCharsets.ISO_8859_1);
        byte[] body=new byte[text.length+3];
        body[0]=1;
        body[1]=(byte)readState;
        System.arraycopy(text,0,body,2,text.length);
        body[body.length-1]=10;
        return body;
    }

    private static boolean rejectsWithoutWriting(
        List<MailboxRewardDeliveryService.Snapshot> rows
    )throws Exception{
        ByteArrayOutputStream sink=new ByteArrayOutputStream();
        boolean rejected=false;

        try{
            MailboxInboxProjection.publish(
                writer(sink),
                rows
            );
        }catch(IllegalArgumentException|
               NullPointerException expected){
            rejected=true;
        }

        return rejected&&sink.size()==0;
    }

    private static int assert250(
        byte[] wire,
        int offset,
        IsaacCipher decoder,
        byte[] expectedBody
    ){
        require(offset+4<=wire.length,"truncated 250 header");

        int opcode=
            ((wire[offset++]&255)-decoder.nextInt())&255;
        require(opcode==250,"expected 250 actual="+opcode);

        int length=wire[offset++]&255;
        require(
            length==expectedBody.length+2,
            "250 length expected="+(expectedBody.length+2)+
            " actual="+length
        );

        int subtype=
            ((wire[offset++]&255)<<8)|(wire[offset++]&255);
        require(subtype==31,"expected subtype 31");

        require(
            offset+expectedBody.length<=wire.length,
            "truncated subtype31 body"
        );

        byte[] actual=Arrays.copyOfRange(
            wire,
            offset,
            offset+expectedBody.length
        );

        require(
            Arrays.equals(expectedBody,actual),
            "subtype31 body mismatch expected="+
            Arrays.toString(expectedBody)+
            " actual="+Arrays.toString(actual)
        );

        return offset+expectedBody.length;
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G212MailboxInboxProjectionIntegrationTest(){}
}
