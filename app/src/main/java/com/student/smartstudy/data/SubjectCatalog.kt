package com.student.smartstudy

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class Chap(val title: String, val notes: List<String>)

@Immutable
data class Subject(val id: String, val name: String, val mark: String, val color: Color,
    val units: List<Chap>)

private fun c(t: String, vararg n: String) = Chap(t, n.toList())

val subjects = listOf(
    Subject("py", "Python", "Py", Color(0xFFD4A017), listOf(
        c(
            "Unit 1: Python Programming Fundamentals",
            "Setting up Python, IDEs, the interpreter, pip and virtual environments.",
            "Variables, data types, type casting and operators.",
            "Conditions, for and while loops, break, continue and pass."
        ),
        c(
            "Unit 2: Data Structures & Functions",
            "Lists, tuples, sets and dictionaries; slicing and comprehensions.",
            "Functions: scope, default, keyword and variable-length arguments.",
            "Lambda, map(), filter() and reduce()."
        ),
        c(
            "Unit 3: OOP, Modules & File I/O",
            "Classes, objects, inheritance, polymorphism and encapsulation.",
            "Modules and packages; reading and writing text, CSV and JSON files.",
            "Log analysis and security monitoring."
        ),
        c(
            "Unit 4: Advanced Libraries & Exceptions",
            "try, except, else, finally, raise and custom exceptions.",
            "os, sys and subprocess for system scripting; HTTP with requests.",
            "NumPy arrays and pandas DataFrames for data analysis."
        )
    )),
    Subject("ma", "Maths", "∑", Color(0xFFE07B39), listOf(
        c(
            "Unit 1: Sets, Relations & Functions",
            "Sets, subsets, Venn diagrams and Cartesian products.",
            "Relations: reflexive, symmetric, transitive; equivalence relations and partitions; closures and Warshall's algorithm.",
            "Functions: injective, surjective, bijective, composition and inverse."
        ),
        c(
            "Unit 2: Counting & Recurrence",
            "Permutations, combinations, binomial coefficients and Pascal's identity.",
            "Pigeonhole principle.",
            "Recurrence relations: Fibonacci, Tower of Hanoi, characteristic equation."
        ),
        c(
            "Unit 3: Matrix Algebra",
            "Matrix types, operations, determinants, rank and inverse.",
            "Symmetric, skew-symmetric and orthogonal matrices.",
            "Linear systems, eigenvalues and eigenvectors."
        ),
        c(
            "Unit 4: Elementary Graph Theory",
            "Terminology, paths, cycles, connectivity and subgraphs.",
            "Eulerian and Hamiltonian graphs, digraphs, weighted graphs.",
            "Trees, spanning trees and planar graphs."
        )
    )),
    Subject("c", "C", "C", Color(0xFF8F7A1E), listOf(
        c(
            "Unit 1: Problem Solving & C Fundamentals",
            "Algorithms, flowcharts and pseudocode; history, features and structure of a C program.",
            "Compilation, tokens, keywords, identifiers, variables, constants and data types.",
            "Standard input and output: printf, scanf, getchar and putchar."
        ),
        c(
            "Unit 2: Operators, Control Flow & Type Conversion",
            "Arithmetic, relational, logical, assignment, bitwise and conditional operators.",
            "if, if-else, else-if ladders, switch; while, do-while and for loops; break, continue and goto.",
            "Expressions, side effects, implicit and explicit type conversion."
        ),
        c(
            "Unit 3: Arrays, Strings, Functions & Recursion",
            "One- and two-dimensional arrays, character arrays and string functions.",
            "Functions, prototypes, scope, call by value and pointer-based modification.",
            "Recursion: factorial, Fibonacci, base cases and trace tables."
        ),
        c(
            "Unit 4: Pointers, Structures, Memory & Files",
            "Pointers, pointer arithmetic, arrays and const correctness.",
            "Structures, unions, malloc, calloc, realloc and free.",
            "File handling and the preprocessor: macros, include guards and conditional compilation."
        )
    )),
    Subject("ai", "Exploration in AI", "AI", Color(0xFFB5651D), listOf(
        c(
            "Unit 1: Artificial Intelligence Foundations",
            "Human versus machine intelligence; defining AI and the Turing Test.",
            "History: Dartmouth 1956, AI winters and modern milestones.",
            "Narrow, general and super AI; data science, computer vision and NLP."
        ),
        c(
            "Unit 2: Problem Solving & Search Basics",
            "AI as problem solving: states, actions, goals and path cost.",
            "Uninformed search: breadth-first and depth-first.",
            "Informed search and heuristics: greedy best-first and A*."
        ),
        c(
            "Unit 3: Knowledge Representation & Expert Systems",
            "Data, information and knowledge; propositional logic.",
            "Rule-based systems, semantic networks and inference.",
            "Expert system architecture, applications and limits."
        ),
        c(
            "Unit 4: Machine Learning & AI Ethics",
            "Supervised, unsupervised and reinforcement learning.",
            "Recommendation engines and virtual assistants.",
            "Bias, privacy, deepfakes and responsible AI."
        )
    )),
    Subject("db", "DBMS", "DB", Color(0xFF6B4E16), listOf(
        c(
            "Unit 1: Database Foundations & Design",
            "DBMS versus file systems; hierarchical, network and relational models.",
            "The ER model: entities, attributes, relationships and diagrams.",
            "Integrity constraints: primary key, foreign key, UNIQUE and CHECK."
        ),
        c(
            "Unit 2: Relational Algebra & SQL",
            "Selection, projection, joins and other algebra operations.",
            "DDL, DML, SELECT, subqueries, views, stored procedures and triggers.",
            "SQL injection, parameterized queries and database hardening."
        ),
        c(
            "Unit 3: Normalization & Transactions",
            "Functional dependencies and normal forms up to BCNF.",
            "ACID properties, schedules, concurrency control and locking.",
            "Deadlocks, logging and crash recovery."
        ),
        c(
            "Unit 4: Security & Emerging Trends",
            "Access control (DAC, RBAC), encryption and auditing.",
            "SQL versus NoSQL: MongoDB, Cassandra and the CAP theorem.",
            "Big data and blockchain."
        )
    ))
)
