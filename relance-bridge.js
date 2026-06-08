(() => {
  const originalChooseLocalLine = window.chooseLocalLine;

  function readRelanceContext() {
    const goalSelect = document.getElementById("goalSelect");
    const projectSelect = document.getElementById("projectSelect");
    const energySelect = document.getElementById("energySelect");
    const timerDisplay = document.getElementById("timerDisplay");

    let elapsedMinutes = 0;
    const timerText = timerDisplay?.textContent || "";
    const match = timerText.match(/(\d{2}):(\d{2})/);
    if (match) {
      const remaining = Number(match[1]) * 60 + Number(match[2]);
      const energy = energySelect?.value || "moyenne";
      const total = energy === "basse" ? 8 * 60 : energy === "haute" ? 18 * 60 : 12 * 60;
      elapsedMinutes = Math.max(0, Math.round((total - remaining) / 60));
    }

    return {
      goal: goalSelect?.value || "creation",
      project: projectSelect?.value || "Blacklace Island",
      energy: energySelect?.value || "moyenne",
      elapsedMinutes
    };
  }

  window.chooseLocalLine = function chooseRelanceFirst(event = "manual") {
    if (event === "manual" && typeof window.clochetteGenerateRelance === "function") {
      const line = window.clochetteGenerateRelance(readRelanceContext());
      if (line) return line;
    }

    if (typeof originalChooseLocalLine === "function") {
      return originalChooseLocalLine(event);
    }

    return "Question : on fabrique quelque chose ou on polit l'excuse avec un chiffon neuf ?";
  };
})();
