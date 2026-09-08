/* Cinema Window Runtime Platform 3.0
 * Additive runtime layer. UI/design remains untouched.
 * No remote JavaScript is evaluated and no sensitive telemetry is collected.
 */
(function () {
  'use strict';
  var w = window;
  var cfg = w.CinemaWindowConfig || {};
  var KEY = 'cw_runtime_state_v3';
  var EVENTS_KEY = 'cw_runtime_events_v3';
  var state = { startedAt: Date.now(), online: navigator.onLine !== false, provider: null, capabilities: null };

  function read(key, fallback) { try { var x = localStorage.getItem(key); return x ? JSON.parse(x) : fallback; } catch (_) { return fallback; } }
  function write(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch (_) {} }
  function emit(name, data) {
    var list = read(EVENTS_KEY, []);
    list.push({ name: name, t: Date.now(), data: data && typeof data === 'object' ? data : undefined });
    if (list.length > 20) list = list.slice(-20);
    write(EVENTS_KEY, list);
  }
  function safeFetch(url, options, timeout) {
    if (!url) return Promise.reject(new Error('missing endpoint'));
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = controller ? setTimeout(function () { controller.abort(); }, timeout || 5000) : null;
    var opts = options || {};
    if (controller) opts.signal = controller.signal;
    return fetch(url, opts).finally(function () { if (timer) clearTimeout(timer); });
  }

  function loadCapabilities() {
    return safeFetch(cfg.capabilitiesUrl, { headers: { Accept: 'application/json' }, credentials: 'same-origin' }, 5000)
      .then(function (r) { if (!r.ok) throw new Error('capabilities'); return r.json(); })
      .then(function (x) { state.capabilities = x; w.CinemaWindowRuntime.capabilities = x; return x; })
      .catch(function () { return null; });
  }

  function loadProviders() {
    return safeFetch(cfg.providersUrl, { headers: { Accept: 'application/json' }, credentials: 'same-origin' }, 5000)
      .then(function (r) { if (!r.ok) throw new Error('providers'); return r.json(); })
      .then(function (x) { w.CinemaWindowRuntime.providers = Array.isArray(x.providers) ? x.providers : []; return w.CinemaWindowRuntime.providers; })
      .catch(function () { return []; });
  }

  function flushTelemetry() {
    var events = read(EVENTS_KEY, []);
    if (!events.length || !cfg.telemetryUrl) return Promise.resolve(false);
    return safeFetch(cfg.telemetryUrl, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ events: events.map(function (e) { return { name: e.name }; }) })
    }, 5000).then(function (r) {
      if (r.ok) { write(EVENTS_KEY, []); return true; }
      return false;
    }).catch(function () { return false; });
  }

  function saveResume(key, payload) {
    if (!key) return;
    var store = read(KEY, {});
    store.resume = store.resume || {};
    store.resume[String(key)] = {
      position: Math.max(0, Number(payload && payload.position) || 0),
      duration: Math.max(0, Number(payload && payload.duration) || 0),
      updatedAt: Date.now()
    };
    write(KEY, store);
    emit('resume_saved');
  }
  function getResume(key) {
    var store = read(KEY, {});
    return store.resume && store.resume[String(key)] ? store.resume[String(key)] : null;
  }

  function chooseProvider(candidates) {
    var list = Array.isArray(candidates) ? candidates.filter(Boolean) : [];
    list.sort(function (a, b) { return (Number(a.priority) || 999) - (Number(b.priority) || 999); });
    var chosen = list.find(function (x) { return x.enabled !== false; }) || null;
    if (chosen) { state.provider = chosen.id || null; emit('provider_selected'); }
    return chosen;
  }

  function diagnostics() {
    return {
      runtimeVersion: cfg.runtimeVersion || 'unknown',
      online: navigator.onLine !== false,
      language: navigator.language || '',
      visibility: document.visibilityState,
      storage: (function () { try { localStorage.setItem('__cw_test','1'); localStorage.removeItem('__cw_test'); return true; } catch (_) { return false; } })(),
      serviceWorker: 'serviceWorker' in navigator,
      standalone: !!(w.matchMedia && w.matchMedia('(display-mode: standalone)').matches),
      memoryMB: w.performance && w.performance.memory ? Math.round(w.performance.memory.usedJSHeapSize / 1048576) : null
    };
  }

  w.CinemaWindowRuntime = {
    version: cfg.runtimeVersion || '3.0.0-runtime',
    state: state,
    providers: [],
    capabilities: null,
    diagnostics: diagnostics,
    chooseProvider: chooseProvider,
    saveResume: saveResume,
    getResume: getResume,
    refresh: function () { return Promise.all([loadCapabilities(), loadProviders()]); },
    flushTelemetry: flushTelemetry
  };

  w.addEventListener('online', function () { state.online = true; emit('online'); flushTelemetry(); });
  w.addEventListener('offline', function () { state.online = false; emit('offline'); });
  w.addEventListener('pagehide', flushTelemetry);
  document.addEventListener('visibilitychange', function () { if (document.visibilityState === 'visible') flushTelemetry(); });

  emit('app_start');
  loadCapabilities().then(loadProviders).then(function () {
    try { w.dispatchEvent(new CustomEvent('cinema_window_runtime_ready', { detail: w.CinemaWindowRuntime })); } catch (_) {}
    flushTelemetry();
  });
})();
