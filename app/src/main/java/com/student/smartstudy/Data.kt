package com.student.smartstudy

import androidx.compose.ui.graphics.Color

data class Chap(val title: String, val notes: List<String>)
data class Subject(val id: String, val name: String, val emoji: String, val color: Color, val units: List<Chap>)

private fun c(t: String, vararg n: String) = Chap(t, n.toList())

val subjects = listOf(
    Subject("py", "Python", "🐍", Color(0xFF7C6CF0), listOf(
        c("Unit 1: Basics & Data Types", "Python is interpreted and dynamically typed.", "Core types: int, float, str, bool, None.", "Use input(), print() and f-strings for I/O."),
        c("Unit 2: Control Flow & Functions", "if/elif/else, for and while loops, break/continue.", "Define functions with def; use default and keyword arguments.", "Lambda gives small anonymous functions."),
        c("Unit 3: Data Structures & OOP", "list, tuple, set and dict cover most needs.", "Classes bundle data and methods; __init__ builds objects.", "Inheritance reuses behaviour; polymorphism overrides it."),
        c("Unit 4: Files, Modules & Exceptions", "open() with 'with' handles files safely.", "import modules; pip installs packages.", "try/except/finally handles runtime errors.")
    )),
    Subject("ma", "Maths", "➗", Color(0xFFF2894E), listOf(
        c("Unit 1: Sets, Relations & Functions", "Sets, subsets, Venn diagrams and Cartesian products.", "Relations: reflexive, symmetric, transitive; equivalence relations and partitions; closures and Warshall's algorithm.", "Functions: injective, surjective, bijective, composition and inverse."),
        c("Unit 2: Counting & Recurrence", "Permutations, combinations, binomial coefficients and Pascal's identity.", "Pigeonhole principle.", "Recurrence relations: Fibonacci, Tower of Hanoi, characteristic equation."),
        c("Unit 3: Matrix Algebra", "Matrix types, operations, determinants, rank and inverse.", "Symmetric, skew-symmetric and orthogonal matrices.", "Linear systems, eigenvalues and eigenvectors."),
        c("Unit 4: Elementary Graph Theory", "Terminology, paths, cycles, connectivity and subgraphs.", "Eulerian and Hamiltonian graphs, digraphs, weighted graphs.", "Trees, spanning trees and planar graphs.")
    )),
    Subject("ai", "Exploration in AI", "🤖", Color(0xFFC2457F), listOf(
        c("Unit 1: Introduction to AI", "AI builds systems that perceive, reason and act.", "Intelligent agents and environments.", "Applications: vision, language, robotics."),
        c("Unit 2: Search & Problem Solving", "Uninformed search: BFS, DFS.", "Informed search: greedy, A* with heuristics.", "Adversarial search: minimax, alpha-beta."),
        c("Unit 3: Machine Learning Basics", "Supervised, unsupervised, reinforcement learning.", "Train/test split, overfitting, evaluation metrics.", "Models: regression, decision trees, k-means."),
        c("Unit 4: Neural Networks & Ethics", "Neurons, layers, activation, backpropagation.", "CNNs for images, transformers for language.", "Bias, privacy and responsible AI.")
    )),
    Subject("db", "DBMS", "🗄️", Color(0xFF3B9BE0), listOf(
        c("Unit 1: Introduction & ER Model", "DBMS vs file systems; three-schema architecture.", "Entities, attributes, relationships.", "Keys: primary, candidate, foreign."),
        c("Unit 2: Relational Model & Algebra", "Tables, tuples, domains and constraints.", "Select, project, union, join, division.", "Integrity: entity and referential."),
        c("Unit 3: SQL", "DDL: CREATE, ALTER, DROP. DML: INSERT, UPDATE, DELETE.", "SELECT with WHERE, GROUP BY, HAVING, ORDER BY.", "Joins, subqueries and views."),
        c("Unit 4: Normalization & Transactions", "Functional dependencies; 1NF, 2NF, 3NF, BCNF.", "ACID properties of transactions.", "Concurrency control, locking, deadlocks.")
    ))
)

data class Q(val unit: Int, val q: String, val o: List<String>, val a: Int, val sol: String = "", val topic: String = "")
private fun q(u: Int, t: String, a: Int, vararg o: String) = Q(u, t, o.toList(), a)

private val baseQuiz: Map<String, List<Q>> = mapOf(
    "py" to listOf(
        q(0, "Which type is immutable?", 2, "list", "dict", "tuple", "set"),
        q(0, "What is type(3.0)?", 1, "int", "float", "str", "bool"),
        q(1, "Which keyword defines a function?", 1, "func", "def", "fun", "lambda"),
        q(1, "range(3) yields?", 1, "1,2,3", "0,1,2", "0,1,2,3", "1,2"),
        q(2, "Which method initialises an object?", 1, "__new__", "__init__", "__str__", "__call__"),
        q(2, "A dict stores data as?", 1, "indexes", "key-value pairs", "sets", "tuples only"),
        q(3, "Safest way to open a file?", 1, "open() only", "with open()", "file()", "read()"),
        q(3, "Which block always runs?", 2, "try", "except", "finally", "else")
    ),
    "ma" to listOf(
        q(0, "Subsets of a 3-element set?", 2, "6", "7", "8", "9"),
        q(0, "A bijection is?", 2, "one-one only", "onto only", "one-one and onto", "neither"),
        q(1, "p → q is false when?", 1, "p T, q T", "p T, q F", "p F, q T", "p F, q F"),
        q(1, "An always-true formula is a?", 1, "Contradiction", "Tautology", "Fallacy", "Axiom"),
        q(2, "5C2 equals?", 1, "5", "10", "20", "25"),
        q(2, "10 pigeons, 9 holes means?", 1, "a hole is empty", "a hole has 2 or more", "each has 1", "none"),
        q(3, "Sum of degrees with 5 edges?", 1, "5", "10", "15", "20"),
        q(3, "Edges in a tree of n vertices?", 1, "n", "n-1", "n+1", "2n")
    ),
    "ai" to listOf(
        q(0, "An agent perceives using?", 1, "Actuators", "Sensors", "Memory", "Heuristics"),
        q(0, "Who coined 'Artificial Intelligence'?", 1, "Turing", "McCarthy", "Minsky", "Newell"),
        q(1, "A* uses?", 0, "f = g + h", "f = g", "f = h only", "random"),
        q(1, "BFS finds the path with fewest?", 1, "costs", "steps", "nodes in memory", "heuristics"),
        q(2, "Labelled data is used in?", 1, "Unsupervised", "Supervised", "Reinforcement", "None"),
        q(2, "Overfitting means?", 1, "Poor on training", "Good on training, poor on new", "Good on both", "Poor on both"),
        q(3, "Neural nets update weights using?", 0, "Backpropagation", "Sorting", "Hashing", "Pooling"),
        q(3, "CNNs are best known for?", 0, "Images", "Tables", "Sorting", "Compression")
    ),
    "db" to listOf(
        q(0, "A primary key must be?", 0, "Unique and not null", "Repeated", "Nullable", "Text only"),
        q(0, "An ER diagram shows?", 1, "Code", "Entities and relationships", "Indexes", "Queries"),
        q(1, "Which operation picks columns?", 1, "Select", "Project", "Union", "Join"),
        q(1, "A foreign key enforces?", 1, "Entity integrity", "Referential integrity", "Domain only", "None"),
        q(2, "Which clause filters groups?", 1, "WHERE", "HAVING", "ORDER BY", "LIMIT"),
        q(2, "Command to remove a table?", 2, "DELETE", "REMOVE", "DROP", "CUT"),
        q(3, "ACID: A stands for?", 0, "Atomicity", "Accuracy", "Access", "Aggregation"),
        q(3, "3NF removes?", 0, "Transitive dependency", "Primary key", "Joins", "Indexes")
    )
)

var quiz: Map<String, List<Q>> = baseQuiz
    private set

fun loadMathBank(ctx: android.content.Context) {
    try {
        val arr = org.json.JSONArray(ctx.assets.open("math_questions.json").bufferedReader().use { it.readText() })
        val list = (0 until arr.length()).map { n ->
            val o = arr.getJSONObject(n)
            val opts = o.getJSONArray("o")
            Q(o.getInt("u") - 1, o.getString("q"), (0 until opts.length()).map { opts.getString(it) }, o.getInt("a"), o.optString("s"), o.optString("t"))
        }
        if (list.isNotEmpty()) quiz = baseQuiz + ("ma" to list)
    } catch (e: Exception) {
        android.util.Log.e("SmartStudy", "Maths question bank failed to load", e)
    }
}
