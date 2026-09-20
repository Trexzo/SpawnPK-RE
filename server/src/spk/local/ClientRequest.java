package spk.local;

/** Typed inbound request emitted after exact packet decoding. */
interface ClientRequest {
    ClientRequestMetadata metadata();
}
