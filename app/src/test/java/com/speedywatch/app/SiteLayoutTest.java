package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SiteLayoutTest {

    private static List<String> names(List<SupportedSite> sites) {
        return sites.stream().map(Enum::name).collect(Collectors.toList());
    }

    private static List<String> stock() {
        return names(Arrays.asList(SupportedSite.browsableValues()));
    }

    @Test
    public void orderedSites_emptyKeepsStock() {
        assertEquals(stock(), names(SiteLayout.orderedSites("")));
        assertEquals(stock(), names(SiteLayout.orderedSites(null)));
    }

    @Test
    public void orderedSites_preservedCustomOrder() {
        String first = stock().get(stock().size() - 1);
        String second = stock().get(0);
        List<SupportedSite> ordered =
                SiteLayout.orderedSites(first + "," + second);
        assertEquals(first, ordered.get(0).name());
        assertEquals(second, ordered.get(1).name());
        // Omitted sites are re-appended in stock position after the stored ones.
        List<String> expected = stock();
        expected.removeAll(Arrays.asList(first, second));
        assertEquals(expected, names(ordered).subList(2, ordered.size()));
    }

    @Test
    public void orderedSites_unknownIdsDropped() {
        List<SupportedSite> ordered =
                SiteLayout.orderedSites("YOUTUBE,NOPE," + stock().get(1));
        assertTrue(names(ordered).contains(stock().get(1)));
        for (String name : names(ordered)) {
            assertTrue(stock().contains(name));
        }
        assertEquals(stock().size(), ordered.size());
    }

    @Test
    public void orderedSites_duplicatesCollapse() {
        String first = stock().get(0);
        List<SupportedSite> ordered =
                SiteLayout.orderedSites(first + "," + first);
        assertEquals(1,
                names(ordered).stream().filter(first::equals).count());
        assertEquals(stock().size(), ordered.size());
    }

    @Test
    public void visibleSites_dropsHidden() {
        String hidden = stock().get(0) + "," + stock().get(2);
        List<SupportedSite> visible = SiteLayout.visibleSites("", hidden);
        assertEquals(stock().size() - 2, visible.size());
        assertFalse(names(visible).contains(stock().get(0)));
        assertFalse(names(visible).contains(stock().get(2)));
        assertTrue(names(visible).contains(stock().get(1)));
    }

    @Test
    public void visibleSites_allHiddenEmpty() {
        List<SupportedSite> visible =
                SiteLayout.visibleSites("", String.join(",", stock()));
        assertTrue(visible.isEmpty());
    }

    @Test
    public void visibleSites_keepsCustomOrder() {
        List<String> reversed = stock();
        java.util.Collections.reverse(reversed);
        String order = String.join(",", reversed);
        // Hide the site that leads the custom order; the next stored id wins.
        List<SupportedSite> visible =
                SiteLayout.visibleSites(order, reversed.get(0));
        assertEquals(reversed.get(1), visible.get(0).name());
    }

    @Test
    public void isHidden_matchesIds() {
        String hidden = "YOUTUBE";
        for (SupportedSite site : SupportedSite.browsableValues()) {
            boolean expected = site.name().equals("YOUTUBE");
            assertEquals(expected, SiteLayout.isHidden(hidden, site));
        }
        assertFalse(SiteLayout.isHidden(hidden, null));
        assertFalse(SiteLayout.isHidden("", SupportedSite.browsableValues()[0]));
    }

    @Test
    public void encodeOrder_roundTrips() {
        List<SupportedSite> sites =
                Arrays.asList(SupportedSite.browsableValues());
        String encoded = SiteLayout.encodeOrder(sites);
        assertEquals(sites, SiteLayout.orderedSites(encoded));
    }

    @Test
    public void parseIds_trimsAndSkipsEmpty() {
        assertEquals(2,
                SiteLayout.parseIds(" YOUTUBE , ,X ,, ").size());
        assertTrue(SiteLayout.parseIds(null).isEmpty());
    }
}
