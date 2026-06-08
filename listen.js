const listenBtn = document.getElementById("listenBtn");
const listenHint = document.getElementById("listenHint");

const SpeechRecognitionApi = window.SpeechRecognition || window.webkitSpeechRecognition;
let recognizer = null;
let isListening = false;

function canListen() {
  return Boolean(SpeechRecognitionApi);
}

function updateListenUi(message) {
  if (!listenBtn || !listenHint) return;

  if (!canListen()) {
    listenBtn.textContent = "Micro indisponible";
    listenBtn.disabled = true;
    listenHint.textContent = "Reconnaissance vocale non disponible ici. Chrome Android devrait mieux coopérer.";
    return;
  }

  listenBtn.textContent = isListening ? "J'écoute..." : "Écouter";
  listenHint.textContent = message || "Micro sur demande uniquement. Pas d'écoute permanente façon majordome inquiet.";
}

function respondToTranscript(transcript) {
  const text = String(transcript || "").trim();
  if (!text) return;

  if (typeof addLog === "function") addLog(`Benoît : ${text}`, "voice-input");

  const lower = text.toLowerCase();
  let reply = "J'ai entendu. Enfin, je crois. Les humains articulent avec une audace discutable.";

  if (lower.includes("projet") || lower.includes("travail")) {
    reply = "J'ai entendu projet. Je note. Je me méfie déjà.";
  } else if (lower.includes("fatigue") || lower.includes("fatigué") || lower.includes("fatiguée")) {
    reply = "Hypothèse : fatigue réelle. Je baisse le niveau de sarcasme. Un peu.";
  } else if (lower.includes("argent") || lower.includes("client") || lower.includes("prospection")) {
    reply = "Ah. Le sujet argent vient d'entrer dans la pièce. Enfin.";
  } else if (lower.includes("pause") || lower.includes("douche") || lower.includes("marcher")) {
    reply = "Décision corporelle détectée. Rare. Je soutiens.";
  } else if (lower.includes("clochette")) {
    reply = "Oui. Présente. Beaucoup trop présente, selon certains comités.";
  }

  if (typeof setBubble === "function") setBubble(reply, "voice-reply");
}

function startListening() {
  if (!canListen() || isListening) return;

  recognizer = new SpeechRecognitionApi();
  recognizer.lang = "fr-FR";
  recognizer.continuous = false;
  recognizer.interimResults = false;
  recognizer.maxAlternatives = 1;

  recognizer.onstart = () => {
    isListening = true;
    updateListenUi("Clochette écoute maintenant. Sur demande. Comme promis.");
  };

  recognizer.onresult = (event) => {
    const transcript = event.results?.[0]?.[0]?.transcript || "";
    respondToTranscript(transcript);
  };

  recognizer.onerror = () => {
    updateListenUi("Micro capricieux. Le Feuch Institut accuse probablement Chrome.");
  };

  recognizer.onend = () => {
    isListening = false;
    updateListenUi();
  };

  recognizer.start();
}

listenBtn?.addEventListener("click", startListening);
updateListenUi();
