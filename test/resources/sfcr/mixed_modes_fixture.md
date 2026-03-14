# Mixed mode equation fixture
```{circuit}
@init
  timestep: 1
@end
```

```{r}
@equations MixedModes
  Vout ~ 10
  FlowAB ~ 5 ; mode=flow; target=NodeB
  Gain ~ 2 ; mode=param
@end
```

```{r}
@hints
  Vout: Voltage output row
  FlowAB: Flow row
  Gain: Parameter row
@end
```
