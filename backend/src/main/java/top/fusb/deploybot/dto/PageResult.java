package top.fusb.deploybot.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResult<T>(
        List<T> items,
        long total,
        int page,
        int pageSize
) {
    public static <T> PageResult<T> of(List<T> source, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        int fromIndex = Math.min((safePage - 1) * safePageSize, source.size());
        int toIndex = Math.min(fromIndex + safePageSize, source.size());
        return new PageResult<>(source.subList(fromIndex, toIndex), source.size(), safePage, safePageSize);
    }

    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }
}
