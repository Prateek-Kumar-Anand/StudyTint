package com.student.smartstudy

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class Q(val unit: Int, val q: String, val o: List<String>, val a: Int, val sol: String = "",
    val topic: String = "")

private fun q(u: Int, t: String, a: Int, vararg o: String) = Q(u, t, o.toList(), a)

private val baseQuiz: Map<String, List<Q>> = mapOf(
    "py" to listOf(
        q(1, "Which type is immutable?", 2, "list", "dict", "tuple", "set"),
        q(0, "What is type(3.0)?", 1, "int", "float", "str", "bool"),
        q(1, "Which keyword defines a function?", 1, "func", "def", "fun", "lambda"),
        q(0, "range(3) yields?", 1, "1,2,3", "0,1,2", "0,1,2,3", "1,2"),
        q(
            2, "Which method initialises an object?", 1,
            "__new__", "__init__", "__str__", "__call__"
        ),
        q(1, "A dict stores data as?", 1, "indexes", "key-value pairs", "sets", "tuples only"),
        q(2, "Safest way to open a file?", 1, "open() only", "with open()", "file()", "read()"),
        q(3, "Which block always runs?", 2, "try", "except", "finally", "else")
    ),
    "ma" to listOf(
        q(0, "Subsets of a 3-element set?", 2, "6", "7", "8", "9"),
        q(0, "A bijection is?", 2, "one-one only", "onto only", "one-one and onto", "neither"),
        q(1, "p → q is false when?", 1, "p T, q T", "p T, q F", "p F, q T", "p F, q F"),
        q(1, "An always-true formula is a?", 1, "Contradiction", "Tautology", "Fallacy", "Axiom"),
        q(2, "5C2 equals?", 1, "5", "10", "20", "25"),
        q(
            2, "10 pigeons, 9 holes means?", 1,
            "a hole is empty", "a hole has 2 or more", "each has 1", "none"
        ),
        q(3, "Sum of degrees with 5 edges?", 1, "5", "10", "15", "20"),
        q(3, "Edges in a tree of n vertices?", 1, "n", "n-1", "n+1", "2n")
    ),
    "ai" to listOf(
        q(0, "An agent perceives using?", 1, "Actuators", "Sensors", "Memory", "Heuristics"),
        q(0, "Who coined 'Artificial Intelligence'?", 1, "Turing", "McCarthy", "Minsky", "Newell"),
        q(1, "A* uses?", 0, "f = g + h", "f = g", "f = h only", "random"),
        q(
            1, "BFS finds the path with fewest?", 1,
            "costs", "steps", "nodes in memory", "heuristics"
        ),
        q(3, "Labelled data is used in?", 1, "Unsupervised", "Supervised", "Reinforcement", "None"),
        q(
            3, "Overfitting means?", 1,
            "Poor on training", "Good on training, poor on new", "Good on both", "Poor on both"
        ),
        q(
            2, "Which expert system component stores facts and rules?", 0,
            "Knowledge base", "User interface", "Compiler", "Scheduler"
        ),
        q(
            2, "Which component applies rules to known facts?", 1,
            "Knowledge base", "Inference engine", "Database index", "Explanation log"
        ),
        q(
            2, "Forward chaining starts from?", 2,
            "The final goal", "A random rule", "Known facts", "User feedback"
        ),
        q(
            3, "Neural nets update weights using?", 0,
            "Backpropagation", "Sorting", "Hashing", "Pooling"
        ),
        q(3, "CNNs are best known for?", 0, "Images", "Tables", "Sorting", "Compression")
    )
)

var quiz: Map<String, List<Q>> = baseQuiz
    private set

fun loadQuestionBanks(ctx: android.content.Context) {
    loadBank(ctx, "ma", "questions/maths_questions.json", keepBuiltIn = false)
    loadBank(ctx, "c", "questions/c_questions.json", keepBuiltIn = false)
    // Python and AI already ship a few built-in questions; the new banks are added after them.
    loadBank(ctx, "py", "questions/python_questions.json", keepBuiltIn = true)
    loadBank(ctx, "ai", "questions/ai_questions.json", keepBuiltIn = true)
    loadBank(ctx, "db", "questions/dbms_questions.json", keepBuiltIn = false)
}

private fun loadBank(ctx: android.content.Context, subjectId: String, file: String, keepBuiltIn: Boolean) {
    try {
        val arr = org.json.JSONArray(ctx.assets.open(file).bufferedReader().use { it.readText() })
        val list = (0 until arr.length()).map { n ->
            val o = arr.getJSONObject(n)
            val opts = o.getJSONArray("o")
            Q(o.getInt("u") - 1, o.getString("q"), (0 until opts.length()).map { opts.getString(it) },
                o.getInt("a"), o.optString("s"), o.optString("t"))
        }
        if (list.isNotEmpty()) quiz = quiz + (subjectId to ((if (keepBuiltIn) quiz[subjectId].orEmpty()
            else emptyList()) + list))
    } catch (e: Exception) {
        android.util.Log.e("SmartStudy", "Question bank $file failed to load", e)
    }
}
