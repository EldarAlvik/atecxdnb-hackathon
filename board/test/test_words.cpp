// Host-side test of board/guardian/sg_words.h: normalising, matching, redaction.
// Built and run by board/test/run_tests.py (g++). Stubs stand in for Arduino.
//
//   test_words            run the built-in checks
//   test_words --norm     read lines on stdin, print the normalised form of each
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <string>

// ---------------- Arduino / FreeRTOS stubs ----------------
class String {
 public:
  String() {}
  String(const char* s) : s_(s ? s : "") {}
  String(const std::string& s) : s_(s) {}
  const char* c_str() const { return s_.c_str(); }
  size_t length() const { return s_.size(); }
  void reserve(size_t n) { s_.reserve(n); }
  bool concat(const char* p, size_t n) { s_.append(p, n); return true; }
  String& operator+=(const char* p) { s_ += p; return *this; }
  String& operator=(const char* p) { s_ = p; return *this; }
  bool operator==(const char* p) const { return s_ == p; }
 private:
  std::string s_;
};
class Preferences {
 public:
  bool begin(const char*, bool) { return false; }
  void end() {}
  size_t getBytesLength(const char*) { return 0; }
  size_t getBytes(const char*, void*, size_t) { return 0; }
  size_t putBytes(const char*, const void*, size_t n) { return n; }
  uint8_t getUChar(const char*, uint8_t d) { return d; }
  size_t putUChar(const char*, uint8_t) { return 1; }
  bool remove(const char*) { return true; }
};
typedef int* SemaphoreHandle_t;
static int g_semDummy;
#define portMAX_DELAY 0
static SemaphoreHandle_t xSemaphoreCreateMutex() { return &g_semDummy; }
static void xSemaphoreTake(SemaphoreHandle_t, int) {}
static void xSemaphoreGive(SemaphoreHandle_t) {}
static void esp_fill_random(void* p, size_t n) { for (size_t i = 0; i < n; i++) ((uint8_t*)p)[i] = rand(); }

// ---------------- SHA-256 (small reference implementation) ----------------
struct Sha256 {
  uint32_t h[8], len = 0; uint64_t bits = 0; uint8_t buf[64];
  static uint32_t ror(uint32_t x, int n) { return (x >> n) | (x << (32 - n)); }
  Sha256() { static const uint32_t i[8] = {0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19}; memcpy(h, i, 32); }
  void block(const uint8_t* p) {
    static const uint32_t k[64] = {
      0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
      0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
      0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
      0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2};
    uint32_t w[64];
    for (int i = 0; i < 16; i++) w[i] = p[4*i] << 24 | p[4*i+1] << 16 | p[4*i+2] << 8 | p[4*i+3];
    for (int i = 16; i < 64; i++) {
      uint32_t s0 = ror(w[i-15],7) ^ ror(w[i-15],18) ^ (w[i-15] >> 3), s1 = ror(w[i-2],17) ^ ror(w[i-2],19) ^ (w[i-2] >> 10);
      w[i] = w[i-16] + s0 + w[i-7] + s1;
    }
    uint32_t a=h[0],b=h[1],c=h[2],d=h[3],e=h[4],f=h[5],g=h[6],hh=h[7];
    for (int i = 0; i < 64; i++) {
      uint32_t t1 = hh + (ror(e,6)^ror(e,11)^ror(e,25)) + ((e&f)^(~e&g)) + k[i] + w[i];
      uint32_t t2 = (ror(a,2)^ror(a,13)^ror(a,22)) + ((a&b)^(a&c)^(b&c));
      hh=g; g=f; f=e; e=d+t1; d=c; c=b; b=a; a=t1+t2;
    }
    h[0]+=a;h[1]+=b;h[2]+=c;h[3]+=d;h[4]+=e;h[5]+=f;h[6]+=g;h[7]+=hh;
  }
  void update(const uint8_t* p, size_t n) { for (size_t i = 0; i < n; i++) { buf[len++] = p[i]; bits += 8; if (len == 64) { block(buf); len = 0; } } }
  void finish(uint8_t out[32]) {
    uint64_t b = bits; uint8_t one = 0x80, zero = 0;
    update(&one, 1);
    while (len != 56) update(&zero, 1);
    for (int i = 7; i >= 0; i--) { uint8_t x = b >> (8 * i); update(&x, 1); }
    for (int i = 0; i < 8; i++) { out[4*i] = h[i] >> 24; out[4*i+1] = h[i] >> 16; out[4*i+2] = h[i] >> 8; out[4*i+3] = h[i]; }
  }
};
static void sgSha256(const uint8_t* a, size_t al, const uint8_t* b, size_t bl, uint8_t out[32]) {
  Sha256 s; if (al) s.update(a, al); if (bl) s.update(b, bl); s.finish(out);
}
static bool sgConstEq(const uint8_t* a, const uint8_t* b, size_t n) { return memcmp(a, b, n) == 0; }

#include "../guardian/sg_words.h"

// ---------------- tests ----------------
static int failures = 0;
#define CHECK(cond, msg) do { if (!(cond)) { printf("FAIL %s\n", msg); failures++; } else printf("PASS %s\n", msg); } while (0)

static std::string normalise(const std::string& s) {
  char out[256]; int nw = 0;
  const char* err = sgNormaliseWord(String(s), out, sizeof out, nw);
  return err ? std::string("<") + err + ">" : std::string(out);
}

static int addMember(const char* name, const char* word) {
  SgMember m; memset(&m, 0, sizeof m);
  strncpy(m.name, name, SG_NAME_MAX);
  m.id = g_nextMemberId++;
  const char* err = sgSetWord(m, String(word));
  assert(!err);
  g_members[g_memberCount++] = m;
  return m.id;
}

static std::string match(const char* text, int* nfound = nullptr, int* firstId = nullptr) {
  String out; uint8_t ids[4]; char names[4][SG_NAME_MAX + 1];
  int n = sgWordsMatch(text, out, ids, names, 4);
  if (nfound) *nfound = n;
  if (firstId) *firstId = n ? ids[0] : 0;
  return out.c_str();
}

int main(int argc, char** argv) {
  if (argc > 1 && !strcmp(argv[1], "--norm")) {
    std::string line;
    while (std::getline(std::cin, line)) {
      if (!line.empty() && line.back() == '\r') line.pop_back();
      char norm[1024]; SgTok toks[200]; char out[1100];
      int nt = sgTokenize(line.c_str(), line.size(), norm, sizeof norm, toks, 200);
      size_t n = nt ? sgJoin(norm, toks, 0, nt, out, sizeof out) : 0;
      out[n] = 0;
      printf("%s\n", out);
    }
    return 0;
  }
  g_wordsMtx = xSemaphoreCreateMutex();

  CHECK(normalise("Blå Elefant!") == "blå elefant", "normalise lower-cases æøå and drops punctuation");
  CHECK(normalise("  BLÅ   elefant ") == "blå elefant", "normalise collapses spaces");
  CHECK(normalise("ÆØÅ") == "æøå", "normalise maps ÆØÅ");
  CHECK(normalise("x") == "<word must be 3-48 bytes>", "too short word refused");
  CHECK(normalise("one two three four five") == "<word must be 1-4 words>", "five words refused");
  CHECK(normalise("!!!") == "<word must be 1-4 words>", "no letters refused");

  int mom = addMember("Mom", "Blå Elefant");
  int ola = addMember("Ola", "kanelbolle");
  int n = 0, id = 0;

  std::string r = match("Hei, det er meg. Blå elefant, du vet.", &n, &id);
  CHECK(n == 1 && id == mom, "two-word phrase found");
  CHECK(r == "Hei, det er meg. [family word], du vet.", "phrase redacted, punctuation kept");

  r = match("BLÅ-ELEFANT!", &n, &id);
  CHECK(n == 1 && r == "[family word]!", "match across a hyphen, case-insensitive");

  r = match("Jeg spiste en kanelbolle i dag og blå elefant", &n);
  CHECK(n == 2, "two members in one segment");
  CHECK(r == "Jeg spiste en [family word] i dag og [family word]", "both redacted");

  r = match("kanelboller er gode", &n);
  CHECK(n == 0 && r == "kanelboller er gode", "a longer word does not match");

  r = match("blå hval og elefant", &n);
  CHECK(n == 0, "words must be adjacent");

  r = match("kanelbolle kanelbolle", &n, &id);
  CHECK(n == 1 && id == ola && r == "[family word] [family word]", "repeat counted once, both redacted");

  r = match("", &n);
  CHECK(n == 0 && r.empty(), "empty text");

  // The word itself must never survive in the output.
  r = match("ok blå elefant blå elefant ok", &n);
  CHECK(r.find("elefant") == std::string::npos, "no plain word left");

  printf(failures ? "%d FAILED\n" : "OK\n", failures);
  return failures ? 1 : 0;
}
