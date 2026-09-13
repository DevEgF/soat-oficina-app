import importlib.util
import contextlib
import io
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).with_name('business-smoke-vm.py')
SPEC = importlib.util.spec_from_file_location('business_smoke_vm', SCRIPT)
smoke = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(smoke)


class Process:
    def __init__(self, running=True, wait_error=None):
        self.running = running
        self.wait_error = wait_error
        self.terminated = False
        self.killed = False

    def poll(self):
        return None if self.running else 1

    def terminate(self):
        self.terminated = True
        self.running = False

    def wait(self, timeout):
        if self.wait_error:
            error, self.wait_error = self.wait_error, None
            raise error
        return 0

    def kill(self):
        self.killed = True


class BusinessSmokeTests(unittest.TestCase):
    def test_rejects_non_hml_before_starting_processes(self):
        with patch.object(smoke, 'start_forwards') as start:
            with contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit):
                    smoke.main(['prod'])
        start.assert_not_called()

    def test_request_failure_still_stops_every_port_forward(self):
        processes = [Process(), Process()]

        def start(target):
            target.extend(processes)

        with patch.object(smoke, 'start_forwards', side_effect=start), \
             patch.object(smoke, 'staff_token', side_effect=RuntimeError('login failed')):
            with self.assertRaisesRegex(RuntimeError, 'login failed'):
                smoke.main(['hml'])
        self.assertTrue(all(process.terminated for process in processes))

    def test_cleanup_kills_process_that_does_not_terminate(self):
        process = Process(wait_error=subprocess.TimeoutExpired('kubectl', 10))
        smoke.stop_forwards([process])
        self.assertTrue(process.terminated)
        self.assertTrue(process.killed)

    def test_check_fails_closed_on_unexpected_business_status(self):
        with self.assertRaisesRegex(RuntimeError, 'unexpected OS status'):
            smoke.check('Delivered OS verified', (200, {'status': 'FINALIZED'}), 200, 'DELIVERED')

    def test_verify_order_mode_does_not_create_business_fixtures(self):
        with patch.object(smoke, 'start_forwards'), patch.object(smoke, 'stop_forwards'), \
             patch.object(smoke, 'staff_token', return_value='staff'), \
             patch.object(smoke, 'verify_order') as verify, \
             patch.object(smoke, 'run_journey') as journey, \
             contextlib.redirect_stdout(io.StringIO()):
            smoke.main(['hml', '--verify-order', 'order-id'])
        verify.assert_called_once_with('order-id', 'staff')
        journey.assert_not_called()


if __name__ == '__main__':
    unittest.main()
