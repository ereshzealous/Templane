import * as readline from 'node:readline';
import { parse as schemaParse } from './schema-parser';
import { check as typeCheck } from './type-checker';
import { generate as irGenerate } from './ir-generator';
import { render as htmlRender } from './html-adapter';
import { render as yamlRender } from './yaml-adapter';
import { ASTNode, TIRResult, TypedSchema } from './models';

interface Request {
  fixture_id: string;
  fixture: Record<string, unknown>;
}

interface Response {
  output?: unknown;
  error?: string;
}

function handle(fixtureId: string, fixture: Record<string, unknown>): Response {
  try {
    if (fixtureId.startsWith('schema-parser')) {
      const result = schemaParse(fixture.yaml as string, (fixture.id as string) ?? 'unknown');
      // Normalize TS camelCase to snake_case on the wire for conformance parity.
      if ('error' in result) return { output: result };
      const out: Record<string, unknown> = { schema: result.schema };
      if (result.body !== undefined) out.body = result.body;
      if (result.bodyPath !== undefined) out.body_path = result.bodyPath;
      if (result.engine !== undefined) out.engine = result.engine;
      return { output: out };
    }

    if (fixtureId.startsWith('type-checker')) {
      const schema = fixture.schema as TypedSchema;
      const errors = typeCheck(schema, fixture.data as Record<string, unknown>);
      return { output: { errors } };
    }

    if (fixtureId.startsWith('ir-generator')) {
      const result = irGenerate(
        fixture.ast as ASTNode[],
        fixture.data as Record<string, unknown>,
        fixture.schema_id as string,
        fixture.template_id as string,
      );
      return { output: result };
    }

    if (fixtureId.startsWith('adapters/html')) {
      return { output: { output: htmlRender(fixture.tir as TIRResult) } };
    }

    if (fixtureId.startsWith('adapters/yaml')) {
      return { output: { output: yamlRender(fixture.tir as TIRResult) } };
    }

    return { output: null, error: `Unhandled fixture: ${fixtureId}` };
  } catch (e) {
    return { output: null, error: (e as Error).message };
  }
}

const rl = readline.createInterface({ input: process.stdin, terminal: false });
rl.on('line', (line) => {
  const trimmed = line.trim();
  if (!trimmed) return;
  const req: Request = JSON.parse(trimmed);
  const result = handle(req.fixture_id, req.fixture);
  process.stdout.write(JSON.stringify(result) + '\n');
});
