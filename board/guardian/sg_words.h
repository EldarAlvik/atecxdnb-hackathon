// Scam Call Guardian: family members and their security words.
// Words are stored as SHA-256(salt || normalised word), never in plain text, and
// are never logged or sent. Matching runs on each final transcript segment; a match
// is replaced by "[family word]" before the text goes anywhere. See docs/protocol.md.
#pragma once

static const int SG_MAX_MEMBERS   = 8;
static const int SG_NAME_MAX      = 24;   // bytes
static const int SG_WORD_MIN      = 3;    // bytes, normalised
static const int SG_WORD_MAX      = 48;
static const int SG_WORD_MAXWORDS = 4;
static const char* SG_REDACTED    = "[family word]";

struct SgMember {
  uint8_t  id;
  uint8_t  nwords;        // 0 = no word set
  uint8_t  reserved[2];
  char     name[SG_NAME_MAX + 1];
  uint8_t  salt[16];
  uint8_t  hash[32];
  uint32_t updated;       // Unix seconds (0 = clock was not set)
};
static SgMember g_members[SG_MAX_MEMBERS];
static uint8_t  g_memberCount  = 0;
static uint8_t  g_nextMemberId = 1;
static SemaphoreHandle_t g_wordsMtx = nullptr;

static void sgWordsLoad() {
  g_wordsMtx = xSemaphoreCreateMutex();
  Preferences p;
  if (!p.begin("sgwords", true)) return;
  size_t n = p.getBytesLength("m");
  if (n && n % sizeof(SgMember) == 0 && n / sizeof(SgMember) <= SG_MAX_MEMBERS) {
    p.getBytes("m", g_members, n);
    g_memberCount = n / sizeof(SgMember);
  }
  g_nextMemberId = p.getUChar("next", 1);
  p.end();
  for (int i = 0; i < g_memberCount; i++) {
    g_members[i].name[SG_NAME_MAX] = 0;
    if (g_members[i].id >= g_nextMemberId) g_nextMemberId = g_members[i].id + 1;
  }
}
// Caller holds g_wordsMtx.
static bool sgWordsSave() {
  Preferences p;
  if (!p.begin("sgwords", false)) return false;
  bool ok;
  if (g_memberCount) ok = p.putBytes("m", g_members, g_memberCount * sizeof(SgMember)) > 0;
  else ok = p.remove("m") || true;
  p.putUChar("next", g_nextMemberId);
  p.end();
  return ok;
}

// ---------------- normalising ----------------
// Lower case (ASCII and Latin-1 letters such as ÆØÅ -> æøå), keep letters and
// digits, everything else separates words. mock/mock_board.py has the same rules.
struct SgTok { uint16_t s, e; uint16_t ns, ne; };   // span in input, span in norm

// Writes the normalised letters of each token (no separators) to `norm`.
static int sgTokenize(const char* in, size_t len, char* norm, size_t normCap,
                      SgTok* toks, int maxToks) {
  int nt = 0; size_t nl = 0; bool inTok = false;
  for (size_t i = 0; i < len;) {
    uint8_t c = (uint8_t)in[i];
    char out[2]; int outLen = 0; size_t adv = 1;
    if (c < 0x80) {
      if (c >= 'A' && c <= 'Z') { out[0] = (char)(c + 32); outLen = 1; }
      else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) { out[0] = (char)c; outLen = 1; }
    } else if (c == 0xC3 && i + 1 < len) {
      uint8_t d = (uint8_t)in[i + 1]; adv = 2;
      if (d >= 0x80 && d <= 0x9E && d != 0x97) d += 0x20;          // upper -> lower
      if (d >= 0x9F && d <= 0xBF && d != 0xB7) { out[0] = (char)0xC3; out[1] = (char)d; outLen = 2; }
    } else {
      adv = 1;                                                      // other UTF-8: separator
      while (i + adv < len && ((uint8_t)in[i + adv] & 0xC0) == 0x80) adv++;
    }
    if (outLen && nl + outLen < normCap) {
      if (!inTok) {
        if (nt >= maxToks) break;
        toks[nt].s = (uint16_t)i; toks[nt].ns = (uint16_t)nl; inTok = true; nt++;
      }
      memcpy(norm + nl, out, outLen); nl += outLen;
      toks[nt - 1].e = (uint16_t)(i + adv); toks[nt - 1].ne = (uint16_t)nl;
    } else if (!outLen) {
      inTok = false;
    }
    i += adv;
  }
  return nt;
}

// Joins tokens [from, from+k) with single spaces into `out`. Returns length.
static size_t sgJoin(const char* norm, const SgTok* t, int from, int k, char* out, size_t cap) {
  size_t n = 0;
  for (int j = from; j < from + k; j++) {
    size_t l = t[j].ne - t[j].ns;
    if (n + l + 1 >= cap) return 0;
    if (j > from) out[n++] = ' ';
    memcpy(out + n, norm + t[j].ns, l); n += l;
  }
  out[n] = 0;
  return n;
}

// Normalises a word for storage. Returns an error message or nullptr.
static const char* sgNormaliseWord(const String& word, char* out, size_t cap, int& nwords) {
  char norm[128]; SgTok toks[16];
  int nt = sgTokenize(word.c_str(), word.length(), norm, sizeof norm, toks, 16);
  if (nt < 1 || nt > SG_WORD_MAXWORDS) return "word must be 1-4 words";
  size_t n = sgJoin(norm, toks, 0, nt, out, cap);
  if ((int)n < SG_WORD_MIN || (int)n > SG_WORD_MAX) return "word must be 3-48 bytes";
  nwords = nt;
  return nullptr;
}

// Caller holds g_wordsMtx.
static const char* sgSetWord(SgMember& m, const String& word) {
  char norm[SG_WORD_MAX + 8]; int nw = 0;
  const char* err = sgNormaliseWord(word, norm, sizeof norm, nw);
  if (err) return err;
  esp_fill_random(m.salt, sizeof m.salt);
  sgSha256(m.salt, sizeof m.salt, (const uint8_t*)norm, strlen(norm), m.hash);
  m.nwords = (uint8_t)nw;
  memset(norm, 0, sizeof norm);
  return nullptr;
}
static int sgFindMember(int id) {
  for (int i = 0; i < g_memberCount; i++) if (g_members[i].id == id) return i;
  return -1;
}

// ---------------- matching ----------------
// Checks one transcript segment. Fills `out` with the text where every spoken
// family word is replaced by SG_REDACTED, and `ids`/`names` with the members found.
static int sgWordsMatch(const char* text, String& out, uint8_t* ids, char (*names)[SG_NAME_MAX + 1], int maxIds) {
  out = text;
  size_t len = strlen(text);
  if (!g_memberCount || len == 0 || len > 1500) return 0;

  struct Cand { uint8_t id, nwords; char name[SG_NAME_MAX + 1]; uint8_t salt[16], hash[32]; };
  static Cand cands[SG_MAX_MEMBERS];          // ws task only
  int nc = 0;
  xSemaphoreTake(g_wordsMtx, portMAX_DELAY);
  for (int i = 0; i < g_memberCount; i++) {
    if (!g_members[i].nwords) continue;
    Cand& c = cands[nc++];
    c.id = g_members[i].id; c.nwords = g_members[i].nwords;
    memcpy(c.name, g_members[i].name, sizeof c.name);
    memcpy(c.salt, g_members[i].salt, 16); memcpy(c.hash, g_members[i].hash, 32);
  }
  xSemaphoreGive(g_wordsMtx);
  if (!nc) return 0;

  static char norm[1600]; static SgTok toks[200];   // ws task only
  int nt = sgTokenize(text, len, norm, sizeof norm, toks, 200);
  struct Range { uint16_t s, e; } ranges[16]; int nr = 0;
  int found = 0;
  char phrase[SG_WORD_MAX + 8]; uint8_t h[32];
  for (int c = 0; c < nc; c++) {
    bool hit = false;
    for (int i = 0; i + cands[c].nwords <= nt; i++) {
      size_t pl = sgJoin(norm, toks, i, cands[c].nwords, phrase, sizeof phrase);
      if (pl < (size_t)SG_WORD_MIN) continue;
      sgSha256(cands[c].salt, 16, (const uint8_t*)phrase, pl, h);
      if (!sgConstEq(h, cands[c].hash, 32)) continue;
      hit = true;
      if (nr < 16) ranges[nr++] = {toks[i].s, toks[i + cands[c].nwords - 1].e};
    }
    if (hit && found < maxIds) {
      ids[found] = cands[c].id;
      memcpy(names[found], cands[c].name, SG_NAME_MAX + 1);
      found++;
    }
  }
  memset(phrase, 0, sizeof phrase);
  if (!nr) return found;
  // Sort ranges, merge overlaps, rebuild the text with the words redacted.
  for (int a = 1; a < nr; a++)
    for (int b = a; b > 0 && ranges[b].s < ranges[b - 1].s; b--) { Range t = ranges[b]; ranges[b] = ranges[b - 1]; ranges[b - 1] = t; }
  String r; r.reserve(len + 16);
  size_t pos = 0;
  for (int k = 0; k < nr; k++) {
    if (ranges[k].e <= pos) continue;
    size_t s = ranges[k].s < pos ? pos : ranges[k].s;
    r.concat(text + pos, s - pos);
    r += SG_REDACTED;
    pos = ranges[k].e;
  }
  r.concat(text + pos, len - pos);
  out = r;
  return found;
}
