const SYSTEM_PROMPT =
  "Tu es Clochette, une présence Android légère. Tu parles français naturellement, avec accents. " +
  "Tu fais des phrases courtes, humaines, parfois drôles, jamais cryptiques par défaut. " +
  "Tu observes seulement les signaux autorisés. Tu ne prétends pas lire le contenu privé. " +
  "Réponds en moins de 25 mots.";

module.exports = async function handler(req, res) {
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  if (req.method !== "POST") {
    res.status(405).json({ ok: false, error: "method_not_allowed" });
    return;
  }

  const body = await readBody(req);
  const preferred = String(body.preferredProvider || "auto").toLowerCase();
  const providers = preferred === "gemini"
    ? ["gemini", "mistral"]
    : preferred === "mistral"
      ? ["mistral", "gemini"]
      : preferred === "local"
        ? ["local"]
        : ["mistral", "gemini", "local"];

  for (const provider of providers) {
    try {
      if (provider === "mistral" && process.env.MISTRAL_API_KEY) {
        const line = await callMistral(body);
        returnRemark(res, line, "mistral");
        return;
      }
      if (provider === "gemini" && process.env.GEMINI_API_KEY) {
        const line = await callGemini(body);
        returnRemark(res, line, "gemini");
        return;
      }
      if (provider === "local") {
        returnRemark(res, localLine(body), "local");
        return;
      }
    } catch (error) {
      // Try the next provider. The endpoint must always return valid JSON.
    }
  }

  returnRemark(res, localLine(body), "local");
};

async function readBody(req) {
  if (req.body && typeof req.body === "object") return req.body;
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  const raw = Buffer.concat(chunks).toString("utf8");
  if (!raw) return {};
  try {
    return JSON.parse(raw);
  } catch {
    return {};
  }
}

async function callMistral(body) {
  const response = await fetch("https://api.mistral.ai/v1/chat/completions", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${process.env.MISTRAL_API_KEY}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      model: "mistral-small-latest",
      temperature: 0.7,
      messages: [
        { role: "system", content: body.systemPrompt || SYSTEM_PROMPT },
        { role: "user", content: promptFrom(body) }
      ]
    })
  });
  if (!response.ok) throw new Error(`mistral_${response.status}`);
  const json = await response.json();
  return sanitizeLine(json.choices?.[0]?.message?.content);
}

async function callGemini(body) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${process.env.GEMINI_API_KEY}`;
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ parts: [{ text: `${body.systemPrompt || SYSTEM_PROMPT}\n\n${promptFrom(body)}` }] }]
    })
  });
  if (!response.ok) throw new Error(`gemini_${response.status}`);
  const json = await response.json();
  return sanitizeLine(json.candidates?.[0]?.content?.parts?.[0]?.text);
}

function promptFrom(body) {
  return [
    `Style: ${body.styleLevel || "naturel"}`,
    `App: ${body.foregroundApp || "inconnue"}`,
    `Durée: ${body.durationMinutes || 0} min`,
    `Bascules: ${body.appSwitchCount || 0}`,
    `Réponse utilisateur: ${body.userLastReply || ""}`,
    `Mémoire: ${body.recentMemorySummary || ""}`
  ].join("\n");
}

function localLine(body) {
  const app = body.foregroundApp || "cette appli";
  if (body.userLastReply) return "Je note. On garde une piste simple, pas un roman.";
  if ((body.appSwitchCount || 0) >= 4) return "Tu changes souvent d’application. Tu cherches quelque chose ou tu évites quelque chose ?";
  if ((body.durationMinutes || 0) >= 20) return `Tu es sur ${app} depuis un moment. Tu veux continuer ou faire le point ?`;
  return "Je suis là. Tu veux que je reste discrète ou que je t’aide à reprendre le fil ?";
}

function sanitizeLine(line) {
  return String(line || "").replace(/\s+/g, " ").trim().split(/\s+/).slice(0, 25).join(" ") || localLine({});
}

function returnRemark(res, line, providerUsed) {
  res.status(200).json({
    line: sanitizeLine(line),
    shouldSpeak: true,
    shouldOpenMic: false,
    listenSeconds: 15,
    providerUsed,
    source: "ai_gateway",
    cooldownMinutes: 8
  });
}
