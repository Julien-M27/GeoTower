param(
    [int]$Port = 8754
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootPath = (Resolve-Path -LiteralPath $Root).Path
$RootPrefix = $RootPath.TrimEnd('\') + '\'
$Utf8 = [System.Text.Encoding]::UTF8
$Ascii = [System.Text.Encoding]::ASCII
$IdleTimeout = [TimeSpan]::FromHours(4)

function Get-ReasonPhrase {
    param([int]$StatusCode)

    switch ($StatusCode) {
        200 { "OK" }
        204 { "No Content" }
        400 { "Bad Request" }
        403 { "Forbidden" }
        404 { "Not Found" }
        405 { "Method Not Allowed" }
        500 { "Internal Server Error" }
        502 { "Bad Gateway" }
        default { "OK" }
    }
}

function Send-Bytes {
    param(
        [System.Net.Sockets.NetworkStream]$Stream,
        [byte[]]$Bytes,
        [string]$ContentType,
        [int]$StatusCode = 200
    )

    $Reason = Get-ReasonPhrase -StatusCode $StatusCode
    $Header = "HTTP/1.1 $StatusCode $Reason`r`n" +
        "Content-Type: $ContentType`r`n" +
        "Content-Length: $($Bytes.Length)`r`n" +
        "Access-Control-Allow-Origin: *`r`n" +
        "Access-Control-Allow-Headers: Accept, Content-Type`r`n" +
        "Connection: close`r`n" +
        "`r`n"
    $HeaderBytes = $Ascii.GetBytes($Header)
    $Stream.Write($HeaderBytes, 0, $HeaderBytes.Length)
    if ($Bytes.Length -gt 0) {
        $Stream.Write($Bytes, 0, $Bytes.Length)
    }
}

function Send-Text {
    param(
        [System.Net.Sockets.NetworkStream]$Stream,
        [string]$Text,
        [string]$ContentType,
        [int]$StatusCode = 200
    )

    Send-Bytes -Stream $Stream -Bytes $Utf8.GetBytes($Text) -ContentType $ContentType -StatusCode $StatusCode
}

function Get-ContentType {
    param([string]$Path)

    switch ([System.IO.Path]::GetExtension($Path).ToLowerInvariant()) {
        ".html" { "text/html; charset=utf-8" }
        ".css" { "text/css; charset=utf-8" }
        ".js" { "application/javascript; charset=utf-8" }
        ".json" { "application/json; charset=utf-8" }
        ".png" { "image/png" }
        ".jpg" { "image/jpeg" }
        ".jpeg" { "image/jpeg" }
        ".svg" { "image/svg+xml" }
        default { "application/octet-stream" }
    }
}

$Address = [System.Net.IPAddress]::Parse("127.0.0.1")
$Listener = [System.Net.Sockets.TcpListener]::new($Address, $Port)

try {
    $Listener.Start()
} catch {
    exit 0
}

try {
    $LastActivity = Get-Date
    while (((Get-Date) - $LastActivity) -lt $IdleTimeout) {
        if (-not $Listener.Pending()) {
            Start-Sleep -Milliseconds 200
            continue
        }

        $Client = $Listener.AcceptTcpClient()
        $LastActivity = Get-Date
        try {
            $Stream = $Client.GetStream()
            $Reader = [System.IO.StreamReader]::new($Stream, $Ascii, $false, 4096, $true)
            $RequestLine = $Reader.ReadLine()
            if ([string]::IsNullOrWhiteSpace($RequestLine)) {
                continue
            }

            do {
                $HeaderLine = $Reader.ReadLine()
            } while ($null -ne $HeaderLine -and $HeaderLine.Length -gt 0)

            $Parts = $RequestLine.Split(" ")
            if ($Parts.Count -lt 2) {
                Send-Text -Stream $Stream -Text "Bad request" -ContentType "text/plain; charset=utf-8" -StatusCode 400
                continue
            }

            $Method = $Parts[0].ToUpperInvariant()
            $Target = $Parts[1]
            if ($Method -eq "OPTIONS") {
                Send-Bytes -Stream $Stream -Bytes ([byte[]]::new(0)) -ContentType "text/plain" -StatusCode 204
                continue
            }
            if ($Method -ne "GET" -and $Method -ne "HEAD") {
                Send-Text -Stream $Stream -Text "Method not allowed" -ContentType "text/plain; charset=utf-8" -StatusCode 405
                continue
            }

            $RequestUri = [System.Uri]::new("http://127.0.0.1:$Port$Target")
            $Path = $RequestUri.AbsolutePath

            if ($Path.StartsWith("/api/live-fr/")) {
                $Suffix = $Path.Substring("/api/live-fr/".Length)
                $RemoteUrl = "https://api.geotower.fr/api/v2/live/fr/$Suffix$($RequestUri.Query)"
                $WebClient = [System.Net.WebClient]::new()
                try {
                    $WebClient.Headers.Add("Accept", "application/json")
                    $Bytes = $WebClient.DownloadData($RemoteUrl)
                    Send-Bytes -Stream $Stream -Bytes $Bytes -ContentType "application/json; charset=utf-8" -StatusCode 200
                } catch {
                    Send-Text -Stream $Stream -Text '{"error":"GeoTower live API unavailable from local proxy"}' -ContentType "application/json; charset=utf-8" -StatusCode 502
                } finally {
                    $WebClient.Dispose()
                }
                continue
            }

            $Relative = [System.Uri]::UnescapeDataString($Path.TrimStart('/'))
            if ([string]::IsNullOrWhiteSpace($Relative)) {
                $Relative = "index.html"
            }

            $FullPath = [System.IO.Path]::GetFullPath((Join-Path $RootPath $Relative))
            if ($FullPath -ne $RootPath -and -not $FullPath.StartsWith($RootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
                Send-Text -Stream $Stream -Text "Forbidden" -ContentType "text/plain; charset=utf-8" -StatusCode 403
                continue
            }

            if (-not (Test-Path -LiteralPath $FullPath -PathType Leaf)) {
                Send-Text -Stream $Stream -Text "Not found" -ContentType "text/plain; charset=utf-8" -StatusCode 404
                continue
            }

            $Bytes = if ($Method -eq "HEAD") {
                [byte[]]::new(0)
            } else {
                [System.IO.File]::ReadAllBytes($FullPath)
            }
            Send-Bytes -Stream $Stream -Bytes $Bytes -ContentType (Get-ContentType -Path $FullPath)
        } catch {
            if ($Stream -and $Stream.CanWrite) {
                Send-Text -Stream $Stream -Text "Server error" -ContentType "text/plain; charset=utf-8" -StatusCode 500
            }
        } finally {
            $Client.Close()
        }
    }
} finally {
    $Listener.Stop()
}
