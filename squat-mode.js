const squatBtn = document.getElementById("squatBtn");
const squatHint = document.getElementById("squatHint");
const SQUAT_KEY = "clochette-lite-squat-mode";

function setSquatMode(enabled, announce = false) {
  document.body.classList.toggle("squat-mode", enabled);
  localStorage.setItem(SQUAT_KEY, enabled ? "on" : "off");

  if (squatBtn) {
    squatBtn.textContent = enabled ? "Quitter Squat" : "Mode Squat";
  }

  if (squatHint) {
    squatHint.textContent = enabled
      ? "Mode Squat actif. Clochette squatte le bord de l’écran comme une locataire lumineuse."
      : "Mode normal : réglages. Mode Squat : Clochette habite le bord.";
  }

  if (announce && typeof setBubble === "function") {
    setBubble(
      enabled
        ? "Je m’installe au bord. Fais comme si c’était ton idée."
        : "Très bien. Je retourne dans l’application comme une fée administrative.",
      "squat"
    );
  }
}

function toggleSquatMode() {
  const enabled = !document.body.classList.contains("squat-mode");
  setSquatMode(enabled, true);
}

squatBtn?.addEventListener("click", toggleSquatMode);
setSquatMode(localStorage.getItem(SQUAT_KEY) === "on", false);
