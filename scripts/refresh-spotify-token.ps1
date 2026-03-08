[CmdletBinding()]
param(
    [string]$ConfigPath = "scripts/spotify-oauth.config.xml",
    [switch]$NoBrowser,
    [switch]$SkipTokenManagerUpdate
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-RepoRoot {
    if ($PSScriptRoot) {
        return (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
    }
    return (Get-Location).Path
}

function Get-ConfigValue {
    param(
        [Parameter(Mandatory)] [System.Xml.XmlNode]$Node,
        [Parameter(Mandatory)] [string]$Name
    )

    $child = $Node.SelectSingleNode($Name)
    if ($null -eq $child) {
        throw "Missing <$Name> in config file."
    }

    return ($child.InnerText | Out-String).Trim()
}

function Ensure-NotPlaceholder {
    param(
        [Parameter(Mandatory)] [string]$Value,
        [Parameter(Mandatory)] [string]$Label
    )

    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -match '^YOUR_.+_HERE$') {
        throw "$Label is not configured yet. Update the XML config first."
    }
}

function ConvertTo-ListenerPrefix {
    param([Parameter(Mandatory)] [uri]$RedirectUri)

    $path = $RedirectUri.AbsolutePath
    if (-not $path.EndsWith('/')) {
        $path = "$path/"
    }

    return "$($RedirectUri.Scheme)://$($RedirectUri.Host):$($RedirectUri.Port)$path"
}

function New-QueryString {
    param([Parameter(Mandatory)] [hashtable]$Parameters)

    return (($Parameters.GetEnumerator() | ForEach-Object {
        '{0}={1}' -f [uri]::EscapeDataString([string]$_.Key), [uri]::EscapeDataString([string]$_.Value)
    }) -join '&')
}

function Escape-KotlinString {
    param([Parameter(Mandatory)] [string]$Value)

    return $Value.Replace('\', '\\').Replace('"', '\"')
}

function Update-TokenManagerFile {
    param(
        [Parameter(Mandatory)] [string]$FilePath,
        [Parameter(Mandatory)] [string]$ClientId,
        [Parameter(Mandatory)] [string]$ClientSecret,
        [Parameter(Mandatory)] [string]$RefreshToken
    )

    if (-not (Test-Path $FilePath)) {
        throw "TokenManager file not found: $FilePath"
    }

    $content = Get-Content -Raw -Path $FilePath
    $updated = $content

    $updated = [regex]::Replace(
        $updated,
        'private const val HARDCODED_CLIENT_ID = ".*?"',
        ('private const val HARDCODED_CLIENT_ID = "{0}"' -f (Escape-KotlinString $ClientId)),
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )

    $updated = [regex]::Replace(
        $updated,
        'private const val HARDCODED_CLIENT_SECRET = ".*?"',
        ('private const val HARDCODED_CLIENT_SECRET = "{0}"' -f (Escape-KotlinString $ClientSecret)),
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )

    $updated = [regex]::Replace(
        $updated,
        'private const val HARDCODED_REFRESH_TOKEN = ".*?"',
        ('private const val HARDCODED_REFRESH_TOKEN = "{0}"' -f (Escape-KotlinString $RefreshToken)),
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    )

    if ($updated -eq $content) {
        throw 'TokenManager.kt was not updated. Verify the constant names still match the expected format.'
    }

    $backupPath = "$FilePath.bak"
    Set-Content -Path $backupPath -Value $content -NoNewline
    Set-Content -Path $FilePath -Value $updated -NoNewline

    return $backupPath
}

$repoRoot = Get-RepoRoot
$configFullPath = Resolve-Path (Join-Path $repoRoot $ConfigPath)
[xml]$config = Get-Content -Raw -Path $configFullPath
$configRoot = $config.SelectSingleNode('/SpotifyOAuthConfig')
if ($null -eq $configRoot) {
    throw 'Root element <SpotifyOAuthConfig> not found.'
}

$clientId = Get-ConfigValue -Node $configRoot -Name 'ClientId'
$clientSecret = Get-ConfigValue -Node $configRoot -Name 'ClientSecret'
$redirectUriRaw = Get-ConfigValue -Node $configRoot -Name 'RedirectUri'
$scopesRaw = Get-ConfigValue -Node $configRoot -Name 'Scopes'
$tokenManagerRelative = Get-ConfigValue -Node $configRoot -Name 'TokenManagerPath'

Ensure-NotPlaceholder -Value $clientId -Label 'Client ID'
Ensure-NotPlaceholder -Value $clientSecret -Label 'Client Secret'

$redirectUri = [uri]$redirectUriRaw
if (-not ($redirectUri.Host -in @('127.0.0.1', 'localhost'))) {
    throw 'RedirectUri must use localhost or 127.0.0.1 so the local callback listener can capture the authorization code.'
}

$scopeList = $scopesRaw -split '[\r\n\t ]+' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
if ($scopeList.Count -eq 0) {
    throw 'At least one Spotify scope is required.'
}
$scopeValue = [string]::Join(' ', $scopeList)

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add((ConvertTo-ListenerPrefix -RedirectUri $redirectUri))
$listener.Start()

try {
    $stateBytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($stateBytes)
    $state = [Convert]::ToBase64String($stateBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')

    $authUri = [System.UriBuilder]'https://accounts.spotify.com/authorize'
    $authUri.Query = New-QueryString -Parameters @{
        client_id = $clientId
        response_type = 'code'
        redirect_uri = $redirectUri.AbsoluteUri
        scope = $scopeValue
        state = $state
        show_dialog = 'true'
    }

    Write-Host ''
    Write-Host 'Spotify authorization URL:' -ForegroundColor Cyan
    Write-Host $authUri.Uri.AbsoluteUri -ForegroundColor Yellow
    Write-Host ''
    Write-Host "Waiting for Spotify callback on $($redirectUri.AbsoluteUri) ..." -ForegroundColor Cyan

    if (-not $NoBrowser) {
        Start-Process $authUri.Uri.AbsoluteUri | Out-Null
    }

    $contextTask = $listener.GetContextAsync()
    if (-not $contextTask.Wait([TimeSpan]::FromMinutes(5))) {
        throw 'Timed out waiting for the Spotify authorization callback.'
    }

    $context = $contextTask.Result
    $request = $context.Request
    $response = $context.Response
    $response.ContentType = 'text/html; charset=utf-8'

    $responseHtml = '<html><body style="font-family:Segoe UI;padding:24px"><h2>Spotify authorization received.</h2><p>You can close this window and return to PowerShell.</p></body></html>'
    $buffer = [System.Text.Encoding]::UTF8.GetBytes($responseHtml)
    $response.ContentLength64 = $buffer.Length
    $response.OutputStream.Write($buffer, 0, $buffer.Length)
    $response.OutputStream.Close()

    $code = $request.QueryString['code']
    $returnedState = $request.QueryString['state']
    $errorValue = $request.QueryString['error']

    if ($errorValue) {
        throw "Spotify authorization failed: $errorValue"
    }
    if ([string]::IsNullOrWhiteSpace($code)) {
        throw 'Spotify callback did not contain an authorization code.'
    }
    if ($returnedState -ne $state) {
        throw 'State mismatch in Spotify callback. Aborting for safety.'
    }

    $basicAuth = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes("$clientId`:$clientSecret"))
    $tokenResponse = Invoke-RestMethod `
        -Method Post `
        -Uri 'https://accounts.spotify.com/api/token' `
        -Headers @{ Authorization = "Basic $basicAuth" } `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{
            grant_type = 'authorization_code'
            code = $code
            redirect_uri = $redirectUri.AbsoluteUri
        }

    if ([string]::IsNullOrWhiteSpace($tokenResponse.refresh_token)) {
        throw 'Spotify did not return a refresh token. Remove app access in your Spotify account and try again with show_dialog enabled.'
    }

    $tokenManagerPath = Join-Path $repoRoot $tokenManagerRelative
    $backupPath = $null
    if (-not $SkipTokenManagerUpdate) {
        $backupPath = Update-TokenManagerFile `
            -FilePath $tokenManagerPath `
            -ClientId $clientId `
            -ClientSecret $clientSecret `
            -RefreshToken $tokenResponse.refresh_token
    }

    Write-Host ''
    Write-Host 'Success.' -ForegroundColor Green
    Write-Host "Access token expires in: $($tokenResponse.expires_in) seconds"
    Write-Host "Refresh token captured: $($tokenResponse.refresh_token.Substring(0, [Math]::Min(12, $tokenResponse.refresh_token.Length)))..."
    if ($backupPath) {
        Write-Host "TokenManager backup: $backupPath"
        Write-Host "TokenManager updated: $tokenManagerPath"
    }
    Write-Host ''
    Write-Host 'Next steps:' -ForegroundColor Cyan
    Write-Host '  1. Rebuild the app.'
    Write-Host '  2. Reinstall it on the emulator/device.'
    Write-Host '  3. Revoke the old Spotify app grant if needed.'
}
finally {
    if ($listener.IsListening) {
        $listener.Stop()
        $listener.Close()
    }
}
