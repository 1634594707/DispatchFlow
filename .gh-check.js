const https = require("https");
const url = "https://api.github.com/repos/1634594707/DispatchFlow/actions/runs?per_page=3";
https.get(url, { headers: { "User-Agent": "ci-check" } }, res => {
  let d = "";
  res.on("data", c => d += c);
  res.on("end", () => {
    const j = JSON.parse(d);
    for (const r of j.workflow_runs) {
      process.stdout.write(`run=${r.id} sha=${r.head_sha.slice(0,7)} status=${r.status} conclusion=${r.conclusion} created=${r.created_at}
`);
    }
  });
});
