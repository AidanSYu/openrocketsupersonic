# Cp,max / Rayleigh pitot validation (NACA Report 1135)

## Claim supported
The `calculateCpMax()` helper used by the Modified Newtonian hypersonic model matches Cp,max values independently derived from **NACA Report 1135** normal-shock (Table I) and isentropic (Table II) relations.

## Independent derivation chains
Two independent code paths compute the same quantity:

1. **Direct Rayleigh pitot** (`calculateCpMax`): combines normal-shock jump and isentropic recovery into a single closed-form expression.
2. **NACA 1135 Table I+II** (`cpMaxFromNaca1135Tables`): uses the A-validated `NormalShockRelations` (downstream Mach, pressure ratio) plus the isentropic total/static recovery formula, then derives Cp,max = 2/(γM²)(p₀₂/p₁ − 1).

Agreement is at machine-epsilon level (≤10⁻¹²), confirming that the combined pitot formula correctly composes the independently validated building blocks.

## Reference sources
- **NACA Report 1135** — *Equations, Tables, and Charts for Compressible Flow* (Ames, 1953), Tables I (normal shock) and II (isentropic flow).
- **Anderson**, *Modern Compressible Flow*, Tables A.1 and A.2.
- `NormalShockRelationsTest` validates the Table I building block to A level.

## Files
| File | Description |
|------|-------------|
| `rayleigh_pitot_cpmax.csv` | 15-point NACA 1135 derivation vs ORP |
| `rayleigh_pitot_cpmax.png` | Overlay plot |

## Interpretation
This validates the hypersonic pressure-cap building block via independent derivation from A-validated sub-components. It does not by itself validate full-body drag above Mach 5, but it closes the Cp,max claim to grade A.
