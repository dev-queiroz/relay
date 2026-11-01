import { createHmac, timingSafeEqual } from "node:crypto";

export type ReceiverHeaders = { deliveryId?: string; timestamp?: string; signature?: string };
export class RelayReceiver {
  private readonly seen = new Set<string>();
  constructor(private readonly secret: string, private readonly now: () => number = () => Math.floor(Date.now() / 1000), private readonly freshnessSeconds = 300) {}
  verify(headers: ReceiverHeaders, body: Uint8Array): "accepted" | "duplicate" | "rejected" {
    if (!headers.deliveryId || !headers.timestamp || !headers.signature || !/^\d+$/.test(headers.timestamp)) return "rejected";
    if (Math.abs(this.now() - Number(headers.timestamp)) > this.freshnessSeconds) return "rejected";
    const expected = `v1=${createHmac("sha256", this.secret).update(`${headers.timestamp}.`).update(body).digest("hex")}`;
    if (expected.length !== headers.signature.length || !timingSafeEqual(Buffer.from(expected), Buffer.from(headers.signature))) return "rejected";
    if (this.seen.has(headers.deliveryId)) return "duplicate";
    this.seen.add(headers.deliveryId); return "accepted";
  }
}
