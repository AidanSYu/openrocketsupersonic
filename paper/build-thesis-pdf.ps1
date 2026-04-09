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
Set-Location $PaperRoot

$machine = [Environment]::GetEnvironmentVariable("Path", "Machine")
$user = [Environment]::GetEnvironmentVariable("Path", "User")
$env:Path = "$machine;$user"

$pandoc = Get-Command pandoc -ErrorAction SilentlyContinue
if (-not $pandoc) {
  Write-Error "pandoc not found. Install with: winget install JohnMacFarlane.Pandoc"
}

$pdflatex = Get-Command pdflatex -ErrorAction SilentlyContinue
if (-not $pdflatex) {
  Write-Error "pdflatex not found. Install MiKTeX with: winget install MiKTeX.MiKTeX"
}

$outPdf = Join-Path $PaperRoot "OpenRocketPlus-Thesis.pdf"
$md = Join-Path $PaperRoot "FULL_TECHNICAL_REPORT.md"
$meta = Join-Path $PaperRoot "thesis-metadata.yaml"

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
