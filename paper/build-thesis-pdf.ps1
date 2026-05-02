<#!
  Build a submission-quality PDF from FULL_TECHNICAL_REPORT.md using Pandoc + pdfLaTeX.

  Uses Pandoc's default LaTeX template (report class, Times-like math via mathptmx) so
  Basic MiKTeX works without extra OpenType fonts. For the Eisvogel template instead,
  install MiKTeX packages sourcesanspro, sourcecodepro, and Latin Modern Math, then
  point --template at templates/eisvogel/eisvogel.latex and use --pdf-engine=xelatex.

  Prerequisites:
    winget install JohnMacFarlane.Pandoc
    winget install MiKTeX.MiKTeX

  Usage:
    .\paper\build-thesis-pdf.ps1
#>
$ErrorActionPreference = "Stop"
$PaperRoot = $PSScriptRoot
$ThesisDir = Join-Path $PaperRoot "Thesis"
Set-Location $PaperRoot

$machine = [Environment]::GetEnvironmentVariable("Path", "Machine")
$user = [Environment]::GetEnvironmentVariable("Path", "User")
$process = [Environment]::GetEnvironmentVariable("Path", "Process")
$localPandoc = Join-Path $env:LOCALAPPDATA "Pandoc"
$env:Path = "$process;$machine;$user;$localPandoc"

$pandoc = Get-Command pandoc -ErrorAction SilentlyContinue
if (-not $pandoc) {
  Write-Error "pandoc not found. Install with: winget install JohnMacFarlane.Pandoc"
}

$pdflatex = Get-Command pdflatex -ErrorAction SilentlyContinue
if (-not $pdflatex) {
  Write-Error "pdflatex not found. Install MiKTeX with: winget install MiKTeX.MiKTeX"
}

$outPdf = Join-Path $ThesisDir "OpenRocketPlus-Thesis.pdf"
$md = Join-Path $ThesisDir "FULL_TECHNICAL_REPORT.md"
$meta = Join-Path $PaperRoot "thesis-metadata.yaml"

# Concatenate PART_A..E with explicit blank lines between files. Without the
# blank-line separators, headings like "## 5. Shock Relations" at the start of
# PART_B touch the last line of PART_A, and pandoc fails to recognise them as
# section headings (they were silently dropped from the ToC and rendered as
# body text). Always rebuild FULL_TECHNICAL_REPORT.md from the PARTs here so
# the build is reproducible from source-of-truth.
$parts = @("PART_A.md","PART_B.md","PART_C.md","PART_D.md","PART_E.md")
$writer = [System.IO.StreamWriter]::new($md, $false, [System.Text.UTF8Encoding]::new($false))
try {
  for ($i = 0; $i -lt $parts.Length; $i++) {
    $content = Get-Content -Raw -Encoding UTF8 (Join-Path $ThesisDir $parts[$i])
    $writer.Write($content)
    if (-not $content.EndsWith("`n")) { $writer.Write("`n") }
    if ($i -lt $parts.Length - 1) { $writer.Write("`n") }
  }
} finally {
  $writer.Dispose()
}

Write-Host "Building PDF -> $outPdf"
Write-Host "Using $($pandoc.Source) and $($pdflatex.Source)"

& pandoc `
  $meta `
  $md `
  -o $outPdf `
  --pdf-engine=pdflatex `
  --pdf-engine-opt=-interaction=nonstopmode `
  --from=markdown+tex_math_double_backslash `
  --syntax-highlighting=idiomatic `
  --standalone

if ($LASTEXITCODE -ne 0) {
  Write-Error "pandoc failed with exit code $LASTEXITCODE"
}

Write-Host "Done. Output: $outPdf"
