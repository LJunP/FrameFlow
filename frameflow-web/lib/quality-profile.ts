export type NumericDraft = number | '';

export interface QualityProfileDraft {
  minDurationSeconds: NumericDraft;
  maxDurationSeconds: NumericDraft;
  minWidth: NumericDraft;
  minHeight: NumericDraft;
  minFps: NumericDraft;
  fpsEnabled: boolean;
  semanticEnabled: boolean;
  semanticModelId: string;
  semanticDimensions: {
    promptAlignment: boolean;
    qualityImpression: boolean;
    policyViolation: boolean;
  };
  weights: {
    duration: number;
    resolution: number;
    fps: number;
    promptAlignment: number;
    qualityImpression: number;
    policyViolation: number;
  };
  duplicateHammingThreshold: number;
}

export const defaultQualityProfileDraft: QualityProfileDraft = {
  minDurationSeconds: 5,
  maxDurationSeconds: 30,
  minWidth: '',
  minHeight: '',
  minFps: 24,
  fpsEnabled: false,
  semanticEnabled: false,
  semanticModelId: '',
  semanticDimensions: {
    promptAlignment: true,
    qualityImpression: true,
    policyViolation: true,
  },
  weights: {
    duration: 10,
    resolution: 10,
    fps: 10,
    promptAlignment: 12,
    qualityImpression: 12,
    policyViolation: 16,
  },
  duplicateHammingThreshold: 8,
};

// ★ 核心：在不可变 Profile 发布前守住跨字段业务边界；若这里漏检，错误规则会被永久版本化并改变自动淘汰与排名结果。
export function validateQualityProfileDraft(
  name: string,
  draft: QualityProfileDraft,
  selectableSemanticModelIds: readonly string[] = [],
): string[] {
  const errors: string[] = [];
  if (!name.trim()) errors.push('请填写质检标准名称。');
  if (draft.minDurationSeconds !== '' && draft.minDurationSeconds < 0) errors.push('最短时长不能小于 0 秒。');
  if (draft.maxDurationSeconds !== '' && draft.maxDurationSeconds < 0) errors.push('最长时长不能小于 0 秒。');
  if (
    draft.minDurationSeconds !== '' &&
    draft.maxDurationSeconds !== '' &&
    draft.maxDurationSeconds < draft.minDurationSeconds
  ) {
    errors.push('最长时长不能小于最短时长。');
  }
  if ((draft.minWidth === '') !== (draft.minHeight === '')) errors.push('最小分辨率需要同时填写宽度和高度。');
  if (draft.minWidth !== '' && (draft.minWidth < 1 || draft.minHeight === '' || draft.minHeight < 1)) errors.push('最小分辨率必须是正整数。');
  if (draft.fpsEnabled && (draft.minFps === '' || draft.minFps < 1 || draft.minFps > 240)) errors.push('最低帧率需要在 1–240 fps 之间。');
  if (draft.semanticEnabled && !draft.semanticModelId.trim()) errors.push('启用 AI 辅助复核时，请选择一个可用模型。');
  if (
    draft.semanticEnabled
    && draft.semanticModelId.trim()
    && !selectableSemanticModelIds.includes(draft.semanticModelId)
  ) {
    errors.push('所选 AI 模型当前不可用，请重新选择。');
  }
  if (draft.semanticEnabled && !Object.values(draft.semanticDimensions).some(Boolean)) errors.push('启用 AI 辅助复核时，请至少选择一个复核维度。');
  if (draft.duplicateHammingThreshold < 0 || draft.duplicateHammingThreshold > 64) errors.push('近重复灵敏度需要在 0–64 之间。');
  return errors;
}

// ★ 核心：只在 API 边界把业务草稿翻译成后端/Worker 的稳定 spec；字段名或开关语义改坏会让界面选择与真实检测行为脱节。
export function serializeQualityProfileDraft(draft: QualityProfileDraft): string {
  const dimensions: Record<string, unknown> = {};
  if (draft.minDurationSeconds !== '' || draft.maxDurationSeconds !== '') {
    dimensions.duration = {
      ...(draft.minDurationSeconds !== '' ? { min: draft.minDurationSeconds } : {}),
      ...(draft.maxDurationSeconds !== '' ? { max: draft.maxDurationSeconds } : {}),
    };
  }
  if (draft.minWidth !== '' && draft.minHeight !== '') {
    dimensions.resolution = { minWidth: draft.minWidth, minHeight: draft.minHeight };
  }
  if (draft.fpsEnabled && draft.minFps !== '') {
    dimensions.fps = { min: draft.minFps };
  }

  const weights: Record<string, number> = {
    duration: draft.weights.duration,
    resolution: draft.weights.resolution,
    fps: draft.weights.fps,
  };
  if (draft.semanticEnabled) {
    const semanticDimensions: string[] = [];
    if (draft.semanticDimensions.promptAlignment) semanticDimensions.push('prompt_alignment');
    if (draft.semanticDimensions.qualityImpression) semanticDimensions.push('quality_impression');
    if (draft.semanticDimensions.policyViolation) semanticDimensions.push('policy_violation');
    if (!draft.semanticModelId.trim()) throw new Error('启用 AI 辅助复核时必须选择模型');
    if (!semanticDimensions.length) throw new Error('启用 AI 辅助复核时至少需要一个维度');
    weights.prompt_alignment = draft.weights.promptAlignment;
    weights.quality_impression = draft.weights.qualityImpression;
    weights.policy_violation = draft.weights.policyViolation;
    return JSON.stringify({
      dimensions,
      weights,
      semantic: {
        enabled: true,
        modelId: draft.semanticModelId.trim(),
        dimensions: semanticDimensions,
      },
      duplicates: { hammingThreshold: draft.duplicateHammingThreshold },
    });
  }

  return JSON.stringify({
    dimensions,
    weights,
    duplicates: { hammingThreshold: draft.duplicateHammingThreshold },
  });
}
