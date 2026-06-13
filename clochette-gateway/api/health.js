module.exports = async function handler(req, res) {
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.status(200).json({
    ok: true,
    service: "clochette-gateway",
    providers: {
      mistral: Boolean(process.env.MISTRAL_API_KEY),
      gemini: Boolean(process.env.GEMINI_API_KEY)
    }
  });
};
