import { test } from 'node:test';
import * as assert from 'node:assert/strict';
import { generate } from '../src/ir-generator';
import { ASTNode } from '../src/models';

test('basic expr', () => {
  const ast: ASTNode[] = [
    { kind: 'text', content: 'Hello ' },
    { kind: 'expr', field: 'name' },
    { kind: 'text', content: '!' },
  ];
  const result = generate(ast, { name: 'Alice' }, 'user', 'greeting');
  assert.deepEqual(result.nodes[1], { kind: 'expr', field: 'name', resolved: 'Alice' });
});

test('missing path resolves to null', () => {
  const ast: ASTNode[] = [{ kind: 'expr', field: 'missing' }];
  const result = generate(ast, {}, 's', 't');
  assert.deepEqual(result.nodes[0], { kind: 'expr', field: 'missing', resolved: null });
});

test('if true picks then branch', () => {
  const ast: ASTNode[] = [
    {
      kind: 'if',
      condition: { op: '==', left: 'status', right: 'active' },
      then_branch: [{ kind: 'text', content: 'Active' }],
      else_branch: [{ kind: 'text', content: 'Inactive' }],
    },
  ];
  const result = generate(ast, { status: 'active' }, 's', 't');
  const node = result.nodes[0];
  assert.equal(node.kind, 'if');
  if (node.kind !== 'if') return;
  assert.equal(node.condition, true);
  assert.deepEqual(node.branch, [{ kind: 'text', content: 'Active' }]);
});

test('if false picks else branch', () => {
  const ast: ASTNode[] = [
    {
      kind: 'if',
      condition: { op: '==', left: 'status', right: 'active' },
      then_branch: [{ kind: 'text', content: 'Active' }],
      else_branch: [],
    },
  ];
  const result = generate(ast, { status: 'inactive' }, 's', 't');
  const node = result.nodes[0];
  assert.equal(node.kind, 'if');
  if (node.kind !== 'if') return;
  assert.equal(node.condition, false);
  assert.deepEqual(node.branch, []);
});

test('foreach renders each item', () => {
  const ast: ASTNode[] = [
    { kind: 'foreach', var: 'tag', iterable: 'tags', body: [{ kind: 'expr', field: 'tag' }] },
  ];
  const result = generate(ast, { tags: ['py', 'ts', 'java'] }, 's', 't');
  const node = result.nodes[0];
  assert.equal(node.kind, 'foreach');
  if (node.kind !== 'foreach') return;
  assert.equal(node.items.length, 3);
  assert.deepEqual(node.items[0], [{ kind: 'expr', field: 'tag', resolved: 'py' }]);
  assert.deepEqual(node.items[1], [{ kind: 'expr', field: 'tag', resolved: 'ts' }]);
});

test('nested dotted path', () => {
  const ast: ASTNode[] = [{ kind: 'expr', field: 'user.address.city' }];
  const result = generate(ast, { user: { address: { city: 'London' } } }, 's', 't');
  assert.deepEqual(result.nodes[0], {
    kind: 'expr',
    field: 'user.address.city',
    resolved: 'London',
  });
});

test('nested path missing segment returns null', () => {
  const ast: ASTNode[] = [{ kind: 'expr', field: 'user.address.city' }];
  const result = generate(ast, { user: {} }, 's', 't');
  const node = result.nodes[0];
  assert.equal(node.kind, 'expr');
  if (node.kind !== 'expr') return;
  assert.equal(node.resolved, null);
});

test('condition equals', () => {
  const ast: ASTNode[] = [
    {
      kind: 'if',
      condition: { op: '==', left: 'score', right: '100' },
      then_branch: [{ kind: 'text', content: 'Perfect' }],
      else_branch: [],
    },
  ];
  const result = generate(ast, { score: '100' }, 's', 't');
  const node = result.nodes[0];
  assert.equal(node.kind, 'if');
  if (node.kind !== 'if') return;
  assert.equal(node.condition, true);
});

test('provenance', () => {
  const result = generate([], {}, 'my-schema', 'my-template');
  assert.equal(result.schema_id, 'my-schema');
  assert.equal(result.template_id, 'my-template');
});
