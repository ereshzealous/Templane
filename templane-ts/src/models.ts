export type TemplaneFieldType =
  | { kind: 'string' }
  | { kind: 'number' }
  | { kind: 'boolean' }
  | { kind: 'null' }
  | { kind: 'enum'; values: string[] }
  | { kind: 'list'; item_type: TemplaneFieldType }
  | { kind: 'object'; fields: Record<string, TemplaneField> };

export interface TemplaneField {
  name: string;
  type: TemplaneFieldType;
  required: boolean;
}

export interface TypedSchema {
  id: string;
  fields: Record<string, TemplaneField>;
}

export interface TypeCheckError {
  code: string;
  field: string;
  message: string;
}

export interface Condition {
  op: string;
  left: string;
  right: unknown;
}

export type ASTNode =
  | { kind: 'text'; content: string }
  | { kind: 'expr'; field: string }
  | { kind: 'if'; condition: Condition; then_branch: ASTNode[]; else_branch: ASTNode[] }
  | { kind: 'foreach'; var: string; iterable: string; body: ASTNode[] };

export type TIRNode =
  | { kind: 'text'; content: string }
  | { kind: 'expr'; field: string; resolved: unknown }
  | { kind: 'if'; condition: boolean; branch: TIRNode[] }
  | { kind: 'foreach'; var: string; items: TIRNode[][] };

export interface TIRResult {
  template_id: string;
  schema_id: string;
  nodes: TIRNode[];
}
