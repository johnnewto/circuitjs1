# World2 SFCR Model

## Init

```{r}
@init
  timestep: 0.2
  timeUnit: yr
  equationTableMnaMode: false
  equationTableTolerance: 0.001
  convergenceCheckThreshold: 5000
  showDots: false
  showVolts: false
@end
```

## Equations

```{r}
World2 <- sfcr_set(
  # [ x=50 y=50 ]
  e1 = BRN ~ 0.04,  # [mode=voltage, sliderValue=0 ]
  e2 = DRN ~ 0.028,  # [mode=voltage, sliderValue=0 ]
  e3 = CIDN ~ 0.025,  # [mode=voltage, sliderValue=0 ]
  e4 = CIGN ~ 0.05,  # [mode=voltage, sliderValue=0 ]
  e5 = NRUN ~ 1,  # [mode=voltage, sliderValue=0 ]
  e6 = POLN ~ 1,  # [mode=voltage, sliderValue=0 ]
  e7 = FC_COEFF ~ 1,  # [mode=voltage, sliderValue=0 ]
  e8 = LA ~ 1.35e8,  # [mode=voltage, sliderValue=0 ]
  e9 = PDN ~ 26.5,  # [mode=voltage, sliderValue=0 ]
  e10 = CIAFN ~ 0.3,  # [mode=voltage, sliderValue=0 ]
  e11 = ECIRN ~ 1,  # [mode=voltage, sliderValue=0 ]
  e12 = CIAFT ~ 15,  # [mode=voltage, sliderValue=0 ]
  e13 = POLS ~ 3.6e9,  # [mode=voltage, sliderValue=0 ]
  e14 = FN ~ 1,  # [mode=voltage, sliderValue=0 ]
  e15 = QLS ~ 1,  # [mode=voltage, sliderValue=0 ]
  e16 = P_0 ~ 1.65e9,  # [mode=voltage, sliderValue=0 ]
  e17 = NR_0 ~ 9e11,  # [mode=voltage, sliderValue=0 ]
  e18 = CR ~ P / (LA * PDN),  # [mode=voltage, sliderValue=0 ]
  e19 = CIR ~ CI / max(1, P),  # [mode=voltage, sliderValue=0 ]
  e20 = NRFR ~ max(0, NR) / NR_0,  # [mode=voltage, sliderValue=0 ]
  e21 = POLR ~ POL / POLS,  # Pollution ratio  [mode=voltage, sliderValue=0 ]
  e22 = BR ~ P * BRN * lookup(BR_lookup, clamp(MSL, 0, 5)) * lookup(BR_lookup_2, clamp(CR, 0, 5)) * lookup(BR_lookup_3, clamp(FR, 0, 4)) * lookup(BR_lookup_4, clamp(POLR, 0, 60)),  # [mode=voltage, sliderValue=0 ]
  e23 = DR ~ P * DRN * lookup(DR_lookup, clamp(MSL, 0, 5)) * lookup(DR_lookup_2, clamp(POLR, 0, 60)) * lookup(DR_lookup_3, clamp(FR, 0, 2)) * lookup(DR_lookup_4, clamp(CR, 0, 5)),  # [mode=voltage, sliderValue=0 ]
  e24 = NRUR ~ P * NRUN * lookup(NRUR_lookup, clamp(MSL, 0, 10)),  # [mode=voltage, sliderValue=0 ]
  e25 = CID ~ CI * CIDN,  # [mode=voltage, sliderValue=0 ]
  e26 = CIG ~ P * lookup(CIG_lookup, clamp(MSL, 0, 5)) * CIGN,  # [mode=voltage, sliderValue=0 ]
  e27 = POLG ~ P * POLN * lookup(POLG_lookup, clamp(CIR, 0, 5)),  # [mode=voltage, sliderValue=0 ]
  e28 = POLA ~ POL / max(1e-9, lookup(POLA_lookup, clamp(POLR, 0, 60))),  # [mode=voltage, sliderValue=0 ]
  e29 = CFIFR ~ lookup(CFIFR_lookup, clamp(FR, 0, 2)),  # [mode=voltage, sliderValue=0 ]
  e30 = QLM ~ lookup(QLM_lookup, clamp(MSL, 0, 5)),  # [mode=voltage, sliderValue=0 ]
  e31 = QLP ~ lookup(QLP_lookup, clamp(POLR, 0, 60)),  # [mode=voltage, sliderValue=0 ]
  e32 = QLF ~ lookup(QLF_lookup, clamp(FR, 0, 4)),  # [mode=voltage, sliderValue=0 ]
  e33 = CIQR ~ lookup(CIQR_lookup, clamp(QLM / max(1e-9, QLF), 0, 2)),  # [mode=voltage, sliderValue=0 ]
  e34 = CIAF_D ~ CFIFR * CIQR,  # [mode=voltage, sliderValue=0 ]
  e35 = CIRA ~ CIR * CIAF / max(1e-9, CIAFN),  # [mode=voltage, sliderValue=0 ]
  e36 = FCM ~ lookup(FCM_lookup, clamp(CR, 0, 5)),  # [mode=voltage, sliderValue=0 ]
  e37 = FPCI ~ lookup(FPCI_lookup, clamp(CIRA, 0, 6)),  # [mode=voltage, sliderValue=0 ]
  e38 = FPM ~ lookup(FPM_lookup, clamp(POLR, 0, 60)),  # [mode=voltage, sliderValue=0 ]
  e39 = FR ~ (FCM * FPCI * FPM * FC_COEFF) / FN,  # Food ratio  [mode=voltage, sliderValue=0 ]
  e40 = NREM ~ lookup(NREM_lookup, clamp(NRFR, 0, 1)),  # [mode=voltage, sliderValue=0 ]
  e41 = ECIR ~ (CIR * (1 - CIAF) * NREM) / max(1e-9, (1 - CIAFN)),  # [mode=voltage, sliderValue=0 ]
  e42 = MSL ~ ECIR / ECIRN,  # Material standard of living  [mode=voltage, sliderValue=0 ]
  e43 = QLC ~ lookup(QLC_lookup, clamp(CR, 0, 5)),  # [mode=voltage, sliderValue=0 ]
  e44 = QL ~ QLS * QLM * QLC * QLF * QLP,  # Quality of life  [mode=voltage, sliderValue=0 ]
  e45 = P_norm ~ P / P_0,  # [mode=voltage, sliderValue=0 ]
  e46 = P ~ integrate(BR - DR),  # Population (people)  [mode=voltage, sliderValue=0, initial=1.65e9 ]
  e47 = NR ~ integrate(-NRUR),  # Nonrenewable natural resources  [mode=voltage, sliderValue=0, initial=9e11 ]
  e48 = CI ~ integrate(CIG - CID),  # Capital investment  [mode=voltage, sliderValue=0, initial=4e8 ]
  e49 = POL ~ integrate(POLG - POLA),  # Pollution stock  [mode=voltage, sliderValue=0, initial=2e8 ]
  e50 = CIAF ~ integrate((CIAF_D - CIAF) / CIAFT)  # Capital-investment-in-agriculture fraction  [mode=voltage, sliderValue=0, initial=0.2 ]
)
```

## Hints

```{r}
@hints
  P: Population (people)
  NR: Nonrenewable natural resources
  CI: Capital investment
  POL: Pollution stock
  CIAF: Capital-investment-in-agriculture fraction
  FR: Food ratio
  MSL: Material standard of living
  QL: Quality of life
  POLR: Pollution ratio
@end
```

## Info

```{r}
@info
# World2 (pyworld2-aligned default run)

This variant is tuned to match the default **pyworld2** standard run structure:

- timestep: 0.2 years
- default switch constants (BRN, DRN, CIDN, CIGN, FC, NRUN, POLN)
- lookup tables copied from `functions_table_default.json`
- equations follow the pyworld2 variable chain (`FR`, `MSL`, `NRMM`, `POLAT`, `CIAF` dynamics, etc.)

Reference target from pyworld2 tests at year 2100:

- QL ≈ 0.54940464789
- POLR ≈ 2.58741372815
- NR ≈ 278240023740
- CI ≈ 6010240430.13
@end
```

## Lookup Tables (World2 Scoped)

```{r}
@lookup BR_lookup scope=World2
  0, 1.2
  1, 1
  2, 0.85
  3, 0.75
  4, 0.7
  5, 0.7
@end
```

```{r}
@lookup BR_lookup_2 scope=World2
  0, 1.05
  1, 1
  2, 0.9
  3, 0.7
  4, 0.6
  5, 0.55
@end
```

```{r}
@lookup BR_lookup_3 scope=World2
  0, 0
  1, 1
  2, 1.6
  3, 1.9
  4, 2
@end
```

```{r}
@lookup BR_lookup_4 scope=World2
  0, 1.02
  10, 0.9
  20, 0.7
  30, 0.4
  40, 0.25
  50, 0.15
  60, 0.1
@end
```

```{r}
@lookup DR_lookup scope=World2
  0, 3
  0.5, 1.8
  1, 1
  1.5, 0.8
  2, 0.7
  2.5, 0.6
  3, 0.53
  3.5, 0.5
  4, 0.5
  4.5, 0.5
  5, 0.5
@end
```

```{r}
@lookup DR_lookup_2 scope=World2
  0, 0.92
  10, 1.3
  20, 2
  30, 3.2
  40, 4.8
  50, 6.8
  60, 9.2
@end
```

```{r}
@lookup DR_lookup_3 scope=World2
  0, 30
  0.25, 3
  0.5, 2
  0.75, 1.4
  1, 1
  1.25, 0.7
  1.5, 0.6
  1.75, 0.5
  2, 0.5
@end
```

```{r}
@lookup DR_lookup_4 scope=World2
  0, 0.9
  1, 1
  2, 1.2
  3, 1.5
  4, 1.9
  5, 3
@end
```

```{r}
@lookup NRUR_lookup scope=World2
  0, 0
  1, 1
  2, 1.8
  3, 2.4
  4, 2.9
  5, 3.3
  6, 3.6
  7, 3.8
  8, 3.9
  9, 3.95
  10, 4
@end
```

```{r}
@lookup CIG_lookup scope=World2
  0, 0.1
  1, 1
  2, 1.8
  3, 2.4
  4, 2.8
  5, 3
@end
```

```{r}
@lookup POLG_lookup scope=World2
  0, 0.05
  1, 1
  2, 3
  3, 5.4
  4, 7.4
  5, 8
@end
```

```{r}
@lookup POLA_lookup scope=World2
  0, 0.6
  10, 2.5
  20, 5
  30, 8
  40, 11.5
  50, 15.5
  60, 20
@end
```

```{r}
@lookup CFIFR_lookup scope=World2
  0, 1
  0.5, 0.6
  1, 0.3
  1.5, 0.15
  2, 0.1
@end
```

```{r}
@lookup QLM_lookup scope=World2
  0, 0.2
  1, 1
  2, 1.7
  3, 2.3
  4, 2.7
  5, 2.9
@end
```

```{r}
@lookup QLP_lookup scope=World2
  0, 1.04
  10, 0.85
  20, 0.6
  30, 0.3
  40, 0.15
  50, 0.05
  60, 0.02
@end
```

```{r}
@lookup QLF_lookup scope=World2
  0, 0
  1, 1
  2, 1.8
  3, 2.4
  4, 2.7
@end
```

```{r}
@lookup CIQR_lookup scope=World2
  0, 0.7
  0.5, 0.8
  1, 1
  1.5, 1.5
  2, 2
@end
```

```{r}
@lookup FCM_lookup scope=World2
  0, 2.4
  1, 1
  2, 0.6
  3, 0.4
  4, 0.3
  5, 0.2
@end
```

```{r}
@lookup FPCI_lookup scope=World2
  0, 0.5
  1, 1
  2, 1.4
  3, 1.7
  4, 1.9
  5, 2.05
  6, 2.2
@end
```

```{r}
@lookup FPM_lookup scope=World2
  0, 1.02
  10, 0.9
  20, 0.65
  30, 0.35
  40, 0.2
  50, 0.1
  60, 0.05
@end
```

```{r}
@lookup NREM_lookup scope=World2
  0, 0
  0.25, 0.15
  0.5, 0.5
  0.75, 0.85
  1, 1
@end
```

```{r}
@lookup QLC_lookup scope=World2
  0, 2
  0.5, 1.3
  1, 1
  1.5, 0.75
  2, 0.55
  2.5, 0.45
  3, 0.38
  3.5, 0.3
  4, 0.25
  4.5, 0.22
  5, 0.2
@end
```

```{r}
@scope QL position=0
  speed: 16
  flags: x2001206
  source: uid:xJHWxB value:0
@end
```

```{r}
@scope P position=1
  speed: 16
  flags: x2001206
  source: uid:BaMGZo value:0
@end
```

```{r}
@scope POLR position=2
  speed: 16
  flags: x2001206
  source: uid:PwVDmt value:0
@end
```

```{r}
@circuit
207 -208 192 -144 192 164 P U:BaMGZo
207 -208 224 -144 224 164 POLR U:PwVDmt
207 -208 144 -144 144 164 QL U:xJHWxB
431 -144 352 -112 448 0 200 true false U:huagx4
@end
```

