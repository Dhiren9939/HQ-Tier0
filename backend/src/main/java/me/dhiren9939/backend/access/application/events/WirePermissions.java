package me.dhiren9939.backend.access.application.events;

import me.dhiren9939.backend.access.domain.aggregates.apikey.Permissions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The published shape of {@link Permissions}: scope name to level name. Shared by the integration events and the
 * in-process verification view, so it stays independent of how the event mapper encodes anything else.
 */
public final class WirePermissions {

    private WirePermissions() {
    }

    /**
     * Ordered by scope declaration. {@link Permissions} never holds {@code NONE} entries, so the map only contains
     * granted levels; an absent scope means no access. The result is unmodifiable.
     */
    public static Map<String, String> of(Permissions permissions) {
        Map<String, String> wire = new LinkedHashMap<>();
        permissions.asMap().forEach((scope, level) -> wire.put(scope.name(), level.name()));
        return Collections.unmodifiableMap(wire);
    }
}
