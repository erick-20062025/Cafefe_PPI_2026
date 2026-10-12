/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package boundary.rest;

import boundary.rest.messages.ApiExceptionUtil;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import java.io.IOException;

/**
 *
 * @author erick
 */

/**
 * Maneja errores producidos al leer JSON recibido
 * en las solicitudes REST.
 */

@Provider
public class JsonbExceptionMapper
        implements ReaderInterceptor {

    @Override
    public Object aroundReadFrom(
            ReaderInterceptorContext context)
            throws IOException, WebApplicationException {

        try {

            return context.proceed();

        } catch (RuntimeException ex) {

            if (ApiExceptionUtil.contieneCausa(
                    ex, JsonbException.class)) {

                ApiResponse respuesta = new ApiResponse(
                        ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                        ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                        "El cuerpo de la solicitud contiene un JSON vacío o inválido"
                );

                throw new BadRequestException(
                        Response.status(Response.Status.BAD_REQUEST)
                                .type(MediaType.APPLICATION_JSON)
                                .entity(respuesta)
                                .build(),
                        ex
                );
            }

            throw ex;
        }
    }
}