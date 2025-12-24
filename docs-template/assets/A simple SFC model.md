# A Simple SFC Model

**Yannis Dafermos** (University of the West of England)  
**Maria Nikolaidi** (University of Greenwich)  

**August 2019**

## 1. Brief description

This is a simple SFC model that consists of three sectors: firms, households and banks. Firms undertake investment by using retained profits and loans. A part of firms' profits is distributed to households. Households accumulate savings in the form of deposits. Banks provide firm loans by creating deposits. Banks' profits are distributed to households. In the model, loans are endogenously created when firms receive credit from banks. The model is calibrated using data for the US economy over the period 1960-2010.

The balance sheet matrix and the transactions flow matrix of the model are shown below.

### Balance sheet matrix

|                  | Households | Firms     | Commercial banks | Total |
|------------------|------------|-----------|------------------|-------|
| Deposits         | +D         |           | –D               | 0     |
| Loans            |            | –L        | +L               | 0     |
| Capital          |            | +K        |                  | +K    |
| **Total (net worth)** | +D     | +V<sub>F</sub> | 0                | +K    |

### Transactions flow matrix

|                          | Households          |            | Firms               |            | Commercial banks    |            | Total |
|--------------------------|---------------------|------------|---------------------|------------|---------------------|------------|-------|
|                          | Current | Capital    | Current | Capital    | Current | Capital    |       |
| Consumption              | –C      |            | +C      |            |                     |            | 0     |
| Investment               |         | +I         | –I      |            |                     |            | 0     |
| Wages                    | +W      |            | –W      |            |                     |            | 0     |
| Firms' profits           | +DP     |            | –TP     | +RP        |                     |            | 0     |
| Banks' profits           | +BP     |            |         |            | –BP                 |            | 0     |
| Interest on deposits     | +int<sub>D</sub> D<sub>-1</sub> | |         |            | –int<sub>D</sub> D<sub>-1</sub> | | 0     |
| Interest on loans        |         |            | –int<sub>L</sub> L<sub>-1</sub> | | +int<sub>L</sub> L<sub>-1</sub> | | 0     |
| Change in deposits       | –ΔD     |            |         |            |                     | +ΔD        | 0     |
| Change in loans          |         |            | +ΔL     |            |                     | –ΔL        | 0     |
| **Total**                | 0       | 0          | 0       | 0          | 0                   | 0          | 0     |

## 2. Model equations

### Households

$$
W = s_w Y \tag{1}
$$

$$
Y_C = DP + BP + int_D D_{-1} \tag{2}
$$

$$
C = c_1 W_{-1} + c_2 Y_{C-1} + c_3 D_{-1} \tag{3}
$$

$$
D = D_{-1} + W + Y_C - C \tag{4}
$$

### Firms

$$
Y = C + I \tag{5}
$$

$$
TP = Y - W - int_L L_{-1} \tag{6}
$$

$$
RP = s_F TP_{-1} \tag{7}
$$

$$
DP = TP - RP \tag{8}
$$

$$
I = g_K K_{-1} \tag{9}
$$

$$
K = K_{-1} + I \tag{10}
$$

$$
L = L_{-1} + I - RP \tag{11}
$$

### Banks

$$
BP = int_L L_{-1} - int_D D_{-1} \tag{12}
$$

$$
D_{red} = L \tag{13}
$$

### Auxiliary equations

$$
Y^* = v K \tag{14}
$$

$$
u = Y / Y^* \tag{15}
$$

$$
g_Y = (Y - Y_{-1}) / Y_{-1} \tag{16}
$$

$$
lev = L / K \tag{17}
$$

## 3. Symbols and values

| Symbol | Description                              | Value/calculation                                      |
|--------|------------------------------------------|--------------------------------------------------------|
| **Parameters** |                                          |                                                        |
| c₁     | Propensity to consume out of wage income | 0.9 [Category B(iii)]                                  |
| c₂     | Propensity to consume out of capital income | 0.75 [Category B(iii)]                              |
| c₃     | Propensity to consume out of deposits    | 0.473755074 [Category C(ii)]                           |
| g_K    | Growth rate of capital                   | US 1960-2010 mean value of g_Y [Category C(ii)]        |
| int_D  | Interest rate on deposits                | US 1960-2010 mean value of int_D [Category B(i)]       |
| int_L  | Interest rate on loans                   | US 1960-2010 mean value of int_L [Category B(i)]       |
| s_F    | Retention rate of firms                  | 0.17863783 [Category C(ii)]                            |
| s_w    | Wage share                               | US 1960-2010 mean value of s_w [Category B(i)]         |
| v      | Capital productivity                     | Calculated using equations (14) and (15) [Category C(i)] |
| **Endogenous variables** |                                  |                                                        |
| W      | Wage income of households                | Calculated from equation (1)                           |
| Y_C    | Capital income of households             | Calculated from equation (2)                           |
| C      | Consumption expenditures                 | Calculated from equation (3)                           |
| D      | Deposits                                 | Calculated from equation (13)                          |
| Y      | Output                                   | US 1960 value (in trillion 2009 US$)                   |
| TP     | Total profits of firms                   | Calculated from equation (6)                           |
| RP     | Retained profits                         | Calculated from equation (7)                           |
| DP     | Distributed profits                      | Calculated from equation (8)                           |
| I      | Investment                               | Calculated from equation (9)                           |
| K      | Capital stock                            | US 1960 value (in trillion 2009 US$)                   |
| L      | Loans                                    | US 1960 value (in trillion 2009 US$)                   |
| BP     | Profits of banks                         | Calculated from equation (12)                          |
| D_red  | Deposits (redundant)                     | Calculated from equation (13)                          |
| Y*     | Potential output                         | Calculated from equation (14)                          |
| u      | Capacity utilisation                     | US 1960 value                                          |
| g_Y    | Growth rate of output                    | US 1960-2010 mean value of g_Y                         |
| lev    | Leverage ratio                           | Calculated from equation (17)                          |

**Note:** For the different categories of parameters, see Appendix A.

## 4. Steps for simulating the model in R

```r
# Open R and create a new R script (File -> New file -> R script). Save this file as 'Model'.

rm(list=ls(all=TRUE))
T <- 51

# Download the excel file that contains the US data for the period 1960-2010...
Data <- read.csv("C:/users/user/Desktop/Data.csv")
# Adjust path as needed

# STEP 1: Identify the endogenous variables
W   <- vector(length=T)
Y_C <- vector(length=T)
CO  <- vector(length=T)  # Consumption
D   <- vector(length=T)
Y   <- vector(length=T)
TP  <- vector(length=T)
RP  <- vector(length=T)
DP  <- vector(length=T)
I   <- vector(length=T)
K   <- vector(length=T)
L   <- vector(length=T)
BP  <- vector(length=T)
D_red <- vector(length=T)
Y_star <- vector(length=T)  # auxiliary
u   <- vector(length=T)     # auxiliary
g_Y <- vector(length=T)     # auxiliary
lev <- vector(length=T)     # auxiliary

# STEP 2 & 3: Parameters and initial values (inside loop)
for (i in 1:T) {
  if (i == 1) {
    for (iterations in 1:10) {
      c_1 <- 0.9
      c_2 <- 0.75
      c_3 <- 0.473755074
      g_K <- mean(Data[,c("g_Y")])
      int_D <- mean(Data[,c("int_D")])
      int_L <- mean(Data[,c("int_L")])
      s_F <- 0.17863783
      v <- Y[i]/(K[i]*u[i])

      # Initial values
      W[i]   <- s_W * Y[i]
      Y_C[i] <- DP[i] + BP[i] + int_D * (D[i]/(1+g_K))
      CO[i]  <- Y[i] - I[i]
      D[i]   <- L[i]
      Y[i]   <- Data[1,c("Y")]
      TP[i]  <- Y[i] - W[i] - int_L * (L[i]/(1+g_K))
      RP[i]  <- s_F * TP[i] / (1+g_K)
      DP[i]  <- TP[i] - RP[i]
      I[i]   <- (g_K/(1+g_K)) * K[i]
      K[i]   <- Data[1,c("K")]
      L[i]   <- Data[1,c("L")]
      BP[i]  <- int_L * (L[i]/(1+g_K)) - int_D * (D[i]/(1+g_K))
      D_red[i] <- L[i]
      Y_star[i] <- v * K[i]
      u[i]   <- Data[1,c("u")]
      g_Y[i] <- g_K
      lev[i] <- L[i]/K[i]
    }
  } else {
    for (iterations in 1:10) {
      # Households
      W[i]   <- s_W * Y[i]
      Y_C[i] <- DP[i] + BP[i] + int_D * D[i-1]
      CO[i]  <- c_1 * W[i-1] + c_2 * Y_C[i-1] + c_3 * D[i-1]
      D[i]   <- D[i-1] + W[i] + Y_C[i] - CO[i]

      # Firms
      Y[i]   <- CO[i] + I[i]
      TP[i]  <- Y[i] - W[i] - int_L * L[i-1]
      RP[i]  <- s_F * TP[i-1]
      DP[i]  <- TP[i] - RP[i]
      I[i]   <- g_K * K[i-1]
      K[i]   <- K[i-1] + I[i]
      L[i]   <- L[i-1] + I[i] - RP[i]

      # Banks
      BP[i]  <- int_L * L[i-1] - int_D * D[i-1]
      D_red[i] <- L[i]

      # Auxiliary
      Y_star[i] <- v * K[i]
      u[i]      <- Y[i] / Y_star[i]
      g_Y[i]    <- (Y[i] - Y[i-1]) / Y[i-1]
      lev[i]    <- L[i] / K[i]
    }
  }
}

# STEP 5: Report results
Table <- round(cbind(D_red, D, u, g_Y, lev, Y), digits=4)

# Example plots
plot(Data[1:T, "lev"], type="l", xlab="Year", ylab="Leverage ratio", xaxt="n")
lines(Table[1:T, "lev"], lty=3)
axis(1, at=c(1,11,21,31,41,51), labels=c("1960","1970","1980","1990","2000","2010"))
legend("bottomright", legend=c("Actual","Simulated"), lty=c(1,3))
# Similar plots for u, g_Y, Y...
```

---

### Shocks example (wage share follows data)

Replace constant `s_W` with:

```r
s_W <- Data[,c("s_W")]
```

And use `s_W[i]` in equations.

### Validation (autocorrelation)

```r
library(mFilter)

Y_log <- log(Table[,"Y"])
Yactual_log <- log(Data[,"Y"])

Y.hp <- hpfilter(Y_log, freq=6.25, drift=TRUE)
actualY.hp <- hpfilter(Yactual_log, freq=6.25, drift=TRUE)

acfYactual <- acf(actualY.hp$cycle, lag.max=20, plot=FALSE)
acfY <- acf(Y.hp$cycle, lag.max=20, plot=FALSE)

plot(acfYactual$acf, type="l", ylab="", xlab="Lag", ylim=c(-0.5,1))
lines(acfY$acf, lty=3)
legend("topright", legend=c("Actual","Simulated"), lty=c(1,3))
```

### Policy simulations

**Higher wage share after 1980:**

```r
s_W <- vector(length=T)
for (i in 1:T) {
  if (i < 21) s_W[i] <- Data[i,"s_W"] else s_W[i] <- 0.55
}
```

**Higher loan interest rate after 1980:**

```r
for (i in 1:T) {
  if (i < 21) int_L <- mean(Data[,"int_L"]) else int_L <- 0.25
}
```

## Appendix A: Categories of parameter values

| Category | Description                              |
|----------|------------------------------------------|
| (A)      | Econometrically estimated parameters     |
| (B)      | Directly calibrated parameters           |
| (Bi)     | Based on data                            |
| (Bii)    | Based on previous studies                |
| (Biii)   | Selected from a reasonable range of values |
| (C)      | Indirectly calibrated parameters         |
| (Ci)     | Calibrated such that the model matches the data |
| (Cii)    | Calibrated such that the model generates the baseline scenario |

## Appendix B: Estimating c₃ and s_F for the baseline scenario

### (a) Calibrating c₃ such that Y/K is constant

$$
c_3 = \frac{K}{L} \left( \frac{Y}{K} (1+g_K) - g_K - \left( c_1 \frac{W}{K} + c_2 \frac{Y_C}{K} \right) \right)
$$

### (b) Calibrating s_F such that L/K is constant

$$
s_F = \frac{g_K - g_K \frac{L}{K}}{\frac{TP}{K}}
$$

## Appendix C: An alternative investment function

The following non-linear investment function allows endogenous cycles:

```r
I[i] <- 0.2 * (mean(Data[,c("g_K")])) / (1 + exp(-10*(u[i-1]-0.8) + 180*(lev[i-1]-0.12))) * K[i-1]
```
```