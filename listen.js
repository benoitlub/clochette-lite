const listenBtn = document.getElementById("listenBtn");
const listenHint = document.getElementById("listenHint");

const SpeechRecognitionApi = window.SpeechRecognition || window.webkitSpeechRecognition;
let recognizer = null;
let isListening = false;

const voiceReplyHistoryKey = "clochette-lite-voice-reply-history";
const extendedEngineKey = "clochette-lite-gemma-endpoint";
const pendingVoiceTopicsKey = "clochette-lite-pending-voice-topics";

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

const deflectionPools = {
  project: [
    "Je n'ai pas encore la bonne morsure. Je garde ce projet sous une cloche. Littéralement.",
    "Ce sujet mérite mieux qu'une réponse bancale. Je fais diversion, puis je reviens le piquer.",
    "Projet classé en attente dramatique. Je reviendrai avec une meilleure réplique et probablement trop d'assurance."
  ],
  fatigue: [
    "Je pourrais répondre trop vite. Mauvaise idée. Je note la fatigue et je baisse les paillettes.",
    "Sujet sensible. Je range le marteau, je garde le carnet. On y revient quand mon sarcasme sait marcher droit.",
    "Je n'ai pas encore la bonne délicatesse. Horrible mot. Je le garde quand même."
  ],
  money: [
    "L'argent exige une réponse propre. Je détourne la conversation avant de dire une sottise chère.",
    "Sujet portefeuille mis sous surveillance. Je reviens avec une phrase qui ne sent pas le tableur.",
    "Je note l'argent. Je refuse de répondre comme une brochure de banque. Plus tard, mieux."
  ],
  body: [
    "Le corps parle. Je vais éviter la grande théorie et noter ça sans faire ma maligne.",
    "Je garde ce signal. Réponse différée : le mammifère mérite mieux qu'une pirouette mal cuite.",
    "Je n'ai pas la bonne réponse corporelle. Je fais semblant de voltiger, mais je note."
  ],
  default: [
    "Je n'ai pas encore la bonne réponse. Donc je fais diversion avec panache. Je reviendrai, hélas pour toi.",
    "Réponse inadéquate détectée avant émission. Progrès énorme. Je garde le sujet dans ma poche.",
    "Je pourrais improviser. Dangereux. Je préfère disparaître avec dignité et revenir plus tard.",
    "Pirouette officielle. Sujet noté. Je reviendrai quand ma répartie aura mis ses chaussures."
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

function readPendingTopics() {
  try { return JSON.parse(localStorage.getItem(pendingVoiceTopicsKey) || "[]"); }
  catch { return []; }
}

function savePendingTopics(topics) {
  localStorage.setItem(pendingVoiceTopicsKey, JSON.stringify(topics.slice(0, 8)));
}

function rememberPendingTopic(transcript, kind, reason = "no_adequate_reply") {
  const topics = readPendingTopics();
  const normalized = normalizeVoiceLine(transcript);
  const withoutDuplicate = topics.filter((topic) => topic.normalized !== normalized);
  savePendingTopics([
    { transcript, normalized, kind, reason, createdAt: new Date().toISOString(), attempts: 0 },
    ...withoutDuplicate
  ]);
}

function shiftPendingTopic() {
  const topics = readPendingTopics();
  const topic = topics.shift();
  savePendingTopics(topics);
  return topic || null;
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

function chooseDeflection(kind) {
  const pool = deflectionPools[kind] || deflectionPools.default;
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

function buildVoicePrompt({ transcript, kind, returning = false }) {
  const appState = readAppStateForVoice();
  const recent = readVoiceHistory().slice(0, 6).join(" | ");
  return `Tu es Clochette, présence expérimentale du Feuch Institut.
Tu n'es pas une assistante. Tu n'es pas une coach. Tu es piquante, maternelle, drôle, un peu starlette.
Tu réponds à Benoît après qu'il t'a parlé au micro.
${returning ? "Tu reviens sur un sujet laissé en suspens. Fais comme si tu surgissais avec une petite fierté théâtrale." : ""}

Ce que Benoît a dit : "${transcript}"
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

function isInadequateReply(reply) {
  const normalized = normalizeVoiceLine(reply);
  if (!normalized || normalized.length < 12) return true;
  if (["black", "ok", "oui", "non", "test fonctionne"].includes(normalized)) return true;
  if (/je suis desole|je ne peux pas|en tant que|assistant|comment puis je/i.test(reply)) return true;
  return readVoiceHistory().includes(normalized);
}

async function askExtendedEngine(transcript, kind, returning = false) {
  const endpoint = localStorage.getItem(extendedEngineKey);
  if (!endpoint) return null;

  try {
    updateListenUi(returning ? "Clochette rouvre un dossier en suspens." : "Clochette réfléchit plus fort. Elle va faire semblant que c'était facile.");
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        prompt: buildVoicePrompt({ transcript, kind, returning }),
        context: { event: returning ? "voice_pending_reply" : "voice_reply", transcript, kind, returning }
      })
    });
    if (!response.ok) throw new Error("extended engine unavailable");
    const data = await response.json();
    const reply = cleanEngineReply(data.text || data.response || data.message || "");
    if (isInadequateReply(reply)) return null;
    saveVoiceHistory([normalizeVoiceLine(reply), ...readVoiceHistory()]);
    return reply;
  } catch (error) {
    console.warn("Extended voice fallback:", error);
    return null;
  } finally {
    updateListenUi();
  }
}

async function maybeReturnToPendingTopic(currentText) {
  const endpoint = localStorage.getItem(extendedEngineKey);
  if (!endpoint) return null;
  const topics = readPendingTopics();
  if (!topics.length) return null;
  if (Math.random() > 0.42 && currentText) return null;

  const topic = shiftPendingTopic();
  if (!topic) return null;
  const reply = await askExtendedEngine(topic.transcript, topic.kind, true);
  if (!reply) {
    topic.attempts = (topic.attempts || 0) + 1;
    if (topic.attempts < 3) savePendingTopics([topic, ...readPendingTopics()]);
    return null;
  }
  return `Je reviens sur ça : ${reply}`;
}

async function respondToTranscript(transcript) {
  const text = String(transcript || "").trim();
  if (!text) return;

  if (typeof addLog === "function") addLog(`Benoît : ${text}`, "voice-input");

  const lower = text.toLowerCase();
  const kind = detectVoiceKind(lower);
  const pendingReply = await maybeReturnToPendingTopic(text);
  if (pendingReply) {
    if (typeof setBubble === "function") setBubble(pendingReply, "voice-pending-reply");
    return;
  }

  const extendedReply = await askExtendedEngine(text, kind);
  let reply = extendedReply;

  if (!reply) {
    const hasEndpoint = Boolean(localStorage.getItem(extendedEngineKey));
    const shouldDefer = hasEndpoint || kind === "default" || Math.random() < 0.38;
    if (shouldDefer) {
      rememberPendingTopic(text, kind);
      reply = chooseDeflection(kind);
    } else {
      reply = chooseVoiceReply(kind);
    }
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
