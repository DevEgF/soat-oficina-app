$ErrorActionPreference = 'Stop'
python (Join-Path $PSScriptRoot 'test-workflows.py')
if ($LASTEXITCODE -ne 0) { throw 'Application workflow policy validation failed' }
