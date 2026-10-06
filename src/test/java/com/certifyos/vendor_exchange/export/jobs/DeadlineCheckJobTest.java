package com.certifyos.vendor_exchange.export.jobs;

import com.certifyos.vendor_exchange.clients.EgressClient;
import com.certifyos.vendor_exchange.export.batch.BatchCompletion;
import com.certifyos.vendor_exchange.export.batch.BatchLifecycle;
import java.time.Clock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class DeadlineCheckJobTest {

    @Test
    void withTheServiceDisabledTheCheckFailsForARetryInsteadOfSucceedingAsANoOp() {
        BatchJobSupport support = Mockito.mock(BatchJobSupport.class);
        Mockito.when(support.enabled()).thenReturn(false);
        EgressClient egress = Mockito.mock(EgressClient.class);
        DeadlineCheckJob job = new DeadlineCheckJob(
                support,
                Mockito.mock(BatchCompletion.class),
                Mockito.mock(BatchLifecycle.class),
                Mockito.mock(JobEnqueuer.class),
                egress,
                Clock.systemUTC());

        IllegalStateException deferred = Assertions.assertThrows(
                IllegalStateException.class,
                () -> job.run(new DeadlineCheckJobRequest("org-1-candor-2026-10-001", 1, 1)));

        Assertions.assertTrue(deferred.getMessage().contains("disabled"), deferred.getMessage());
        Mockito.verify(support, Mockito.never())
                .loadExpecting(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
        Mockito.verifyNoInteractions(egress);
    }
}
