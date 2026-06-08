const ENGINE_ENDPOINT_KEY = "clochette-lite-gemma-endpoint";
const engineWitness = document.getElementById("engineWitness");
const engineLabel = document.getElementById("engineLabel");
const engineDetail = document.getElementById("engineDetail");

function hasExternalEngine() {
  return Boolean(localStorage.getItem(ENGINE_ENDPOINT_KEY));
}

function setEngineMode(mode, detail) {
  if (!engineWitness || !engineLabel || !engineDetail) return;

  engineWitness.dataset.mode = mode;

  if (mode === "thinking") {
    engineLabel.textContent = "Réflexion étendue";
    engineDetail.textContent = detail || "Clochette consulte un moteur externe. Elle fait semblant que c'est naturel.";
    return;
  }

  if (mode === "extended") {
    engineLabel.textContent = "Réflexion étendue prête";
    engineDetail.textContent = detail || "Un moteur externe est branché. Clochette reste aux commandes.";
    return;
  }

  if (mode === "fallback") {
    engineLabel.textContent = "Retour local";
    engineDetail.textContent = detail || "Le moteur externe n'a pas répondu. Clochette improvise dignement.";
    return;
  }

  engineLabel.textContent = "Mode local";
  engineDetail.textContent = detail || "Clochette improvise avec son cerveau de poche.";
}

function refreshEngineMode() {
  setEngineMode(hasExternalEngine() ? "extended" : "local");
}

const originalFetch = window.fetch.bind(window);
window.fetch = async (...args) => {
  const url = String(args[0]?.url || args[0] || "");
  const endpoint = localStorage.getItem(ENGINE_ENDPOINT_KEY) || "";
  const isEngineCall = endpoint && url === endpoint;

  if (isEngineCall) setEngineMode("thinking", "Ça mouline. Elle déteste attendre, donc c'est intéressant.");

  try {
    const response = await originalFetch(...args);
    if (isEngineCall) setEngineMode(response.ok ? "extended" : "fallback");
    return response;
  } catch (error) {
    if (isEngineCall) setEngineMode("fallback");
    throw error;
  }
};

window.addEventListener("storage", refreshEngineMode);
refreshEngineMode();
