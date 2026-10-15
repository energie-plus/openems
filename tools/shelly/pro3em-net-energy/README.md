# Shelly Pro 3EM: saldierende Energiezähler per Script

Interne Zusammenfassung (Stand 2026-10-15). Gilt für den EnPlus-Fork, es gibt keinen Upstream-PR.
Quelle der Wahrheit für das Script ist `net_energy.js` in diesem Verzeichnis, der Abschnitt
"Script" unten ist eine Kopie davon.

## 1. Problem und Ziel

- Der Shelly Pro 3EM zählt Bezug und Einspeisung **pro Phase getrennt**. Die Summen `total_act`
  und `total_act_ret` (`EMData.GetStatus`) sind daher **nicht saldiert**: Speist eine Phase ein,
  während eine andere bezieht, zählt das Gerät beides, obwohl netto fast nichts fliesst.
- Die Edge hat die Energie bisher aus der saldierten Summenleistung selbst integriert
  (`CalculateEnergyFromPower`) und beim Neustart aus der Timedata-DB wieder aufgenommen. Ein
  Neustart vor dem InfluxDB-Flush liess die Zähler dadurch springen.
- Ziel: saldierte, monotone Energiezähler **auf dem Gerät**, die von Edge-Neustarts unabhängig sind
  und von der Edge nur noch gelesen werden.

## 2. Architektur

```
Shelly Pro 3EM (192.168.1.227)
  em:0.total_act_power  --(alle 500 ms)-->  Script (Integration, Wh-Zähler, KVS alle 15 min)
                                                     |
                                    GET /script/1/energy  (HTTP, JSON)
                                                     |
OpenEMS Edge: IO.Shelly.Pro3EM, energySource=SCRIPT_ENERGY_ENDPOINT
  -> ShellyScriptEnergyHandler -> ActiveProductionEnergy / ActiveConsumptionEnergy
```

- Kein MQTT. Die Edge pollt den Endpoint jeden Zyklus wie `EM.GetStatus`.
- Zuordnung am GRID-Meter: `import_wh` (Netzbezug) -> `ActiveProductionEnergy`,
  `export_wh` (Einspeisung) -> `ActiveConsumptionEnergy`. Bei `invert=true` vertauscht.
  (`Sum.GridBuy <- ActiveProductionEnergy`, `Sum.GridSell <- ActiveConsumptionEnergy`.)

## 3. Endpoint

`GET http://<ip>/script/<script-id>/energy`

```json
{"v":1,"ready":true,"import_wh":395,"export_wh":112978,"uptime_ms":155238784}
```

| Feld | Bedeutung |
|---|---|
| `v` | Payload-Version, die Edge akzeptiert nur `1` |
| `ready` | `false`, solange kein gültiger Seed aus dem KVS geladen wurde (Zähler steht dann auf 0) |
| `import_wh` | saldierter Netzbezug, ganze Wh, monoton |
| `export_wh` | saldierte Einspeisung, ganze Wh, monoton |
| `uptime_ms` | Uptime des Geräts beim letzten gültigen Sample (`null` vor dem ersten) |

## 4. Verhalten des Scripts

- Liest `Shelly.getComponentStatus("em", 0).total_act_power` (Summe der drei Phasen, vorzeichenbehaftet,
  positiv = Bezug). Integration per Trapezregel mit **gemessener** Zeit (`Shelly.getUptimeMs`).
- Positive Beiträge zählen zu `import_wh`, negative zu `export_wh`. Gesaldiert wird **über die
  Phasen** (Summenleistung), nicht über die Zeit. Bei Leistung um 0 W fallen Bezug und Einspeisung
  daher höher aus als bei einer Viertelstunden-Saldierung des Netzbetreibers.
- Lücken über 5 s (Timer-Stau) und ungültige Samples werden übersprungen, nicht extrapoliert.
- **Seed-Pflicht:** Gezählt wird erst, wenn der KVS-Key `NetEnergyWh` einen gültigen Wert
  `"<import_wh>,<export_wh>"` enthält. Fehlt er oder schlägt das Lesen fehl, meldet der Endpoint
  `ready:false`, und das Script versucht es alle 10 s erneut. Ein KVS-Fehler kann den Zähler
  nie auf 0 setzen.
- Speichert beide Zähler in **einem** KVS-Key alle 15 min, nur bei Änderung (ca. 96 Writes/Tag).
  Bei Stromausfall oder Reboot gehen bis zu 15 min Energie verloren, der Zähler fällt dann auf
  den letzten gespeicherten Stand zurück.
- Das Script liest den KVS-Key **nur beim Start**. Ein `KVS.Set` bei laufendem Script wirkt nicht
  und wird bei der nächsten Speicherung überschrieben.

## 5. Installation und Betrieb

Vorbedingung: Pro 3EM im Profil `triphase`, Firmware >= 1.7 (getestet mit 1.7.1 und 2.0.1).

1. Im Web-UI unter Scripts ein neues Script anlegen, den Inhalt von `net_energy.js` einfügen,
   **Save**. Die Script-ID mit `GET /rpc/Script.List` ablesen.
2. **Seed setzen**, bevor das Script gestartet wird. Beim ersten Mal mit den aktuellen Werten des
   Edge-Meters (`<import_wh>` = `ActiveProductionEnergy`, `<export_wh>` = `ActiveConsumptionEnergy`),
   für einen frischen Start mit `0,0`:

```powershell
Invoke-RestMethod "http://<ip>/rpc/KVS.Set?key=NetEnergyWh&value=<import_wh>,<export_wh>"
```

3. Script starten und **Run on startup** aktivieren (Schalter in der Script-Liste), alternativ:

```powershell
Invoke-RestMethod "http://<ip>/rpc/Script.SetConfig?id=1&config={%22enable%22:true}"
```

4. Prüfen: `GET /script/1/energy` liefert `"ready":true`.

### Neu seeden eines laufenden Scripts

Den RAM-Stand vorher am Endpoint lesen, dann in dieser Reihenfolge:

```powershell
Invoke-RestMethod "http://<ip>/script/1/energy"
Invoke-RestMethod "http://<ip>/rpc/Script.Stop?id=1"
Invoke-RestMethod "http://<ip>/rpc/KVS.Set?key=NetEnergyWh&value=<import_wh>,<export_wh>"
Invoke-RestMethod "http://<ip>/rpc/Script.Start?id=1"
```

### Diagnose (nur lesend)

```powershell
Invoke-RestMethod "http://<ip>/script/1/energy"
Invoke-RestMethod "http://<ip>/rpc/KVS.Get?key=NetEnergyWh"
Invoke-RestMethod "http://<ip>/rpc/Script.GetStatus?id=1"
Invoke-RestMethod "http://<ip>/rpc/EMData.GetStatus?id=0"
```

Referenz: Das Script belegt ca. 1.5 KB RAM (Peak 3 KB, 23 KB frei).

## 6. Edge-Konfiguration (`IO.Shelly.Pro3EM`)

| Feld | Wert | Anmerkung |
|---|---|---|
| `ip` | `192.168.1.227` | |
| `type` | `GRID` | |
| `invert` | `false` | vertauscht auch Bezug/Einspeisung der Energie |
| `energySource` | `SCRIPT_ENERGY_ENDPOINT` | Default ist `POWER_INTEGRATION` (altes Verhalten) |
| `scriptId` | `1` | nur mit `SCRIPT_ENERGY_ENDPOINT` relevant |

- `ShellyScriptEnergyHandler` übernimmt nur `v=1` und `ready=true`, lehnt negative oder fehlende
  Werte ab und **hält fallende Werte**, bis das Script wieder aufgeholt hat (Guard). Der Guard
  liegt nur im Speicher: Nach einem Neustart der Komponente wird der erste Scriptwert unverändert
  übernommen.
- State-Channel `EnergyScriptUnavailable` (Warning): `true`, wenn das Script nicht erreichbar,
  nicht bereit oder ungültig ist. In dieser Zeit bleiben die Energiewerte stehen.
- Im Script-Modus wird `CalculateEnergyFromPower` nicht angelegt, es gibt keine Timedata-Abfrage.
- Code: `io.openems.edge.io.shelly`: `ShellyScriptEnergyHandler`, `ShellyEnergyMeterHandler`
  (optionale Integration), `shellypro3em/EnergySource`, `Config`, `IoShellyPro3EmImpl`.
  Branch `feature/shelly-pro3em-net-energy-script`, Edge-Commit `d19cc4ee0e`.
  Tests: `ShellyScriptEnergyHandlerTest` (7 Fälle).

## 7. Messergebnisse

| Test | Ergebnis |
|---|---|
| Netto-Gegenprobe über 21.6 h (12.-13.10., inkl. PV-Tag, 17.6 kWh Umsatz) | Script -17'584 Wh, Gerät -17'569 Wh: 15 Wh Differenz = 0.09 % |
| Nachtfenster 9.3 h | Script +43/+43 Wh, Gerät unsaldiert +1103/+1102 Wh, Netto-Differenz -1.5 Wh |
| Firmware-Update 1.7.1 -> 2.0.1 (13.10.) | Script, KVS-Key und `EMData`-Zähler erhalten, kein sichtbarer Verlust |
| Edge-Neustart ems7 (15.10.) | Energiewerte nahtlos weiter, kein Rücksprung |
| Edge-Integration vs. Script auf ems4 (12.-15.10., 72.8 kWh netto) | 78 Wh Differenz = 0.11 % |
| Umschaltung ems4 (15.10. 17:15:11) | einmalig -17 Wh Bezug / +61 Wh Einspeisung, danach synchron |

## 8. Rollout und Stand

- ems7 (Test): `io0` als `IO.Shelly.Pro3EM` auf `192.168.1.227`, Script-Modus, abgenommen.
- ems4 (Produktion): JAR am 15.10. um 17:08 deployt (Integration `2026.8.0-73-g40c533f4fd`, Rest
  unverändert), `meter0` um 17:15 auf `SCRIPT_ENERGY_ENDPOINT` umgestellt.
- Rückweg: `energySource` zurück auf `POWER_INTEGRATION`, JAR-Backup auf ems4
  `/opt/openems/openems-edge.jar_bak20261015`.
- Gerät: `auth_en:false` (kein Passwort), ein Passwort würde die HTTP-Abfragen der Edge betreffen.

## 9. Offene Punkte

- Test des State-Channels `EnergyScriptUnavailable` bei gestopptem Script steht aus.
- Upstream-Sync steht aus: Upstream hat `GoodWeBatteryInverterImpl` und `ess/Config` stark
  geändert (Konflikt mit `feature/goodwe-15k-et-export-limit`), zusätzlich der bekannte
  `EdgeApp.bndrun`-Konflikt. Für die Shelly-Dateien kein Konflikt (Upstream hat dort nur Tests
  mechanisch migriert).
- Optional: Variante über `EMData.GetNetEnergies` (Hardware-Saldierung pro Minute) wurde
  nicht untersucht.

## 10. Script

Vollständig, identisch mit dem auf dem Gerät laufenden Code (Script-ID 1):

```javascript
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
```
