const cadenceBtn = document.getElementById("cadenceBtn");
const cadenceHint = document.getElementById("cadenceHint");

let cadenceInterval = null;
let cadenceStopTimer = null;
let cadenceStartedAt = 0;
let cadenceCount = 0;

const cadenceLines = [
  "Test cadence. Si tu m'entends en arrière-plan, Opera a laissé une fenêtre ouverte.",
  "Toc toc. Je vérifie si le téléphone m'a mise au placard.",
  "Cadence active. Je parle trop souvent exprès. Moment rare, profite.",
  "Si cette phrase arrive en retard, Android m'a probablement endormie avec un coussin.",
  "Observation : rester vivante en arrière-plan n'est pas naturel pour une PWA. Je le prends mal.",
  "Je note l'heure. Si je saute un tour, accuse le navigateur, pas mon talent.",
  "Petit signal féerique. Pas une notification système, juste moi qui tape sur la vitre.",
  "Test en cours. La fée fait du bruit pour la science. Quelle époque."
];

function setCadenceHint(message) {
  if (cadenceHint) cadenceHint.textContent = message;
}

function cadencePick() {
  return cadenceLines[Math.floor(Math.random() * cadenceLines.length)];
}

function cadenceSpeak() {
  cadenceCount += 1;
  const hidden = document.visibilityState === "hidden";
  const elapsed = Math.round((Date.now() - cadenceStartedAt) / 1000);
  const line = `${cadencePick()} [test ${cadenceCount}, ${elapsed}s, ${hidden ? "arrière-plan" : "visible"}]`;

  if (typeof setBubble === "function") {
    setBubble(line, "cadence-test");
  } else {
    console.log("Clochette cadence:", line);
  }

  setCadenceHint(`Test cadence actif : phrase ${cadenceCount}. Passe l'app en arrière-plan 1 ou 2 minutes, puis reviens voir le journal.`);
}

function stopCadenceTest(reason = "Test terminé. Clochette reprend une fréquence moins insupportable.") {
  clearInterval(cadenceInterval);
  clearTimeout(cadenceStopTimer);
  cadenceInterval = null;
  cadenceStopTimer = null;
  if (cadenceBtn) cadenceBtn.textContent = "Test cadence";
  setCadenceHint(reason);
  if (typeof setBubble === "function") setBubble(reason, "cadence-test");
}

function startCadenceTest() {
  cadenceStartedAt = Date.now();
  cadenceCount = 0;
  if (cadenceBtn) cadenceBtn.textContent = "Stop cadence";
  setCadenceHint("Test cadence lancé pour 6 minutes. Mets l'app en arrière-plan, verrouille brièvement, puis reviens voir si le journal a continué.");
  cadenceSpeak();
  cadenceInterval = setInterval(cadenceSpeak, 20_000);
  cadenceStopTimer = setTimeout(() => stopCadenceTest(), 6 * 60_000);
}

function toggleCadenceTest() {
  if (cadenceInterval) {
    stopCadenceTest("Test cadence arrêté. La fée range son mégaphone minuscule.");
    return;
  }
  startCadenceTest();
}

cadenceBtn?.addEventListener("click", toggleCadenceTest);
