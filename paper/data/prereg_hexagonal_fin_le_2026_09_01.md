# PRE-REGISTERED PREDICTIONS — written before any code change
# Baseline measured 2026-08-31: ORP 4.65% / RAS 5.56% (n=24); supersonic n=8 ORP 4.01% / RAS 5.68%

## Change M2 — Viswanath boattail factor (BarrowmanDragCalculator.java:1730)
Defect: eta multiplies base drag (lower = less drag) but
  - theta -> 0 gives eta 0.25 (max benefit) instead of 1.0 (no benefit)
  - theta > 16 deg drives eta -> 0 (unbounded benefit) instead of back to 1.0 (separated, benefit lost)
Measured corpus eta: Qu8k 37.3deg -> 0.000 (ZERO base drag); Proteus6 26.6 -> 0.422;
IonDrive 26.6 -> 0.422; AeroPac 0.1deg -> 0.253.

PREDICTIONS (M2 alone):
 P2.1 Only the 4 vehicles with a <BoatTail> move: Qu8k, Proteus6, IonDrive, AeroPac104K.
      ALL 20 others move EXACTLY 0.00. If any other flight moves, implementation is wrong.
 P2.2 All four gain drag => apogee DECREASES for all four.
 P2.3 Proteus6 +7.4% -> lower (improves; direction certain, magnitude not predicted)
 P2.4 Qu8k -1.9% -> more negative (WORSENS). AeroPac -1.0% -> more negative (WORSENS).
      IonDrive -3.7% -> more negative (WORSENS).
 P2.5 Net effect on the 24-flight mean is NOT predicted to improve. It may worsen.
      I am making this change because zero base drag on a 37deg boattail is indefensible,
      not because it helps the metric.

## Change M1 — fin leading-edge bluntness for HEXAGONAL fins (FinSetCalc.java:923)
Defect: cd = 0 assuming wedge half-angle < 5 deg; Kinsel is 45 deg. Bevel geometry
(FX1/FX3) is never imported, so ORP cannot see it.

PREDICTIONS (M1 alone):
 P1.1 ALL 16 subsonic/transonic flights move EXACTLY 0.00, because the term is gated on a
      supersonic leading edge M_n = M cos(Lambda) > 1 and max subsonic M_n is Torrent 0.86.
      If ANY subsonic flight moves, implementation is wrong.
 P1.2 Kinsel +8.7% decreases by 3-9 pp.
 P1.3 Vehicles with small bevels move much less than Kinsel (angle enters as sin^2).
 P1.4 Flights already over-dragged (Qu8k, AeroPac, FMJ x2, DontDebate) become slightly WORSE.
 P1.5 Net 24-flight mean improvement is NOT guaranteed.

## Falsification / anti-tuning rules
 - No constant is adjusted after seeing corpus output. If a prediction fails, it is REPORTED
   as a failure, not fitted away.
 - If the mean worsens, that is the reported result. The fixes stand on physics, not on score.
 - Any subsonic movement in M1, or any non-boattail movement in M2, = implementation bug.
