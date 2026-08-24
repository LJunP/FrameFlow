import assert from 'node:assert/strict';
import test from 'node:test';
import {
  buildGatewayUpstreamUrl,
  classifyRefreshStatus,
  safeNextPath,
  sameAuthTeam,
  sameAuthUser,
} from '../lib/security-contracts';

test('safeNextPath preserves normalized workspace destinations', () => {
  assert.equal(safeNextPath('/projects/42?tab=evidence#finding-3'), '/projects/42?tab=evidence#finding-3');
  assert.equal(safeNextPath('/projects/42/../7'), '/projects/7');
});

test('safeNextPath rejects external and encoded path destinations', () => {
  for (const unsafe of [
    null,
    'https://attacker.example/workspace',
    '//attacker.example/workspace',
    '/\\attacker.example/workspace',
    '\\\\attacker.example/workspace',
    '/projects/%5Cattacker.example',
    '/projects/%255Cattacker.example',
    '/projects/name%2F..%2Faccount',
    'javascript:alert(1)',
    '/login',
    '/api/auth/logout',
  ]) {
    assert.equal(safeNextPath(unsafe), '/workspace', String(unsafe));
  }
});

test('gateway builder keeps valid business paths under /api/v1', () => {
  const target = buildGatewayUpstreamUrl(
    'http://127.0.0.1:18080',
    ['teams', '42', 'members'],
    '?size=20',
  );
  assert.equal(target?.href, 'http://127.0.0.1:18080/api/v1/teams/42/members?size=20');
});

test('gateway builder rejects auth and decoded traversal payloads', () => {
  for (const path of [
    ['auth', 'login'],
    ['AUTH', 'refresh'],
    ['%2e%2e', '%2e%2e', 'v3', 'api-docs'],
    ['..', 'actuator', 'health'],
    ['projects/..', 'auth', 'login'],
    ['projects', '\\..'],
    [],
  ]) {
    assert.equal(buildGatewayUpstreamUrl('http://127.0.0.1:18080', path, ''), null, path.join('/'));
  }
});

test('refresh status separates invalid credentials from transient failures', () => {
  assert.equal(classifyRefreshStatus(200), 'authenticated');
  assert.equal(classifyRefreshStatus(400), 'unauthenticated');
  assert.equal(classifyRefreshStatus(401), 'unauthenticated');
  assert.equal(classifyRefreshStatus(429), 'transient-error');
  assert.equal(classifyRefreshStatus(503), 'transient-error');
});

test('principal equality changes only when identity or authorization changes', () => {
  const user = { id: 7, email: 'owner@example.com', displayName: 'Owner' };
  const team = { id: 9, name: 'Studio', role: 'OWNER' };
  assert.equal(sameAuthUser(user, { ...user }), true);
  assert.equal(sameAuthUser(user, { ...user, displayName: 'Renamed' }), false);
  assert.equal(sameAuthTeam(team, { ...team }), true);
  assert.equal(sameAuthTeam(team, { ...team, role: 'VIEWER' }), false);
});
