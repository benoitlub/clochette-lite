const LEARNED_LINES_KEY = "clochette-lite-learned-lines";
const REJECTED_LINES_KEY = "clochette-lite-rejected-lines";
const CLOCHETTE_TENSION_KEY = "clochette-lite-feedback-tension";

const thumbUpBtn = document.getElementById("thumbUpBtn");
const thumbDownBtn = document.getElementById("thumbDownBtn");
const feedbackRow = document.querySelector(".feedback-row");

const insistEvents = ["money", "drift", "fatigue"];

function readJsonStore(key, fallback) {
  try {
    return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback));
  } catch {
    return fallback;
  }
}

function writeJsonStore(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

function currentBubbleText() {
  return document.getElementById("bubble")?.textContent?.trim() || "";
}

function currentFeedbackEvent() {
  const latest = window.clochetteLastEvent || "manual";
  if (["fatigue", "money", "drift", "win", "idle", "start", "manual", "gemma", "gemma-test", "voice-reply"].includes(latest)) return latest;
  return "manual";
}

function flashFeedback(message) {
  if (typeof setBubble === "function") setBubble(message, "feedback");
}

function getStubbornness(event, text) {
  let score = 0.28;
  if (insistEvents.includes(event)) score += 0.28;
  if (/argent|client|facture|projet principal|fatigu|évite|recommences/i.test(text)) score += 0.2;
  if (/je mérite|j'avais préparé|j'adore|public/i.test(text)) score -= 0.12;
  return Math.max(0.05, Math.min(0.92, score));
}

function adoptLine() {
  const text = currentBubbleText();
  if (!text) return;

  const event = currentFeedbackEvent();
  const learned = readJsonStore(LEARNED_LINES_KEY, {});
  learned[event] = learned[event] || [];

  if (!learned[event].includes(text)) {
    learned[event] = [text, ...learned[event]].slice(0, 80);
    writeJsonStore(LEARNED_LINES_KEY, learned);
    flashFeedback("Réplique adoptée. Je vais prétendre que c'était mon idée depuis le début.");
  } else {
    flashFeedback("Déjà homologuée. Je suis constante. C'est rare.");
  }
}

function rejectLine() {
  const text = currentBubbleText();
  if (!text) return;

  const event = currentFeedbackEvent();
  const rejected = readJsonStore(REJECTED_LINES_KEY, []);
  const tension = readJsonStore(CLOCHETTE_TENSION_KEY, {});
  const stubbornness = getStubbornness(event, text);
  const shouldInsist = Math.random() < stubbornness;

  tension[text] = {
    event,
    count: (tension[text]?.count || 0) + 1,
    lastRejectedAt: new Date().toISOString(),
    stubbornness,
    decision: shouldInsist ? "insist" : "revise"
  };
  writeJsonStore(CLOCHETTE_TENSION_KEY, tension);

  if (!shouldInsist && !rejected.includes(text)) {
    writeJsonStore(REJECTED_LINES_KEY, [text, ...rejected].slice(0, 120));
  }

  if (shouldInsist) {
    flashFeedback("Refus noté. Je n'abandonne pas encore cette hypothèse. C'est pénible, donc possiblement utile.");
  } else if (rejected.includes(text)) {
    flashFeedback("Oui, oui. Celle-là est déjà au placard. Je révise mon numéro.");
  } else {
    flashFeedback("D'accord. Ton refus devient une donnée, pas un ordre. Je corrige la trajectoire.");
  }
}

function revealFeedback() {
  if (feedbackRow) feedbackRow.classList.remove("hidden");
}

thumbUpBtn?.addEventListener("click", adoptLine);
thumbDownBtn?.addEventListener("click", rejectLine);

const originalSetBubbleForFeedback = window.setBubble;
if (typeof originalSetBubbleForFeedback === "function") {
  window.setBubble = function patchedSetBubble(text, event = "manual") {
    window.clochetteLastEvent = event;
    originalSetBubbleForFeedback(text, event);
    revealFeedback();
  };
}

revealFeedback();
