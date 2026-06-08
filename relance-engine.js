const RELANCE_ENGINE_KEY = "clochette-lite-relance-engine-v1";

const relanceProfiles = {
  creation: {
    intent: "proteger l'elan",
    targets: ["une scene", "un commit", "une page", "un montage", "un choix visible"],
    traps: ["peaufiner le decor", "ouvrir un nouveau chantier", "renommer un bouton", "faire semblant de preparer"],
    verbs: ["fabriques", "poses", "termines", "montres", "verrouilles"]
  },
  argent: {
    intent: "ramener du concret",
    targets: ["un message client", "une relance", "un prix", "une offre", "un devis minuscule mais vivant"],
    traps: ["etre genial gratuitement", "faire joli sans vendre", "attendre un signe", "confondre reseau et revenus"],
    verbs: ["envoies", "proposes", "clarifies", "chiffres", "relances"]
  },
  admin: {
    intent: "enlever une epine",
    targets: ["un formulaire", "un mail", "un appel", "une piece jointe", "une decision administrative"],
    traps: ["ouvrir quinze onglets", "lire sans agir", "maudire l'administration comme sport", "chercher le bouton parfait"],
    verbs: ["closes", "envoies", "ranges", "notes", "valides"]
  },
  sante: {
    intent: "proteger le mammifere",
    targets: ["un verre d'eau", "une vraie pause", "un etirement", "une marche courte", "une douche diplomatique"],
    traps: ["negocier avec la fatigue", "tenir par fierte", "oublier le corps", "appeler ca motivation"],
    verbs: ["bois", "poses", "respires", "marches", "arretes"]
  },
  repos: {
    intent: "faire une vraie pause",
    targets: ["dix minutes nettes", "un repos sans ecran", "un repas", "une sortie", "un silence non coupable"],
    traps: ["scroller en appelant ca repos", "rester entre deux", "surveiller les projets pendant la pause", "culpabiliser dans le vide"],
    verbs: ["quittes", "poses", "respires", "manges", "laisses"]
  }
};

const relanceMoods = {
  basse: {
    temperature: "soft",
    openers: ["Doucement", "Mini-question", "Je baisse le volume", "Sans panique", "Version mammifere fatigue"],
    closers: ["Un seul geste suffit.", "Pas une epopee. Une miette.", "Je garde le fouet au placard.", "On sauve l'elan, pas l'empire."]
  },
  moyenne: {
    temperature: "sharp",
    openers: ["Question", "Observation", "Petit proces verbal", "Hypothese", "Je regarde la scene"],
    closers: ["Choisis petit, mais choisis.", "Je chronometre mon scepticisme.", "Le flou commence a transpirer.", "Une action, pas un roman." ]
  },
  haute: {
    temperature: "spark",
    openers: ["Feuch Institut", "Alerte energie", "Tres bien", "La machine est chaude", "Profitons du miracle"],
    closers: ["Maintenant, on capitalise.", "Fais un degat utile.", "Transforme ca en trace.", "Que quelque chose reste." ]
  }
};

const relanceFrames = [
  ({ opener, project, target, closer }) => `${opener} : sur ${project}, quel ${target} peut exister avant que tu t'echappes ? ${closer}`,
  ({ opener, trap, verb, target }) => `${opener} : tu peux ${verb} ${target}, ou ${trap}. Je sais lequel fait semblant d'etre intelligent.`,
  ({ project, intent, target }) => `${project} demande moins de genie et plus de trace. Mission : ${intent}. Prochaine preuve : ${target}.`,
  ({ opener, project, trap, closer }) => `${opener}. ${project} avance-t-il, ou es-tu en train de ${trap} ? ${closer}`,
  ({ project, verb, target }) => `Question sale et utile : tu ${verb} ${target} maintenant, ou tu preferes decorer l'evitement ?`,
  ({ opener, target, closer }) => `${opener} : donne-moi ${target}. Pas une strategie de royaume. ${closer}`,
  ({ project, trap }) => `${project} n'a pas besoin d'un nouveau mythe. Il a besoin que tu arretes de ${trap}.`,
  ({ opener, verb, target }) => `${opener}. Tu ${verb} ${target}. Ensuite seulement tu as le droit d'avoir une theorie brillante.`
];

function relanceReadMemory() {
  try {
    const saved = JSON.parse(localStorage.getItem(RELANCE_ENGINE_KEY) || "{}");
    return { recent: Array.isArray(saved.recent) ? saved.recent : [], lastMode: saved.lastMode || null };
  } catch {
    return { recent: [], lastMode: null };
  }
}

function relanceWriteMemory(memory) {
  localStorage.setItem(RELANCE_ENGINE_KEY, JSON.stringify({
    ...memory,
    recent: (memory.recent || []).slice(0, 40)
  }));
}

function relanceNormalize(text) {
  return String(text || "")
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function relancePick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

function relanceFreshPick(candidates, memory) {
  const recent = new Set((memory.recent || []).map(relanceNormalize));
  const fresh = candidates.filter((line) => !recent.has(relanceNormalize(line)));
  return relancePick(fresh.length ? fresh : candidates);
}

function relanceBuildContext(input = {}) {
  const goal = input.goal || "creation";
  const profile = relanceProfiles[goal] || relanceProfiles.creation;
  const mood = relanceMoods[input.energy || "moyenne"] || relanceMoods.moyenne;
  return {
    goal,
    project: input.project || "le chantier principal",
    energy: input.energy || "moyenne",
    elapsedMinutes: input.elapsedMinutes || 0,
    intent: profile.intent,
    target: relancePick(profile.targets),
    trap: relancePick(profile.traps),
    verb: relancePick(profile.verbs),
    opener: relancePick(mood.openers),
    closer: relancePick(mood.closers),
    temperature: mood.temperature
  };
}

function generateRelance(input = {}) {
  const memory = relanceReadMemory();
  const context = relanceBuildContext(input);
  const candidates = Array.from({ length: 12 }, () => relancePick(relanceFrames)(relanceBuildContext(input)))
    .concat([
      `${context.opener} : quelle est la prochaine preuve visible sur ${context.project} ? ${context.closer}`,
      `Tu n'as pas besoin d'etre pret. Tu as besoin de ${context.verb} ${context.target}.`,
      `Je note le contexte : ${context.project}, energie ${context.energy}. Maintenant, on retire le theatre et on garde ${context.target}.`,
      `Hypothese : tu sais deja quoi faire. Tu esperes juste une ceremonie d'autorisation. Refusee. ${context.closer}`,
      `Le plan minimal : ${context.verb} ${context.target}. Le plan feuch : inventer trois detours. Je surveille.`
    ]);

  const line = relanceFreshPick(candidates, memory);
  relanceWriteMemory({ ...memory, recent: [line, ...(memory.recent || [])], lastMode: context.temperature });

  if (typeof storePhraseLine === "function") {
    storePhraseLine(line, "manual", { source: "relance-engine" });
  }

  return line;
}

window.clochetteGenerateRelance = generateRelance;
