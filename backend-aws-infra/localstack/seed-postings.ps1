param(
    [int]$Count = 50,
    [string]$ApiBase = "",
    [string]$SourceName = "IMX",
    [string]$RequestType = "IMX_CBS_GL"
)

if ([string]::IsNullOrWhiteSpace($ApiBase)) {
    $infraRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
    $ApiBase = terraform -chdir="$infraRoot" output -raw localstack_api_gateway_url
}

$ApiBase = $ApiBase.TrimEnd("/")
$postingUrl = "$ApiBase"
$configUrl = "$ApiBase/config"
$runId = Get-Date -Format "yyyyMMddHHmmss"

function Invoke-JsonPost {
    param(
        [string]$Uri,
        [hashtable]$Body
    )

    $json = $Body | ConvertTo-Json -Depth 5
    try {
        Invoke-RestMethod -Method Post -Uri $Uri -ContentType "application/json" -Body $json | Out-Null
    } catch {
        $response = $_.ErrorDetails.Message
        if ($response -notmatch "DUPLICATE_CONFIG") {
            throw
        }
    }
}

function Ensure-ImxCbsGlConfig {
    Write-Host "Ensuring routing config $RequestType exists"

    Invoke-JsonPost -Uri $configUrl -Body @{
        request_type    = $RequestType
        order_seq       = 1
        source_name     = $SourceName
        target_system   = "CBS"
        operation       = "POSTING"
        processing_mode = "ASYNC"
    }

    Invoke-JsonPost -Uri $configUrl -Body @{
        request_type    = $RequestType
        order_seq       = 2
        source_name     = $SourceName
        target_system   = "GL"
        operation       = "POSTING"
        processing_mode = "ASYNC"
    }
}

Ensure-ImxCbsGlConfig

for ($i = 1; $i -le $Count; $i++) {
    $seq = "{0:D3}" -f $i
    $body = @{
        source_name              = $SourceName
        source_reference_id      = "SRC-PAGE-$runId-$seq"
        end_to_end_reference_id  = "E2E-PAGE-$runId-$seq"
        request_type             = $RequestType
        credit_debit_indicator   = "DEBIT"
        debtor_account           = "1000123456"
        creditor_account         = "1000654321"
        requested_execution_date = (Get-Date -Format "yyyy-MM-dd")
        remittance_information   = "Pagination test $seq"
        amount                   = @{
            value         = "100.00"
            currency_code = "USD"
        }
    }

    Write-Host "Creating posting $seq / $Count"
    Invoke-JsonPost -Uri $postingUrl -Body $body
}

Write-Host "Seeded $Count postings with run id $runId"
