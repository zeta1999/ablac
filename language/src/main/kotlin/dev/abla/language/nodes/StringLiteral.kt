package dev.abla.language.nodes

import dev.abla.language.ASTVisitor
import dev.abla.language.Position

data class StringLiteral(val string: String, override val position: Position) : Literal {
    override suspend fun accept(visitor: ASTVisitor) {
        visitor.visit(this)
    }
}