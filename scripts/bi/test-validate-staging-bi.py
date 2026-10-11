import importlib.util
import pathlib
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('validator', pathlib.Path(__file__).with_name('validate-staging-bi.py'))
validator = importlib.util.module_from_spec(spec); spec.loader.exec_module(validator)


class StagingValidationTest(unittest.TestCase):
    def test_production_database_never_authenticates(self):
        with patch.object(validator, 'request') as request:
            with self.assertRaises(ValueError): validator.validate({'DB_URL': 'jdbc:postgresql://localhost/brasil-saas', 'STAGING_ADMIN_PASSWORD': 'fixture'})
            request.assert_not_called()

    def test_failed_login_does_not_query_or_print_secret(self):
        with patch.object(validator, 'request', return_value=(401, None)) as request:
            report = validator.validate({'DB_URL': 'jdbc:postgresql://localhost:15432/brasil_saas_staging', 'STAGING_ADMIN_PASSWORD': 'PRIVATE'})
            self.assertFalse(report['passed']); self.assertEqual(request.call_count, 1)
            self.assertNotIn('PRIVATE', str(report))

    def test_missing_areas_or_wrong_selection_rejected(self):
        area = {'codigo': 'vendas', 'indicadores': [{}, {}, {}]}
        self.assertTrue(validator.valid_summary({'data': {'areas': [area]}}, {'vendas'}))
        self.assertFalse(validator.valid_summary({'data': {'areas': [area]}}, {'financeiro'}))
        for body in [None, {}, {'data': None}, {'data': {'areas': [None]}}]:
            self.assertFalse(validator.valid_summary(body, {'vendas'}))

    def test_only_staging_endpoints_called_and_report_is_aggregated(self):
        paths = []
        def request(path, token=None, body=None, empresa=None):
            paths.append(path)
            if path == '/api/auth/login': return 200, {'data': {'accessToken': 'PRIVATE-TOKEN'}}
            if path == '/api/auth/me': return 200, {'data': {'empresaId': 123}}
            if token is None: return 401, None
            if 'area-invalida' in path: return 400, None
            if 'integracao' in path: return 200, {'data': {'configured': False}}
            area = path.split('?')[0].rsplit('/', 1)[-1]
            areas = validator.AREAS if area == 'resumo' else {area}
            return 200, {'data': {'areas': [{'codigo': value, 'indicadores': [{}, {}, {}]} for value in areas]}}
        with patch.object(validator, 'request', side_effect=request):
            report = validator.validate({'DB_URL': 'jdbc:postgresql://localhost:15432/brasil_saas_staging', 'STAGING_ADMIN_PASSWORD': 'PRIVATE'})
        self.assertTrue(report['passed']); self.assertNotIn('PRIVATE', str(report)); self.assertNotIn('123', str(report))
        self.assertEqual(len(paths), 10)


if __name__ == '__main__': unittest.main()
