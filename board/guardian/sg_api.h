// Scam Call Guardian: local API on the home Wi-Fi. HTTP on port 80, WebSocket on
// port 81, mDNS name guardian.local, DNS-SD _guardian._tcp. Every request needs the
// device token. Runs in its own task, which also writes the event log and fans
// events out to WebSocket clients. See docs/protocol.md.
//
// Needs from project.yaml: g_mtx, the guardian state (g_guarding, g_verdict, ...),
// sgEmit*, g_netQ, g_seq, g_guardReq, g_restartAt, sgRequestSetup().
#pragma once
#include <WebServer.h>
#include <WebSocketsServer.h>
#include <ESPmDNS.h>

static const uint16_t SG_HTTP_PORT = 80;
static const uint16_t SG_WS_PORT   = 81;
static const int      SG_WS_MAX_CLIENTS = 2;
static const uint32_t SG_HEAP_WS_MIN   = 40000;   // refuse new WebSocket clients below
static const uint32_t SG_HEAP_HTTP_MIN = 30000;   // 503 below
static const uint32_t SG_BLOCK_LOG_MIN = 8000;    // skip log writes below (largest block)

enum : uint8_t { SG_DEG_WS = 1, SG_DEG_HTTP = 2, SG_DEG_LOG = 4 };
static const char* const SG_DEG_NAMES[] = {"ws_server", "http", "event_log"};
static volatile uint8_t g_degraded = 0;

static void sgDegrade(uint8_t bit, const char* reason) {
  if (g_degraded & bit) return;
  g_degraded |= bit;
  int i = bit == SG_DEG_WS ? 0 : bit == SG_DEG_HTTP ? 1 : 2;
  JsonDocument d;
  d["feature"] = SG_DEG_NAMES[i];
  d["reason"] = reason;
  d["free_heap"] = ESP.getFreeHeap();
  d["max_block"] = ESP.getMaxAllocHeap();
  sgEmitDoc("state", "degraded", d);
}
static void sgRecover(uint8_t bit) { g_degraded &= ~bit; }

static WebServer*        g_http = nullptr;
static WebSocketsServer* g_wss  = nullptr;
static bool     g_wsAuthed[WEBSOCKETS_SERVER_CLIENT_MAX];
static volatile int g_wsClientCount = 0;
static uint8_t  g_badTokens = 0;
static uint32_t g_lockUntil = 0;
static bool     g_apiStarted = false;

static const char* sgVerdictName(uint8_t v) {
  static const char* names[] = {"none", "safe", "unsure", "scam", "ai_error"};
  return v < 5 ? names[v] : "none";
}

// ---------------- HTTP helpers ----------------
static void sgSendJson(int code, JsonDocument& d) {
  String out; serializeJson(d, out);
  g_http->send(code, "application/json; charset=utf-8", out);
}
static void sgSendError(int code, const char* msg) {
  JsonDocument d; d["error"] = msg; sgSendJson(code, d);
}

// Token, lockout and memory checks for every request.
static bool sgGate() {
  uint32_t now = millis();
  if (g_lockUntil && (int32_t)(g_lockUntil - now) > 0) { sgSendError(429, "too_many_attempts"); return false; }
  g_lockUntil = 0;
  String a = g_http->header("Authorization");
  if (!a.startsWith("Bearer ") || !sgTokenValid(a.c_str() + 7)) {
    if (++g_badTokens >= 10) { g_badTokens = 0; g_lockUntil = now + 30000; }
    sgSendError(401, "unauthorized");
    return false;
  }
  g_badTokens = 0;
  if (ESP.getFreeHeap() < SG_HEAP_HTTP_MIN) {
    sgDegrade(SG_DEG_HTTP, "low_heap");
    sgSendError(503, "low_memory");
    return false;
  }
  sgRecover(SG_DEG_HTTP);
  return true;
}
// Parses the request body as a JSON object. Sends 400 and returns false if not.
static bool sgBody(JsonDocument& d) {
  String b = g_http->arg("plain");
  if (b.length() > 1024) { sgSendError(400, "body too large"); return false; }
  if (deserializeJson(d, b) || !d.is<JsonObject>()) { sgSendError(400, "bad json"); return false; }
  return true;
}
static uint32_t sgNow() { time_t t = time(nullptr); return t > 1700000000 ? (uint32_t)t : 0; }

// ---------------- /status ----------------
static void sgHandleStatus() {
  if (!sgGate()) return;
  JsonDocument d;
  d["device"] = g_deviceId;
  d["fw"] = SG_FW_VERSION;
  d["proto"] = SG_PROTO;
  d["uptime_s"] = millis() / 1000;
  d["time"] = sgNow();
  d["guarding"] = (bool)g_guarding;
  String reason, tail; int matchId; char matchName[SG_NAME_MAX + 1];
  xSemaphoreTake(g_mtx, portMAX_DELAY);
  d["verdict"] = sgVerdictName(g_verdict);
  reason = g_reason;
  int from = (int)g_transcript.length() - 300;
  tail = g_transcript.substring(from < 0 ? 0 : from);
  matchId = g_wordMatchId;
  memcpy(matchName, g_wordMatchName, sizeof matchName);
  xSemaphoreGive(g_mtx);
  if (from > 0) { int sp = tail.indexOf(' '); if (sp >= 0) tail = tail.substring(sp + 1); }
  d["reason"] = reason;
  d["latched"] = (bool)g_scamLatched;
  d["transcript"] = tail;
  if (matchId) { d["word_match"]["member_id"] = matchId; d["word_match"]["member"] = matchName; }
  else d["word_match"] = nullptr;
  JsonObject w = d["wifi"].to<JsonObject>();
  w["ssid"] = WiFi.SSID();
  w["rssi"] = WiFi.RSSI();
  w["ip"] = WiFi.localIP().toString();
  w["source"] = g_wifiSource == SG_WIFI_SAVED ? "saved" : g_wifiSource == SG_WIFI_FALLBACK ? "fallback" : "none";
  d["stt"] = g_wsConnected ? "connected" : (g_sttStarted ? "connecting" : "closed");
  JsonObject ai = d["ai"].to<JsonObject>();
  ai["calls"] = (uint32_t)g_aiCalls; ai["last_ms"] = (uint32_t)g_aiLastMs; ai["last_http"] = (int)g_aiLastHttp;
  JsonObject h = d["heap"].to<JsonObject>();
  h["free"] = ESP.getFreeHeap(); h["min"] = ESP.getMinFreeHeap(); h["max_block"] = ESP.getMaxAllocHeap();
  JsonArray deg = d["degraded"].to<JsonArray>();
  for (int i = 0; i < 3; i++) if (g_degraded & (1 << i)) deg.add(SG_DEG_NAMES[i]);
  d["last_seq"] = (uint32_t)g_seq;
  d["members"] = g_memberCount;
  sgSendJson(200, d);
}

// ---------------- /guard ----------------
static void sgHandleGuard() {
  if (!sgGate()) return;
  JsonDocument b;
  if (!sgBody(b)) return;
  if (!b["on"].is<bool>()) { sgSendError(400, "on must be true or false"); return; }
  bool on = b["on"];
  g_guardReq = on ? 1 : 0;                       // loop() applies it
  JsonDocument d; d["guarding"] = on;
  sgSendJson(200, d);
}

// ---------------- /events ----------------
static void sgHandleEvents() {
  if (!sgGate()) return;
  long since = g_http->hasArg("since") ? g_http->arg("since").toInt() : 0;
  long limit = g_http->hasArg("limit") ? g_http->arg("limit").toInt() : 100;
  if (since < 0) since = 0;
  if (limit < 1) limit = 1;
  if (limit > 200) limit = 200;
  g_http->setContentLength(CONTENT_LENGTH_UNKNOWN);
  g_http->send(200, "application/json; charset=utf-8", "");
  g_http->sendContent("{\"events\":[");
  int sent = 0; bool more = false;
  sgLogForEach((uint32_t)since, [&](const char* json, size_t len) {
    if (sent >= limit) { more = true; return false; }
    if (sent) g_http->sendContent(",", 1);
    g_http->sendContent(json, len);
    sent++;
    return true;
  });
  char tail[96];
  snprintf(tail, sizeof tail, "],\"last_seq\":%lu,\"oldest_seq\":%lu,\"more\":%s}",
           (unsigned long)g_seq, (unsigned long)sgLogOldestSeq(), more ? "true" : "false");
  g_http->sendContent(tail);
  g_http->sendContent("");                        // end of chunked response
}

// ---------------- /config ----------------
static void sgConfigJson(JsonDocument& d) {
  d["mic_gain"] = g_cfg.micGain;
  d["ai_interval_s"] = g_cfg.aiIntervalS;
  d["language"] = SG_LANG_NAMES[g_cfg.lang % 3];
  d["red_latch"] = g_cfg.redLatch;
  JsonArray ev = d["events"].to<JsonArray>();
  for (int i = 0; i < SG_EVENT_KEY_COUNT; i++) if (g_cfg.eventMask & (1u << i)) ev.add(SG_EVENT_KEYS[i]);
  d["screen_brightness"] = g_cfg.screenPct;
}
static bool sgIntIn(JsonVariantConst v, int lo, int hi, int& out) {
  if (!v.is<int>() || v.is<bool>()) return false;
  out = v.as<int>();
  return out >= lo && out <= hi;
}
static void sgHandleConfig() {
  if (!sgGate()) return;
  if (g_http->method() == HTTP_POST) {
    JsonDocument b;
    if (!sgBody(b)) return;
    SgSettings n = g_cfg;
    JsonDocument changed; JsonArray ch = changed["changed"].to<JsonArray>();
    for (JsonPairConst kv : b.as<JsonObjectConst>()) {
      const char* k = kv.key().c_str(); JsonVariantConst v = kv.value(); int x;
      if (!strcmp(k, "mic_gain")) {
        if (!sgIntIn(v, 1, 64, x)) { sgSendError(400, "mic_gain must be 1-64"); return; }
        if (x != n.micGain) ch.add(k); n.micGain = x;
      } else if (!strcmp(k, "ai_interval_s")) {
        if (!sgIntIn(v, 3, 60, x)) { sgSendError(400, "ai_interval_s must be 3-60"); return; }
        if (x != n.aiIntervalS) ch.add(k); n.aiIntervalS = x;
      } else if (!strcmp(k, "language")) {
        const char* s = v | ""; int li = -1;
        for (int i = 0; i < 3; i++) if (!strcmp(s, SG_LANG_NAMES[i])) li = i;
        if (li < 0) { sgSendError(400, "language must be multi, no or en"); return; }
        if (li != n.lang) ch.add(k); n.lang = li;
      } else if (!strcmp(k, "red_latch")) {
        if (!v.is<bool>()) { sgSendError(400, "red_latch must be true or false"); return; }
        if (v.as<bool>() != n.redLatch) ch.add(k); n.redLatch = v.as<bool>();
      } else if (!strcmp(k, "events")) {
        if (!v.is<JsonArrayConst>()) { sgSendError(400, "events must be a list"); return; }
        uint32_t m = 0;
        for (JsonVariantConst e : v.as<JsonArrayConst>()) {
          int bit = sgEventBit(e | "");
          if (bit < 0) { sgSendError(400, "unknown event key in events"); return; }
          m |= 1u << bit;
        }
        if (m != n.eventMask) ch.add(k); n.eventMask = m;
      } else if (!strcmp(k, "screen_brightness")) {
        if (!sgIntIn(v, 10, 100, x)) { sgSendError(400, "screen_brightness must be 10-100"); return; }
        if (x != n.screenPct) ch.add(k); n.screenPct = x;
      } else {
        sgSendError(400, "unknown setting"); return;
      }
    }
    if (ch.size()) {
      xSemaphoreTake(g_mtx, portMAX_DELAY);
      g_cfg = n;
      xSemaphoreGive(g_mtx);
      g_gain = n.micGain;
      sgSaveSettings();
      sgEmitDoc("state", "config", changed);
    }
  }
  JsonDocument d; sgConfigJson(d); sgSendJson(200, d);
}

// ---------------- /words ----------------
static void sgMemberJson(const SgMember& m, JsonObject o) {
  o["id"] = m.id; o["name"] = m.name; o["has_word"] = m.nwords > 0; o["updated"] = m.updated;
}
static void sgWordsEvent(const char* action, int id, const char* name) {
  JsonDocument e; e["action"] = action; e["id"] = id; e["name"] = name;
  sgEmitDoc("state", "words", e);
}
static void sgHandleWords() {
  if (!sgGate()) return;
  HTTPMethod method = g_http->method();
  if (method == HTTP_GET) {
    JsonDocument d; JsonArray a = d["members"].to<JsonArray>();
    xSemaphoreTake(g_wordsMtx, portMAX_DELAY);
    for (int i = 0; i < g_memberCount; i++) sgMemberJson(g_members[i], a.add<JsonObject>());
    xSemaphoreGive(g_wordsMtx);
    d["max_members"] = SG_MAX_MEMBERS;
    sgSendJson(200, d);
    return;
  }
  if (method == HTTP_DELETE) {
    int id = g_http->arg("id").toInt();
    xSemaphoreTake(g_wordsMtx, portMAX_DELAY);
    int i = sgFindMember(id);
    if (i < 0) { xSemaphoreGive(g_wordsMtx); sgSendError(404, "no such member"); return; }
    char name[SG_NAME_MAX + 1]; memcpy(name, g_members[i].name, sizeof name);
    memset(&g_members[i], 0, sizeof(SgMember));
    memmove(&g_members[i], &g_members[i + 1], (g_memberCount - i - 1) * sizeof(SgMember));
    g_memberCount--;
    sgWordsSave();
    xSemaphoreGive(g_wordsMtx);
    sgWordsEvent("remove", id, name);
    JsonDocument d; d["deleted"] = id; sgSendJson(200, d);
    return;
  }
  // POST
  JsonDocument b;
  if (!sgBody(b)) return;
  JsonVariantConst nameV = b["name"], wordV = b["word"];
  String name;
  if (!nameV.isNull()) {
    name = nameV | ""; name.trim();
    if (name.length() < 1 || name.length() > SG_NAME_MAX) { sgSendError(400, "name must be 1-24 bytes"); return; }
  }
  if (!wordV.isNull() && !wordV.is<const char*>()) { sgSendError(400, "word must be a string"); return; }
  String word = wordV | "";
  JsonDocument d;
  const char* err = nullptr; int code = 400;
  const char* actions[3]; int na = 0; int id = 0; char evName[SG_NAME_MAX + 1] = "";
  xSemaphoreTake(g_wordsMtx, portMAX_DELAY);
  if (b["id"].is<int>()) {
    id = b["id"];
    int i = sgFindMember(id);
    if (i < 0) { err = "no such member"; code = 404; }
    else {
      SgMember m = g_members[i];
      if (name.length()) { strlcpy(m.name, name.c_str(), sizeof m.name); actions[na++] = "rename"; }
      if (b["clear"] | false) { memset(m.salt, 0, 16); memset(m.hash, 0, 32); m.nwords = 0; actions[na++] = "clear"; }
      else if (!wordV.isNull()) { err = sgSetWord(m, word); actions[na++] = "set"; }
      if (!err) {
        m.updated = sgNow();
        g_members[i] = m;
        if (!sgWordsSave()) err = "could not save";
        sgMemberJson(m, d.to<JsonObject>());
        memcpy(evName, m.name, sizeof evName);
      }
    }
  } else if (!b["id"].isNull()) {
    err = "id must be an integer";
  } else if (!name.length()) {
    err = "name must be 1-24 bytes";
  } else if (g_memberCount >= SG_MAX_MEMBERS) {
    err = "at most 8 members";
  } else {
    SgMember m; memset(&m, 0, sizeof m);
    strlcpy(m.name, name.c_str(), sizeof m.name);
    if (word.length()) err = sgSetWord(m, word);
    if (!err) {
      m.id = g_nextMemberId++;
      if (g_nextMemberId == 0) g_nextMemberId = 1;
      m.updated = sgNow();
      g_members[g_memberCount++] = m;
      if (!sgWordsSave()) err = "could not save";
      sgMemberJson(m, d.to<JsonObject>());
      id = m.id; memcpy(evName, m.name, sizeof evName);
      actions[na++] = "add";
    }
  }
  xSemaphoreGive(g_wordsMtx);
  word = String();                                // drop the plain word
  b.clear();
  if (err) { sgSendError(code, err); return; }
  for (int k = 0; k < na; k++) sgWordsEvent(actions[k], id, evName);
  sgSendJson(200, d);
}

// ---------------- /setup ----------------
static void sgHandleSetup() {
  if (!sgGate()) return;
  JsonDocument d; d["setup"] = true; sgSendJson(200, d);
  sgRequestSetup();                               // loop() restarts in a moment
}

// ---------------- WebSocket ----------------
static void sgWsServerEvent(uint8_t num, WStype_t type, uint8_t* payload, size_t len) {
  if (num >= WEBSOCKETS_SERVER_CLIENT_MAX) return;
  if (type == WStype_DISCONNECTED) {
    if (g_wsAuthed[num]) { g_wsAuthed[num] = false; g_wsClientCount--; }
    return;
  }
  if (type != WStype_CONNECTED) return;          // client messages are ignored
  const char* url = (const char*)payload;
  const char* t = url ? strstr(url, "token=") : nullptr;
  if (!t || !sgTokenValid(t + 6)) { g_wss->disconnect(num); return; }
  if (g_wsClientCount >= SG_WS_MAX_CLIENTS) { g_wss->disconnect(num); return; }
  if (ESP.getFreeHeap() < SG_HEAP_WS_MIN) {
    sgDegrade(SG_DEG_WS, "low_heap");
    g_wss->disconnect(num);
    return;
  }
  sgRecover(SG_DEG_WS);
  g_wsAuthed[num] = true; g_wsClientCount++;
  char hello[160];
  int n = snprintf(hello, sizeof hello,
                   "{\"type\":\"hello\",\"device\":\"%s\",\"fw\":\"%s\",\"proto\":%d,\"last_seq\":%lu}",
                   g_deviceId, SG_FW_VERSION, SG_PROTO, (unsigned long)g_seq);
  g_wss->sendTXT(num, hello, n);
}
static void sgWsBroadcast(const char* json, size_t len) {
  if (!g_wss || !g_wsClientCount) return;
  for (int i = 0; i < WEBSOCKETS_SERVER_CLIENT_MAX; i++)
    if (g_wsAuthed[i]) g_wss->sendTXT(i, json, len);
}

// ---------------- start / task ----------------
static void sgApiStart() {
  static const char* hdrs[] = {"Authorization"};
  g_http = new WebServer(SG_HTTP_PORT);
  g_http->collectHeaders(hdrs, 1);
  g_http->on("/status", HTTP_GET, sgHandleStatus);
  g_http->on("/guard", HTTP_POST, sgHandleGuard);
  g_http->on("/events", HTTP_GET, sgHandleEvents);
  g_http->on("/config", HTTP_GET, sgHandleConfig);
  g_http->on("/config", HTTP_POST, sgHandleConfig);
  g_http->on("/words", HTTP_GET, sgHandleWords);
  g_http->on("/words", HTTP_POST, sgHandleWords);
  g_http->on("/words", HTTP_DELETE, sgHandleWords);
  g_http->on("/setup", HTTP_POST, sgHandleSetup);
  g_http->onNotFound([]() { sgSendError(404, "not found"); });
  g_http->begin();
  g_wss = new WebSocketsServer(SG_WS_PORT);
  g_wss->onEvent(sgWsServerEvent);
  g_wss->enableHeartbeat(15000, 10000, 1);
  g_wss->begin();
  if (MDNS.begin("guardian")) {
    MDNS.addService("guardian", "tcp", SG_HTTP_PORT);
    MDNS.addServiceTxt("guardian", "tcp", "id", (const char*)g_deviceId);
    MDNS.addServiceTxt("guardian", "tcp", "ws", String(SG_WS_PORT));
    MDNS.addServiceTxt("guardian", "tcp", "proto", String(SG_PROTO));
    MDNS.addServiceTxt("guardian", "tcp", "fw", SG_FW_VERSION);
    MDNS.addService("http", "tcp", SG_HTTP_PORT);
  } else {
    sgEmitStr("log", "error", "mdns: begin failed");
  }
  configTime(0, 0, "pool.ntp.org", "time.google.com");
  g_apiStarted = true;
}

// One queued event (see sgEmit): flags in byte 0, seq in bytes 4-7, then the JSON line.
static void sgApiHandleEvent(char* line) {
  uint8_t flags = (uint8_t)line[0];
  uint32_t seq; memcpy(&seq, line + 4, 4);
  char* json = line + SG_EV_HDR;
  size_t len = strlen(json);
  if (len && json[len - 1] == '\n') len--;
  if (flags & SG_EV_LOG) {
    if (ESP.getMaxAllocHeap() < SG_BLOCK_LOG_MIN) sgDegrade(SG_DEG_LOG, "low_heap");
    else {
      sgRecover(SG_DEG_LOG);
      if (!sgLogAppend(seq, json, len)) sgEmitStr("log", "error", "log: write failed");
    }
  }
  if ((flags & SG_EV_NET) && g_apiStarted) sgWsBroadcast(json, len);
  free(line);
}

static void sgApiTask(void*) {
  for (;;) {
    if (!g_apiStarted && !g_setupMode && WiFi.status() == WL_CONNECTED) sgApiStart();
    if (g_apiStarted) {
      g_http->handleClient();
      g_wss->loop();
    }
    char* line;
    for (int i = 0; i < 8 && xQueueReceive(g_netQ, &line, 0) == pdTRUE; i++) sgApiHandleEvent(line);
    vTaskDelay(pdMS_TO_TICKS(g_apiStarted ? 2 : 20));
  }
}
