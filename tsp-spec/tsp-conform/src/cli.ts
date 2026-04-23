#!/usr/bin/env node
import * as fs from 'fs';
import * as path from 'path';
import { spawn } from 'child_process';

interface Fixture {
  fixture_id: string;
  input: unknown;
  expected_output: unknown;
}

interface AdapterResult {
  name: string;
  passed: number;
  total: number;
  failures: string[];
}

function loadFixtures(fixturesDir: string): Fixture[] {
  const fixtures: Fixture[] = [];

  function walk(dir: string): void {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.name.endsWith('.json')) {
        fixtures.push(JSON.parse(fs.readFileSync(full, 'utf-8')) as Fixture);
      }
    }
  }

  walk(fixturesDir);
  fixtures.sort((a, b) => a.fixture_id.localeCompare(b.fixture_id));
  return fixtures;
}

function deepEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (a === null || b === null) return a === b;
  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length !== b.length) return false;
    return a.every((v, i) => deepEqual(v, b[i]));
  }
  if (typeof a === 'object' && typeof b === 'object') {
    const ao = a as Record<string, unknown>;
    const bo = b as Record<string, unknown>;
    const ak = Object.keys(ao).sort();
    const bk = Object.keys(bo).sort();
    if (ak.length !== bk.length) return false;
    if (JSON.stringify(ak) !== JSON.stringify(bk)) return false;
    return ak.every(k => deepEqual(ao[k], bo[k]));
  }
  return false;
}

async function runAdapter(name: string, command: string, fixtures: Fixture[]): Promise<AdapterResult> {
  let cmd: string;
  let args: string[];

  if (command.endsWith('.jar')) {
    cmd = 'java';
    args = ['-jar', command];
  } else {
    const parts = command.split(' ');
    cmd = parts[0];
    args = parts.slice(1);
  }

  const proc = spawn(cmd, args, { stdio: ['pipe', 'pipe', 'inherit'] });

  let stdout = '';
  proc.stdout!.on('data', (chunk: Buffer) => { stdout += chunk.toString(); });

  for (const fixture of fixtures) {
    const line = JSON.stringify({ fixture_id: fixture.fixture_id, fixture: fixture.input });
    proc.stdin!.write(line + '\n');
  }
  proc.stdin!.end();

  await new Promise<void>((resolve, reject) => {
    proc.on('close', (code) => {
      if (code !== 0 && stdout.trim() === '') {
        reject(new Error(`Adapter "${name}" exited with code ${code} and no output`));
      } else {
        resolve();
      }
    });
    proc.on('error', reject);
  });

  const lines = stdout.trim().split('\n').filter(l => l.trim() !== '');
  let passed = 0;
  const failures: string[] = [];

  for (let i = 0; i < fixtures.length; i++) {
    const fixture = fixtures[i];
    const rawLine = lines[i];

    if (!rawLine) {
      failures.push(`${fixture.fixture_id}: no response from adapter`);
      continue;
    }

    let response: { output?: unknown; error?: string };
    try {
      response = JSON.parse(rawLine) as { output?: unknown; error?: string };
    } catch {
      failures.push(`${fixture.fixture_id}: adapter returned invalid JSON`);
      continue;
    }

    if (deepEqual(response.output, fixture.expected_output)) {
      passed++;
    } else {
      failures.push(
        `${fixture.fixture_id}\n` +
        `    expected: ${JSON.stringify(fixture.expected_output)}\n` +
        `    got:      ${JSON.stringify(response.output)}`
      );
    }
  }

  return { name, passed, total: fixtures.length, failures };
}

async function main(): Promise<void> {
  const argv = process.argv.slice(2);

  const adapterSpecs: { name: string; command: string }[] = [];
  const adaptersIdx = argv.indexOf('--adapters');
  if (adaptersIdx !== -1) {
    let i = adaptersIdx + 1;
    while (i < argv.length && !argv[i].startsWith('--')) {
      const sep = argv[i].indexOf(':');
      if (sep !== -1) {
        adapterSpecs.push({ name: argv[i].substring(0, sep), command: argv[i].substring(sep + 1) });
      }
      i++;
    }
  }

  if (adapterSpecs.length === 0) {
    console.error('Usage: cli.js --adapters "name:command" [...]');
    process.exit(1);
  }

  // Fixtures live two directories up from dist/ → tsp-conform/dist → tsp-conform → tsp-spec → fixtures
  const fixturesDir = path.join(__dirname, '..', '..', 'fixtures');

  if (!fs.existsSync(fixturesDir)) {
    console.error(`Fixtures directory not found: ${fixturesDir}`);
    process.exit(1);
  }

  const fixtures = loadFixtures(fixturesDir);
  console.log(`Running ${fixtures.length} fixture(s) across ${adapterSpecs.length} implementation(s)...\n`);

  let allPassed = true;

  for (const spec of adapterSpecs) {
    try {
      const result = await runAdapter(spec.name, spec.command, fixtures);
      const icon = result.passed === result.total ? '✓' : '✗';
      console.log(`  ${icon} ${result.name}:   ${result.passed}/${result.total}`);
      for (const f of result.failures) {
        console.log(`    - ${f}`);
      }
      if (result.passed < result.total) allPassed = false;
    } catch (err) {
      console.log(`  ✗ ${spec.name}: ${(err as Error).message}`);
      allPassed = false;
    }
  }

  console.log('');
  if (allPassed) {
    console.log('All implementations conformant.');
  } else {
    console.log('Some implementations failed.');
    process.exit(1);
  }
}

main().catch(err => { console.error(err); process.exit(1); });
