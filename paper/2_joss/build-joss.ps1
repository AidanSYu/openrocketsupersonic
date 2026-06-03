<#
  Build the JOSS (Paper 2) PDF from 2_joss/paper.md.

  Reproduces the original ad-hoc pandoc invocation (no build script existed before
  the 2026-06 folder cleanup). The script Set-Location's to the paper/ root BEFORE
  invoking pandoc, because paper.md embeds a CWD-relative figure path
  (data/png/naca_rm_a52h28_validation.png) and joss-header.tex uses
  \graphicspath{{./}} — both resolve only when the working directory is paper/.

  Prerequisites:
    winget install JohnMacFarlane.Pandoc
    winget install MiKTeX.MiKTeX   (provides pdflatex)

  Usage (from anywhere — it cd's to paper/ itself):
    .\2_joss\build-joss.ps1

  Output: paper/2_JOSS_OpenRocketPlus.pdf  (the canonical submission deliverable).
#>
$ErrorActionPreference = "Stop"
$PaperRoot = Split-Path $PSScriptRoot -Parent   # 2_joss/ -> paper/
Set-Location $PaperRoot

# Make pandoc + pdflatex discoverable (same pattern as _build/build-thesis-pdf.ps1)
$localPandoc = Join-Path $env:LOCALAPPDATA "Pandoc"
$miktex = Join-Path $env:LOCALAPPDATA "Programs\MiKTeX\miktex\bin\x64"
$env:Path = "$env:Path;$localPandoc;$miktex"

$pandoc = Get-Command pandoc -ErrorAction SilentlyContinue
if (-not $pandoc) { Write-Error "pandoc not found. Install with: winget install JohnMacFarlane.Pandoc" }
$pdflatex = Get-Command pdflatex -ErrorAction SilentlyContinue
if (-not $pdflatex) { Write-Error "pdflatex not found. Install MiKTeX with: winget install MiKTeX.MiKTeX" }

Write-Host "Building JOSS PDF -> 2_JOSS_OpenRocketPlus.pdf (CWD = $PaperRoot)"

& pandoc 2_joss/paper.md `
  -o 2_JOSS_OpenRocketPlus.pdf `
  --include-in-header=2_joss/joss-header.tex `
  --citeproc `
  --bibliography=2_joss/paper.bib `
  --pdf-engine=pdflatex `
  --standalone

if ($LASTEXITCODE -ne 0) { Write-Error "pandoc failed with exit code $LASTEXITCODE" }
Write-Host "Done. Output: $(Join-Path $PaperRoot '2_JOSS_OpenRocketPlus.pdf')"
