package com.student.smartstudy

import androidx.compose.ui.graphics.Color

data class Chap(val title: String, val notes: List<String>)
data class Subject(val id: String, val name: String, val mark: String, val color: Color, val units: List<Chap>)

private fun c(t: String, vararg n: String) = Chap(t, n.toList())

val subjects = listOf(
    Subject("py", "Python", "Py", Color(0xFF7C6CF0), listOf(
        c("Unit 1: Basics & Data Types", "Python is interpreted and dynamically typed.", "Core types: int, float, str, bool, None.", "Use input(), print() and f-strings for I/O."),
        c("Unit 2: Control Flow & Functions", "if/elif/else, for and while loops, break/continue.", "Define functions with def; use default and keyword arguments.", "Lambda gives small anonymous functions."),
        c("Unit 3: Data Structures & OOP", "list, tuple, set and dict cover most needs.", "Classes bundle data and methods; __init__ builds objects.", "Inheritance reuses behaviour; polymorphism overrides it."),
        c("Unit 4: Files, Modules & Exceptions", "open() with 'with' handles files safely.", "import modules; pip installs packages.", "try/except/finally handles runtime errors.")
    )),
    Subject("ma", "Maths", "∑", Color(0xFFF2894E), listOf(
        c("Unit 1: Sets, Relations & Functions", "Sets, subsets, Venn diagrams and Cartesian products.", "Relations: reflexive, symmetric, transitive; equivalence relations and partitions; closures and Warshall's algorithm.", "Functions: injective, surjective, bijective, composition and inverse."),
        c("Unit 2: Counting & Recurrence", "Permutations, combinations, binomial coefficients and Pascal's identity.", "Pigeonhole principle.", "Recurrence relations: Fibonacci, Tower of Hanoi, characteristic equation."),
        c("Unit 3: Matrix Algebra", "Matrix types, operations, determinants, rank and inverse.", "Symmetric, skew-symmetric and orthogonal matrices.", "Linear systems, eigenvalues and eigenvectors."),
        c("Unit 4: Elementary Graph Theory", "Terminology, paths, cycles, connectivity and subgraphs.", "Eulerian and Hamiltonian graphs, digraphs, weighted graphs.", "Trees, spanning trees and planar graphs.")
    )),
    Subject("ai", "Exploration in AI", "AI", Color(0xFFC2457F), listOf(
        c("Unit 1: Artificial Intelligence Foundations", "Human versus machine intelligence; defining AI and the Turing Test.", "History: Dartmouth 1956, AI winters and modern milestones.", "Narrow, general and super AI; data science, computer vision and NLP."),
        c("Unit 2: Problem Solving & Search Basics", "AI as problem solving: states, actions, goals and path cost.", "Uninformed search: breadth-first and depth-first.", "Informed search and heuristics: greedy best-first and A*."),
        c("Unit 3: Knowledge Representation & Expert Systems", "Data, information and knowledge; propositional logic.", "Rule-based systems, semantic networks and inference.", "Expert system architecture, applications and limits."),
        c("Unit 4: Machine Learning & AI Ethics", "Supervised, unsupervised and reinforcement learning.", "Recommendation engines and virtual assistants.", "Bias, privacy, deepfakes and responsible AI.")
    )),
    Subject("db", "DBMS", "DB", Color(0xFF3B9BE0), listOf(
        c("Unit 1: Database Foundations & Design", "DBMS versus file systems; hierarchical, network and relational models.", "The ER model: entities, attributes, relationships and diagrams.", "Integrity constraints: primary key, foreign key, UNIQUE and CHECK."),
        c("Unit 2: Relational Algebra & SQL", "Selection, projection, joins and other algebra operations.", "DDL, DML, SELECT, subqueries, views, stored procedures and triggers.", "SQL injection, parameterized queries and database hardening."),
        c("Unit 3: Normalization & Transactions", "Functional dependencies and normal forms up to BCNF.", "ACID properties, schedules, concurrency control and locking.", "Deadlocks, logging and crash recovery."),
        c("Unit 4: Security & Emerging Trends", "Access control (DAC, RBAC), encryption and auditing.", "SQL versus NoSQL: MongoDB, Cassandra and the CAP theorem.", "Big data and blockchain.")
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
        q(3, "Labelled data is used in?", 1, "Unsupervised", "Supervised", "Reinforcement", "None"),
        q(3, "Overfitting means?", 1, "Poor on training", "Good on training, poor on new", "Good on both", "Poor on both"),
        q(2, "Which expert system component stores facts and rules?", 0, "Knowledge base", "User interface", "Compiler", "Scheduler"),
        q(2, "Which component applies rules to known facts?", 1, "Knowledge base", "Inference engine", "Database index", "Explanation log"),
        q(2, "Forward chaining starts from?", 2, "The final goal", "A random rule", "Known facts", "User feedback"),
        q(3, "Neural nets update weights using?", 0, "Backpropagation", "Sorting", "Hashing", "Pooling"),
        q(3, "CNNs are best known for?", 0, "Images", "Tables", "Sorting", "Compression")
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
        if (list.isNotEmpty()) quiz = quiz + ("ma" to list)
    } catch (e: Exception) {
        android.util.Log.e("SmartStudy", "Maths question bank failed to load", e)
    }
}
