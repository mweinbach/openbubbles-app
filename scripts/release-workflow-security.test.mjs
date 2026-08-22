import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const workflowPath = fileURLToPath(new URL('../.github/workflows/release-update.yml', import.meta.url));
const releaseSha = 'a'.repeat(40);
const productionSignerSha256 = '285c66e5e2c693dbfa53fb86f0cd7b0bb85ead139cf3cdb5f617bed7be402604';

function workflowStepScript(name) {
  const lines = readFileSync(workflowPath, 'utf8').split('\n');
  const step = lines.findIndex((line) => line === `      - name: ${name}`);
  assert.notEqual(step, -1, `missing workflow step: ${name}`);

  let run = step + 1;
  while (run < lines.length && !lines[run].startsWith('      - name: ')) {
    if (lines[run] === '        run: |') break;
    run++;
  }
  assert.equal(lines[run], '        run: |', `missing executable script for workflow step: ${name}`);

  const script = [];
  for (let line = run + 1; line < lines.length; line++) {
    if (lines[line] === '') {
      script.push('');
      continue;
    }
    if (!lines[line].startsWith('          ')) break;
    script.push(lines[line].slice(10));
  }
  return script.join('\n');
}

function nativeRun({
  id = 17,
  headSha = releaseSha,
  event = 'push',
  status = 'completed',
  conclusion = 'success',
  attempt = 1,
  started = '2026-08-22T12:00:00Z',
  path = '.github/workflows/native.yml',
} = {}) {
  return {
    id,
    head_sha: headSha,
    event,
    status,
    conclusion,
    run_attempt: attempt,
    run_started_at: started,
    created_at: started,
    path,
  };
}

function runNativeValidation(responses, overrides = {}) {
  const temporaryDirectory = mkdtempSync(join(tmpdir(), 'openbubbles-release-validation-'));
  const commandPath = join(temporaryDirectory, 'gh');
  const statePath = join(temporaryDirectory, 'calls');
  const outputPath = join(temporaryDirectory, 'outputs');
  writeFileSync(commandPath, `#!/usr/bin/env node
const fs = require('node:fs');
const path = process.env.MOCK_GH_STATE;
const index = fs.existsSync(path) ? Number(fs.readFileSync(path, 'utf8')) : 0;
const responses = JSON.parse(process.env.MOCK_GH_RESPONSES);
const response = responses[Math.min(index, responses.length - 1)];
fs.writeFileSync(path, String(index + 1));
if (!response || response.exitCode) {
  process.stderr.write(response?.error ?? 'mock GitHub API failure');
  process.exit(response?.exitCode ?? 1);
}
process.stdout.write(JSON.stringify(response));
`);
  chmodSync(commandPath, 0o755);

  try {
    const result = spawnSync('bash', ['-c', workflowStepScript('Wait for Native (Kotlin+Rust) on the release commit')], {
      encoding: 'utf8',
      timeout: 10_000,
      env: {
        ...process.env,
        GH_TOKEN: 'test-token',
        GITHUB_OUTPUT: outputPath,
        MOCK_GH_RESPONSES: JSON.stringify(responses),
        MOCK_GH_STATE: statePath,
        PATH: `${temporaryDirectory}:${process.env.PATH}`,
        RELEASE_REPOSITORY: 'mweinbach/openbubbles-app',
        RELEASE_SHA: releaseSha,
        VALIDATION_POLL_SECONDS: '0',
        VALIDATION_TIMEOUT_SECONDS: '5',
        ...overrides,
      },
    });
    return {
      ...result,
      calls: existsSync(statePath) ? Number(readFileSync(statePath, 'utf8')) : 0,
      outputs: existsSync(outputPath) ? readFileSync(outputPath, 'utf8') : '',
    };
  } finally {
    rmSync(temporaryDirectory, { recursive: true, force: true });
  }
}

function runSignerVerification(certificateOutput, exitCode = 0) {
  const script = workflowStepScript('Stage assets and write update feed');
  const start = script.indexOf('BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort | tail -1)');
  const end = script.indexOf('\nSHA256=', start);
  assert.notEqual(start, -1, 'production signing verification must resolve apksigner');
  assert.notEqual(end, -1, 'production signing verification must complete before artifact publication');

  const temporaryDirectory = mkdtempSync(join(tmpdir(), 'openbubbles-release-signer-'));
  const buildTools = join(temporaryDirectory, 'build-tools', '99.0.0');
  mkdirSync(buildTools, { recursive: true });
  const apksigner = join(buildTools, 'apksigner');
  writeFileSync(apksigner, '#!/bin/sh\nprintf \'%s\\n\' "$MOCK_CERT_OUTPUT"\nexit "$MOCK_CERT_EXIT"\n');
  chmodSync(apksigner, 0o755);

  try {
    return spawnSync(
      'bash',
      ['-euo', 'pipefail', '-c', script.slice(start, end).replaceAll('${{ steps.ver.outputs.asset }}', 'fixture.apk')],
      {
        cwd: temporaryDirectory,
        encoding: 'utf8',
        env: {
          ...process.env,
          ANDROID_HOME: temporaryDirectory,
          MOCK_CERT_EXIT: String(exitCode),
          MOCK_CERT_OUTPUT: certificateOutput,
          PRODUCTION_SIGNER_SHA256: productionSignerSha256,
        },
      },
    );
  } finally {
    rmSync(temporaryDirectory, { recursive: true, force: true });
  }
}

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

test('signed releases require the encrypted Google Maps key and verify the resulting build', () => {
  const workflow = readFileSync(workflowPath, 'utf8');
  const validationStart = workflow.indexOf('      - name: Verify Google Maps release key is configured');
  const buildStart = workflow.indexOf('      - name: Build signed release APK');
  const verificationStart = workflow.indexOf('      - name: Verify Google Maps is enabled in the signed build');

  assert.ok(validationStart >= 0 && validationStart < buildStart, 'missing Google Maps preflight');
  assert.ok(buildStart < verificationStart, 'Google Maps must be verified immediately after building');

  const preflight = workflow.slice(validationStart, buildStart);
  const build = workflow.slice(buildStart, verificationStart);
  const verification = workflow.slice(verificationStart, workflow.indexOf('      - name: ', verificationStart + 1));

  assert.match(preflight, /^\s+MAPS_API_KEY:\s+\$\{\{\s*secrets\.GOOGLE_MAPS_API_KEY\s*\}\}\s*$/m);
  assert.match(preflight, /if \[ -z "\$MAPS_API_KEY" \]; then/);
  assert.match(build, /^\s+MAPS_API_KEY:\s+\$\{\{\s*secrets\.GOOGLE_MAPS_API_KEY\s*\}\}\s*$/m);
  assert.match(verification, /GOOGLE_MAPS_CONFIGURED/);
  assert.match(verification, /true;/);
});

test('release signing depends on least-privilege successful native validation for the exact commit', () => {
  const workflow = readFileSync(workflowPath, 'utf8');
  const gateStart = workflow.indexOf('\n  native_validation:\n');
  const releaseStart = workflow.indexOf('\n  release:\n');
  assert.ok(gateStart >= 0 && gateStart < releaseStart, 'native validation must precede the signing job');

  const validationJob = workflow.slice(gateStart, releaseStart);
  assert.match(validationJob, /permissions:\n\s+actions: read\n\s+contents: read/);
  assert.match(validationJob, /^\s+RELEASE_SHA:\s+\$\{\{\s*github\.sha\s*\}\}\s*$/m);
  assert.match(validationJob, /actions\/workflows\/native\.yml\/runs\?head_sha=\$RELEASE_SHA/);
  assert.match(validationJob, /select\(\.head_sha == \$sha\)/);
  assert.match(validationJob, /\.head_sha == \$sha/);
  assert.match(validationJob, /\.path \| startswith\("\.github\/workflows\/native\.yml"\)/);
  assert.match(workflow.slice(releaseStart), /needs: \[version, native_validation\]/);
  assert.match(workflow.slice(releaseStart), /needs\.native_validation\.result == 'success'/);
  assert.ok(workflow.indexOf('      - name: Restore signing identity') > releaseStart);

  const syntax = spawnSync('bash', ['-n'], {
    encoding: 'utf8',
    input: workflowStepScript('Wait for Native (Kotlin+Rust) on the release commit'),
  });
  assert.equal(syntax.status, 0, syntax.stderr);
});

test('native validation waits for indexing and accepts a successful manual exact-head run', () => {
  const successfulRun = nativeRun({ event: 'workflow_dispatch' });
  const result = runNativeValidation([
    { workflow_runs: [] },
    { workflow_runs: [nativeRun({ id: 99, headSha: 'b'.repeat(40) }), successfulRun] },
    successfulRun,
  ]);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /not indexed yet/);
  assert.match(result.stdout, /Validated release commit/);
  assert.equal(result.outputs, 'run_id=17\nrun_attempt=1\n');
  assert.equal(result.calls, 3);
});

test('native validation rejects a refreshed run for a different commit or workflow', () => {
  const listedRun = nativeRun();
  for (const mismatchedRun of [
    nativeRun({ headSha: 'b'.repeat(40) }),
    nativeRun({ path: '.github/workflows/release-update.yml' }),
  ]) {
    const result = runNativeValidation([{ workflow_runs: [listedRun] }, mismatchedRun]);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /does not belong to release commit/);
    assert.equal(result.outputs, '');
  }
});

test('the newest failed or canceled exact-head native run fails closed', () => {
  for (const conclusion of ['failure', 'cancelled', 'timed_out']) {
    const staleSuccess = nativeRun({ id: 9, started: '2026-08-22T11:00:00Z' });
    const latestFailure = nativeRun({ id: 18, conclusion });
    const result = runNativeValidation([
      { workflow_runs: [staleSuccess, latestFailure] },
      latestFailure,
    ]);

    assert.notEqual(result.status, 0);
    assert.match(result.stderr, new RegExp(`ended with ${conclusion}`));
    assert.equal(result.outputs, '');
  }
});

test('native validation refreshes stale attempts and waits for the current rerun', () => {
  const staleAttempt = nativeRun({ attempt: 1 });
  const runningAttempt = nativeRun({ attempt: 2, status: 'in_progress', conclusion: null });
  const successfulAttempt = nativeRun({ attempt: 2 });
  const result = runNativeValidation([
    { workflow_runs: [staleAttempt] },
    runningAttempt,
    { workflow_runs: [runningAttempt] },
    runningAttempt,
    { workflow_runs: [successfulAttempt] },
    successfulAttempt,
  ]);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /advanced from attempt 1 to 2/);
  assert.match(result.stdout, /is in_progress; waiting/);
  assert.equal(result.outputs, 'run_id=17\nrun_attempt=2\n');
});

test('native validation times out instead of signing without successful exact-head CI', () => {
  const result = runNativeValidation([], { VALIDATION_TIMEOUT_SECONDS: '0' });

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /Timed out waiting for successful Native CI/);
  assert.equal(result.outputs, '');
  assert.equal(result.calls, 0);
});

test('release workflow pins the expected production APK signing certificate', () => {
  const workflow = readFileSync(workflowPath, 'utf8');

  assert.match(workflow, new RegExp(`^\\s+PRODUCTION_SIGNER_SHA256:\\s+${productionSignerSha256}\\s*$`, 'm'));
  assert.doesNotMatch(workflow, /grep -q "certificate SHA-256 digest:" certs\.txt/);
  assert.match(workflow, /\[ "\$SIGNER_COUNT" -ne 1 \]/);
  assert.match(workflow, /\[ "\$SIGNER_DIGESTS" != "\$PRODUCTION_SIGNER_SHA256" \]/);
});

test('production signer comparison normalizes digest case, colons, and whitespace', () => {
  const colonSeparated = productionSignerSha256.toUpperCase().match(/.{2}/g).join(':');
  const result = runSignerVerification(
    `Signer #1 certificate SHA-256 digest:   ${colonSeparated}  \t`,
  );

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Verified production signing certificate SHA-256/);
});

test('release rejects missing, unexpected, multiple, and invalid APK signers', () => {
  for (const [certificateOutput, exitCode] of [
    ['Signer #1 certificate SHA-1 digest: deadbeef', 0],
    [`Signer #1 certificate SHA-256 digest: ${'f'.repeat(64)}`, 0],
    [`Signer #1 certificate SHA-256 digest: ${productionSignerSha256}\nSigner #2 certificate SHA-256 digest: ${productionSignerSha256}`, 0],
    [`Signer #1 certificate SHA-256 digest: ${productionSignerSha256}\nSigner #2 certificate SHA-256 digest: ${'f'.repeat(64)}`, 0],
    [`Signer #1 certificate SHA-256 digest: ${productionSignerSha256}`, 1],
  ]) {
    const result = runSignerVerification(certificateOutput, exitCode);
    assert.notEqual(result.status, 0, `unexpectedly accepted signer output:\n${certificateOutput}`);
  }
});
