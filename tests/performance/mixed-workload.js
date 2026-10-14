import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const tenants = JSON.parse(__ENV.TENANTS_JSON || '[]');
const functionalErrors = new Rate('functional_errors');
const tenantThrottles = new Counter('tenant_throttles');

export const options = {
  scenarios: {
    query: { executor: 'constant-arrival-rate', rate: 20, timeUnit: '1s', duration: '2m', preAllocatedVUs: 20, exec: 'query' },
    sync: { executor: 'constant-arrival-rate', rate: 1, timeUnit: '5s', duration: '2m', preAllocatedVUs: 4, exec: 'sync' },
    ai: { executor: 'constant-arrival-rate', rate: 1, timeUnit: '2s', duration: '2m', preAllocatedVUs: 8, exec: 'ai' },
    download: { executor: 'constant-arrival-rate', rate: 2, timeUnit: '1s', duration: '2m', preAllocatedVUs: 4, exec: 'download' },
  },
  thresholds: {
    'http_req_duration{workload:query}': ['p(95)<3000'],
    'http_req_duration{workload:ai}': ['p(95)<20000'],
    'http_req_failed': ['rate<0.01'],
    'functional_errors': ['rate<0.01'],
  },
};

function tenant() {
  if (tenants.length < 20) {
    throw new Error('TENANTS_JSON must contain at least 20 isolated tenant fixtures');
  }
  return tenants[(__VU - 1) % tenants.length];
}

function headers(item, workload, idempotencyKey) {
  const value = {
    Authorization: `Bearer ${item.token}`,
    'X-Tenant-Id': String(item.tenantId),
    'Content-Type': 'application/json',
    'X-Load-Workload': workload,
  };
  if (idempotencyKey) value['Idempotency-Key'] = idempotencyKey;
  return value;
}

function accepted(response, expected) {
  if (response.status === 429) tenantThrottles.add(1, response.tags);
  const ok = check(response, { 'expected status': (r) => expected.includes(r.status) || r.status === 429 });
  functionalErrors.add(!ok, response.tags);
}

export function query() {
  const item = tenant();
  const response = http.post(`${baseUrl}/api/queries/plans/execute`, JSON.stringify(item.queryPlan), {
    headers: headers(item, 'query'), tags: { workload: 'query', tenant: String(item.tenantId) },
  });
  accepted(response, [200]);
}

export function sync() {
  const item = tenant();
  const key = `load-sync-${item.tenantId}-${__VU}-${__ITER}`;
  const response = http.post(`${baseUrl}/api/sync-jobs/${item.syncJobId}/run`, null, {
    headers: headers(item, 'sync', key), tags: { workload: 'sync', tenant: String(item.tenantId) },
  });
  accepted(response, [202]);
}

export function ai() {
  const item = tenant();
  const key = `load-ai-${item.tenantId}-${__VU}-${__ITER}`;
  const response = http.post(`${baseUrl}/api/ai/conversations/${item.conversationId}/messages`,
    JSON.stringify({ question: '本月净销售额是多少？', datasetId: item.datasetId }), {
      headers: headers(item, 'ai', key), tags: { workload: 'ai', tenant: String(item.tenantId) },
    });
  accepted(response, [202]);
}

export function download() {
  const item = tenant();
  const response = http.get(`${baseUrl}/api/files/${item.downloadFileId}/content`, {
    headers: headers(item, 'download'), tags: { workload: 'download', tenant: String(item.tenantId) },
  });
  accepted(response, [200]);
}

