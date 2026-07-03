$ErrorActionPreference = "Stop"
$baseUrl = "http://localhost:8081/api"
$headers = @{ "Content-Type" = "application/json" }

Write-Host "--- 1. Register ---"
$rand = [Guid]::NewGuid().ToString().Substring(0,8)
$email = "test$rand@test.com"
$regBody = @{ name="Test User"; email=$email; password="password123" } | ConvertTo-Json
$reg = Invoke-RestMethod -Uri "$baseUrl/auth/register" -Method Post -Body $regBody -Headers $headers
Write-Host $reg

Write-Host "`n--- 2. Login ---"
$loginBody = @{ email=$email; password="password123" } | ConvertTo-Json
$log = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -Headers $headers
Write-Host ($log | ConvertTo-Json)
$token = $log.token
$authHeaders = @{ "Authorization" = "Bearer $token" }

Write-Host "`n--- 3. Upload File ---"
# Using curl for multipart upload is easier, just need correct escaping for other parts. But since it's just a file upload:
$upload = curl.exe -s -X POST "$baseUrl/files/upload" -H "Authorization: Bearer $token" -F "file=@test.csv"
Write-Host $upload
$fileId = ($upload | ConvertFrom-Json).id

Write-Host "`n--- 4. Column Mapping ---"
$mapping = Invoke-RestMethod -Uri "$baseUrl/files/$fileId/mapping" -Method Get -Headers $authHeaders
Write-Host ($mapping | ConvertTo-Json)

Write-Host "`n--- 5. Normalization ---"
$norm = Invoke-RestMethod -Uri "$baseUrl/normalization/process/$fileId" -Method Post -Headers $authHeaders
Write-Host $norm

Write-Host "`n--- 6. Report Generation ---"
$reportBody = @{ groupBy="LOCATION"; metrics=@("SUM_AMOUNT", "SUM_QUANTITY") } | ConvertTo-Json
$report = Invoke-RestMethod -Uri "$baseUrl/reports/generate" -Method Post -Body $reportBody -Headers @{ "Authorization" = "Bearer $token"; "Content-Type" = "application/json" }
Write-Host ($report | ConvertTo-Json)

Write-Host "`n--- 7. Save Report ---"
$saveBody = @{
    name = "Test Saved Report"
    groupBy = "LOCATION"
    metrics = @("SUM_AMOUNT")
    snapshotMode = $false
    filters = "{}"
    file = @{ id = [int]$fileId }
} | ConvertTo-Json
$save = Invoke-RestMethod -Uri "$baseUrl/reports/save" -Method Post -Body $saveBody -Headers @{ "Authorization" = "Bearer $token"; "Content-Type" = "application/json" }
Write-Host ($save | ConvertTo-Json)
$reportId = $save.id

Write-Host "`n--- 8. Get Saved Reports ---"
$savedList = Invoke-RestMethod -Uri "$baseUrl/reports/saved" -Method Get -Headers $authHeaders
Write-Host ($savedList | ConvertTo-Json -Depth 5)

Write-Host "`n--- 9. Preview Saved Report ---"
$preview = Invoke-RestMethod -Uri "$baseUrl/reports/$reportId/preview" -Method Get -Headers $authHeaders
Write-Host ($preview | ConvertTo-Json)

