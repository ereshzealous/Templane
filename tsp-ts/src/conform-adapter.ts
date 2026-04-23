import * as readline from 'node:readline';
import { parse as schemaParse } from './schema-parser';
import { check as typeCheck } from './type-checker';
import { TypedSchema } from './models';

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
      return { output: schemaParse(fixture.yaml as string, (fixture.id as string) ?? 'unknown') };
    }

    if (fixtureId.startsWith('type-checker')) {
      const schema = fixture.schema as TypedSchema;
      const errors = typeCheck(schema, fixture.data as Record<string, unknown>);
      return { output: { errors } };
    }

    // Categories 3-4 added in later tasks
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
