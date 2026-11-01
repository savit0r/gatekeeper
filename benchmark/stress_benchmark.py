import http.client
import json
import statistics
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.parse import urlparse

BASE_URL = "http://localhost:8080"

def make_request(method, path, headers=None, body=None):
    parsed = urlparse(BASE_URL)
    conn = http.client.HTTPConnection(parsed.hostname, parsed.port, timeout=10)
    data = json.dumps(body) if body else None
    req_headers = headers or {}
    if data and "Content-Type" not in req_headers:
        req_headers["Content-Type"] = "application/json"
    conn.request(method, path, body=data, headers=req_headers)
    resp = conn.getresponse()
    resp_body = resp.read().decode("utf-8")
    resp_headers = dict(resp.getheaders())
    conn.close()
    return resp.status, resp_headers, resp_body

def setup_test_key():
    email = f"benchmark_{int(time.time())}@gatekeeper.dev"
    password = "BenchmarkSecret123!"

    # 1. Register developer
    status, _, body = make_request("POST", "/api/v1/developers/register", body={
        "email": email,
        "password": password
    })
    if status != 201:
        raise RuntimeError(f"Failed to register dev: {status} - {body}")

    # 2. Login to get token
    status, _, body = make_request("POST", "/api/v1/auth/login", body={
        "email": email,
        "password": password
    })
    if status != 200:
        raise RuntimeError(f"Failed to login: {status} - {body}")
    token = json.loads(body)["token"]

    # 3. Get plans (pick Enterprise or Pro for high limit test)
    status, _, body = make_request("GET", "/api/v1/plans")
    plans = json.loads(body)
    # Pick highest limit plan
    plans.sort(key=lambda p: p["requestsPerWindow"], reverse=True)
    target_plan = plans[0]

    # 4. Create key
    status, _, body = make_request("POST", "/api/v1/keys", headers={
        "Authorization": f"Bearer {token}"
    }, body={
        "name": "Stress Benchmark Key",
        "planId": target_plan["id"]
    })
    if status != 201:
        raise RuntimeError(f"Failed to create key: {status} - {body}")
    raw_key = json.loads(body)["rawKey"]
    return raw_key, target_plan

def send_protected_request(raw_key):
    start = time.perf_counter()
    parsed = urlparse(BASE_URL)
    conn = http.client.HTTPConnection(parsed.hostname, parsed.port, timeout=10)
    try:
        conn.request("GET", "/api/v1/protected/hello", headers={"X-API-Key": raw_key})
        resp = conn.getresponse()
        resp.read()
        duration_ms = (time.perf_counter() - start) * 1000.0
        status = resp.status
        limit = resp.getheader("X-RateLimit-Limit")
        remaining = resp.getheader("X-RateLimit-Remaining")
        conn.close()
        return status, duration_ms, limit, remaining, None
    except Exception as e:
        duration_ms = (time.perf_counter() - start) * 1000.0
        return 0, duration_ms, None, None, str(e)

def run_stress_test(raw_key, plan_info, total_requests=1000, concurrency=50):
    print("=" * 65)
    print("GATEKEEPER HIGH-CONCURRENCY VIRTUAL THREAD STRESS BENCHMARK")
    print("=" * 65)
    print(f"Target URL:         {BASE_URL}/api/v1/protected/hello")
    print(f"Usage Plan:         {plan_info['name']} ({plan_info['requestsPerWindow']} req / {plan_info['windowSeconds']}s)")
    print(f"Total Requests:     {total_requests}")
    print(f"Concurrency Level:  {concurrency} virtual client threads")
    print("-" * 65)

    latencies = []
    status_counts = {}
    errors = 0

    overall_start = time.perf_counter()

    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(send_protected_request, raw_key) for _ in range(total_requests)]
        for f in as_completed(futures):
            status, duration_ms, limit, remaining, err = f.result()
            latencies.append(duration_ms)
            if err:
                errors += 1
            status_counts[status] = status_counts.get(status, 0) + 1

    overall_duration = time.perf_counter() - overall_start
    throughput = len(latencies) / overall_duration

    latencies.sort()
    p50 = latencies[int(len(latencies) * 0.50)]
    p90 = latencies[int(len(latencies) * 0.90)]
    p95 = latencies[int(len(latencies) * 0.95)]
    p99 = latencies[int(len(latencies) * 0.99)]
    avg_lat = statistics.mean(latencies)

    print("\nBENCHMARK RESULTS:")
    print(f"  Total Duration:     {overall_duration:.3f} s")
    print(f"  Throughput:         {throughput:.1f} req/sec")
    print(f"  HTTP 200 (Allowed): {status_counts.get(200, 0)}")
    print(f"  HTTP 429 (Limited): {status_counts.get(429, 0)}")
    print(f"  Network Errors:     {errors}")
    print("\nLATENCY DISTRIBUTION (Virtual Threads + Redis Atomic Lua + Sub-ms Key Cache):")
    print(f"  Min:                {min(latencies):.2f} ms")
    print(f"  Average:            {avg_lat:.2f} ms")
    print(f"  Median (p50):       {p50:.2f} ms")
    print(f"  p90:                {p90:.2f} ms")
    print(f"  p95:                {p95:.2f} ms")
    print(f"  p99:                {p99:.2f} ms")
    print(f"  Max:                {max(latencies):.2f} ms")
    print("=" * 65)

    return {
        "throughput": throughput,
        "avg_ms": avg_lat,
        "p50_ms": p50,
        "p90_ms": p90,
        "p99_ms": p99,
        "allowed": status_counts.get(200, 0),
        "limited": status_counts.get(429, 0),
    }

if __name__ == "__main__":
    req_count = int(sys.argv[1]) if len(sys.argv) > 1 else 1000
    concurr = int(sys.argv[2]) if len(sys.argv) > 2 else 50
    raw_key, plan_info = setup_test_key()
    run_stress_test(raw_key, plan_info, total_requests=req_count, concurrency=concurr)
