// 判定"错峰返充"在现有数据里是否可表达：到达序列的小时剖面到底有多不平。
// 输入 reports/energy_features.csv（TSV：park/station/slot_start/arrivals/energy_kwh）
// 以及 reports/energy_forecast_result.sql（模型产出的 24 小时剖面）
const fs = require('fs');

const csv = fs.readFileSync('reports/energy_features.csv', 'utf8').trim().split(/\r?\n/);
const head = csv[0].split('\t');
const rows = csv.slice(1).map(l => {
  const c = l.split('\t');
  return {
    station: c[head.indexOf('station_code')],
    slot: c[head.indexOf('slot_start')],
    arrivals: Number(c[head.indexOf('arrivals')]),
    kwh: Number(c[head.indexOf('energy_kwh')]),
  };
});

const q = (a, p) => { const s = [...a].sort((x, y) => x - y); return s[Math.min(s.length - 1, Math.round(p * (s.length - 1)))]; };
const mean = a => a.reduce((x, y) => x + y, 0) / a.length;
const sd = a => { const m = mean(a); return Math.sqrt(a.reduce((s, v) => s + (v - m) ** 2, 0) / (a.length - 1)); };

const all = rows.map(r => r.arrivals);
console.log('== 观测序列（站点×小时）==');
console.log(`样本 ${all.length}  站点 ${new Set(rows.map(r => r.station)).size}  ` +
  `日期 ${[...new Set(rows.map(r => r.slot.slice(0, 10)))].sort()[0]} .. ` +
  `${[...new Set(rows.map(r => r.slot.slice(0, 10)))].sort().slice(-1)[0]}`);
console.log(`arrivals: mean ${mean(all).toFixed(1)}  p50 ${q(all, .5)}  p95 ${q(all, .95).toFixed(1)}  max ${Math.max(...all)}  CV ${(sd(all) / mean(all)).toFixed(2)}`);
console.log(`energy_kwh(按 SOC 百分点求和): mean ${mean(rows.map(r => r.kwh)).toFixed(1)}  max ${Math.max(...rows.map(r => r.kwh))}`);

// 小时剖面：这是 pressureThreshold 能不能成立的唯一依据
const byHour = {};
rows.forEach(r => {
  const h = Number(r.slot.slice(11, 13));
  (byHour[h] = byHour[h] || []).push(r.arrivals);
});
const prof = Object.keys(byHour).map(Number).sort((a, b) => a - b)
  .map(h => ({ h, mean: mean(byHour[h]), n: byHour[h].length, p95: q(byHour[h], .95) }));
const peak = Math.max(...prof.map(p => p.mean));
const trough = Math.min(...prof.map(p => p.mean));
const overall = mean(all);
console.log('\n== 到达的小时剖面（全站点合并）==');
console.log('  h  mean   p95    相对全局均值');
prof.forEach(p => console.log(`  ${String(p.h).padStart(2)}  ${p.mean.toFixed(1).padStart(6)}  ${p.p95.toFixed(1).padStart(6)}  ${(p.mean / overall).toFixed(2)}x  ${'#'.repeat(Math.round(p.mean / overall * 10))}`));
console.log(`峰值小时均值 ${peak.toFixed(1)} / 谷值 ${trough.toFixed(1)} = ${(peak / trough).toFixed(2)}x`);
console.log(`剖面自身的变异系数 CV = ${sd(prof.map(p => p.mean)) / mean(prof.map(p => p.mean))}`);
console.log('判据：错峰需要"峰段"和"平段"可分。经验上峰谷比 <1.5 时，任何绝对阈值都只能退化为"整天恒开"或"整天恒关"。');

// 缺失小时 = 该小时没有任何会话；剖面必须按 24 小时补零，否则均值被高估
const present = new Set(prof.map(p => p.h));
const missing = [...Array(24).keys()].filter(h => !present.has(h));
if (missing.length) console.log(`没有任何会话的小时：${missing.join(',')}`);

// 模型剖面 vs 观测
const sql = fs.readFileSync('reports/energy_forecast_result.sql', 'utf8');
const pred = [];
for (const m of sql.matchAll(/VALUES \((\d+),(\d+),'([^']+)','([\d-]+)',(\d+),([\d.]+),([\d.]+),([\d.]+)/g)) {
  pred.push({ station: m[3], date: m[4], hour: Number(m[5]), p50: Number(m[6]), p90: Number(m[7]), pressure: Number(m[8]) });
}
console.log('\n== 模型产出的剖面 ==');
console.log(`行 ${pred.length}  站点 ${new Set(pred.map(p => p.station)).size}  日期 ${[...new Set(pred.map(p => p.date))].join(',')}`);
const perDate = [...new Set(pred.map(p => p.date))];
perDate.forEach(d => {
  const p = pred.filter(x => x.date === d);
  console.log(`  ${d}  pressure_p95: min ${Math.min(...p.map(x => x.pressure))}  max ${Math.max(...p.map(x => x.pressure))}  极差 ${(Math.max(...p.map(x => x.pressure)) - Math.min(...p.map(x => x.pressure))).toFixed(2)}`);
  const ph = {};
  p.forEach(x => { (ph[x.hour] = ph[x.hour] || []).push(x.pressure); });
  const hourly = Object.keys(ph).map(Number).sort((a, b) => a - b).map(h => mean(ph[h]));
  console.log(`     按小时取均值后的峰谷比 ${(Math.max(...hourly) / Math.min(...hourly)).toFixed(3)}，CV ${(sd(hourly) / mean(hourly)).toFixed(3)}`);
});
const thresholds = [2, 20, 100, 150, 155, 158, 159.7, 160];
console.log('\n== 默认阈值 2.0 下有多少小时会触发推迟（模型 pressure_p95 ≥ 阈值）==');
thresholds.forEach(t => {
  const hit = pred.filter(x => x.pressure >= t).length;
  console.log(`  阈值 ${String(t).padStart(6)}：${hit}/${pred.length} = ${(hit / pred.length * 100).toFixed(1)}% 的小时×站点会被判为高峰`);
});
console.log(`\n（园区级 = 各站取最大）触发小时数：`);
perDate.forEach(d => {
  const p = pred.filter(x => x.date === d);
  const byH = {};
  p.forEach(x => { byH[x.hour] = Math.max(byH[x.hour] || 0, x.pressure); });
  const hours = Object.keys(byH).map(Number).sort((a, b) => a - b);
  [2, 150, 159.6].forEach(t => {
    const hit = hours.filter(h => byH[h] >= t).length;
    console.log(`  ${d} 阈值 ${t}：${hit}/${hours.length} 小时触发`);
  });
});
