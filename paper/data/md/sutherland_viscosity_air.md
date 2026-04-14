# Dynamic viscosity validation

## Claim supported
The `AtmosphericConditions.getDynamicViscosity()` implementation matches standard Sutherland-law reference values for air across the temperature range used by the supersonic skin-friction model.

## Reference sources
- NIST / standard engineering tables for air viscosity.
- Sutherland, W. (1893), Philosophical Magazine.
- `AtmosphericConditionsUpgradeTest` in this repo uses the same reference values.

## Files
| File | Description |
|------|-------------|
| `sutherland_viscosity_air.csv` | Tabulated reference vs ORP |
| `sutherland_viscosity_air.png` | Overlay plot |

## Interpretation
This is the source-anchored validation for the atmospheric viscosity upgrade that feeds Reynolds-number and compressible skin-friction calculations.
