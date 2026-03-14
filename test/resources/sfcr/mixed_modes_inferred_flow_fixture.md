# Mixed mode equation fixture with inferred FLOW mode
```{circuit}
@init
  timestep: 1
@end
```

```{r}
@equations MixedModesInferred
  FlowSrc->NodeC ~ 7
  Vonly ~ 3
@end
```
