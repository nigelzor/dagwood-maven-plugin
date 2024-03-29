package com.github.nigelzor.dagwood;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.graph.DependencyVisitor;

/**
 * adapted from org.apache.maven.shared.dependency.tree.traversal.SerializingDependencyNodeVisitor
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public class SerializingDependencyVisitor implements DependencyVisitor {

	/**
	 * Provides tokens to use when serializing the dependency tree.
	 */
	public static class TreeTokens {
		private final String nodeIndent;
		private final String lastNodeIndent;
		private final String fillIndent;
		private final String lastFillIndent;

		public TreeTokens(String nodeIndent, String lastNodeIndent, String fillIndent, String lastFillIndent) {
			this.nodeIndent = nodeIndent;
			this.lastNodeIndent = lastNodeIndent;
			this.fillIndent = fillIndent;
			this.lastFillIndent = lastFillIndent;
		}

		public String getNodeIndent(boolean last) {
			return last ? lastNodeIndent : nodeIndent;
		}

		public String getFillIndent(boolean last) {
			return last ? lastFillIndent : fillIndent;
		}
	}

	public static interface NodeFormatter {
		public String format(DependencyNode dependencyNode);
	}

	/**
	 * Format tree items as dependency:tree does
	 */
	public static class DefaultNodeFormatter implements NodeFormatter {
		private final String fallback;

		public DefaultNodeFormatter() {
			this("null");
		}

		public DefaultNodeFormatter(String fallback) {
			this.fallback = fallback;
		}

		public String format(DependencyNode dependencyNode) {
			Dependency dependency = dependencyNode.getDependency();
			if (dependency == null) {
				return fallback;
			}
			String formatted = dependency.getArtifact().toString();
			if (dependency.getScope().length() > 0) {
				formatted += ":" + dependency.getScope();
			}
			return formatted;
		}
	}

	/**
	 * Whitespace tokens to use when outputting the dependency tree.
	 */
	public static final TreeTokens WHITESPACE_TOKENS = new TreeTokens("   ", "   ", "   ", "   ");

	/**
	 * The standard ASCII tokens to use when outputting the dependency tree.
	 */
	public static final TreeTokens STANDARD_TOKENS = new TreeTokens("+- ", "\\- ", "|  ", "   ");

	/**
	 * The extended ASCII tokens to use when outputting the dependency tree.
	 */
	public static final TreeTokens EXTENDED_TOKENS = new TreeTokens("\u00c3\u00c4 ", "\u00c0\u00c4 ", "\u00b3  ", "   ");

	/**
	 * The writer to serialize to.
	 */
	private final PrintWriter writer;

	/**
	 * The tokens to use when serializing the dependency tree.
	 */
	private final TreeTokens tokens;

	private final NodeFormatter formatter;

	private Map<DependencyNode, DependencyNode> childToParent = new IdentityHashMap<DependencyNode, DependencyNode>();
	private Deque<DependencyNode> parent = new ArrayDeque<DependencyNode>();

	/**
	 * Creates a dependency node visitor that serializes visited nodes to the
	 * specified writer using whitespace tokens.
	 *
	 * @param writer
	 *            the writer to serialize to
	 */
	public SerializingDependencyVisitor(Writer writer) {
		this(writer, WHITESPACE_TOKENS, new DefaultNodeFormatter());
	}

	/**
	 * Creates a dependency node visitor that serializes visited nodes to the
	 * specified writer using the specified tokens.
	 *
	 * @param writer
	 *            the writer to serialize to
	 * @param tokens
	 *            the tokens to use when serializing the dependency tree
	 * @param formatter
	 *            the formatter to use when serializing dependency nodes
	 */
	public SerializingDependencyVisitor(Writer writer, TreeTokens tokens, NodeFormatter formatter) {
		if (writer instanceof PrintWriter) {
			this.writer = (PrintWriter) writer;
		} else {
			this.writer = new PrintWriter(writer, true);
		}
		this.tokens = tokens;
		this.formatter = formatter;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean visitEnter(DependencyNode node) {
		if (!parent.isEmpty()) {
			childToParent.put(node, parent.peek());
		}
		indent(node);
		writer.println(formatter.format(node));
		parent.push(node);
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean visitLeave(DependencyNode node) {
		childToParent.remove(node);
		parent.pop();
		return true;
	}

	/**
	 * Writes the necessary tokens to indent the specified dependency node to
	 * this visitor's writer.
	 *
	 * @param node
	 *            the dependency node to indent
	 */
	private void indent(DependencyNode node) {
		int depth = parent.size();
		for (int i = 1; i < depth; i++) {
			writer.write(tokens.getFillIndent(isLast(node, i)));
		}

		if (depth > 0) {
			writer.write(tokens.getNodeIndent(isLast(node)));
		}
	}

	/**
	 * Gets whether the specified dependency node is the last of its siblings.
	 *
	 * @param node
	 *            the dependency node to check
	 * @return <code>true</code> if the specified dependency node is the last of
	 *         its last siblings
	 */
	private boolean isLast(DependencyNode node) {
		DependencyNode parent = childToParent.get(node);

		boolean last;

		if (parent == null) {
			last = true;
		} else {
			List<DependencyNode> siblings = parent.getChildren();

			last = (siblings.indexOf(node) == siblings.size() - 1);
		}

		return last;
	}

	/**
	 * Gets whether the specified dependency node ancestor is the last of its
	 * siblings.
	 *
	 * @param node
	 *            the dependency node whose ancestor to check
	 * @param ancestorDepth
	 *            the depth of the ancestor of the specified dependency node to
	 *            check
	 * @return <code>true</code> if the specified dependency node ancestor is
	 *         the last of its siblings
	 */
	private boolean isLast(DependencyNode node, int ancestorDepth) {
		int distance = parent.size() - ancestorDepth;

		while (distance-- > 0) {
			node = childToParent.get(node);
		}

		return isLast(node);
	}
}
