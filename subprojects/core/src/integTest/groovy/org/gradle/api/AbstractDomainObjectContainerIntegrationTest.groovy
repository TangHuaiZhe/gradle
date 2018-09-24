/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

abstract class AbstractDomainObjectContainerIntegrationTest extends AbstractIntegrationSpec {
    abstract String makeContainer()
    abstract String disallowMutationMessage(String assertingMethod)

    Map<String, String> getQueryCodeUnderTest() {
        [
            "getByName(String)":    "testContainer.getByName('unrealized')",
            "named(String)":        "testContainer.named('unrealized')",
            "findAll(Closure)":     "testContainer.findAll { it.name == 'unrealized' }",
            "findByName(String)":   "testContainer.findByName('unrealized')",
            "TaskProvider.get()":   "unrealized.get()",
            "iterator()":           "for (def element : testContainer) { println element.name }",
        ]
    }

    Map<String, String> getMutationCodeUnderTest() {
        [
            "create(String)":   "testContainer.create('c')",
            "register(String)": "testContainer.register('c')",
        ]
    }

    def setup() {
        buildFile << """
            def testContainer = ${makeContainer()}
            def toBeRealized = testContainer.register('toBeRealized')
            def unrealized = testContainer.register('unrealized')
            def realized = testContainer.register('realized')
            realized.get()
        """
    }

    @Unroll
    def "can execute query method #queryMethod.key from configureEach"() {
        buildFile << """
            testContainer.configureEach {
                ${queryMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        succeeds "help"

        where:
        queryMethod << getQueryCodeUnderTest()
    }

    @Unroll
    def "can execute query method #queryMethod.key from withType.configureEach"() {
        buildFile << """
            testContainer.withType(testContainer.type).configureEach {
                ${queryMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        succeeds "help"

        where:
        queryMethod << getQueryCodeUnderTest()
    }

    @Unroll
    def "can execute query method #queryMethod.key from Provider.configure"() {
        buildFile << """
            toBeRealized.configure {
                ${queryMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        succeeds "help"

        where:
        queryMethod << getQueryCodeUnderTest()
    }

    @Unroll
    def "can execute query method #queryMethod.key from Provider.configure (realized)"() {
        buildFile << """
            realized.configure {
                ${queryMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        succeeds "help"

        where:
        queryMethod << getQueryCodeUnderTest()
    }

    @Unroll
    def "cannot execute mutation method #queryMethod.key from configureEach"() {
        buildFile << """
            testContainer.configureEach {
                ${queryMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        fails "help"
        failure.assertHasCause(disallowMutationMessage(queryMethod.key))

        where:
        queryMethod << getMutationCodeUnderTest()
    }

    @Unroll
    def "cannot execute mutation method #queryMethod.key from withType.configureEach"() {
        buildFile << """
            testContainer.withType(testContainer.type).configureEach {
                ${queryMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        fails "help"
        failure.assertHasCause(disallowMutationMessage(queryMethod.key))

        where:
        queryMethod << getMutationCodeUnderTest()
    }

    @Unroll
    def "cannot execute mutation method #queryMethod.key from Provider.configure"() {
        buildFile << """
            toBeRealized.configure {
                ${queryMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        fails "help"
        failure.assertHasCause(disallowMutationMessage(queryMethod.key))

        where:
        queryMethod << getMutationCodeUnderTest()
    }

    @Unroll
    def "cannot execute mutation method #queryMethod.key from Provider.configure (realized)"() {
        buildFile << """
            realized.configure {
                ${queryMethod.value}
            }
            toBeRealized.get()
        """

        expect:
        fails "help"
        failure.assertHasCause(disallowMutationMessage(queryMethod.key))

        where:
        queryMethod << getMutationCodeUnderTest()
    }
}
