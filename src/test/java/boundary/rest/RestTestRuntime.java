package boundary.rest;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.RuntimeDelegate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.Answers;

import static org.mockito.Mockito.mock;

/**
 * Soporte exclusivo para pruebas unitarias de recursos REST.
 *
 * En Maven/JUnit no se inicia Open Liberty y la API de Jakarta REST no trae
 * una implementacion de RuntimeDelegate. Esta clase crea respuestas minimas
 * para comprobar los codigos HTTP y las entidades devueltas por los recursos.
 * No se incluye en el WAR y no modifica el comportamiento de produccion.
 */
public final class RestTestRuntime {

    private RestTestRuntime() {
    }

    /** Instala un delegado aislado antes de cada prueba. */
    public static void instalar() {
        RuntimeDelegate delegado = mock(RuntimeDelegate.class, invocacion -> {
            if ("createResponseBuilder".equals(invocacion.getMethod().getName())) {
                return nuevoConstructorRespuesta();
            }
            return Answers.RETURNS_DEFAULTS.answer(invocacion);
        });
        RuntimeDelegate.setInstance(delegado);
    }

    /** Quita el delegado simulado para no contaminar otras pruebas. */
    public static void limpiar() {
        RuntimeDelegate.setInstance(null);
    }

    private static Response.ResponseBuilder nuevoConstructorRespuesta() {
        AtomicInteger estado = new AtomicInteger(200);
        AtomicReference<Object> entidad = new AtomicReference<>();

        return mock(Response.ResponseBuilder.class, invocacion -> {
            String metodo = invocacion.getMethod().getName();

            if ("status".equals(metodo)) {
                Object argumento = invocacion.getArgument(0);
                if (argumento instanceof Response.StatusType) {
                    estado.set(((Response.StatusType) argumento).getStatusCode());
                } else if (argumento instanceof Integer) {
                    estado.set((Integer) argumento);
                }
                return invocacion.getMock();
            }

            if ("entity".equals(metodo)) {
                entidad.set(invocacion.getArgument(0));
                return invocacion.getMock();
            }

            if ("build".equals(metodo)) {
                return nuevaRespuesta(estado.get(), entidad.get());
            }

            // Permite encadenar metodos del builder, como type(...),
            // sin necesitar una implementacion completa de JAX-RS.
            if (Response.ResponseBuilder.class.isAssignableFrom(
                    invocacion.getMethod().getReturnType())) {
                return invocacion.getMock();
            }
            return Answers.RETURNS_DEFAULTS.answer(invocacion);
        });
    }

    private static Response nuevaRespuesta(int estado, Object entidad) {
        MultivaluedMap<String, Object> cabeceras = new MultivaluedHashMap<>();
        return mock(Response.class, invocacion -> {
            switch (invocacion.getMethod().getName()) {
                case "getHeaders":
                    return cabeceras;
                case "getStatus":
                    return estado;
                case "getStatusInfo":
                    return Response.Status.fromStatusCode(estado);
                case "getEntity":
                    return entidad;
                case "hasEntity":
                    return entidad != null;
                default:
                    return Answers.RETURNS_DEFAULTS.answer(invocacion);
            }
        });
    }
}
