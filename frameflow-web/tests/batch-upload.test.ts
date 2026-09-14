import assert from 'node:assert/strict';
import test from 'node:test';
import { browserUploadError, collectVideoFiles, MAX_BROWSER_UPLOAD_BYTES, SIMPLE_UPLOAD_THRESHOLD_BYTES } from '../lib/batch-upload';

test('browser rejects oversized videos before registration', () => {
  assert.match(browserUploadError({ size: MAX_BROWSER_UPLOAD_BYTES + 1, type: 'video/mp4' })!, /200 MiB/);
  assert.equal(browserUploadError({ size: MAX_BROWSER_UPLOAD_BYTES, type: 'video/mp4' }), null);
});
test('simple threshold stays below browser cap so large files register as multipart', () => {
  assert.ok(SIMPLE_UPLOAD_THRESHOLD_BYTES < MAX_BROWSER_UPLOAD_BYTES);
});
test('browser rejects empty and non-video files, permits missing MIME for server inspection', () => {
  assert.ok(browserUploadError({ size: 0, type: 'video/mp4' }));
  assert.ok(browserUploadError({ size: 10, type: 'text/plain' }));
  assert.equal(browserUploadError({ size: 10, type: '' }), null);
});
test('collectVideoFiles queues valid videos and reports rejects', () => {
  const { accepted, rejected } = collectVideoFiles([
    { name: 'ok.mp4', size: 10, type: 'video/mp4' },
    { name: 'empty.mp4', size: 0, type: 'video/mp4' },
    { name: 'note.txt', size: 10, type: 'text/plain' },
  ]);
  assert.equal(accepted.length, 1);
  assert.equal(accepted[0].name, 'ok.mp4');
  assert.equal(rejected.length, 2);
});
