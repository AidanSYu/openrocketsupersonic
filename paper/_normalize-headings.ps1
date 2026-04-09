# One-off: align markdown heading levels with sections 1-4 (## = top section).
# Run from repo:  pwsh -File paper/_normalize-headings.ps1
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

function Add-HashToHeading([string]$line) {
    if ($line -match '^(\#{1,5})(\s.*)$') {
        return '#' + $Matches[1] + $Matches[2]
    }
    return $line
}

# --- PART B ---
$bPath = Join-Path $root "PART_B.md"
$bl = [System.IO.File]::ReadAllLines($bPath)
$bo = [System.Collections.ArrayList]::new()
[void]$bo.Add("## 5. Shock Relations")
for ($i = 1; $i -lt $bl.Length; $i++) {
    [void]$bo.Add((Add-HashToHeading $bl[$i]))
}
[System.IO.File]::WriteAllLines($bPath, $bo)

# --- PART C ---
$cPath = Join-Path $root "PART_C.md"
$cl = [System.IO.File]::ReadAllLines($cPath)
$co = [System.Collections.ArrayList]::new()
[void]$co.Add("## 6. Drag Models")
for ($i = 1; $i -lt $cl.Length; $i++) {
    [void]$co.Add((Add-HashToHeading $cl[$i]))
}
[System.IO.File]::WriteAllLines($cPath, $co)

# --- PART D: replace # PART D block + following --- with italic banner ---
$dPath = Join-Path $root "PART_D.md"
$dl = [System.IO.File]::ReadAllLines($dPath)
$do = [System.Collections.ArrayList]::new()
[void]$do.Add("---")
[void]$do.Add("")
[void]$do.Add('*Part D -- Shock geometry pre-pass and stability corrections.*')
[void]$do.Add("")
[void]$do.Add("---")
[void]$do.Add("")
for ($i = 4; $i -lt $dl.Length; $i++) {
    [void]$do.Add($dl[$i])
}
[System.IO.File]::WriteAllLines($dPath, $do)

# --- PART E: drop # Part E block; bump all headings ---
$ePath = Join-Path $root "PART_E.md"
$el = [System.IO.File]::ReadAllLines($ePath)
$start = 0
while ($start -lt $el.Length -and $el[$start] -notmatch '^# 9\.') { $start++ }
if ($start -ge $el.Length) { throw "PART_E.md: could not find '# 9.' heading" }
$eo = [System.Collections.ArrayList]::new()
[void]$eo.Add("---")
[void]$eo.Add("")
[void]$eo.Add('*Part E -- Dynamic stability, regime blending, validation, and conclusions.*')
[void]$eo.Add("")
[void]$eo.Add("---")
[void]$eo.Add("")
for ($i = $start; $i -lt $el.Length; $i++) {
    [void]$eo.Add((Add-HashToHeading $el[$i]))
}
[System.IO.File]::WriteAllLines($ePath, $eo)

# --- FULL_TECHNICAL_REPORT.md (1-based line numbers from editor) ---
$fPath = Join-Path $root "FULL_TECHNICAL_REPORT.md"
$fl = [System.IO.File]::ReadAllLines($fPath)

function Bump-Range($lines, $from0, $to0Inclusive, $firstLineReplacement) {
    $lines[$from0] = $firstLineReplacement
    for ($j = $from0 + 1; $j -le $to0Inclusive; $j++) {
        $lines[$j] = Add-HashToHeading $lines[$j]
    }
}

# Section 5: # Section 5 at line 986 (index 985); last line before # Section 6 is line 2309 (index 2308)
Bump-Range $fl 985 2308 "## 5. Shock Relations"

# Section 6: # Section 6 at line 2310 (index 2309); last line before # PART D is line 3850 (index 3849)
Bump-Range $fl 2309 3849 "## 6. Drag Models"

# Drop # PART D (index 3850); insert unnumbered part banner (same as PART_D.md)
$insert = @(
    "---",
    "",
    '*Part D -- Shock geometry pre-pass and stability corrections.*',
    "",
    "---"
)
$nfl = [System.Collections.ArrayList]::new()
for ($i = 0; $i -le 3849; $i++) { [void]$nfl.Add($fl[$i]) }
foreach ($x in $insert) { [void]$nfl.Add($x) }
# Skip original "# PART D", blank, "---", blank (indices 3850-3853); resume at "## 7."
for ($i = 3854; $i -lt $fl.Length; $i++) { [void]$nfl.Add($fl[$i]) }
$fl = $nfl.ToArray()

# Re-find Part E and # 9 after PART D insert (5 lines added)
$pe = -1
$n9 = -1
for ($i = 0; $i -lt $fl.Length; $i++) {
    if ($fl[$i] -match '^# Part E') { $pe = $i; break }
}
if ($pe -lt 0) { throw "FULL: Part E heading not found" }
for ($i = $pe; $i -lt $fl.Length; $i++) {
    if ($fl[$i] -match '^# 9\.') { $n9 = $i; break }
}
if ($n9 -lt 0) { throw "FULL: # 9. heading not found" }

# Remove Part E title through blank line before # 9 (same pattern as PART_E)
$head = [System.Collections.ArrayList]::new()
for ($i = 0; $i -lt $pe; $i++) { [void]$head.Add($fl[$i]) }
[void]$head.Add("---")
[void]$head.Add("")
[void]$head.Add('*Part E -- Dynamic stability, regime blending, validation, and conclusions.*')
[void]$head.Add("")
[void]$head.Add("---")
[void]$head.Add("")
for ($i = $n9; $i -lt $fl.Length; $i++) {
    [void]$head.Add((Add-HashToHeading $fl[$i]))
}
[System.IO.File]::WriteAllLines($fPath, $head)

Write-Host "Normalized headings in PART_B, PART_C, PART_D, PART_E, FULL_TECHNICAL_REPORT.md"
