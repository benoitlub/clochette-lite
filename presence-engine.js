(() => {
  const PRESENCE_KEY = "clochette-lite-presence-v1";
  const EVENT_KEY = "clochette-lite-presence-events-v1";
  const ANSWER_LOG_KEY = "clochette-lite-answer-log-v1";

  const CHECK_EVERY = 45_000;
  const MIN_COOLDOWN = 3 * 60_000;
  const FIRST_GRACE = 90_000;
  const IDLE_AFTER = 6 * 60_000;
  const NO_DONE_AFTER = 9 * 60_000;
  const MAX_PER_HOUR = 5;

  const now = () => Date.now();

  const defaultPresence = {
    installedAt: now(),
    lastActivityAt: now(),
    lastAppearanceAt: 0,
    lastDoneAt: 0,
    lastPromptAt: 0,
    appearances: [],
    projectChanges: [],
    mutedUntil: 0,
    lastProject: null,
    lastGoal: null,
    lastEnergy: null
  };

  function readJson(key, fallback) {
    try {
      const value = JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback));
      return value || fallback;
    } catch {
      return fallback;
    }
  }

  function writeJson(key, value) {
    localStorage.setItem(key, JSON.stringify(value));
  }

  function readPresence() {
    return { ...defaultPresence, ...readJson(PRESENCE_KEY, {}) };
  }

  function writePresence(presence) {
    const hourAgo = now() - 60 * 60_000;
    writeJson(PRESENCE_KEY, {
      ...presence,
      appearances: (presence.appearances || []).filter((time) => time > hourAgo).slice(-MAX_PER_HOUR),
      projectChanges: (presence.projectChanges || []).filter((item) => item.at > now() - 20 * 60_000).slice(-20)
    });
  }

  function readAnswerLog() {
    const log = readJson(ANSWER_LOG_KEY, []);
    return Array.isArray(log) ? log : [];
  }

  function recordEvent(type, extra = {}) {
    const presence = readPresence();
    const entry = { type, at: now(), ...extra };
    const events = readJson(EVENT_KEY, []);
    writeJson(EVENT_KEY, [entry, ...events].slice(0, 160));

    presence.lastActivityAt = now();
    if (type === "answer-done") presence.lastDoneAt = now();
    if (type === "manual-prompt" || type === "bubble-click" || type === "sprite-click") presence.lastPromptAt = now();
    if (type === "project-change") {
      presence.projectChanges = [{ at: now(), project: extra.project }, ...(presence.projectChanges || [])].slice(0, 20);
      presence.lastProject = extra.project || presence.lastProject;
    }
    if (type === "goal-change") presence.lastGoal = extra.goal || presence.lastGoal;
    if (type === "energy-change") presence.lastEnergy = extra.energy || presence.lastEnergy;

    writePresence(presence);
  }

  function getContext() {
    return {
      goal: document.getElementById("goalSelect")?.value || "creation",
      project: document.getElementById("projectSelect")?.value || "Blacklace Island",
      energy: document.getElementById("energySelect")?.value || "moyenne",
      timer: document.getElementById("timerDisplay")?.textContent || "12:00"
    };
  }

  function pick(list) {
    return list[Math.floor(Math.random() * list.length)];
  }

  function appearancesThisHour(presence) {
    const hourAgo = now() - 60 * 60_000;
    return (presence.appearances || []).filter((time) => time > hourAgo).length;
  }

  function isEligible(presence) {
    if (document.hidden) return false;
    if (now() - (presence.installedAt || now()) < FIRST_GRACE) return false;
    if (presence.mutedUntil && now() < presence.mutedUntil) return false;
    if (now() - (presence.lastAppearanceAt || 0) < MIN_COOLDOWN) return false;
    if (appearancesThisHour(presence) >= MAX_PER_HOUR) return false;
    return true;
  }

  function latestAnswer() {
    return readAnswerLog()[0] || null;
  }

  function evaluatePresence() {
    const presence = readPresence();
    const context = getContext();
    const answer = latestAnswer();
    const silence = now() - (presence.lastActivityAt || presence.installedAt || now());
    const noDone = now() - (presence.lastDoneAt || presence.installedAt || now());
    const recentProjectChanges = (presence.projectChanges || []).filter((item) => item.at > now() - 8 * 60_000);

    if (!isEligible(presence)) return null;

    if (answer?.action === "avoid" && now() - new Date(answer.at).getTime() > 4 * 60_000) {
      return {
        reason: "avoid-followup",
        text: pick([
          `Tu avais déclaré une fuite sur ${context.project}. Je reviens avec une lampe torche minuscule : quel morceau pique ?`,
          `Fuite enregistrée, puis silence. C'est presque poétique. On transforme ça en prochaine miette ?`,
          `Je n'appelle pas ça échouer. J'appelle ça une fuite qui n'a pas encore rempli son formulaire.`
        ])
      };
    }

    if (answer?.action === "idea" && now() - new Date(answer.at).getTime() > 3 * 60_000) {
      return {
        reason: "idea-followup",
        text: pick([
          `L'idée parasite est toujours en bocal. ${context.project} récupère le volant.`,
          `Je surveille la luciole. Elle est jolie, pas prioritaire. Prochaine preuve sur ${context.project} ?`,
          `Nouvelle idée capturée. Maintenant, on arrête de lui construire un palais.`
        ])
      };
    }

    if (recentProjectChanges.length >= 2) {
      return {
        reason: "dispersion",
        text: pick([
          `Deux changements de projet en peu de temps. Je sens la danse des portes. Laquelle reste ouverte ?`,
          `Tu viens de déplacer le projecteur plusieurs fois. Charmant. Suspect. On fixe ${context.project} cinq minutes ?`,
          `Dispersion feutrée détectée. Pas une accusation. Une petite sirène avec des ailes.`
        ])
      };
    }

    if (noDone > NO_DONE_AFTER) {
      return {
        reason: "no-done",
        text: pick([
          `Aucun “Fait” depuis un moment. Je ne dramatise pas : je réclame une preuve minuscule.`,
          `${context.project} n'a pas besoin d'une grande déclaration. Il demande juste un bouton “Fait” mérité.`,
          `Je vois des intentions. J'attends une trace. Même ridicule. Surtout ridicule.`
        ])
      };
    }

    if (silence > IDLE_AFTER) {
      return {
        reason: "idle",
        text: pick([
          `Silence long. Repos réel ou marécage avec écran lumineux ?`,
          `Je réapparais parce que le flou prenait ses aises. Tu travailles, tu pauses, ou tu flottilles ?`,
          `Absence de signal. Hypothèse : contemplation plate. Révision possible : mammifère fatigué.`
        ])
      };
    }

    return null;
  }

  function appear(candidate) {
    if (!candidate?.text) return;
    const presence = readPresence();
    presence.lastAppearanceAt = now();
    presence.appearances = [now(), ...(presence.appearances || [])];
    writePresence(presence);

    if (typeof setBubble === "function") setBubble(candidate.text, `presence-${candidate.reason}`);
    else {
      const bubble = document.getElementById("bubble");
      if (bubble) {
        bubble.textContent = candidate.text;
        bubble.classList.remove("hidden");
      }
    }
  }

  function bindPresenceEvents() {
    const projectSelect = document.getElementById("projectSelect");
    const goalSelect = document.getElementById("goalSelect");
    const energySelect = document.getElementById("energySelect");

    projectSelect?.addEventListener("change", () => recordEvent("project-change", { project: projectSelect.value }));
    goalSelect?.addEventListener("change", () => recordEvent("goal-change", { goal: goalSelect.value }));
    energySelect?.addEventListener("change", () => recordEvent("energy-change", { energy: energySelect.value }));

    document.getElementById("startBtn")?.addEventListener("click", () => recordEvent("timer-start"));
    document.getElementById("pauseBtn")?.addEventListener("click", () => recordEvent("timer-pause"));
    document.getElementById("resetBtn")?.addEventListener("click", () => recordEvent("timer-reset"));
    document.getElementById("manualPingBtn")?.addEventListener("click", () => recordEvent("manual-prompt"));
    document.getElementById("bubble")?.addEventListener("click", () => recordEvent("bubble-click"));
    document.getElementById("spriteBtn")?.addEventListener("click", () => recordEvent("sprite-click"));

    document.addEventListener("click", (event) => {
      const answer = event.target.closest?.(".answer-btn");
      if (answer) recordEvent(`answer-${answer.textContent.toLowerCase().replace(/\s+/g, "-")}`, { label: answer.textContent });
    }, true);

    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "visible") recordEvent("visible");
      else recordEvent("hidden");
    });
  }

  function tick() {
    const candidate = evaluatePresence();
    if (candidate) appear(candidate);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => {
      bindPresenceEvents();
      setInterval(tick, CHECK_EVERY);
    });
  } else {
    bindPresenceEvents();
    setInterval(tick, CHECK_EVERY);
  }

  window.clochettePresenceEngine = {
    read: readPresence,
    events: () => readJson(EVENT_KEY, []),
    tick,
    mute(minutes = 30) {
      const presence = readPresence();
      presence.mutedUntil = now() + minutes * 60_000;
      writePresence(presence);
    },
    unmute() {
      const presence = readPresence();
      presence.mutedUntil = 0;
      writePresence(presence);
    }
  };
})();
