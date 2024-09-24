from typing import List
from pysat.formula import *
from pysat.solvers import Solver
from pysat.card import *
from itertools import combinations

def name_dicts(num_cards: int):
    shape = {
        'c': 1+num_cards,
        'r': 2+num_cards,
        'w': 3+num_cards
    }
    filling = {
        'l': 4+num_cards,
        'g': 5+num_cards,
        'd': 6+num_cards
    }
    colour = {
        'g': 7+num_cards,
        'r': 8+num_cards,
        'v': 9+num_cards
    }
    number = {
        '1': 10+num_cards,
        '2': 11+num_cards,
        '3': 12+num_cards
    }
    return shape, filling, colour, number

def run_satSolver(detections:List[str]):
    
    num_vars = len(detections)
    required_true = 3

    # Variable list: 1 to num_detections (PySAT uses 1-based indexing)
    vars = list(range(1, num_vars + 1))

    # Add clause that exactly 3 out of all variables should be true
    cnf = CNF()

    # Constraint: exactly 3 cards are selected
    cnf.extend(CardEnc.equals(lits=vars, bound=required_true))
    shape, filling, colour, number = name_dicts(cnf.nv)
    propertyDict = {key: [] for key in range(cnf.nv+1, cnf.nv+14)}
    
    # Constraint: either 1 or 3 cards with each property
    cnf.append([shape['c'], shape['r'], shape['w']])
    cnf.append([shape['c'], -shape['r'], -shape['w']])
    cnf.append([-shape['c'], shape['r'], -shape['w']])
    cnf.append([-shape['c'], -shape['r'], shape['w']])
    
    cnf.append([filling['l'], filling['g'], filling['d']])
    cnf.append([filling['l'], -filling['g'], -filling['d']])
    cnf.append([-filling['l'], filling['g'], -filling['d']])
    cnf.append([-filling['l'], -filling['g'], filling['d']])

    cnf.append([colour['g'], colour['r'], colour['v']])
    cnf.append([colour['g'], -colour['r'], -colour['v']])
    cnf.append([-colour['g'], colour['r'], -colour['v']])
    cnf.append([-colour['g'], -colour['r'], colour['v']])
    
    cnf.append([number['1'], number['2'], number['3']])
    cnf.append([ number['1'], -number['2'], -number['3']])
    cnf.append([-number['1'], number['2'], -number['3']])
    cnf.append([-number['1'], -number['2'], number['3']])


    # selecting the card implies the fullfimment of the properties
    # A -> B is equivalent to -A or B
    card = 1
    for detection in detections:

        # Add card to list of cards with each property
        propertyDict[shape[detection[0]]].append(card)
        propertyDict[filling[detection[1]]].append(card)
        propertyDict[colour[detection[2]]].append(card)
        propertyDict[number[detection[3]]].append(card)
    
        # Add implication    
        cnf.append([-card, shape[detection[0]]])
        cnf.append([-card, filling[detection[1]]])
        cnf.append([-card, colour[detection[2]]])
        cnf.append([-card, number[detection[3]]])
        
        card += 1

    # A property can only be fullfilled if a card with it is selected
    for prop in propertyDict:
        cnf.append([-prop] + [card for card in propertyDict[prop]])

    # Use the solver to solve the problem
    with Solver(bootstrap_with=cnf) as solver:
        if solver.solve():
            solution = solver.get_model()
            # The solution will contain a list of positive or negative integers
            # Positive means True, Negative means False
            print("Solution found:")
            print([f'X{i} = {sol > 0}' for i, sol in enumerate(solution[:num_vars], 1)])
            return str(solution)
        else:
            print("No solution exists.")
        print(cnf.clauses)


def main():
    detections = ["clg1", "clg3", "clg3", "rdv3", "wgr2"]
    run_satSolver(detections)

if __name__ == '__main__':
    main()