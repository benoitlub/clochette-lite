const observationKey = "clochette-lite-observations-v1";
const sourceConsentKey = "clochette-lite-source-consent-v1";

function readObservationNotes() {
  try { return JSON.parse(localStorage.getItem(observationKey) || "[]"); }
  catch { return []; }
}

function saveObservationNotes(notes) {
  localStorage.setItem(observationKey, JSON.stringify(notes.slice(0, 80)));
}

function readSourceConsent() {
  try {
    return JSON.parse(localStorage.getItem(sourceConsentKey) || "{}");
  } catch {
    return {};
  }
}

function saveSourceConsent(consent) {
  localStorage.setItem(sourceConsentKey, JSON.stringify(consent));
}

function getDialogueStatsSafe() {
  if (window.clochetteDialogueMemory?.stats) return window.clochetteDialogueMemory.stats();
  return { total: 0, counts: { A: 0, B: 0, C: 0, D: 0 }, memory: [] };
}

function dominantChoice(counts) {
  return Object.entries(counts).sort((a, b) => b[1] - a[1])[0] || ["?", 0];
}

function inferMoodFromDialogue() {
  const stats = getDialogueStatsSafe();
  const [choice, value] = dominantChoice(stats.counts);
  if (stats.total < 3) return { label: "données insuffisantes", confidence: 18 };
  const confidence = Math.min(82, Math.round((value / Math.max(1, stats.total)) * 100));
  const labels = {
    A: "tu cherches une entrée corporelle ou simple",
    B: "tu tournes autour d’un évitement créatif",
    C: "tu cherches le projet qui réclame la scène",
    D: "tu es dans le brouillard ou tu réclames de l’espace"
  };
  return { label: labels[choice] || "motif flou", confidence };
}

function makeObservationNote() {
  const stats = getDialogueStatsSafe();
  const mood = inferMoodFromDialogue();
  const hour = new Date().getHours();
  const late = hour >= 22 || hour < 6;
  const consent = readSourceConsent();
  const enabledSources = Object.entries(consent).filter(([, enabled]) => enabled).map(([key]) => key);

  const lines = [
    `Ce que je sais : ${stats.total} réponses A/B/C/D enregistrées localement.`,
    `Ce que je suppose : ${mood.label}.`,
    `Confiance : ${mood.confidence}%.`
  ];

  if (late) lines.push("Observation bonus : il est tard. Le mammifère négocie peut-être avec sa batterie interne.");
  if (enabledSources.length) lines.push(`Sources autorisées en préparation : ${enabledSources.join(", ")}. Je ne lis rien sans passerelle réelle.`);
  if (!enabledSources.length) lines.push("Sources externes : aucune. Je travaille avec tes réponses et mes sourcils.");

  return {
    id: crypto?.randomUUID ? crypto.randomUUID() : String(Date.now()),
    at: new Date().toISOString(),
    text: lines.join("\n"),
    confidence: mood.confidence
  };
}

function renderObservationNote(note) {
  const privateNote = document.getElementById("privateNote");
  if (privateNote) {
    privateNote.innerHTML = note.text.replaceAll("\n", "<br />");
  }
  if (typeof setBubble === "function") {
    setBubble(`Note de Clochette : ${note.text.split("\n")[1] || "je commence à avoir des hypothèses."}`, "observation");
  }
}

function addObservationNote() {
  const note = makeObservationNote();
  saveObservationNotes([note, ...readObservationNotes()]);
  renderObservationNote(note);
  return note;
}

function ensureSourcesPanel() {
  let panel = document.getElementById("sourcesPanel");
  if (panel) return panel;
  panel = document.createElement("section");
  panel.id = "sourcesPanel";
  panel.className = "panel sources-panel";
  panel.innerHTML = `
    <div class="log-header"><div><p class="eyebrow mini">Consentement</p><h2>Sources autorisées</h2></div><button id="sourceResetBtn" class="small ghost">Tout couper</button></div>
    <p class="microcopy">Préparation seulement : cocher une source n’ouvre aucune donnée réelle tant qu’une passerelle n’est pas branchée.</p>
    <div class="source-grid">
      ${[
        ["github", "GitHub"],
        ["notion", "Notion"],
        ["calendar", "Calendrier"],
        ["weather", "Météo"],
        ["location", "Localisation"],
        ["activity", "Activité physique"]
      ].map(([key, label]) => `<label class="source-toggle"><input type="checkbox" data-source="${key}" /> <span>${label}</span></label>`).join("")}
    </div>
  `;
  const logPanel = document.querySelector(".log-panel");
  document.querySelector(".app-shell")?.insertBefore(panel, logPanel || null);
  return panel;
}

function hydrateSourcesPanel() {
  const panel = ensureSourcesPanel();
  const consent = readSourceConsent();
  panel.querySelectorAll("input[data-source]").forEach((input) => {
    input.checked = Boolean(consent[input.dataset.source]);
    input.addEventListener("change", () => {
      const next = readSourceConsent();
      next[input.dataset.source] = input.checked;
      saveSourceConsent(next);
      if (typeof setBubble === "function") {
        setBubble(input.checked ? `Je note : ${input.dataset.source} autorisé en principe. Je ne lis rien sans vraie passerelle.` : `Source ${input.dataset.source} coupée. Petite fée, grandes manières.`, "consent");
      }
    });
  });
  panel.querySelector("#sourceResetBtn")?.addEventListener("click", () => {
    saveSourceConsent({});
    panel.querySelectorAll("input[data-source]").forEach((input) => { input.checked = false; });
    if (typeof setBubble === "function") setBubble("Toutes les sources sont coupées. Je retourne à mes hypothèses artisanales.", "consent");
  });
}

window.clochetteObservations = {
  add: addObservationNote,
  read: readObservationNotes,
  sources: readSourceConsent
};

document.getElementById("noteBtn")?.addEventListener("click", addObservationNote);
hydrateSourcesPanel();
window.setTimeout(() => {
  const notes = readObservationNotes();
  if (notes[0]) renderObservationNote(notes[0]);
}, 1400);
