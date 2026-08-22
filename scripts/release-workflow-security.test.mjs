import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const workflowPath = fileURLToPath(new URL('../.github/workflows/release-update.yml', import.meta.url));

test('manual release notes enter the workflow only through a quoted environment variable', () => {
  const workflow = readFileSync(workflowPath, 'utf8');
  const expressions = workflow.match(/\$\{\{\s*github\.event\.inputs\.notes(?:\s*\|\|\s*'')?\s*\}\}/g) ?? [];

  assert.equal(expressions.length, 1, 'release notes must never be interpolated into executable shell');
  assert.match(workflow, /^\s+RELEASE_NOTES:\s+\$\{\{\s*github\.event\.inputs\.notes\s*\|\|\s*''\s*\}\}\s*$/m);
  assert.match(workflow, /if \[ -n "\$RELEASE_NOTES" \]; then/);
  assert.match(workflow, /printf '%s\\n' "\$RELEASE_NOTES" > release-notes\.md/);
});

test('shell metacharacters in release notes are preserved without execution', () => {
  const payload = '$(printf injected)\n`printf injected`\n"; printf injected; #';
  const result = spawnSync(
    'bash',
    ['-c', 'if [ -n "$RELEASE_NOTES" ]; then printf \'%s\\n\' "$RELEASE_NOTES"; fi'],
    {
      encoding: 'utf8',
      env: { ...process.env, RELEASE_NOTES: payload },
    },
  );

  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout, `${payload}\n`);
});
