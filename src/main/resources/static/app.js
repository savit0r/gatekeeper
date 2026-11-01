// Gatekeeper Developer Portal Application Logic
const state = {
  token: localStorage.getItem('gk_token') || null,
  developer: null,
  plans: [],
  keys: [],
  activeKey: null,
  selectedKeyForPlayground: null,
  analyticsKeyId: null,
  analyticsSummary: null,
  analyticsEvents: [],
  resetTimerInterval: null,
};

// Utilities
function showToast(message, type = 'info') {
  const container = document.getElementById('toastContainer');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `<span>${message}</span>`;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(20px)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

async function api(path, options = {}) {
  const headers = options.headers || {};
  if (state.token && !headers['Authorization']) {
    headers['Authorization'] = `Bearer ${state.token}`;
  }
  if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(options.body);
  }
  options.headers = headers;

  try {
    const res = await fetch(path, options);
    const contentType = res.headers.get('content-type') || '';
    let data = null;
    if (contentType.includes('application/json')) {
      data = await res.json();
    } else {
      data = await res.text();
    }

    return {
      status: res.status,
      ok: res.ok,
      headers: res.headers,
      data
    };
  } catch (err) {
    showToast(`Network error: ${err.message}`, 'error');
    throw err;
  }
}

// Auth Lifecycle
function setToken(token) {
  state.token = token;
  if (token) {
    localStorage.setItem('gk_token', token);
  } else {
    localStorage.removeItem('gk_token');
    state.developer = null;
  }
  renderApp();
}

async function fetchInitialData() {
  if (!state.token) return;
  try {
    // 1. Profile
    const devRes = await api('/api/v1/developers/me');
    if (devRes.status === 401) {
      setToken(null);
      return;
    }
    state.developer = devRes.data;

    // 2. Plans
    const plansRes = await api('/api/v1/plans');
    if (plansRes.ok) {
      state.plans = plansRes.data;
    }

    // 3. Keys
    await fetchKeys();

    // 4. Actuator Health
    checkBackendHealth();
  } catch (e) {
    console.error('Initialization error:', e);
  }
}

async function fetchKeys() {
  const keysRes = await api('/api/v1/keys');
  if (keysRes.ok) {
    state.keys = keysRes.data;
    if (state.keys.length > 0 && !state.selectedKeyForPlayground) {
      const active = state.keys.find(k => k.status === 'ACTIVE') || state.keys[0];
      state.selectedKeyForPlayground = active;
      state.analyticsKeyId = active.id;
      fetchAnalytics(active.id);
    }
  }
}

async function checkBackendHealth() {
  try {
    const res = await fetch('/actuator/health');
    const badge = document.getElementById('healthBadge');
    if (res.ok) {
      badge.innerHTML = '<span class="pill-dot"></span> Cluster UP';
      badge.className = 'pill pill-green';
    } else {
      badge.innerHTML = '<span class="pill-dot"></span> Degraded';
      badge.className = 'pill pill-rose';
    }
  } catch (e) {
    const badge = document.getElementById('healthBadge');
    if (badge) {
      badge.innerHTML = '<span class="pill-dot"></span> Offline';
      badge.className = 'pill pill-rose';
    }
  }
}

// Render Functions
function renderApp() {
  const authSection = document.getElementById('authSection');
  const dashboardSection = document.getElementById('dashboardSection');
  const userMenu = document.getElementById('userMenu');

  if (state.token && state.developer) {
    authSection.style.display = 'none';
    dashboardSection.style.display = 'block';
    userMenu.style.display = 'flex';
    document.getElementById('navUserEmail').innerText = state.developer.email;
    renderDashboard();
  } else {
    authSection.style.display = 'flex';
    dashboardSection.style.display = 'none';
    userMenu.style.display = 'none';
  }
}

function renderDashboard() {
  // Stat cards
  const activeCount = state.keys.filter(k => k.status === 'ACTIVE').length;
  document.getElementById('statActiveKeys').innerText = activeCount;

  renderKeyTable();
  renderPlanSelectOptions();
  renderPlaygroundSelector();
}

function renderPlanSelectOptions() {
  const select = document.getElementById('newKeyPlanSelect');
  if (!select) return;
  select.innerHTML = state.plans.map(p => `
    <option value="${p.id}">${p.name} — ${p.requestsPerWindow} req / ${p.windowSeconds}s</option>
  `).join('');
}

function renderKeyTable() {
  const tbody = document.getElementById('keysTableBody');
  if (!tbody) return;

  if (state.keys.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="6" style="text-align: center; color: var(--text-muted); padding: 2.5rem;">
          No API keys created yet. Click "Generate API Key" to provision your first key.
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = state.keys.map(k => {
    const isActive = k.status === 'ACTIVE';
    const statusBadge = isActive 
      ? '<span class="badge-active">ACTIVE</span>' 
      : '<span class="badge-revoked">REVOKED</span>';

    return `
      <tr>
        <td><strong>${escapeHtml(k.name)}</strong></td>
        <td><span class="code-pill">${k.keyPrefix}••••</span></td>
        <td><span class="pill pill-blue">${escapeHtml(k.plan.name)}</span></td>
        <td>${statusBadge}</td>
        <td style="color: var(--text-muted); font-size: 0.8rem;">${new Date(k.createdAt).toLocaleDateString()}</td>
        <td>
          <div class="table-actions">
            ${isActive ? `
              <button class="btn-icon-action" onclick="handleRotateKey('${k.id}', '${escapeHtml(k.name)}')">Rotate</button>
              <button class="btn-icon-action danger" onclick="handleRevokeKey('${k.id}')">Revoke</button>
            ` : `<span style="color: var(--text-muted); font-size: 0.78rem;">Revoked</span>`}
            <button class="btn-icon-action" onclick="selectForAnalytics('${k.id}')">Analytics</button>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

function renderPlaygroundSelector() {
  const select = document.getElementById('playgroundKeySelect');
  if (!select) return;

  const activeKeys = state.keys.filter(k => k.status === 'ACTIVE');
  if (activeKeys.length === 0) {
    select.innerHTML = `<option value="">No Active Keys Available</option>`;
    return;
  }

  select.innerHTML = activeKeys.map(k => `
    <option value="${k.id}" ${state.selectedKeyForPlayground && state.selectedKeyForPlayground.id === k.id ? 'selected' : ''}>
      ${escapeHtml(k.name)} (${k.keyPrefix}••••) - ${escapeHtml(k.plan.name)}
    </option>
  `).join('');
}

// Modal Handlers
function openModal(id) {
  document.getElementById(id).classList.add('active');
}

function closeModal(id) {
  document.getElementById(id).classList.remove('active');
}

// Analytics
async function selectForAnalytics(keyId) {
  state.analyticsKeyId = keyId;
  await fetchAnalytics(keyId);
  const target = document.getElementById('analyticsPanel');
  if (target) {
    target.scrollIntoView({ behavior: 'smooth' });
  }
}

async function fetchAnalytics(keyId) {
  if (!keyId) return;
  const key = state.keys.find(k => k.id === keyId);
  const title = document.getElementById('analyticsKeyTitle');
  if (title && key) {
    title.innerText = `Key: ${key.name} (${key.keyPrefix}••••)`;
  }

  const [summaryRes, eventsRes] = await Promise.all([
    api(`/api/v1/analytics/keys/${keyId}/summary`),
    api(`/api/v1/analytics/keys/${keyId}/events?page=0&size=10`)
  ]);

  if (summaryRes.ok) {
    const s = summaryRes.data;
    document.getElementById('statTotalRequests').innerText = s.totalRequests;
    document.getElementById('statAllowedRequests').innerText = s.allowedRequests;
    document.getElementById('statRejectedRequests').innerText = s.rejectedRequests;
    document.getElementById('statAvgLatency').innerText = `${s.averageLatencyMs.toFixed(1)} ms`;
  }

  if (eventsRes.ok) {
    const eventsTable = document.getElementById('analyticsEventsBody');
    if (eventsTable) {
      if (eventsRes.data.content.length === 0) {
        eventsTable.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 1.5rem;">No usage events recorded for this key yet.</td></tr>`;
      } else {
        eventsTable.innerHTML = eventsRes.data.content.map(e => `
          <tr>
            <td>${new Date(e.occurredAt).toLocaleTimeString()}</td>
            <td><span class="code-pill">${e.route}</span></td>
            <td><strong>${e.statusCode}</strong></td>
            <td>
              ${e.decision === 'ALLOWED' 
                ? '<span class="badge-active">ALLOWED</span>' 
                : `<span class="badge-revoked">REJECTED (${e.rejectionReason || 'RATE_LIMITED'})</span>`}
            </td>
            <td>${e.latencyMs} ms</td>
          </tr>
        `).join('');
      }
    }
  }
}

// Actions
async function handleCreateKey(e) {
  e.preventDefault();
  const name = document.getElementById('newKeyName').value.trim();
  const planId = document.getElementById('newKeyPlanSelect').value;

  if (!name || !planId) {
    showToast('Key name and plan are required', 'error');
    return;
  }

  const res = await api('/api/v1/keys', {
    method: 'POST',
    body: { name, planId }
  });

  if (res.ok) {
    closeModal('createKeyModal');
    document.getElementById('newKeyName').value = '';
    showToast('API Key generated successfully!', 'success');

    // Reveal one-time secret modal
    document.getElementById('secretKeyDisplay').innerText = res.data.rawKey;
    document.getElementById('secretKeyRawInput').value = res.data.rawKey;
    openModal('keySecretModal');

    // Automatically store this raw key in session for immediate playground testing
    sessionStorage.setItem(`gk_raw_${res.data.id}`, res.data.rawKey);

    await fetchKeys();
    renderDashboard();
  } else {
    showToast(res.data.message || 'Failed to create key', 'error');
  }
}

async function handleRevokeKey(keyId) {
  if (!confirm('Are you sure you want to revoke this API key? This action is immediate and permanent.')) {
    return;
  }

  const res = await api(`/api/v1/keys/${keyId}`, { method: 'DELETE' });
  if (res.ok) {
    showToast('API Key revoked instantly and evicted from cache', 'success');
    await fetchKeys();
    renderDashboard();
  } else {
    showToast('Failed to revoke key', 'error');
  }
}

async function handleRotateKey(keyId, keyName) {
  if (!confirm(`Rotate key "${keyName}"? The current key will be immediately revoked, and a fresh key with identical limits will be generated.`)) {
    return;
  }

  const res = await api(`/api/v1/keys/${keyId}/rotate`, { method: 'POST' });
  if (res.ok) {
    showToast(`Key "${keyName}" rotated successfully!`, 'success');
    document.getElementById('secretKeyDisplay').innerText = res.data.rawKey;
    document.getElementById('secretKeyRawInput').value = res.data.rawKey;
    openModal('keySecretModal');

    sessionStorage.setItem(`gk_raw_${res.data.id}`, res.data.rawKey);
    await fetchKeys();
    renderDashboard();
  } else {
    showToast('Failed to rotate key', 'error');
  }
}

// Live Playground Execution
async function executePlaygroundRequest(burst = false) {
  const terminal = document.getElementById('playgroundTerminal');
  const keyInput = document.getElementById('playgroundCustomKey').value.trim();
  const selectedKeyId = document.getElementById('playgroundKeySelect').value;

  // Determine key to use
  let keyToUse = keyInput;
  if (!keyToUse && selectedKeyId) {
    keyToUse = sessionStorage.getItem(`gk_raw_${selectedKeyId}`) || '';
  }

  if (!keyToUse) {
    showToast('Paste the raw API key secret (gk_live_...) to execute requests.', 'error');
    document.getElementById('playgroundCustomKey').focus();
    return;
  }

  const totalCalls = burst ? 10 : 1;
  logToTerminal(`--- ${burst ? 'BURST TEST (10 REQUESTS)' : 'DISPATCHING GET /api/v1/protected/hello'} ---`);

  for (let i = 0; i < totalCalls; i++) {
    const start = performance.now();
    try {
      const res = await fetch('/api/v1/protected/hello', {
        headers: {
          'X-API-Key': keyToUse,
          'Accept': 'application/json'
        }
      });
      const duration = (performance.now() - start).toFixed(1);
      const limit = res.headers.get('X-RateLimit-Limit');
      const remaining = res.headers.get('X-RateLimit-Remaining');
      const reset = res.headers.get('X-RateLimit-Reset');
      const body = await res.json().catch(() => ({}));

      // Update Meter Bar
      updateRateLimitMeter(limit, remaining, reset);

      const statusColor = res.status === 200 ? '#34d399' : '#fb7185';
      logToTerminal(`[${new Date().toLocaleTimeString()}] Status: <span style="color: ${statusColor}; font-weight: bold;">${res.status}</span> | Limit: ${remaining}/${limit} | Latency: ${duration}ms`);

      if (i === totalCalls - 1) {
        logToTerminal(`Payload: ${JSON.stringify(body, null, 2)}`);
      }
    } catch (e) {
      logToTerminal(`<span style="color: #fb7185;">Network Error: ${e.message}</span>`);
    }

    if (burst) {
      await new Promise(r => setTimeout(r, 40));
    }
  }

  // Refresh analytics after playground requests
  if (state.analyticsKeyId) {
    setTimeout(() => fetchAnalytics(state.analyticsKeyId), 500);
  }
}

function updateRateLimitMeter(limitStr, remainingStr, resetStr) {
  if (!limitStr || remainingStr === null) return;
  const limit = parseInt(limitStr, 10);
  const remaining = parseInt(remainingStr, 10);
  const reset = parseInt(resetStr, 10);

  const fill = document.getElementById('meterProgressFill');
  const countDisplay = document.getElementById('meterCountDisplay');
  const resetDisplay = document.getElementById('meterResetDisplay');

  const pct = Math.max(0, Math.min(100, (remaining / limit) * 100));
  fill.style.width = `${pct}%`;

  if (pct > 50) {
    fill.className = 'progress-fill';
  } else if (pct > 20) {
    fill.className = 'progress-fill warning';
  } else {
    fill.className = 'progress-fill danger';
  }

  countDisplay.innerText = `${remaining} / ${limit}`;

  if (reset) {
    let timeLeft = reset;
    resetDisplay.innerText = `Window resets in: ${timeLeft}s`;
    clearInterval(state.resetTimerInterval);
    state.resetTimerInterval = setInterval(() => {
      timeLeft--;
      if (timeLeft <= 0) {
        clearInterval(state.resetTimerInterval);
        resetDisplay.innerText = 'Window reset!';
      } else {
        resetDisplay.innerText = `Window resets in: ${timeLeft}s`;
      }
    }, 1000);
  }
}

function logToTerminal(html) {
  const terminal = document.getElementById('playgroundTerminal');
  const line = document.createElement('div');
  line.className = 'terminal-line';
  line.innerHTML = html;
  terminal.appendChild(line);
  terminal.scrollTop = terminal.scrollHeight;
}

function copySecretToClipboard() {
  const val = document.getElementById('secretKeyRawInput').value;
  navigator.clipboard.writeText(val).then(() => {
    showToast('API Key copied to clipboard!', 'success');
  });
}

function escapeHtml(str) {
  return String(str || '').replace(/[&<>"']/g, m => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[m]);
}

// DOM Event Listeners
document.addEventListener('DOMContentLoaded', () => {
  // Tab switching
  const tabLogin = document.getElementById('tabLogin');
  const tabRegister = document.getElementById('tabRegister');
  const formLogin = document.getElementById('loginForm');
  const formRegister = document.getElementById('registerForm');

  tabLogin?.addEventListener('click', () => {
    tabLogin.classList.add('active');
    tabRegister.classList.remove('active');
    formLogin.style.display = 'block';
    formRegister.style.display = 'none';
  });

  tabRegister?.addEventListener('click', () => {
    tabRegister.classList.add('active');
    tabLogin.classList.remove('active');
    formLogin.style.display = 'none';
    formRegister.style.display = 'block';
  });

  // Login submission
  formLogin?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('loginEmail').value.trim();
    const password = document.getElementById('loginPassword').value;

    const res = await api('/api/v1/auth/login', {
      method: 'POST',
      body: { email, password }
    });

    if (res.ok) {
      showToast('Logged in successfully', 'success');
      setToken(res.data.token);
      await fetchInitialData();
      renderApp();
    } else {
      showToast(res.data.message || 'Invalid email or password', 'error');
    }
  });

  // Register submission
  formRegister?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('regEmail').value.trim();
    const password = document.getElementById('regPassword').value;

    const res = await api('/api/v1/developers/register', {
      method: 'POST',
      body: { email, password }
    });

    if (res.ok) {
      showToast('Account registered! Logging in...', 'success');
      // Auto-login
      const loginRes = await api('/api/v1/auth/login', {
        method: 'POST',
        body: { email, password }
      });
      if (loginRes.ok) {
        setToken(loginRes.data.token);
        await fetchInitialData();
        renderApp();
      }
    } else {
      showToast(res.data.message || 'Registration failed', 'error');
    }
  });

  // Demo Login helper
  document.getElementById('quickDemoBtn')?.addEventListener('click', () => {
    const demoEmail = `demo_dev_${Math.floor(Date.now() / 1000)}@gatekeeper.dev`;
    const demoPass = 'GatekeeperDemo123!';
    document.getElementById('regEmail').value = demoEmail;
    document.getElementById('regPassword').value = demoPass;
    tabRegister.click();
    showToast('Demo credentials prefilled. Click "Create Account" to start!', 'info');
  });

  // Logout
  document.getElementById('logoutBtn')?.addEventListener('click', () => {
    setToken(null);
    showToast('Logged out', 'info');
  });

  // Key creation form
  document.getElementById('createKeyForm')?.addEventListener('submit', handleCreateKey);

  // Playground buttons
  document.getElementById('btnPlaygroundSingle')?.addEventListener('click', () => executePlaygroundRequest(false));
  document.getElementById('btnPlaygroundBurst')?.addEventListener('click', () => executePlaygroundRequest(true));
  document.getElementById('btnClearTerminal')?.addEventListener('click', () => {
    document.getElementById('playgroundTerminal').innerHTML = '<div class="terminal-line" style="color: var(--text-muted);">Ready. Click "Send Request" to test live endpoint.</div>';
  });

  // Playground key select change
  document.getElementById('playgroundKeySelect')?.addEventListener('change', (e) => {
    const keyId = e.target.value;
    const rawSaved = sessionStorage.getItem(`gk_raw_${keyId}`);
    if (rawSaved) {
      document.getElementById('playgroundCustomKey').value = rawSaved;
    }
  });

  // Start app
  if (state.token) {
    fetchInitialData().then(renderApp);
  } else {
    renderApp();
  }
});
