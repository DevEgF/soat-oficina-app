import assert from 'node:assert/strict';
import {createHash, createHmac} from 'node:crypto';

const base = new URL(process.argv[2] || 'http://localhost:18080');
assert(base.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(base.hostname), 'Fixture smoke is limited to local CI');
// This committed development sentinel is accepted only by the local/test profile.
const key = createHash('sha256').update('local-oficina-jwt-secret-for-development-only').digest();
const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
const issued = Math.floor(Date.now() / 1000);
const payload = `${encode({alg:'HS256',typ:'JWT'})}.${encode({iss:'oficina',aud:'oficina-api',sub:'00000000-0000-4000-8000-000000000001',env:'hml',scope:['CUSTOMER'],iat:issued,exp:issued+300})}`;
const token = `${payload}.${createHmac('sha256',key).update(payload).digest('base64url')}`;
for (const suffix of ['1001','1002','1003']) {
  const code = `00000000-0000-4000-8000-00000000${suffix}`;
  const response = await fetch(new URL(`/api/customer/os/acompanhar?codigo=${code}`,base), {
    headers:{authorization:`Bearer ${token}`}, redirect:'error', signal:AbortSignal.timeout(10000),
  });
  assert.equal(response.status,200,`Fixture ${suffix} must be readable by its owner`);
  const order = await response.json();
  assert.equal(order.trackingCode,code);
  assert.equal(order.status,'PENDING_APPROVAL');
  assert.equal(order.totalCents,1000);
}
console.log('All three synthetic work orders are readable and awaiting customer approval');
