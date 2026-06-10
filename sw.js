const CACHE_NAME = "clochette-lite-v191-voicefix";
const STATIC_ASSETS = [
  "./style.css?v=1.9.1",
  "./manifest.webmanifest?v=1.9.1"
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(STATIC_ASSETS)));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
  );
  self.clients.claim();
});

function isFreshFile(request) {
  const url = new URL(request.url);
  return url.pathname.endsWith("/")
    || url.pathname.endsWith("/index.html")
    || url.pathname.endsWith("/clochette-compat.js")
    || url.pathname.endsWith("/app.js")
    || url.pathname.endsWith("/listen.js")
    || url.pathname.endsWith("/gemma-settings.js")
    || url.pathname.endsWith("/engine-status.js")
    || url.pathname.endsWith("/phrase-bank.js")
    || url.pathname.endsWith("/project-knowledge.js")
    || url.pathname.endsWith("/relance-engine.js")
    || url.pathname.endsWith("/relance-bridge.js")
    || url.pathname.endsWith("/answer-engine.js")
    || url.pathname.endsWith("/presence-engine.js")
    || url.pathname.endsWith("/feedback.js")
    || url.pathname.endsWith("/squat-mode.js")
    || url.pathname.endsWith("/sw.js");
}

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;

  if (isFreshFile(event.request)) {
    event.respondWith(
      fetch(event.request, { cache: "no-store" })
        .then((response) => response)
        .catch(() => caches.match(event.request).then((cached) => cached || caches.match("./index.html")))
    );
    return;
  }

  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).then((response) => {
        const clone = response.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        return response;
      }).catch(() => caches.match("./index.html"));
    })
  );
});
