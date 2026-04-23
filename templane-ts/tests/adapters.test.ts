import { test } from 'node:test';
import * as assert from 'node:assert/strict';
import { render as htmlRender } from '../src/html-adapter';
import { render as yamlRender } from '../src/yaml-adapter';
import { TIRNode, TIRResult } from '../src/models';

function tirOf(nodes: TIRNode[], templateId = 't', schemaId = 's'): TIRResult {
  return { template_id: templateId, schema_id: schemaId, nodes };
}

test('html basic', () => {
  const tir = tirOf(
    [
      { kind: 'text', content: 'Hello ' },
      { kind: 'expr', field: 'name', resolved: 'Alice' },
      { kind: 'text', content: '!' },
    ],
    'greeting',
    'user',
  );
  assert.equal(
    htmlRender(tir),
    '<!-- templane template_id=greeting schema_id=user -->\nHello Alice!',
  );
});

test('html escapes special chars', () => {
  const tir = tirOf(
    [{ kind: 'expr', field: 'content', resolved: '<b>Hello & World</b>' }],
    'escape',
    'data',
  );
  const result = htmlRender(tir);
  assert.ok(result.includes('&lt;b&gt;Hello &amp; World&lt;/b&gt;'));
});

test('html does not escape text nodes', () => {
  const tir = tirOf([{ kind: 'text', content: '<li>item</li>' }]);
  assert.ok(htmlRender(tir).includes('<li>item</li>'));
});

test('html provenance comment', () => {
  const tir = tirOf([{ kind: 'text', content: 'Hello' }], 'my-template', 'my-schema');
  assert.ok(
    htmlRender(tir).startsWith('<!-- templane template_id=my-template schema_id=my-schema -->'),
  );
});

test('html falsy zero renders as string', () => {
  const tir = tirOf(
    [
      { kind: 'text', content: 'Count: ' },
      { kind: 'expr', field: 'count', resolved: 0 },
    ],
    'counter',
    'stats',
  );
  assert.ok(htmlRender(tir).includes('Count: 0'));
});

test('html null resolves to empty', () => {
  const tir = tirOf([
    { kind: 'text', content: 'X=' },
    { kind: 'expr', field: 'x', resolved: null },
  ]);
  const result = htmlRender(tir);
  assert.ok(result.includes('X='));
  assert.ok(!result.includes('null'));
});

test('html foreach', () => {
  const tir = tirOf(
    [
      {
        kind: 'foreach',
        var: 'item',
        items: [
          [
            { kind: 'text', content: '<li>' },
            { kind: 'expr', field: 'item', resolved: 'apple' },
            { kind: 'text', content: '</li>' },
          ],
          [
            { kind: 'text', content: '<li>' },
            { kind: 'expr', field: 'item', resolved: 'banana' },
            { kind: 'text', content: '</li>' },
          ],
        ],
      },
    ],
    'list',
    'data',
  );
  assert.ok(htmlRender(tir).includes('<li>apple</li><li>banana</li>'));
});

test('yaml basic', () => {
  const tir = tirOf(
    [
      { kind: 'text', content: 'name: ' },
      { kind: 'expr', field: 'name', resolved: 'Alice' },
    ],
    'greeting',
    'user',
  );
  assert.equal(yamlRender(tir), '# templane template_id=greeting schema_id=user\nname: Alice');
});

test('yaml does not escape', () => {
  const tir = tirOf([{ kind: 'expr', field: 'content', resolved: '<b>Hello</b>' }]);
  assert.ok(yamlRender(tir).includes('<b>Hello</b>'));
});

test('yaml provenance comment', () => {
  const tir = tirOf([{ kind: 'text', content: 'Hello' }], 'my-template', 'my-schema');
  assert.ok(yamlRender(tir).startsWith('# templane template_id=my-template schema_id=my-schema'));
});
