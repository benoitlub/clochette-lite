const LEARNED_LINES_KEY = "clochette-lite-learned-lines";
const REJECTED_LINES_KEY = "clochette-lite-rejected-lines";

const thumbUpBtn = document.getElementById("thumbUpBtn");
const thumbDownBtn = document.getElementById("thumbDownBtn");
const feedbackRow = document.querySelector(".feedback-row");

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
  if (["fatigue", "money", "drift", "win", "idle", "start", "manual"].includes(latest)) return latest;
  return "manual";
}

function flashFeedback(message) {
  if (typeof setBubble === "function") setBubble(message, "feedback");
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

  const rejected = readJsonStore(REJECTED_LINES_KEY, []);
  if (!rejected.includes(text)) {
    writeJsonStore(REJECTED_LINES_KEY, [text, ...rejected].slice(0, 120));
    flashFeedback("Ton refus est noté. Je vais bouder professionnellement.");
  } else {
    flashFeedback("Oui, oui. Celle-là est déjà au placard.");
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
