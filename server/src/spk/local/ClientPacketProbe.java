package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Conservative client->server post-login decoder.
 *
 * v3 extends the proven ISAAC alignment across the startup/world stream using only
 * statically exact writer schemas:
 *   185 fixed-2 widget action (valid anywhere in the post-login stream)
 *   103 var-byte command text
 *   3   fixed-1 focus-state notification (1 focused / 0 unfocused)
 *   0   fixed-0 startup/region acknowledgement family
 *   121 fixed-0 loading-complete acknowledgement
 *   77  var-byte periodic client telemetry/anti-cheat-noise family
 *   226 var-byte periodic randomized telemetry family (live v4 decoder blocker)
 *   202 fixed-0 long-session keepalive (exact pinned-client writer; v5.2 live blocker)
 *   36  fixed-4 periodic movement/checksum telemetry (next blocker recovered by ISAAC replay)
 *   164/98 var-byte movement families -> immutable MovementRequest objects
 *   248 var-byte minimap movement -> same core path + exact opaque 14-byte extension
 *   130 fixed-0 interface-close notification
 *   132 fixed-6 object first-option interaction -> transformed worldX/objectId/worldY
 *   145/117/43/129/135/140/141 exact item-container action families used by bank widgets
 *   208 fixed-4 big-endian amount entry used after server packet 27
 *   214 fixed-7 exact container drag packet
 *   41  fixed-6 normal inventory item option (decoded/observed so it cannot stall framing)
 *   72  fixed-2 NPC Attack action -> big-endian short-A scene index
 *
 * Every opcode emitted by the pinned exact client now has authoritative framing.
 * Implemented packets keep semantic handlers; the remainder are consumed as framingOnly.
 * Opcodes outside the exact-current-client authority set still pause fail-closed.
 */
final class ClientPacketProbe {
    private final InputStream in;
    private final IsaacCipher cipher;
    private final String tag;
    private boolean aligned = true;
    private long decodedCount;
    private long opcode0Count;
    private final ClientRequestQueue typedRequests=
        new ClientRequestQueue();

    ClientPacketProbe(InputStream in, IsaacCipher cipher, String tag) {
        this.in = in;
        this.cipher = cipher;
        this.tag = tag;
    }

    boolean isAligned() { return aligned; }
    long decodedCount() { return decodedCount; }

    ClientRequest takeTypedRequest(){
        return typedRequests.poll();
    }

    int typedRequestCount(){
        return typedRequests.size();
    }

    /** Decode the one login-success packet statically proven in the current client. */
    int readFirst185() throws IOException {
        int encoded = in.read();
        if (encoded < 0) return -1;
        int opcode = (encoded - cipher.nextInt()) & 0xff;
        decodedCount++;
        if (opcode != 185) {
            System.out.printf("%sCLIENT_PACKET seq=%d opcode=%d encoded=%d expectedFirst=185; decoder paused%n",
                              tag, decodedCount, opcode, encoded);
            aligned = false;
            return opcode;
        }
        byte[] body = Binary.readExactly(in, 2);
        int widget = Binary.u16(body, 0);
        System.out.printf("%sCLIENT_PACKET seq=%d opcode=185 len=2 widget=%d expectedWidget=912%n",
                          tag, decodedCount, widget);
        return opcode;
    }

    /**
     * Decode one subsequent packet if its framing is statically exact.  Returns
     * false after EOF or an unknown opcode; unknown payload bytes are not consumed.
     */
    boolean readNextKnownPacket() throws IOException {
        if (!aligned) return false;
        int encoded = in.read();
        if (encoded < 0) return false;
        int opcode = (encoded - cipher.nextInt()) & 0xff;
        decodedCount++;

        switch (opcode) {
            case 3: {
                int focus = readU8();
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=3 len=1 focus=%d focused=%s schema=STATIC_EXACT%n",
                                  tag, decodedCount, focus, focus != 0);
                return true;
            }

            case 36: {
                // Exact pinned-client periodic writer:
                //   fv.a(36); fv.g(0)
                // g(int) is ordinary BE32. This appeared later in the same v4 raw
                // stream after opcode226; framing it preserves alignment.
                byte[] body = Binary.readExactly(in, 4);
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=36 len=4 periodicMovementTelemetry=true payload=%s schema=STATIC_EXACT_FIXED4_BE%n",
                                  tag, decodedCount, hex(body, 16));
                return true;
            }

            case 0:
                opcode0Count++;
                if (opcode0Count <= 4 || opcode0Count % 25 == 0) {
                    System.out.printf("%sCLIENT_PACKET seq=%d opcode=0 len=0 count=%d%n",
                                      tag, decodedCount, opcode0Count);
                }
                return true;

            case 121:
                offerTypedRequest(
                    new RegionLoadAckClientRequest(
                        ClientRequestMetadata.exactCurrent(
                            121,
                            "FIXED0_REGION_LOAD_COMPLETE",
                            "V308_RUNTIME_PROBE_RS_CLIENT_BW_REGION_LOAD_COMPLETE"
                        )
                    ),
                    121
                );
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=121 len=0 loadingAck=true regionLifecycleTyped=true%n",
                                  tag, decodedCount);
                return true;

            case 185: {
                // Every opcode-185 writer callsite in the pinned client is
                // exactly: fv.a(185); fv.d(widgetId). rs.x.e.d(int) writes a
                // big-endian unsigned short. v1 only special-cased the first
                // login-time instance (widget 912), which caused the live M5
                // decoder to pause on later widget actions such as 152.
                byte[] body = Binary.readExactly(in, 2);
                int widget = Binary.u16(body, 0);

                offerTypedRequest(
                    new WidgetActionClientRequest(
                        widget,
                        ClientRequestMetadata.exactCurrent(
                            185,
                            "FIXED2_WIDGET_U16_BE",
                            "PINNED_CLIENT_OPCODE_185_ALL_CALLSITES"
                        )
                    ),
                    opcode
                );

                System.out.printf("%sCLIENT_PACKET seq=%d opcode=185 len=2 widget=%d schema=STATIC_EXACT_ALL_CALLSITES%n",
                                  tag, decodedCount, widget);
                return true;
            }

            case 4: {
                int len=readU8();
                byte[] body=
                    Binary.readExactly(
                        in,
                        len
                    );

                if(body.length<2)
                    throw new IOException(
                        "opcode4 len="+
                        body.length
                    );

                int effect=
                    (128-
                        Binary.u8(
                            body,
                            0
                        ))&255;
                int colour=
                    (128-
                        Binary.u8(
                            body,
                            1
                        ))&255;

                String message;

                try{
                    message=
                        ClientChatTextCodec
                            .decodePublicWire(
                                body,
                                2
                            );
                }catch(IllegalArgumentException error){
                    throw new IOException(
                        "opcode4 invalid chat text",
                        error
                    );
                }

                offerTypedRequest(
                    new PublicChatClientRequest(
                        effect,
                        colour,
                        message,
                        ClientRequestMetadata.exactCurrent(
                            4,
                            "VARBYTE_EFFECT_128_MINUS_COLOUR_128_MINUS_REVERSED_CHAT_TABLE_ADD128",
                            "V308_CLIENT_PUBLIC_CHAT_WRITER"
                        )
                    ),
                    opcode
                );

                System.out.printf(
                    "%sCLIENT_PACKET seq=%d opcode=4 len=%d publicChat=true effect=%d colour=%d messageLength=%d schema=EXACT_CURRENT_CLIENT_CHAT_TABLE%n",
                    tag,
                    decodedCount,
                    len,
                    effect,
                    colour,
                    message.length()
                );
                return true;
            }

            case 126: {
                int len=readU8();
                byte[] body=
                    Binary.readExactly(
                        in,
                        len
                    );

                if(body.length<8)
                    throw new IOException(
                        "opcode126 len="+
                        body.length
                    );

                long recipientNameKey=
                    Binary.i64(
                        body,
                        0
                    );

                byte[] encodedMessage=
                    new byte[
                        body.length-8
                    ];

                System.arraycopy(
                    body,
                    8,
                    encodedMessage,
                    0,
                    encodedMessage.length
                );

                String message;

                try{
                    message=
                        ClientChatTextCodec
                            .decode(
                                encodedMessage
                            );
                }catch(IllegalArgumentException error){
                    throw new IOException(
                        "opcode126 invalid chat text",
                        error
                    );
                }

                offerTypedRequest(
                    new PrivateMessageClientRequest(
                        recipientNameKey,
                        message,
                        ClientRequestMetadata.exactCurrent(
                            126,
                            "VARBYTE_RECIPIENT_NAME_KEY_I64_BE_CHAT_TABLE",
                            "V308_CLIENT_PRIVATE_MESSAGE_WRITER"
                        )
                    ),
                    opcode
                );

                System.out.printf(
                    "%sCLIENT_PACKET seq=%d opcode=126 len=%d privateMessage=true recipientNameKey=%s messageLength=%d schema=EXACT_CURRENT_CLIENT_CHAT_TABLE%n",
                    tag,
                    decodedCount,
                    len,
                    Long.toUnsignedString(
                        recipientNameKey
                    ),
                    message.length()
                );
                return true;
            }

            case 40: {
                byte[] body=Binary.readExactly(in,2);
                int widget=Binary.u16(body,0);

                offerTypedRequest(
                    new DialogueContinueClientRequest(
                        widget,
                        ClientRequestMetadata.exactCurrent(
                            40,
                            "FIXED2_WIDGET_U16_BE",
                            "V308_CLIENT_DIALOGUE_CONTINUE_WRITER"
                        )
                    ),
                    opcode
                );

                System.out.printf(
                    "%sCLIENT_PACKET seq=%d opcode=40 len=2 dialogueContinue=true widget=%d schema=EXACT_CURRENT_CLIENT_U16_BE%n",
                    tag,decodedCount,widget
                );
                return true;
            }

            case 101: {
                byte[] body=Binary.readExactly(
                    in,
                    CharacterDesignRequest.WIRE_LENGTH
                );
                CharacterDesignRequest design=
                    CharacterDesignRequest.decode(body);

                offerTypedRequest(
                    new CharacterDesignClientRequest(
                        design,
                        ClientRequestMetadata.exactCurrent(
                            101,
                            "FIXED13_GENDER_KITS7_COLOURS5",
                            "V308_CLIENT_CHARACTER_DESIGN_WRITER"
                        )
                    ),
                    opcode
                );

                System.out.printf(
                    "%sCLIENT_PACKET seq=%d opcode=101 len=13 characterDesign=true request=%s schema=EXACT_CURRENT_CLIENT_FIXED13%n",
                    tag,decodedCount,design
                );
                return true;
            }

            case 60: {
                byte[] body=
                    Binary.readExactly(
                        in,
                        8
                    );
                long nameKey=
                    Binary.i64(
                        body,
                        0
                    );

                offerTypedRequest(
                    new NameEntryClientRequest(
                        nameKey,
                        ClientRequestMetadata.exactCurrent(
                            60,
                            "FIXED8_NAME_KEY_I64_BE",
                            "V308_CLIENT_GENERIC_NAME_ENTRY_WRITER"
                        )
                    ),
                    opcode
                );

                System.out.printf(
                    "%sCLIENT_PACKET seq=%d opcode=60 len=8 nameEntry=true nameKey=%s schema=EXACT_CURRENT_CLIENT_I64_BE%n",
                    tag,
                    decodedCount,
                    Long.toUnsignedString(nameKey)
                );
                return true;
            }

            case 95: {
                byte[] body=
                    Binary.readExactly(
                        in,
                        3
                    );

                int mode0=Binary.u8(body,0);
                int mode1=Binary.u8(body,1);
                int mode2=Binary.u8(body,2);

                offerTypedRequest(
                    new ChatModeClientRequest(
                        mode0,
                        mode1,
                        mode2,
                        ClientRequestMetadata.exactCurrent(
                            95,
                            "FIXED3_CHAT_MODE_U8_U8_U8",
                            "V308_CLIENT_CHAT_MODE_WRITER"
                        )
                    ),
                    opcode
                );

                System.out.printf(
                    "%sCLIENT_PACKET seq=%d opcode=95 len=3 chatModes=%d,%d,%d schema=EXACT_CURRENT_CLIENT_FIXED3%n",
                    tag,
                    decodedCount,
                    mode0,
                    mode1,
                    mode2
                );
                return true;
            }

            case 218: {
                byte[] body=
                    Binary.readExactly(
                        in,
                        10
                    );

                long nameKey=
                    Binary.i64(
                        body,
                        0
                    );
                int ruleIndex=
                    Binary.u8(
                        body,
                        8
                    );
                int muteToggle=
                    Binary.u8(
                        body,
                        9
                    );

                offerTypedRequest(
                    new ReportAbuseClientRequest(
                        nameKey,
                        ruleIndex,
                        muteToggle,
                        ClientRequestMetadata.exactCurrent(
                            218,
                            "FIXED10_NAME_KEY_I64_BE_RULE_U8_MUTE_U8",
                            "V308_CLIENT_REPORT_ABUSE_WRITER"
                        )
                    ),
                    opcode
                );

                System.out.printf(
                    "%sCLIENT_PACKET seq=%d opcode=218 len=10 reportAbuse=true nameKey=%s ruleIndex=%d muteToggle=%d schema=EXACT_CURRENT_CLIENT_FIXED10%n",
                    tag,
                    decodedCount,
                    Long.toUnsignedString(nameKey),
                    ruleIndex,
                    muteToggle
                );
                return true;
            }

            case 74:
            case 133:
            case 188:
            case 215: {
                byte[] body=
                    Binary.readExactly(
                        in,
                        8
                    );
                long nameKey=
                    Binary.i64(
                        body,
                        0
                    );

                SocialListClientRequest.Action action;
                String source;

                switch(opcode){
                    case 188:
                        action=
                            SocialListClientRequest.Action
                                .ADD_FRIEND;
                        source=
                            "V308_CLIENT_ADD_FRIEND_WRITER";
                        break;
                    case 215:
                        action=
                            SocialListClientRequest.Action
                                .REMOVE_FRIEND;
                        source=
                            "V308_CLIENT_REMOVE_FRIEND_WRITER";
                        break;
                    case 133:
                        action=
                            SocialListClientRequest.Action
                                .ADD_IGNORE;
                        source=
                            "V308_CLIENT_ADD_IGNORE_WRITER";
                        break;
                    case 74:
                        action=
                            SocialListClientRequest.Action
                                .REMOVE_IGNORE;
                        source=
                            "V308_CLIENT_REMOVE_IGNORE_WRITER";
                        break;
                    default:
                        throw new AssertionError();
                }

                offerTypedRequest(
                    new SocialListClientRequest(
                        action,
                        nameKey,
                        ClientRequestMetadata.exactCurrent(
                            opcode,
                            "FIXED8_NAME_KEY_I64_BE",
                            source
                        )
                    ),
                    opcode
                );

                System.out.printf(
                    "%sCLIENT_PACKET seq=%d opcode=%d len=8 socialAction=%s nameKey=%s schema=EXACT_CURRENT_CLIENT_I64_BE%n",
                    tag,
                    decodedCount,
                    opcode,
                    action,
                    Long.toUnsignedString(nameKey)
                );
                return true;
            }

            case 57: {
                // Exact-current client item-on-NPC writer (menu action 582):
                // selectedItemId u16_be_low_add128
                // targetNpcIndex u16_be_low_add128
                // selectedItemSlot u16_le_alias
                // selectedItemWidget u16_be_low_add128
                byte[] body=Binary.readExactly(in,8);
                ItemOnNpcAction decoded=decodeItemOnNpc(body);
                int selectedItem=decoded.itemId;
                int targetNpc=decoded.targetNpcIndex;
                int selectedSlot=decoded.slot;
                int selectedWidget=decoded.widgetId;

                offerTypedRequest(
                    new ItemOnNpcClientRequest(
                        decoded,
                        ClientRequestMetadata.exactCurrent(
                            57,
                            "FIXED8_ITEM_BE_A_NPC_BE_A_SLOT_LE_WIDGET_BE_A",
                            "PINNED_CLIENT_ITEM_ON_NPC_WRITER"
                        )
                    ),
                    opcode
                );

                System.out.printf("%sCLIENT_PACKET seq=%d opcode=57 len=8 itemOnNpc=true itemId=%d slot=%d widget=%d targetNpc=%d schema=STATIC_EXACT_FIXED8%n",
                                  tag,decodedCount,selectedItem,selectedSlot,selectedWidget,targetNpc);
                return true;
            }

            case 130:
                // Exact pinned-client writer (Client.bQ): fv.a(130) with no payload.
                // This is emitted when the client closes an interface. v3 paused here,
                // which made subsequent bank clicks and movement look frozen.
                offerTypedRequest(
                    new InterfaceCloseClientRequest(
                        ClientRequestMetadata.exactCurrent(
                            130,
                            "FIXED0_INTERFACE_CLOSE",
                            "PINNED_CLIENT_CLIENT_BQ"
                        )
                    ),
                    opcode
                );
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=130 len=0 interfaceClose=true schema=STATIC_EXACT_FIXED0%n",
                                  tag, decodedCount);
                return true;

            case 132: {
                // Exact schema appears in both the menu dispatcher and the dedicated
                // rs.o.a.a.a.N packet object:
                //   fv.a(132); fv.p(worldX); fv.d(objectId); fv.o(worldY)
                // p = low-byte+128 then high-byte, d = big-endian short,
                // o = high-byte then low-byte+128.
                byte[] body = Binary.readExactly(in, 6);
                int worldX = (((body[0] & 0xff) - 128) & 0xff) | ((body[1] & 0xff) << 8);
                int objectId = ((body[2] & 0xff) << 8) | (body[3] & 0xff);
                int worldY = ((body[4] & 0xff) << 8) | (((body[5] & 0xff) - 128) & 0xff);

                offerTypedRequest(
                    new ObjectInteractionClientRequest(
                        new ObjectInteraction(
                            opcode,
                            objectId,
                            worldX,
                            worldY
                        ),
                        ClientRequestMetadata.exactCurrent(
                            132,
                            "FIXED6_WORLD_X_LE_A_OBJECT_BE_WORLD_Y_BE_A",
                            "PINNED_CLIENT_MENU_ACTION_502_AND_N_SERIALIZER"
                        )
                    ),
                    opcode
                );

                System.out.printf("%sCLIENT_PACKET seq=%d opcode=132 len=6 objectAction=true objectId=%d worldX=%d worldY=%d schema=STATIC_EXACT_FIXED6%n",
                                  tag, decodedCount, objectId, worldX, worldY);
                return true;
            }


            case 41: {
                // Exact menu action 454 writer in the pinned client:
                //   fv.a(41); fv.d(itemId); fv.o(slot); fv.o(widgetId)
                // d = big-endian short, o = big-endian short-A.  v3.1 exposed
                // normal inventory 3214 inside the bank, so a click on its seeded
                // whip produced this packet and paused the conservative decoder.
                byte[] body = Binary.readExactly(in, 6);
                int item = be(body,0);
                int slot = beA(body,2);
                int widget = beA(body,4);
                offerItemAction(
                    new ItemContainerAction(
                        opcode,
                        widget,
                        slot,
                        item,
                        0,
                        widget == BankState.NORMAL_INVENTORY_CONTAINER
                            ? "WEAR_WIELD_EQUIP"
                            : "ITEM_OPTION_2"
                    ),
                    "FIXED6_ITEM_BE_SLOT_BE_A_WIDGET_BE_A",
                    "PINNED_CLIENT_MENU_ACTION_454_WRITER"
                );
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=41 len=6 inventoryItemAction=true widget=%d slot=%d itemId=%d semantic=%s schema=STATIC_EXACT_FIXED6%n",
                                  tag, decodedCount, widget, slot, item,
                                  widget == BankState.NORMAL_INVENTORY_CONTAINER ? "WEAR_WIELD_EQUIP" : "ITEM_OPTION_2");
                return true;
            }

            case 75: {
                // Exact menu action 493 writer (item action index 3):
                //   fv.a(75); fv.p(widgetId); fv.n(slot); fv.o(itemId)
                // p = little-endian short-A, n = plain little-endian short,
                // o = big-endian short-A. Grand completionist capes expose
                // their native Customize action in this index.
                byte[] body = Binary.readExactly(in, 6);
                int widget = leA(body,0);
                int slot = le(body,2);
                int item = beA(body,4);
                String semantic = (widget == BankState.NORMAL_INVENTORY_CONTAINER
                    && (item==23063 || item==21963 || item==21964))
                    ? "CUSTOMIZE_COMP_CAPE" : "ITEM_OPTION_4";
                offerItemAction(
                    new ItemContainerAction(
                        opcode,
                        widget,
                        slot,
                        item,
                        0,
                        semantic
                    ),
                    "FIXED6_WIDGET_LE_A_SLOT_LE_ITEM_BE_A",
                    "PINNED_CLIENT_MENU_ACTION_493_WRITER"
                );
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=75 len=6 inventoryItemAction=true widget=%d slot=%d itemId=%d semantic=%s schema=STATIC_EXACT_FIXED6%n",
                                  tag, decodedCount, widget, slot, item, semantic);
                return true;
            }

            case 145:
            case 117:
            case 43:
            case 129:
            case 135:
            case 140:
            case 141: {
                int len = opcode == 141 ? 10 : 6;
                byte[] body = Binary.readExactly(in, len);
                int widget, slot, item, extra = 0;
                String semantic;
                String schema;
                String source;
                switch (opcode) {
                    case 145: // action 632 / W[0]
                        widget = beA(body,0); slot = beA(body,2); item = beA(body,4);
                        semantic=itemSemantic(widget,"1");
                        schema="FIXED6_WIDGET_BE_A_SLOT_BE_A_ITEM_BE_A";
                        source="PINNED_CLIENT_MENU_ACTION_632_WRITER";
                        break;
                    case 117: // action 78 / W[1]
                        widget = leA(body,0); item = leA(body,2); slot = le(body,4);
                        semantic=itemSemantic(widget,"5");
                        schema="FIXED6_WIDGET_LE_A_ITEM_LE_A_SLOT_LE";
                        source="PINNED_CLIENT_MENU_ACTION_78_WRITER";
                        break;
                    case 43:  // action 867 / W[2]
                        widget = le(body,0); item = beA(body,2); slot = beA(body,4);
                        semantic=itemSemantic(widget,"10");
                        schema="FIXED6_WIDGET_LE_ITEM_BE_A_SLOT_BE_A";
                        source="PINNED_CLIENT_MENU_ACTION_867_WRITER";
                        break;
                    case 129: // action 431 / W[3]
                        slot = beA(body,0); widget = be(body,2); item = beA(body,4);
                        semantic=itemSemantic(widget,"ALL");
                        schema="FIXED6_SLOT_BE_A_WIDGET_BE_ITEM_BE_A";
                        source="PINNED_CLIENT_MENU_ACTION_431_WRITER";
                        break;
                    case 135: // action 53 / W[4]
                        slot = le(body,0); widget = beA(body,2); item = le(body,4);
                        semantic=itemSemantic(widget,"X");
                        schema="FIXED6_SLOT_LE_WIDGET_BE_A_ITEM_LE";
                        source="PINNED_CLIENT_MENU_ACTION_53_WRITER";
                        break;
                    case 140: // action 291 / bank W[6]: ordinary All-But-One, coins 995 Bag-exchange
                        slot = beA(body,0); widget = be(body,2); item = beA(body,4);
                        semantic=item==995?"BAG_EXCHANGE_REQUEST":"WITHDRAW_ALL_BUT_ONE";
                        schema="FIXED6_SLOT_BE_A_WIDGET_BE_ITEM_BE_A";
                        source="PINNED_CLIENT_MENU_ACTION_291_WRITER";
                        break;
                    case 141: // action 300 / bank W[5]: final i32 is current Client.ih configured amount
                        slot = beA(body,0); widget = be(body,2); item = beA(body,4); extra = be32(body,6);
                        semantic="WITHDRAW_CONFIGURED_AMOUNT";
                        schema="FIXED10_SLOT_BE_A_WIDGET_BE_ITEM_BE_A_EXTRA_BE32";
                        source="PINNED_CLIENT_MENU_ACTION_300_WRITER";
                        break;
                    default: throw new AssertionError();
                }
                offerItemAction(
                    new ItemContainerAction(
                        opcode,
                        widget,
                        slot,
                        item,
                        extra,
                        semantic
                    ),
                    schema,
                    source
                );
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=%d len=%d itemContainerAction=true widget=%d slot=%d itemId=%d semantic=%s%s schema=STATIC_EXACT%n",
                                  tag, decodedCount, opcode, len, widget, slot, item, semantic,
                                  opcode==141 ? " extra="+extra : "");
                return true;
            }

            case 208: {
                // Exact pinned-client amount-entry writer:
                //   fv.a(208); fv.g(Integer.parseInt(dY))
                // rs.x.e.g(int) is ordinary big-endian 32-bit.
                byte[] body = Binary.readExactly(in, 4);
                int amount = be32(body,0);

                offerTypedRequest(
                    new AmountEntryClientRequest(
                        amount,
                        ClientRequestMetadata.exactCurrent(
                            208,
                            "FIXED4_BE_SIGNED_AMOUNT",
                            "PINNED_CLIENT_AMOUNT_ENTRY_WRITER"
                        )
                    ),
                    opcode
                );

                System.out.printf("%sCLIENT_PACKET seq=%d opcode=208 len=4 amount=%d amountEntry=true schema=STATIC_EXACT_FIXED4_BE%n",
                                  tag, decodedCount, amount);
                return true;
            }

            case 214: {
                // Exact pinned-client container drag writer:
                //   fv.a(214); fv.p(widget); fv.l(mode); fv.p(source); fv.n(destination)
                // p=LE short-A, l=negated byte, n=plain LE short.
                byte[] body = Binary.readExactly(in, 7);
                int widget = leA(body,0);
                int mode = (-(body[2] & 0xff)) & 0xff;
                int source = leA(body,3);
                int destination = le(body,5);

                offerTypedRequest(
                    new ContainerDragClientRequest(
                        new ContainerDrag(
                            widget,
                            mode,
                            source,
                            destination
                        ),
                        ClientRequestMetadata.exactCurrent(
                            214,
                            "FIXED7_WIDGET_LE_A_MODE_NEG_SOURCE_LE_A_DEST_LE",
                            "PINNED_CLIENT_CONTAINER_DRAG_WRITER"
                        )
                    ),
                    opcode
                );

                System.out.printf("%sCLIENT_PACKET seq=%d opcode=214 len=7 containerDrag=true widget=%d mode=%d source=%d destination=%d schema=STATIC_EXACT_FIXED7%n",
                                  tag, decodedCount, widget, mode, source, destination);
                return true;
            }

            case 53: {
                // Exact current-client item-on-item writer (12 bytes):
                // targetSlot BE; selectedSlot BE-A; targetItem LE-A;
                // selectedWidget BE; selectedItem LE; targetWidget BE.
                byte[] body=Binary.readExactly(in,12);
                int targetSlot=be(body,0);
                int selectedSlot=beA(body,2);
                int targetItem=leA(body,4);
                int selectedWidget=be(body,6);
                int selectedItem=le(body,8);
                int targetWidget=be(body,10);
                ItemOnItemAction action=
                    new ItemOnItemAction(
                        targetSlot,
                        selectedSlot,
                        targetItem,
                        selectedWidget,
                        selectedItem,
                        targetWidget
                    );

                offerTypedRequest(
                    new ItemOnItemClientRequest(
                        action,
                        ClientRequestMetadata.exactCurrent(
                            53,
                            "FIXED12_TARGET_SLOT_BE_SELECTED_SLOT_BE_A_TARGET_ITEM_LE_A_SELECTED_WIDGET_BE_SELECTED_ITEM_LE_TARGET_WIDGET_BE",
                            "PINNED_CLIENT_ITEM_ON_ITEM_WRITER"
                        )
                    ),
                    opcode
                );

                System.out.printf("%sCLIENT_PACKET seq=%d opcode=53 len=12 itemOnItem=true selected=%d@%d/%d target=%d@%d/%d schema=STATIC_EXACT_FIXED12%n",
                    tag,decodedCount,selectedItem,selectedSlot,selectedWidget,targetItem,targetSlot,targetWidget);
                return true;
            }

            case 249:
            case 131:
            case 35:
            case 181:
            case 237: {
                int len=(opcode==249||opcode==131)?4:8;
                byte[] body=Binary.readExactly(in,len);
                SpellTargetRequest decoded=
                    decodeSpellTarget(opcode,body);

                String schema;
                String source;
                switch(opcode){
                    case 249:
                        schema="FIXED4_PLAYER_BE_A_SPELL_LE";
                        source="PINNED_CLIENT_SPELL_ON_PLAYER_WRITER";
                        break;
                    case 131:
                        schema="FIXED4_NPC_LE_A_SPELL_BE_A";
                        source="PINNED_CLIENT_SPELL_ON_NPC_WRITER";
                        break;
                    case 35:
                        schema="FIXED8_WORLD_X_LE_SPELL_BE_A_WORLD_Y_BE_A_OBJECT_LE";
                        source="PINNED_CLIENT_SPELL_ON_OBJECT_WRITER";
                        break;
                    case 181:
                        schema="FIXED8_WORLD_Y_LE_ITEM_BE_WORLD_X_LE_SPELL_BE_A";
                        source="PINNED_CLIENT_SPELL_ON_GROUND_ITEM_WRITER";
                        break;
                    case 237:
                        schema="FIXED8_SLOT_BE_ITEM_BE_A_WIDGET_BE_SPELL_BE_A";
                        source="PINNED_CLIENT_SPELL_ON_INVENTORY_ITEM_WRITER";
                        break;
                    default:
                        throw new AssertionError();
                }

                offerTypedRequest(
                    new SpellTargetClientRequest(
                        decoded,
                        ClientRequestMetadata.exactCurrent(
                            opcode,
                            schema,
                            source
                        )
                    ),
                    opcode
                );

                System.out.printf("%sCLIENT_PACKET seq=%d opcode=%d len=%d spellTarget=%s schema=R25_EXACT_CURRENT%n",
                                  tag,decodedCount,opcode,len,decoded);
                return true;
            }

            case 122: {
                // Inventory option 1: widget LE-A, slot BE-A, item LE.
                byte[] body=Binary.readExactly(in,6);
                int widget=leA(body,0), slot=beA(body,2), item=le(body,4);
                String semantic=ItemActionResolver.inventoryOption1Semantic(item);
                offerItemAction(
                    new ItemContainerAction(
                        opcode,
                        widget,
                        slot,
                        item,
                        0,
                        semantic==null?"ITEM_OPTION_1":semantic
                    ),
                    "FIXED6_WIDGET_LE_A_SLOT_BE_A_ITEM_LE",
                    "PINNED_CLIENT_INVENTORY_OPTION_1_WRITER"
                );
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=122 len=6 itemOption1=true widget=%d slot=%d itemId=%d semantic=%s schema=STATIC_EXACT_FIXED6_LEA_BEA_LE%n",
                    tag,decodedCount,widget,slot,item,semantic);
                return true;
            }

            case 17:
            case 21:
            case 18: {
                byte[] body=Binary.readExactly(in,2);
                int sceneIndex;
                String schema;
                int option=NpcInteractionRouter.optionForOpcode(opcode);
                if(opcode==17){
                    sceneIndex=leA(body,0);                 // NPC option 3
                    schema="FIXED2_NPC_SCENE_INDEX_LE_A";
                }else if(opcode==21){
                    sceneIndex=be(body,0);                  // NPC option 4
                    schema="FIXED2_NPC_SCENE_INDEX_BE";
                }else{
                    sceneIndex=le(body,0);                  // NPC option 5
                    schema="FIXED2_NPC_SCENE_INDEX_LE";
                }
                offerTypedRequest(
                    new NpcActionClientRequest(
                        new NpcAction(opcode,sceneIndex),
                        ClientRequestMetadata.exactCurrent(
                            opcode,
                            schema,
                            "PINNED_CLIENT_NPC_OPTION_"+option+"_WRITER"
                        )
                    ),
                    opcode
                );
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=%d len=2 npcOption=%d sceneIndex=%d schema=STATIC_EXACT%n",
                    tag,decodedCount,opcode,option,sceneIndex);
                return true;
            }

            case 156:
            case 23:
            case 236:
            case 253:
            case 79: {
                byte[] body=Binary.readExactly(in,6);
                int option,worldX,worldY,item;
                String requestSchema;
                if(opcode==156){
                    option=1; worldX=beA(body,0); worldY=le(body,2); item=leA(body,4);
                    requestSchema="FIXED6_WORLD_X_BE_A_WORLD_Y_LE_ITEM_LE_A";
                }else if(opcode==23){
                    option=2; worldY=le(body,0); item=le(body,2); worldX=le(body,4);
                    requestSchema="FIXED6_WORLD_Y_LE_ITEM_LE_WORLD_X_LE";
                }else if(opcode==236){
                    option=3; worldY=le(body,0); item=be(body,2); worldX=le(body,4);
                    requestSchema="FIXED6_WORLD_Y_LE_ITEM_BE_WORLD_X_LE";
                }else if(opcode==253){
                    option=4; worldX=le(body,0); worldY=leA(body,2); item=beA(body,4);
                    requestSchema="FIXED6_WORLD_X_LE_WORLD_Y_LE_A_ITEM_BE_A";
                }else{
                    option=5; worldY=le(body,0); item=be(body,2); worldX=beA(body,4);
                    requestSchema="FIXED6_WORLD_Y_LE_ITEM_BE_WORLD_X_BE_A";
                }

                offerTypedRequest(
                    new GroundItemClientRequest(
                        new GroundItemInteraction(
                            opcode,
                            option,
                            item,
                            worldX,
                            worldY
                        ),
                        ClientRequestMetadata.exactCurrent(
                            opcode,
                            requestSchema,
                            "PINNED_CLIENT_GROUND_ITEM_OPTION_"+
                                option+
                                "_WRITER"
                        )
                    ),
                    opcode
                );

                System.out.printf("%sCLIENT_PACKET seq=%d opcode=%d len=6 groundOption=%d item=%d world=%d,%d schema=STATIC_EXACT%n",
                    tag,decodedCount,opcode,option,item,worldX,worldY);
                return true;
            }

            case 72: {
                // Exact current-client NPC Attack writer recovered from the pinned client:
                //   fv.a(72); fv.o(sceneNpcIndex)
                // followed immediately by EntityInteraction.setCombat(true).
                // rs.x.e.o(int) is big-endian short-A (high byte, low byte + 128).
                // Live WORLD-R7 proof: wire 00 03 -> scene 131 (def 1488),
                // 00 01 -> scene 129 (def 1489), 00 06 -> scene 134 (def 1488).
                byte[] body=Binary.readExactly(in,2);
                int sceneIndex=beA(body,0);
                offerTypedRequest(
                    new NpcActionClientRequest(
                        new NpcAction(opcode,sceneIndex),
                        ClientRequestMetadata.exactCurrent(
                            72,
                            "FIXED2_NPC_SCENE_INDEX_BE_A",
                            "PINNED_CLIENT_NPC_OPTION_2_ATTACK_WRITER"
                        )
                    ),
                    opcode
                );
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=72 len=2 npcAttack=true sceneIndex=%d schema=STATIC_EXACT_FIXED2_BE_A%n",
                                  tag,decodedCount,sceneIndex);
                return true;
            }

            case 87: {
                // Exact current-client inventory Drop writer:
                //   a(87); o(itemId); d(widgetId); o(slot)
                // o = big-endian short-A, d = ordinary big-endian short.
                byte[] body=Binary.readExactly(in,6);
                int item=beA(body,0);
                int widget=be(body,2);
                int slot=beA(body,4);
                offerTypedRequest(
                    new DropItemClientRequest(
                        new DropItemAction(
                            item,
                            widget,
                            slot
                        ),
                        ClientRequestMetadata.exactCurrent(
                            87,
                            "FIXED6_ITEM_BE_A_WIDGET_BE_SLOT_BE_A",
                            "PINNED_CLIENT_INVENTORY_DROP_WRITER"
                        )
                    ),
                    opcode
                );
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=87 len=6 inventoryDrop=true widget=%d slot=%d itemId=%d schema=STATIC_EXACT_FIXED6%n",
                                  tag,decodedCount,widget,slot,item);
                return true;
            }

            case 155: {
                // Exact normal first-NPC-option writer used by pet Pick-up.
                // The scene NPC index is a plain little-endian short.
                byte[] body=Binary.readExactly(in,2);
                int sceneIndex=le(body,0);
                offerTypedRequest(
                    new NpcActionClientRequest(
                        new NpcAction(opcode,sceneIndex),
                        ClientRequestMetadata.exactCurrent(
                            155,
                            "FIXED2_NPC_SCENE_INDEX_LE",
                            "PINNED_CLIENT_NPC_OPTION_1_WRITER"
                        )
                    ),
                    opcode
                );
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=155 len=2 npcFirstOption=true sceneIndex=%d schema=STATIC_EXACT_FIXED2_LE%n",
                                  tag,decodedCount,sceneIndex);
                return true;
            }

            case 103: {
                int len = readU8();
                byte[] body = Binary.readExactly(in, len);
                boolean newline = len > 0 && (body[len - 1] & 0xff) == 10;
                int textLen = newline ? len - 1 : len;
                String text = new String(body, 0, textLen, StandardCharsets.ISO_8859_1);

                int dialogueOption=
                    dialogueOptionIndex(text);

                DailyChallengeClientRequest
                    dailyChallenge=
                        dialogueOption>0
                        ?null
                        :dailyChallengeRequest(
                            text
                        );

                ClientRequest request;

                if(dialogueOption>0)
                    request=
                        new DialogueOptionClientRequest(
                            dialogueOption,
                            ClientRequestMetadata.exactCurrent(
                                103,
                                "VAR_BYTE_DIALOGUEOPTION_INDEX_OPTIONAL_LF",
                                "V308_CLIENT_DIALOGUE_OPTION_HOTKEY"
                            )
                        );
                else if(dailyChallenge!=null)
                    request=dailyChallenge;
                else
                    request=
                        new CommandClientRequest(
                            text,
                            ClientRequestMetadata.exactCurrent(
                                103,
                                "VAR_BYTE_ISO_8859_1_OPTIONAL_LF",
                                "PINNED_CLIENT_OPCODE_103_WRITER"
                            )
                        );

                offerTypedRequest(
                    request,
                    opcode
                );

                System.out.printf(
                    "%sCLIENT_PACKET seq=%d opcode=103 len=%d command=%s dialogueOption=%d dailyChallenge=%s newline=%s%n",
                    tag,
                    decodedCount,
                    len,
                    quote(text),
                    dialogueOption,
                    dailyChallenge==null
                        ?"none"
                        :dailyChallenge.action(),
                    newline
                );
                return true;
            }

            case 77: {
                int len = readU8();
                byte[] body = Binary.readExactly(in, len);
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=77 len=%d periodicTelemetry=true payload=%s schema=STATIC_EXACT_VARBYTE%n",
                                  tag, decodedCount, len, hex(body, 64));
                return true;
            }

            case 202:
                // Exact pinned-client long-session writer in Client.C():
                //   if (hK > 4500) { ... fv.a(202); }
                // There are no payload writes between a(202) and the next packet.
                // v5.2 live hit this at seq=377 after ~5 minutes and paused forever.
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=202 len=0 longSessionKeepalive=true schema=STATIC_EXACT_FIXED0%n",
                                  tag, decodedCount);
                return true;

            case 226: {
                // Exact pinned-client writer at both callsites:
                //   fv.a(226); fv.b(0); start=fv.h; ... randomized fields ...; fv.j(fv.h-start)
                // b(0) reserves the single-byte payload length and j(...) backpatches it.
                // Live v4 hit this as seq=81 and paused all subsequent input.
                int len = readU8();
                byte[] body = Binary.readExactly(in, len);
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=226 len=%d periodicTelemetry=true payload=%s schema=STATIC_EXACT_VARBYTE%n",
                                  tag, decodedCount, len, hex(body, 64));
                return true;
            }

            case 128:
            case 153:
            case 73:
            case 139:
            case 39: {
                byte[] body=Binary.readExactly(in,2);
                int slot,playerIndex; String semantic;
                switch(opcode){
                    case 128: slot=1; playerIndex=Binary.u16(body,0); semantic="Attack"; break;
                    case 153: slot=2; playerIndex=(body[0]&255)|((body[1]&255)<<8); semantic="Follow"; break;
                    case 73:  slot=3; playerIndex=(body[0]&255)|((body[1]&255)<<8); semantic="Trade with"; break;
                    case 139: slot=4; playerIndex=(body[0]&255)|((body[1]&255)<<8); semantic="Option 4"; break;
                    default:  slot=5; playerIndex=(body[0]&255)|((body[1]&255)<<8); semantic="Option 5"; break;
                }

                offerTypedRequest(
                    new PlayerActionClientRequest(
                        new PlayerAction(
                            opcode,
                            slot,
                            playerIndex,
                            semantic
                        ),
                        ClientRequestMetadata.exactCurrent(
                            opcode,
                            opcode==128
                                ?"FIXED2_PLAYER_INDEX_BE"
                                :"FIXED2_PLAYER_INDEX_LE",
                            "PINNED_CLIENT_PLAYER_OPTION_"+
                                slot+
                                "_WRITER"
                        )
                    ),
                    opcode
                );

                System.out.printf("%sCLIENT_PACKET seq=%d opcode=%d len=2 playerAction=true slot=%d playerIndex=%d semantic=%s schema=STATIC_EXACT_CURRENT_CLIENT%n",
                                  tag,decodedCount,opcode,slot,playerIndex,semantic);
                return true;
            }

            case 164:
            case 98:
            case 248: {
                int len = readU8();
                byte[] body = Binary.readExactly(in, len);
                logMovement(opcode, body);
                return true;
            }

            default:
                if (consumeFramingOnly(opcode)) return true;
                aligned = false;
                System.out.printf("%sCLIENT_PACKET_UNKNOWN seq=%d opcode=%d encoded=%d payloadLen=UNKNOWN decoder=PAUSED%n",
                                  tag, decodedCount, opcode, encoded);
                return false;
        }
    }

    /**
     * Exact-current-client framing registry for opcodes whose server semantics are
     * not implemented yet. This prevents legitimate packets from desynchronizing
     * the stream while keeping gameplay behavior fail-closed.
     *
     * Authority: pinned client SHA 6232bae...93662, 188 writer callsites,
     * 86 distinct opcodes, zero dynamic opcode sites.
     */
    private boolean consumeFramingOnly(int opcode) throws IOException {
        int genericLength=
            GenericInteractionPacketDecoder.length(opcode);
        if(genericLength>=0){
            byte[] payload=
                Binary.readExactly(in,genericLength);
            GenericInteractionEvent event=
                GenericInteractionPacketDecoder.decode(
                    opcode,
                    payload
                );

            offerTypedRequest(
                new GenericInteractionClientRequest(
                    event,
                    ClientRequestMetadata.exactCurrent(
                        opcode,
                        GenericInteractionPacketDecoder
                            .schema(opcode),
                        GenericInteractionPacketDecoder
                            .source(opcode)
                    )
                ),
                opcode
            );

            System.out.printf(
                "%sCLIENT_PACKET seq=%d opcode=%d len=%d genericInteraction=%s schema=STATIC_EXACT_TYPED%n",
                tag,
                decodedCount,
                opcode,
                genericLength,
                event
            );
            return true;
        }

        if (opcode == 16) {
            byte[] payload = Binary.readExactly(in, 6);

            int itemId   = beA(payload, 0);
            int slot     = leA(payload, 2);
            int widgetId = leA(payload, 4);

            String semantic = itemSemantic(
                widgetId,
                "ITEM_OPTION_3"
            );

            offerItemAction(
                new ItemContainerAction(
                    opcode,
                    widgetId,
                    slot,
                    itemId,
                    0,
                    semantic
                ),
                "FIXED6_ITEM_BE_A_SLOT_LE_A_WIDGET_LE_A",
                "PINNED_CLIENT_OPCODE_16_ITEM_OPTION_3_WRITER"
            );

            System.out.printf(
                "%sCLIENT_PACKET seq=%d opcode=16 len=6 inventoryItemAction=true widget=%d slot=%d itemId=%d semantic=%s schema=STATIC_EXACT_FIXED6%n",
                tag,
                decodedCount,
                widgetId,
                slot,
                itemId,
                semantic
            );

            return true;
        }

        int fixed = framingOnlyFixedLength(opcode);
        if (fixed >= 0) {
            byte[] body = Binary.readExactly(in, fixed);
            System.out.printf("%sCLIENT_PACKET seq=%d opcode=%d len=%d framingOnly=true payload=%s schema=STATIC_EXACT_FIXED%d%n",
                              tag, decodedCount, opcode, fixed, hex(body, 32), fixed);
            return true;
        }
        if (opcode == 246) {
            int len = readU8();
            byte[] body = Binary.readExactly(in, len);
            System.out.printf("%sCLIENT_PACKET seq=%d opcode=%d len=%d framingOnly=true payload=%s schema=STATIC_EXACT_VARBYTE%n",
                              tag, decodedCount, opcode, len, hex(body, 32));
            return true;
        }
        return false;
    }

    /** Fixed body lengths for exact-current-client framing-only packets. */
    static int framingOnlyFixedLength(int opcode) {
        switch (opcode) {
            case 78: case 136: case 148: case 150:
                return 0;
            case 85: case 120: case 152: case 189: case 230:
                return 1;
            case 2: case 6: case 17: case 18: case 21: case 39:
            case 73: case 128: case 139: case 153: case 200:
                return 2;
            case 183:
                return 3;
            case 86: case 131: case 210: case 249:
                return 4;
            case 16: case 23: case 70: case 79: case 122:
            case 156: case 176: case 228: case 234: case 236: case 252: case 253:
                return 6;
            case 14: case 35: case 57: case 181: case 237:
                return 8;

            case 25: case 109: case 192:
                return 12;
            default:
                return -1;
        }
    }

    static int dialogueOptionIndex(String text){
        if(text==null)return -1;
        String prefix="dialogueoption ";
        if(text.length()!=prefix.length()+1||
           !text.startsWith(prefix))
            return -1;
        char value=text.charAt(prefix.length());
        return value>='1'&&value<='5'
            ? value-'0'
            : -1;
    }

    static DailyChallengeClientRequest
        dailyChallengeRequest(
            String text
        ){
        if(text==null)
            return null;

        String claimPrefix=
            "claimchallenge ";
        String infoPrefix=
            "infochallenge ";

        DailyChallengeClientRequest.Action action;
        int keyOffset;

        if(text.startsWith(
                claimPrefix)){
            action=
                DailyChallengeClientRequest.Action.CLAIM;
            keyOffset=
                claimPrefix.length();
        }else if(text.startsWith(
                infoPrefix)){
            action=
                DailyChallengeClientRequest.Action.INFO;
            keyOffset=
                infoPrefix.length();
        }else{
            return null;
        }

        if(keyOffset>=text.length())
            return null;

        String key=
            text.substring(
                keyOffset
            );

        return new DailyChallengeClientRequest(
            action,
            key,
            ClientRequestMetadata.exactCurrent(
                103,
                "VAR_BYTE_DAILY_CHALLENGE_ACTION_KEY_OPTIONAL_LF",
                "V308_CLIENT_DAILY_CHALLENGE_COMPAT_COMMAND"
            )
        );
    }

    static boolean isFramingOnlyVarByte(int opcode) {
        return opcode == 246;
    }

    static ItemOnNpcAction decodeItemOnNpc(byte[] body){
        if(body==null||body.length!=8)throw new IllegalArgumentException("opcode57 len="+(body==null?-1:body.length));
        return new ItemOnNpcAction(beA(body,0),le(body,4),beA(body,6),beA(body,2));
    }

    static SpellTargetRequest decodeSpellTarget(int opcode,byte[] body){
        if(body==null)throw new IllegalArgumentException("body");
        switch(opcode){
            case 249:
                if(body.length!=4)throw new IllegalArgumentException("opcode249 len="+body.length);
                return new SpellTargetRequest(opcode,SpellTargetRequest.Kind.PLAYER,le(body,2),beA(body,0),-1,-1,-1,-1,-1);
            case 131:
                if(body.length!=4)throw new IllegalArgumentException("opcode131 len="+body.length);
                return new SpellTargetRequest(opcode,SpellTargetRequest.Kind.NPC,beA(body,2),leA(body,0),-1,-1,-1,-1,-1);
            case 35:
                if(body.length!=8)throw new IllegalArgumentException("opcode35 len="+body.length);
                return new SpellTargetRequest(opcode,SpellTargetRequest.Kind.OBJECT,beA(body,2),-1,le(body,6),-1,-1,le(body,0),beA(body,4));
            case 181:
                if(body.length!=8)throw new IllegalArgumentException("opcode181 len="+body.length);
                return new SpellTargetRequest(opcode,SpellTargetRequest.Kind.GROUND_ITEM,beA(body,6),-1,be(body,2),-1,-1,le(body,4),le(body,0));
            case 237:
                if(body.length!=8)throw new IllegalArgumentException("opcode237 len="+body.length);
                return new SpellTargetRequest(opcode,SpellTargetRequest.Kind.INVENTORY_ITEM,beA(body,6),-1,beA(body,2),be(body,4),be(body,0),-1,-1);
            default:
                throw new IllegalArgumentException("not spell-target opcode "+opcode);
        }
    }

    private static String itemSemantic(int widget, String amount) {
        if (widget == BankState.BANK_CONTAINER) return "WITHDRAW_" + amount;
        if (widget == BankState.BANK_INVENTORY_CONTAINER) return "STORE_" + amount;
        if (widget == EquipmentState.EQUIPMENT_WIDGET && "1".equals(amount)) return "REMOVE_EQUIPMENT";
        return "ITEM_ACTION_" + amount;
    }

    private static int be(byte[] b,int o){ return ((b[o]&255)<<8)|(b[o+1]&255); }
    private static int le(byte[] b,int o){ return (b[o]&255)|((b[o+1]&255)<<8); }
    private static int beA(byte[] b,int o){ return ((b[o]&255)<<8)|(((b[o+1]&255)-128)&255); }
    private static int leA(byte[] b,int o){ return (((b[o]&255)-128)&255)|((b[o+1]&255)<<8); }
    private static int be32(byte[] b,int o){ return ((b[o]&255)<<24)|((b[o+1]&255)<<16)|((b[o+2]&255)<<8)|(b[o+3]&255); }

    private void offerItemAction(
        ItemContainerAction action,
        String schema,
        String source
    )throws IOException{
        offerTypedRequest(
            new ItemContainerActionClientRequest(
                action,
                ClientRequestMetadata.exactCurrent(
                    action.opcode,
                    schema,
                    source
                )
            ),
            action.opcode
        );
    }

    private void offerTypedRequest(
        ClientRequest request,
        int opcode
    )throws IOException{
        if(typedRequests.offer(request))
            return;

        aligned=false;
        throw new IOException(
            "CLIENT_REQUEST_QUEUE_FULL capacity="+
            typedRequests.capacity()+
            " opcode="+opcode+
            " decodedCount="+decodedCount
        );
    }

    private int readU8() throws IOException {
        int v = in.read();
        if (v < 0) throw new EOFException("EOF reading packet length");
        return v;
    }

    /**
     * Packet 164 and 98 share the classic var-byte walking body. Packet 248 uses
     * the same core body followed by an exact 14-byte minimap telemetry extension.
     * v2 preserves that extension as opaque evidence while accepting the path core.
     *
     * v2 reconstructs absolute turning-point waypoints but does not itself move
     * anything. LocalSession owns the authoritative movement state/tick.
     */
    private void logMovement(int opcode, byte[] body) throws IOException {
        int coreLen = body.length;
        byte[] telemetry = new byte[0];
        if (opcode == 248) {
            // Exact writer proof: minimap walk uses the same ordinary walk serializer
            // but declares +14 payload bytes, which its caller appends after the core.
            if (body.length < 17) {
                System.out.printf("%sCLIENT_PACKET seq=%d opcode=248 len=%d movementShape=INVALID_MINIMAP payload=%s%n",
                                  tag, decodedCount, body.length, hex(body, 96));
                return;
            }
            coreLen = body.length - 14;
            telemetry = java.util.Arrays.copyOfRange(body, coreLen, body.length);
        }

        if (coreLen < 3 || ((coreLen - 3) & 1) != 0) {
            System.out.printf("%sCLIENT_PACKET seq=%d opcode=%d len=%d movementShape=INVALID payload=%s%n",
                              tag, decodedCount, opcode, body.length, hex(body, 64));
            return;
        }
        int steps = (coreLen - 3) / 2;
        if (steps < 1) {
            System.out.printf("%sCLIENT_PACKET seq=%d opcode=%d len=%d movementSteps=%d payload=%s%n",
                              tag, decodedCount, opcode, body.length, steps, hex(body, 64));
            return;
        }

        // Exact writer order from the pinned client's path serializer:
        //   p(firstWaypointX), (steps-1)*(dx,dy relative to first waypoint),
        //   n(firstWaypointY), l(runFlag), then +14 telemetry for opcode 248.
        int startX = (((body[0] & 0xff) - 128) & 0xff) | ((body[1] & 0xff) << 8);
        int yPos = 2 + (steps - 1) * 2;
        int startY = (body[yPos] & 0xff) | ((body[yPos + 1] & 0xff) << 8);
        int runByte = (-(body[yPos + 2] & 0xff)) & 0xff;
        boolean run = runByte != 0;

        int[] xs = new int[steps];
        int[] ys = new int[steps];
        xs[0] = startX; ys[0] = startY;
        StringBuilder deltas = new StringBuilder();
        for (int i = 0; i < steps - 1; i++) {
            int dx = body[2 + i * 2];
            int dy = body[3 + i * 2];
            xs[i+1] = startX + dx;
            ys[i+1] = startY + dy;
            if (i != 0) deltas.append(';');
            deltas.append(dx).append(',').append(dy);
        }
        MovementRequest movement=
            new MovementRequest(
                opcode,
                run,
                xs,
                ys,
                telemetry
            );

        String schema=
            opcode==248
                ?"VARBYTE_PATH_X_LE_A_SIGNED_DELTAS_Y_LE_RUN_NEG_PLUS_OPAQUE14"
                :"VARBYTE_PATH_X_LE_A_SIGNED_DELTAS_Y_LE_RUN_NEG";

        String source;
        switch(opcode){
            case 164:
                source="PINNED_CLIENT_MOVEMENT_OPCODE_164_WRITER";
                break;
            case 98:
                source="PINNED_CLIENT_MOVEMENT_OPCODE_98_WRITER";
                break;
            case 248:
                source="PINNED_CLIENT_MINIMAP_MOVEMENT_OPCODE_248_WRITER";
                break;
            default:
                throw new AssertionError(
                    "unexpected movement opcode="+opcode
                );
        }

        offerTypedRequest(
            new MovementClientRequest(
                movement,
                ClientRequestMetadata.exactCurrent(
                    opcode,
                    schema,
                    source
                )
            ),
            opcode
        );

        System.out.printf("%sCLIENT_PACKET seq=%d opcode=%d len=%d movementFamily=true steps=%d startX=%d startY=%d run=%d deltas=[%s] decodedWaypoints=true%s%n",
                          tag, decodedCount, opcode, body.length, steps, startX, startY, run?1:0, deltas,
                          opcode==248 ? " telemetry14="+hex(telemetry, 32)+" minimapPath=ACCEPTABLE" : "");
    }

    void drainAvailableAsRaw(String reason) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        while (in.available() > 0 && raw.size() < 256) {
            int b = in.read();
            if (b < 0) break;
            raw.write(b);
        }
        byte[] data = raw.toByteArray();
        if (data.length > 0) {
            System.out.printf("%sCLIENT_RAW reason=%s bytes=%d hex=%s%n",
                              tag, reason, data.length, hex(data, 96));
        }
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    static String hex(byte[] data, int max) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(data.length, max);
        for (int i = 0; i < n; i++) {
            if (i != 0) sb.append(' ');
            sb.append(String.format("%02X", data[i] & 0xff));
        }
        if (data.length > n) sb.append(" ...");
        return sb.toString();
    }
}

final class ItemOnNpcAction {
    final int itemId,slot,widgetId,targetNpcIndex;
    ItemOnNpcAction(int itemId,int slot,int widgetId,int targetNpcIndex){
        this.itemId=itemId;this.slot=slot;this.widgetId=widgetId;this.targetNpcIndex=targetNpcIndex;
    }
    public String toString(){return "ItemOnNpcAction{itemId="+itemId+",slot="+slot+",widgetId="+widgetId+",targetNpcIndex="+targetNpcIndex+"}";}
}
