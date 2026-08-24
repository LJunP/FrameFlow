import type { SemanticModelCatalog, SemanticModelOption } from './types';

/** 在网络边界确认目录字段，避免接口版本漂移被当成“可用模型”静默提交。 */
export function parseSemanticModelCatalog(value: unknown): SemanticModelCatalog | null {
  if (!value || typeof value !== 'object') return null;
  const candidate = value as Record<string, unknown>;
  if (typeof candidate.defaultModelId !== 'string' || !Array.isArray(candidate.models)) return null;

  const models: SemanticModelOption[] = [];
  for (const valueModel of candidate.models) {
    if (!valueModel || typeof valueModel !== 'object') return null;
    const model = valueModel as Record<string, unknown>;
    if (
      typeof model.id !== 'string'
      || typeof model.label !== 'string'
      || typeof model.description !== 'string'
      || typeof model.provider !== 'string'
      || typeof model.model !== 'string'
      || typeof model.enabled !== 'boolean'
    ) {
      return null;
    }
    models.push({
      id: model.id,
      label: model.label,
      description: model.description,
      provider: model.provider,
      model: model.model,
      enabled: model.enabled,
    });
  }

  return { defaultModelId: candidate.defaultModelId, models };
}

/**
 * ★ 核心：浏览器只展示平台目录里明确 enabled 的条目，并去掉空 ID 与重复 ID；
 * 若这里把禁用项放回去，用户会保存一个平台已经拒绝执行的模型配置。
 */
export function listEnabledSemanticModels(
  catalog: SemanticModelCatalog | null,
): SemanticModelOption[] {
  if (!catalog) return [];
  const seenIds = new Set<string>();
  return catalog.models.filter((entry) => {
    if (!entry.enabled || !entry.id.trim() || seenIds.has(entry.id)) return false;
    seenIds.add(entry.id);
    return true;
  });
}

/**
 * 目录刷新时保留仍有效的用户选择；否则只采用服务端声明且当前 enabled 的默认值。
 * 没有可信默认值时返回空串，交给用户明确选择，不在前端猜测或伪造模型。
 */
export function resolveSemanticModelId(
  catalog: SemanticModelCatalog | null,
  currentModelId: string,
): string {
  const enabledModels = listEnabledSemanticModels(catalog);
  if (enabledModels.some((entry) => entry.id === currentModelId)) return currentModelId;
  if (enabledModels.some((entry) => entry.id === catalog?.defaultModelId)) {
    return catalog?.defaultModelId ?? '';
  }
  return '';
}
