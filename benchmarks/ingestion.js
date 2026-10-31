import http from 'k6/http';
import { check } from 'k6';
export const options = { vus: Number(__ENV.VUS || 10), duration: __ENV.DURATION || '1m' };
export default function () { const r=http.post(`${__ENV.RELAY_URL}/v1/events`, JSON.stringify({type:'benchmark.event',payload:{sequence:__ITER}}), {headers:{'Content-Type':'application/json','X-Relay-Api-Key':__ENV.RELAY_API_KEY,'Idempotency-Key':crypto.randomUUID()}}); check(r,{'accepted':x=>x.status===202}); }
