package boundary.rest;

import boundary.rest.dto.OrdenDTO;
import boundary.rest.dto.OrdenSolicitud;
import boundary.rest.messages.ApiMensajes;
import boundary.rest.messages.ApiResponse;
import control.OrdenDAO;
import control.ProductoDAO;
import entity.Descuento;
import entity.EmpleadoRol;
import entity.Orden;
import entity.OrdenProducto;
import entity.Producto;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * API REST para órdenes. No serializa las entidades JPA directamente.
 * El frontend utiliza DTO; la persistencia y reglas finales siguen en OrdenDAO.
 *
 * Las operaciones internas requieren un control de acceso antes de publicar
 * la PWA fuera del entorno de desarrollo.
 */
@Path("/ordenes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrdenResource {

    private static final Logger LOGGER = Logger.getLogger(OrdenResource.class.getName());
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Inject private OrdenDAO ordenDAO;
    @Inject private ProductoDAO productoDAO;

    /** GET /api/ordenes: cabeceras sin relaciones circulares. */
    @GET
    public Response listar() {
        try {
            List<Orden> ordenes = ordenDAO.findAll();
            return Response.ok(resumir(ordenes)).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "consultar las órdenes");
        }
    }

    /** GET /api/ordenes/pendientes: al menos una fila no facturada como PAGADA. */
    @GET
    @Path("/pendientes")
    public Response pendientes() {
        try {
            return Response.ok(resumir(ordenDAO.findPendientesPago())).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "consultar las órdenes pendientes");
        }
    }

    /** GET /api/ordenes/{id}: cabecera con las filas de ESTA orden solamente. */
    @GET
    @Path("/{id}")
    public Response buscar(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = convertirUUID(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de orden no es un UUID válido");
        }
        try {
            Orden orden = ordenDAO.findById(uuid);
            if (orden == null) return error(404, "No existe la orden solicitada");
            return Response.ok(OrdenDTO.detalle(orden, ordenDAO.findProductosByOrden(uuid))).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "consultar la orden");
        }
    }

    /** GET /api/ordenes/{id}/productos: sin relaciones a factura ni a otras órdenes. */
    @GET
    @Path("/{id}/productos")
    public Response productos(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = convertirUUID(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de orden no es un UUID válido");
        }
        try {
            if (ordenDAO.findById(uuid) == null) return error(404, "La orden no existe");
            List<OrdenDTO.ProductoDTO> salida = new ArrayList<>();
            for (OrdenProducto fila : ordenDAO.findProductosByOrden(uuid)) {
                salida.add(OrdenDTO.ProductoDTO.desde(fila));
            }
            return Response.ok(salida).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "consultar los productos de la orden");
        }
    }

    /** GET /api/ordenes/{id}/promociones: solo promociones aplicables a esa orden. */
    @GET
    @Path("/{id}/promociones")
    public Response promociones(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = convertirUUID(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de orden no es un UUID válido");
        }
        try {
            Orden orden = ordenDAO.findById(uuid);
            if (orden == null) return error(404, "La orden no existe");
            if (orden.getFechaCreacion() == null) {
                return error(422, "La orden no tiene una fecha válida");
            }
            List<OrdenDTO.PromocionDTO> respuesta = new ArrayList<>();
            for (Descuento d : ordenDAO.findPromocionesAplicables(uuid, orden.getFechaCreacion())) {
                respuesta.add(new OrdenDTO.PromocionDTO(d.getIdDescuento(), d.getNombre()));
            }
            return Response.ok(respuesta).build();
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "consultar las promociones de la orden");
        }
    }

    /**
     * POST /api/ordenes: una orden completa tomada por un camarero activo.
     * El cliente no puede enviar precios: siempre se usa el catálogo vigente.
     */
    @POST
    public Response crear(String json) {
        OrdenSolicitud solicitud;
        try {
            solicitud = OrdenSolicitud.leer(json, false);
        } catch (OrdenSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        }
        return crearConCamarero(solicitud, solicitud.getIdEmpleadoRol());
    }

    /**
     * POST /api/ordenes/publicas: pedido sin inicio de sesión.
     * El camarero virtual se configura EN EL SERVIDOR, no viene del cliente.
     */
    @POST
    @Path("/publicas")
    public Response crearPublica(String json) {
        OrdenSolicitud solicitud;
        try {
            solicitud = OrdenSolicitud.leer(json, true);
        } catch (OrdenSolicitud.DatoInvalido ex) {
            return error(ex.getEstadoHttp(), ex.getMessage());
        }
        String configurado = camareroVirtualConfigurado();
        if (configurado == null || configurado.isBlank()) {
            return error(503, "El servicio de pedidos en línea no tiene camarero virtual configurado");
        }
        UUID idVirtual;
        try {
            idVirtual = convertirUUID(configurado.trim());
        } catch (IllegalArgumentException ex) {
            LOGGER.severe("CAFEFE_CAMARERO_VIRTUAL_ID no es un UUID válido");
            return error(503, "El servicio de pedidos en línea no está disponible");
        }
        return crearConCamarero(solicitud, idVirtual);
    }

    /** Punto sustituible en pruebas; configuración de Liberty desde server.env. */
    protected String camareroVirtualConfigurado() {
        return System.getenv("CAFEFE_CAMARERO_VIRTUAL_ID");
    }

    /** POST /api/ordenes/{id}/promociones/{idPromocion}. */
    @POST
    @Path("/{id}/promociones/{idPromocion}")
    public Response aplicarPromocion(@PathParam("id") String id,
            @PathParam("idPromocion") String idPromocion) {
        UUID idOrden;
        UUID idDescuento;
        try {
            idOrden = convertirUUID(id);
            idDescuento = convertirUUID(idPromocion);
        } catch (IllegalArgumentException ex) {
            return error(400, "Orden y promoción deben tener UUID válidos");
        }
        try {
            if (ordenDAO.findById(idOrden) == null) {
                return error(404, "La orden no existe");
            }
            OrdenDAO.ResultadoPromocion resultado = ordenDAO.aplicarPromocion(idOrden, idDescuento);
            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("productosAfectados", resultado.getProductosAfectados());
            respuesta.put("descuentoTotal", resultado.getDescuentoTotal());
            respuesta.put("mensaje", "Promoción aplicada correctamente");
            return Response.ok(respuesta).build();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return error(422, ex.getMessage());
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "aplicar la promoción");
        }
    }

    /** DELETE /api/ordenes/{id}: el DAO impide borrar órdenes con productos. */
    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID uuid;
        try {
            uuid = convertirUUID(id);
        } catch (IllegalArgumentException ex) {
            return error(400, "El identificador de orden no es un UUID válido");
        }
        try {
            Orden orden = ordenDAO.findById(uuid);
            if (orden == null) return error(404, "La orden no existe");

            // Comprobación previa: una restricción de negocio en el EJB puede
            // llegar envuelta en una excepción del contenedor. La validación
            // definitiva permanece en OrdenDAO.eliminar para evitar carreras.
            if (ordenDAO.countProductosByOrden(uuid) > 0) {
                return error(422, "No se puede eliminar una orden con productos");
            }

            ordenDAO.eliminar(orden);
            return Response.ok(new ApiResponse(ApiMensajes.CODIGO_OK,
                    ApiMensajes.MENSAJE_OK, "Orden eliminada correctamente")).build();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return error(422, ex.getMessage());
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "eliminar la orden");
        }
    }

    private Response crearConCamarero(OrdenSolicitud solicitud, UUID idCamarero) {
        try {
            EmpleadoRol camarero = ordenDAO.findCamareroActivoById(idCamarero);
            if (camarero == null) {
                return error(422, "El camarero indicado no existe o no está activo");
            }

            // Todos los productos se verifican ANTES de crear la cabecera.
            // No se confía en precio/cantidad manipulados por el navegador.
            Map<UUID, Producto> catalogo = new HashMap<>();
            List<OrdenProducto> filas = new ArrayList<>();
            for (OrdenSolicitud.ProductoSolicitado solicitado : solicitud.getProductos()) {
                Producto producto = catalogo.get(solicitado.getIdProducto());
                if (producto == null) {
                    producto = productoDAO.findById(solicitado.getIdProducto());
                    if (producto == null || !Boolean.TRUE.equals(producto.getActivo())) {
                        return error(422, "La orden contiene un producto inexistente o inactivo");
                    }
                    catalogo.put(solicitado.getIdProducto(), producto);
                }
                BigDecimal precio = producto.getPrecioSugerido();
                if (precio == null || precio.signum() < 0) {
                    return error(422, "Un producto del catálogo tiene precio inválido");
                }
                try {
                    precio = precio.setScale(2, RoundingMode.UNNECESSARY);
                } catch (ArithmeticException ex) {
                    return error(422, "Un precio del catálogo tiene más de dos decimales");
                }

                String observacion = solicitado.getObservaciones();
                if (solicitud.getNombreCliente() != null) {
                    String cliente = "Cliente: " + solicitud.getNombreCliente();
                    observacion = observacion == null ? cliente : cliente + " | " + observacion;
                }
                for (int i = 0; i < solicitado.getCantidad(); i++) {
                    OrdenProducto fila = new OrdenProducto(UUID.randomUUID());
                    fila.setIdProducto(producto);
                    fila.setPrecio(precio);
                    fila.setObservaciones(observacion);
                    filas.add(fila);
                }
            }

            Orden orden = new Orden(UUID.randomUUID());
            orden.setFechaCreacion(solicitud.getFechaCreacion());
            orden.setIdEmpleadoRol(camarero);
            // @Stateless: llamada externa EJB, transacción REQUIRED por defecto.
            // Si falla una fila, la excepción provoca rollback de toda la orden.
            ordenDAO.crearOrdenCompleta(orden, filas);
            return Response.status(Response.Status.CREATED)
                    .entity(OrdenDTO.detalle(orden, filas)).build();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return error(422, ex.getMessage());
        } catch (RuntimeException ex) {
            return errorOperacion(ex, "registrar la orden");
        }
    }

    private static List<OrdenDTO> resumir(List<Orden> ordenes) {
        List<OrdenDTO> salida = new ArrayList<>();
        if (ordenes != null) {
            for (Orden orden : ordenes) salida.add(OrdenDTO.resumen(orden));
        }
        return salida;
    }

    private static UUID convertirUUID(String texto) {
        if (texto == null || !UUID_CANONICO.matcher(texto).matches()) {
            throw new IllegalArgumentException("UUID inválido");
        }
        return UUID.fromString(texto);
    }

    private static Response error(int estado, String detalle) {
        String codigo;
        String mensaje;
        switch (estado) {
            case 400 -> { codigo = ApiMensajes.CODIGO_SOLICITUD_INVALIDA;
                          mensaje = ApiMensajes.MENSAJE_SOLICITUD_INVALIDA; }
            case 404 -> { codigo = ApiMensajes.CODIGO_NO_ENCONTRADO;
                          mensaje = ApiMensajes.MENSAJE_NO_ENCONTRADO; }
            case 409 -> { codigo = ApiMensajes.CODIGO_CONFLICTO;
                          mensaje = ApiMensajes.MENSAJE_CONFLICTO; }
            case 422 -> { codigo = ApiMensajes.CODIGO_DATOS_INVALIDOS;
                          mensaje = ApiMensajes.MENSAJE_DATOS_INVALIDOS; }
            case 503 -> { codigo = "API-503";
                          mensaje = "Servicio no disponible"; }
            default -> { codigo = ApiMensajes.CODIGO_ERROR_INTERNO;
                         mensaje = ApiMensajes.MENSAJE_ERROR_INTERNO; }
        }
        return Response.status(estado).type(MediaType.APPLICATION_JSON)
                .entity(new ApiResponse(codigo, mensaje, detalle)).build();
    }

    private Response errorOperacion(RuntimeException ex, String operacion) {
        LOGGER.log(Level.SEVERE, "Error al " + operacion, ex);
        if (esConflictoIntegridad(ex)) {
            return error(409, "La operación entra en conflicto con los datos relacionados");
        }
        return error(500, "No fue posible " + operacion);
    }

    private static boolean esConflictoIntegridad(Throwable ex) {
        int nivel = 0;
        for (Throwable causa = ex; causa != null && nivel++ < 16; causa = causa.getCause()) {
            if (causa instanceof SQLException sql) {
                String estado = sql.getSQLState();
                if (estado != null && estado.startsWith("23")) return true;
            }
        }
        return false;
    }
}
