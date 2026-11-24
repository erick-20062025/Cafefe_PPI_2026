"""Pruebas de seguridad del empaquetado; no requieren Docker ni PostgreSQL."""
import importlib.util
from io import BytesIO
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

spec = importlib.util.spec_from_file_location('entrega', Path(__file__).with_name('generar-entrega.py'))
entrega = importlib.util.module_from_spec(spec)
spec.loader.exec_module(entrega)


class EntregaTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='cafefe-zip-test-')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.front = self.root / 'frontend'
        for name in ['index.html', 'boundary/app.js', 'config/api.js', 'sw.js', 'manifest.webmanifest']:
            p = self.front / 'src' / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text('contenido de prueba')
        self.war = self.root / 'cafefe.war'
        with ZipFile(self.war, 'w') as z:
            z.writestr('WEB-INF/classes/META-INF/persistence.xml', '<persistence><property name="jakarta.persistence.schema-generation.database.action" value="none"/><property name="eclipselink.ddl-generation" value="none"/></persistence>')
        self.template = self.root / 'integrantes.txt'
        self.template.write_text('{{COMMIT_BACKEND}} {{COMMIT_FRONTEND}} {{NOMBRE_COMPLETO_DIEGO}}')
        self.zip = self.root / 'entrega.zip'

    def generar(self):
        with patch.object(entrega, 'git', side_effect=lambda repo, *args: '' if args[0] == 'status' else 'abc123'):
            entrega.generar(self.front, self.war, self.template, self.zip)

    def test_estructura_exacta_y_solo_src(self):
        (self.front / '.env').write_text('secreto que no debe incluirse')
        self.generar()
        with ZipFile(self.zip) as z:
            self.assertEqual(set(z.namelist()), {'cafefe.war', 'frontend-src.zip', 'integrantes.txt'})
            with ZipFile(BytesIO(z.read('frontend-src.zip'))) as interno:
                self.assertTrue(all(n.startswith('src/') for n in interno.namelist()))
                self.assertFalse(any('.env' in n for n in interno.namelist()))
            self.assertIn('PRELIMINAR', z.read('integrantes.txt').decode())

    def test_rechaza_archivo_privado_dentro_src(self):
        (self.front / 'src/.env').write_text('privado')
        with self.assertRaises(ValueError):
            self.generar()
        self.assertFalse(self.zip.exists())

    def test_rechaza_enlaces_a_archivos_externos(self):
        (self.front / 'src/externo.js').symlink_to(self.template)
        with self.assertRaises(ValueError):
            self.generar()

    def test_rechaza_war_con_dependencias_empaquetadas(self):
        with ZipFile(self.war, 'a') as z:
            z.writestr('WEB-INF/lib/dependencia.jar', b'jar')
        with self.assertRaises(ValueError):
            self.generar()

    def test_rechaza_generacion_ddl(self):
        with ZipFile(self.war, 'w') as z:
            z.writestr('WEB-INF/classes/META-INF/persistence.xml', '<persistence><property name="jakarta.persistence.schema-generation.database.action" value="create"/></persistence>')
        with self.assertRaises(ValueError):
            self.generar()


if __name__ == '__main__':
    unittest.main()
