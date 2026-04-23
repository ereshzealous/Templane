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
