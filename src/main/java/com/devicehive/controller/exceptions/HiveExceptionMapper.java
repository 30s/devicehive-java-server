package com.devicehive.controller.exceptions;


import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import com.devicehive.controller.ResponseFactory;
import com.devicehive.exceptions.HiveException;
import com.devicehive.model.ErrorResponse;

@Provider
public class HiveExceptionMapper implements ExceptionMapper<HiveException> {

    @Override
    public Response toResponse(HiveException exception) {
        Response.Status responseCode = (exception.getCode() != null)
                                        ? Response.Status.fromStatusCode(exception.getCode())
                                        : Response.Status.BAD_REQUEST;
        return ResponseFactory.response(responseCode, new ErrorResponse(exception.getMessage()));
    }
}