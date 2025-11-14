package knapsack;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Random;

public class Knapsack_MILP_ORTools {

    public static void main(String[] args) {
        // Charge les bibliothèques natives OR-Tools (doit être appelé une fois).
        Loader.loadNativeLibraries();

        final int R = 1000;                 // range des poids (1..R)
        final int[] sizes = new int[]{20, 50, 100};
        final int H = 10;                   // instances per bucket
        final int d = 3;                    // param pour "profit ceiling"
        final int timeOutSeconds = 600;     // timeout par instance en secondes

        String csvFile = "knapsack_ortools_results.csv";

        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {
            writer.println("instance,size,capacity,best_value,best_weight,time_seconds,status");

            int totalInstances = 3 * H;
            for (int h = 1; h <= totalInstances; h++) {
                int n = (h <= 10) ? sizes[0] : (h <= 20 ? sizes[1] : sizes[2]);

                // Génération instance
                int[] w = new int[n];
                int[] v = new int[n];
                generate_profit_ceiling_instances(w, v, n, R, H, d);

                int C = capacity_generator(w, ((h - 1) % H) + 1, H);

                System.out.printf("\nInstance %d/%d (n=%d) — capacity=%d\n", h, totalInstances, n, C);
                // Optionnel : afficher vecteurs
                // System.out.println("weights = " + Arrays.toString(w));
                // System.out.println("values  = " + Arrays.toString(v));

                // --- Création du solveur MIP (CBC) ---
                // "CBC_MIXED_INTEGER_PROGRAMMING" utilise le solver CBC si disponible.
                MPSolver solver = MPSolver.createSolver("CBC_MIXED_INTEGER_PROGRAMMING");
                if (solver == null) {
                    System.err.println("Erreur : MPSolver.createSolver() a retourné null. Vérifie l'installation d'OR-Tools.");
                    return;
                }

                // Variables binaires x[i] ∈ {0,1}
                MPVariable[] x = new MPVariable[n];
                for (int i = 0; i < n; i++) {
                    x[i] = solver.makeIntVar(0.0, 1.0, "x_" + i);
                }

                // Contrainte : somme w[i] * x[i] <= C
                MPConstraint capacityCons = solver.makeConstraint(0.0, C, "capacity");
                for (int i = 0; i < n; i++) {
                    capacityCons.setCoefficient(x[i], w[i]);
                }

                // Objectif : maximiser sum v[i] * x[i]
                MPObjective objective = solver.objective();
                for (int i = 0; i < n; i++) {
                    objective.setCoefficient(x[i], v[i]);
                }
                objective.setMaximization();

                // Timeout (en millisecondes). Selon build OR-Tools, setTimeLimit accepte ms.
                // Si la version d'OR-Tools utilisée n'expose pas setTimeLimit, cette ligne peut échouer -> commenter.
                try {
                    solver.setTimeLimit((long) timeOutSeconds * 1000L);
                } catch (Throwable t) {
                    // Certaines versions peuvent ne pas supporter setTimeLimit ; on ignore proprement.
                    System.err.println("Warning: impossible d'appliquer setTimeLimit sur ce build OR-Tools: " + t.getMessage());
                }

                // Résolution
                long tStart = System.currentTimeMillis();
                MPSolver.ResultStatus resultStatus = solver.solve();
                long tEnd = System.currentTimeMillis();

                double elapsedSec = (tEnd - tStart) / 1000.0;
                String status;
                if (resultStatus == MPSolver.ResultStatus.OPTIMAL) {
                    status = "OPTIMAL";
                } else if (resultStatus == MPSolver.ResultStatus.FEASIBLE) {
                    status = "FEASIBLE"; // nombre de cas où timeout mais solution trouvée
                } else {
                    status = "INFEASIBLE_OR_ERROR";
                }

                // Récupère solution
                long bestValue = 0;
                long bestWeight = 0;
                if (resultStatus == MPSolver.ResultStatus.OPTIMAL || resultStatus == MPSolver.ResultStatus.FEASIBLE) {
                    for (int i = 0; i < n; i++) {
                        double xi = x[i].solutionValue();
                        if (xi > 0.5) {
                            bestValue += v[i];
                            bestWeight += w[i];
                        }
                    }
                    // Alternatively: solver.objective().value() gives bestValue as double
                    // double objectiveVal = solver.objective().value();
                } else {
                    System.out.println("Pas de solution (infeasible or error). Status: " + resultStatus);
                }

                System.out.printf("Résultat: status=%s, value=%d, weight=%d, time=%.3fs%n",
                        status, bestValue, bestWeight, elapsedSec);

                writer.printf("%d,%d,%d,%d,%d,%.3f,%s%n",
                        h, n, C, bestValue, bestWeight, elapsedSec, status);
            } // end for instances

            System.out.println("\nTerminé. Résultats écrits dans: " + csvFile);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // -------------------------
    // Helpers: generate instances
    // -------------------------
    private static void generate_profit_ceiling_instances(int[] weights, int[] values, int n, int r, int H, int d) {
        Random rng = new Random(123456L); // seed fixe pour reproductibilité
        for (int i = 0; i < n; i++) {
            weights[i] = rng.nextInt(r) + 1; // entre 1 et r
            values[i] = d * (int) Math.ceil((double) weights[i] / d);
        }
    }

    private static int capacity_generator(int[] weights, int h, int H) {
        long sum = 0;
        for (int w : weights) sum += w;
        return (int) Math.floor((double) h / (H + 1) * sum);
    }
}
