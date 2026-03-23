# CircuitJS1 SFCR Export
# Generated from circuit simulation

```{r}
@init
  timestep: 0.05
  voltageUnit: $
  timeUnit: yr
  showDots: false
  showVolts: false
  showValues: true
  showPower: false
  autoAdjustTimestep: false
  equationTableMnaMode: true
  equationTableNewtonJacobianEnabled: false
  equationTableTolerance: 0.001
  lookupMode: pwl
  lookupClamp: true
  convergenceCheckThreshold: 199
  infoViewerUpdateIntervalMs: 100
@end
```

```{r}
@lookup BRMM scope=World2
  0, 1.2
  1, 1
  2, 0.85
  3, 0.75
  4, 0.7
  5, 0.7
@end

@lookup BRCM scope=World2
  0, 1.05
  1, 1
  2, 0.9
  3, 0.7
  4, 0.6
  5, 0.55
@end

@lookup BRFM scope=World2
  0, 0
  1, 1
  2, 1.6
  3, 1.9
  4, 2
@end

@lookup BRPM scope=World2
  0, 1.02
  10, 0.9
  20, 0.7
  30, 0.4
  40, 0.25
  50, 0.15
  60, 0.1
@end

@lookup DRMM scope=World2
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

@lookup DRPM scope=World2
  0, 0.92
  10, 1.3
  20, 2
  30, 3.2
  40, 4.8
  50, 6.8
  60, 9.2
@end

@lookup DRFM scope=World2
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

@lookup DRCM scope=World2
  0, 0.9
  1, 1
  2, 1.2
  3, 1.5
  4, 1.9
  5, 3
@end

@lookup NRMM scope=World2
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

@lookup CIM scope=World2
  0, 0.1
  1, 1
  2, 1.8
  3, 2.4
  4, 2.8
  5, 3
@end

@lookup POLCM scope=World2
  0, 0.05
  1, 1
  2, 3
  3, 5.4
  4, 7.4
  5, 8
@end

@lookup POLAT scope=World2
  0, 0.6
  10, 2.5
  20, 5
  30, 8
  40, 11.5
  50, 15.5
  60, 20
@end

@lookup CFIFR scope=World2
  0, 1
  0.5, 0.6
  1, 0.3
  1.5, 0.15
  2, 0.1
@end

@lookup QLM scope=World2
  0, 0.2
  1, 1
  2, 1.7
  3, 2.3
  4, 2.7
  5, 2.9
@end

@lookup QLP scope=World2
  0, 1.04
  10, 0.85
  20, 0.6
  30, 0.3
  40, 0.15
  50, 0.05
  60, 0.02
@end

@lookup QLF scope=World2
  0, 0
  1, 1
  2, 1.8
  3, 2.4
  4, 2.7
@end

@lookup CIQR scope=World2
  0, 0.7
  0.5, 0.8
  1, 1
  1.5, 1.5
  2, 2
@end

@lookup FCM scope=World2
  0, 2.4
  1, 1
  2, 0.6
  3, 0.4
  4, 0.3
  5, 0.2
@end

@lookup FPCI scope=World2
  0, 0.5
  1, 1
  2, 1.4
  3, 1.7
  4, 1.9
  5, 2.05
  6, 2.2
@end

@lookup FPM scope=World2
  0, 1.02
  10, 0.9
  20, 0.65
  30, 0.35
  40, 0.2
  50, 0.1
  60, 0.05
@end

@lookup NREM scope=World2
  0, 0
  0.25, 0.15
  0.5, 0.5
  0.75, 0.85
  1, 1
@end

@lookup QLC scope=World2
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

## Equations

```{r}
World2 <- sfcr_set(
  # [ x=50 y=50 ]
  e1 = BRN ~ 0.04,  # [mode=param, sliderValue=0 ]
  e2 = DRN ~ 0.028,  # [mode=param, sliderValue=0 ]
  e3 = CIDN ~ 0.025,  # [mode=param, sliderValue=0 ]
  e4 = CIGN ~ 0.05,  # [mode=param, sliderValue=0 ]
  e5 = NRUN ~ 1,  # [mode=param, sliderValue=0 ]
  e6 = POLN ~ 1,  # [mode=param, sliderValue=0 ]
  e7 = FC_COEFF ~ 1,  # [mode=param, sliderValue=0 ]
  e8 = LA ~ 1.35e8,  # [mode=param, sliderValue=0 ]
  e9 = PDN ~ 26.5,  # [mode=param, sliderValue=0 ]
  e10 = CIAFN ~ 0.3,  # [mode=param, sliderValue=0 ]
  e11 = ECIRN ~ 1,  # [mode=param, sliderValue=0 ]
  e12 = CIAFT ~ 15,  # [mode=param, sliderValue=0 ]
  e13 = POLS ~ 3.6e9,  # [mode=param, sliderValue=0 ]
  e14 = FN ~ 1,  # [mode=param, sliderValue=0 ]
  e15 = QLS ~ 1,  # [mode=param, sliderValue=0 ]
  e16 = P_0 ~ 1.65e9,  # [mode=param, sliderValue=0 ]
  e17 = NR_0 ~ 9e11,  # [mode=param, sliderValue=0 ]
  e18 = CR ~ P / (LA * PDN),  # [mode=param, sliderValue=0 ]
  e19 = CIR ~ CI / max(1, P),  # [mode=param, sliderValue=0 ]
  e20 = NRFR ~ max(0, NR) / NR_0,  # [mode=param, sliderValue=0 ]
  e21 = POLR ~ POL / POLS,  # Pollution ratio  [mode=param, sliderValue=0 ]
  e22 = BR ~ P * BRN * lookup(BRMM, MSL) * lookup(BRCM, CR) * lookup(BRFM, FR) * lookup(BRPM, POLR),  # [mode=param, sliderValue=0 ]
  e23 = DR ~ P * DRN * lookup(DRMM, MSL) * lookup(DRPM, POLR) * lookup(DRFM, FR) * lookup(DRCM, CR),  # [mode=param, sliderValue=0 ]
  e24 = NRUR ~ P * NRUN * lookup(NRMM, MSL),  # [mode=param, sliderValue=0 ]
  e25 = CID ~ CI * CIDN,  # [mode=param, sliderValue=0 ]
  e26 = CIG ~ P * lookup(CIM, MSL) * CIGN,  # [mode=param, sliderValue=0 ]
  e27 = POLG ~ P * POLN * lookup(POLCM, CIR),  # [mode=param, sliderValue=0 ]
  e28 = POLA ~ POL / max(1e-9, lookup(POLAT, POLR)),  # [mode=param, sliderValue=0 ]
  e29 = CFIFR ~ lookup(CFIFR, FR),  # [mode=param, sliderValue=0 ]
  e30 = QLM ~ lookup(QLM, MSL),  # [mode=param, sliderValue=0 ]
  e31 = QLP ~ lookup(QLP, POLR),  # [mode=param, sliderValue=0 ]
  e32 = QLF ~ lookup(QLF, FR),  # [mode=param, sliderValue=0 ]
  e33 = CIQR ~ lookup(CIQR, QLM / max(1e-9, QLF)),  # [mode=param, sliderValue=0 ]
  e34 = CIAF_D ~ CFIFR * CIQR,  # [mode=param, sliderValue=0 ]
  e35 = CIRA ~ CIR * CIAF / max(1e-9, CIAFN),  # [mode=param, sliderValue=0 ]
  e36 = FCM ~ lookup(FCM, CR),  # [mode=param, sliderValue=0 ]
  e37 = FPCI ~ lookup(FPCI, CIRA),  # [mode=param, sliderValue=0 ]
  e38 = FPM ~ lookup(FPM, POLR),  # [mode=param, sliderValue=0 ]
  e39 = FR ~ (FCM * FPCI * FPM * FC_COEFF) / FN,  # Food ratio  [mode=param, sliderValue=0 ]
  e40 = NREM ~ lookup(NREM, NRFR),  # [mode=param, sliderValue=0 ]
  e41 = ECIR ~ (CIR * (1 - CIAF) * NREM) / max(1e-9, (1 - CIAFN)),  # [mode=param, sliderValue=0 ]
  e42 = MSL ~ ECIR / ECIRN,  # Material standard of living  [mode=param, sliderValue=0 ]
  e43 = QLC ~ lookup(QLC, CR),  # [mode=param, sliderValue=0 ]
  e44 = QL ~ QLS * QLM * QLC * QLF * QLP,  # Quality of life  [mode=param, sliderValue=0 ]
  e45 = P_norm ~ P / P_0,  # [mode=param, sliderValue=0 ]
  e46 = P ~ integrate(BR - DR),  # Population (people)  [mode=param, sliderValue=0, initial=1.65e9 ]
  e47 = NR ~ integrate(-NRUR),  # Nonrenewable natural resources  [mode=param, sliderValue=0, initial=9e11 ]
  e48 = CI ~ integrate(CIG - CID),  # Capital investment  [mode=param, sliderValue=0, initial=4e8 ]
  e49 = POL ~ integrate(POLG - POLA),  # Pollution stock  [mode=param, sliderValue=0, initial=2e8 ]
  e50 = CIAF ~ integrate((CIAF_D - CIAF) / CIAFT)  # Capital-investment-in-agriculture fraction  [mode=param, sliderValue=0, initial=0.2 ]
)
```

```{r}
@circuit
207 -208 192 -144 192 164 P U:BaMGZo
207 -208 224 -144 224 164 POLR U:PwVDmt
207 -208 144 -144 144 164 QL U:xJHWxB
431 -144 352 -112 448 0 5 true false U:huagx4
w -464 384 -416 384 2 U:sfuGs4
w -416 384 -368 384 1 U:nuMe0n
g -288 544 -288 560 0 0 U:KWQ50O
w -288 512 -288 544 1 U:yjz-Aj
r -288 384 -288 512 0 1000 U:A_-Dqs
w -464 512 -464 544 1 U:ewym6B
g -464 544 -464 560 0 0 U:d--0QY
r -464 384 -464 512 0 100 U:Z7jyYq
R -464 384 -608 384 0 1 0.5 5 0 0 0.5 V U:st3MOa
x -750 249 -700 252 4 24 hello 808080FF U:te-6qy
d -368 384 -288 384 2 default U:_BOj85
409 -912 544 -752 544 1 0.6 -1.8896369273604239 0.023100000000000002 0 U:95hf5C
w -752 544 -656 544 2 U:IJPt23
R -912 560 -960 560 0 1 0.5 1 2.5 0 0.5 V U:k5F15f
g -832 576 -832 624 0 0 U:PA8T5w
R -832 512 -832 464 0 0 40 5 0 0 0.5 V U:OZC9Da
w -912 528 -912 432 0 U:MM5KeK
w -912 432 -752 432 0 U:aGKYqb
w -752 432 -752 544 0 U:WIkM_E
@end
```

```{r}
@scope QL position=0
  speed: 1
  flags: x2001206
  source: uid:xJHWxB value:0
@end
@scope P position=1
  speed: 1
  flags: x2001206
  source: uid:BaMGZo value:0
@end
@scope POLR position=2
  speed: 1
  flags: x2001206
  source: uid:PwVDmt value:0
@end
@scope Scope_4 position=3
  speed: 1
  flags: x2001206
  source: uid:st3MOa value:0
  trace: uid:A_-Dqs value:0
@end
@scope wire position=4
  speed: 1
  flags: x2001206
  source: uid:IJPt23 value:0
@end
```

