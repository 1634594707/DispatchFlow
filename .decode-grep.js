const fs = require("fs");
const file = process.argv[2];
const pattern = process.argv[3];
const b = fs.readFileSync(file);
const text = new TextDecoder("gb18030").decode(b);
const lines = text.split(/\r?\n/);
for (const line of lines) {
  if (!pattern || line.includes(pattern)) {
    process.stdout.write(line + "\n");
  }
}
