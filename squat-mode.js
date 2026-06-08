const squatBtn = document.getElementById("squatBtn");
const squatHint = document.getElementById("squatHint");
const spriteBtnForSquat = document.getElementById("spriteBtn");
const bubbleForSquat = document.getElementById("bubble");
const SQUAT_KEY = "clochette-lite-squat-mode";

function ensureSquatExitButton() {
  let exitBtn = document.getElementById("squatExitBtn");
  if (exitBtn) return exitBtn;

  exitBtn = document.createElement("button");
  exitBtn.id = "squatExitBtn";
  exitBtn.type = "button";
  exitBtn.textContent = "Réglages";
  exitBtn.setAttribute("aria-label", "Accéder aux réglages de Clochette");
  exitBtn.addEventListener("click", () => setSquatMode(false, true));
  document.body.appendChild(exitBtn);
  return exitBtn;
}

function setSquatMode(enabled, announce = false) {
  document.body.classList.toggle("squat-mode", enabled);
  localStorage.setItem(SQUAT_KEY, enabled ? "on" : "off");

  const exitBtn = ensureSquatExitButton();
  exitBtn.hidden = !enabled;

  if (squatBtn) {
    squatBtn.textContent = enabled ? "Réglages" : "Retour Squat";
  }

  if (squatHint) {
    squatHint.textContent = enabled
      ? "Accueil Squat actif. Clochette habite le bord. Réglages disponibles en haut."
      : "Réglages ouverts pour cette session. Retour Squat remet Clochette devant.";
  }

  if (announce && typeof setBubble === "function") {
    setBubble(
      enabled
        ? "Je m’installe au bord. Fais comme si c’était ton idée."
        : "Très bien. J’ouvre les coulisses. Ne touche pas aux câbles avec les dents.",
      "squat"
    );
  }
}

function toggleSquatMode() {
  const enabled = !document.body.classList.contains("squat-mode");
  setSquatMode(enabled, true);
}

function triggerNextDialogue() {
  if (typeof intervene === "function") {
    intervene("manual");
    return;
  }
  if (typeof setBubble === "function") {
    const lines = [
      "Je suis déjà là. C’est toi qui arrives en retard dans ton propre téléphone.",
      "Clique encore. J’adore quand on confond dialogue et bouton d’ascenseur.",
      "Hypothèse : tu testes ma patience. Bonne nouvelle, elle est décorative.",
      "Je peux continuer. C’est mon talent et ton problème."
    ];
    setBubble(lines[Math.floor(Math.random() * lines.length)], "squat-dialogue");
  }
}

squatBtn?.addEventListener("click", toggleSquatMode);
spriteBtnForSquat?.addEventListener("click", triggerNextDialogue);
bubbleForSquat?.addEventListener("click", triggerNextDialogue);
ensureSquatExitButton();
setSquatMode(true, false);
