const dialogueChoicesKey = "clochette-lite-dialogue-choices-v1";
const dialogueStateKey = "clochette-lite-dialogue-state-v1";

const playfulReactions = ["✨", "🫢", "😏", "🪄", "🙃", "👀"];
const mockGifLabels = ["[gif mental : Clochette lève un sourcil]", "[gif mental : petite révérence insolente]", "[gif mental : elle tapote le bord de l’écran]", "[gif mental : fumée dramatique inutile]"];

const dialogueTrees = {
  opening: {
    question: "Je te cerne comment aujourd’hui ?",
    choices: [
      { id: "A", label: "Par mon énergie", next: "energy" },
      { id: "B", label: "Par ce que j'évite", next: "avoidance" },
      { id: "C", label: "Par mon projet", next: "project" },
      { id: "D", label: "Tu improvises", next: "improvise" }
    ]
  },
  energy: {
    question: "Ton corps raconte quoi ?",
    choices: [
      { id: "A", label: "Je suis rincé", response: "Hypothèse : batterie basse. Je range trois piques et j’en garde une pour la précision.", next: "low_energy" },
      { id: "B", label: "Je suis moyen", response: "Énergie moyenne. Magnifique zone grise où les humains négocient avec leurs chaussettes mentales.", next: "first_step" },
      { id: "C", label: "Je suis chaud", response: "Très bien. Je note une anomalie exploitable : tu as du carburant. Ne le verse pas dans huit projets.", next: "first_step" },
      { id: "D", label: "Je ne sais pas", response: "D’accord. Quand l’humain ne sait pas, la fée commence par observer les miettes.", next: "avoidance" }
    ]
  },
  avoidance: {
    question: "Tu évites quoi, précisément ?",
    choices: [
      { id: "A", label: "Un appel", response: "Un appel. Donc un dragon minuscule avec une sonnerie. Je comprends, hélas.", next: "first_step" },
      { id: "B", label: "Un projet créatif", response: "Ah. Le projet tousse dans le placard. Il veut une poignée, pas une cathédrale.", next: "first_step" },
      { id: "C", label: "De l'admin", response: "Admin détecté. Papier invisible, menace réelle. Je mets des gants mentaux.", next: "first_step" },
      { id: "D", label: "Je ne sais même plus", response: "Alors on ne cherche pas la vérité. On cherche le premier fil qui dépasse.", next: "first_step" }
    ]
  },
  project: {
    question: "Lequel veut voler la scène ?",
    choices: [
      { id: "A", label: "Blacklace", response: "Blacklace a toujours l’air d’entrer avec de la fumée. Très pratique pour cacher les priorités.", next: "first_step" },
      { id: "B", label: "Pro.Hibited", response: "Pro.Hibited veut une table de jeu, pas un colloque intérieur. Je dis ça avec tendresse armée.", next: "first_step" },
      { id: "C", label: "Terra / livre", response: "Terra demande du silence. C’est rare, donc suspect et précieux.", next: "first_step" },
      { id: "D", label: "Clochette elle-même", response: "Évidemment. Je suis devenue le projet qui observe les autres projets. C’est presque professionnel.", next: "first_step" }
    ]
  },
  improvise: {
    question: "Je tente une hypothèse. Tu veux quoi, là ?",
    choices: [
      { id: "A", label: "Un coup de pied doux", response: "Très bien. Coup de pied doux : choisis un geste de deux minutes, pas une destinée.", next: "first_step" },
      { id: "B", label: "Une présence", response: "Je reste sur le bord. Pas sage. Présente. Légèrement trop.", next: "first_step" },
      { id: "C", label: "Une idée", response: "Idée autorisée, avalanche interdite. Je surveille les cailloux brillants.", next: "project" },
      { id: "D", label: "Qu’on me fiche la paix", response: "Accordé partiellement. Je me tais avec un carnet ouvert, nuance importante.", next: "opening" }
    ]
  },
  low_energy: {
    question: "Version batterie basse : on vise quoi ?",
    choices: [
      { id: "A", label: "Boire / manger", response: "Excellent. Le mammifère reçoit enfin une maintenance de base. Scène émouvante.", next: "opening" },
      { id: "B", label: "Ranger un détail", response: "Un détail. Pas la maison mentale entière. Je te vois venir avec ton balai dramatique.", next: "opening" },
      { id: "C", label: "Faire 2 minutes", response: "Deux minutes. Taille parfaite pour tromper le cerveau sans l’humilier publiquement.", next: "opening" },
      { id: "D", label: "Rien", response: "Rien peut être une stratégie si tu l’avoues. Si tu le déguises, je tousse.", next: "opening" }
    ]
  },
  first_step: {
    question: "Premier geste acceptable ?",
    choices: [
      { id: "A", label: "Ouvrir le fichier", response: "Ouvrir suffit. Pas sauver l’humanité. Juste ouvrir. Révolution minuscule.", next: "opening" },
      { id: "B", label: "Écrire une phrase", response: "Une phrase. Même moche. Les phrases moches sont des échafaudages, pas des aveux.", next: "opening" },
      { id: "C", label: "Faire une capture", response: "Capture acceptée. Preuve visuelle : le langage préféré des projets qui gigotent.", next: "opening" },
      { id: "D", label: "Je bloque", response: "Blocage reconnu. Très bien. Maintenant on arrête de le maquiller en réflexion profonde.", next: "avoidance" }
    ]
  }
};

function readDialogueMemory() {
  try { return JSON.parse(localStorage.getItem(dialogueChoicesKey) || "[]"); }
  catch { return []; }
}

function saveDialogueMemory(memory) {
  localStorage.setItem(dialogueChoicesKey, JSON.stringify(memory.slice(0, 200)));
}

function getDialogueState() {
  try { return JSON.parse(localStorage.getItem(dialogueStateKey) || "{\"node\":\"opening\"}"); }
  catch { return { node: "opening" }; }
}

function setDialogueState(state) {
  localStorage.setItem(dialogueStateKey, JSON.stringify(state));
}

function ensureChoicePanel() {
  let panel = document.getElementById("dialogueChoices");
  if (panel) return panel;
  panel = document.createElement("div");
  panel.id = "dialogueChoices";
  panel.className = "dialogue-choices";
  panel.setAttribute("aria-label", "Réponses rapides à Clochette");
  document.body.appendChild(panel);
  return panel;
}

function ensureStatsPanel() {
  let panel = document.getElementById("dialogueStats");
  if (panel) return panel;
  panel = document.createElement("div");
  panel.id = "dialogueStats";
  panel.className = "dialogue-stats";
  panel.hidden = true;
  document.body.appendChild(panel);
  return panel;
}

function buildDialogueStats() {
  const memory = readDialogueMemory();
  const counts = { A: 0, B: 0, C: 0, D: 0 };
  memory.forEach((item) => { if (counts[item.choice] !== undefined) counts[item.choice] += 1; });
  return { memory, counts, total: memory.length };
}

function maybeShowStats() {
  const { counts, total } = buildDialogueStats();
  const panel = ensureStatsPanel();
  if (total < 4 || Math.random() > 0.28) {
    panel.hidden = true;
    return;
  }
  const max = Math.max(1, ...Object.values(counts));
  panel.hidden = false;
  panel.innerHTML = `
    <div class="stats-title">Mini-diagnostic féerique</div>
    ${["A", "B", "C", "D"].map((key) => `<div class="stat-row"><span>${key}</span><div class="stat-track"><i style="width:${Math.round((counts[key] / max) * 100)}%"></i></div><b>${counts[key]}</b></div>`).join("")}
    <small>${total} réponses observées. Je ne conclus rien. Je fronce juste un sourcil.</small>
  `;
}

function playfulSuffix() {
  if (Math.random() < 0.28) return ` ${playfulReactions[Math.floor(Math.random() * playfulReactions.length)]}`;
  if (Math.random() < 0.18) return `\n${mockGifLabels[Math.floor(Math.random() * mockGifLabels.length)]}`;
  return "";
}

function renderDialogueNode(nodeId = "opening") {
  const node = dialogueTrees[nodeId] || dialogueTrees.opening;
  const panel = ensureChoicePanel();
  panel.innerHTML = "";

  if (typeof setBubble === "function") setBubble(node.question, "dialogue-question");

  node.choices.forEach((choice) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "choice-btn";
    button.innerHTML = `<strong>${choice.id}</strong><span>${choice.label}</span>`;
    button.addEventListener("click", () => chooseDialogueOption(nodeId, choice));
    panel.appendChild(button);
  });

  setDialogueState({ node: nodeId, updatedAt: new Date().toISOString() });
  maybeShowStats();
}

function chooseDialogueOption(nodeId, choice) {
  const memory = readDialogueMemory();
  saveDialogueMemory([
    { node: nodeId, choice: choice.id, label: choice.label, at: new Date().toISOString() },
    ...memory
  ]);

  if (window.clochettePhraseBank?.store && choice.response) {
    window.clochettePhraseBank.store(choice.response, "dialogue-response", { source: "dialogue" });
  }

  if (typeof setBubble === "function") {
    setBubble(`${choice.response || `Option ${choice.id}. Je note.`}${playfulSuffix()}`, "dialogue-response");
  }

  window.setTimeout(() => renderDialogueNode(choice.next || "opening"), 2200);
}

function startClochetteDialogue() {
  const state = getDialogueState();
  renderDialogueNode(state.node || "opening");
}

window.startClochetteDialogue = startClochetteDialogue;
window.clochetteDialogueMemory = { read: readDialogueMemory, stats: buildDialogueStats };

ensureChoicePanel();
ensureStatsPanel();
window.setTimeout(startClochetteDialogue, 900);
