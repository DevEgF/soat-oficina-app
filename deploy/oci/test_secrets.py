import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('setup', Path(__file__).with_name('configure-secrets.py'))
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class ConfigurationTests(unittest.TestCase):
    def test_tls_and_schema_are_enforced_and_credentials_decoded(self):
        result = setup.neon_config('postgresql://user:synthetic%40password@ep-demo.us-east-2.aws.neon.tech/neondb?sslmode=require', 'hml')
        self.assertIn('sslmode=verify-full', result['SPRING_DATASOURCE_URL'])
        self.assertIn('currentSchema=hml', result['SPRING_DATASOURCE_URL'])
        self.assertNotIn('password', result['SPRING_DATASOURCE_URL'])
        self.assertEqual(result['SPRING_DATASOURCE_PASSWORD'], 'synthetic@password')

    def test_rejects_pooler_insecure_tls_and_foreign_hosts(self):
        for url in ('postgresql://u:p@ep-demo-pooler.us-east-2.aws.neon.tech/db?sslmode=require',
                    'postgresql://u:p@ep-demo.neon.tech/db?sslmode=disable',
                    'postgresql://u:p@example.org/db?sslmode=require',
                    'postgresql://u:p@ep-demo.neon.tech:bad/db?sslmode=require'):
            with self.subTest(url=url), self.assertRaises(ValueError):
                setup.neon_config(url, 'prod')

    def test_preserves_identities_and_separates_environments(self):
        with tempfile.TemporaryDirectory() as tmp:
            hml, prod = Path(tmp) / 'hml', Path(tmp) / 'prod'
            setup.prepare_runtime(hml, {'SPRING_DATASOURCE_PASSWORD': 'synthetic-one'})
            identity = (hml / 'APP_JWT_SECRET').read_text()
            setup.prepare_runtime(hml, {'SPRING_DATASOURCE_PASSWORD': 'synthetic-two'})
            setup.prepare_runtime(prod, {})
            self.assertEqual(identity, (hml / 'APP_JWT_SECRET').read_text())
            self.assertNotEqual(identity, (prod / 'APP_JWT_SECRET').read_text())
            self.assertEqual('synthetic-two', (hml / 'SPRING_DATASOURCE_PASSWORD').read_text())


if __name__ == '__main__':
    unittest.main()
