# Speed of sound validation

## Claim supported
The `AtmosphericConditions.getMachSpeed()` implementation matches the exact thermodynamic relation `a = sqrt(gamma * R * T)` at U.S. Standard Atmosphere 1976 reference temperatures.

## Reference sources
- U.S. Standard Atmosphere, 1976 (NASA-TM-X-74335 / NOAA / USAF).
- `AtmosphericConditionsUpgradeTest` in this repo uses the same reference points.

## Files
| File | Description |
|------|-------------|
| `us_standard_atmosphere_speed_of_sound.csv` | Tabulated reference vs ORP |
| `us_standard_atmosphere_speed_of_sound.png` | Overlay plot |

## Interpretation
This validates the speed-of-sound formula directly. It is a manuscript-safe building-block validation artifact.
