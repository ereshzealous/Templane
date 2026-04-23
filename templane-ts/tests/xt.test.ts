import { test } from 'node:test';
import * as assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as os from 'node:os';

const TSX_BIN = path.resolve(__dirname, '..', 'node_modules', '.bin', 'tsx');
const XT_SRC = path.resolve(__dirname, '..', 'src', 'xt.ts');

function tempFile(content: string, ext: string): string {
  const file = path.join(
    os.tmpdir(),
    `xt-test-${process.pid}-${Math.random().toString(36).slice(2)}${ext}`,
  );
  fs.writeFileSync(file, content);
  return file;
}

function runXt(args: string[]): { stdout: string; stderr: string; code: number } {
  const r = spawnSync(TSX_BIN, [XT_SRC, ...args], { encoding: 'utf-8' });
  return { stdout: r.stdout, stderr: r.stderr, code: r.status ?? -1 };
}

test('xt render — success', () => {
  const tmpl = tempFile(
    'name:\n  type: string\n  required: true\n---\nHi {{name}}',
    '.hbs',
  );
  const data = tempFile('{"name": "Alice"}', '.json');
  try {
    const r = runXt(['render', tmpl, data]);
    assert.equal(r.code, 0);
    assert.equal(r.stdout, 'Hi Alice');
  } finally {
    fs.unlinkSync(tmpl);
    fs.unlinkSync(data);
  }
});

test('xt render — type error exits non-zero', () => {
  const tmpl = tempFile(
    'age:\n  type: number\n  required: true\n---\n{{age}}',
    '.hbs',
  );
  const data = tempFile('{"age": "old"}', '.json');
  try {
    const r = runXt(['render', tmpl, data]);
    assert.notEqual(r.code, 0);
    assert.ok(r.stderr.includes('type_mismatch'));
  } finally {
    fs.unlinkSync(tmpl);
    fs.unlinkSync(data);
  }
});

test('xt check — valid data', () => {
  const tmpl = tempFile(
    'age:\n  type: number\n  required: true\n---\n{{age}}',
    '.hbs',
  );
  const data = tempFile('{"age": 30}', '.json');
  try {
    const r = runXt(['check', tmpl, data]);
    assert.equal(r.code, 0);
    assert.ok(r.stdout.includes('matches schema'));
  } finally {
    fs.unlinkSync(tmpl);
    fs.unlinkSync(data);
  }
});

test('xt check — invalid data reports errors', () => {
  const tmpl = tempFile(
    'age:\n  type: number\n  required: true\nname:\n  type: string\n  required: true\n---\n{{age}}',
    '.hbs',
  );
  const data = tempFile('{"age": "old"}', '.json');
  try {
    const r = runXt(['check', tmpl, data]);
    assert.notEqual(r.code, 0);
    assert.ok(r.stderr.includes('type_mismatch'));
    assert.ok(r.stderr.includes('missing_required_field'));
  } finally {
    fs.unlinkSync(tmpl);
    fs.unlinkSync(data);
  }
});

test('xt without args shows usage', () => {
  const r = runXt([]);
  assert.notEqual(r.code, 0);
  assert.ok(r.stderr.includes('Usage'));
});

test('xt with unknown command shows usage', () => {
  const tmpl = tempFile('name:\n  type: string\n---\nHi', '.hbs');
  const data = tempFile('{}', '.json');
  try {
    const r = runXt(['bogus', tmpl, data]);
    assert.notEqual(r.code, 0);
    assert.ok(r.stderr.includes('Usage'));
  } finally {
    fs.unlinkSync(tmpl);
    fs.unlinkSync(data);
  }
});

function tempDir(): string {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'xt-test-dir-'));
}

function rmDir(d: string): void {
  fs.rmSync(d, { recursive: true, force: true });
}

test('xt test — valid templates pass', () => {
  const dir = tempDir();
  fs.writeFileSync(
    path.join(dir, 'greet.hbs'),
    'name:\n  type: string\n  required: true\n---\nHi {{name}}',
  );
  try {
    const r = runXt(['test', dir]);
    assert.equal(r.code, 0);
    assert.ok(r.stdout.includes('✓'));
    assert.ok(r.stdout.includes('1/1 passed'));
  } finally {
    rmDir(dir);
  }
});

test('xt test — invalid schema fails', () => {
  const dir = tempDir();
  fs.writeFileSync(path.join(dir, 'bad.hbs'), '- a list\n---\nbody');
  try {
    const r = runXt(['test', dir]);
    assert.notEqual(r.code, 0);
    assert.ok(r.stdout.includes('✗'));
  } finally {
    rmDir(dir);
  }
});

test('xt test — adjacent example.json triggers render', () => {
  const dir = tempDir();
  fs.writeFileSync(
    path.join(dir, 'greet.hbs'),
    'name:\n  type: string\n  required: true\n---\nHi {{name}}',
  );
  fs.writeFileSync(path.join(dir, 'greet.example.json'), '{"name": "World"}');
  try {
    const r = runXt(['test', dir]);
    assert.equal(r.code, 0);
    assert.ok(r.stdout.includes('rendered example'));
  } finally {
    rmDir(dir);
  }
});

test('xt test — example with bad data fails render', () => {
  const dir = tempDir();
  fs.writeFileSync(
    path.join(dir, 'age.hbs'),
    'age:\n  type: number\n  required: true\n---\n{{age}}',
  );
  fs.writeFileSync(path.join(dir, 'age.example.json'), '{"age": "old"}');
  try {
    const r = runXt(['test', dir]);
    assert.notEqual(r.code, 0);
    assert.ok(r.stdout.includes('✗'));
  } finally {
    rmDir(dir);
  }
});

test('xt test — empty dir reports no templates', () => {
  const dir = tempDir();
  try {
    const r = runXt(['test', dir]);
    assert.equal(r.code, 0);
    assert.ok(r.stdout.includes('No .hbs templates'));
  } finally {
    rmDir(dir);
  }
});

test('xt test — recurses into subdirectories', () => {
  const dir = tempDir();
  fs.mkdirSync(path.join(dir, 'sub'));
  fs.writeFileSync(
    path.join(dir, 'sub', 'nested.hbs'),
    'x:\n  type: string\n  required: true\n---\n{{x}}',
  );
  try {
    const r = runXt(['test', dir]);
    assert.equal(r.code, 0);
    assert.ok(r.stdout.includes('sub/nested.hbs'));
  } finally {
    rmDir(dir);
  }
});

test('xt build — produces compiled CommonJS module', () => {
  const dir = tempDir();
  const outFile = path.join(dir, 'dist', 'templates.js');
  fs.writeFileSync(
    path.join(dir, 'greet.hbs'),
    'name:\n  type: string\n  required: true\n---\nHi {{name}}',
  );
  try {
    const r = runXt(['build', dir, '--out', outFile]);
    assert.equal(r.code, 0);
    assert.ok(fs.existsSync(outFile));
    const content = fs.readFileSync(outFile, 'utf-8');
    assert.ok(content.includes('"greet"'));
    assert.ok(content.includes('Handlebars.template'));
    assert.ok(content.includes("handlebars/runtime"));
  } finally {
    rmDir(dir);
  }
});

test('xt build — compiled output is loadable and renders', () => {
  const dir = tempDir();
  const outFile = path.join(dir, 'templates.cjs');
  fs.writeFileSync(
    path.join(dir, 'greet.hbs'),
    'name:\n  type: string\n  required: true\n---\nHello {{name}}!',
  );
  try {
    const r = runXt(['build', dir, '--out', outFile]);
    assert.equal(r.code, 0);
    // Execute the generated module via `node` with cwd inside templane-ts so
    // node_modules resolution finds handlebars/runtime.
    const script = `const t = require(${JSON.stringify(outFile)}); process.stdout.write(t.greet({name:'Alice'}));`;
    const exec = spawnSync('node', ['-e', script], {
      cwd: path.resolve(__dirname, '..'),
      encoding: 'utf-8',
      env: {
        ...process.env,
        NODE_PATH: path.resolve(__dirname, '..', 'node_modules'),
      },
    });
    assert.equal(exec.status, 0);
    assert.equal(exec.stdout, 'Hello Alice!');
  } finally {
    rmDir(dir);
  }
});

test('xt build — requires --out flag', () => {
  const dir = tempDir();
  try {
    const r = runXt(['build', dir]);
    assert.notEqual(r.code, 0);
    assert.ok(r.stderr.includes('Usage'));
  } finally {
    rmDir(dir);
  }
});

test('xt build — fails on empty directory', () => {
  const dir = tempDir();
  const outFile = path.join(dir, 'out.js');
  try {
    const r = runXt(['build', dir, '--out', outFile]);
    assert.notEqual(r.code, 0);
    assert.ok(r.stderr.includes('No .hbs templates'));
  } finally {
    rmDir(dir);
  }
});

test('xt render handles foreach', () => {
  const tmpl = tempFile(
    [
      'items:',
      '  type: list',
      '  items:',
      '    type: string',
      '  required: true',
      '---',
      '{{#each items}}- {{this}}\n{{/each}}',
    ].join('\n'),
    '.hbs',
  );
  const data = tempFile('{"items": ["a", "b"]}', '.json');
  try {
    const r = runXt(['render', tmpl, data]);
    assert.equal(r.code, 0);
    assert.equal(r.stdout, '- a\n- b\n');
  } finally {
    fs.unlinkSync(tmpl);
    fs.unlinkSync(data);
  }
});
