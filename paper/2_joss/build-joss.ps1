<#
  Build the JOSS (Paper 2) PDF from paper.md.

  Runs pandoc FROM the 2_joss/ directory so that all resource paths resolve
  exactly as JOSS's own Open Journals / inara pipeline resolves them — relative
  to paper.md's own directory. The figure lives at 2_joss/figures/, the header at
  2_joss/joss-header.tex (\graphicspath{{./}}), and the bibliography at
  2_joss/paper.bib, so the paper directory is fully self-contained for submission.

  Prerequisites:
    winget install JohnMacFarlane.Pandoc
    winget install MiKTeX.MiKTeX   (provides pdflatex)

  Usage (from anywhere — it cd's to its own directory):
    .\2_joss\build-joss.ps1

  Output: paper/2_JOSS_OpenRocketPlus.pdf (the canonical submission deliverable).
#>
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot   # paper/2_joss/  -- matches JOSS's paper.md-relative resolution

$localPandoc = Join-Path $env:LOCALAPPDATA "Pandoc"
$miktex = Join-Path $env:LOCALAPPDATA "Programs\MiKTeX\miktex\bin\x64"
$env:Path = "$env:Path;$localPandoc;$miktex"

$pandoc = Get-Command pandoc -ErrorAction SilentlyContinue
if (-not $pandoc) { Write-Error "pandoc not found. Install with: winget install JohnMacFarlane.Pandoc" }
$pdflatex = Get-Command pdflatex -ErrorAction SilentlyContinue
if (-not $pdflatex) { Write-Error "pdflatex not found. Install MiKTeX with: winget install MiKTeX.MiKTeX" }

Write-Host "Building JOSS PDF (CWD = $PSScriptRoot) -> ../2_JOSS_OpenRocketPlus.pdf"

& pandoc paper.md `
  -o ../2_JOSS_OpenRocketPlus.pdf `
  --include-in-header=joss-header.tex `
  --citeproc `
  --bibliography=paper.bib `
  --pdf-engine=pdflatex `
  --standalone

if ($LASTEXITCODE -ne 0) { Write-Error "pandoc failed with exit code $LASTEXITCODE" }
Write-Host "Done. Output: ../2_JOSS_OpenRocketPlus.pdf"
