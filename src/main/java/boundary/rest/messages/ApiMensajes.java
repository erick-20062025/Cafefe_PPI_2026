/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package boundary.rest.messages;

/**
 *
 * @author erick
 */

/**
 * Contiene los códigos y mensajes generales utilizados
 * por los recursos de la API REST.
 *
 * Estos mensajes pueden reutilizarse en diferentes recursos
 * como TipoDescuento, Producto, Rol, Empleado, entre otros.
 */

public final class ApiMensajes {

    // 200 - Operación correcta
    public static final String CODIGO_OK = "API-200";
    public static final String MENSAJE_OK = "Operación realizada correctamente";

    // 201 - Recurso creado
    public static final String CODIGO_CREADO = "API-201";
    public static final String MENSAJE_CREADO = "Recurso creado correctamente";

    // 400 - Solicitud incorrecta
    public static final String CODIGO_SOLICITUD_INVALIDA = "API-400";
    public static final String MENSAJE_SOLICITUD_INVALIDA = "Solicitud inválida";

    // 404 - Recurso no encontrado
    public static final String CODIGO_NO_ENCONTRADO = "API-404";
    public static final String MENSAJE_NO_ENCONTRADO = "Recurso no encontrado";

    // 409 - Conflicto
    public static final String CODIGO_CONFLICTO = "API-409";
    public static final String MENSAJE_CONFLICTO = "Conflicto con el recurso";

    // 422 - Error de validación
    public static final String CODIGO_DATOS_INVALIDOS = "API-422";
    public static final String MENSAJE_DATOS_INVALIDOS
            = "Los datos enviados no cumplen las reglas de validación";

    // 500 - Error interno
    public static final String CODIGO_ERROR_INTERNO = "API-500";
    public static final String MENSAJE_ERROR_INTERNO
            = "Error interno del servidor";

    /**
     * Constructor privado para evitar crear objetos de esta clase.
     */
    private ApiMensajes() {
    }
}
