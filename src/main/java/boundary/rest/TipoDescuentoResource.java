/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package boundary.rest;

import boundary.rest.messages.ApiExceptionUtil;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.TipoDescuentoDAO;
import entity.TipoDescuento;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import entity.Descuento;
import control.DescuentoDAO;

/**
 *
 * @author erick
 */
/**
 * Recurso REST encargado de exponer las operaciones relacionadas con los tipos
 * de descuento.
 */
@Path("/tipo-descuento")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TipoDescuentoResource {

    @Inject
    private TipoDescuentoDAO tipoDescuentoDAO;
    @Inject
    private DescuentoDAO descuentoDAO;

    private static final Logger LOGGER
            = Logger.getLogger(TipoDescuentoResource.class.getName());

    /**
     * Obtiene todos los tipos de descuento.
     *
     * GET /api/tipo-descuento
     */
    @GET
    public Response findAll() {

        try {

            List<TipoDescuento> lista = tipoDescuentoDAO.findAll();

            return Response.ok(lista).build();

        } catch (Exception ex) {

            LOGGER.log(Level.SEVERE,
                    "Error al consultar los tipos de descuento", ex);

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_ERROR_INTERNO,
                            ApiMensajes.MENSAJE_ERROR_INTERNO,
                            "No fue posible consultar los tipos de descuento"
                    ))
                    .build();
        }
    }

    /**
     * Obtiene un tipo de descuento mediante su identificador.
     *
     * GET /api/tipo-descuento/{id}
     */
    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") String id) {

        UUID uuid;

        try {

            uuid = UUID.fromString(id);

        } catch (IllegalArgumentException | NullPointerException ex) {

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                            ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                            "El identificador proporcionado no es un UUID válido"
                    ))
                    .build();
        }

        try {

            TipoDescuento encontrado
                    = tipoDescuentoDAO.findById(uuid);

            if (encontrado == null) {

                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiResponse(
                                ApiMensajes.CODIGO_NO_ENCONTRADO,
                                ApiMensajes.MENSAJE_NO_ENCONTRADO,
                                "No existe el tipo de descuento solicitado"
                        ))
                        .build();
            }

            return Response.ok(encontrado).build();

        } catch (Exception ex) {

            LOGGER.log(Level.SEVERE,
                    "Error al consultar el tipo de descuento", ex);

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_ERROR_INTERNO,
                            ApiMensajes.MENSAJE_ERROR_INTERNO,
                            "No fue posible consultar el tipo de descuento"
                    ))
                    .build();
        }
    }

    /**
     * Crea un nuevo tipo de descuento.
     *
     * POST /api/tipo-descuento
     */
    @POST
    public Response crear(TipoDescuento registro) {

        if (registro == null) {

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                            ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                            "Debe proporcionar los datos del tipo de descuento"
                    ))
                    .build();
        }

        try {

            if (registro.getIdTipoDescuento() == null) {
                registro.setIdTipoDescuento(UUID.randomUUID());
            }

            tipoDescuentoDAO.crear(registro);

            return Response.status(Response.Status.CREATED)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_CREADO,
                            ApiMensajes.MENSAJE_CREADO,
                            "Tipo de descuento creado con identificador: "
                            + registro.getIdTipoDescuento()
                    ))
                    .build();

        } catch (ConstraintViolationException ex) {

            LOGGER.log(Level.WARNING,
                    "Datos inválidos al crear tipo de descuento", ex);

            return Response.status(422)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_DATOS_INVALIDOS,
                            ApiMensajes.MENSAJE_DATOS_INVALIDOS,
                            "Los datos del tipo de descuento no son válidos"
                    ))
                    .build();

        } catch (PersistenceException ex) {

            LOGGER.log(Level.WARNING,
                    "Conflicto al crear tipo de descuento", ex);

            return Response.status(Response.Status.CONFLICT)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_CONFLICTO,
                            ApiMensajes.MENSAJE_CONFLICTO,
                            "No fue posible crear el tipo de descuento"
                    ))
                    .build();

        } catch (Exception ex) {

            if (ApiExceptionUtil.contieneCausa(
                    ex, PersistenceException.class)) {

                LOGGER.log(Level.WARNING,
                        "Conflicto al crear tipo de descuento", ex);

                return Response.status(Response.Status.CONFLICT)
                        .entity(new ApiResponse(
                                ApiMensajes.CODIGO_CONFLICTO,
                                ApiMensajes.MENSAJE_CONFLICTO,
                                "No fue posible crear el tipo de descuento porque existe un conflicto con los datos enviados"
                        ))
                        .build();
            }

            LOGGER.log(Level.SEVERE,
                    "Error al crear tipo de descuento", ex);

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_ERROR_INTERNO,
                            ApiMensajes.MENSAJE_ERROR_INTERNO,
                            "Ocurrió un error al crear el tipo de descuento"
                    ))
                    .build();
        }
    }

    /**
     * Modifica un tipo de descuento existente.
     *
     * PUT /api/tipo-descuento/{id}
     */
    @PUT
    @Path("/{id}")
    public Response modificar(
            @PathParam("id") String id,
            TipoDescuento registro) {

        UUID uuid;

        try {

            uuid = UUID.fromString(id);

        } catch (IllegalArgumentException | NullPointerException ex) {

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                            ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                            "El identificador proporcionado no es un UUID válido"
                    ))
                    .build();
        }

        if (registro == null) {

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                            ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                            "Debe proporcionar los datos del tipo de descuento"
                    ))
                    .build();
        }

        try {

            TipoDescuento encontrado
                    = tipoDescuentoDAO.findById(uuid);

            if (encontrado == null) {

                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiResponse(
                                ApiMensajes.CODIGO_NO_ENCONTRADO,
                                ApiMensajes.MENSAJE_NO_ENCONTRADO,
                                "No existe el tipo de descuento que desea modificar"
                        ))
                        .build();
            }

            encontrado.setNombre(registro.getNombre());
            encontrado.setActivo(registro.getActivo());
            encontrado.setDescuentoMaximo(
                    registro.getDescuentoMaximo());
            encontrado.setObservaciones(
                    registro.getObservaciones());

            tipoDescuentoDAO.modificar(encontrado);

            return Response.ok(
                    new ApiResponse(
                            ApiMensajes.CODIGO_OK,
                            ApiMensajes.MENSAJE_OK,
                            "Tipo de descuento modificado correctamente"
                    ))
                    .build();

        } catch (ConstraintViolationException ex) {

            LOGGER.log(Level.WARNING,
                    "Datos inválidos al modificar tipo de descuento", ex);

            return Response.status(422)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_DATOS_INVALIDOS,
                            ApiMensajes.MENSAJE_DATOS_INVALIDOS,
                            "Los datos del tipo de descuento no son válidos"
                    ))
                    .build();

        } catch (PersistenceException ex) {

            LOGGER.log(Level.WARNING,
                    "Conflicto al modificar tipo de descuento", ex);

            return Response.status(Response.Status.CONFLICT)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_CONFLICTO,
                            ApiMensajes.MENSAJE_CONFLICTO,
                            "No fue posible modificar el tipo de descuento"
                    ))
                    .build();

        } catch (Exception ex) {

            if (ApiExceptionUtil.contieneCausa(
                    ex, PersistenceException.class)) {

                LOGGER.log(Level.WARNING,
                        "Conflicto al modificar tipo de descuento", ex);

                return Response.status(Response.Status.CONFLICT)
                        .entity(new ApiResponse(
                                ApiMensajes.CODIGO_CONFLICTO,
                                ApiMensajes.MENSAJE_CONFLICTO,
                                "No fue posible modificar el tipo de descuento porque existe un conflicto con los datos enviados"
                        ))
                        .build();
            }

            LOGGER.log(Level.SEVERE,
                    "Error al modificar tipo de descuento", ex);

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_ERROR_INTERNO,
                            ApiMensajes.MENSAJE_ERROR_INTERNO,
                            "Ocurrió un error al modificar el tipo de descuento"
                    ))
                    .build();
        }
    }

    /**
     * Elimina un tipo de descuento existente.
     *
     * DELETE /api/tipo-descuento/{id}
     */
    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {

        UUID uuid;

        try {

            uuid = UUID.fromString(id);

        } catch (IllegalArgumentException | NullPointerException ex) {

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                            ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                            "El identificador proporcionado no es un UUID válido"
                    ))
                    .build();
        }

        try {

            TipoDescuento encontrado
                    = tipoDescuentoDAO.findById(uuid);

            if (encontrado == null) {

                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiResponse(
                                ApiMensajes.CODIGO_NO_ENCONTRADO,
                                ApiMensajes.MENSAJE_NO_ENCONTRADO,
                                "No existe el tipo de descuento que desea eliminar"
                        ))
                        .build();
            }

            tipoDescuentoDAO.eliminar(encontrado);

            return Response.ok(
                    new ApiResponse(
                            ApiMensajes.CODIGO_OK,
                            ApiMensajes.MENSAJE_OK,
                            "Tipo de descuento eliminado correctamente"
                    ))
                    .build();

        } catch (PersistenceException ex) {

            LOGGER.log(Level.WARNING,
                    "Conflicto al eliminar tipo de descuento", ex);

            return Response.status(Response.Status.CONFLICT)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_CONFLICTO,
                            ApiMensajes.MENSAJE_CONFLICTO,
                            "El tipo de descuento no puede eliminarse porque está relacionado con otros registros"
                    ))
                    .build();

        } catch (Exception ex) {

            if (ApiExceptionUtil.contieneCausa(
                    ex, PersistenceException.class)) {

                LOGGER.log(Level.WARNING,
                        "Conflicto al eliminar tipo de descuento", ex);

                return Response.status(Response.Status.CONFLICT)
                        .entity(new ApiResponse(
                                ApiMensajes.CODIGO_CONFLICTO,
                                ApiMensajes.MENSAJE_CONFLICTO,
                                "El tipo de descuento no puede eliminarse porque está relacionado con otros registros"
                        ))
                        .build();
            }

            LOGGER.log(Level.SEVERE,
                    "Error al eliminar tipo de descuento", ex);

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_ERROR_INTERNO,
                            ApiMensajes.MENSAJE_ERROR_INTERNO,
                            "Ocurrió un error al eliminar el tipo de descuento"
                    ))
                    .build();
        }

    }

    /**
     * Obtiene los descuentos asociados a un tipo de descuento.
     *
     * GET /api/tipo-descuento/{id}/descuentos
     */
    @GET
    @Path("/{id}/descuentos")
    public Response findDescuentos(@PathParam("id") String id) {

        UUID uuid;

        try {
            uuid = UUID.fromString(id);

        } catch (IllegalArgumentException | NullPointerException ex) {

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_SOLICITUD_INVALIDA,
                            ApiMensajes.MENSAJE_SOLICITUD_INVALIDA,
                            "El identificador proporcionado no es un UUID válido"
                    ))
                    .build();
        }

        try {

            TipoDescuento tipoDescuento
                    = tipoDescuentoDAO.findById(uuid);

            if (tipoDescuento == null) {

                return Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiResponse(
                                ApiMensajes.CODIGO_NO_ENCONTRADO,
                                ApiMensajes.MENSAJE_NO_ENCONTRADO,
                                "No existe el tipo de descuento solicitado"
                        ))
                        .build();
            }

            List<Descuento> descuentos
                    = descuentoDAO.findByTipoDescuento(uuid);

            return Response.ok(descuentos).build();

        } catch (Exception ex) {

            LOGGER.log(Level.SEVERE,
                    "Error al consultar los descuentos asociados", ex);

            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ApiResponse(
                            ApiMensajes.CODIGO_ERROR_INTERNO,
                            ApiMensajes.MENSAJE_ERROR_INTERNO,
                            "No fue posible consultar los descuentos asociados al tipo de descuento"
                    ))
                    .build();
        }
    }
}
