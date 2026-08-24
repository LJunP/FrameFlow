import assert from 'node:assert/strict';
import test from 'node:test';
import {
  defaultQualityProfileDraft,
  serializeQualityProfileDraft,
  validateQualityProfileDraft,
  type QualityProfileDraft,
} from '../lib/quality-profile';
import {
  listEnabledSemanticModels,
  parseSemanticModelCatalog,
  resolveSemanticModelId,
} from '../lib/semantic-models';
import type { SemanticModelCatalog } from '../lib/types';

function draft(overrides: Partial<QualityProfileDraft> = {}): QualityProfileDraft {
  return {
    ...defaultQualityProfileDraft,
    semanticDimensions: { ...defaultQualityProfileDraft.semanticDimensions },
    weights: { ...defaultQualityProfileDraft.weights },
    ...overrides,
  };
}

const catalog: SemanticModelCatalog = {
  defaultModelId: 'balanced',
  models: [
    {
      id: 'balanced',
      label: '均衡推荐',
      description: '日常复核默认模型',
      provider: 'provider-a',
      model: 'vision-balanced-1',
      enabled: true,
    },
    {
      id: 'premium',
      label: '高质量',
      description: '复杂视频复核',
      provider: 'provider-b',
      model: 'vision-premium-2',
      enabled: true,
    },
    {
      id: 'retired',
      label: '已停用',
      description: '不能再创建新配置',
      provider: 'provider-a',
      model: 'vision-old',
      enabled: false,
    },
  ],
};

test('model catalog parser accepts only the public response contract', () => {
  assert.deepEqual(parseSemanticModelCatalog(catalog), catalog);
  assert.equal(parseSemanticModelCatalog({ ...catalog, models: [{ id: 'broken' }] }), null);
  assert.equal(parseSemanticModelCatalog({ defaultModelId: 'balanced', models: 'not-an-array' }), null);
});

test('model catalog exposes only enabled unique platform entries', () => {
  const result = listEnabledSemanticModels({
    ...catalog,
    models: [catalog.models[0], catalog.models[2], catalog.models[0]],
  });
  assert.deepEqual(result.map((entry) => entry.id), ['balanced']);
});

test('model resolver preserves a valid choice then uses the enabled server default', () => {
  assert.equal(resolveSemanticModelId(catalog, 'premium'), 'premium');
  assert.equal(resolveSemanticModelId(catalog, ''), 'balanced');
  assert.equal(resolveSemanticModelId(catalog, 'retired'), 'balanced');
});

test('model resolver does not invent a fallback when server default is unusable', () => {
  assert.equal(resolveSemanticModelId({ ...catalog, defaultModelId: 'retired' }, ''), '');
  assert.equal(resolveSemanticModelId(null, 'premium'), '');
});

test('semantic validation requires a currently selectable model', () => {
  assert.deepEqual(
    validateQualityProfileDraft('电商竖版', draft({ semanticEnabled: true }), ['balanced']),
    ['启用 AI 辅助复核时，请选择一个可用模型。'],
  );
  assert.deepEqual(
    validateQualityProfileDraft(
      '电商竖版',
      draft({ semanticEnabled: true, semanticModelId: 'retired' }),
      ['balanced'],
    ),
    ['所选 AI 模型当前不可用，请重新选择。'],
  );
});

test('semantic serialization persists modelId inside semantic policy', () => {
  const spec = JSON.parse(serializeQualityProfileDraft(draft({
    semanticEnabled: true,
    semanticModelId: 'balanced',
  }))) as { semantic: { enabled: boolean; modelId: string; dimensions: string[] } };
  assert.deepEqual(spec.semantic, {
    enabled: true,
    modelId: 'balanced',
    dimensions: ['prompt_alignment', 'quality_impression', 'policy_violation'],
  });
});

test('semantic serialization refuses an enabled policy without a modelId', () => {
  assert.throws(
    () => serializeQualityProfileDraft(draft({ semanticEnabled: true })),
    /必须选择模型/,
  );
});

test('semantic serialization omits stale model data when AI review is disabled', () => {
  const spec = JSON.parse(serializeQualityProfileDraft(draft({
    semanticEnabled: false,
    semanticModelId: 'balanced',
  }))) as Record<string, unknown>;
  assert.equal(Object.hasOwn(spec, 'semantic'), false);
});
