package com.jalbardaner.android_setSolver;

import android.util.Log;

import org.sat4j.minisat.SolverFactory;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.TimeoutException;
import org.sat4j.core.VecInt;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class SATSolver {

    // Method to create mappings for properties
    public static Map<Character, Integer>[] nameDicts(int numCards) {
        Map<Character, Integer> shape = new HashMap<>();
        shape.put('c', 1 + numCards);
        shape.put('r', 2 + numCards);
        shape.put('w', 3 + numCards);

        Map<Character, Integer> filling = new HashMap<>();
        filling.put('l', 4 + numCards);
        filling.put('g', 5 + numCards);
        filling.put('d', 6 + numCards);

        Map<Character, Integer> colour = new HashMap<>();
        colour.put('g', 7 + numCards);
        colour.put('r', 8 + numCards);
        colour.put('v', 9 + numCards);

        Map<Character, Integer> number = new HashMap<>();
        number.put('1', 10 + numCards);
        number.put('2', 11 + numCards);
        number.put('3', 12 + numCards);

        return new Map[]{shape, filling, colour, number};
    }

    public static int[] runSatSolver(List<String> detections) {
        int numVars = detections.size();

        int requiredTrue = 3;

        // Initialize the Sat4j solver
        ISolver solver = SolverFactory.newDefault();
        solver.newVar(numVars);  // Number of variables
        solver.setExpectedNumberOfClauses(50);  // Estimate the number of clauses

        // Get the property dictionaries
        Map<Character, Integer>[] dicts = nameDicts(solver.realNumberOfVariables());
        Map<Character, Integer> shape = dicts[0];
        Map<Character, Integer> filling = dicts[1];
        Map<Character, Integer> colour = dicts[2];
        Map<Character, Integer> number = dicts[3];

        // Map for properties and the cards that satisfy them
        Map<Integer, List<Integer>> propertyDict = new HashMap<>();
        for (int i = numVars + 1; i <= numVars + 13; i++) {
            propertyDict.put(i, new ArrayList<Integer>());
        }

        // Create an array for the variables based on the number of detections
        int[] variableArray = new int[numVars];
        for (int i = 0; i < numVars; i++) {
            variableArray[i] = i + 1;  // Variables are 1-indexed
        }

        // Adding clauses for each property
        try {

            // Exactly 3 cards should be selected: Encode (x1 OR x2 OR x3)
            solver.addExactly(new VecInt(variableArray), requiredTrue);


            // Constraints for shapes (using characters 'c', 'r', 'w')
            solver.addClause(new VecInt(new int[]{shape.get('c'), shape.get('r'), shape.get('w')}));
            solver.addClause(new VecInt(new int[]{shape.get('c'), -shape.get('r'), -shape.get('w')}));
            solver.addClause(new VecInt(new int[]{-shape.get('c'), shape.get('r'), -shape.get('w')}));
            solver.addClause(new VecInt(new int[]{-shape.get('c'), -shape.get('r'), shape.get('w')}));

            // Constraints for fillings (using characters 'l', 'g', 'd')
            solver.addClause(new VecInt(new int[]{filling.get('l'), filling.get('g'), filling.get('d')}));
            solver.addClause(new VecInt(new int[]{filling.get('l'), -filling.get('g'), -filling.get('d')}));
            solver.addClause(new VecInt(new int[]{-filling.get('l'), filling.get('g'), -filling.get('d')}));
            solver.addClause(new VecInt(new int[]{-filling.get('l'), -filling.get('g'), filling.get('d')}));

            // Constraints for colours (using characters 'g', 'r', 'v')
            solver.addClause(new VecInt(new int[]{colour.get('g'), colour.get('r'), colour.get('v')}));
            solver.addClause(new VecInt(new int[]{colour.get('g'), -colour.get('r'), -colour.get('v')}));
            solver.addClause(new VecInt(new int[]{-colour.get('g'), colour.get('r'), -colour.get('v')}));
            solver.addClause(new VecInt(new int[]{-colour.get('g'), -colour.get('r'), colour.get('v')}));

            // Constraints for numbers (using characters '1', '2', '3')
            solver.addClause(new VecInt(new int[]{number.get('1'), number.get('2'), number.get('3')}));
            solver.addClause(new VecInt(new int[]{number.get('1'), -number.get('2'), -number.get('3')}));
            solver.addClause(new VecInt(new int[]{-number.get('1'), number.get('2'), -number.get('3')}));
            solver.addClause(new VecInt(new int[]{-number.get('1'), -number.get('2'), number.get('3')}));


            // Iterate over each card and map its properties
            int card = 1;
            for (String detection : detections) {
                // Add each card to the respective property lists

                propertyDict.get(shape.get(detection.charAt(0))).add(card);
                propertyDict.get(filling.get(detection.charAt(1))).add(card);
                propertyDict.get(colour.get(detection.charAt(2))).add(card);
                propertyDict.get(number.get(detection.charAt(3))).add(card);

                // Add implications (If a card is selected, its properties must hold)
                solver.addClause(new VecInt(new int[]{-card, shape.get(detection.charAt(0))}));
                solver.addClause(new VecInt(new int[]{-card, filling.get(detection.charAt(1))}));
                solver.addClause(new VecInt(new int[]{-card, colour.get(detection.charAt(2))}));
                solver.addClause(new VecInt(new int[]{-card, number.get(detection.charAt(3))}));

                card++;
            }

            // A property can only be fulfilled if a card with that property is selected
            for (Map.Entry<Integer, List<Integer>> entry : propertyDict.entrySet()) {
                List<Integer> cards = entry.getValue();
                if (!cards.isEmpty()) {
                    int[] clause = new int[cards.size() + 1];
                    clause[0] = -entry.getKey();  // Property is false or one of the cards must be true
                    for (int i = 0; i < cards.size(); i++) {
                        clause[i + 1] = cards.get(i);
                    }
                    solver.addClause(new VecInt(clause));
                }
                else{
                    solver.addClause(new VecInt(new int[]{-entry.getKey()}));
                }
            }

            Log.i("setSolver",""+solver.realNumberOfVariables());


            // Use the solver to find a solution
            int[] solution = new int[0];
            if (solver.isSatisfiable()) {
                solution = solver.model();
            }
            return solution;

        } catch (TimeoutException e) {
            System.out.println("Solver timed out!");
        } catch (ContradictionException e) {
            System.out.println("Runtime exception!");
//            throw new RuntimeException(e);
        }
        return null;
    }
}
