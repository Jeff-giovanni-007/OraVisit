package com.orabank.backend.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class Pageables {

    // Generic across entities, so it cannot be a typed property path.
    private static final String ID = "id";

    private Pageables() {
    }

    public static Pageable stable(Pageable pageable) {
        if (pageable.getSort().getOrderFor(ID) != null || pageable.isUnpaged()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), stable(pageable.getSort()));
    }

    public static Sort stable(Sort sort) {
        if (sort.getOrderFor(ID) != null) {
            return sort;
        }
        Sort.Direction direction = Sort.Direction.ASC;
        for (Sort.Order order : sort) {
            direction = order.getDirection();
        }
        return sort.and(Sort.by(new Sort.Order(direction, ID)));
    }
}
