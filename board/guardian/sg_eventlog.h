// Scam Call Guardian: event log in LittleFS, kept across restarts.
// Up to SG_LOG_SEGMENTS files of SG_LOG_PER_SEGMENT events each in /ev. When the
// newest is full a new one starts and the oldest is deleted, so 400-500 events are
// kept. Each line is "<seq>\t<event json>\n". Only the API task touches the files
// after setup(). See docs/protocol.md.
#pragma once
#include <LittleFS.h>

static const int SG_LOG_SEGMENTS    = 5;
static const int SG_LOG_PER_SEGMENT = 100;
static const size_t SG_LOG_MAX_LINE = 1200;

static bool     g_logOk = false;
static uint32_t g_segStart[SG_LOG_SEGMENTS];   // first seq of each segment, oldest first
static int      g_segCount = 0;
static int      g_segLines = 0;                // lines in the newest segment
static uint32_t g_logLastSeq = 0;
static uint32_t g_logEvents  = 0;              // events on disk (approximate)

static void sgSegPath(uint32_t start, char* buf, size_t cap) {
  snprintf(buf, cap, "/ev/%010lu.l", (unsigned long)start);
}

// Mounts LittleFS (formatting it the first time) and finds the newest seq.
static bool sgLogBegin() {
  if (!LittleFS.begin(true)) return false;
  if (!LittleFS.exists("/ev")) LittleFS.mkdir("/ev");
  uint32_t found[16]; int nf = 0;
  File dir = LittleFS.open("/ev");
  if (!dir) return false;
  for (File f = dir.openNextFile(); f; f = dir.openNextFile()) {
    const char* nm = f.name();
    const char* base = strrchr(nm, '/'); base = base ? base + 1 : nm;
    char* end = nullptr;
    unsigned long v = strtoul(base, &end, 10);
    bool ok = end && strcmp(end, ".l") == 0;
    f.close();
    if (!ok) continue;
    if (nf < 16) found[nf++] = (uint32_t)v;
  }
  dir.close();
  for (int a = 1; a < nf; a++)
    for (int b = a; b > 0 && found[b] < found[b - 1]; b--) { uint32_t t = found[b]; found[b] = found[b - 1]; found[b - 1] = t; }
  char path[32];
  while (nf > SG_LOG_SEGMENTS) {                  // left over from an older layout
    sgSegPath(found[0], path, sizeof path); LittleFS.remove(path);
    memmove(found, found + 1, (nf - 1) * sizeof(uint32_t)); nf--;
  }
  memcpy(g_segStart, found, nf * sizeof(uint32_t));
  g_segCount = nf;
  g_logEvents = nf > 1 ? (nf - 1) * SG_LOG_PER_SEGMENT : 0;
  if (nf) {
    sgSegPath(g_segStart[nf - 1], path, sizeof path);
    File f = LittleFS.open(path, "r");
    char line[24];
    while (f && f.available()) {
      size_t n = f.readBytesUntil('\t', line, sizeof line - 1);
      line[n] = 0;
      uint32_t s = strtoul(line, nullptr, 10);
      if (s > g_logLastSeq) g_logLastSeq = s;
      g_segLines++;
      if (!f.find("\n")) break;
    }
    if (f) f.close();
    g_logEvents += g_segLines;
  }
  g_logOk = true;
  return true;
}

static uint32_t sgLogOldestSeq() { return g_segCount ? g_segStart[0] : g_logLastSeq + 1; }

static bool sgLogAppend(uint32_t seq, const char* json, size_t len) {
  if (!g_logOk || len > SG_LOG_MAX_LINE) return false;
  char path[32];
  if (g_segCount == 0 || g_segLines >= SG_LOG_PER_SEGMENT) {
    if (g_segCount == SG_LOG_SEGMENTS) {
      sgSegPath(g_segStart[0], path, sizeof path); LittleFS.remove(path);
      memmove(g_segStart, g_segStart + 1, (SG_LOG_SEGMENTS - 1) * sizeof(uint32_t));
      g_segCount--;
      g_logEvents -= SG_LOG_PER_SEGMENT;
    }
    g_segStart[g_segCount++] = seq;
    g_segLines = 0;
  }
  sgSegPath(g_segStart[g_segCount - 1], path, sizeof path);
  File f = LittleFS.open(path, "a");
  if (!f) return false;
  char pre[16];
  int pl = snprintf(pre, sizeof pre, "%lu\t", (unsigned long)seq);
  bool ok = f.write((const uint8_t*)pre, pl) == (size_t)pl &&
            f.write((const uint8_t*)json, len) == len &&
            f.write((const uint8_t*)"\n", 1) == 1;
  f.close();
  if (ok) { g_segLines++; g_logEvents++; g_logLastSeq = seq; }
  return ok;
}

// Calls fn(json, len) for each logged event with seq > since, oldest first, until
// fn returns false. Returns false if it stopped early (more events are waiting).
template <typename F>
static bool sgLogForEach(uint32_t since, F fn) {
  if (!g_logOk) return true;
  static char line[SG_LOG_MAX_LINE + 32];        // API task only
  char path[32];
  for (int s = 0; s < g_segCount; s++) {
    // Skip whole segments that end at or before `since`.
    if (s + 1 < g_segCount && g_segStart[s + 1] <= since + 1) continue;
    sgSegPath(g_segStart[s], path, sizeof path);
    File f = LittleFS.open(path, "r");
    if (!f) continue;
    while (f.available()) {
      size_t n = f.readBytesUntil('\n', line, sizeof line - 1);
      line[n] = 0;
      char* tab = strchr(line, '\t');
      if (!tab) continue;
      uint32_t seq = strtoul(line, nullptr, 10);
      if (seq <= since) continue;
      if (!fn(tab + 1, n - (tab + 1 - line))) { f.close(); return false; }
    }
    f.close();
  }
  return true;
}
