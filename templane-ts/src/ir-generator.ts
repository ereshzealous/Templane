import { ASTNode, Condition, TIRNode, TIRResult } from './models';

function resolve(data: Record<string, unknown>, path: string): unknown {
  let current: unknown = data;
  for (const part of path.split('.')) {
    if (typeof current !== 'object' || current === null || Array.isArray(current)) return null;
    const obj = current as Record<string, unknown>;
    if (!(part in obj)) return null;
    current = obj[part];
  }
  return current;
}

function evaluate(condition: Condition, data: Record<string, unknown>): boolean {
  if (condition.op === '==') {
    return String(resolve(data, condition.left)) === String(condition.right);
  }
  return false;
}

export function generate(
  astNodes: ASTNode[],
  data: Record<string, unknown>,
  schemaId: string,
  templateId: string,
): TIRResult {
  return {
    template_id: templateId,
    schema_id: schemaId,
    nodes: astNodes.map(n => nodeToTir(n, data)),
  };
}

function nodeToTir(node: ASTNode, data: Record<string, unknown>): TIRNode {
  switch (node.kind) {
    case 'text':
      return { kind: 'text', content: node.content };
    case 'expr':
      return { kind: 'expr', field: node.field, resolved: resolve(data, node.field) };
    case 'if': {
      const cond = evaluate(node.condition, data);
      const branch = cond ? node.then_branch : node.else_branch;
      return { kind: 'if', condition: cond, branch: branch.map(n => nodeToTir(n, data)) };
    }
    case 'foreach': {
      const itemsVal = resolve(data, node.iterable);
      const items: unknown[] = Array.isArray(itemsVal) ? itemsVal : [];
      return {
        kind: 'foreach',
        var: node.var,
        items: items.map(item => {
          const scope = { ...data, [node.var]: item };
          return node.body.map(n => nodeToTir(n, scope));
        }),
      };
    }
  }
}
