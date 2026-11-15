"""Pruebas HTTP de solo lectura contra Liberty activo. Sin dependencias externas."""
import json
import os
import unittest
from urllib.request import Request, urlopen
from urllib.error import HTTPError

BASE = os.environ.get('CAFEFE_API', 'http://localhost:9080/Cafefe_PPI_2026-1.0-SNAPSHOT/api/')
ORIGEN = 'http://127.0.0.1:5501'


def solicitar(ruta, origen=ORIGEN, metodo='GET', headers=None):
    cabeceras = dict(headers or {})
    if origen is not None:
        cabeceras['Origin'] = origen
    peticion = Request(BASE + ruta, headers=cabeceras, method=metodo)
    try:
        respuesta = urlopen(peticion, timeout=10)
    except HTTPError as error:
        respuesta = error
    with respuesta:
        return respuesta.status, respuesta.headers, respuesta.read()


class CorsHTTPTest(unittest.TestCase):
    def comprobar_origen(self, headers):
        self.assertEqual(headers.get_all('Access-Control-Allow-Origin'), [ORIGEN])
        self.assertEqual(headers.get('Access-Control-Allow-Credentials'), 'true')

    def test_get_tipos_producto(self):
        status, headers, body = solicitar('tipos-producto')
        self.assertEqual(status, 200)
        self.comprobar_origen(headers)
        self.assertIsInstance(json.loads(body), list)

    def test_get_cajas(self):
        status, headers, body = solicitar('cajas')
        self.assertEqual(status, 200)
        self.comprobar_origen(headers)
        self.assertIsInstance(json.loads(body), list)

    def test_preflight_post_json(self):
        for ruta in ['ordenes/publicas', 'cajas']:
            with self.subTest(ruta=ruta):
                status, headers, _ = solicitar(ruta, metodo='OPTIONS', headers={
                    'Access-Control-Request-Method': 'POST',
                    'Access-Control-Request-Headers': 'content-type'})
                self.assertIn(status, (200, 204))
                self.comprobar_origen(headers)
                self.assertIn('POST', headers.get('Access-Control-Allow-Methods', '').split(', '))
                self.assertIn('content-type', headers.get('Access-Control-Allow-Headers', '').lower())

    def test_origenes_no_autorizados(self):
        for origen in ['http://localhost:5501', 'http://127.0.0.1:5502', 'https://no-autorizado.example', 'null']:
            for metodo in ['GET', 'OPTIONS']:
                with self.subTest(origen=origen, metodo=metodo):
                    _, headers, _ = solicitar('cajas', origen, metodo, {
                        'Access-Control-Request-Method': 'POST',
                        'Access-Control-Request-Headers': 'content-type'} if metodo == 'OPTIONS' else {})
                    self.assertIsNone(headers.get('Access-Control-Allow-Origin'))
                    self.assertIsNone(headers.get('Access-Control-Allow-Credentials'))

    def test_sin_origin_no_anade_permiso_cors(self):
        status, headers, _ = solicitar('tipos-producto', origen=None)
        self.assertEqual(status, 200)
        self.assertIsNone(headers.get('Access-Control-Allow-Origin'))

    def test_cabecera_no_permitida(self):
        _, headers, _ = solicitar('cajas', metodo='OPTIONS', headers={
            'Access-Control-Request-Method': 'POST',
            'Access-Control-Request-Headers': 'x-no-autorizada'})
        self.assertNotIn('x-no-autorizada', headers.get('Access-Control-Allow-Headers', '').lower())


if __name__ == '__main__':
    unittest.main(verbosity=2)
