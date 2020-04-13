package dev.ablac.language.nodes

import dev.ablac.language.ASTVisitor
import dev.ablac.language.Position

data class FunctionCall(val name: String, val parameters: Array<Expression>, override val position: Position) : Expression {
    override suspend fun accept(visitor: ASTVisitor) {
        visitor.visit(this)
    }
}