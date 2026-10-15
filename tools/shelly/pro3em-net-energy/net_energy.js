/**
 * Shelly Pro 3EM - net (saldierend) energy counter, served via HTTP.
 *
 * Integrates the signed total active power (sum of all three phases) into two
 * monotonic counters in Wh:
 *   import_wh - net energy drawn from the grid
 *   export_wh - net energy fed into the grid
 *
 * Output (no MQTT needed):
 *   GET http://<device-ip>/script/<script-id>/energy
 *   {"v":1,"ready":true,"import_wh":201795,"export_wh":173725,"uptime_ms":123456}
 *
 * Persistence: both counters are stored in ONE KVS key ("NetEnergyWh" =
 * "<import_wh>,<export_wh>") every 15 minutes, and only if they changed.
 * Energy of up to 15 minutes is lost if the device loses power in between.
 *
 * Seeding (required once, "0,0" for a fresh start):
 *   http://<device-ip>/rpc/KVS.Set?key=NetEnergyWh&value=<import_wh>,<export_wh>
 * Counting starts only after a valid seed has been read from KVS, so a failed
 * or missing KVS read can never reset the counters to zero. While waiting for
 * a seed, the endpoint answers with "ready":false and the script retries.
 *
 * Sign convention is the device's: positive power = import. Handle a reversed
 * CT orientation on the consumer side (swap import/export).
 */

let CFG = {
  tickMs: 500,
  saveEveryMs: 900000,
  maxGapMs: 5000,
  loadRetryMs: 10000,
  kvsKey: "NetEnergyWh",
  endpoint: "energy"
};

let importWh = 0;
let exportWh = 0;
let importWs = 0;
let exportWs = 0;
let ready = false;
let loading = false;
let dirty = false;
let lastMs = null;
let lastPower = 0;
let lastSaveMs = 0;
let retryTimer = null;
let fallbackMs = 0;

// Milliseconds since boot. Falls back to a fixed step per tick if the firmware
// provides no uptime function.
function nowMs() {
  if (Shelly.getUptimeMs !== undefined) {
    return Shelly.getUptimeMs();
  }
  fallbackMs += CFG.tickMs;
  return fallbackMs;
}

// Parses "<import_wh>,<export_wh>"; returns [import, export] or null.
function parseSeed(v) {
  if (v === null || v === undefined) {
    return null;
  }
  let parts = ("" + v).split(",");
  if (parts.length !== 2 || parts[0] === "" || parts[1] === "") {
    return null;
  }
  let i = Number(parts[0]);
  let e = Number(parts[1]);
  if (isNaN(i) || isNaN(e) || i < 0 || e < 0) {
    return null;
  }
  return [Math.floor(i), Math.floor(e)];
}

function loadCounters() {
  if (ready || loading) {
    return;
  }
  loading = true;
  Shelly.call("KVS.Get", { key: CFG.kvsKey }, function (res, errCode) {
    loading = false;
    if (ready) {
      return;
    }
    let seed = (errCode === 0 && res) ? parseSeed(res.value) : null;
    if (seed === null) {
      print("NetEnergy: waiting for a valid seed in KVS key " + CFG.kvsKey);
      return;
    }
    importWh = seed[0];
    exportWh = seed[1];
    importWs = 0;
    exportWs = 0;
    lastMs = null;
    lastSaveMs = nowMs();
    dirty = false;
    ready = true;
    if (retryTimer !== null) {
      Timer.clear(retryTimer);
      retryTimer = null;
    }
    print("NetEnergy: ready, import " + importWh + " Wh, export " + exportWh + " Wh");
  });
}

function saveCounters(now) {
  lastSaveMs = now;
  dirty = false;
  Shelly.call("KVS.Set", { key: CFG.kvsKey, value: importWh + "," + exportWh }, function (res, errCode) {
    if (errCode !== 0) {
      // Retry in about one minute instead of waiting a full interval.
      dirty = true;
      lastSaveMs = lastSaveMs - CFG.saveEveryMs + 60000;
      print("NetEnergy: KVS save failed, code " + errCode);
    }
  });
}

function tick() {
  if (!ready) {
    return;
  }
  let em = Shelly.getComponentStatus("em", 0);
  if (!em || typeof em.total_act_power !== "number") {
    // No valid sample: do not bridge the gap with an assumed power value.
    lastMs = null;
    return;
  }

  let now = nowMs();
  let power = em.total_act_power;

  if (lastMs !== null) {
    let dt = now - lastMs;
    // Gaps longer than maxGapMs (stalled timer) are skipped, not extrapolated.
    if (dt > 0 && dt <= CFG.maxGapMs) {
      let ws = (lastPower + power) / 2 * dt / 1000;
      if (ws >= 0) {
        importWs += ws;
      } else {
        exportWs -= ws;
      }
      let w = Math.floor(importWs / 3600);
      if (w > 0) {
        importWh += w;
        importWs -= w * 3600;
        dirty = true;
      }
      w = Math.floor(exportWs / 3600);
      if (w > 0) {
        exportWh += w;
        exportWs -= w * 3600;
        dirty = true;
      }
    }
  }

  lastMs = now;
  lastPower = power;

  if (dirty && now - lastSaveMs >= CFG.saveEveryMs) {
    saveCounters(now);
  }
}

HTTPServer.registerEndpoint(CFG.endpoint, function (req, res) {
  res.code = 200;
  res.body = JSON.stringify({
    v: 1,
    ready: ready,
    import_wh: importWh,
    export_wh: exportWh,
    uptime_ms: lastMs
  });
  res.send();
});

retryTimer = Timer.set(CFG.loadRetryMs, true, loadCounters);
loadCounters();
Timer.set(CFG.tickMs, true, tick);
