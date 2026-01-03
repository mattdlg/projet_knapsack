package knapsack;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainBest;
import org.chocosolver.solver.search.strategy.selectors.variables.FirstFail;
import org.chocosolver.solver.search.strategy.selectors.variables.Smallest;
import org.chocosolver.solver.search.strategy.selectors.variables.VariableSelectorWithTies;
import org.chocosolver.solver.search.loop.lns.INeighborFactory;
import org.chocosolver.solver.search.restart.LubyCutoff;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class KnapsackSolver {

    // ===================== Classes =====================
    static class Instance {
        String name;
        int n;
        int capacity;
        int[] weights;
        int[] profits;
        String difficulty;
        int optimalValue;

        Instance(String name, int n, int capacity, int[] weights, int[] profits, String difficulty, int optimalValue) {
            this.name = name;
            this.n = n;
            this.capacity = capacity;
            this.weights = weights;
            this.profits = profits;
            this.difficulty = difficulty;
            this.optimalValue = optimalValue;
        }
    }

    static class Result {
        String instance;
        String method;
        int bestValue;
        long timeMs;
        int nodes;
        boolean optimal;
        int optimalKnown;
        double gapPercent;

        Result(String instance, String method, int bestValue, long timeMs, int nodes, boolean optimal, int optimalKnown) {
            this.instance = instance;
            this.method = method;
            this.bestValue = bestValue;
            this.timeMs = timeMs;
            this.nodes = nodes;
            this.optimal = optimal;
            this.optimalKnown = optimalKnown;
            if (optimalKnown > 0) {
                this.gapPercent = ((double)(optimalKnown - bestValue) / optimalKnown) * 100;
            } else {
                this.gapPercent = -1;
            }
        }
    }

    // ===================== Lecture d’instances =====================
    static Instance readInstance(String filepath, String difficulty) {
        try {
            File file = new File(filepath);
            BufferedReader br;

            if (file.exists()) {
                br = new BufferedReader(new FileReader(file));
            } else {
                InputStream is = KnapsackSolver.class.getClassLoader().getResourceAsStream(filepath);
                if (is == null) {
                    System.err.println("Fichier introuvable: " + filepath);
                    return null;
                }
                br = new BufferedReader(new InputStreamReader(is));
            }

            List<String> lines = br.lines().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            br.close();

            if (lines.size() < 2) {
                System.err.println("Fichier invalide (trop court): " + filepath);
                return null;
            }

            int n = Integer.parseInt(lines.get(0));
            int capacity = Integer.parseInt(lines.get(1));
            if (lines.size() < 2 + n) {
                System.err.println("Fichier invalide (pas assez d'items): " + filepath);
                return null;
            }

            int[] profits = new int[n];
            int[] weights = new int[n];

            for (int i = 0; i < n; i++) {
                String[] parts = lines.get(2 + i).trim().split("\\s+");
                if (parts.length < 2) {
                    System.err.println("Ligne invalide: " + lines.get(2 + i));
                    return null;
                }
                profits[i] = Integer.parseInt(parts[0]);
                weights[i] = Integer.parseInt(parts[1]);
            }
            String name = filepath.substring("kplib/".length()).replace("/", "_").replace(".kp", "");
            return new Instance(name, n, capacity, weights, profits, difficulty, -1);

        } catch (NumberFormatException e) {
            System.err.println("Erreur de format: " + filepath + " - " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("Erreur lecture: " + filepath + " - " + e.getMessage());
            return null;
        }
    }

    static List<Instance> generateFallbackInstances() {
        List<Instance> instances = new ArrayList<>();
        Random rand = new Random(42);

        int[][] sizes = {{10, 20, 50}, {40, 60, 100}, {100, 150, 200}};
        String[] difficulties = {"facile", "moyen", "difficile"};

        for (int k = 0; k < 3; k++) {
            for (int i = 0; i < 10; i++) {
                int n = sizes[k][0] + rand.nextInt(sizes[k][1] - sizes[k][0] + 1);
                int[] weights = new int[n];
                int[] profits = new int[n];
                int totalWeight = 0;
                for (int j = 0; j < n; j++) {
                    weights[j] = 1 + rand.nextInt(sizes[k][2]);
                    profits[j] = 1 + rand.nextInt(sizes[k][2] * 2);
                    totalWeight += weights[j];
                }
                int capacity = totalWeight / 2;
                instances.add(new Instance(difficulties[k] + "_gen_" + i, n, capacity, weights, profits, difficulties[k], -1));
            }
        }

        return instances;
    }

    static List<Instance> loadBenchmarkInstances() {
        List<Instance> instances = new ArrayList<>();
        System.out.println("=== Chargement des instances de benchmark ===");

        String[][] files = {
                { // faciles
                	"kplib/00Uncorrelated/n00050/R01000/s000.kp",
                    /*"kplib/00Uncorrelated/n00050/R01000/s001.kp",
                	"kplib/01WeaklyCorrelated/n00050/R01000/s000.kp",
                	"kplib/02StronglyCorrelated/n00050/R01000/s000.kp",
                	"kplib/03InverseStronglyCorrelated/n00050/R01000/s000.kp",
                	"kplib/04AlmostStronglyCorrelated/n00050/R01000/s000.kp",
                	"kplib/05SubsetSum/n00050/R01000/s000.kp",
                	"kplib/06UncorrelatedWithSimilarWeights/n00050/R01000/s000.kp",
                	"kplib/07SpannerUncorrelated/n00050/R01000/s000.kp",
                	"kplib/08SpannerWeaklyCorrelated/n00050/R01000/s000.kp"*/
                },
                { // moyens
                	"kplib/00Uncorrelated/n00100/R01000/s000.kp",
                	/*"kplib/00Uncorrelated/n00100/R01000/s001.kp",
                	"kplib/01WeaklyCorrelated/n00100/R01000/s000.kp",
                	"kplib/01WeaklyCorrelated/n00100/R01000/s001.kp",
                	"kplib/02StronglyCorrelated/n00100/R01000/s000.kp",
                	"kplib/03InverseStronglyCorrelated/n00100/R01000/s000.kp",
                	"kplib/04AlmostStronglyCorrelated/n00100/R01000/s000.kp",
                	"kplib/05SubsetSum/n00100/R01000/s000.kp",
                	"kplib/06UncorrelatedWithSimilarWeights/n00100/R01000/s000.kp",
                	"kplib/07SpannerUncorrelated/n00100/R01000/s000.kp"*/
                },
                { // difficiles
                	"kplib/00Uncorrelated/n01000/R01000/s000.kp",
                	/*"kplib/00Uncorrelated/n01000/R01000/s001.kp",
                    "kplib/01WeaklyCorrelated/n01000/R01000/s000.kp",
                    "kplib/02StronglyCorrelated/n01000/R01000/s000.kp",
                    "kplib/03InverseStronglyCorrelated/n01000/R01000/s000.kp",
                    "kplib/04AlmostStronglyCorrelated/n01000/R01000/s000.kp",
                    "kplib/05SubsetSum/n01000/R01000/s000.kp",
                    "kplib/06UncorrelatedWithSimilarWeights/n01000/R01000/s000.kp",
                    "kplib/07SpannerUncorrelated/n01000/R01000/s000.kp",
                    "kplib/08SpannerWeaklyCorrelated/n01000/R01000/s000.kp"*/
                }
        };
        String[] difficulties = {"facile", "moyen", "difficile"};

        int loadedCount = 0;
        for (int i = 0; i < files.length; i++) {
            for (String file : files[i]) {
                Instance inst = readInstance(file, difficulties[i]);
                if (inst != null) {
                    instances.add(inst);
                    loadedCount++;
                }
            }
        }

        if (instances.isEmpty()) {
            System.out.println("Aucune instance trouvée. Génération d'instances de secours...");
            instances = generateFallbackInstances();
        } else {
            System.out.println(loadedCount + " instances chargées depuis les benchmarks\n");
        }
        return instances;
    }

    // ===================== Méthodes de résolution =====================
    static Result solveCompleteFirstFail(Instance inst, long timeLimit) {
        Model model = new Model("Knapsack");
        BoolVar[] x = model.boolVarArray("x", inst.n);
        IntVar totalProfit = model.intVar("profit", 0, Arrays.stream(inst.profits).sum());
        model.scalar(x, inst.weights, "<=", inst.capacity).post();
        model.scalar(x, inst.profits, "=", totalProfit).post();

        Solver solver = model.getSolver();
        solver.setSearch(Search.inputOrderLBSearch(x));
        solver.limitTime(timeLimit);

        long start = System.currentTimeMillis();
        model.setObjective(Model.MAXIMIZE, totalProfit);

        int bestValue = 0;
        while (solver.solve()) bestValue = totalProfit.getValue();

        long elapsed = System.currentTimeMillis() - start;
        boolean optimal = solver.isStopCriterionMet() == false && solver.getSolutionCount() > 0;

        return new Result(inst.name, "Complete_FirstFail", bestValue, elapsed, (int)solver.getNodeCount(), optimal, inst.optimalValue);
    }

    static Result solveCompleteDomOverWDeg(Instance inst, long timeLimit) {
        Model model = new Model("Knapsack");
        BoolVar[] x = model.boolVarArray("x", inst.n);
        IntVar totalProfit = model.intVar("profit", 0, Arrays.stream(inst.profits).sum());
        model.scalar(x, inst.weights, "<=", inst.capacity).post();
        model.scalar(x, inst.profits, "=", totalProfit).post();

        Solver solver = model.getSolver();
        solver.setSearch(Search.domOverWDegSearch(x));
        solver.limitTime(timeLimit);

        long start = System.currentTimeMillis();
        model.setObjective(Model.MAXIMIZE, totalProfit);

        int bestValue = 0;
        while (solver.solve()) bestValue = totalProfit.getValue();

        long elapsed = System.currentTimeMillis() - start;
        boolean optimal = solver.isStopCriterionMet() == false && solver.getSolutionCount() > 0;

        return new Result(inst.name, "Complete_DomOverWDeg", bestValue, elapsed, (int)solver.getNodeCount(), optimal, inst.optimalValue);
    }

    static Result solveCompleteMILP_ORTools(Instance inst, long timeLimit) {
        MPSolver solver = MPSolver.createSolver("CBC_MIXED_INTEGER_PROGRAMMING");
        if (solver == null) return new Result(inst.name, "Complete_MILP_ORTools", 0, 0, 0, false, inst.optimalValue);

        int n = inst.n;
        MPVariable[] x = new MPVariable[n];
        for (int i = 0; i < n; i++) x[i] = solver.makeIntVar(0.0, 1.0, "x_" + i);

        MPConstraint capacityConstraint = solver.makeConstraint(0.0, inst.capacity, "capacity");
        for (int i = 0; i < n; i++) capacityConstraint.setCoefficient(x[i], inst.weights[i]);

        MPObjective objective = solver.objective();
        for (int i = 0; i < n; i++) objective.setCoefficient(x[i], inst.profits[i]);
        objective.setMaximization();

        solver.setTimeLimit(timeLimit);
        long start = System.currentTimeMillis();
        MPSolver.ResultStatus status = solver.solve();
        long elapsed = System.currentTimeMillis() - start;

        int bestValue = (status == MPSolver.ResultStatus.OPTIMAL || status == MPSolver.ResultStatus.FEASIBLE)
                ? (int) Math.round(objective.value()) : 0;
        boolean optimal = (status == MPSolver.ResultStatus.OPTIMAL);

        return new Result(inst.name, "Complete_MILP_ORTools", bestValue, elapsed, -1, optimal, inst.optimalValue);
    }

    /*static Result solveIncompleteLNS(Instance inst, long timeLimit) {
        Model model = new Model("Knapsack");
        BoolVar[] x = model.boolVarArray("x", inst.n);
        IntVar totalProfit = model.intVar("profit", 0, Arrays.stream(inst.profits).sum());
        model.scalar(x, inst.weights, "<=", inst.capacity).post();
        model.scalar(x, inst.profits, "=", totalProfit).post();

        Solver solver = model.getSolver();
        solver.setSearch(Search.randomSearch(x, System.currentTimeMillis()));
        solver.setRestarts(count -> solver.getFailCount() >= 100, new LubyCutoff(100), 5000);
        solver.limitTime(timeLimit);

        long start = System.currentTimeMillis();
        model.setObjective(Model.MAXIMIZE, totalProfit);

        int bestValue = 0;
        while (solver.solve()) bestValue = totalProfit.getValue();
        long elapsed = System.currentTimeMillis() - start;

        return new Result(inst.name, "Incomplete_LNS", bestValue, elapsed, (int)solver.getNodeCount(), false, inst.optimalValue);
    }*/
    
    static Result solveIncompleteLNS(Instance inst, long timeLimit) {

        long startTime = System.currentTimeMillis();
        Random rand = new Random(42);

       
        // 1) Construction d’une solution initiale GREEDY
        int n = inst.n;
        int[] bestSol = new int[n];
        int bestValue = 0;

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);

        order.sort((a, b) -> Double.compare(
                (double) inst.profits[b] / inst.weights[b],
                (double) inst.profits[a] / inst.weights[a]
        ));

        int totalWeight = 0;
        for (int i : order) {
            if (totalWeight + inst.weights[i] <= inst.capacity) {
                bestSol[i] = 1;
                totalWeight += inst.weights[i];
                bestValue += inst.profits[i];
            }
        }

        int iterations = 0;

        //  2) Boucle LNS
        while (System.currentTimeMillis() - startTime < timeLimit) {

            Model model = new Model("Knapsack_LNS");

            BoolVar[] x = model.boolVarArray("x", n);
            IntVar profit = model.intVar("profit", 0, Arrays.stream(inst.profits).sum());

            model.scalar(x, inst.weights, "<=", inst.capacity).post();
            model.scalar(x, inst.profits, "=", profit).post();

            
            // 3) Fixation partielle (voisinage)
            double relaxRatio = 0.15; // 15 % des variables relâchées
            for (int i = 0; i < n; i++) {
                if (rand.nextDouble() > relaxRatio) {
                    model.arithm(x[i], "=", bestSol[i]).post();
                }
            }

            // 4) Recherche locale courte
            Solver solver = model.getSolver();
            solver.setSearch(Search.randomSearch(x, rand.nextLong()));
            solver.limitTime(100); // 100 ms par sous-problème
            model.setObjective(Model.MAXIMIZE, profit);

            while (solver.solve()) {
                int val = profit.getValue();
                if (val > bestValue) {
                    bestValue = val;
                    for (int i = 0; i < n; i++) {
                        bestSol[i] = x[i].getValue();
                    }
                }
            }

            iterations++;
        }

        long elapsed = System.currentTimeMillis() - startTime;

        return new Result(
                inst.name,
                "Incomplete_LNS",
                bestValue,
                elapsed,
                iterations,
                false,
                inst.optimalValue
        );
    }
    
    /*static Result solveIncompleteLNS(Instance inst, long timeLimit) {
        Model model = new Model("Knapsack_LNS");
        BoolVar[] x = model.boolVarArray("x", inst.n);
        IntVar totalProfit = model.intVar("profit", 0, Arrays.stream(inst.profits).sum());

        // Contraintes
        model.scalar(x, inst.weights, "<=", inst.capacity).post();
        model.scalar(x, inst.profits, "=", totalProfit).post();

        Solver solver = model.getSolver();

        // --- CONFIGURATION LNS ---
        // On définit un voisinage aléatoire (RLNS) qui libère une partie des variables
        // Ici, on utilise un sélecteur de voisinage par défaut de Choco
        solver.setLNS(INeighborFactory.blackBox(x)); 

        // Stratégie de recherche de base (utilisée pour réparer le voisinage)
        solver.setSearch(Search.intVarSearch(
            new VariableSelectorWithTies<>(new FirstFail(model), new Smallest()),
            new IntDomainBest(), 
            x
        ));

        // Paramétrage des Restarts (important pour ne pas rester bloqué dans un voisinage)
        // On redémarre si on échoue 100 fois à améliorer la solution dans le voisinage
        solver.setRestarts(count -> solver.getFailCount() >= 100, new LubyCutoff(100), 10000);
        
        solver.limitTime(timeLimit);
        long start = System.currentTimeMillis();
        model.setObjective(Model.MAXIMIZE, totalProfit);

        int bestValue = 0;
        while (solver.solve()) {
            bestValue = totalProfit.getValue();
        }
        
        long elapsed = System.currentTimeMillis() - start;

        return new Result(
            inst.name, 
            "Incomplete_LNS", 
            bestValue, 
            elapsed, 
            (int)solver.getNodeCount(), 
            false, 
            inst.optimalValue
        );
    }*/


    /*static Result solveIncompleteGreedy(Instance inst, long timeLimit) {
        long start = System.currentTimeMillis();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < inst.n; i++) indices.add(i);

        indices.sort((a, b) -> Double.compare(
                (double) inst.profits[b] / inst.weights[b],
                (double) inst.profits[a] / inst.weights[a]
        ));

        int totalWeight = 0, totalProfit = 0;
        for (int idx : indices) {
            if (totalWeight + inst.weights[idx] <= inst.capacity) {
                totalWeight += inst.weights[idx];
                totalProfit += inst.profits[idx];
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        return new Result(inst.name, "Incomplete_Greedy", totalProfit, elapsed, 0, false, inst.optimalValue);
    }*/
    
    /*static Result solveIncompleteGreedy(Instance inst, long timeLimit) {
        long start = System.currentTimeMillis();

        int n = inst.n;
        int capacity = inst.capacity;

        int totalWeight = 0;
        int totalProfit = 0;

        int k = 3; // paramètre : nombre de meilleurs composants
        Random rand = new Random(42); // reproductibilité

        // Ensemble des composants candidats (objets non encore choisis)
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            candidates.add(i);
        }

        while (!candidates.isEmpty()) {

            // 1️ Filtrer les objets encore admissibles
            List<Integer> feasible = new ArrayList<>();
            for (int i : candidates) {
                if (totalWeight + inst.weights[i] <= capacity) {
                    feasible.add(i);
                }
            }

            // Aucun objet ne peut être ajouté
            if (feasible.isEmpty()) break;

            // 2️ Trier selon l’heuristique gloutonne h(i) = profit / poids
            feasible.sort((a, b) -> Double.compare(
                    (double) inst.profits[b] / inst.weights[b],
                    (double) inst.profits[a] / inst.weights[a]
            ));

            // 3️ Sélectionner les k meilleurs composants
            int limit = Math.min(k, feasible.size());
            List<Integer> topK = feasible.subList(0, limit);

            // 4️ Choisir aléatoirement 1 composant parmi les k meilleurs
            int chosen = topK.get(rand.nextInt(topK.size()));

            // 5️ Ajouter à la solution
            totalWeight += inst.weights[chosen];
            totalProfit += inst.profits[chosen];

            // 6️ Mettre à jour l’ensemble des candidats
            candidates.remove(Integer.valueOf(chosen));
        }

        long elapsed = System.currentTimeMillis() - start;

        return new Result(
                inst.name,
                "Greedy_k_best",
                totalProfit,
                elapsed,
                0,
                false,
                inst.optimalValue
        );
    }*/
    
    
    static Result solveIncompleteGreedy(Instance inst, long timeLimit) {
        long start = System.currentTimeMillis();

        int n = inst.n;
        int capacity = inst.capacity;

        int totalWeight = 0;
        int totalProfit = 0;

        double alpha = 1; // paramètre probabiliste
        Random rand = new Random(); // aléatoire

        // Ensemble des composants candidats
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            candidates.add(i);
        }

        while (!candidates.isEmpty()) {

            // 1️ Filtrer les objets admissibles
            List<Integer> feasible = new ArrayList<>();
            for (int i : candidates) {
                if (totalWeight + inst.weights[i] <= capacity) {
                    feasible.add(i);
                }
            }

            if (feasible.isEmpty()) break;

            // 2️ Calcul des h(ci)^alpha
            double[] h = new double[feasible.size()];
            double sumH = 0.0;

            for (int i = 0; i < feasible.size(); i++) {
                int idx = feasible.get(i);
                h[i] = Math.pow((double) inst.profits[idx] / inst.weights[idx], alpha);
                sumH += h[i];
            }

            // 3️ Tirage probabiliste
            double r = rand.nextDouble();
            double cumulative = 0.0;
            int chosen = feasible.get(0);

            for (int i = 0; i < feasible.size(); i++) {
                cumulative += h[i] / sumH;
                if (r <= cumulative) {
                    chosen = feasible.get(i);
                    break;
                }
            }

            // 4️ Ajouter à la solution
            totalWeight += inst.weights[chosen];
            totalProfit += inst.profits[chosen];

            // 5️ Mise à jour des candidats
            candidates.remove(Integer.valueOf(chosen));
        }

        long elapsed = System.currentTimeMillis() - start;

        return new Result(
                inst.name,
                "Probabilistic_Greedy",
                totalProfit,
                elapsed,
                0,
                false,
                inst.optimalValue
        );
    }



    // ===================== Export CSV =====================
    static void exportToCSV(List<Result> results, List<Instance> instances, String filename) {
        try (PrintWriter writer = new PrintWriter(new File(filename))) {
            writer.println("Instance,Difficulty,Class,n,Capacity,TotalWeight,TotalProfit,Method,Value,Time(ms),Nodes,Optimal,OptimalKnown,Gap(%)");

            for (Result r : results) {
                Instance inst = instances.stream().filter(i -> i.name.equals(r.instance)).findFirst().orElse(null);
                if (inst == null) continue;

                int totalWeight = Arrays.stream(inst.weights).sum();
                int totalProfit = Arrays.stream(inst.profits).sum();

                // Gestion multiplateforme
                String cls = "Unknown";
                if (r.instance.matches("^(easy|medium|hard)_.*")) cls = "Generated";
                else if (inst.name.contains(File.separator)) cls = inst.name.split(Pattern.quote(File.separator))[0];

                writer.printf("%s,%s,%s,%d,%d,%d,%d,%s,%d,%d,%d,%b,%d,%.2f\n",
                        inst.name, inst.difficulty, cls, inst.n, inst.capacity,
                        totalWeight, totalProfit, r.method, r.bestValue, r.timeMs,
                        r.nodes, r.optimal, r.optimalKnown, r.gapPercent
                );
            }
            System.out.println("\nRésultats exportés vers: " + filename);
        } catch (Exception e) {
            System.err.println("Erreur export CSV: " + e.getMessage());
        }
    }

    // ===================== Génération de rapport console =====================
    static void generateReport(List<Result> results, List<Instance> instances) {
        System.out.println("\n\n=== RAPPORT D'ANALYSE ===\n");

        // Regrouper les résultats par difficulté
        Map<String, List<Result>> byDifficulty = new HashMap<>();
        byDifficulty.put("facile", new ArrayList<>());
        byDifficulty.put("moyen", new ArrayList<>());
        byDifficulty.put("difficile", new ArrayList<>());

        for (Result r : results) {
            Instance inst = instances.stream().filter(i -> i.name.equals(r.instance)).findFirst().orElse(null);
            if (inst != null) {
                byDifficulty.get(inst.difficulty).add(r);
            }
        }

        for (String diff : Arrays.asList("facile", "moyen", "difficile")) {
            System.out.println("\n--- Instances " + diff.toUpperCase() + " ---");

            // Regrouper par méthode
            Map<String, List<Result>> byMethod = new HashMap<>();
            for (Result r : byDifficulty.get(diff)) {
                byMethod.computeIfAbsent(r.method, k -> new ArrayList<>()).add(r);
            }

            for (String method : byMethod.keySet()) {
                List<Result> methodResults = byMethod.get(method);
                double avgValue = methodResults.stream().mapToInt(r -> r.bestValue).average().orElse(0);
                double avgTime = methodResults.stream().mapToLong(r -> r.timeMs).average().orElse(0);
                long optimalCount = methodResults.stream().filter(r -> r.optimal).count();

                System.out.printf("%-25s : Valeur moy=%.0f, Temps moy=%.0fms, Optimaux=%d/%d\n",
                        method, avgValue, avgTime, optimalCount, methodResults.size());
            }
        }
    }

    // ===================== Main mis à jour =====================
    public static void main(String[] args) {
        Loader.loadNativeLibraries();

        List<Instance> instances = loadBenchmarkInstances();
        if (instances.isEmpty()) {
            System.err.println("Aucune instance disponible. Arrêt du programme.");
            return;
        }

        List<Result> allResults = new ArrayList<>();
        long timeLimit = 10 * 60 * 1000; // 10 minutes

        System.out.println("=== Résolution en cours ===");
        for (Instance inst : instances) {
            /*System.out.println("\nInstance: " + inst.name + " (n=" + inst.n + ", capacité=" + inst.capacity + ", difficulté=" + inst.difficulty + ")");
            
            Result r1 = solveCompleteFirstFail(inst, timeLimit);
            allResults.add(r1);
            System.out.println("  - Complete FirstFail : Valeur=" + r1.bestValue + ", Temps=" + r1.timeMs + "ms");
            
            Result r2 = solveCompleteDomOverWDeg(inst, timeLimit);
            allResults.add(r2);
            System.out.println("  - Complete DomOverWDeg : Valeur=" + r2.bestValue + ", Temps=" + r2.timeMs + "ms");*/
            
            Result r3 = solveCompleteMILP_ORTools(inst, timeLimit);
            allResults.add(r3);
            System.out.println("  - Complete MILP OR-Tools : Valeur=" + r3.bestValue + ", Temps=" + r3.timeMs + "ms");
            
            /*Result r5 = solveIncompleteGreedy(inst, timeLimit);
            allResults.add(r5);
            System.out.println("  - Incomplete Greedy : Valeur=" + r5.bestValue + ", Temps=" + r5.timeMs + "ms");*/
            
            Result r4 = solveIncompleteLNS(inst, timeLimit);
            allResults.add(r4);
            System.out.println("  - Incomplete LNS : Valeur=" + r4.bestValue + ", Temps=" + r4.timeMs + "ms");
            
        }

        generateReport(allResults, instances);
        exportToCSV(allResults, instances, "results.csv");
    }
}
