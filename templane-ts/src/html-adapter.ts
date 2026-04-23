import { TIRNode, TIRResult } from './models';

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;');
}

export function render(tir: TIRResult): string {
  const header = `<!-- templane template_id=${tir.template_id} schema_id=${tir.schema_id} -->`;
  const body = tir.nodes.map(renderNode).join('');
  return header + '\n' + body;
}

function renderNode(node: TIRNode): string {
  switch (node.kind) {
    case 'text':
      return node.content;
    case 'expr':
      return node.resolved === null ? '' : escapeHtml(String(node.resolved));
    case 'if':
      return node.branch.map(renderNode).join('');
    case 'foreach':
      return node.items.map(item => item.map(renderNode).join('')).join('');
  }
}
