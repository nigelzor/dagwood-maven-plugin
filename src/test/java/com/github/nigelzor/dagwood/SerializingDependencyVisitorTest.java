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

import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.graph.DefaultDependencyNode;

import com.github.nigelzor.dagwood.SerializingDependencyVisitor.DefaultNodeFormatter;

import static org.junit.Assert.assertEquals;

/**
 * adapted from org.apache.maven.shared.dependency.tree.traversal.SerializingDependencyNodeVisitorTest
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public class SerializingDependencyVisitorTest {
	private static final String NEWLINE = System.getProperty("line.separator");

	private StringWriter writer;
	private SerializingDependencyVisitor serializer;

	@Before
	public void setUp() throws Exception {
		writer = new StringWriter();
		serializer = new SerializingDependencyVisitor(writer,
				SerializingDependencyVisitor.STANDARD_TOKENS,
				new DefaultNodeFormatter("dagwood"));
	}

	@Test
	public void testSingleNode() {
		DependencyNode rootNode = createNode("g:p:t:1");

		assertTree(
				"g:p:t:1" + NEWLINE, rootNode);
	}

	@Test
	public void testNodeWithChild() {
		DependencyNode rootNode = createNode("g:p:t:1");
		rootNode.getChildren().add(createNode("g:a:t:1"));

		assertTree(
				"g:p:t:1" + NEWLINE +
				"\\- g:a:t:1" + NEWLINE, rootNode);
	}

	@Test
	public void testNodeWithMultipleChildren() {
		DependencyNode rootNode = createNode("g:p:t:1");
		rootNode.getChildren().add(createNode("g:a:t:1"));
		rootNode.getChildren().add(createNode("g:b:t:1"));
		rootNode.getChildren().add(createNode("g:c:t:1"));

		assertTree(
				"g:p:t:1" + NEWLINE +
				"+- g:a:t:1" + NEWLINE +
				"+- g:b:t:1" + NEWLINE +
				"\\- g:c:t:1" + NEWLINE, rootNode);
	}

	@Test
	public void testNodeWithGrandchild() {
		DependencyNode rootNode = createNode("g:p:t:1");
		DependencyNode childNode = createNode("g:a:t:1");
		rootNode.getChildren().add(childNode);
		childNode.getChildren().add(createNode("g:b:t:1"));

		assertTree(
				"g:p:t:1" + NEWLINE +
				"\\- g:a:t:1" + NEWLINE +
				"   \\- g:b:t:1" + NEWLINE, rootNode);
	}

	@Test
	public void testNodeWithMultipleGrandchildren() {
		DependencyNode rootNode = createNode("g:p:t:1");
		DependencyNode child1Node = createNode("g:a:t:1");
		rootNode.getChildren().add(child1Node);
		child1Node.getChildren().add(createNode("g:b:t:1"));
		DependencyNode child2Node = createNode("g:c:t:1");
		rootNode.getChildren().add(child2Node);
		child2Node.getChildren().add(createNode("g:d:t:1"));

		assertTree(
				"g:p:t:1" + NEWLINE +
				"+- g:a:t:1" + NEWLINE +
				"|  \\- g:b:t:1" + NEWLINE +
				"\\- g:c:t:1" + NEWLINE +
				"   \\- g:d:t:1" + NEWLINE, rootNode);
	}

	@Test
	public void testArtifactlessParentNode() {
		DependencyNode rootNode = new DefaultDependencyNode();
		rootNode.getChildren().add(createNode("g:a:t:1"));

		assertTree(
				"dagwood" + NEWLINE +
				"\\- g:a:t:1" + NEWLINE, rootNode);
	}

	private static DependencyNode createNode(String id) {
		return new DefaultDependencyNode(new Dependency(new DefaultArtifact(id), null));
	}

	private void assertTree(String expectedTree, DependencyNode actualNode) {
		actualNode.accept(serializer);
		assertEquals(expectedTree, writer.toString());
	}
}
