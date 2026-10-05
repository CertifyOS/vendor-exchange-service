package com.certifyos.vendor_exchange.export.api;

import com.certifyos.vendor_exchange.auth.Permission;
import com.certifyos.vendor_exchange.auth.RequiresPermission;
import com.certifyos.vendor_exchange.auth.UserContext;
import com.certifyos.vendor_exchange.http.Problem;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Operator reads. Both answers are empty lists for now: they exist so the deployed service can
 * prove the whole chain (platform token, tenant membership, permission annotation, problem+json)
 * end to end before the export lane fills them.
 */
@Path("/v1/vendor-exports")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "vendor-exports")
@SecurityRequirement(name = "platform-token")
@APIResponse(
        responseCode = "400",
        description = "tenant-id header missing (TENANT_REQUIRED)",
        content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
@APIResponse(responseCode = "401", description = "No or invalid platform token")
@APIResponse(
        responseCode = "403",
        description =
                "Caller holds no role in the tenant (TENANT_FORBIDDEN, TENANT_UNRESOLVED) or lacks the permission (PERMISSION_DENIED)",
        content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
@APIResponse(
        responseCode = "503",
        description = "User lookup failed (DAL_UNAVAILABLE)",
        content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
public class VendorExportsResource {

    private static final Logger LOG = Logger.getLogger(VendorExportsResource.class);

    private final UserContext ctx;

    public VendorExportsResource(UserContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Lists the tenant's schedules.
     *
     * @return the schedules, empty until schedules are managed through this API
     */
    @GET
    @Path("/schedules")
    @RequiresPermission(Permission.READ)
    @Operation(
            summary = "List export schedules",
            description = "Schedules of the tenant named by the tenant-id header. Empty until schedule "
                    + "management is built.")
    public Items<ScheduleSummary> schedules() {
        LOG.debugf("schedules listed for tenant %s", ctx.tenantId());
        return new Items<>(List.of());
    }

    /**
     * Lists the tenant's export batches.
     *
     * @return the batches, empty until the export lane creates them
     */
    @GET
    @RequiresPermission(Permission.READ)
    @Operation(
            summary = "List export batches",
            description = "Export batches of the tenant named by the tenant-id header. Empty until the "
                    + "export lane creates batches.")
    public Items<BatchSummary> batches() {
        LOG.debugf("batches listed for tenant %s", ctx.tenantId());
        return new Items<>(List.of());
    }
}
