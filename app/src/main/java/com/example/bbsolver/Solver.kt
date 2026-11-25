package com.example.bbsolver

data class Move(val pi: Int, val r: Int, val c: Int, val cells: List<IntArray>, val lines: Int)
class Solution(val moves: List<Move>, val complete: Boolean, val total: Int)

/**
 * Vét cạn mọi thứ tự & vị trí đặt của tối đa 3 mảnh.
 * Điểm số ưu tiên: đặt hết mảnh > xóa nhiều dòng & combo > bàn sạch > tránh lỗ/hàng nguy hiểm.
 */
object Solver {
    private data class StateKey(val rows: List<Int>, val used: Int)

    private fun toMasks(cells: List<IntArray>): IntArray {
        if (cells.isEmpty()) return IntArray(0)
        val minR = cells.minOf { it[0] }; val minC = cells.minOf { it[1] }
        val h = cells.maxOf { it[0] } - minR + 1
        val masks = IntArray(h)
        for (p in cells) {
            val r = p[0] - minR; val c = p[1] - minC
            if (r in masks.indices && c in 0..7) masks[r] = masks[r] or (1 shl c)
        }
        return masks
    }

    private fun width(mask: IntArray): Int =
        if (mask.isEmpty()) 0 else mask.maxOf { 32 - Integer.numberOfLeadingZeros(it) }

    private fun clearCompleted(input: IntArray): Pair<IntArray, Int> {
        var fullRows = 0; var fullCols = 255
        for (r in 0 until 8) {
            if ((input[r] and 255) == 255) fullRows++
            fullCols = fullCols and input[r]
        }
        val colCount = Integer.bitCount(fullCols)
        val out = IntArray(8) { r ->
            if ((input[r] and 255) == 255) 0 else input[r] and fullCols.inv() and 255
        }
        return out to (fullRows + colCount)
    }

    private fun canPlace(rows: IntArray, mask: IntArray, r: Int, c: Int): Boolean {
        if (mask.isEmpty() || r < 0 || r + mask.size > 8 || c < 0 || c + width(mask) > 8) return false
        for (i in mask.indices) if ((rows[r + i] and (mask[i] shl c)) != 0) return false
        return true
    }

    /** Điểm càng cao càng tốt. linesPerMove[d] = số dòng mảnh thứ d xóa được. */
    private fun score(rows: IntArray, cleared: Int, moves: Int, linesPerMove: IntArray): Double {
        var empty = 0; var holes = 0; var roughness = 0; var danger = 0

        fun occ(r: Int, c: Int): Int =
            if (r !in 0..7 || c !in 0..7) 1 else (rows[r] ushr c) and 1

        for (r in 0 until 8) for (c in 0 until 8) {
            if (occ(r, c) == 0) {
                empty++
                if (occ(r - 1, c) + occ(r + 1, c) + occ(r, c - 1) + occ(r, c + 1) == 4) holes++
            }
            if (c < 7 && occ(r, c) != occ(r, c + 1)) roughness++
            if (r < 7 && occ(r, c) != occ(r + 1, c)) roughness++
        }

        // Hàng/cột nguy hiểm: 7 ô đầy dễ kẹt, 6 ô đầy khó lấp bằng mảnh ngẫu nhiên
        for (r in 0 until 8) when (Integer.bitCount(rows[r] and 255)) { 7 -> danger += 3; 6 -> danger += 1 }
        for (c in 0 until 8) {
            var n = 0; for (r in 0 until 8) if (((rows[r] ushr c) and 1) == 1) n++
            when (n) { 7 -> danger += 3; 6 -> danger += 1 }
        }

        // Combo: xóa nhiều dòng cùng 1 mảnh → hệ số nhân lớn trong game
        var combo = 0.0
        for (i in 0 until moves) if (linesPerMove[i] >= 2)
            combo += linesPerMove[i].toDouble() * linesPerMove[i] * linesPerMove[i] * 800.0

        return moves * 10_000_000.0 +
               cleared * 30_000.0 +
               combo +
               empty * 150.0 -
               holes * 2500.0 -
               danger * 4000.0 -
               roughness * 25.0
    }

    fun solve(board: IntArray, pieces: List<List<IntArray>>): Solution? {
        if (board.size != 8 || pieces.size != 3) return null
        val start = IntArray(8) { board[it] and 255 }
        val masks = pieces.map(::toMasks)
        val active = pieces.indices.filter { masks[it].isNotEmpty() }
        if (active.isEmpty()) return null

        var bestMoves: List<Move> = emptyList()
        var bestScore = Double.NEGATIVE_INFINITY
        val memo = HashMap<StateKey, Int>()
        val linesArr = IntArray(3)

        fun visit(rows: IntArray, used: Int, path: List<Move>, cleared: Int) {
            val cur = score(rows, cleared, path.size, linesArr)
            if (cur > bestScore) { bestScore = cur; bestMoves = path }
            if (path.size == active.size) return

            val key = StateKey(rows.toList(), used)
            val prev = memo[key]
            if (prev != null && prev >= cleared) return
            memo[key] = cleared

            val depth = path.size
            for (pi in active) {
                if ((used and (1 shl pi)) != 0) continue
                val shape = masks[pi]; val w = width(shape)
                if (shape.isEmpty() || w == 0 || shape.size > 8 || w > 8) continue
                for (r in 0..(8 - shape.size)) for (c in 0..(8 - w)) {
                    if (!canPlace(rows, shape, r, c)) continue
                    val placed = rows.copyOf()
                    for (i in shape.indices) placed[r + i] = placed[r + i] or (shape[i] shl c)
                    val (next, removed) = clearCompleted(placed)
                    linesArr[depth] = removed
                    visit(next, used or (1 shl pi), path + Move(pi, r, c, pieces[pi], removed), cleared + removed)
                }
            }
            linesArr[depth] = 0
        }

        visit(start, 0, emptyList(), 0)
        return Solution(bestMoves, bestMoves.size == active.size, active.size)
    }
}