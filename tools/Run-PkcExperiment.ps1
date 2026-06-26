param(
    [string]$BaseUrl = "http://localhost:8090",
    [string]$ProjectCode = "pkc-exp",
    [string]$ProjectName = "PKC Experiment",
    [Parameter(Mandatory = $true)]
    [string]$RootPath,
    [Parameter(Mandatory = $true)]
    [string]$StageName,
    [ValidateSet("INCREMENTAL", "FULL")]
    [string]$ScanMode = "INCREMENTAL",
    [bool]$Entity = $true,
    [string]$CasesPath = "tools/pkc-experiment-cases.sample.json",
    [string]$OutDir = "z/experiment-runs",
    [switch]$UpdateExistingProject,
    [int]$TimeoutSeconds = 900
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Net.Http

function New-HttpClient {
    $handler = [System.Net.Http.HttpClientHandler]::new()
    return [System.Net.Http.HttpClient]::new($handler)
}

$script:Http = New-HttpClient

function Invoke-PkcApi {
    param(
        [ValidateSet("GET", "POST", "PUT", "PATCH", "DELETE")]
        [string]$Method,
        [string]$Path,
        [object]$Body = $null
    )

    $uri = $BaseUrl.TrimEnd("/") + $Path
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $uri)
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 20
        $request.Content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, "application/json")
    }

    $response = $script:Http.SendAsync($request).GetAwaiter().GetResult()
    $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    if (-not $response.IsSuccessStatusCode) {
        throw "HTTP $([int]$response.StatusCode) $Method $Path`n$text"
    }
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }
    $payload = $text | ConvertFrom-Json
    if (($payload.PSObject.Properties.Name -contains "code") -and $payload.code -ne 0 -and $payload.code -ne 200) {
        throw "API $Method $Path failed: $($payload.message)"
    }
    return $payload.data
}

function UrlEncode([string]$Value) {
    return [System.Uri]::EscapeDataString($Value)
}

function Test-ContainsAny {
    param([string]$Text, [object[]]$Terms)
    if ($null -eq $Terms -or $Terms.Count -eq 0) {
        return $true
    }
    foreach ($term in $Terms) {
        if ($Text -like "*$term*") {
            return $true
        }
    }
    return $false
}

function Test-ContainsAll {
    param([string]$Text, [object[]]$Terms)
    if ($null -eq $Terms -or $Terms.Count -eq 0) {
        return $true
    }
    foreach ($term in $Terms) {
        if (-not ($Text -like "*$term*")) {
            return $false
        }
    }
    return $true
}

function Get-NumberOrDefault {
    param([object]$Object, [string]$Name, [int]$DefaultValue)
    if ($null -eq $Object) {
        return $DefaultValue
    }
    $prop = $Object.PSObject.Properties[$Name]
    if ($null -eq $prop -or $null -eq $prop.Value) {
        return $DefaultValue
    }
    return [int]$prop.Value
}

function Ensure-Project {
    $projects = Invoke-PkcApi -Method GET -Path "/api/pkc/projects"
    $project = $projects | Where-Object { $_.code -eq $ProjectCode } | Select-Object -First 1
    if (-not $project) {
        $project = $projects | Where-Object { $_.rootPath -eq $RootPath } | Select-Object -First 1
    }

    if ($project) {
        if ($UpdateExistingProject -and ($project.name -ne $ProjectName -or $project.rootPath -ne $RootPath)) {
            $project = Invoke-PkcApi -Method PUT -Path "/api/pkc/projects/$($project.id)" -Body @{
                name = $ProjectName
                rootPath = $RootPath
            }
        }
        return $project
    }

    return Invoke-PkcApi -Method POST -Path "/api/pkc/projects" -Body @{
        code = $ProjectCode
        name = $ProjectName
        rootPath = $RootPath
    }
}

function Start-And-Wait-Scan {
    param([long]$ProjectId)

    $entityText = if ($Entity) { "true" } else { "false" }
    $task = Invoke-PkcApi -Method POST -Path "/api/pkc/projects/$ProjectId/scans?mode=$ScanMode&entity=$entityText"
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        Start-Sleep -Seconds 2
        $task = Invoke-PkcApi -Method GET -Path "/api/pkc/scans/$($task.id)"
        if ($task.status -notin @("PENDING", "RUNNING")) {
            return $task
        }
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "Scan timeout: scanId=$($task.id), status=$($task.status), phase=$($task.phase)"
}

function Invoke-AskCase {
    param(
        [long]$ProjectId,
        [object]$Case,
        [string]$Mode
    )

    $answer = Invoke-PkcApi -Method POST -Path "/api/pkc/projects/$ProjectId/ask" -Body @{
        question = $Case.question
        topK = 10
        mode = $Mode
    }

    $citations = @($answer.citations)
    $evidence = @($answer.trace.evidenceChunks)
    $firstFile = if ($citations.Count -gt 0) { [string]$citations[0].fileName } else { "" }
    $allCitationFiles = ($citations | ForEach-Object { [string]$_.fileName }) -join "`n"
    $textForTerms = ([string]$answer.answer) + "`n" + (($evidence | ForEach-Object { [string]$_.preview }) -join "`n")

    $checks = [ordered]@{
        grounded = [bool]$answer.grounded
        minCitations = ($citations.Count -ge (Get-NumberOrDefault -Object $Case -Name "minCitations" -DefaultValue 0))
        minEvidence = ($evidence.Count -ge (Get-NumberOrDefault -Object $Case -Name "minEvidence" -DefaultValue 0))
        expectedFirstFile = $true
        expectedAnyCitationFile = $true
        mustContainAny = (Test-ContainsAny -Text $textForTerms -Terms @($Case.mustContainAny))
        mustContainAll = (Test-ContainsAll -Text $textForTerms -Terms @($Case.mustContainAll))
    }

    if ($Case.expectedFirstFileContains) {
        $checks.expectedFirstFile = ($firstFile -like "*$($Case.expectedFirstFileContains)*")
    }
    if ($Case.expectedAnyCitationFileContains) {
        $checks.expectedAnyCitationFile = ($allCitationFiles -like "*$($Case.expectedAnyCitationFileContains)*")
    }

    $ok = -not (@($checks.Values) -contains $false)
    return [ordered]@{
        id = $Case.id
        role = $Case.role
        question = $Case.question
        mode = $Mode
        ok = $ok
        checks = $checks
        grounded = [bool]$answer.grounded
        citationCount = $citations.Count
        evidenceCount = $evidence.Count
        firstFile = $firstFile
        answerPreview = if ($answer.answer.Length -gt 500) { $answer.answer.Substring(0, 500) } else { $answer.answer }
    }
}

if (-not (Test-Path -LiteralPath $RootPath -PathType Container)) {
    throw "Experiment root path does not exist: $RootPath"
}
if (-not (Test-Path -LiteralPath $CasesPath -PathType Leaf)) {
    throw "Case file does not exist: $CasesPath"
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$startedAt = Get-Date
$project = Ensure-Project
if ($project -is [array]) {
    $project = $project[0]
}
$scan = Start-And-Wait-Scan -ProjectId ([long]$project.id)
$report = Invoke-PkcApi -Method GET -Path "/api/pkc/projects/$($project.id)/report/latest"
$changes = @()
try { $changes = @(Invoke-PkcApi -Method GET -Path "/api/pkc/scans/$($scan.id)/changes") } catch {}
$files = Invoke-PkcApi -Method GET -Path "/api/pkc/projects/$($project.id)/files?page=0&size=100"
$graph = Invoke-PkcApi -Method GET -Path "/api/pkc/projects/$($project.id)/graph"
$families = Invoke-PkcApi -Method GET -Path "/api/pkc/projects/$($project.id)/doc-families?page=0&size=100"
$cases = Get-Content -Raw -Encoding UTF8 -Path $CasesPath | ConvertFrom-Json

$askResults = @()
foreach ($case in @($cases)) {
    $modes = if ($case.modes) { @($case.modes) } else { @("standard") }
    foreach ($mode in $modes) {
        $askResults += Invoke-AskCase -ProjectId ([long]$project.id) -Case $case -Mode $mode
    }
}

$failed = @($askResults | Where-Object { -not $_.ok })
$finishedAt = Get-Date
$summary = [ordered]@{
    stageName = $StageName
    startedAt = $startedAt.ToString("s")
    finishedAt = $finishedAt.ToString("s")
    project = [ordered]@{
        id = $project.id
        code = $project.code
        name = $project.name
        rootPath = $project.rootPath
    }
    scan = [ordered]@{
        id = $scan.id
        mode = $scan.mode
        status = $scan.status
        phase = $scan.phase
        totalFiles = $scan.totalFiles
        changedFiles = $scan.changedFiles
        entityExtraction = $scan.entityExtraction
    }
    report = [ordered]@{
        scanId = $report.scanId
        fileTotal = $report.files.total
        fileChanged = $report.files.changed
        heavyPhasesSkipped = $report.files.heavyPhasesSkipped
        graphNodes = $report.graph.totalNodes
        graphEdges = $report.graph.totalEdges
        configItems = $report.configItems.total
        riskCount = @($report.risks).Count
    }
    files = [ordered]@{
        pageCount = @($files.content).Count
        parsed = @($files.content | Where-Object { $_.parseStatus -eq "PARSED" }).Count
        failed = @($files.content | Where-Object { $_.parseStatus -eq "FAILED" }).Count
    }
    graph = [ordered]@{
        nodeCount = $graph.nodeCount
        edgeCount = $graph.edgeCount
    }
    families = [ordered]@{
        totalElements = $families.page.totalElements
        pageCount = @($families.content).Count
    }
    changes = [ordered]@{
        count = $changes.Count
        types = @($changes | Group-Object changeType | ForEach-Object { [ordered]@{ type = $_.Name; count = $_.Count } })
    }
    ask = [ordered]@{
        total = $askResults.Count
        passed = @($askResults | Where-Object { $_.ok }).Count
        failed = $failed.Count
        results = $askResults
    }
    verdict = if ($scan.status -eq "SUCCESS" -and $failed.Count -eq 0) { "PASS" } else { "FAIL" }
}

$safeStage = ($StageName -replace '[\\/:*?"<>|]', '_')
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$jsonPath = Join-Path $OutDir "$stamp-$safeStage.json"
$mdPath = Join-Path $OutDir "$stamp-$safeStage.md"

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$jsonFullPath = Join-Path (Resolve-Path -LiteralPath (Split-Path -Parent $jsonPath)).Path (Split-Path -Leaf $jsonPath)
[System.IO.File]::WriteAllText($jsonFullPath, ($summary | ConvertTo-Json -Depth 30), $utf8NoBom)

$lines = @()
$lines += "# PKC Experiment Loop Report - $StageName"
$lines += ""
$lines += "- Verdict: $($summary['verdict'])"
$lines += "- Project: $($project.name) / $($project.rootPath)"
$lines += "- Scan: #$($scan.id) $($scan.mode) $($scan.status), files $($scan.totalFiles), changed $($scan.changedFiles)"
$lines += "- Graph: nodes $($graph.nodeCount), edges $($graph.edgeCount)"
$lines += "- Families: $($families.page.totalElements)"
$lines += "- Ask: $($summary['ask']['passed'])/$($summary['ask']['total']) passed"
$lines += ""
$lines += "## Ask Cases"
foreach ($r in $askResults) {
    $mark = if ($r["ok"]) { "PASS" } else { "FAIL" }
    $lines += "- [$mark] $($r['id']) / $($r['mode']) / $($r['role']): $($r['question'])"
    $lines += "  - First citation: $($r['firstFile'])"
    $lines += "  - Citations/evidence: $($r['citationCount'])/$($r['evidenceCount'])"
    if (-not $r["ok"]) {
        $lines += "  - Checks: $($r['checks'] | ConvertTo-Json -Compress)"
        $lines += "  - Answer preview: $($r['answerPreview'])"
    }
}
$mdFullPath = Join-Path (Resolve-Path -LiteralPath (Split-Path -Parent $mdPath)).Path (Split-Path -Leaf $mdPath)
[System.IO.File]::WriteAllText($mdFullPath, ($lines -join [Environment]::NewLine), $utf8NoBom)

$verdict = [string]$summary["verdict"]
Write-Host "Experiment completed: $verdict"
Write-Host "JSON: $jsonPath"
Write-Host "Markdown: $mdPath"
if ($verdict -ne "PASS") {
    exit 2
}
