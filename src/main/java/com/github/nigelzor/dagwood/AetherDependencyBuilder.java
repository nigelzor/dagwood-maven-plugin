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
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.artifact.ArtifactType;
import org.sonatype.aether.artifact.ArtifactTypeRegistry;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.Exclusion;
import org.sonatype.aether.util.artifact.ArtifactProperties;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.DefaultArtifactType;

/**
 * adapted from org.apache.maven.repository.internal.DefaultArtifactDescriptorReader
 *
 * @author Benjamin Bentmann
 */
public class AetherDependencyBuilder {

	public void addDependenciesToRequest(CollectRequest collectRequest, Model model, ArtifactTypeRegistry stereotypes) {
		for (org.apache.maven.model.Dependency dependency : model.getDependencies()) {
			collectRequest.addDependency(convert(dependency, stereotypes));
		}

		DependencyManagement mngt = model.getDependencyManagement();
		if (mngt != null) {
			for (org.apache.maven.model.Dependency dependency : mngt.getDependencies()) {
				collectRequest.addManagedDependency(convert(dependency, stereotypes));
			}
		}
	}

	private Dependency convert(org.apache.maven.model.Dependency dependency, ArtifactTypeRegistry stereotypes) {
		ArtifactType stereotype = stereotypes.get(dependency.getType());
		if (stereotype == null) {
			stereotype = new DefaultArtifactType(dependency.getType());
		}

		boolean system = dependency.getSystemPath() != null && dependency.getSystemPath().length() > 0;

		Map<String, String> props = null;
		if (system) {
			props = Collections.singletonMap(ArtifactProperties.LOCAL_PATH, dependency.getSystemPath());
		}

		Artifact artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getClassifier(), null,
				dependency.getVersion(), props, stereotype);

		List<Exclusion> exclusions = new ArrayList<Exclusion>(dependency.getExclusions().size());
		for (org.apache.maven.model.Exclusion exclusion : dependency.getExclusions()) {
			exclusions.add(convert(exclusion));
		}

		return new Dependency(artifact, dependency.getScope(), dependency.isOptional(), exclusions);
	}

	private Exclusion convert(org.apache.maven.model.Exclusion exclusion) {
		return new Exclusion(exclusion.getGroupId(), exclusion.getArtifactId(), "*", "*");
	}

}
