import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
export const failureRate = new Rate('failed_requests');
export const gatewayLatency = new Trend('gateway_latency_ms');

export const options = {
  stages: [
    { duration: '5s', target: 20 },  // Ramp-up to 20 VUs
    { duration: '15s', target: 50 }, // Ramp-up and sustain at 50 VUs
    { duration: '5s', target: 0 },   // Ramp-down
  ],
  thresholds: {
    'http_req_duration': ['p(95)<100', 'p(99)<250'], // 95% of requests under 100ms
    'failed_requests': ['rate<0.01'],                 // Less than 1% errors (429 is not error)
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || '';

export function setup() {
  if (API_KEY) {
    return { apiKey: API_KEY };
  }

  // 1. Register developer
  const email = `k6_dev_${Date.now()}@example.com`;
  const devRes = http.post(`${BASE_URL}/api/v1/developers/register`, JSON.stringify({
    email: email,
    password: 'K6TestPassword123!',
  }), { headers: { 'Content-Type': 'application/json' } });

  // 2. Login
  const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
    email: email,
    password: 'K6TestPassword123!',
  }), { headers: { 'Content-Type': 'application/json' } });

  const token = loginRes.json('token');

  // 3. Get plans
  const plansRes = http.get(`${BASE_URL}/api/v1/plans`);
  const plans = plansRes.json();
  const enterprisePlan = plans.reduce((prev, curr) => 
    (curr.requestsPerWindow > prev.requestsPerWindow) ? curr : prev
  );

  // 4. Create API Key
  const keyRes = http.post(`${BASE_URL}/api/v1/keys`, JSON.stringify({
    name: 'k6-load-key',
    planId: enterprisePlan.id,
  }), {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  return { apiKey: keyRes.json('rawKey') };
}

export default function (data) {
  const params = {
    headers: {
      'X-API-Key': data.apiKey,
      'Accept': 'application/json',
    },
  };

  const res = http.get(`${BASE_URL}/api/v1/protected/hello`, params);

  gatewayLatency.add(res.timings.duration);

  // Check valid response (either 200 OK or 429 Rate Limited)
  const isExpected = res.status === 200 || res.status === 429;
  check(res, {
    'status is 200 or 429': () => isExpected,
    'has rate limit limit header': () => res.headers['X-RateLimit-Limit'] !== undefined,
    'has rate limit remaining header': () => res.headers['X-RateLimit-Remaining'] !== undefined,
  });

  if (!isExpected) {
    failureRate.add(1);
  } else {
    failureRate.add(0);
  }

  sleep(0.01);
}
