const listenBtn = document.getElementById("listenBtn");
const listenHint = document.getElementById("listenHint");

const SpeechRecognitionApi = window.SpeechRecognition || window.webkitSpeechRecognition;
let recognizer = null;
let isListening = false;

const voiceReplyHistoryKey = "clochette-lite-voice-reply-history";
const extendedEngineKey = "clochette-lite-gemma-endpoint";

const voiceReplyPools = {
  project: [
    "J'ai entendu projet. Je note. Je me méfie déjà.",
    "Projet détecté. Très bien. Maintenant, lequel essaie de voler la scène ?",
    "Le mot projet vient d'apparaître. Les anciens projets demandent un avocat.",
    "Projet, donc danger de bifurcation brillante. Je sors mon carnet.",
    "Très bien. Projet entendu. Je vérifie s'il sert l'objectif ou son ego."
  ],
  fatigue: [
    "Hypothèse : fatigue réelle. Je baisse le niveau de sarcasme. Un peu.",
    "Fatigue détectée. Je range une partie des piques, pas toutes.",
    "Le mammifère créatif semble cuit. Je recommande la prudence, mot horrible.",
    "Je t'entends. Fatigue possible. Confiance : assez haute pour être moins pénible.",
    "D'accord. On ne négocie pas avec un cerveau vide. Enfin, pas longtemps."
  ],
  money: [
    "Ah. Le sujet argent vient d'entrer dans la pièce. Enfin.",
    "Argent entendu. Le portefeuille fait un petit bruit de meuble ancien.",
    "Client, prospection, argent. Voilà des mots moches mais nourrissants.",
    "Je note une intrusion du réel économique. Très impoli. Utile, pourtant.",
    "La partie argent réclame une chaise à la table. Je lui donne une petite chaise."
  ],
  body: [
    "Décision corporelle détectée. Rare. Je soutiens.",
    "Pause ou douche ? Le corps vient de gagner un procès discret.",
    "Marcher, boire, respirer. Les bases humiliantes fonctionnent encore.",
    "Je valide cette décision de mammifère. Ce n'est pas une faiblesse, c'est une batterie.",
    "Le corps demande la parole. Pour une fois, écoutons ce vieux matériel."
  ],
  clochette: [
    "Oui. Présente. Beaucoup trop présente, selon certains comités.",
    "On m'appelle ? J'espère que ce n'était pas pour une diversion.",
    "Clochette présente. J'ai déjà une opinion. C'est mon sport.",
    "Oui. Je suis là. Je fais semblant d'attendre poliment.",
    "Présente. Minuscule, intrusive, remarquablement difficile à congédier."
  ],
  default: [
    "J'ai entendu. Enfin, je crois. Les humains articulent avec une audace discutable.",
    "Message reçu. Je classe ça dans : probablement important, vaguement humain.",
    "Je t'entends. Je vais faire une hypothèse prudente, activité rarissime.",
    "Noté. Je manque de contexte, donc je vais éviter de jouer à l'oracle.",
    "Je crois avoir compris. Ce qui, historiquement, n'est pas toujours une garantie.",
    "D'accord. Je garde ça dans le carnet mental. Le vrai carnet veut déjà savoir pourquoi.",
    "Entendu. Je vais répondre court, sinon je deviens une réunion."
  ]
};

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

function readVoiceHistory() {
  try { return JSON.parse(localStorage.getItem(voiceReplyHistoryKey) || "[]"); }
  catch { return []; }
}

function saveVoiceHistory(history) {
  localStorage.setItem(voiceReplyHistoryKey, JSON.stringify(history.slice(0, 18)));
}

function normalizeVoiceLine(text) {
  return String(text || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]+/g, " ").trim();
}

function chooseVoiceReply(kind) {
  const pool = voiceReplyPools[kind] || voiceReplyPools.default;
  const history = readVoiceHistory();
  const fresh = pool.filter((line) => !history.includes(normalizeVoiceLine(line)));
  const selected = fresh.length ? fresh[Math.floor(Math.random() * fresh.length)] : pool[Math.floor(Math.random() * pool.length)];
  saveVoiceHistory([normalizeVoiceLine(selected), ...history.filter((item) => item !== normalizeVoiceLine(selected))]);
  return selected;
}

function detectVoiceKind(lower) {
  if (lower.includes("projet") || lower.includes("travail") || lower.includes("bosser")) return "project";
  if (lower.includes("fatigue") || lower.includes("fatigué") || lower.includes("fatiguée") || lower.includes("épuisé")) return "fatigue";
  if (lower.includes("argent") || lower.includes("client") || lower.includes("prospection") || lower.includes("facture")) return "money";
  if (lower.includes("pause") || lower.includes("douche") || lower.includes("marcher") || lower.includes("manger") || lower.includes("boire")) return "body";
  if (lower.includes("clochette")) return "clochette";
  return "default";
}

function readAppStateForVoice() {
  try { return JSON.parse(localStorage.getItem("clochette-lite-state-v2") || "{}"); }
  catch { return {}; }
}

function buildVoicePrompt({ transcript, kind }) {
  const appState = readAppStateForVoice();
  const recent = readVoiceHistory().slice(0, 6).join(" | ");
  return `Tu es Clochette, présence expérimentale du Feuch Institut.
Tu n'es pas une assistante. Tu n'es pas une coach. Tu es piquante, maternelle, drôle, un peu starlette.
Tu réponds à Benoît après qu'il t'a parlé au micro.

Ce que Benoît vient de dire : "${transcript}"
Catégorie détectée : ${kind}
Projet actuel : ${appState.project || "inconnu"}
Objectif actuel : ${appState.goal || "inconnu"}
Énergie déclarée : ${appState.energy || "inconnue"}
Répliques récentes normalisées à ne pas répéter : ${recent || "aucune"}

Règles absolues :
- réponds en français ;
- maximum 24 mots ;
- pas d'emoji ;
- pas de diagnostic ;
- pas de morale ;
- pas de conseil banal ;
- pas de phrase de chatbot ;
- une seule réplique ;
- si tu déduis, dis "hypothèse" ou "je suppose" ;
- garde le style Clochette : vif, personnel, drôle, un peu insolent, jamais méchant.
`;
}

function cleanEngineReply(text) {
  return String(text || "")
    .replace(/^Clochette\s*:\s*/i, "")
    .replace(/["“”]+/g, "")
    .trim()
    .slice(0, 220);
}

async function askExtendedEngine(transcript, kind) {
  const endpoint = localStorage.getItem(extendedEngineKey);
  if (!endpoint) return null;

  try {
    updateListenUi("Clochette réfléchit plus fort. Elle va faire semblant que c'était facile.");
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        prompt: buildVoicePrompt({ transcript, kind }),
        context: { event: "voice_reply", transcript, kind }
      })
    });
    if (!response.ok) throw new Error("extended engine unavailable");
    const data = await response.json();
    const reply = cleanEngineReply(data.text || data.response || data.message || "");
    if (!reply || readVoiceHistory().includes(normalizeVoiceLine(reply))) return null;
    saveVoiceHistory([normalizeVoiceLine(reply), ...readVoiceHistory()]);
    return reply;
  } catch (error) {
    console.warn("Extended voice fallback:", error);
    return null;
  } finally {
    updateListenUi();
  }
}

async function respondToTranscript(transcript) {
  const text = String(transcript || "").trim();
  if (!text) return;

  if (typeof addLog === "function") addLog(`Benoît : ${text}`, "voice-input");

  const lower = text.toLowerCase();
  const kind = detectVoiceKind(lower);
  const extendedReply = await askExtendedEngine(text, kind);
  const reply = extendedReply || chooseVoiceReply(kind);

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
