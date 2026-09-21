#!/usr/bin/env node
// Guard for "never edit an applied Flyway migration": recompute each migration's
// Flyway checksum from disk and compare it against flyway_schema_history.
//
//   docker exec fsd-mysql sh -c 'mysql -N -B ... "SELECT version,checksum FROM flyway_schema_history"' \
//     | node scripts/check-migration-checksums.js
//
// With no stored rows on stdin it prints the computed checksums as TSV instead.
const fs = require("fs");
const path = require("path");
const { crc32 } = require("zlib");

const MIGRATIONS_DIR = path.join(__dirname, "..", "back", "sql", "migrations");

// Flyway checksums SQL migrations line-by-line so CRLF/LF differences are ignored:
// it is a CRC32 over the UTF-8 bytes with every line terminator removed.
function flywayChecksum(buffer) {
  let bytes = buffer;
  if (bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) bytes = bytes.slice(3);
  const stripped = Buffer.from(Array.from(bytes).filter((b) => b !== 0x0a && b !== 0x0d));
  return crc32(stripped, 0) | 0;
}

// Flyway's version parsing: V<version>__<description>.sql, dots are sub-versions.
function parseVersion(fileName) {
  const match = /^V([\d.\w]+?)__[^/\\]+\.sql$/.exec(fileName);
  return match ? match[1] : null;
}

function compute() {
  const rows = new Map();
  for (const name of fs.readdirSync(MIGRATIONS_DIR).sort()) {
    if (!name.startsWith("V") || !name.endsWith(".sql")) continue;
    const version = parseVersion(name);
    if (!version) {
      console.error(`[WARN] unparseable migration file name: ${name}`);
      continue;
    }
    rows.set(version, {
      file: path.relative(process.cwd(), path.join(MIGRATIONS_DIR, name)),
      checksum: flywayChecksum(fs.readFileSync(path.join(MIGRATIONS_DIR, name))),
    });
  }
  return rows;
}

function readStored(input) {
  const stored = new Map();
  for (const line of input.split(/\r?\n/)) {
    if (!line.trim()) continue;
    const [version, checksum] = line.split("\t");
    if (!version || checksum === undefined) continue;
    stored.set(version.trim(), Number(checksum));
  }
  return stored;
}

function main() {
  const migrations = compute();
  const input = fs.readFileSync(0, "utf8");

  if (!input.trim()) {
    for (const [version, row] of migrations) console.log(`${version}\t${row.checksum}\t${row.file}`);
    return 0;
  }

  const stored = readStored(input);
  const drift = [];
  const missing = [];
  for (const [version, expected] of stored) {
    if (expected === null || Number.isNaN(expected)) continue; // baseline rows carry NULL
    const onDisk = migrations.get(version);
    if (!onDisk) {
      missing.push(version);
      continue;
    }
    if (onDisk.checksum !== expected) drift.push({ version, file: onDisk.file, expected, actual: onDisk.checksum });
  }

  if (!drift.length && !missing.length) {
    console.log(`OK: ${stored.size} applied migrations match their files on disk.`);
    return 0;
  }
  for (const d of drift) {
    console.error(
      `DRIFT V${d.version} (${d.file}): applied checksum ${d.expected}, file now ${d.actual}. ` +
        `Flyway validate will fail with "Migration checksum mismatch".`
    );
  }
  for (const v of missing) {
    console.error(`MISSING V${v}: applied in this database but no file found under back/sql/migrations.`);
  }
  console.error(
    "\nAn applied migration must never be edited. Revert it with\n" +
      "  git checkout HEAD -- <file>\n" +
      "and express the intended change as a new migration instead."
  );
  return 1;
}

process.exit(main());
