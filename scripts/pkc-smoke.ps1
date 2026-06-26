param(
    [string]$BaseUrl = "http://localhost:8090",
    [long]$ProjectId = 38
)

$ErrorActionPreference = "Stop"

function Invoke-Ask($Question, $ExpectedFile) {
    $body = @{
        question = $Question
        topK = 10
        mode = "standard"
    } | ConvertTo-Json -Compress

    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    $raw = Invoke-WebRequest `
        -UseBasicParsing `
        -Method Post `
        -Uri "$BaseUrl/api/pkc/projects/$ProjectId/ask" `
        -ContentType "application/json; charset=utf-8" `
        -Body $bytes `
        -TimeoutSec 180
    $jsonText = [System.Text.Encoding]::UTF8.GetString($raw.RawContentStream.ToArray())
    $res = $jsonText | ConvertFrom-Json

    if ($res.code -ne 0 -and $res.code -ne 200) {
        throw "API failed: $($res.message)"
    }
    if (-not $res.data.grounded) {
        throw "Question not grounded: $Question"
    }

    $files = @($res.data.citations | ForEach-Object { $_.fileName })
    if ($ExpectedFile -and -not ($files | Where-Object { $_ -like "*$ExpectedFile*" })) {
        throw "Expected citation '$ExpectedFile' not found for '$Question'. Actual: $($files -join ' | ')"
    }

    [pscustomobject]@{
        question = $Question
        grounded = $res.data.grounded
        firstCitation = $files[0]
        citationCount = $files.Count
    }
}

function Decode-Utf8Base64($Value) {
    [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($Value))
}

$cases = @(
    @{ q = Decode-Utf8Base64 "5ZCI5ZCM5aSW5Y+Y5pu05pyJ5ZOq5Lqb77yf"; file = Decode-Utf8Base64 "5ZCI5ZCM5aSW5Y+Y5pu05riF5Y2V" },
    @{ q = Decode-Utf8Base64 "5b2T5YmN5pyA6auY6aOO6Zmp5LqL6aG55piv5LuA5LmI77yf"; file = Decode-Utf8Base64 "5Y6f5Z6L6K+05piO" },
    @{ q = Decode-Utf8Base64 "56e75Yqo56uv56a757q/5aGr5oql5Li65LuA5LmI5bGe5LqO5ZCI5ZCM5aSW77yf"; file = Decode-Utf8Base64 "5ZCI5ZCM5aSW5Y+Y5pu05riF5Y2V" }
)

$results = foreach ($case in $cases) {
    Invoke-Ask $case.q $case.file
}

$results | Format-Table -AutoSize
