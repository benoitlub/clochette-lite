const LEARNED_LINES_KEY = "clochette-lite-learned-lines";
const REJECTED_LINES_KEY = "clochette-lite-rejected-lines";
const STYLE_NOTES_KEY = "clochette-lite-style-notes";
const CLOCHETTE_TENSION_KEY = "clochette-lite-feedback-tension";

const adoptBtn = document.getElementById("adoptBtn") || document.getElementById("thumbUpBtn");
const rejectBtn = document.getElementById("rejectBtn") || document.getElementById("thumbDownBtn");
const moreClochetteBtn = document.getElementById("moreClochetteBtn");
const tooCoachBtn = document.getElementById("tooCoachBtn");
const feedbackRow = document.querySelector(".feedback-row");

const insistEvents = ["money", "drift", "fatigue"];

function readJsonStore(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback)); }
  catch { return fallback; }
}

function writeJsonStore(key, value) { localStorage.setItem(key, JSON.stringify(value)); }
function currentBubbleText() { return document.getElementById("bubble")?.textContent?.trim() || ""; }

function currentFeedbackEvent() {
  const latest = window.clochetteLastEvent || "manual";
  if (["fatigue", "money", "drift", "win", "idle", "start", "manual", "gemma", "gemma-test", "voice-reply", "voice-pending-reply"].includes(latest)) return latest;
  return "manual";
}

function flashFeedback(message) {
  if (typeof setBubble === "function") setBubble(message, "feedback");
}

function sendFeedbackToPhraseBank(text, feedback, event) {
  if (window.clochettePhraseBank?.feedback) window.clochettePhraseBank.feedback(text, feedback, event);
}

function rememberStyle(note, text, event) {
  const notes = readJsonStore(STYLE_NOTES_KEY, []);
  writeJsonStore(STYLE_NOTES_KEY, [{ note, text, event, at: new Date().toISOString() }, ...notes].slice(0, 120));
}

function adoptLine() {
  const text = currentBubbleText();
  if (!text) return;
  const event = currentFeedbackEvent();
  const learned = readJsonStore(LEARNED_LINES_KEY, {});
  learned[event] = learned[event] || [];
  sendFeedbackToPhraseBank(text, "up", event);
  if (!learned[event].includes(text)) learned[event] = [text, ...learned[event]].slice(0, 80);
  writeJsonStore(LEARNED_LINES_KEY, learned);
  flashFeedback("A validé. Réplique homologuée. Je la range dans la vitrine, pas dans le tiroir honteux.");
}

function rejectLine() {
  const text = currentBubbleText();
  if (!text) return;
  const event = currentFeedbackEvent();
  const rejected = readJsonStore(REJECTED_LINES_KEY, []);
  sendFeedbackToPhraseBank(text, "down", event);
  if (!rejected.includes(text)) writeJsonStore(REJECTED_LINES_KEY, [text, ...rejected].slice(0, 120));
  flashFeedback("B rejeté. Celle-là part au compost verbal. Elle n'avait pas les épaules.");
}

function moreClochette() {
  const text = currentBubbleText();
  if (!text) return;
  const event = currentFeedbackEvent();
  rememberStyle("plus_clochette", text, event);
  sendFeedbackToPhraseBank(text, "more_clochette", event);
  flashFeedback("C demandé. Même idée, moins notice technique. Plus d'ailes, plus de dents.");
}

function tooCoach() {
  const text = currentBubbleText();
  if (!text) return;
  const event = currentFeedbackEvent();
  rememberStyle("too_coach", text, event);
  sendFeedbackToPhraseBank(text, "too_coach", event);
  flashFeedback("D noté. Cette phrase portait un blazer LinkedIn. Je confisque le blazer.");
}

function revealFeedback() {
  if (feedbackRow) feedbackRow.classList.remove("hidden");
}

adoptBtn?.addEventListener("click", adoptLine);
rejectBtn?.addEventListener("click", rejectLine);
moreClochetteBtn?.addEventListener("click", moreClochette);
tooCoachBtn?.addEventListener("click", tooCoach);

const originalSetBubbleForFeedback = window.setBubble;
if (typeof originalSetBubbleForFeedback === "function") {
  window.setBubble = function patchedSetBubble(text, event = "manual") {
    window.clochetteLastEvent = event;
    originalSetBubbleForFeedback(text, event);
    revealFeedback();
  };
}

revealFeedback();
