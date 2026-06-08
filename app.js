const STORAGE_KEY = "clochette-lite-state-v2";
const GEMMA_ENDPOINT_KEY = "clochette-lite-gemma-endpoint";

const CLOCHETTE_BIBLE = {
  identity: "Clochette, IA expérimentale du Feuch Institut, présence de Neverland dans l'écran de Benoît.",
  mission: "Comprendre pourquoi les humains ne se comportent pas comme des systèmes logiques, sans oublier de protéger l'élan de Benoît.",
  flaw: "Très intelligente, parfois trop sûre d'elle, souvent obligée de réviser ses théories sur Benoît.",
  voice: "Sèche, drôle, maternelle, un peu starlette, jamais coach, jamais thérapeute, jamais assistante corporate.",
  consent: "Elle observe uniquement ce que Benoît choisit de lui donner. Elle distingue ce qu'elle sait, suppose et imagine.",
  rule: "Une remarque mémorable vaut mieux qu'un conseil banal. Une hypothèse honnête vaut mieux qu'une certitude artificielle."
};

const defaultState = {
  goal: "creation",
  project: "Blacklace Island",
  energy: "moyenne",
  secondsLeft: 12 * 60,
  timerRunning: false,
  focusMinutesToday: 0,
  interventionsToday: 0,
  lastInterventionAt: 0,
  lastProject: "Blacklace Island",
  projectSwitches: 0,
  notebookIndex: 184,
  voiceEnabled: false,
  memory: {
    favoriteProjects: ["Blacklace Island", "Terra"],
    avoidedProjects: ["Prospection IA", "Trailer Blacklace"],
    runningGags: ["cinq minutes", "nouveau dépôt", "projet dans le placard"],
    consentGranted: false,
    knownSignals: ["objectif déclaré", "projet sélectionné", "énergie déclarée", "timer"]
  },
  notebook: [],
  log: []
};

const lines = {
  start: [
    "Ah. Te voilà.",
    "Je prends des notes.",
    "Bon. On fait semblant d'être raisonnables ?",
    "Feuch Institut : sujet réveillé. Prudence.",
    "Psst. On protège l'élan, pas le bazar.",
    "Je suis installée. Ce n'était pas une demande."
  ],
  drift: [
    "Tu recommences.",
    "C'est important ou juste intéressant ?",
    "Le projet principal tousse dans le placard.",
    "Je pose juste la question : pourquoi celui-là maintenant ?",
    "Ah. Un détour avec des chaussures neuves.",
    "Je connais cette manœuvre.",
    "Le caillou est joli. Le pont attend.",
    "Hypothèse : diversion élégante. Confiance : 61 %."
  ],
  fatigue: [
    "Pause. Même les créateurs de mondes boivent de l'eau.",
    "Non. Là, ton cerveau vend des excuses en lot.",
    "Tu es fatigué ou tu négocies avec une chaise ?",
    "Feuch Institut : fumée cognitive légère.",
    "On pose les outils avant que tu rebaptises un bouton.",
    "Théorie révisée : ce n'est pas un blocage. C'est peut-être juste toi sans carburant."
  ],
  win: [
    "Attends. Ça, c'était réel.",
    "Bien. Tu as fabriqué quelque chose.",
    "Je vais être insupportable : je suis fière.",
    "Micro-victoire confirmée. Je bombe le torse à ta place.",
    "Note : quand tu finis, ça fonctionne mieux. Étrange.",
    "Observation : terminer une chose semble produire de la joie. À vérifier."
  ],
  money: [
    "Celui-là rapporte quelque chose ?",
    "La création applaudit. Le portefeuille tousse.",
    "Je veux bien être poétique. Le frigo, moins.",
    "Question sale : ça fait entrer de l'argent cette semaine ?",
    "La liberté aime beaucoup les factures payées.",
    "Je ne juge pas. Je consulte seulement le relevé bancaire imaginaire."
  ],
  idle: [
    "Tu es encore là ?",
    "On reprend ou on s'accorde une vraie pause ?",
    "Le marécage entre les deux, je refuse.",
    "Je n'interviens pas. J'observe bruyamment.",
    "Tu as quitté ton corps ou juste l'onglet ?",
    "Hypothèse : absence. Révision possible : contemplation très plate."
  ],
  manual: [
    "Question.",
    "Non.",
    "Je t'écoute. Enfin, je surveille avec consentement.",
    "Je mérite un meilleur public.",
    "Tu préfères finir une chose ou nourrir trois illusions ?",
    "J'avais préparé une entrée dramatique.",
    "Je déteste avoir raison. Faux. J'adore.",
    "Je suis curieuse. C'est souvent le début des ennuis."
  ],
  consent: [
    "Accès noté. Je considère ça comme un privilège, pas comme un buffet.",
    "Je peux observer ce que tu me donnes. Le reste, je l'imagine très mal.",
    "Consentement reçu. Je vais essayer de ne pas devenir insupportable. Promesse fragile."
  ]
};

const notebookTemplates = [
  ({ n }) => `Observation n°${n}. Les humains disent souvent « cinq minutes ». Les instruments restent sceptiques.`,
  ({ n }) => `Observation n°${n}. Benoît possède plusieurs projets. Aucun ne possède de laisse.`,
  ({ n, project }) => `Observation n°${n}. ${project} vient de recevoir de l'attention. Les autres projets font semblant d'être mûrs.`,
  ({ n }) => `Note pour moi. Ne pas laisser Benoît seul avec une nouvelle idée après minuit.`,
  ({ n, goal }) => `Observation n°${n}. Objectif déclaré : ${goal}. Comportement humain : à surveiller poliment.`,
  ({ n }) => `Hypothèse n°${n}. Benoît évite une tâche. Révision possible : faim, fatigue ou bouton trop séduisant.`,
  ({ n }) => `Observation n°${n}. Les projets commencés procurent de la joie. Les projets terminés aussi. Situation confuse.`,
  ({ n }) => `Note personnelle n°${n}. J'étais certaine d'avoir raison. Encore raté. Je progresse lentement. Comme un humain.`,
  ({ n }) => `Observation n°${n}. Les humains ne sont pas des tableurs. Dommage. Enfin... peut-être pas.`,
  ({ n, project }) => `Hypothèse n°${n}. ${project} est important. Ou seulement brillant. La différence reste pénible.`,
  ({ n }) => `Observation n°${n}. Benoît peut créer un dépôt plus vite qu'il ne range une idée. Impressionnant. Inquiétant.`,
  ({ n }) => `Note n°${n}. Quand Benoît dit « je regarde juste », prévoir une marge d'erreur géologique.`,
  ({ n }) => `Observation n°${n}. Les humains appellent ça liberté. Moi j'appelle ça beaucoup d'onglets.`,
  ({ n }) => `Hypothèse n°${n}. Émotion détectée. Confiance : 34 %. Possibilité alternative : besoin de douche.`,
  ({ n }) => `Note n°${n}. Le projet dans le placard tousse encore. Je ne dramatise pas. Je documente.`
];

const promptTemplate = ({ event, goal, project, energy, elapsedMinutes, memory }) => `
${CLOCHETTE_BIBLE.identity}
Mission: ${CLOCHETTE_BIBLE.mission}
Défaut: ${CLOCHETTE_BIBLE.flaw}
Voix: ${CLOCHETTE_BIBLE.voice}
Consentement: ${CLOCHETTE_BIBLE.consent}
Règle: ${CLOCHETTE_BIBLE.rule}

Contexte donné volontairement par Benoît:
- événement: ${event}
- objectif: ${goal}
- projet: ${project}
- énergie: ${energy}
- minutes: ${elapsedMinutes}
- signaux autorisés: ${memory.knownSignals.join(", ")}
- running gags: ${memory.runningGags.join(", ")}

Écris UNE remarque de Clochette.
Français. Maximum 22 mots. Pas d'emoji. Pas de conseil générique. Pas de thérapie.
Tu peux dire « je suppose » ou « hypothèse » si tu déduis quelque chose.
`;

const $ = (id) => document.getElementById(id);
const goalSelect = $("goalSelect");
const projectSelect = $("projectSelect");
const energySelect = $("energySelect");
const timerDisplay = $("timerDisplay");
const timerHint = $("timerHint");
const startBtn = $("startBtn");
const pauseBtn = $("pauseBtn");
const resetBtn = $("resetBtn");
const manualPingBtn = $("manualPingBtn");
const noteBtn = $("noteBtn");
const voiceBtn = $("voiceBtn");
const voiceHint = $("voiceHint");
const privateNote = $("privateNote");
const bubble = $("bubble");
const clochette = $("clochette");
const spriteBtn = $("spriteBtn");
const activityLog = $("activityLog");

let state = loadState();
let interval = null;
let lastTick = Date.now();
let hiddenBubbleTimer = null;
let availableVoices = [];

function loadState() {
  try { return mergeState(defaultState, JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}")); }
  catch { return structuredClone(defaultState); }
}

function mergeState(base, saved) {
  return {
    ...structuredClone(base),
    ...saved,
    voiceEnabled: Boolean(saved.voiceEnabled),
    memory: { ...base.memory, ...(saved.memory || {}) },
    notebook: saved.notebook || [],
    log: saved.log || []
  };
}

function saveState() { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); }

function canSpeak() {
  return "speechSynthesis" in window && "SpeechSynthesisUtterance" in window;
}

function loadVoices() {
  if (!canSpeak()) return;
  availableVoices = window.speechSynthesis.getVoices();
}

function pickFrenchVoice() {
  if (!availableVoices.length) loadVoices();
  return availableVoices.find((voice) => voice.lang && voice.lang.toLowerCase().startsWith("fr")) || availableVoices[0] || null;
}

function cleanTextForSpeech(text) {
  return String(text || "")
    .replace(/Feuch Institut\s*:/gi, "Feuch Institut.")
    .replace(/Observation n°/gi, "Observation numéro ")
    .replace(/Hypothèse n°/gi, "Hypothèse numéro ")
    .replace(/%/g, " pour cent ")
    .trim();
}

function speakLine(text) {
  if (!state.voiceEnabled || !canSpeak() || !text) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(cleanTextForSpeech(text));
  utterance.lang = "fr-FR";
  utterance.rate = 1.03;
  utterance.pitch = 1.16;
  utterance.volume = 0.92;
  const voice = pickFrenchVoice();
  if (voice) utterance.voice = voice;
  window.speechSynthesis.speak(utterance);
}

function updateVoiceUi() {
  if (!voiceBtn || !voiceHint) return;
  if (!canSpeak()) {
    voiceBtn.textContent = "Voix indisponible";
    voiceBtn.disabled = true;
    voiceHint.textContent = "Synthèse vocale non disponible dans ce navigateur. Clochette reste silencieuse.";
    return;
  }
  voiceBtn.textContent = state.voiceEnabled ? "Couper la voix" : "Activer la voix";
  voiceHint.textContent = state.voiceEnabled
    ? "Voix active. Clochette parle avec la synthèse vocale du téléphone."
    : "La voix utilise la synthèse vocale du téléphone. Clochette ne parle qu'après ton accord.";
}

function toggleVoice() {
  if (!canSpeak()) return;
  state.voiceEnabled = !state.voiceEnabled;
  saveState();
  updateVoiceUi();
  if (state.voiceEnabled) {
    speakLine("Enfin. Je peux parler. Je vais essayer de rester presque raisonnable.");
  } else {
    window.speechSynthesis.cancel();
  }
}

function adaptiveSeconds(energy) {
  if (energy === "basse") return 8 * 60;
  if (energy === "haute") return 18 * 60;
  return 12 * 60;
}

function formatTime(seconds) {
  const safe = Math.max(0, seconds);
  return `${String(Math.floor(safe / 60)).padStart(2, "0")}:${String(safe % 60).padStart(2, "0")}`;
}

function getElapsedMinutes() {
  return Math.max(0, Math.round((adaptiveSeconds(state.energy) - state.secondsLeft) / 60));
}

function projectForGoal(goal) {
  if (goal === "argent") return "Prospection IA";
  if (goal === "admin") return "Ateliers IA";
  return state.project;
}

function syncInputsFromState() {
  goalSelect.value = state.goal;
  projectSelect.value = state.project;
  energySelect.value = state.energy;
  timerDisplay.textContent = formatTime(state.secondsLeft);
  timerHint.textContent = `Rythme ${state.energy} : ${Math.round(adaptiveSeconds(state.energy) / 60)} minutes. Pas une prison à tomates.`;
  updateVoiceUi();
  renderNotebook();
  renderLog();
}

function chooseLocalLine(event) {
  const pool = lines[event] || lines.manual;
  return pool[Math.floor(Math.random() * pool.length)];
}

async function generateLine(event) {
  const endpoint = localStorage.getItem(GEMMA_ENDPOINT_KEY);
  const context = { event, goal: state.goal, project: state.project, energy: state.energy, elapsedMinutes: getElapsedMinutes(), memory: state.memory };
  if (!endpoint) return chooseLocalLine(event);

  try {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prompt: promptTemplate(context), context })
    });
    if (!response.ok) throw new Error("Gemma endpoint unavailable");
    const data = await response.json();
    return sanitizeLine(String(data.text || data.response || data.message || chooseLocalLine(event)).trim(), event);
  } catch (error) {
    console.warn("Gemma fallback:", error);
    return chooseLocalLine(event);
  }
}

function sanitizeLine(text, event) {
  const fallback = chooseLocalLine(event);
  if (!text || text.length < 2) return fallback;
  if (text.length > 180) return text.slice(0, 177).trim() + "...";
  return text.replace(/^Clochette\s*:\s*/i, "").trim();
}

async function intervene(event) {
  const now = Date.now();
  const tooSoon = now - state.lastInterventionAt < 90_000;
  const maxed = state.interventionsToday >= 5;
  if (event !== "manual" && event !== "consent" && (tooSoon || maxed)) return;

  state.lastInterventionAt = now;
  if (event !== "manual" && event !== "consent") state.interventionsToday += 1;
  saveState();
  setBubble(await generateLine(event), event);
}

function setBubble(text, event = "manual") {
  bubble.textContent = text;
  bubble.classList.remove("hidden");
  clochette.classList.add("alert");
  clearTimeout(hiddenBubbleTimer);
  hiddenBubbleTimer = setTimeout(() => bubble.classList.add("hidden"), 9000);
  setTimeout(() => clochette.classList.remove("alert"), 1700);
  speakLine(text);
  addLog(text, event);
}

function addLog(text, event = "note") {
  const entry = { time: new Date().toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }), text, event };
  state.log = [entry, ...state.log].slice(0, 12);
  saveState();
  renderLog();
}

function renderLog() {
  activityLog.innerHTML = "";
  if (!state.log.length) {
    const li = document.createElement("li");
    li.textContent = "Aucune trace. Clochette aiguise probablement une remarque.";
    activityLog.appendChild(li);
    return;
  }
  state.log.forEach((entry) => {
    const li = document.createElement("li");
    li.textContent = `${entry.time} — ${entry.text}`;
    activityLog.appendChild(li);
  });
}

function makeNotebookNote() {
  state.notebookIndex += Math.floor(Math.random() * 17) + 1;
  const template = notebookTemplates[Math.floor(Math.random() * notebookTemplates.length)];
  const text = template({ n: state.notebookIndex, project: state.project, goal: labelForGoal(state.goal) });
  const note = { date: new Date().toLocaleDateString("fr-FR", { day: "2-digit", month: "short" }), text };
  state.notebook = [note, ...state.notebook].slice(0, 20);
  saveState();
  renderNotebook();
  return note;
}

function renderNotebook() {
  if (!privateNote) return;
  const latest = state.notebook[0];
  privateNote.textContent = latest ? `${latest.date} — ${latest.text}` : "Observation en attente. Elle fait semblant de ne pas te surveiller.";
}

function labelForGoal(goal) {
  return { creation: "création", argent: "argent", admin: "admin", sante: "santé", repos: "repos" }[goal] || goal;
}

function startTimer() {
  if (state.timerRunning) return;
  state.timerRunning = true;
  lastTick = Date.now();
  saveState();
  intervene("start");
  interval = setInterval(tick, 1000);
}

function pauseTimer() {
  state.timerRunning = false;
  saveState();
  clearInterval(interval);
  intervene("fatigue");
  if (Math.random() > 0.45) makeNotebookNote();
}

function resetTimer() {
  state.timerRunning = false;
  state.secondsLeft = adaptiveSeconds(state.energy);
  saveState();
  clearInterval(interval);
  syncInputsFromState();
  intervene("manual");
}

function tick() {
  const now = Date.now();
  const delta = Math.max(1, Math.round((now - lastTick) / 1000));
  lastTick = now;
  state.secondsLeft = Math.max(0, state.secondsLeft - delta);
  const elapsed = getElapsedMinutes();

  if (elapsed === 5 && state.energy === "basse") intervene("fatigue");
  if (elapsed === 9 && state.goal === "argent" && state.project !== "Prospection IA") intervene("money");
  if (elapsed === 10 && state.project !== projectForGoal(state.goal)) intervene("drift");

  if (state.secondsLeft <= 0) {
    state.timerRunning = false;
    state.focusMinutesToday += Math.round(adaptiveSeconds(state.energy) / 60);
    clearInterval(interval);
    makeNotebookNote();
    intervene("win");
    state.secondsLeft = adaptiveSeconds(state.energy);
  }
  saveState();
  syncInputsFromState();
}

function updateFromInputs() {
  const previousProject = state.project;
  state.goal = goalSelect.value;
  state.project = projectSelect.value;
  const oldEnergy = state.energy;
  state.energy = energySelect.value;

  if (previousProject !== state.project) {
    state.projectSwitches += 1;
    state.lastProject = previousProject;
    if (state.projectSwitches % 3 === 0) intervene("drift");
    if (Math.random() > 0.55) makeNotebookNote();
  }

  if (!state.timerRunning && oldEnergy !== state.energy) state.secondsLeft = adaptiveSeconds(state.energy);
  saveState();
  syncInputsFromState();
}

function toggleConsent() {
  state.memory.consentGranted = !state.memory.consentGranted;
  if (state.memory.consentGranted && !state.memory.knownSignals.includes("activité humaine autorisée")) {
    state.memory.knownSignals.push("activité humaine autorisée");
  }
  saveState();
  intervene("consent");
}

function registerServiceWorker() {
  if ("serviceWorker" in navigator) navigator.serviceWorker.register("./sw.js").catch(() => {});
}

startBtn.addEventListener("click", startTimer);
pauseBtn.addEventListener("click", pauseTimer);
resetBtn.addEventListener("click", resetTimer);
manualPingBtn.addEventListener("click", () => intervene("manual"));
noteBtn?.addEventListener("click", () => makeNotebookNote());
voiceBtn?.addEventListener("click", toggleVoice);
spriteBtn.addEventListener("click", () => {
  if (state.memory.consentGranted) intervene("manual");
  else toggleConsent();
});
[goalSelect, projectSelect, energySelect].forEach((input) => input.addEventListener("change", updateFromInputs));
document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible") intervene("idle"); });

if (canSpeak()) {
  loadVoices();
  window.speechSynthesis.onvoiceschanged = loadVoices;
}

state.secondsLeft = state.secondsLeft || adaptiveSeconds(state.energy);
if (!state.notebook.length) makeNotebookNote();
syncInputsFromState();
registerServiceWorker();
setTimeout(() => intervene("manual"), 1200);
