# Projet Knapsack

### Description
This repository presents a code using ChocoSolver with two complete and two incomplete methods, as well as ORTools with a linear programming method, to resolve the 0-1 knapsack problem.
The benchmark used to get some instances can be found in [kplib git repository](https://github.com/likr/kplib). kplib repository needs to be clone in this project to run the code. 

### Project Structure
The lib folder presents the necessary jar to add to your project structure to use Choco and ORTools (not needed if you created a Maven Project with the correct *pom.xml*).

### Code
The Main code is *KnapsackSolver.java*. *Knapsack_MILP_ORTools.java* and *ManualInstancesTest.java* or test intermediary program. To visualize some results, use *result_analysis.ipynb* after having move your *result.csv* in the results folder.

