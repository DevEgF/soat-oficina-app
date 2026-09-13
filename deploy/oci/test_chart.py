import pathlib
import subprocess
import unittest

CHART = pathlib.Path(__file__).with_name('chart')
DIGEST = 'sha256:' + 'a' * 64


def render(environment='hml', namespace='hml', digest=DIGEST, *options):
    return subprocess.run(['helm', 'template', 'oficina', str(CHART), '-n', namespace,
                           '--set-string', f'environment={environment}',
                           '--set-string', 'image.repository=example.test/oficina',
                           '--set-string', f'image.digest={digest}', *options],
                          capture_output=True, text=True)


class ChartContractTests(unittest.TestCase):
    def test_production_requires_digest_and_matching_namespace(self):
        for environment, namespace, digest in [('hml', 'prod', DIGEST),
                                               ('prod', 'prod', 'latest'),
                                               ('dev', 'dev', DIGEST)]:
            result = render(environment, namespace, digest)
            self.assertNotEqual(0, result.returncode)

    def test_both_environments_render_migration_without_inline_secrets(self):
        for environment in ('hml', 'prod'):
            result = render(environment, environment)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn('helm.sh/hook: pre-install,pre-upgrade', result.stdout)
            self.assertIn('name: oficina-runtime', result.stdout)
            self.assertNotIn('kind: Secret\n', result.stdout)
            self.assertNotIn('secrets-store.csi.k8s.io', result.stdout)
            self.assertIn('spring.jpa.properties.hibernate.default_schema', result.stdout)

    def test_external_ingress_requires_tls_and_routes_auth_separately(self):
        failed = render('hml', 'hml', DIGEST, '--set', 'ingress.enabled=true')
        self.assertNotEqual(0, failed.returncode)
        result = render('hml', 'hml', DIGEST, '--set', 'ingress.enabled=true',
                        '--set', 'ingress.host=oficina.example.test', '--set', 'ingress.tlsSecret=tls',
                        '--set', 'auth.enabled=true', '--set', 'auth.image.repository=example.test/auth',
                        '--set', f'auth.image.digest={DIGEST}')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('path: /auth/token', result.stdout)
        self.assertIn('router.entrypoints: websecure', result.stdout)


if __name__ == '__main__':
    unittest.main()
