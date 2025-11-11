package knapsack;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.search.strategy.assignments.DecisionOperator;
import org.chocosolver.solver.variables.IntVar;

import java.util.Arrays;

import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.selectors.variables.VariableSelector;
import org.chocosolver.solver.search.strategy.selectors.values.IntValueSelector;


import static org.chocosolver.solver.search.strategy.Search.inputOrderLBSearch;
import static org.chocosolver.solver.search.strategy.Search.inputOrderUBSearch;

public class Main
{
    public static void main( String[] args )
    {
        System.out.println("Lancement du test knapsack");

        int R = 1000; // ranges for weights and values
        int[] sizes = new int[]{20, 50, 100}; // number of different items
        int H = 10; // number of instances
        int d = 3; // parameter for profit ceiling instances (supposed to give difficult instances)
        int timeOut = 600; // time-out at t=10min to avoid to long computation
        for (int h = 1; h < (3*H+1); h++) {
            int n;
            if (h<=10){
                n = sizes[0]; // small instance size
            } else if (h<=20) {
                n = sizes[1]; // medium instance size
            } else {
                n = sizes[2]; // big instance size
            }
            System.out.printf("Instance %d/%d with size %d\n", h, 3*H, n);
            /*
             int W = 67; // a maximum weight capacity
             int[] w = new int[]{23, 26,20,18,32, 27, 29, 26, 30, 27}; // weight of items
             int[] v = new int[]{505, 352, 458, 220, 354, 414, 498, 545, 473, 543}; // value of items
            */
            int[] w = new int[n]; // weight of items
            int[] v = new int[n]; // value of items
            profit_ceiling_instances(w, v, n, R, H, d);
            System.out.printf("Weights: %s\n", Arrays.toString(w));
            System.out.printf("Values: %s\n", Arrays.toString(v));
            int C = capacity_generator(w, (h-1)%H + 1, H); // Capacity of current instance, keeping the instance index between [1 and 10]
            System.out.printf("Capacity: %d\n", C);

            Model model = new Model("Knapsack");
            IntVar[] items = new IntVar[n];
            // To model 0-1 knapsack problem, the upper bound of each variable must be set to 1
            for (int i = 0; i < n; i++) {
                items[i] = model.intVar("item_" + (i + 1), 0, 1); // 0 means not taken, 1 means taken
            }
            // objective : maximize the sum of the values of the items in the knapsack
            int maxValue = Arrays.stream(v).sum();
            IntVar value = model.intVar("value", 0, maxValue); // upper bound is the max possible value
            // the sum of the weights is less than or equal to the knapsack's capacity
            IntVar weight = model.intVar("weight", 0, C); // upper bound is the knapsack's capacity

            model.knapsack(items, weight, value, w, v).post(); // model.knapsack() maintains feasible weight and value consistency and Choco’s internal optimization automatically prunes branches where the upper bound < current best lower bound.
            model.setObjective(Model.MAXIMIZE, value);

            // Sort item indices by decreasing value/weight ratio
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            Arrays.sort(order, (i, j) -> Double.compare((double)v[j]/w[j], (double)v[i]/w[i]));

            // Define variable selector (pick next uninstantiated item by ratio order)
                        VariableSelector<IntVar> varSelector = vars -> {
                            for (int i : order) {
                                if (!vars[i].isInstantiated()) {
                                    return vars[i];
                                }
                            }
                            return null; // stop if all instantiated
                        };

            // Define value selector (try 1 before 0)
                        IntValueSelector valueSelector = var -> 1; // first assign 1, then 0 automatically handled by solver

            // Plug custom search strategy into solver
            Solver solver = model.getSolver();
            solver.setSearch(Search.intVarSearch(varSelector, valueSelector, items));
            solver.limitTime(timeOut + "s");
            /*
            solver.setSearch(
                    // inputOrderUBSearch(value), // first try to maximize the value
                    // inputOrderLBSearch(items) // then try to fill the knapsack with items
            );
            */

            while (solver.solve()) {
                /*
                System.out.printf("Knapsack -- %d items\n", n);
                System.out.println("\tItem: Count");
                for (int i = 0; i < items.length; i++) {
                    System.out.printf("\tItem #%d: %d\n", (i+1), items[i].getValue());
                }
                */
                if (value.isInstantiated() & weight.isInstantiated()) {
                    System.out.printf("Better solution found: value=%d weight=%d\n", value.getValue(), weight.getValue());
                }
            }
            if (value.isInstantiated() & weight.isInstantiated()) {
                System.out.printf("Best solution after B&B: %d\n", value.getValue());
            }
            // solver.reset(); // to solve the model several times
        }
    }

    /**
     * Generator of different capacities for each instances
     */
    private static int capacity_generator(int[] weights, int h, int H) {
        int sumWeights = 0;
        for (int weight : weights) {
            sumWeights += weight;
        }
        return((int) ((double) h/(H+1)*sumWeights));
    }

    /**
     * Generator of profit ceiling instances
     */
    private static void profit_ceiling_instances(int[] weights, int[] values, int n, int r, int H, int d) {
        for (int i = 0; i < n; i++) {
            weights[i] = (int) (Math.random() * r) + 1; // 1 to r
            values[i] = (int) (d * Math.ceil((double) weights[i] / d));
        }
    }


}
