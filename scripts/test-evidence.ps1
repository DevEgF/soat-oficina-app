$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$collection = Get-Content -LiteralPath (Join-Path $root 'postman/Fase3.postman_collection.json') -Raw | ConvertFrom-Json
$environment = Get-Content -LiteralPath (Join-Path $root 'postman/hml.postman_environment.json') -Raw | ConvertFrom-Json
$required = @('Auth ACTIVE','Auth invalid CPF','Auth BLOCKED','Auth unknown','Unauthenticated customer 401','Staff login','Staff reads fixture order','Customer tracking','Customer approval','Customer rejection','Health and cleanup')
foreach ($name in $required) {
    $item = $collection.item | Where-Object name -eq $name
    if (-not $item -or -not $item.event.script.exec) { throw "Missing request/tests: $name" }
}
foreach ($value in $environment.values) {
    if ($value.key -ne 'environment' -and $value.value -ne '') { throw 'Exported environment must not contain endpoints, customer data or credentials' }
}
foreach ($variable in $collection.variable) {
    if ($variable.value -ne '') { throw 'Exported collection variables must be empty' }
}
$text = $collection | ConvertTo-Json -Depth 30
foreach ($name in @('customerToken','trackingCode','workOrderId')) {
    if (-not $text.Contains("pm.variables.set('$name'")) { throw "Missing run-local capture: $name" }
}
if ($text -notmatch 'pm.variables.unset' -or $text -match 'pm\.(environment|collectionVariables)\.set') { throw 'Tokens must remain run-local and be cleaned up' }
if ($text -match '/api/public/os|documento=') { throw 'Obsolete public customer identity route found' }
foreach ($file in @('componentes.md','sequencia-auth.md','sequencia-os.md')) {
    $doc = Get-Content -LiteralPath (Join-Path $root "docs/architecture/$file") -Raw
    if ($doc -notmatch '```mermaid') { throw "Missing architecture diagram: $file" }
}
$rfcs = @('0001-escolha-da-nuvem.md','0002-escolha-do-banco-de-dados.md','0003-estrategia-de-autenticacao.md')
$activeText = ''
foreach ($file in $rfcs) {
    $rfc = Get-Content -LiteralPath (Join-Path $root "docs/rfcs/$file") -Raw
    if ($rfc -notmatch 'Status: Accepted - AWS' -or $rfc -notmatch 'Status: Superseded') { throw "Current and historical decisions not distinguished: $file" }
    $activeText += $rfc
}
foreach ($word in @('AWS','EKS','RDS','CPF')) { if ($activeText -notmatch $word) { throw "Missing active decision: $word" } }
Write-Output 'Phase 3 evidence contracts passed'
