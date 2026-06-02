# SimVReal Model-Mechanism Ablation - 2026-05-02

Source test: `info.openrocket.core.aerodynamics.SimVRealModelMechanismAblationTest`.

Machine-readable CSV: `paper/data/csv/simvreal_model_mechanism_ablation_2026_05_02.csv`.

This is the AST manuscript's model-mechanism (causal-evidence) ablation. Each mutation independently disables one of the OpenRocket Plus supersonic calculator additions and replays the full 24-flight SimVReal corpus. The delta column converts engineering claims ("this mechanism contributes") into corpus-level evidence ("removing X costs Y pp of average error").

## Aggregate per mutation

| Mutation | Mechanism | Avg \|err\| | Within +/-5% | Within +/-10% | Abnormal | Avg signed delta pp | Max \|delta\| pp |
|---|---|---:|---:|---:|---:|---:|---:|
| baseline_current | current production model | 4.65% | 58.3% | 100.0% | 0 | +0.000 | 0.000 |
| no_shockgeometry | ShockGeometry pre-pass disabled | 4.58% | 58.3% | 100.0% | 0 | +0.152 | 3.630 |
| no_pnk | Pitts-Nielsen-Kaattari interference disabled (F_WB=F_BW=1) | 4.65% | 58.3% | 100.0% | 0 | -0.000 | 0.000 |
| no_van_driest_ii | Van Driest II skin friction disabled (incompressible Cf) | 4.93% | 58.3% | 95.8% | 0 | +0.868 | 7.943 |
| no_k1_floor | Mach-blended K1 floor disabled (no fin CNa floor) | 4.65% | 58.3% | 100.0% | 0 | -0.000 | 0.000 |
| no_finned_base_aug | Finned-body base augmentation disabled | 8.86% | 41.7% | 75.0% | 0 | +8.104 | 39.504 |
| no_datcom_fin_wave_drag | DATCOM 4.1.5.1 fin wave drag disabled | 4.57% | 62.5% | 95.8% | 0 | +0.389 | 1.924 |

## Per-case delta (mutated minus baseline)

| Mutation | Rocket | Baseline err | Mutated err | Delta pp | Delta ft | Baseline Mach | Mutated Mach | Mutated terminal |
|---|---|---:|---:|---:|---:|---:|---:|---|
| no_shockgeometry | Byrum | +7.49% | +7.49% | +0.00 | +0 | 0.75 | 0.75 | NORMAL |
| no_shockgeometry | Cancer Descending | -2.33% | -2.33% | +0.00 | +0 | 0.56 | 0.56 | NORMAL |
| no_shockgeometry | EZI-65 J450ST | +4.87% | +4.87% | +0.00 | +0 | 0.60 | 0.60 | NORMAL |
| no_shockgeometry | Gibb | +1.94% | +1.94% | +0.00 | +0 | 0.55 | 0.55 | NORMAL |
| no_shockgeometry | Ion Drive | -3.71% | -3.71% | -0.00 | -0 | 0.79 | 0.79 | NORMAL |
| no_shockgeometry | Raven | +7.64% | +7.64% | -0.00 | -0 | 1.07 | 1.07 | NORMAL |
| no_shockgeometry | Thunder & Lightning | +8.37% | +8.37% | +0.00 | +0 | 0.54 | 0.54 | NORMAL |
| no_shockgeometry | Blister | -8.40% | -8.40% | +0.00 | +0 | 0.83 | 0.83 | NORMAL |
| no_shockgeometry | Rabia | -6.53% | -6.53% | +0.00 | +0 | 1.14 | 1.14 | NORMAL |
| no_shockgeometry | Rabia Short Fin Can | -6.31% | -6.31% | +0.00 | +0 | 0.86 | 0.86 | NORMAL |
| no_shockgeometry | Torrent | -2.75% | -2.75% | -0.00 | -0 | 1.22 | 1.22 | NORMAL |
| no_shockgeometry | Caliber Isp 04 Team 3 | -1.90% | -1.90% | +0.00 | +0 | 0.64 | 0.64 | NORMAL |
| no_shockgeometry | Caliber Isp 04 Team 1 | +3.21% | +3.21% | +0.00 | +0 | 0.66 | 0.66 | NORMAL |
| no_shockgeometry | Caliber Isp 04 Team 2 | +4.85% | +4.85% | +0.00 | +0 | 0.64 | 0.64 | NORMAL |
| no_shockgeometry | Caliber Isp 05 Columbia | -6.05% | -6.05% | +0.00 | +0 | 0.84 | 0.84 | NORMAL |
| no_shockgeometry | Caliber Isp 05 Discovery | -3.20% | -3.20% | +0.00 | +0 | 0.81 | 0.81 | NORMAL |
| no_shockgeometry | Kline-Rogers L500 | -2.39% | -2.40% | -0.01 | -1 | 1.98 | 1.98 | NORMAL |
| no_shockgeometry | Don't Debate This | -6.05% | -6.05% | +0.00 | +0 | 3.04 | 3.04 | NORMAL |
| no_shockgeometry | Qu8k | -1.89% | -1.89% | +0.00 | +0 | 3.46 | 3.46 | NORMAL |
| no_shockgeometry | Proteus 6 | +7.37% | +7.41% | +0.03 | +28 | 2.87 | 2.87 | NORMAL |
| no_shockgeometry | Full Metal Jacket BALLS 005 | -1.91% | -1.91% | +0.00 | +0 | 2.31 | 2.31 | NORMAL |
| no_shockgeometry | Full Metal Jacket Black Rock 6 | -2.66% | +0.97% | +3.63 | +1090 | 2.46 | 2.47 | NORMAL |
| no_shockgeometry | A-601 Kinsel | +8.72% | +8.72% | +0.00 | +0 | 2.19 | 2.19 | NORMAL |
| no_shockgeometry | AeroPac 104K | -1.01% | -1.01% | +0.00 | +3 | 3.04 | 3.04 | NORMAL |
| no_pnk | Byrum | +7.49% | +7.49% | +0.00 | +0 | 0.75 | 0.75 | NORMAL |
| no_pnk | Cancer Descending | -2.33% | -2.33% | +0.00 | +0 | 0.56 | 0.56 | NORMAL |
| no_pnk | EZI-65 J450ST | +4.87% | +4.87% | +0.00 | +0 | 0.60 | 0.60 | NORMAL |
| no_pnk | Gibb | +1.94% | +1.94% | +0.00 | +0 | 0.55 | 0.55 | NORMAL |
| no_pnk | Ion Drive | -3.71% | -3.71% | +0.00 | +0 | 0.79 | 0.79 | NORMAL |
| no_pnk | Raven | +7.64% | +7.64% | +0.00 | +0 | 1.07 | 1.07 | NORMAL |
| no_pnk | Thunder & Lightning | +8.37% | +8.37% | +0.00 | +0 | 0.54 | 0.54 | NORMAL |
| no_pnk | Blister | -8.40% | -8.40% | +0.00 | +0 | 0.83 | 0.83 | NORMAL |
| no_pnk | Rabia | -6.53% | -6.53% | -0.00 | -0 | 1.14 | 1.14 | NORMAL |
| no_pnk | Rabia Short Fin Can | -6.31% | -6.31% | -0.00 | -0 | 0.86 | 0.86 | NORMAL |
| no_pnk | Torrent | -2.75% | -2.75% | +0.00 | +0 | 1.22 | 1.22 | NORMAL |
| no_pnk | Caliber Isp 04 Team 3 | -1.90% | -1.90% | +0.00 | +0 | 0.64 | 0.64 | NORMAL |
| no_pnk | Caliber Isp 04 Team 1 | +3.21% | +3.21% | +0.00 | +0 | 0.66 | 0.66 | NORMAL |
| no_pnk | Caliber Isp 04 Team 2 | +4.85% | +4.85% | +0.00 | +0 | 0.64 | 0.64 | NORMAL |
| no_pnk | Caliber Isp 05 Columbia | -6.05% | -6.05% | +0.00 | +0 | 0.84 | 0.84 | NORMAL |
| no_pnk | Caliber Isp 05 Discovery | -3.20% | -3.20% | +0.00 | +0 | 0.81 | 0.81 | NORMAL |
| no_pnk | Kline-Rogers L500 | -2.39% | -2.39% | +0.00 | +0 | 1.98 | 1.98 | NORMAL |
| no_pnk | Don't Debate This | -6.05% | -6.05% | +0.00 | +0 | 3.04 | 3.04 | NORMAL |
| no_pnk | Qu8k | -1.89% | -1.89% | +0.00 | +0 | 3.46 | 3.46 | NORMAL |
| no_pnk | Proteus 6 | +7.37% | +7.37% | -0.00 | -0 | 2.87 | 2.87 | NORMAL |
| no_pnk | Full Metal Jacket BALLS 005 | -1.91% | -1.91% | +0.00 | +0 | 2.31 | 2.31 | NORMAL |
| no_pnk | Full Metal Jacket Black Rock 6 | -2.66% | -2.66% | -0.00 | -0 | 2.46 | 2.46 | NORMAL |
| no_pnk | A-601 Kinsel | +8.72% | +8.72% | -0.00 | -0 | 2.19 | 2.19 | NORMAL |
| no_pnk | AeroPac 104K | -1.01% | -1.01% | -0.00 | -0 | 3.04 | 3.04 | NORMAL |
| no_van_driest_ii | Byrum | +7.49% | +7.49% | +0.00 | +0 | 0.75 | 0.75 | NORMAL |
| no_van_driest_ii | Cancer Descending | -2.33% | -2.33% | +0.00 | +0 | 0.56 | 0.56 | NORMAL |
| no_van_driest_ii | EZI-65 J450ST | +4.87% | +4.87% | +0.00 | +0 | 0.60 | 0.60 | NORMAL |
| no_van_driest_ii | Gibb | +1.94% | +1.94% | +0.00 | +0 | 0.55 | 0.55 | NORMAL |
| no_van_driest_ii | Ion Drive | -3.71% | -3.71% | +0.00 | +0 | 0.79 | 0.79 | NORMAL |
| no_van_driest_ii | Raven | +7.64% | +7.64% | -0.01 | -1 | 1.07 | 1.07 | NORMAL |
| no_van_driest_ii | Thunder & Lightning | +8.37% | +8.37% | +0.00 | +0 | 0.54 | 0.54 | NORMAL |
| no_van_driest_ii | Blister | -8.40% | -8.40% | +0.00 | +0 | 0.83 | 0.83 | NORMAL |
| no_van_driest_ii | Rabia | -6.53% | -6.61% | -0.08 | -11 | 1.14 | 1.14 | NORMAL |
| no_van_driest_ii | Rabia Short Fin Can | -6.31% | -6.31% | +0.00 | +0 | 0.86 | 0.86 | NORMAL |
| no_van_driest_ii | Torrent | -2.75% | -2.86% | -0.11 | -14 | 1.22 | 1.22 | NORMAL |
| no_van_driest_ii | Caliber Isp 04 Team 3 | -1.90% | -1.90% | +0.00 | +0 | 0.64 | 0.64 | NORMAL |
| no_van_driest_ii | Caliber Isp 04 Team 1 | +3.21% | +3.21% | +0.00 | +0 | 0.66 | 0.66 | NORMAL |
| no_van_driest_ii | Caliber Isp 04 Team 2 | +4.85% | +4.85% | +0.00 | +0 | 0.64 | 0.64 | NORMAL |
| no_van_driest_ii | Caliber Isp 05 Columbia | -6.05% | -6.05% | +0.00 | +0 | 0.84 | 0.84 | NORMAL |
| no_van_driest_ii | Caliber Isp 05 Discovery | -3.20% | -3.20% | +0.00 | +0 | 0.81 | 0.81 | NORMAL |
| no_van_driest_ii | Kline-Rogers L500 | -2.39% | -2.39% | +0.00 | +1 | 1.98 | 1.99 | NORMAL |
| no_van_driest_ii | Don't Debate This | -6.05% | -1.53% | +4.52 | +2558 | 3.04 | 3.06 | NORMAL |
| no_van_driest_ii | Qu8k | -1.89% | +6.06% | +7.94 | +9649 | 3.46 | 3.49 | NORMAL |
| no_van_driest_ii | Proteus 6 | +7.37% | +14.10% | +6.73 | +5726 | 2.87 | 2.89 | NORMAL |
| no_van_driest_ii | Full Metal Jacket BALLS 005 | -1.91% | -1.30% | +0.61 | +230 | 2.31 | 2.31 | NORMAL |
| no_van_driest_ii | Full Metal Jacket Black Rock 6 | -2.66% | -2.46% | +0.21 | +62 | 2.46 | 2.47 | NORMAL |
| no_van_driest_ii | A-601 Kinsel | +8.72% | +9.78% | +1.06 | +455 | 2.19 | 2.20 | NORMAL |
| no_van_driest_ii | AeroPac 104K | -1.01% | -1.05% | -0.04 | -45 | 3.04 | 3.04 | NORMAL |
| no_k1_floor | Byrum | +7.49% | +7.49% | +0.00 | +0 | 0.75 | 0.75 | NORMAL |
| no_k1_floor | Cancer Descending | -2.33% | -2.33% | +0.00 | +0 | 0.56 | 0.56 | NORMAL |
| no_k1_floor | EZI-65 J450ST | +4.87% | +4.87% | +0.00 | +0 | 0.60 | 0.60 | NORMAL |
| no_k1_floor | Gibb | +1.94% | +1.94% | +0.00 | +0 | 0.55 | 0.55 | NORMAL |
| no_k1_floor | Ion Drive | -3.71% | -3.71% | +0.00 | +0 | 0.79 | 0.79 | NORMAL |
| no_k1_floor | Raven | +7.64% | +7.64% | +0.00 | +0 | 1.07 | 1.07 | NORMAL |
| no_k1_floor | Thunder & Lightning | +8.37% | +8.37% | +0.00 | +0 | 0.54 | 0.54 | NORMAL |
| no_k1_floor | Blister | -8.40% | -8.40% | +0.00 | +0 | 0.83 | 0.83 | NORMAL |
| no_k1_floor | Rabia | -6.53% | -6.53% | +0.00 | +0 | 1.14 | 1.14 | NORMAL |
| no_k1_floor | Rabia Short Fin Can | -6.31% | -6.31% | +0.00 | +0 | 0.86 | 0.86 | NORMAL |
| no_k1_floor | Torrent | -2.75% | -2.75% | +0.00 | +0 | 1.22 | 1.22 | NORMAL |
| no_k1_floor | Caliber Isp 04 Team 3 | -1.90% | -1.90% | +0.00 | +0 | 0.64 | 0.64 | NORMAL |
| no_k1_floor | Caliber Isp 04 Team 1 | +3.21% | +3.21% | +0.00 | +0 | 0.66 | 0.66 | NORMAL |
| no_k1_floor | Caliber Isp 04 Team 2 | +4.85% | +4.85% | +0.00 | +0 | 0.64 | 0.64 | NORMAL |
| no_k1_floor | Caliber Isp 05 Columbia | -6.05% | -6.05% | +0.00 | +0 | 0.84 | 0.84 | NORMAL |
| no_k1_floor | Caliber Isp 05 Discovery | -3.20% | -3.20% | +0.00 | +0 | 0.81 | 0.81 | NORMAL |
| no_k1_floor | Kline-Rogers L500 | -2.39% | -2.39% | +0.00 | +0 | 1.98 | 1.98 | NORMAL |
| no_k1_floor | Don't Debate This | -6.05% | -6.05% | -0.00 | -0 | 3.04 | 3.04 | NORMAL |
| no_k1_floor | Qu8k | -1.89% | -1.89% | -0.00 | -0 | 3.46 | 3.46 | NORMAL |
| no_k1_floor | Proteus 6 | +7.37% | +7.37% | +0.00 | +0 | 2.87 | 2.87 | NORMAL |
| no_k1_floor | Full Metal Jacket BALLS 005 | -1.91% | -1.91% | +0.00 | +0 | 2.31 | 2.31 | NORMAL |
| no_k1_floor | Full Metal Jacket Black Rock 6 | -2.66% | -2.66% | +0.00 | +0 | 2.46 | 2.46 | NORMAL |
| no_k1_floor | A-601 Kinsel | +8.72% | +8.72% | -0.00 | -0 | 2.19 | 2.19 | NORMAL |
| no_k1_floor | AeroPac 104K | -1.01% | -1.01% | +0.00 | +0 | 3.04 | 3.04 | NORMAL |
| no_finned_base_aug | Byrum | +7.49% | +8.90% | +1.41 | +81 | 0.75 | 0.75 | NORMAL |
| no_finned_base_aug | Cancer Descending | -2.33% | +3.62% | +5.95 | +368 | 0.56 | 0.57 | NORMAL |
| no_finned_base_aug | EZI-65 J450ST | +4.87% | +13.44% | +8.57 | +340 | 0.60 | 0.61 | NORMAL |
| no_finned_base_aug | Gibb | +1.94% | +9.33% | +7.39 | +289 | 0.55 | 0.55 | NORMAL |
| no_finned_base_aug | Ion Drive | -3.71% | -2.84% | +0.86 | +69 | 0.79 | 0.79 | NORMAL |
| no_finned_base_aug | Raven | +7.64% | +20.77% | +13.13 | +1158 | 1.07 | 1.10 | NORMAL |
| no_finned_base_aug | Thunder & Lightning | +8.37% | +15.78% | +7.41 | +265 | 0.54 | 0.55 | NORMAL |
| no_finned_base_aug | Blister | -8.40% | -3.82% | +4.58 | +413 | 0.83 | 0.84 | NORMAL |
| no_finned_base_aug | Rabia | -6.53% | +1.74% | +8.26 | +1053 | 1.14 | 1.17 | NORMAL |
| no_finned_base_aug | Rabia Short Fin Can | -6.31% | -0.22% | +6.09 | +645 | 0.86 | 0.88 | NORMAL |
| no_finned_base_aug | Torrent | -2.75% | +7.11% | +9.86 | +1263 | 1.22 | 1.25 | NORMAL |
| no_finned_base_aug | Caliber Isp 04 Team 3 | -1.90% | -0.21% | +1.69 | +67 | 0.64 | 0.64 | NORMAL |
| no_finned_base_aug | Caliber Isp 04 Team 1 | +3.21% | +5.18% | +1.97 | +76 | 0.66 | 0.67 | NORMAL |
| no_finned_base_aug | Caliber Isp 04 Team 2 | +4.85% | +6.67% | +1.82 | +67 | 0.64 | 0.64 | NORMAL |
| no_finned_base_aug | Caliber Isp 05 Columbia | -6.05% | -2.98% | +3.08 | +156 | 0.84 | 0.85 | NORMAL |
| no_finned_base_aug | Caliber Isp 05 Discovery | -3.20% | -0.33% | +2.87 | +142 | 0.81 | 0.82 | NORMAL |
| no_finned_base_aug | Kline-Rogers L500 | -2.39% | +7.96% | +10.35 | +2564 | 1.98 | 2.07 | NORMAL |
| no_finned_base_aug | Don't Debate This | -6.05% | +12.48% | +18.53 | +10484 | 3.04 | 3.10 | NORMAL |
| no_finned_base_aug | Qu8k | -1.89% | +8.12% | +10.01 | +12154 | 3.46 | 3.52 | NORMAL |
| no_finned_base_aug | Proteus 6 | +7.37% | +16.90% | +9.52 | +8102 | 2.87 | 2.92 | NORMAL |
| no_finned_base_aug | Full Metal Jacket BALLS 005 | -1.91% | +9.29% | +11.20 | +4253 | 2.31 | 2.32 | NORMAL |
| no_finned_base_aug | Full Metal Jacket Black Rock 6 | -2.66% | +3.72% | +6.38 | +1917 | 2.46 | 2.48 | NORMAL |
| no_finned_base_aug | A-601 Kinsel | +8.72% | +48.22% | +39.50 | +16896 | 2.19 | 2.42 | NORMAL |
| no_finned_base_aug | AeroPac 104K | -1.01% | +3.05% | +4.06 | +4246 | 3.04 | 3.07 | NORMAL |
| no_datcom_fin_wave_drag | Byrum | +7.49% | +7.49% | +0.00 | +0 | 0.75 | 0.75 | NORMAL |
| no_datcom_fin_wave_drag | Cancer Descending | -2.33% | -2.33% | +0.00 | +0 | 0.56 | 0.56 | NORMAL |
| no_datcom_fin_wave_drag | EZI-65 J450ST | +4.87% | +4.87% | +0.00 | +0 | 0.60 | 0.60 | NORMAL |
| no_datcom_fin_wave_drag | Gibb | +1.94% | +1.94% | +0.00 | +0 | 0.55 | 0.55 | NORMAL |
| no_datcom_fin_wave_drag | Ion Drive | -3.71% | -3.71% | +0.00 | +0 | 0.79 | 0.79 | NORMAL |
| no_datcom_fin_wave_drag | Raven | +7.64% | +7.70% | +0.06 | +5 | 1.07 | 1.07 | NORMAL |
| no_datcom_fin_wave_drag | Thunder & Lightning | +8.37% | +8.37% | +0.00 | +0 | 0.54 | 0.54 | NORMAL |
| no_datcom_fin_wave_drag | Blister | -8.40% | -8.40% | +0.00 | +0 | 0.83 | 0.83 | NORMAL |
| no_datcom_fin_wave_drag | Rabia | -6.53% | -6.45% | +0.07 | +9 | 1.14 | 1.14 | NORMAL |
| no_datcom_fin_wave_drag | Rabia Short Fin Can | -6.31% | -6.31% | -0.00 | -0 | 0.86 | 0.86 | NORMAL |
| no_datcom_fin_wave_drag | Torrent | -2.75% | -2.53% | +0.22 | +28 | 1.22 | 1.22 | NORMAL |
| no_datcom_fin_wave_drag | Caliber Isp 04 Team 3 | -1.90% | -1.90% | +0.00 | +0 | 0.64 | 0.64 | NORMAL |
| no_datcom_fin_wave_drag | Caliber Isp 04 Team 1 | +3.21% | +3.21% | +0.00 | +0 | 0.66 | 0.66 | NORMAL |
| no_datcom_fin_wave_drag | Caliber Isp 04 Team 2 | +4.85% | +4.85% | +0.00 | +0 | 0.64 | 0.64 | NORMAL |
| no_datcom_fin_wave_drag | Caliber Isp 05 Columbia | -6.05% | -6.05% | +0.00 | +0 | 0.84 | 0.84 | NORMAL |
| no_datcom_fin_wave_drag | Caliber Isp 05 Discovery | -3.20% | -3.20% | +0.00 | +0 | 0.81 | 0.81 | NORMAL |
| no_datcom_fin_wave_drag | Kline-Rogers L500 | -2.39% | -1.04% | +1.36 | +336 | 1.98 | 2.00 | NORMAL |
| no_datcom_fin_wave_drag | Don't Debate This | -6.05% | -4.62% | +1.43 | +811 | 3.04 | 3.04 | NORMAL |
| no_datcom_fin_wave_drag | Qu8k | -1.89% | -0.98% | +0.91 | +1101 | 3.46 | 3.47 | NORMAL |
| no_datcom_fin_wave_drag | Proteus 6 | +7.37% | +9.30% | +1.92 | +1636 | 2.87 | 2.88 | NORMAL |
| no_datcom_fin_wave_drag | Full Metal Jacket BALLS 005 | -1.91% | -1.52% | +0.38 | +146 | 2.31 | 2.31 | NORMAL |
| no_datcom_fin_wave_drag | Full Metal Jacket Black Rock 6 | -2.66% | -2.39% | +0.27 | +82 | 2.46 | 2.47 | NORMAL |
| no_datcom_fin_wave_drag | A-601 Kinsel | +8.72% | +10.22% | +1.50 | +643 | 2.19 | 2.20 | NORMAL |
| no_datcom_fin_wave_drag | AeroPac 104K | -1.01% | +0.20% | +1.21 | +1270 | 3.04 | 3.05 | NORMAL |

## Interpretation

Each mutation reverts a single calculator mechanism to its pre-supersonic-uplift baseline:

- **no_shockgeometry** (ShockGeometry pre-pass disabled): avg |err| changes from 4.65% to 4.58% (signed delta +0.15 pp, max |delta| 3.63 pp, abnormal 0/24).
- **no_pnk** (Pitts-Nielsen-Kaattari interference disabled (F_WB=F_BW=1)): avg |err| changes from 4.65% to 4.65% (signed delta -0.00 pp, max |delta| 0.00 pp, abnormal 0/24).
- **no_van_driest_ii** (Van Driest II skin friction disabled (incompressible Cf)): avg |err| changes from 4.65% to 4.93% (signed delta +0.87 pp, max |delta| 7.94 pp, abnormal 0/24).
- **no_k1_floor** (Mach-blended K1 floor disabled (no fin CNa floor)): avg |err| changes from 4.65% to 4.65% (signed delta -0.00 pp, max |delta| 0.00 pp, abnormal 0/24).
- **no_finned_base_aug** (Finned-body base augmentation disabled): avg |err| changes from 4.65% to 8.86% (signed delta +8.10 pp, max |delta| 39.50 pp, abnormal 0/24).
- **no_datcom_fin_wave_drag** (DATCOM 4.1.5.1 fin wave drag disabled): avg |err| changes from 4.65% to 4.57% (signed delta +0.39 pp, max |delta| 1.92 pp, abnormal 0/24).

Mutations with negligible aggregate delta are not regressions — they indicate the mechanism is dormant on this corpus (e.g., DATCOM fin wave drag is small compared to body wave drag, or the K1 floor only fires on the few low-AR swept fins). The per-case delta table identifies the rockets where each mechanism is load-bearing.
