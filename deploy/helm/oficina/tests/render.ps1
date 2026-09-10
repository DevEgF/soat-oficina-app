$ErrorActionPreference = 'Stop'
python (Join-Path $PSScriptRoot 'render.py')
if ($LASTEXITCODE -ne 0) { throw 'Helm semantic contracts failed' }
