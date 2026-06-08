const GEMMA_ENDPOINT_STORAGE_KEY = "clochette-lite-gemma-endpoint";

const gemmaEndpointInput = document.getElementById("gemmaEndpointInput");
const gemmaSaveBtn = document.getElementById("gemmaSaveBtn");
const gemmaTestBtn = document.getElementById("gemmaTestBtn");
const gemmaClearBtn = document.getElementById("gemmaClearBtn");
const gemmaStatus = document.getElementById("gemmaStatus");

function getGemmaEndpoint() {
  return localStorage.getItem(GEMMA_ENDPOINT_STORAGE_KEY) || "";
}

function setGemmaStatus(message) {
  if (gemmaStatus) gemmaStatus.textContent = message;
}

function refreshGemmaUi() {
  const endpoint = getGemmaEndpoint();
  if (gemmaEndpointInput) gemmaEndpointInput.value = endpoint;
  setGemmaStatus(endpoint
    ? "Gemma est branché. Clochette garde son caractère, Gemma varie les répliques."
    : "Mode local. Clochette improvise avec son cerveau de poche.");
}

function saveGemmaEndpoint() {
  const value = gemmaEndpointInput?.value?.trim() || "";
  if (!value) {
    localStorage.removeItem(GEMMA_ENDPOINT_STORAGE_KEY);
    setGemmaStatus("Mode local rétabli. Gemma retourne bouder dans son coin.");
    return;
  }
  localStorage.setItem(GEMMA_ENDPOINT_STORAGE_KEY, value);
  setGemmaStatus("Passerelle enregistrée. Prochaine apparition : tentative Gemma.");
  if (typeof setBubble === "function") setBubble("Gemma branché. Je reste Clochette. Qu'il ne prenne pas la grosse tête.", "gemma");
}

function clearGemmaEndpoint() {
  localStorage.removeItem(GEMMA_ENDPOINT_STORAGE_KEY);
  refreshGemmaUi();
  if (typeof setBubble === "function") setBubble("Mode local. Plus rustique. Plus fiable. Très moi.", "gemma");
}

async function testGemmaEndpoint() {
  const endpoint = gemmaEndpointInput?.value?.trim() || getGemmaEndpoint();
  if (!endpoint) {
    setGemmaStatus("Aucune passerelle. Clochette reste en solo.");
    return;
  }

  setGemmaStatus("Test Gemma en cours. Si ça répond 'Black', on documente.");

  try {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        prompt: "Tu es Clochette du Feuch Institut. Réponds en français, maximum 12 mots. Dis que le test fonctionne.",
        context: { event: "gemma_test" }
      })
    });

    if (!response.ok) throw new Error("Réponse non valide");
    const data = await response.json();
    const text = String(data.text || data.response || data.message || "Gemma a répondu, mais avec un chapeau invisible.").trim();
    setGemmaStatus(`Réponse Gemma : ${text}`);
    if (typeof setBubble === "function") setBubble(text, "gemma-test");
  } catch (error) {
    setGemmaStatus("Gemma inaccessible. Clochette repasse en local sans faire de scène. Enfin presque.");
    if (typeof setBubble === "function") setBubble("Gemma ne répond pas. Je vais prétendre que c'était prévu.", "gemma-test");
  }
}

gemmaSaveBtn?.addEventListener("click", saveGemmaEndpoint);
gemmaClearBtn?.addEventListener("click", clearGemmaEndpoint);
gemmaTestBtn?.addEventListener("click", testGemmaEndpoint);
refreshGemmaUi();
