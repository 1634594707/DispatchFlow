const https = require("https");
const url = "https://api.github.com/repos/1634594707/DispatchFlow/actions/runs/32693590565/jobs";
https.get(url, { headers: { "User-Agent": "ci-check" } }, res => {
  let d = "";
  res.on("data", c => d += c);
  res.on("end", () => {
    const j = JSON.parse(d);
    for (const job of j.jobs || []) {
      process.stdout.write(`job=${job.name} status=${job.status} conclusion=${job.conclusion}
`);
      for (const s of job.steps || []) {
        if (s.conclusion === "failure") process.stdout.write(`  failed-step=${s.name}
`);
      }
    }
  });
});
