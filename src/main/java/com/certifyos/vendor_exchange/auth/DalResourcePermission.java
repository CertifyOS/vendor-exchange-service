package com.certifyos.vendor_exchange.auth;

import java.util.List;

/** One entry of the DAL's permission list: a resource and the actions allowed on it. */
public record DalResourcePermission(String resource, List<String> actions) {
    public DalResourcePermission {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
