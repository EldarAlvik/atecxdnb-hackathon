// Scam Call Guardian: settings, saved Wi-Fi and the device token, all in NVS.
// Included from project.yaml (code: globals:). See docs/protocol.md.
#pragma once
#include <Preferences.h>
#include "mbedtls/sha256.h"
#include "mbedtls/version.h"
#include "esp_random.h"

#define SG_FW_VERSION "2.0.0"
#define SG_PROTO 2

// ---------------- settings (/config) ----------------
enum SgLang : uint8_t { SG_LANG_MULTI = 0, SG_LANG_NO, SG_LANG_EN };
static const char* const SG_LANG_NAMES[] = {"multi", "no", "en"};

// Event keys the `events` setting can switch off for the WebSocket and the log.
// Keys not listed here (startup, ws_msg, wifi_retry) are always sent.
static const char* const SG_EVENT_KEYS[] = {
    "guard", "wifi", "ws", "transcript", "verdict", "health", "word_match",
    "setup", "config", "words", "degraded", "error"};
static const int SG_EVENT_KEY_COUNT = sizeof(SG_EVENT_KEYS) / sizeof(SG_EVENT_KEYS[0]);
static const uint32_t SG_EVENTS_ALL = (1u << SG_EVENT_KEY_COUNT) - 1;

struct SgSettings {
  uint8_t  micGain     = 8;     // 1..64
  uint8_t  aiIntervalS = 6;     // 3..60
  uint8_t  lang        = SG_LANG_MULTI;
  bool     redLatch    = true;
  uint32_t eventMask   = SG_EVENTS_ALL;
  uint8_t  screenPct   = 100;   // 10..100
};
// Written only by the API task (under g_mtx); other tasks read single fields.
static SgSettings g_cfg;

static int sgEventBit(const char* key) {
  for (int i = 0; i < SG_EVENT_KEY_COUNT; i++)
    if (strcmp(key, SG_EVENT_KEYS[i]) == 0) return i;
  return -1;
}
static bool sgEventEnabled(const char* key) {
  int b = sgEventBit(key);
  return b < 0 || (g_cfg.eventMask & (1u << b));
}

static void sgLoadSettings() {
  Preferences p;
  if (!p.begin("sg", true)) return;            // nothing saved yet: defaults
  g_cfg.micGain     = constrain(p.getUChar("gain", 8), 1, 64);
  g_cfg.aiIntervalS = constrain(p.getUChar("aiint", 6), 3, 60);
  g_cfg.lang        = p.getUChar("lang", SG_LANG_MULTI) % 3;
  g_cfg.redLatch    = p.getBool("latch", true);
  g_cfg.eventMask   = p.getULong("evmask", SG_EVENTS_ALL) & SG_EVENTS_ALL;
  g_cfg.screenPct   = constrain(p.getUChar("bright", 100), 10, 100);
  p.end();
}
static bool sgSaveSettings(const SgSettings& c) {
  Preferences p;
  if (!p.begin("sg", false)) return false;
  bool ok = p.putUChar("gain", c.micGain) && p.putUChar("aiint", c.aiIntervalS) &&
            p.putUChar("lang", c.lang) && p.putBool("latch", c.redLatch) &&
            p.putULong("evmask", c.eventMask) && p.putUChar("bright", c.screenPct);
  p.end();
  return ok;
}

// ---------------- saved Wi-Fi ----------------
static String g_savedSsid, g_savedPass;

static void sgLoadWifi() {
  Preferences p;
  if (!p.begin("sgwifi", true)) return;
  g_savedSsid = p.getString("ssid", "");
  g_savedPass = p.getString("pass", "");
  p.end();
}
static bool sgSaveWifi(const String& ssid, const String& pass) {
  Preferences p;
  if (!p.begin("sgwifi", false)) return false;
  // An empty password is stored as a zero-length string (putString returns 0).
  bool ok = p.putString("ssid", ssid) == ssid.length() && p.putString("pass", pass) == pass.length();
  p.end();
  if (ok) { g_savedSsid = ssid; g_savedPass = pass; }
  return ok;
}
static bool sgHasFallbackWifi() { return strlen(SG_WIFI_SSID) > 0; }

// ---------------- hashing ----------------
static void sgSha256(const uint8_t* a, size_t al, const uint8_t* b, size_t bl, uint8_t out[32]) {
  mbedtls_sha256_context c;
  mbedtls_sha256_init(&c);
#if MBEDTLS_VERSION_MAJOR >= 3
  mbedtls_sha256_starts(&c, 0);
  if (al) mbedtls_sha256_update(&c, a, al);
  if (bl) mbedtls_sha256_update(&c, b, bl);
  mbedtls_sha256_finish(&c, out);
#else
  mbedtls_sha256_starts_ret(&c, 0);
  if (al) mbedtls_sha256_update_ret(&c, a, al);
  if (bl) mbedtls_sha256_update_ret(&c, b, bl);
  mbedtls_sha256_finish_ret(&c, out);
#endif
  mbedtls_sha256_free(&c);
}
static bool sgConstEq(const uint8_t* a, const uint8_t* b, size_t n) {
  uint8_t d = 0;
  for (size_t i = 0; i < n; i++) d |= a[i] ^ b[i];
  return d == 0;
}

// ---------------- device token ----------------
// Only SHA-256(token) is kept, in RAM and NVS. The token itself leaves the board
// once, in the BLE `connected` notification (or the USB `api_token` action).
static uint8_t g_tokenHash[32];
static bool    g_hasToken = false;

static void sgLoadToken() {
  Preferences p;
  if (!p.begin("sgauth", true)) return;
  g_hasToken = p.getBytes("tokh", g_tokenHash, 32) == 32;
  p.end();
}
// Returns "" if the token could not be saved (then the old one stays valid).
static String sgNewToken() {
  uint8_t raw[32];
  esp_fill_random(raw, sizeof raw);
  uint8_t h[32];
  sgSha256(raw, sizeof raw, nullptr, 0, h);
  Preferences p;
  bool ok = p.begin("sgauth", false) && p.putBytes("tokh", h, 32) == 32;
  p.end();
  if (!ok) { memset(raw, 0, sizeof raw); return String(); }
  memcpy(g_tokenHash, h, 32);
  g_hasToken = true;
  static const char* hex = "0123456789abcdef";
  String out; out.reserve(64);
  for (int i = 0; i < 32; i++) { out += hex[raw[i] >> 4]; out += hex[raw[i] & 15]; }
  memset(raw, 0, sizeof raw);
  return out;
}
static int sgHexNib(char c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  if (c >= 'A' && c <= 'F') return c - 'A' + 10;
  return -1;
}
// `tok` may continue after the 64 hex chars (e.g. "&x=1" in a URL).
static bool sgTokenValid(const char* tok) {
  if (!g_hasToken || !tok) return false;
  uint8_t raw[32];
  for (int i = 0; i < 32; i++) {
    int hi = sgHexNib(tok[2 * i]), lo = hi < 0 ? -1 : sgHexNib(tok[2 * i + 1]);
    if (hi < 0 || lo < 0) return false;
    raw[i] = (uint8_t)(hi << 4 | lo);
  }
  char after = tok[64];
  if (after && after != '&' && after != ' ' && after != '\r' && after != '\n') return false;
  uint8_t h[32];
  sgSha256(raw, sizeof raw, nullptr, 0, h);
  return sgConstEq(h, g_tokenHash, 32);
}

// ---------------- device id ----------------
static char g_deviceId[16] = "guardian-0000";
static void sgInitDeviceId() {
  uint64_t mac = ESP.getEfuseMac();             // byte 0 of the MAC is the low byte
  snprintf(g_deviceId, sizeof g_deviceId, "guardian-%02x%02x",
           (unsigned)((mac >> 32) & 0xFF), (unsigned)((mac >> 40) & 0xFF));
}
