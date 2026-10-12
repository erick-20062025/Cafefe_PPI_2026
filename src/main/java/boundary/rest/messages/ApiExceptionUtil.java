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
 * Utilidades para analizar excepciones producidas durante
 * las operaciones de la API REST.
 */
public final class ApiExceptionUtil {

    private ApiExceptionUtil() {
    }

    /**
     * Verifica si una excepción o alguna de sus causas
     * corresponde al tipo indicado.
     *
     * @param throwable excepción recibida
     * @param tipo tipo de excepción que se desea localizar
     * @return true si se encuentra el tipo en la cadena de causas
     */
    public static boolean contieneCausa(
            Throwable throwable,
            Class<? extends Throwable> tipo) {

        Throwable causa = throwable;

        while (causa != null) {

            if (tipo.isInstance(causa)) {
                return true;
            }

            causa = causa.getCause();
        }

        return false;
    }
}
