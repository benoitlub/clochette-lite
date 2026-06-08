const PHRASE_BANK_KEY = "clochette-lite-phrase-bank-v1";

const phraseBankStatusByEvent = {
  feedback: "meta",
  "gemma-test": "candidate",
  gemma: "candidate",
  "voice-reply": "candidate",
  "voice-pending-reply": "candidate",
  manual: "candidate",
  start: "candidate",
  drift: "candidate",
  fatigue: "candidate",
  money: "candidate",
  idle: "candidate",
  win: "approved",
  consent: "context"
};

const phraseBankCategoryByEvent = {
  start: "presence",
  manual: "presence",
  idle: "absence",
  drift: "dispersion",
  fatigue: "fatigue",
  money: "argent",
  win: "victoire",
  consent: "consentement",
  feedback: "meta",
  "voice-reply": "conversation",
  "voice-pending-reply": "conversation",
  gemma: "moteur",
  "gemma-test": "moteur"
};

function bankNormalize(text) {
  return String(text || "")
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function readPhraseBank() {
  try {
    const bank = JSON.parse(localStorage.getItem(PHRASE_BANK_KEY) || "[]");
    return Array.isArray(bank) ? bank : [];
  } catch {
    return [];
  }
}

function writePhraseBank(bank) {
  localStorage.setItem(PHRASE_BANK_KEY, JSON.stringify(bank.slice(0, 900)));
}

function inferTags(text, event) {
  const normalized = bankNormalize(text);
  const tags = new Set([phraseBankCategoryByEvent[event] || event || "general"]);

  if (/argent|client|prospection|facture|portefeuille|loyer|frigo/.test(normalized)) tags.add("argent");
  if (/fatigue|fatigue|pause|cerveau|mammifere|carburant|cuit/.test(normalized)) tags.add("fatigue");
  if (/projet|chantier|placard|bifurcation|cailloux|detour/.test(normalized)) tags.add("dispersion");
  if (/victoire|avance|fier|fabrique|termine|resultat/.test(normalized)) tags.add("victoire");
  if (/hypothese|je suppose|confiance|probabilite/.test(normalized)) tags.add("hypothese");
  if (/consentement|acces|observe|fouiller/.test(normalized)) tags.add("consentement");
  if (/pirouette|suspens|reviendrai|diversion/.test(normalized)) tags.add("pirouette");
  if (/clochette|fee|ailes|starlette/.test(normalized)) tags.add("personnage");

  return [...tags].filter(Boolean);
}

function scoreForEvent(event) {
  if (event === "win") return 2;
  if (event === "voice-reply" || event === "voice-pending-reply") return 1;
  if (event === "feedback") return -1;
  return 0;
}

function storePhraseLine(text, event = "manual", extra = {}) {
  const cleanText = String(text || "").trim();
  const normalized = bankNormalize(cleanText);
  if (!normalized || normalized.length < 3) return null;

  const bank = readPhraseBank();
  const now = new Date().toISOString();
  const existing = bank.find((item) => item.normalized === normalized);

  if (existing) {
    existing.lastSeenAt = now;
    existing.uses = (existing.uses || 0) + 1;
    existing.events = [...new Set([...(existing.events || []), event])];
    existing.tags = [...new Set([...(existing.tags || []), ...inferTags(cleanText, event)])];
    existing.score = (existing.score || 0) + scoreForEvent(event);
    if (extra.source) existing.sources = [...new Set([...(existing.sources || []), extra.source])];
    writePhraseBank(bank);
    return existing;
  }

  const item = {
    id: `pb_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    text: cleanText,
    normalized,
    status: phraseBankStatusByEvent[event] || "candidate",
    category: phraseBankCategoryByEvent[event] || "general",
    tags: inferTags(cleanText, event),
    events: [event],
    sources: [extra.source || inferSource(event)],
    score: scoreForEvent(event),
    uses: 1,
    createdAt: now,
    lastSeenAt: now,
    lastFeedbackAt: null,
    notes: []
  };

  writePhraseBank([item, ...bank]);
  return item;
}

function inferSource(event) {
  if (String(event).startsWith("voice")) return "voice";
  if (String(event).startsWith("gemma")) return "extended";
  if (event === "feedback") return "feedback";
  return "local";
}

function updatePhraseFeedback(text, feedback, event = "manual") {
  const cleanText = String(text || "").trim();
  const normalized = bankNormalize(cleanText);
  if (!normalized) return null;

  const bank = readPhraseBank();
  let item = bank.find((entry) => entry.normalized === normalized);

  if (!item) {
    item = storePhraseLine(cleanText, event, { source: "feedback" });
    return updatePhraseFeedback(cleanText, feedback, event);
  }

  const now = new Date().toISOString();
  item.lastFeedbackAt = now;
  item.feedback = item.feedback || { up: 0, down: 0, tension: 0 };

  if (feedback === "up") {
    item.feedback.up += 1;
    item.status = "approved";
    item.score = (item.score || 0) + 5;
    item.tags = [...new Set([...(item.tags || []), "homologuee"])]
  } else if (feedback === "down") {
    item.feedback.down += 1;
    item.feedback.tension += 1;
    item.status = item.score > 2 ? "tension" : "rejected";
    item.score = (item.score || 0) - 3;
    item.tags = [...new Set([...(item.tags || []), "tension"])]
  } else if (feedback === "tension") {
    item.feedback.tension += 1;
    item.status = "tension";
    item.score = (item.score || 0) - 1;
    item.tags = [...new Set([...(item.tags || []), "tension"])]
  }

  item.events = [...new Set([...(item.events || []), event])];
  writePhraseBank(bank);
  return item;
}

function getPhraseBankCandidates(event = "manual", limit = 12) {
  const category = phraseBankCategoryByEvent[event] || "general";
  return readPhraseBank()
    .filter((item) => item.text && item.status !== "rejected" && item.status !== "meta")
    .filter((item) => item.score >= 0 || item.status === "approved")
    .filter((item) => item.category === category || (item.tags || []).includes(category) || (item.events || []).includes(event))
    .sort((a, b) => {
      const scoreDelta = (b.score || 0) - (a.score || 0);
      if (scoreDelta) return scoreDelta;
      return String(b.lastSeenAt || "").localeCompare(String(a.lastSeenAt || ""));
    })
    .slice(0, limit)
    .map((item) => item.text);
}

function exportPhraseBank() {
  return {
    version: 1,
    exportedAt: new Date().toISOString(),
    phrases: readPhraseBank()
  };
}

const originalSetBubbleForPhraseBank = window.setBubble;
if (typeof originalSetBubbleForPhraseBank === "function") {
  window.setBubble = function phraseBankSetBubble(text, event = "manual") {
    storePhraseLine(text, event);
    originalSetBubbleForPhraseBank(text, event);
  };
}

window.clochettePhraseBank = {
  read: readPhraseBank,
  store: storePhraseLine,
  feedback: updatePhraseFeedback,
  candidates: getPhraseBankCandidates,
  export: exportPhraseBank
};

window.clochetteGetBankCandidates = getPhraseBankCandidates;
