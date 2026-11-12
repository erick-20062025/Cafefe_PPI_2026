package boundary.rest;

import boundary.rest.dto.PagoDTO;
import boundary.rest.dto.PagoSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.PagoDAO;
import control.PagoDetalleDAO;
import entity.Factura;
import entity.FacturaOrdenProducto;
import entity.PagoDetalle;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * API REST del cajero. Expone solamente operaciones existentes en PagoDAO y
 * PagoDetalleDAO; no modifica FacturaDAO ni serializa entidades JPA completas.
 *
 * IMPORTANTE: los endpoints de cobro requieren autenticación/autorización del
 * personal antes de exponer esta API en Internet.
 */
@Path("/pagos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PagoResource {

    private static final Logger LOGGER = Logger.getLogger(PagoResource.class.getName());
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Inject private PagoDAO pagoDAO;
    @Inject private PagoDetalleDAO detalleDAO;

    /** Facturas pendientes disponibles para el cajero. */
    @GET
    @Path("/facturas/pendientes")
    public Response pendientes() {
        try {
            return Response.ok(facturas(pagoDAO.listarFacturasPendientes())).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar las facturas pendientes");
        }
    }

    /** Facturas pagadas para consultar su historial. */
    @GET
    @Path("/facturas/pagadas")
    public Response pagadas() {
        try {
            return Response.ok(facturas(pagoDAO.listarFacturasPagadas())).build();
        } catch (RuntimeException ex) {
            return errorTecnico(ex, "consultar las facturas pagadas");
        }
    }

    /** GET /api/pagos/facturas/{id}/resumen. */
    @GET
    @Path("/facturas/{id}/resumen")
    public Response resumen(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de factura no es un UUID válido");
        }
        try {
            return Response.ok(new PagoDTO.Resumen(uuid, pagoDAO.consultarResumen(uuid))).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "consultar el saldo de la factura");
        }
    }

    /** Productos y precios de ESTA factura; nunca devuelve órdenes completas. */
    @GET
    @Path("/facturas/{id}/productos")
    public Response productos(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de factura no es un UUID válido");
        }
        try {
            // Garantiza que la factura existe; las listas vacías no significan 404.
            pagoDAO.consultarResumen(uuid);
            List<PagoDTO.ProductoFactura> salida = new ArrayList<>();
            List<FacturaOrdenProducto> filas = pagoDAO.obtenerDetallesFactura(uuid);
            if (filas != null) {
                for (FacturaOrdenProducto fila : filas) {
                    salida.add(new PagoDTO.ProductoFactura(fila));
                }
            }
            return Response.ok(salida).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "consultar los productos de la factura");
        }
    }

    /** Abonos de ESTA factura; no devuelve todas las relaciones de Pago. */
    @GET
    @Path("/facturas/{id}/abonos")
    public Response abonos(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de factura no es un UUID válido");
        }
        try {
            pagoDAO.consultarResumen(uuid);
            List<PagoDTO.Abono> salida = new ArrayList<>();
            List<PagoDetalle> detalles = detalleDAO.findByFactura(uuid);
            if (detalles != null) {
                for (PagoDetalle detalle : detalles) {
                    salida.add(new PagoDTO.Abono(detalle));
                }
            }
            return Response.ok(salida).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "consultar los abonos de la factura");
        }
    }

    /**
     * Registra un abono sobre la factura. Solo PagoDAO decide si corresponde
     * crear Pago, agregar PagoDetalle o completar el saldo.
     */
    @POST
    @Path("/facturas/{id}/abonos")
    public Response registrarAbono(@PathParam("id") String id, String json) {
        UUID uuid;
        try {
            uuid = uuid(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de factura no es un UUID válido");
        }
        PagoSolicitud solicitud;
        try {
            solicitud = PagoSolicitud.leer(json);
        } catch (PagoSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        }
        try {
            PagoDAO.ResumenPago resultado = pagoDAO.registrarAbono(uuid,
                    solicitud.getMonto(), solicitud.getTipoPago(),
                    solicitud.getReferenciaExterna(), solicitud.getObservaciones());
            return Response.status(Response.Status.CREATED)
                    .entity(new PagoDTO.Resumen(uuid, resultado)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "registrar el abono");
        }
    }

    private static List<PagoDTO.FacturaItem> facturas(List<Factura> facturas) {
        List<PagoDTO.FacturaItem> resultado = new ArrayList<>();
        if (facturas != null) {
            for (Factura factura : facturas) {
                resultado.add(new PagoDTO.FacturaItem(factura));
            }
        }
        return resultado;
    }

    private static UUID uuid(String id) {
        if (id == null || !UUID_CANONICO.matcher(id).matches()) {
            throw new IllegalArgumentException("UUID inválido");
        }
        return UUID.fromString(id);
    }

    private static Response error(int estado, String detalle) {
        String codigo;
        String mensaje;
        switch (estado) {
            case 400 -> {
                codigo = ApiMensajes.CODIGO_SOLICITUD_INVALIDA;
                mensaje = ApiMensajes.MENSAJE_SOLICITUD_INVALIDA;
            }
            case 404 -> {
                codigo = ApiMensajes.CODIGO_NO_ENCONTRADO;
                mensaje = ApiMensajes.MENSAJE_NO_ENCONTRADO;
            }
            case 409 -> {
                codigo = ApiMensajes.CODIGO_CONFLICTO;
                mensaje = ApiMensajes.MENSAJE_CONFLICTO;
            }
            case 422 -> {
                codigo = ApiMensajes.CODIGO_DATOS_INVALIDOS;
                mensaje = ApiMensajes.MENSAJE_DATOS_INVALIDOS;
            }
            default -> {
                codigo = ApiMensajes.CODIGO_ERROR_INTERNO;
                mensaje = ApiMensajes.MENSAJE_ERROR_INTERNO;
            }
        }
        return Response.status(estado).type(MediaType.APPLICATION_JSON)
                .entity(new ApiResponse(codigo, mensaje, detalle)).build();
    }

    /**
     * Distingue excepciones de negocio envueltas por EJB de fallos técnicos.
     * Nunca devuelve a HTTP los mensajes de SQL, credenciales o excepciones
     * no reconocidas. La causa completa solo se registra en el servidor.
     */
    private Response errorOperacion(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        int pasosSql = 0;
        for (Throwable actual = ex; actual != null && pasosSql++ < 16;) {
            // Las violaciones de integridad tienen prioridad sobre mensajes
            // internos del proveedor JPA.
            if (actual instanceof SQLException sql) {
                String estado = sql.getSQLState();
                if (estado != null && estado.startsWith("23")) {
                    return error(409, "La operación entra en conflicto con los datos relacionados");
                }
            }
            actual = actual.getCause();
        }
        int profundidad = 0;
        for (Throwable causa = ex; causa != null && profundidad++ < 16;
                causa = causa.getCause()) {
            if (causa instanceof IllegalArgumentException
                    || causa instanceof IllegalStateException) {
                Response negocio = errorNegocio(causa.getMessage());
                if (negocio != null) return negocio;
            }
        }
        return error(500, "No fue posible " + operacion);
    }

    /** Mensajes reconocidos expresamente de PagoDAO; no filtramos texto SQL. */
    private static Response errorNegocio(String mensaje) {
        if (mensaje == null) return null;
        if (mensaje.equals("La factura seleccionada no existe")) {
            return error(404, mensaje);
        }
        if (mensaje.equals("Solo se pueden registrar abonos a facturas pendientes")
                || mensaje.equals("La factura ya está completamente cubierta")
                || mensaje.equals("La referencia de este pago ya fue registrada en la factura")
                || mensaje.equals("El registro de pago existente no admite nuevos abonos")
                || mensaje.equals("La factura tiene varias cabeceras de pago; requiere revisión")
                || mensaje.equals("La factura tiene abonos superiores a su total")
                || mensaje.equals("La factura contiene precios inválidos")
                || mensaje.equals("Los abonos de la factura son inválidos")) {
            return error(409, mensaje);
        }
        if (mensaje.startsWith("El abono supera el saldo pendiente de $")
                || mensaje.equals("La factura no tiene productos con un total positivo")
                || mensaje.equals("El abono debe ser mayor que cero")
                || mensaje.equals("El monto admite como máximo dos decimales")
                || mensaje.equals("Debe seleccionar una factura")
                || mensaje.equals("Debe seleccionar un método de pago")
                || mensaje.equals("El método de pago no es válido")
                || mensaje.startsWith("Debe indicar una referencia para ")) {
            return error(422, mensaje);
        }
        return null;
    }

    private Response errorTecnico(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        return error(500, "No fue posible " + operacion);
    }
}
