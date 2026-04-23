#!/usr/bin/env node
import * as fs from 'node:fs';
import * as path from 'node:path';
import { compile, TSPHandlebarsError } from './handlebars-tsp';

const USAGE = `Usage:
  xt render <template> <data.json>    Render template with data
  xt check <template> <data.json>     Validate data against template schema`;

function die(msg: string, code: number = 1): never {
  process.stderr.write(msg + '\n');
  process.exit(code);
}

function readJson(p: string): Record<string, unknown> {
  const raw = fs.readFileSync(p, 'utf-8');
  const parsed = JSON.parse(raw);
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    die(`Data file must be a JSON object: ${p}`);
  }
  return parsed as Record<string, unknown>;
}

function main(argv: string[]): void {
  const cmd = argv[0];
  const templatePath = argv[1];
  const dataPath = argv[2];

  if (!cmd || !templatePath || !dataPath || !['render', 'check'].includes(cmd)) {
    die(USAGE);
  }

  const source = fs.readFileSync(templatePath, 'utf-8');
  const data = readJson(dataPath);
  const schemaId = path.basename(templatePath);

  const tmpl = compile(source, schemaId);

  if (cmd === 'check') {
    const errors = tmpl.check(data);
    if (errors.length === 0) {
      process.stdout.write('✓ data matches schema\n');
      return;
    }
    for (const e of errors) {
      process.stderr.write(`  [${e.code}] ${e.message}\n`);
    }
    process.exit(1);
  }

  // render
  try {
    process.stdout.write(tmpl.render(data));
  } catch (e) {
    if (e instanceof TSPHandlebarsError) {
      die(e.message);
    }
    throw e;
  }
}

try {
  main(process.argv.slice(2));
} catch (e) {
  die((e as Error).message);
}
