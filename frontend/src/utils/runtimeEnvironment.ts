import type { RuntimeEnvironmentSummary, RuntimeEnvironmentType } from '../types/domain';

export const runtimeEnvironmentTypeLabels: Record<string, string> = {
  JAVA: 'Java',
  NODE: 'Node',
  MAVEN: 'Maven',
};

export const defaultRuntimeEnvironmentTypes: RuntimeEnvironmentType[] = ['JAVA', 'NODE', 'MAVEN'];

export function getRuntimeEnvironmentTypeLabel(type?: string | null) {
  return type ? runtimeEnvironmentTypeLabels[type] || type : '-';
}

export function getRuntimeEnvironmentHomeVariable(type?: string | null) {
  return type ? `$${String(type).toUpperCase()}_HOME` : '$<TYPE>_HOME';
}

export function sortRuntimeEnvironmentTypes(types: string[]) {
  const order = new Map(defaultRuntimeEnvironmentTypes.map((type, index) => [type, index]));
  return [...types].sort((left, right) => {
    const leftOrder = order.get(left);
    const rightOrder = order.get(right);
    if (leftOrder != null || rightOrder != null) {
      return (leftOrder ?? Number.MAX_SAFE_INTEGER) - (rightOrder ?? Number.MAX_SAFE_INTEGER);
    }
    return left.localeCompare(right);
  });
}

export function buildRuntimeEnvironmentTypeOptions(items: RuntimeEnvironmentSummary[] = []) {
  const dynamicTypes = Array.from(new Set(items.map((item) => item.type).filter(Boolean)));
  const allTypes = sortRuntimeEnvironmentTypes(Array.from(new Set([...defaultRuntimeEnvironmentTypes, ...dynamicTypes])));
  return allTypes.map((type) => ({ label: getRuntimeEnvironmentTypeLabel(type), value: type }));
}
