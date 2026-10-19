package com.speedywatch.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure ordering/visibility logic for the site picker: an ordered list of
 * site ids plus a hidden set. Sites absent from a stored order keep their
 * stock position; unknown ids are ignored.
 */
final class SiteLayout {
    private SiteLayout() {
    }

    /** Every browsable site in user order; unknown/missing ids are reconciled. */
    static List<SupportedSite> orderedSites(String storedOrder) {
        List<SupportedSite> ordered = new ArrayList<>();
        for (String id : parseIds(storedOrder)) {
            SupportedSite site = siteById(id);
            if (site != null && !ordered.contains(site)) {
                ordered.add(site);
            }
        }
        // Any site the stored order omitted is re-appended in stock position.
        for (SupportedSite site : SupportedSite.browsableValues()) {
            if (!ordered.contains(site)) {
                ordered.add(site);
            }
        }
        return ordered;
    }

    /** Ordered sites minus the hidden ids. */
    static List<SupportedSite> visibleSites(String storedOrder, String storedHidden) {
        Set<String> hidden = parseIds(storedHidden);
        List<SupportedSite> visible = new ArrayList<>();
        for (SupportedSite site : orderedSites(storedOrder)) {
            if (!hidden.contains(site.name())) {
                visible.add(site);
            }
        }
        return visible;
    }

    static boolean isHidden(String storedHidden, SupportedSite site) {
        return site != null && parseIds(storedHidden).contains(site.name());
    }

    /** Serializes the full ordered id list for storage. */
    static String encodeOrder(List<SupportedSite> ordered) {
        StringBuilder out = new StringBuilder();
        for (SupportedSite site : ordered) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(site.name());
        }
        return out.toString();
    }

    static String encodeHidden(List<SupportedSite> hidden) {
        return encodeOrder(hidden);
    }

    static Set<String> parseIds(String stored) {
        Set<String> ids = new LinkedHashSet<>();
        if (stored == null) {
            return ids;
        }
        for (String part : stored.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static SupportedSite siteById(String id) {
        try {
            SupportedSite site = SupportedSite.valueOf(id);
            for (SupportedSite browsable : SupportedSite.browsableValues()) {
                if (browsable == site) {
                    return site;
                }
            }
            return null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
