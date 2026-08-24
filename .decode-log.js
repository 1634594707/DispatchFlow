const fs = require("fs");
const file = process.argv[2] || "D:/Administrator/Desktop/Project/DispatchFlow/back-target15.log";
const b = fs.readFileSync(file);
const dec = new TextDecoder("gb18030");
process.stdout.write(dec.decode(b));
