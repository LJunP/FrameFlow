import assert from 'node:assert/strict';
import test from 'node:test';
import { browserUploadError, MAX_BROWSER_UPLOAD_BYTES } from '../lib/batch-upload';

test('browser rejects oversized videos before registration', () => {
  assert.match(browserUploadError({ size: MAX_BROWSER_UPLOAD_BYTES + 1, type: 'video/mp4' })!, /32 MiB/);
  assert.equal(browserUploadError({ size: MAX_BROWSER_UPLOAD_BYTES, type: 'video/mp4' }), null);
});
test('browser rejects empty and non-video files, permits missing MIME for server inspection', () => {
  assert.ok(browserUploadError({ size: 0, type: 'video/mp4' }));
  assert.ok(browserUploadError({ size: 10, type: 'text/plain' }));
  assert.equal(browserUploadError({ size: 10, type: '' }), null);
});
