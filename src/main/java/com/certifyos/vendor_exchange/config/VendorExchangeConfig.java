package com.certifyos.vendor_exchange.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Optional;

/**
 * Deployment configuration, one closed namespace: {@code vendor-exchange.*}. SmallRye validates the
 * whole mapping at startup, so a key without a default that the environment forgets fails the boot
 * instead of running with a wrong value. Keys that are {@link Optional} are the swap-in points from
 * the scaffolding plan (section 2): absent until the external dependency exists, read by code that
 * reports "not configured" rather than failing.
 */
@ConfigMapping(prefix = "vendor-exchange")
public interface VendorExchangeConfig {

    /** Service-wide kill switch: the tick inserts nothing, jobs exit as no-ops, events are acknowledged and ignored. */
    @WithDefault("true")
    boolean enabled();

    /** JobRunr retries per job, exponential backoff. Design doc {@code jobRetries}. */
    @WithDefault("8")
    int jobRetries();

    /** Age after which an untouched non-terminal batch is re-enqueued and alerted. Design doc {@code reconcilerStaleMinutes}. */
    @WithDefault("30")
    int reconcilerStaleMinutes();

    /** Bucket egress copies the finished file to and {@code FinishJob} verifies it in. Design doc {@code vendorBucket}. */
    String vendorBucket();

    /** The egress service (file build and placement). */
    Egress egress();

    /** The DAL, called through IAP for the user and tenant lookup. */
    Dal dal();

    /** api-layer, called with an Auth0 client-credentials token for practitioner selection and templates. */
    ApiLayer apiLayer();

    /** Pub/Sub push delivery of the egress completion event. */
    PubSub pubsub();

    /** Operator permission enforcement. */
    Permissions permissions();

    /** User lookup cache. */
    Auth auth();

    /** Egress settings. */
    interface Egress {
        /** Base URL of the egress service. */
        String url();

        /** IAP OAuth client id of the egress backend, the audience of the Google ID token we send. */
        String iapClientId();

        /** When the first deadline check runs after the egress request. Design doc {@code egressDeadlineHours}. */
        @WithDefault("6")
        int deadlineHours();

        /** When the second check runs and a still-running job is failed. Design doc {@code egressAbandonHours}. */
        @WithDefault("48")
        int abandonHours();
    }

    /** DAL settings. */
    interface Dal {
        /** Base URL of the DAL. */
        String url();

        /** IAP OAuth client id of the DAL backend, the audience of the Google ID token we send. */
        String iapClientId();
    }

    /** api-layer settings. The client id and secret are absent until the Auth0 machine client exists. */
    interface ApiLayer {
        /** Base URL of api-layer. */
        String url();

        /** Auth0 machine-to-machine client id; absent means api-layer calls are not configured yet. */
        Optional<String> clientId();

        /** Auth0 machine-to-machine client secret; absent means api-layer calls are not configured yet. */
        Optional<String> clientSecret();
    }

    /** Pub/Sub push settings. Both absent until the subscription exists; the push endpoint answers 401 meanwhile. */
    interface PubSub {
        /** Service account Pub/Sub pushes with; the {@code email} claim the token must carry. */
        Optional<String> pushServiceAccount();

        /** Audience the push identity token must carry, normally the push endpoint URL. */
        Optional<String> pushAudience();
    }

    /** Permission settings. */
    interface Permissions {
        /**
         * When true, endpoints check the specific action ({@code vendor-export:manage} or {@code read}).
         * When false, tenant membership alone is enough, as in file-ingestion-service.
         */
        @WithDefault("false")
        boolean enforce();
    }

    /** Auth cache settings. */
    interface Auth {
        /** How long a user lookup is cached. */
        @WithDefault("PT5M")
        Duration cacheTtl();

        /** Maximum cached users. */
        @WithDefault("1000")
        int cacheMaxSize();
    }
}
