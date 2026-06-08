(() => {
  const ANSWER_LOG_KEY = "clochette-lite-answer-log-v1";
  const MAX_LOG = 120;

  const actions = [
    {
      id: "done",
      label: "Fait",
      title: "J'ai fait quelque chose de concret",
      event: "answer-done",
      replies: [
        "Preuve reçue. Une chose existe. Je range mon soupçon dans un tiroir moins bruyant.",
        "Action confirmée. Je vais être insupportable : ça compte.",
        "Très bien. Le réel a gagné un point contre le brouillard. Je note."
      ]
    },
    {
      id: "not_done",
      label: "Pas fait",
      title: "Je n'ai pas avancé",
      event: "answer-not-done",
      replies: [
        "Merci pour l'honnêteté. On réduit la taille du monstre : quelle miette faisable maintenant ?",
        "Pas fait. Ce n'est pas un drame, c'est une donnée. Une petite action, et on repart.",
        "Reçu. Le dossier n'avance pas par télépathie. Dommage, j'avais espoir."
      ]
    },
    {
      id: "avoid",
      label: "Je fuis",
      title: "Je suis en train d'éviter",
      event: "answer-avoid",
      replies: [
        "Fuite déclarée. C'est presque élégant. Maintenant on choisit une sortie utile.",
        "Voilà. Une confession décorative. Qu'est-ce qui pique dans cette tâche ?",
        "Je note : évitement conscient. C'est déjà plus propre qu'une fuite en costume de recherche."
      ]
    },
    {
      id: "pause",
      label: "Pause",
      title: "Je prends une vraie pause",
      event: "answer-pause",
      replies: [
        "Pause validée, mais vraie pause. Pas le marécage lumineux des onglets.",
        "Très bien. Le mammifère créatif part refroidir le moteur. Je garde la porte.",
        "Pause acceptée. Reviens avec un corps, pas seulement une collection d'idées chaudes."
      ]
    },
    {
      id: "idea",
      label: "Idée parasite",
      title: "Une nouvelle idée vient de détourner l'attention",
      event: "answer-idea",
      replies: [
        "Idée parasite capturée. On la met en bocal, pas au volant.",
        "Nouvelle idée repérée. Charmante. Dangereuse. Elle attendra dans le placard à lucioles.",
        "Je note l'étincelle. Maintenant retour au feu principal, Dionysos de poche."
      ]
    }
  ];

  function pick(list) {
    return list[Math.floor(Math.random() * list.length)];
  }

  function readLog() {
    try {
      const log = JSON.parse(localStorage.getItem(ANSWER_LOG_KEY) || "[]");
      return Array.isArray(log) ? log : [];
    } catch {
      return [];
    }
  }

  function writeLog(log) {
    localStorage.setItem(ANSWER_LOG_KEY, JSON.stringify(log.slice(0, MAX_LOG)));
  }

  function getContext() {
    return {
      goal: document.getElementById("goalSelect")?.value || "creation",
      project: document.getElementById("projectSelect")?.value || "Blacklace Island",
      energy: document.getElementById("energySelect")?.value || "moyenne",
      prompt: document.getElementById("bubble")?.textContent || "",
      at: new Date().toISOString()
    };
  }

  function recordAnswer(action) {
    const context = getContext();
    const entry = {
      id: `ans_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      action: action.id,
      label: action.label,
      event: action.event,
      ...context
    };

    writeLog([entry, ...readLog()]);

    if (typeof storePhraseLine === "function") {
      storePhraseLine(`${action.label} — ${context.project} — ${context.goal}`, action.event, { source: "answer-engine" });
    }

    const reply = contextualReply(action, context);
    if (typeof setBubble === "function") setBubble(reply, action.event);
    else {
      const bubble = document.getElementById("bubble");
      if (bubble) bubble.textContent = reply;
    }
  }

  function contextualReply(action, context) {
    if (action.id === "done") {
      return pick([
        `${context.project} a reçu une preuve. Petite, peut-être. Réelle, sûrement. Je note.` ,
        `Fait sur ${context.project}. Le brouillard recule d'un centimètre. C'est humiliant pour lui.`,
        pick(action.replies)
      ]);
    }
    if (action.id === "not_done") {
      return pick([
        `Pas fait sur ${context.project}. On réduit : une action de deux minutes, pas un destin.`,
        `${context.project} reste immobile. Question utile : c'est trop grand, trop flou, ou trop pénible ?`,
        pick(action.replies)
      ]);
    }
    if (action.id === "avoid") {
      return pick([
        `Fuite autour de ${context.project}. Hypothèse : le vrai morceau est plus petit et plus désagréable que prévu.`,
        `Tu fuis ${context.project}. Enfin une donnée honnête. Maintenant, on nomme le caillou.`,
        pick(action.replies)
      ]);
    }
    if (action.id === "pause") {
      return context.energy === "basse"
        ? "Pause validée. Avec énergie basse, c'est maintenance du mammifère, pas trahison du génie."
        : pick(action.replies);
    }
    if (action.id === "idea") {
      return pick([
        `Idée parasite stockée. ${context.project} garde le volant. Elle pourra miauler plus tard.`,
        `Nouvelle luciole capturée. J'interdis son couronnement immédiat. Retour à ${context.project}.`,
        pick(action.replies)
      ]);
    }
    return pick(action.replies);
  }

  function injectStyles() {
    if (document.getElementById("answerEngineStyles")) return;
    const style = document.createElement("style");
    style.id = "answerEngineStyles";
    style.textContent = `
      .answer-row {
        display: flex;
        flex-wrap: wrap;
        gap: 7px;
        margin-top: 10px;
        pointer-events: auto;
      }

      .answer-btn {
        border: 1px solid rgba(255,255,255,.18);
        background: rgba(21, 16, 40, .74);
        color: rgba(255,255,255,.92);
        border-radius: 999px;
        padding: 8px 10px;
        font-size: .72rem;
        font-weight: 800;
        letter-spacing: .01em;
        box-shadow: 0 8px 24px rgba(0,0,0,.22);
        backdrop-filter: blur(12px);
      }

      .answer-btn:hover,
      .answer-btn:focus-visible {
        transform: translateY(-1px);
        background: rgba(113, 73, 255, .35);
        border-color: rgba(255,255,255,.34);
        outline: none;
      }

      body.squat-mode .answer-row {
        position: fixed;
        left: 18px;
        right: min(28vw, 210px);
        bottom: 144px;
        z-index: 6;
      }

      @media (max-width: 760px) {
        body.squat-mode .answer-row {
          left: 12px;
          right: 118px;
          bottom: 148px;
          gap: 6px;
        }
        .answer-btn {
          padding: 7px 8px;
          font-size: .66rem;
        }
      }

      @media (max-width: 420px) {
        body.squat-mode .answer-row {
          right: 100px;
          bottom: 140px;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function buildAnswerRow() {
    injectStyles();
    const bubble = document.getElementById("bubble");
    if (!bubble || document.getElementById("answerRow")) return;

    const row = document.createElement("div");
    row.id = "answerRow";
    row.className = "answer-row";
    row.setAttribute("aria-label", "Réponses rapides à Clochette");

    actions.forEach((action) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "answer-btn";
      button.textContent = action.label;
      button.title = action.title;
      button.addEventListener("click", (event) => {
        event.preventDefault();
        event.stopPropagation();
        recordAnswer(action);
      });
      row.appendChild(button);
    });

    bubble.insertAdjacentElement("afterend", row);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", buildAnswerRow);
  } else {
    buildAnswerRow();
  }

  window.clochetteAnswerEngine = {
    read: readLog,
    answer: recordAnswer
  };
})();
