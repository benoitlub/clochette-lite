module.exports = async function handler(req, res) {
  res.setHeader("Content-Type", "text/html; charset=utf-8");
  const mistral = process.env.MISTRAL_API_KEY ? "configured" : "missing";
  const gemini = process.env.GEMINI_API_KEY ? "configured" : "missing";
  res.status(200).send(`<!doctype html>
<html lang="fr">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Clochette Gateway</title>
  <style>
    body { font-family: system-ui, sans-serif; max-width: 720px; margin: 32px auto; padding: 0 16px; }
    textarea, select, button { width: 100%; margin: 8px 0; padding: 10px; }
    pre { background: #f4f0ea; padding: 12px; white-space: pre-wrap; }
  </style>
</head>
<body>
  <h1>Clochette Gateway</h1>
  <p>Service actif. Aucune clé n’est affichée.</p>
  <ul>
    <li>Mistral: ${mistral}</li>
    <li>Gemini: ${gemini}</li>
  </ul>
  <select id="style"><option>naturel</option><option>espiegle</option><option>feuch</option></select>
  <textarea id="context" rows="4">Je suis sur ChatGPT depuis 20 minutes.</textarea>
  <button id="go">Générer</button>
  <pre id="out">En attente.</pre>
  <script>
    document.getElementById('go').onclick = async () => {
      const out = document.getElementById('out');
      out.textContent = 'Test en cours...';
      const response = await fetch('/api/generate-remark', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          styleLevel: document.getElementById('style').value,
          foregroundApp: document.getElementById('context').value,
          preferredProvider: 'auto',
          language: 'fr-FR'
        })
      });
      out.textContent = JSON.stringify(await response.json(), null, 2);
    };
  </script>
</body>
</html>`);
};
