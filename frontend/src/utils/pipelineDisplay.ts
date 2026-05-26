import { sortTagNames } from './tagColors';

export const normalizeTagList = (content: unknown): string[] => {
  if (!content) {
    return [];
  }
  if (Array.isArray(content)) {
    return content.filter(Boolean).map((item) => String(item).trim()).filter(Boolean);
  }
  try {
    const parsed = JSON.parse(String(content));
    return Array.isArray(parsed) ? parsed.filter(Boolean).map((item) => String(item).trim()).filter(Boolean) : [];
  } catch {
    return [];
  }
};

export const filterDisplayTags = (tags: unknown, importantTags: unknown): string[] => {
  const importantSet = new Set(normalizeTagList(importantTags));
  return sortTagNames(normalizeTagList(tags).filter((tag) => !importantSet.has(tag)));
};
