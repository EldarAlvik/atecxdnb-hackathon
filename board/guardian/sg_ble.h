// Scam Call Guardian: BLE setup mode (Arduino BLE library on its NimBLE host).
// The phone pairs with a 6-digit passkey shown on the screen (LE Secure
// Connections, MITM), sends the Wi-Fi details, and gets the device token back.
// BLE only runs in a boot that starts in setup mode; every other boot hands the
// BT memory back to the heap (bleInUse() in project.yaml). Leaving setup mode is a
// restart. See docs/protocol.md section 1.
#pragma once
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLESecurity.h>
#include "host/ble_store.h"
#include "host/ble_hs.h"
#include "nimble/nimble_port.h"

#define SG_BLE_SERVICE  "47a10001-8b3c-4d2e-9f1a-5c6d7e8f9a0b"
#define SG_BLE_COMMAND  "47a10002-8b3c-4d2e-9f1a-5c6d7e8f9a0b"
#define SG_BLE_STATUS   "47a10003-8b3c-4d2e-9f1a-5c6d7e8f9a0b"
#define SG_BLE_NETWORKS "47a10004-8b3c-4d2e-9f1a-5c6d7e8f9a0b"

static const uint32_t SG_SETUP_TIMEOUT_MS  = 10UL * 60 * 1000;
static const uint32_t SG_SETUP_LINGER_MS   = 60000;   // after success, without `done`
static const uint32_t SG_SETUP_CONNECT_MS  = 30000;

enum SgSetupPhase : uint8_t { SP_WAITING, SP_SCANNING, SP_CONNECTING, SP_CONNECTED, SP_FAILED, SP_BYE };

static uint32_t g_passkey = 0;
static volatile bool g_blePeer = false;      // a phone is connected
static volatile bool g_bleSecure = false;    // ... and paired with the passkey
static volatile uint16_t g_bleConn = 0xFFFF;  // its connection handle
static BLECharacteristic* g_chStatus = nullptr;
static BLECharacteristic* g_chNetworks = nullptr;
static portMUX_TYPE g_bleMux = portMUX_INITIALIZER_UNLOCKED;
static char g_bleCmd[600];
static volatile bool g_bleCmdPending = false;
static uint8_t  g_setupPhase = SP_WAITING;
static uint32_t g_setupStartedAt = 0, g_phaseAt = 0;
static String   g_setupLine;                 // second line on the screen
static String   g_setupSsid, g_setupPass;    // being tried (pass cleared afterwards)
static bool     g_setupIsPair = false;
static volatile uint8_t g_wifiDiscReason = 0;

class SgBleServerCb : public BLEServerCallbacks {
  void onConnect(BLEServer*, ble_gap_conn_desc* desc) override {
    g_blePeer = true; g_bleSecure = false; g_bleConn = desc->conn_handle;
    BLESecurity::startSecurity(desc->conn_handle);   // ask the phone to pair now
  }
  void onDisconnect(BLEServer*, ble_gap_conn_desc*) override {
    g_blePeer = false; g_bleSecure = false;
    BLEDevice::startAdvertising();
  }
};
class SgBleSecurityCb : public BLESecurityCallbacks {
  uint32_t onPassKeyRequest() override { return g_passkey; }
  void onPassKeyNotify(uint32_t) override {}
  bool onSecurityRequest() override { return true; }
  bool onConfirmPIN(uint32_t) override { return false; }      // no yes/no on this board
  bool onAuthorizationRequest(uint16_t, uint16_t, bool) override { return true; }
  void onAuthenticationComplete(ble_gap_conn_desc* desc) override {
    g_bleSecure = desc->sec_state.encrypted && desc->sec_state.authenticated;
  }
};
class SgBleCommandCb : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    String v = c->getValue();
    if (v.length() == 0 || v.length() >= sizeof g_bleCmd) return;
    portENTER_CRITICAL(&g_bleMux);
    memcpy(g_bleCmd, v.c_str(), v.length() + 1);
    g_bleCmdPending = true;
    portEXIT_CRITICAL(&g_bleMux);
  }
};

// Characteristic values are only changed on the NimBLE host task, which also
// serves reads and subscriptions: loop() leaves the new value here and queues an
// event that applies it there.
static SemaphoreHandle_t g_blePendMtx = nullptr;
static String g_pendStatus, g_pendNets;
static bool   g_pendStatusSet = false, g_pendNetsSet = false;
static struct ble_npl_event g_bleApplyEv;

static void sgBleApply(struct ble_npl_event*) {
  String st, nets; bool hs, hn;
  xSemaphoreTake(g_blePendMtx, portMAX_DELAY);
  hs = g_pendStatusSet; hn = g_pendNetsSet;
  if (hs) { st = g_pendStatus; g_pendStatus = String(); }
  if (hn) { nets = g_pendNets; g_pendNets = String(); }
  g_pendStatusSet = g_pendNetsSet = false;
  xSemaphoreGive(g_blePendMtx);
  if (hn) g_chNetworks->setValue(nets);
  if (hs) { g_chStatus->setValue(st); if (g_blePeer) g_chStatus->notify(); }
}
static void sgBlePost(bool status, const String& v) {
  xSemaphoreTake(g_blePendMtx, portMAX_DELAY);
  if (status) { g_pendStatus = v; g_pendStatusSet = true; }
  else        { g_pendNets = v;   g_pendNetsSet = true; }
  xSemaphoreGive(g_blePendMtx);
  ble_npl_eventq_put(nimble_port_get_dflt_eventq(), &g_bleApplyEv);   // ignored if already queued
}
static void sgSetupNotify(JsonDocument& d) {
  String s; serializeJson(d, s);
  sgBlePost(true, s);
}
static void sgSetupEvent(const char* state, const char* error = nullptr) {
  JsonDocument d; d["state"] = state; if (error) d["error"] = error;
  sgEmitDoc("state", "setup", d);
}
static void sgSetupFail(const char* error, int reason = 0) {
  g_setupPhase = SP_FAILED; g_phaseAt = millis();
  g_setupPass = String();
  JsonDocument d; d["state"] = "failed"; d["error"] = error; if (reason) d["reason"] = reason;
  sgSetupNotify(d);
  JsonDocument e; e["state"] = "failed"; e["error"] = error; if (reason) e["reason"] = reason;
  sgEmitDoc("state", "setup", e);
  g_setupLine = String("Failed: ") + error + (reason ? String(" (") + reason + ")" : String());
}

static void sgBleBegin() {
  g_passkey = 100000 + esp_random() % 900000;
  BLEDevice::init(String("Guardian-") + (g_deviceId + 9));
  BLEDevice::setMTU(247);
  BLEDevice::setSecurityCallbacks(new SgBleSecurityCb());
  BLESecurity::setCapability(ESP_IO_CAP_OUT);                 // display only
  BLESecurity::setAuthenticationMode(true, true, true);       // bond, MITM, LE Secure Connections
  BLESecurity::setPassKey(true, g_passkey);
  ble_hs_cfg.sm_sc_only = 1;                                  // no fallback to legacy pairing
  ble_store_clear();                                          // forget phones paired before
  g_blePendMtx = xSemaphoreCreateMutex();
  ble_npl_event_init(&g_bleApplyEv, sgBleApply, nullptr);

  BLEServer* srv = BLEDevice::createServer();
  srv->setCallbacks(new SgBleServerCb());
  BLEService* svc = srv->createService(SG_BLE_SERVICE);
  BLECharacteristic* cmd = svc->createCharacteristic(SG_BLE_COMMAND,
      BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_ENC |
      BLECharacteristic::PROPERTY_WRITE_AUTHEN);
  cmd->setCallbacks(new SgBleCommandCb());
  g_chStatus = svc->createCharacteristic(SG_BLE_STATUS,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_READ_ENC |
      BLECharacteristic::PROPERTY_READ_AUTHEN | BLECharacteristic::PROPERTY_NOTIFY);
  g_chNetworks = svc->createCharacteristic(SG_BLE_NETWORKS,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_READ_ENC |
      BLECharacteristic::PROPERTY_READ_AUTHEN);
  g_chNetworks->setValue("[]");                               // before the host runs: no race
  JsonDocument d;
  d["state"] = "ready"; d["device"] = g_deviceId; d["fw"] = SG_FW_VERSION; d["proto"] = SG_PROTO;
  d["saved_ssid"] = g_savedSsid.length() ? g_savedSsid : String(SG_WIFI_SSID);   // what `pair` joins
  String s; serializeJson(d, s); g_chStatus->setValue(s);
  svc->start();
  BLEAdvertising* adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(SG_BLE_SERVICE);
  adv->setScanResponse(true);
  BLEDevice::startAdvertising();

  WiFi.onEvent([](WiFiEvent_t, WiFiEventInfo_t info) {
    g_wifiDiscReason = info.wifi_sta_disconnected.reason;
  }, ARDUINO_EVENT_WIFI_STA_DISCONNECTED);
  g_setupStartedAt = g_phaseAt = millis();
  g_setupPhase = SP_WAITING;
  g_setupLine = "Open the Guardian app";
  sgSetupEvent("advertising");
}

// Wi-Fi and BLE share the radio. A phone keeps the link at a 7.5 ms interval,
// which leaves Wi-Fi too little air time to join; ask for 50-100 ms first.
static void sgBleSlowDown() {
  if (!g_blePeer || g_bleConn == 0xFFFF) return;
  struct ble_gap_upd_params p = {};
  p.itvl_min = 40; p.itvl_max = 80;            // x 1.25 ms
  p.latency = 0;
  p.supervision_timeout = 600;                 // x 10 ms
  ble_gap_update_params(g_bleConn, &p);
}

static void sgSetupStartWifi(const String& ssid, const String& pass, bool pair) {
  g_setupSsid = ssid; g_setupPass = pass; g_setupIsPair = pair;
  sgBleSlowDown();
  WiFi.setAutoReconnect(false);                // one clean attempt, no retries behind our back
  WiFi.disconnect();
  delay(50);
  g_wifiDiscReason = 0;
  if (pass.length()) WiFi.begin(ssid.c_str(), pass.c_str()); else WiFi.begin(ssid.c_str());
  g_setupPhase = SP_CONNECTING; g_phaseAt = millis();
  JsonDocument d; d["state"] = "connecting"; d["ssid"] = ssid; sgSetupNotify(d);
  sgSetupEvent("connecting");
  g_setupLine = String("Joining ") + ssid;
}

static void sgSetupCommand(const char* text) {
  JsonDocument c;
  if (deserializeJson(c, text)) { sgSetupFail("bad_request"); return; }
  const char* op = c["op"] | "";
  if (!strcmp(op, "scan")) {
    if (g_setupPhase == SP_CONNECTING || g_setupPhase == SP_SCANNING) return;
    WiFi.scanNetworks(true);
    g_setupPhase = SP_SCANNING; g_phaseAt = millis();
    JsonDocument d; d["state"] = "scanning"; sgSetupNotify(d);
    g_setupLine = "Scanning Wi-Fi...";
  } else if (!strcmp(op, "wifi")) {
    String ssid = c["ssid"] | "", pass = c["pass"] | "";
    if (ssid.length() < 1 || ssid.length() > 32 || (pass.length() && (pass.length() < 8 || pass.length() > 63))) {
      sgSetupFail("bad_request"); return;
    }
    sgSetupStartWifi(ssid, pass, false);
  } else if (!strcmp(op, "pair")) {
    // The saved network, else the one compiled in from .env (not saved: normal
    // mode falls back to it anyway).
    if (g_savedSsid.length()) sgSetupStartWifi(g_savedSsid, g_savedPass, true);
    else if (sgHasFallbackWifi()) sgSetupStartWifi(SG_WIFI_SSID, SG_WIFI_PASSWORD, true);
    else sgSetupFail("no_saved_wifi");
  } else if (!strcmp(op, "done")) {
    if (g_setupPhase != SP_CONNECTED) { sgSetupFail("bad_request"); return; }
    g_setupPhase = SP_BYE; g_phaseAt = millis();
    JsonDocument d; d["state"] = "bye"; sgSetupNotify(d);
    sgSetupEvent("done");
    g_setupLine = "Done! Restarting...";
  } else {
    sgSetupFail("bad_request");
  }
}

static const char* sgDiscReasonError(uint8_t r) {
  if (r == WIFI_REASON_NO_AP_FOUND) return "no_ssid";
  if (r == WIFI_REASON_AUTH_FAIL || r == WIFI_REASON_4WAY_HANDSHAKE_TIMEOUT ||
      r == WIFI_REASON_HANDSHAKE_TIMEOUT || r == WIFI_REASON_AUTH_EXPIRE ||
      r == WIFI_REASON_MIC_FAILURE || r == WIFI_REASON_ASSOC_FAIL) return "auth";
  return nullptr;
}

// Called from loop() in setup mode. Returns true when the board should restart.
static bool sgSetupLoop() {
  uint32_t now = millis();
  if (g_bleCmdPending) {
    char cmd[sizeof g_bleCmd];
    portENTER_CRITICAL(&g_bleMux);
    memcpy(cmd, g_bleCmd, sizeof cmd);
    g_bleCmdPending = false;
    memset(g_bleCmd, 0, sizeof g_bleCmd);
    portEXIT_CRITICAL(&g_bleMux);
    if (g_bleSecure) sgSetupCommand(cmd);         // the characteristic already needs it
    memset(cmd, 0, sizeof cmd);
    now = millis();                               // the command stamped g_phaseAt after `now`
  }
  switch (g_setupPhase) {
    case SP_SCANNING: {
      int n = WiFi.scanComplete();
      if (n == WIFI_SCAN_RUNNING) {
        if ((int32_t)(now - g_phaseAt) > 15000) { WiFi.scanDelete(); sgSetupFail("timeout"); }
        break;
      }
      if (n < 0) { sgSetupFail("timeout"); break; }
      // Strongest first, unique names, at most 12.
      int order[40]; int m = 0;
      for (int i = 0; i < n && m < 40; i++) {
        if (!WiFi.SSID(i).length()) continue;
        bool dup = false;
        for (int k = 0; k < m && !dup; k++) dup = WiFi.SSID(order[k]) == WiFi.SSID(i);
        if (!dup) order[m++] = i;
      }
      for (int a = 1; a < m; a++)
        for (int b = a; b > 0 && WiFi.RSSI(order[b]) > WiFi.RSSI(order[b - 1]); b--) { int t = order[b]; order[b] = order[b - 1]; order[b - 1] = t; }
      // A BLE attribute holds at most 512 bytes: stop adding before that.
      JsonDocument list; JsonArray arr = list.to<JsonArray>();
      for (int k = 0; k < m && k < 12; k++) {
        JsonObject o = arr.add<JsonObject>();
        o["ssid"] = WiFi.SSID(order[k]);
        o["rssi"] = WiFi.RSSI(order[k]);
        o["secure"] = WiFi.encryptionType(order[k]) != WIFI_AUTH_OPEN;
        if (measureJson(list) > 500) { arr.remove(arr.size() - 1); break; }
      }
      String s; serializeJson(list, s);
      sgBlePost(false, s);
      WiFi.scanDelete();
      g_setupPhase = SP_WAITING; g_phaseAt = now;
      JsonDocument d; d["state"] = "scanned"; d["count"] = arr.size(); sgSetupNotify(d);
      g_setupLine = String(arr.size()) + " networks found";
      break;
    }
    case SP_CONNECTING: {
      if (WiFi.status() == WL_CONNECTED) {
        bool saved = g_setupIsPair || sgSaveWifi(g_setupSsid, g_setupPass);
        g_setupPass = String();
        String token = saved ? sgNewToken() : String();
        if (!token.length()) { WiFi.disconnect(); sgSetupFail("storage"); break; }
        JsonDocument d;
        d["state"] = "connected"; d["ssid"] = g_setupSsid; d["ip"] = WiFi.localIP().toString();
        d["host"] = "guardian.local"; d["port"] = SG_HTTP_PORT; d["ws_port"] = SG_WS_PORT;
        d["device"] = g_deviceId; d["token"] = token;
        sgSetupNotify(d);
        token = String(); d.clear();
        g_setupPhase = SP_CONNECTED; g_phaseAt = now;
        sgSetupEvent("connected");
        g_setupLine = String("Connected ") + WiFi.localIP().toString();
        break;
      }
      const char* err = sgDiscReasonError(g_wifiDiscReason);
      int32_t age = (int32_t)(now - g_phaseAt);    // signed: never "huge" if g_phaseAt is newer
      if ((err && age > 10000) || age > (int32_t)SG_SETUP_CONNECT_MS) {
        int reason = g_wifiDiscReason;
        WiFi.disconnect();
        sgSetupFail(err ? err : "timeout", reason);
      }
      break;
    }
    case SP_CONNECTED:
      if ((int32_t)(now - g_phaseAt) > (int32_t)SG_SETUP_LINGER_MS) return true;   // token was sent; go
      break;
    case SP_BYE:
      if ((int32_t)(now - g_phaseAt) > 600) return true;
      break;
    default:
      break;
  }
  if (g_setupPhase != SP_CONNECTED && g_setupPhase != SP_BYE && now - g_setupStartedAt > SG_SETUP_TIMEOUT_MS) {
    sgSetupEvent("timeout");
    return true;
  }
  return false;
}
