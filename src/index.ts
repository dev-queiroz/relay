export type Problem = { type: string; title: string; status: number; detail: string; instance?: string };
export class RelayError extends Error { constructor(public readonly problem: Problem, public readonly retryAfter?: number) { super(problem.detail); this.name = "RelayError"; } }
export class RelayNetworkError extends Error { constructor(message: string, public readonly ambiguousCommit = false) { super(message); this.name = "RelayNetworkError"; } }
export type FetchLike = (input: string, init?: RequestInit) => Promise<Response>;
export type Endpoint = { id: string; name: string; destinationUrl: string; enabled: boolean; createdAt: string };
export type Delivery = { id: string; eventId: string; endpointId: string; state: "PENDING"|"IN_FLIGHT"|"RETRY_WAIT"|"SUCCEEDED"|"EXHAUSTED"; attemptsCount: number; nextAttemptAt: string|null; createdAt: string; updatedAt: string };
export type Attempt = { id: string; deliveryId: string; sequenceNumber: number; startedAt: string; finishedAt: string|null; outcome: string|null; responseStatus: number|null; sanitizedErrorCode: string|null };
export type Page<T> = { data: T[]; page: { hasMore: boolean; nextCursor: string|null } };
export type RelayClientOptions = { baseUrl: string; apiKey: string; timeoutMs?: number; fetch?: FetchLike; retryAttempts?: number };

export class RelayClient {
  private readonly base: string; private readonly fetcher: FetchLike; private readonly timeout: number; private readonly attempts: number; private readonly apiKeyValue: string;
  constructor(options: RelayClientOptions) { this.base = options.baseUrl.replace(/\/$/, ""); this.fetcher = options.fetch ?? fetch; this.timeout = options.timeoutMs ?? 10_000; this.attempts = options.retryAttempts ?? 3; this.apiKeyValue = options.apiKey; }
  async acceptEvent(input: { type: string; payload: Record<string, unknown> }, idempotencyKey = crypto.randomUUID()): Promise<{ eventId: string; status: "accepted" }> {
    const body = JSON.stringify(input); return this.request("/v1/events", { method: "POST", headers: { "Idempotency-Key": idempotencyKey, "Content-Type": "application/json" }, body }, true);
  }
  async createEndpoint(input: { name: string; destinationUrl: string }): Promise<Endpoint & { signingSecret: string }> { return this.request("/v1/endpoints", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) }, false); }
  async rotateSigningSecret(endpointId: string): Promise<{ signingSecret: string }> { return this.request(`/v1/endpoints/${endpointId}/signing-secret/rotate`, { method: "POST" }, false); }
  listEndpoints(limit?: number, cursor?: string): Promise<Page<Endpoint>> { return this.getPage("/v1/endpoints", limit, cursor); }
  listEvents(limit?: number, cursor?: string): Promise<Page<{ id:string; type:string; status:"ACCEPTED"; createdAt:string }>> { return this.getPage("/v1/events", limit, cursor); }
  getDelivery(id: string): Promise<Delivery> { return this.request(`/v1/deliveries/${id}`, {}, false); }
  listAttempts(id: string, limit?: number, cursor?: string): Promise<Page<Attempt>> { return this.getPage(`/v1/deliveries/${id}/attempts`, limit, cursor); }
  private getPage<T>(path:string, limit?:number, cursor?:string):Promise<Page<T>> { const q=new URLSearchParams(); if(limit!==undefined)q.set("limit",String(limit)); if(cursor)q.set("cursor",cursor); return this.request(path+(q.size?`?${q}`:""),{},false); }
  private async request<T>(path:string, init:RequestInit, retry:boolean):Promise<T> { let last: unknown; for(let n=0;n<(retry?this.attempts:1);n++) { try { const response=await this.fetchWithTimeout(path,init); if(response.ok) return await response.json() as T; const problem=await this.problem(response); const error=new RelayError(problem, retryAfter(response)); if(retry && (response.status===429||response.status===503) && n+1<this.attempts) { await delay(error.retryAfter ? error.retryAfter*1000 : 50); continue; } throw error; } catch(error) { if(error instanceof RelayError) throw error; last=error; if(!retry || n+1===this.attempts) throw new RelayNetworkError("Network failure while accepting event; the commit outcome may be ambiguous", true); await delay(50); } } throw last; }
  private async fetchWithTimeout(path:string, init:RequestInit):Promise<Response> { const controller=new AbortController(); const timer=setTimeout(()=>controller.abort(),this.timeout); try { return await this.fetcher(this.base+path,{...init,headers:{"X-Relay-Api-Key":this.apiKey(),...init.headers},signal:controller.signal}); } finally { clearTimeout(timer); } }
  private apiKey():string { return this.apiKeyValue; }
  private async problem(response:Response):Promise<Problem> { try{return await response.json() as Problem;}catch{return {type:"about:blank",title:"HTTP error",status:response.status,detail:"Relay request failed"};} }
}
function retryAfter(response:Response):number|undefined { const value=response.headers.get("Retry-After"); const seconds=value?Number(value):NaN; return Number.isFinite(seconds)&&seconds>=0?seconds:undefined; }
function delay(ms:number):Promise<void>{return new Promise(resolve=>setTimeout(resolve,ms));}
