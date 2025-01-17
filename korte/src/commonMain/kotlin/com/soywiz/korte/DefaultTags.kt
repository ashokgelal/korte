package com.soywiz.korte

@Suppress("unused")
object DefaultTags {
	val BlockTag = Tag("block", setOf(), setOf("end", "endblock")) {
		val part = chunks.first()
		val tokens = part.tag.tokens
		val name = ExprNode.parseId(tokens)
		if (name.isEmpty()) throw IllegalArgumentException("block without name")
		tokens.expectEnd()
		context.template.addBlock(name, part.body)
		DefaultBlocks.BlockBlock(name)
	}

	val Capture = Tag("capture", setOf(), null) {
		val main = chunks[0]
		val tr = main.tag.tokens
		val varname = ExprNode.parseId(tr)
		tr.expectEnd()
		DefaultBlocks.BlockCapture(varname, main.body)
	}

	val Debug = Tag("debug", setOf(), null) {
		DefaultBlocks.BlockDebug(chunks[0].tag.expr)
	}

	val Empty = Tag("", setOf(""), null) {
		Block.group(chunks.map { it.body })
	}

	val Extends = Tag("extends", setOf(), null) {
		val part = chunks.first()
		val parent = ExprNode.parseExpr(part.tag.tokens)
		DefaultBlocks.BlockExtends(parent)
	}

	val For = Tag("for", setOf("else"), setOf("end", "endfor")) {
		val main = chunks[0]
		val elseTag = chunks.getOrNull(1)?.body
		val tr = main.tag.tokens
		val varnames = arrayListOf<String>()
		do {
			varnames += ExprNode.parseId(tr)
		} while (tr.tryRead(",") != null)
		ExprNode.expect(tr, "in")
		val expr = ExprNode.parseExpr(tr)
		tr.expectEnd()
		DefaultBlocks.BlockFor(varnames, expr, main.body, elseTag)
	}

	val If = Tag("if", setOf("else", "elseif"), setOf("end", "endif")) {
		val ifBranches = arrayListOf<Pair<ExprNode, Block>>()
		var elseBranch: Block? = null

		for (part in chunks) {
			when (part.tag.name) {
				"if", "elseif" -> {
					ifBranches += part.tag.expr to part.body
				}
				"else" -> {
					elseBranch = part.body
				}
			}
		}
		val ifBranchesRev = ifBranches.reversed()
		var node: Block = DefaultBlocks.BlockIf(ifBranchesRev.first().first, ifBranchesRev.first().second, elseBranch)
		for (branch in ifBranchesRev.takeLast(ifBranchesRev.size - 1)) {
			node = DefaultBlocks.BlockIf(branch.first, branch.second, node)
		}

		node
	}

	val Import = Tag("import", setOf(), null) {
		val part = chunks.first()
		val s = part.tag.tokens
		val file = s.parseExpr()
		s.expect("as")
		val name = s.read().text
		s.expectEnd()
		DefaultBlocks.BlockImport(file, name)
	}

	val Include = Tag("include", setOf(), null) {
		val part = chunks.first()
		val fileName = part.tag.expr
		DefaultBlocks.BlockInclude(fileName)
	}

	val Macro = Tag("macro", setOf(), setOf("end", "endmacro")) {
		val part = chunks[0]
		val s = part.tag.tokens
		val funcname = s.parseId()
		s.expect("(")
		val params = s.parseIdList()
		s.expect(")")
		s.expectEnd()
		DefaultBlocks.BlockMacro(funcname, params, part.body)
	}

	val Set = Tag("set", setOf(), null) {
		val main = chunks[0]
		val tr = main.tag.tokens
		val varname = ExprNode.parseId(tr)
		ExprNode.expect(tr, "=")
		val expr = ExprNode.parseExpr(tr)
		tr.expectEnd()
		DefaultBlocks.BlockSet(varname, expr)
	}

	val Switch = Tag("switch", setOf("case", "default"), setOf("endswitch")) {
		var subject: ExprNode? = null
		val cases = arrayListOf<Pair<ExprNode, Block>>()
		var defaultCase: Block? = null

		for (part in this.chunks) {
			val body = part.body
			when (part.tag.name) {
				"switch" -> subject = part.tag.expr
				"case" -> cases += part.tag.expr to body
				"default" -> defaultCase = body
			}
		}
		if (subject == null) error("No subject set in switch")
		//println(this.chunks)
		object : Block {
			override suspend fun eval(context: Template.EvalContext) {
				val subjectValue = subject.eval(context)
				for ((case, block) in cases) {
					if (subjectValue == case.eval(context)) {
						block.eval(context)
						return
					}
				}
				defaultCase?.eval(context)
				return
			}
		}
	}

	val ALL = listOf(
		BlockTag,
		Capture, Debug,
		Empty, Extends, For, If, Switch, Import, Include, Macro, Set
	)
}