package com.example.aikb.common;

import com.example.aikb.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public final class PageRequests {

    private static final int MAX_SIZE = 50;

    private PageRequests() {
    }

    public static Pageable of(int page, int size) {
        if (page < 0) {
            throw new BusinessException("page 不能小于 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException("size 必须在 1 到 " + MAX_SIZE + " 之间");
        }
        return PageRequest.of(page, size);
    }
}
