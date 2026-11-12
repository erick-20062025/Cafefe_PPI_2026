package boundary.rest;

import boundary.rest.messages.ApiResponse;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Casos negativos de lectura JSON. La deserialización real mediante HTTP
 * requiere además una prueba de integración en Open Liberty.
 */
class JsonbExceptionMapperTest {

    /** Prepara el constructor HTTP que falta fuera de Open Liberty. */
    @BeforeEach
    void prepararRuntimeRest() {
        RestTestRuntime.instalar();
    }

    @AfterEach
    void limpiarRuntimeRest() {
        RestTestRuntime.limpiar();
    }


    private final JsonbExceptionMapper interceptor = new JsonbExceptionMapper();

    @Test
    void jsonValidoContinuaSinCambiarElResultado() throws Exception {
        ReaderInterceptorContext contexto = mock(ReaderInterceptorContext.class);
        Object objeto = new Object();
        when(contexto.proceed()).thenReturn(objeto);
        assertSame(objeto, interceptor.aroundReadFrom(contexto));
        verify(contexto, times(1)).proceed();
    }

    @Test
    void jsonInvalidoGenera400ConRespuestaUniforme() throws Exception {
        ReaderInterceptorContext contexto = mock(ReaderInterceptorContext.class);
        when(contexto.proceed()).thenThrow(new JsonbException("dato sensible"));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> interceptor.aroundReadFrom(contexto));

        assertEquals(400, ex.getResponse().getStatus());
        ApiResponse respuesta = (ApiResponse) ex.getResponse().getEntity();
        assertEquals("API-400", respuesta.getCodigo());
        assertFalse(respuesta.getDetalle().contains("dato sensible"));
    }

    @Test
    void jsonbAnidadoTambienSeMapeaA400() throws Exception {
        ReaderInterceptorContext contexto = mock(ReaderInterceptorContext.class);
        when(contexto.proceed()).thenThrow(
                new IllegalArgumentException("envoltura", new JsonbException("fallo")));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> interceptor.aroundReadFrom(contexto));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void falloAjenoAJsonbNoSeTransformaEn400() throws Exception {
        ReaderInterceptorContext contexto = mock(ReaderInterceptorContext.class);
        IllegalStateException original = new IllegalStateException("otro error");
        when(contexto.proceed()).thenThrow(original);
        assertSame(original, assertThrows(IllegalStateException.class,
                () -> interceptor.aroundReadFrom(contexto)));
    }

    @Test
    void errorDeLecturaIONoSeTransformaEnJSONInvalido() throws Exception {
        ReaderInterceptorContext contexto = mock(ReaderInterceptorContext.class);
        IOException original = new IOException("sin lectura");
        when(contexto.proceed()).thenThrow(original);
        assertSame(original, assertThrows(IOException.class,
                () -> interceptor.aroundReadFrom(contexto)));
    }

    @Test
    void numeroEnNombreEsRechazadoAntesDeJsonb() {
        jakarta.json.JsonObject json = mock(jakarta.json.JsonObject.class);
        jakarta.json.JsonValue numero = mock(jakarta.json.JsonValue.class);
        when(numero.getValueType()).thenReturn(jakarta.json.JsonValue.ValueType.NUMBER);
        when(json.get("nombre")).thenReturn(numero);
        assertTrue(JsonbExceptionMapper.validarTiposTipoDescuento(json)
                .contains("nombre"));
    }

    @Test
    void textoEnActivoEsRechazadoAntesDeJsonb() {
        jakarta.json.JsonObject json = mock(jakarta.json.JsonObject.class);
        jakarta.json.JsonValue texto = mock(jakarta.json.JsonValue.class);
        when(texto.getValueType()).thenReturn(jakarta.json.JsonValue.ValueType.STRING);
        when(json.get("activo")).thenReturn(texto);
        assertTrue(JsonbExceptionMapper.validarTiposTipoDescuento(json)
                .contains("activo"));
    }

    @Test
    void decimalEnDescuentoMaximoEsRechazadoAntesDeJsonb() {
        jakarta.json.JsonObject json = mock(jakarta.json.JsonObject.class);
        jakarta.json.JsonNumber decimal = mock(jakarta.json.JsonNumber.class);
        when(decimal.getValueType()).thenReturn(jakarta.json.JsonValue.ValueType.NUMBER);
        when(decimal.isIntegral()).thenReturn(false);
        when(json.get("descuentoMaximo")).thenReturn(decimal);
        assertTrue(JsonbExceptionMapper.validarTiposTipoDescuento(json)
                .contains("sin decimales"));
    }

    @Test
    void enteroFueraDeRangoSeRechaza() {
        jakarta.json.JsonObject json = mock(jakarta.json.JsonObject.class);
        jakarta.json.JsonNumber numero = mock(jakarta.json.JsonNumber.class);
        when(numero.getValueType()).thenReturn(jakarta.json.JsonValue.ValueType.NUMBER);
        when(numero.isIntegral()).thenReturn(true);
        when(numero.bigDecimalValue()).thenReturn(new java.math.BigDecimal("99999999999999"));
        when(json.get("descuentoMaximo")).thenReturn(numero);
        assertTrue(JsonbExceptionMapper.validarTiposTipoDescuento(json)
                .contains("fuera del rango"));
    }

    @Test
    void camposAusentesONulosLosValidaElResource() {
        jakarta.json.JsonObject json = mock(jakarta.json.JsonObject.class);
        when(json.get("nombre")).thenReturn(jakarta.json.JsonValue.NULL);
        when(json.get("activo")).thenReturn(jakarta.json.JsonValue.NULL);
        when(json.get("descuentoMaximo")).thenReturn(jakarta.json.JsonValue.NULL);
        assertNull(JsonbExceptionMapper.validarTiposTipoDescuento(json));
    }

    @Test
    void booleanoYNumeroEnteroCorrectosSeAceptan() {
        jakarta.json.JsonObject json = mock(jakarta.json.JsonObject.class);
        jakarta.json.JsonNumber entero = mock(jakarta.json.JsonNumber.class);
        when(entero.getValueType()).thenReturn(jakarta.json.JsonValue.ValueType.NUMBER);
        when(entero.isIntegral()).thenReturn(true);
        when(entero.bigDecimalValue()).thenReturn(new java.math.BigDecimal("10"));
        when(json.get("activo")).thenReturn(jakarta.json.JsonValue.FALSE);
        when(json.get("descuentoMaximo")).thenReturn(entero);
        assertNull(JsonbExceptionMapper.validarTiposTipoDescuento(json));
    }

    @Test
    void tipoIncorrectoEnObservacionesTambienSeRechaza() {
        jakarta.json.JsonObject json = mock(jakarta.json.JsonObject.class);
        jakarta.json.JsonValue numero = mock(jakarta.json.JsonValue.class);
        when(numero.getValueType()).thenReturn(jakarta.json.JsonValue.ValueType.NUMBER);
        when(json.get("observaciones")).thenReturn(numero);
        assertTrue(JsonbExceptionMapper.validarTiposTipoDescuento(json)
                .contains("observaciones"));
    }
}
