#!/usr/bin/env node
import * as fs from 'node:fs';
import * as path from 'node:path';
import Handlebars from 'handlebars';
import { compile, TSPHandlebarsError } from './handlebars-tsp';

const USAGE = `Usage:
  xt render <template> <data.json>              Render template with data
  xt check <template> <data.json>               Validate data against template schema
  xt test <templates-dir>                       Compile all .hbs files; render any with adjacent <name>.example.json
  xt dev <template> <data.json>                 Watch template + data; re-render on changes
  xt build <templates-dir> --out <file>         Precompile all .hbs bodies to a single CommonJS module`;

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

function findHbsFiles(dir: string): string[] {
  const out: string[] = [];
  function walk(d: string): void {
    for (const entry of fs.readdirSync(d, { withFileTypes: true })) {
      const full = path.join(d, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith('.hbs')) out.push(full);
    }
  }
  walk(dir);
  return out.sort();
}

function cmdRender(templatePath: string, dataPath: string): void {
  const source = fs.readFileSync(templatePath, 'utf-8');
  const data = readJson(dataPath);
  const tmpl = compile(source, path.basename(templatePath));
  try {
    process.stdout.write(tmpl.render(data));
  } catch (e) {
    if (e instanceof TSPHandlebarsError) die(e.message);
    throw e;
  }
}

function cmdCheck(templatePath: string, dataPath: string): void {
  const source = fs.readFileSync(templatePath, 'utf-8');
  const data = readJson(dataPath);
  const tmpl = compile(source, path.basename(templatePath));
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

function cmdTest(dir: string): void {
  if (!fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) {
    die(`Directory not found: ${dir}`);
  }
  const files = findHbsFiles(dir);
  if (files.length === 0) {
    process.stdout.write('No .hbs templates found\n');
    return;
  }
  let passed = 0;
  let failed = 0;
  for (const file of files) {
    const rel = path.relative(dir, file);
    try {
      const source = fs.readFileSync(file, 'utf-8');
      const tmpl = compile(source, rel);
      const exampleFile = file.replace(/\.hbs$/, '.example.json');
      if (fs.existsSync(exampleFile)) {
        const data = readJson(exampleFile);
        tmpl.render(data);
        process.stdout.write(`  ✓ ${rel} (compiled + rendered example)\n`);
      } else {
        process.stdout.write(`  ✓ ${rel} (compiled)\n`);
      }
      passed++;
    } catch (e) {
      const msg = (e as Error).message.split('\n')[0];
      process.stdout.write(`  ✗ ${rel}: ${msg}\n`);
      failed++;
    }
  }
  process.stdout.write(`\n${passed}/${passed + failed} passed\n`);
  if (failed > 0) process.exit(1);
}

function cmdDev(templatePath: string, dataPath: string): void {
  const render = (): void => {
    try {
      console.clear();
      const source = fs.readFileSync(templatePath, 'utf-8');
      const data = readJson(dataPath);
      const tmpl = compile(source, path.basename(templatePath));
      process.stdout.write(tmpl.render(data));
      process.stdout.write('\n\n[watching — Ctrl+C to exit]\n');
    } catch (e) {
      process.stdout.write(`ERROR: ${(e as Error).message}\n`);
      process.stdout.write('\n[watching — fix and save to re-render]\n');
    }
  };
  render();
  fs.watchFile(templatePath, { interval: 200 }, render);
  fs.watchFile(dataPath, { interval: 200 }, render);
  process.on('SIGINT', () => {
    fs.unwatchFile(templatePath);
    fs.unwatchFile(dataPath);
    process.exit(0);
  });
}

function cmdBuild(dir: string, outFile: string): void {
  if (!fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) {
    die(`Directory not found: ${dir}`);
  }
  const files = findHbsFiles(dir);
  if (files.length === 0) {
    die(`No .hbs templates found in ${dir}`);
  }
  const entries: string[] = [];
  for (const file of files) {
    const rel = path.relative(dir, file).replace(/\\/g, '/');
    const source = fs.readFileSync(file, 'utf-8');
    const sep = '\n---\n';
    const idx = source.indexOf(sep);
    if (idx < 0) die(`Template ${rel} has no body separator '---'`);
    const body = source.substring(idx + sep.length);
    const precompiled = Handlebars.precompile(body);
    const key = rel.replace(/\.hbs$/, '');
    entries.push(`  ${JSON.stringify(key)}: Handlebars.template(${precompiled})`);
  }
  const output = [
    '// Generated by xt build',
    'const Handlebars = require("handlebars/runtime");',
    '',
    'module.exports = {',
    entries.join(',\n'),
    '};',
    '',
  ].join('\n');
  fs.mkdirSync(path.dirname(outFile), { recursive: true });
  fs.writeFileSync(outFile, output);
  process.stdout.write(`Built ${files.length} template(s) → ${outFile}\n`);
}

function main(argv: string[]): void {
  const cmd = argv[0];
  if (!cmd) die(USAGE);

  if (cmd === 'render' || cmd === 'check') {
    if (!argv[1] || !argv[2]) die(USAGE);
    (cmd === 'render' ? cmdRender : cmdCheck)(argv[1], argv[2]);
    return;
  }

  if (cmd === 'test') {
    if (!argv[1]) die(USAGE);
    cmdTest(argv[1]);
    return;
  }

  if (cmd === 'dev') {
    if (!argv[1] || !argv[2]) die(USAGE);
    cmdDev(argv[1], argv[2]);
    return;
  }

  if (cmd === 'build') {
    if (!argv[1]) die(USAGE);
    const outIdx = argv.indexOf('--out');
    if (outIdx < 0 || !argv[outIdx + 1]) die(USAGE);
    cmdBuild(argv[1], argv[outIdx + 1]);
    return;
  }

  die(USAGE);
}

try {
  main(process.argv.slice(2));
} catch (e) {
  die((e as Error).message);
}
