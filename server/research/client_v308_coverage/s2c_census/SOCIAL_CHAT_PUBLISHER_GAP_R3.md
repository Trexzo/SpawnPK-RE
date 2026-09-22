# Exact v308 S2C Social / Chat Publisher Gap — R3

SYSTEM

Exact-current friend/ignore/chat/message S2C presentation families handled by v308 but not exposed as normalized current-main LocalLab publishers.

STATUS

EXACT-CLIENT-SCHEMAS-CLOSED / CURRENT-MAIN-SOCIAL-CHAT-PUBLISHER-GAP

## AUTHORITY

~~~
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
handler rs.Client#bP()
buffer rs.x.e
~~~

Current-main baseline audited:

~~~
1ca841fdb413a06017b4729d6c5a286592f28aa1
~~~

## EXACT READER TRANSFORMS

~~~
y() = raw u8
N() = (wire - 128) & 255
O() = (-wire) & 255
D() = i32/u32 BE bit pattern
E() = 64-bit BE, implemented as two D() words
F() = newline-terminated string
~~~

## S2C50 — friend presence update

Frame: FIXED_9

Exact reads:

~~~
nameKey = E()
status  = y()
~~~

Client behavior:

- resolves the long name key to display username;
- inserts/updates the friend row;
- sorts friend rows by status/current-world presentation rules;
- current exact status vocabulary previously recovered:
  - 0 = Offline
  - 10 = Online
  - 11 = AFK.

Suggested semantic publisher:

~~~
friendPresence(nameKey, status)
~~~

Server/domain code should use semantic presence state and keep the legacy long-name encoding inside the adapter.

## S2C104 — player interaction option

Frame: VAR_BYTE

Exact reads:

~~~
slot        = O()
rawFlag     = N()
optionText  = F()
~~~

Exact behavior:

- valid slots are 1..5;
- case-insensitive text `null` becomes no option;
- client stores a boolean for the slot as `(rawFlag == 0)`.

Suggested internal publisher:

~~~
playerInteractionOption(slot, optionText, rawFlagSemantic)
~~~

Do not guess a business name for the boolean beyond its exact client effect unless another read/write site proves it.

## S2C196 — private message receive

Frame: VAR_BYTE

Exact reads:

~~~
senderNameKey = E()
messageId     = D()
senderCode    = D()
messageText   = F()
~~~

Exact behavior:

- messageId is stored in a 100-entry recent-message ring for duplicate suppression;
- ignore-list filtering is checked against senderNameKey for ordinary sender codes;
- senderCode participates in rank/type presentation;
- message text supports an exact `<N>` split form that can override the displayed sender-name prefix;
- senderCode > 0 uses ranked/private-message presentation; zero uses ordinary private-message presentation.

Suggested semantic publisher:

~~~
privateMessage(senderIdentity, messageId, senderPresentationCode, text)
~~~

The server should generate stable message identity and own authorization/privacy; the client-side duplicate ring is presentation defense, not authoritative delivery semantics.

## S2C206 — chat mode state

Frame: FIXED_3

Exact reads, in order:

~~~
publicMode  = y()
privateMode = y()
tradeMode   = y()
~~~

These identities are corroborated by exact client chat-menu state:

- public: On / Friends / Off / Hide;
- private: On / Friends / Off;
- trade: On / Friends / Off.

Suggested publisher:

~~~
chatModes(publicMode, privateMode, tradeMode)
~~~

## S2C214 — full ignore list

Frame: VAR_SHORT

Exact behavior:

~~~
count = frameLength / 8
repeat count times:
  ignoredNameKey = E()
~~~

There is no header count field in the body.

Suggested publisher:

~~~
fullIgnoreList(nameKeys[])
~~~

Frame length must therefore remain an exact multiple of 8.

## S2C221 — friend-server connection status

Frame: FIXED_1

Exact read:

~~~
status = y()
~~~

Exact client UI meaning:

~~~
0 -> Loading friend list
1 -> Connecting to friendserver
2 -> active/list-ready state
~~~

Suggested publisher:

~~~
friendServerStatus(status)
~~~

## S2C253 — server message / legacy request-control text

Frame: VAR_BYTE

Exact read:

~~~
text = F()
~~~

Exact suffix controls recovered in this handler:

~~~
:tradereq:
:duelreq:
:cwarreq:
:whipddsreq:
:whipduelreq:
:gambreq:
:chalreq:
~~~

The client converts these strings into specific local request-message presentation after ignore/block checks.

Examples include:

- wishes to trade with you;
- wishes to duel with you;
- wishes to challenge your clan to a Clan War;
- wishes to whip + dds duel with you;
- wishes to whip duel with you;
- wishes to gamble with you;
- challenge payload presentation.

Other strings fall through to ordinary server-message display.

Suggested architecture:

~~~
ServerMessagePresentation.text(...)
LegacyRequestPresentation.tradeRequest(...)
LegacyRequestPresentation.duelRequest(...)
LegacyRequestPresentation.clanWarRequest(...)
...
~~~

Do not make colon-suffix strings the public domain API.

## CURRENT-MAIN AUDIT

Current `main` was checked through both exact opcode-writer searches and semantic source-tree inspection.

No normalized generic publisher implementation was found for:

~~~
50, 104, 196, 206, 214, 221, 253
~~~

The current server tree also has no dedicated friend/ignore/social/chat presentation classes; the only message-named server source in the canonical tree is unrelated reward-delivery state.

Use status:

~~~
CURRENT_MAIN_SOCIAL_CHAT_PUBLISHER_GAP_R3
~~~

rather than claiming the exact client capability is absent.

## ARCHITECTURE BOUNDARY

These publishers do not define social policy.

Chat 2 owns exact runtime/presentation plumbing.

Chat 3 should own server-authoritative:

- friend/ignore aggregate;
- persistence;
- privacy/permission rules;
- presence semantics;
- PM authorization/routing;
- reconnect/offline behavior.

Do not make the client's 350-friend / 100-ignore local capacities authoritative server policy merely because they are client-visible.

## ACCEPTANCE

Focused publisher tests should cover:

- S2C50 exact 8-byte name key + status byte;
- S2C104 O/N byte transforms and `null` option;
- S2C196 64-bit sender key + two BE32 fields + newline text;
- S2C206 byte ordering public/private/trade;
- S2C214 zero-entry and N-entry lists; payload length exactly N*8;
- S2C221 statuses 0/1/2;
- S2C253 newline termination and legacy suffix vectors;
- no raw opcode or suffix vocabulary in public content/plugin APIs.

## READY FOR CHAT 2

yes

## READY FOR CHAT 3

yes after presentation publishers exist; server social policy remains a separate domain concern.