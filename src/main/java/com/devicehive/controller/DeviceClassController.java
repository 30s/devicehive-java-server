package com.devicehive.controller;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

/**
 * TODO JavaDoc
 */
@Path("/device")
public class DeviceClassController {

    public Response getDeviceList() {
        return Response.ok().build();
    }
}
