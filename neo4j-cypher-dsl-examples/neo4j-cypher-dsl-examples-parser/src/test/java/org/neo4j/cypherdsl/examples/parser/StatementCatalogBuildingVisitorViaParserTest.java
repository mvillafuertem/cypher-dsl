/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypherdsl.examples.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.neo4j.cypherdsl.core.StatementCatalog;
import org.neo4j.cypherdsl.parser.CypherParser;

/**
 * @author Michael J. Simons
 */
class StatementCatalogBuildingVisitorViaParserTest {

	@Test
	void simpleShowCase() {

		// tag::catalog-example[]
		var input = """
			MATCH (m:`Movie` {title: 'The Matrix'})<-[a:`ACTED_IN`]-(p:`Person`)
			WHERE p.born >= $born
			RETURN p
			""";
		var statement = CypherParser.parse(input);

		var catalog = statement.getCatalog();

		assertThat(catalog.getNodeLabels())
			.extracting(StatementCatalog.Token::value)
			.containsExactlyInAnyOrder("Person", "Movie");
		assertThat(catalog.getProperties())
			.containsExactlyInAnyOrder(
				StatementCatalog.property(Set.of(StatementCatalog.label("Movie")), "title"),
				StatementCatalog.property(Set.of(StatementCatalog.label("Person")), "born")
			);
		// end::catalog-example[]

		var cypher = statement.getCypher();
		assertThat(cypher).isEqualTo("MATCH (m:`Movie` {title: 'The Matrix'})<-[a:`ACTED_IN`]-(p:`Person`) WHERE p.born >= $born RETURN p");
	}

	@Test
	void mapOfLabelsAndProperties() {
		var input = """
			MATCH (m:`Movie` {title: 'The Matrix'})<-[a:`ACTED_IN`]-(p:`Person`|Actor {b:true})
			WHERE p.born >= $born
			  AND a.starring = true
			RETURN p
			""";
		var statement = CypherParser.parse(input);

		var catalog = statement.getCatalog();

		assertThat(catalog.getProperties())
			.anyMatch(p -> p.owningToken().equals(Set.of(StatementCatalog.label("Person"), StatementCatalog.label("Actor"))));
		Map<String, List<String>> labelsAndProperties = catalog.getProperties()
			.stream()
			.filter(p -> p.owningToken().stream().allMatch(t -> t.type() == StatementCatalog.Token.Type.NODE_LABEL))
			.collect(
				Collectors.toMap(
					p -> p.owningToken().stream().map(StatementCatalog.Token::value).collect(Collectors.joining(",")),
					p -> List.of(p.name()),
				(l1, l2) -> {
					var mergedList = new ArrayList<>(l1);
					mergedList.addAll(l2);
					Collections.sort(mergedList);
					return mergedList;
				})
			);

		assertThat(labelsAndProperties)
			.containsExactly(Map.entry("Movie", List.of("title")), Map.entry("Actor,Person", List.of("b", "born")));
	}

	@Test // GH-674
	void retrievalOfRelationshipsAndTargetSourcesShouldWork() {
		var input = """
			MATCH (m:`Movie` {title: 'The Matrix'})<-[a:`ACTED_IN`]-(p:`Person`|Actor {b:true})
			MATCH () -[:WHATEVER]-> (m)
			MATCH (x:X) -[:UNDIRECTED]- (y:Y)
			MATCH (m) -[:UNDIRECTED]- (y)
			WHERE p.born >= $born
			  AND a.starring = true
			WITH m
			MATCH (m) -[:FOO]->(f:FooNode)
			CALL {
				MATCH (:LabelA) -[:A_REL]-> (n:X)
			}
			RETURN p
			""";
		var statement = CypherParser.parse(input);

		var catalog = statement.getCatalog();

		assertThat(catalog.getOutgoingRelations(StatementCatalog.label("Person")))
			.containsExactlyInAnyOrder(StatementCatalog.type("ACTED_IN"));
		assertThat(catalog.getOutgoingRelations(StatementCatalog.label("Movie")))
			.containsExactlyInAnyOrder(StatementCatalog.type("FOO"));
		assertThat(catalog.getOutgoingRelations(StatementCatalog.label("Actor")))
			.containsExactlyInAnyOrder(StatementCatalog.type("ACTED_IN"));
		assertThat(catalog.getOutgoingRelations(StatementCatalog.label("LabelA")))
			.containsExactlyInAnyOrder(StatementCatalog.type("A_REL"));

		assertThat(catalog.getIncomingRelations(StatementCatalog.label("Movie")))
			.containsExactlyInAnyOrder(StatementCatalog.type("ACTED_IN"), StatementCatalog.type("WHATEVER"));
		assertThat(catalog.getIncomingRelations(StatementCatalog.label("FooNode")))
			.containsExactlyInAnyOrder(StatementCatalog.type("FOO"));
		assertThat(catalog.getIncomingRelations(StatementCatalog.label("X")))
			.containsExactlyInAnyOrder(StatementCatalog.type("A_REL"));

		assertThat(catalog.getUndirectedRelations(StatementCatalog.label("X")))
			.containsExactlyInAnyOrder(StatementCatalog.type("UNDIRECTED"));

		assertThat(catalog.getTargetNodes(StatementCatalog.type("ACTED_IN")))
			.containsExactlyInAnyOrder(StatementCatalog.label("Movie"));
		assertThat(catalog.getTargetNodes(StatementCatalog.type("FOO")))
			.containsExactlyInAnyOrder(StatementCatalog.label("FooNode"));
		assertThat(catalog.getTargetNodes(StatementCatalog.type("A_REL")))
			.containsExactlyInAnyOrder(StatementCatalog.label("X"));
		assertThat(catalog.getTargetNodes(StatementCatalog.type("WHATEVER")))
			.containsExactlyInAnyOrder(StatementCatalog.label("Movie"));
		assertThat(catalog.getTargetNodes(StatementCatalog.type("UNDIRECTED")))
			.isEmpty();

		assertThat(catalog.getSourceNodes(StatementCatalog.type("ACTED_IN")))
			.containsExactlyInAnyOrder(StatementCatalog.label("Person"), StatementCatalog.label("Actor"));
		assertThat(catalog.getSourceNodes(StatementCatalog.type("FOO")))
			.containsExactlyInAnyOrder(StatementCatalog.label("Movie"));
		assertThat(catalog.getSourceNodes(StatementCatalog.type("A_REL")))
			.containsExactlyInAnyOrder(StatementCatalog.label("LabelA"));
		assertThat(catalog.getSourceNodes(StatementCatalog.type("WHATEVER")))
			.isEmpty();
		assertThat(catalog.getSourceNodes(StatementCatalog.type("UNDIRECTED")))
			.isEmpty();
	}
}
