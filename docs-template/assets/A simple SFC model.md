A simple SFC model
Yannis Dafermos (University of the West of England)
Maria Nikolaidi (University of Greenwich)
August 2019
1. Brief description
This is a simple SFC model that consists of three sectors: firms, households and banks. Firms undertake investment by using retained profits and loans. A part of firms' profits is distributed to households. Households accumulate savings in the form of deposits. Banks provide firm loans by creating deposits. Banks' profits are distributed to households. In the model, loans are endogenously created when firms receive credit from banks. The model is calibrated using data for the US economy over the period 1960-2010.
The balance sheet matrix and the transactions flow matrix of the model are shown below.
Balance sheet matrix








































HouseholdsFirmsCommercial banksTotalDeposits+D-D0Loans-L+L0Capital+K+KTotal (net worth)+D+VF0+K
Transactions flow matrix





























































































































HouseholdsFirmsCommercial banksTotalCurrentCapitalCurrentCapitalCurrentCapitalConsumption-C+C0Investment+I-I0Wages+W-W0Firms' profits+DP-TP+RP0Banks' profits+BP-BP0Interest on deposits+intDD-1-intDD-10Interest on loans-intLL-1+intLL-10Change in deposits-ΔD+ΔD0Change in loans+ΔL-ΔL0Total0000000
2. Model equations
Households:
$$\text{Wage income of households: } W = s_w Y \quad (1)$$
$$\text{Capital income of households: } Y_C = DP + BP + int_D D_{-1} \quad (2)$$
$$\text{Consumption expenditures: } C = c_1 W_{-1} + c_2 Y_{C-1} + c_3 D_{-1} \quad (3)$$
$$\text{Deposits (identity): } D = D_{-1} + W + Y_C - C \quad (4)$$
Firms:
$$\text{Output: } Y = C + I \quad (5)$$
$$\text{Total profits of firms (identity): } TP = Y - W - int_L L_{-1} \quad (6)$$
$$\text{Retained profits: } RP = s_F TP_{-1} \quad (7)$$
$$\text{Distributed profits (identity): } DP = TP - RP \quad (8)$$
$$\text{Investment: } I = s_K K_{-1} \quad (9)$$
$$\text{Capital stock: } K = K_{-1} + I \quad (10)$$
$$\text{Loans (identity): } L = L_{-1} + I - RP \quad (11)$$
Banks:
$$\text{Profits of banks (identity): } BP = int_L L_{-1} - int_D D_{-1} \quad (12)$$
$$\text{Deposits (redundant identity): } D_{red} = L \quad (13)$$
Auxiliary equations:
$$\text{Potential output: } Y^* = vK \quad (14)$$
$$\text{Capacity utilisation: } u = Y / Y^* \quad (15)$$
$$\text{Growth rate of output: } g_Y = (Y - Y_{-1})/Y_{-1} \quad (16)$$
$$\text{Leverage ratio: } lev = L / K \quad (17)$$
3. Symbols and values






















































































































































SymbolDescriptionValue/calculationParametersc1Propensity to consume out of wage income0.9 [Category B(iii)]c2Propensity to consume out of capital income0.75 [Category B(iii)]c3Propensity to consume out of deposits0.473755074 [Category C(ii)]gKGrowth rate of capitalUS 1960-2010 mean value of gY [Category C(ii)]intDInterest rate on depositsUS 1960-2010 mean value of intD [Category B(i)]intLInterest rate on loansUS 1960-2010 mean value of intL [Category B(i)]sFRetention rate of firms0.17863783 [Category C(ii)]sWWage shareUS 1960-2010 mean value of sW [Category (Bi)]vCapital productivityCalculated using equations (14) and (15) [Category C(i)]Endogenous variablesWWage income of householdsCalculated from equation (1)YCCapital income of householdsCalculated from equation (2)CConsumption expendituresCalculated from equation (5)DDepositsCalculated from equation (13)YOutputUS 1960 value (in trillion 2009 US$)TPTotal profits of firmsCalculated from equation (6)RPRetained profitsCalculated from equation (7)DPDistributed profitsCalculated from equation (8)IInvestmentCalculated from equation (9)KCapital stockUS 1960 value (in trillion 2009 US$)LLoansUS 1960 value (in trillion 2009 US$)BPProfits of banksCalculated from equation (12)DredDeposits (redundant)Calculated from equation (13)Y*Potential outputCalculated from equation (14)uCapacity utilisationUS 1960 valuegYGrowth rate of outputUS 1960-2010 mean value of gYlevLeverage ratioCalculated from equation (17)
Note: For the different categories of parameters, see Appendix B
4. Steps for simulating the model in R
#Open R and create a new R script (File->New file->R script). Save this file as 'Model' (File->Save as).
#Clear the workspace and identify how many time periods (T) you wish your model to run. Since we use US data for the period 1960-2010, we will run the model for 51 periods. (Once you have written the commands, press 'Source'.)
RCopyrm(list=ls(all=TRUE))
T<-51
#Download the excel file that contains the US data for the period 1960-2010 that will be used for the calibration of the model (the data come from FRED and BIS). Save the file as .csv in your desktop and insert it into R using the command below. (Once you have written the command, press 'Source'.)
RCopyData<- read.csv("C:/users/user/Desktop/Data.csv")
#This should be adjusted based on the location of your file; if you have problems in reading the file, you 
#could potentially try to use a command like this one: 
Data<- read.csv("C:/users/user/Desktop/Data.csv, dec = "," , sep = ";")
#If you wish to estimate the mean of a variable use a command like this one (type this in Console):
RCopymean(Data[,c("g_Y")])
#STEP 1: Identify the endogenous variables of the model (as well as some auxiliary
variables). For each of them create a vector that has a length equal to the time periods.
(Once you have written the commands, press ‘Source’.)
RCopy#Endogenous variables 
W<- vector(length=T)    
Y_C<- vector(length=T)    
CO<- vector(length=T)    
D<- vector(length=T)      
Y<- vector(length=T)      
TP<- vector(length=T)    
RP<- vector(length=T)    
DP<- vector(length=T)    
I<- vector(length=T)    
K<- vector(length=T)    
L<- vector(length=T)    
BP<- vector(length=T)    
D_red<- vector(length=T)    
Y_star<- vector(length=T) #auxiliary variable   
u<- vector(length=T)  #auxiliary variable   
g_Y<- vector(length=T) #auxiliary variable   
lev<- vector(length=T) #auxiliary variable
#STEP 2: Identify the parameter values based on the information that is available in
Section 3. Note that in our baseline scenario we wish our model to be at a steady state
whereby economic growth is equal to the mean economic growth in the US in 1960-2010.
RCopy#Parameters 
for (i in 1:T) { 
if (i == 1) { 
    for (iterations in 1:10){ 
 
c_1<-0.9  
c_2<-0.75  
c_3<-0.473755074#(K[i]/L[i])*((Y[i]/K[i])*(1+g_K)-g_K-(c_1*W[i]/K[i]+c_2*Y_C[i]/K[i])) 
g_K<- mean(Data[,c("g_Y")])  
int_D<- mean(Data[,c("int_D")])  
int_L<- mean(Data[,c("int_L")])  
s_F<-0.17863783# (g_K-g_K*(L[i]/K[i]))/(TP[i]/K[i])  
s_W<-mean(Data[,c("s_W")])  
v<-Y[i]/(K[i]*u[i])  
 
#STEP 3: Select the initial values using the data for your economy or the equations of the 
model (see Section 3).  
 
#Initial values 
W[i]<-s_W*Y[i]  
Y_C[i]<-DP[i]+BP[i]+int_D*(D[i]/(1+g_K))  
CO[i]<-Y[i]-I[i]  
D[i]<-L[i]   
Y[i]<-Data[1,c("Y")]  
TP[i]<-Y[i]-W[i]-int_L*(L[i]/(1+g_K))  
RP[i]<-s_F*TP[i]/(1+g_K) 
DP[i]<-TP[i]-RP[i]  
I[i]<-(g_K/(1+g_K))*K[i]  
K[i]<-Data[1,c("K")]              
L[i]<-Data[1,c("L")]  
BP[i]<-int_L*(L[i]/(1+g_K))-int_D*(D[i]/(1+g_K))  
D_red[i]<-L[i]  
Y_star[i]<-v*K[i]  
u[i]<-Data[1,c("u")]  
g_Y[i]<-g_K  
lev[i]<-L[i]/K[i]  
 
    } 
  } 
 
#STEP 4: Write down the equations and run the model. (Once you have written the 
commands, press ‘Source’.) 
 
#Equations 
else { 
    
for (iterations in 1:10){ 
 
#Households 
W[i]<-s_W*Y[i] 
Y_C[i]<-DP[i]+BP[i]+int_D*D[i-1] 
CO[i]<-c_1*W[i-1]+c_2*Y_C[i-1]+c_3*D[i-1] 
D[i]<-D[i-1]+W[i]+Y_C[i]-CO[i] 
 
#Firms 
Y[i]<-CO[i]+I[i] 
TP[i]<-Y[i]-W[i]-int_L*L[i-1] 
RP[i]<-s_F*TP[i-1] 
DP[i]<-TP[i]-RP[i] 
I[i]<-g_K*K[i-1] 
K[i]<-K[i-1]+I[i] 
L[i]<-L[i-1]+I[i]-RP[i] 
 
#Banks 
BP[i]<-int_L*L[i-1]-int_D*D[i-1] 
D_red[i]<-L[i] 
 
#Auxiliary equations 
Y_star[i]<-v*K[i] 
u[i]<-Y[i]/Y_star[i] 
g_Y[i]<-(Y[i]-Y[i-1])/Y[i-1] 
lev[i]<-L[i]/K[i] 
 
     } 
  } 
}