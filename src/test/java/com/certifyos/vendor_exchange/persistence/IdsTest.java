package com.certifyos.vendor_exchange.persistence;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void batchIdHasTheDesignShape() {
        Assertions.assertEquals(
                "org-xyz-candor-2026-10-001", Ids.batchId("org-xyz", "candor", YearMonth.of(2026, 10), 1));
        Assertions.assertEquals(
                "org-xyz-candor-2026-10-002", Ids.batchId("org-xyz", "candor", YearMonth.of(2026, 10), 2));
    }

    @Test
    void tenantIdWithUnderscoreIsRefusedBecauseTheFilenameUsesUnderscores() {
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> Ids.batchId("org_xyz", "candor", YearMonth.of(2026, 10), 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Ids.scheduleId("org_xyz", "candor"));
    }

    @Test
    void sequenceOutsideOneToNineNinetyNineIsRefused() {
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> Ids.batchId("org-xyz", "candor", YearMonth.of(2026, 10), 0));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> Ids.batchId("org-xyz", "candor", YearMonth.of(2026, 10), 1000));
    }

    @Test
    void scheduleAndNpiIdsUseThePipeSeparator() {
        Assertions.assertEquals("org-xyz|candor", Ids.scheduleId("org-xyz", "candor"));
        Assertions.assertEquals(
                "org-xyz-candor-2026-10-001|1234567893", Ids.npiId("org-xyz-candor-2026-10-001", "1234567893"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Ids.npiId("b", "12345"));
    }

    @Test
    void correlationIdRoundTripsToTheBatchId() {
        String correlation = Ids.correlationId("org-xyz-candor-2026-10-001", 2);
        Assertions.assertEquals("org-xyz-candor-2026-10-001-r2", correlation);
        Assertions.assertEquals("org-xyz-candor-2026-10-001", Ids.batchIdOf(correlation));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Ids.correlationId("x", 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Ids.batchIdOf("no-suffix"));
    }

    @Test
    void eventIdsAreVersionSevenPrefixedAndSortInCreationOrder() {
        List<String> ids = new ArrayList<>();
        for (int count = 0; count < 1000; count++) {
            ids.add(Ids.eventId());
        }
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(null);
        Assertions.assertEquals(ids, sorted, "v7 ids must sort in creation order");
        Assertions.assertEquals(1000, ids.stream().distinct().count());
        for (String id : ids) {
            Assertions.assertTrue(id.startsWith("ev-"));
            Assertions.assertEquals(7, UUID.fromString(id.substring(3)).version());
        }
    }
}
