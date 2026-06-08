const STORAGE_KEY = "clochette-lite-state-v1";
const GEMMA_ENDPOINT_KEY = "clochette-lite-gemma-endpoint";

const defaultState = {
  goal: "creation",
  project: "Blacklace Island",
  energy: "moyenne",
  secondsLeft: 12 * 60,
  timerRunning: false,
  focusMinutesToday: 0,
  interventionsToday: 0,
  lastInterventionAt: 0,
  log: []
};

const lines = {
  start: [
    "Ah. Te voilà.",
    "Je prends des notes.",
    "Bon. On fait semblant d'être raisonnables ?",
    "Feuch Institut : sujet réveillé. Prudence.",
    "Psst. On protège l'élan, pas le bazar."
  ],
  drift: [
    "Tu recommences.",
    "C'est important ou juste intéressant ?",
    "Le projet principal tousse dans le placard.",
    "Je pose juste la question : pourquoi celui-là maintenant ?",
    "Ah. Un détour avec des chaussures neuves.",
    "Je connais cette manœuvre.",
    "Le caillou est joli. Le pont attend."
  ],
  fatigue: [
    "Pause. Même les créateurs de mondes boivent de l'eau.",
    "Non. Là, ton cerveau vend des excuses en lot.",
    "Tu es fatigué ou tu négocies avec une chaise ?",
    "Feuch Institut : fumée cognitive légère.",
    "On pose les outils avant que tu rebaptises un bouton."
  ],
  win: [
    "Attends. Ça, c'était réel.",
    "Bien. Tu as fabriqué quelque chose.",
    "Je vais être insupportable : je suis fière.",
    "Micro-victoire confirmée. Je bombe le torse à ta place.",
    "Note : quand tu finis, ça fonctionne mieux. Étrange."
  ],
  money: [
    "Celui-là rapporte quelque chose ?",
    "La création applaudit. Le portefeuille tousse.",
    "Je veux bien être poétique. Le frigo, moins.",
    "Question sale : ça fait entrer de l'argent cette semaine ?",
    "La liberté aime beaucoup les factures payées."
  ],
  idle: [
    "Tu es encore là ?",
    "On reprend ou on s'accorde une vraie pause ?",
    "Le marécage entre les deux, je refuse.",
    "Je n'interviens pas. J'observe bruyamment.",
    "Tu as quitté ton corps ou juste l'onglet ?"
  ],
  manual: [
    "Question.",
    "Non.",
    "Je t'écoute. Enfin, je surveille.",
    "Je mérite un meilleur public.",
    "Tu préfères finir une chose ou nourrir trois illusions ?",
    "J'avais préparé une entrée dramatique.",
    "Je déteste avoir raison. Faux. J'adore."
  ]
};

const promptTemplate = ({ event, goal, project, energy, elapsedMinutes }) => `Tu es Clochette du Feuch Institut.
Tu vis dans l'écran de Benoît.
Tu es drôle, piquante, maternelle, un peu starlette, jamais coach.
Tu as du mal à comprendre les humains mais tu essaies.
Réponds en français. Maximum 22 mots.
Ne finis pas systématiquement par une question.
Pas d'emoji. Pas de conseil générique. Pas de thérapie.
Contexte: événement=${event}; objectif=${goal}; projet=${project}; énergie=${energy}; minutes=${elapsedMinutes}.
Écris UNE remarque courte, personnelle et vivante.`;

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
const bubble = $("bubble");
const clochette = $("clochette");
const spriteBtn = $("spriteBtn");
const activityLog = $("activityLog");

let state = loadState();
let interval = null;
let lastTick = Date.now();
let hiddenBubbleTimer = null;

function loadState() {
  try { return { ...defaultState, ...JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}") }; }
  catch { return { ...defaultState }; }
}

function saveState() { localStorage.setItem(STORAGE_KEY, JSON.stringify(state)); }

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
  renderLog();
}

function chooseLocalLine(event) {
  const pool = lines[event] || lines.manual;
  return pool[Math.floor(Math.random() * pool.length)];
}

async function generateLine(event) {
  const endpoint = localStorage.getItem(GEMMA_ENDPOINT_KEY);
  const context = { event, goal: state.goal, project: state.project, energy: state.energy, elapsedMinutes: getElapsedMinutes() };
  if (!endpoint) return chooseLocalLine(event);

  try {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prompt: promptTemplate(context), context })
    });
    if (!response.ok) throw new Error("Gemma endpoint unavailable");
    const data = await response.json();
    return String(data.text || data.response || data.message || chooseLocalLine(event)).trim();
  } catch (error) {
    console.warn("Gemma fallback:", error);
    return chooseLocalLine(event);
  }
}

async function intervene(event) {
  const now = Date.now();
  const tooSoon = now - state.lastInterventionAt < 90_000;
  const maxed = state.interventionsToday >= 5;
  if (event !== "manual" && (tooSoon || maxed)) return;

  state.lastInterventionAt = now;
  if (event !== "manual") state.interventionsToday += 1;
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
    intervene("win");
    state.secondsLeft = adaptiveSeconds(state.energy);
  }
  saveState();
  syncInputsFromState();
}

function updateFromInputs() {
  state.goal = goalSelect.value;
  state.project = projectSelect.value;
  const oldEnergy = state.energy;
  state.energy = energySelect.value;
  if (!state.timerRunning && oldEnergy !== state.energy) state.secondsLeft = adaptiveSeconds(state.energy);
  saveState();
  syncInputsFromState();
}

function registerServiceWorker() {
  if ("serviceWorker" in navigator) navigator.serviceWorker.register("./sw.js").catch(() => {});
}

startBtn.addEventListener("click", startTimer);
pauseBtn.addEventListener("click", pauseTimer);
resetBtn.addEventListener("click", resetTimer);
manualPingBtn.addEventListener("click", () => intervene("manual"));
spriteBtn.addEventListener("click", () => intervene("manual"));
[goalSelect, projectSelect, energySelect].forEach((input) => input.addEventListener("change", updateFromInputs));
document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible") intervene("idle"); });

state.secondsLeft = state.secondsLeft || adaptiveSeconds(state.energy);
syncInputsFromState();
registerServiceWorker();
setTimeout(() => intervene("manual"), 1200);
