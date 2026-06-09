window.CLOCHETTE_PROJECTS = {
  "Pro.Hibited Online": {
    kind: "jeu de cartes web",
    summary: "Adaptation numérique de Pro.Hibited : table de jeu, joueurs autour du plateau, cartes weed/hash/CBD/feuilles/filtres/contenants, sets à compléter, actions junky/cops/lost.",
    clochetteAngle: "Ne pas parler comme si c'était un simple site. C'est un jeu de table numérique avec placement, lisibilité mobile/paysage et règles déjà sensibles.",
    risks: ["trop toucher aux règles", "labels joueurs mal placés", "boutons gênants", "confondre habillage et gameplay"],
    usefulLines: [
      "Pro.Hibited n'est pas un site. C'est une table qui essaie de tenir dans une poche. Nuance pénible.",
      "Hypothèse : tu ne bloques pas sur le jeu. Tu bloques sur la lisibilité du plateau.",
      "Je rappelle que les règles marchent. Ne va pas les chatouiller avec un tournevis.",
      "Les cartes doivent respirer. Les boutons aussi. Les joueurs, hélas, ont des noms."
    ]
  },
  "Blacklace Island": {
    kind: "univers transmedia web",
    summary: "Île narrative, hub Natasha, zones visitables, Feuch Institut, Rotas, Aloisia, cartes, vidéos, ambiance ARG.",
    clochetteAngle: "Projet-monde. Risque de partir dans cinq directions magnifiques au lieu de finir un passage testable.",
    risks: ["dispersion", "nouvelle zone avant stabilisation", "POI hors carte", "cache GitHub Pages"],
    usefulLines: ["Blacklace tousse. Pas poétiquement. Techniquement.", "Une île entière, oui. Mais un bouton testable d'abord."]
  },
  "Terra": {
    kind: "livre publié / fable cosmique",
    summary: "Fable cosmique publiée, observation, lumière verte, Disclosure Day, trajectoires temporelles.",
    clochetteAngle: "Projet littéraire réel, déjà publié. Peut nourrir la suite sans se transformer en fuite des tâches concrètes.",
    risks: ["réécriture infinie", "fuite dans le volume suivant"],
    usefulLines: ["Terra existe. C'est donc moins une idée qu'une preuve embarrassante."]
  },
  "Creature-sync": {
    kind: "traducteur animalier absurde",
    summary: "Détection/traduction d'animaux, journal, partage, dons, ton pseudo-scientifique drôle.",
    clochetteAngle: "Garder l'humour et rendre l'interface lisible : image animal, traduction, journal, partage/don.",
    risks: ["pigeon suspect éternel", "traductions trop basses", "cache"],
    usefulLines: ["Le pigeon suspect est peut-être une fonctionnalité. Mais pas toute l'application."]
  },
  "SpecTRL": {
    kind: "traducteur spectral",
    summary: "Version fantôme de Creature-sync : radar, fragments, SLS, journal, partage, dons, ambiance Feuch Institut.",
    clochetteAngle: "Éviter les traces animales/corvidés. Garder signature, traduction, journal et sons spectraux.",
    risks: ["résidus Creature-sync", "page blanche", "header/footer trop lourds"],
    usefulLines: ["Un fantôme avec des plumes, c'est un problème de traduction. Ou un pigeon mort."
    ]
  }
};

window.getClochetteProjectContext = function getClochetteProjectContext(projectName) {
  return window.CLOCHETTE_PROJECTS?.[projectName] || null;
};
