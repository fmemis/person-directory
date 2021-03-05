/**
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apereo.services.persondir.support.jdbc;

import junit.framework.TestResult;
import org.apereo.services.persondir.IPersonAttributeDaoFilter;
import org.apereo.services.persondir.support.AbstractDefaultAttributePersonAttributeDao;
import org.apereo.services.persondir.support.SimpleUsernameAttributeProvider;
import org.apereo.services.persondir.util.Util;
import org.junit.Ignore;
import org.postgresql.Driver;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test the {@link SingleRowJdbcPersonAttributeDao} against a dummy DataSource.
 *
 * @author andrew.petro@yale.edu
 * @author Eric Dalquist

 */
@Ignore
public class PostgresSingleRowJdbcPersonAttributeDaoTest extends AbstractCaseSensitivityJdbcPersonAttributeDaoTest {

    @Override
    public void run(final TestResult result) {
        if (Util.isPortInUse("localhost", 5432)) {
            super.run(result);
        }
    }


    @Override
    protected DataSource setUpDataSource() {
        return new SimpleDriverDataSource(new Driver(), "jdbc:postgresql://localhost:5432/postgres", "postgres", "password");
    }

    @Override
    protected void setUpSchema(final DataSource dataSource) throws SQLException {
        var con = dataSource.getConnection();

        con.prepareStatement("CREATE TABLE user_table " +
            "(netid VARCHAR, " +
            "name VARCHAR, " +
            "email VARCHAR, " +
            "locations TEXT[], " +
            "shirt_color VARCHAR)").execute();

        con.prepareStatement("INSERT INTO user_table " +
            "(netid, name, email, shirt_color, locations) " +
            "VALUES ('awp9', 'Andrew', 'andrew.petro@yale.edu', 'blue', '{\"usa\",\"uk\"}')").execute();

        con.prepareStatement("INSERT INTO user_table " +
            "(netid, name, email, shirt_color) " +
            "VALUES ('edalquist', 'Eric', 'edalquist@unicon.net', 'blue')").execute();

        con.prepareStatement("INSERT INTO user_table " +
            "(netid, name, email, shirt_color) " +
            "VALUES ('atest', 'Andrew', 'andrew.test@test.net', 'red')").execute();

        con.prepareStatement("INSERT INTO user_table " +
            "(netid, name, email, shirt_color) " +
            "VALUES ('susan', 'Susan', 'susan.test@test.net', null)").execute();

        con.close();
    }

    @Override
    protected void tearDownSchema(final DataSource dataSource) throws SQLException {
        var con = dataSource.getConnection();
        con.prepareStatement("DROP TABLE user_table").execute();
        con.close();
    }

    @Override
    protected AbstractJdbcPersonAttributeDao<Map<String, Object>> newDao(final DataSource dataSource) {
        return new SingleRowJdbcPersonAttributeDao(dataSource, "SELECT netid, name, email, shirt_color FROM user_table WHERE {0}");
    }

    @Override
    protected boolean supportsPerDataAttributeCaseSensitivity() {
        return true;
    }

    @Override
    protected void beforeNonUsernameQuery(final AbstractJdbcPersonAttributeDao<Map<String, Object>> dao) {
        // no-op
    }

    public void testNoQueryAttributeMapping() {
        var impl = new SingleRowJdbcPersonAttributeDao(testDataSource, "SELECT name, email, shirt_color FROM user_table WHERE netid = 'awp9'");
        impl.setUseAllQueryAttributes(false);

        impl.setUsernameAttributeProvider(new SimpleUsernameAttributeProvider("uid"));

        final Map<String, Object> columnsToAttributes = new HashMap<>();
        columnsToAttributes.put("name", "firstName");

        final Set<String> emailAttributeNames = new HashSet<>();
        emailAttributeNames.add("email");
        emailAttributeNames.add("emailAddress");
        columnsToAttributes.put("email", emailAttributeNames);
        columnsToAttributes.put("shirt_color", "dressShirtColor");
        impl.setResultAttributeMapping(columnsToAttributes);

        var attribs = impl.getPerson("awp9", IPersonAttributeDaoFilter.alwaysChoose()).getAttributes();
        assertEquals(Util.list("andrew.petro@yale.edu"), attribs.get("email"));
        assertEquals(Util.list("andrew.petro@yale.edu"), attribs.get("emailAddress"));
        assertEquals(Util.list("blue"), attribs.get("dressShirtColor"));
        assertNull(attribs.get("shirt_color"));
        assertEquals(Util.list("Andrew"), attribs.get("firstName"));
    }

    /**
     * Test that the implementation properly reports the attribute names it
     * expects to map.
     */
    public void testPossibleUserAttributeNames() {
        var impl = new SingleRowJdbcPersonAttributeDao(testDataSource, "SELECT name, email, shirt_color FROM user_table WHERE {0}");
        impl.setQueryAttributeMapping(Collections.singletonMap("uid", "netid"));

        final Map<String, Object> columnsToAttributes = new HashMap<>();
        columnsToAttributes.put("name", "firstName");

        final Set<String> emailAttributeNames = new HashSet<>();
        emailAttributeNames.add("email");
        emailAttributeNames.add("emailAddress");
        columnsToAttributes.put("email", emailAttributeNames);
        columnsToAttributes.put("shirt_color", "dressShirtColor");
        impl.setResultAttributeMapping(columnsToAttributes);

        final Set<String> expectedAttributeNames = new HashSet<>();
        expectedAttributeNames.add("firstName");
        expectedAttributeNames.add("email");
        expectedAttributeNames.add("emailAddress");
        expectedAttributeNames.add("dressShirtColor");

        var attributeNames = impl.getPossibleUserAttributeNames(IPersonAttributeDaoFilter.alwaysChoose());
        assertEquals(attributeNames, expectedAttributeNames);
    }

    /**
     * Test for a query with a single attribute
     */
    public void testSingleAttrQuery() {
        var impl = new
            SingleRowJdbcPersonAttributeDao(testDataSource, "SELECT name, email, shirt_color, locations FROM user_table WHERE {0}");
        impl.setQueryAttributeMapping(Collections.singletonMap("uid", "netid"));

        impl.setUsernameAttributeProvider(new SimpleUsernameAttributeProvider("uid"));

        final Map<String, Object> columnsToAttributes = new HashMap<>();
        columnsToAttributes.put("name", "firstName");

        final Set<String> emailAttributeNames = new HashSet<>();
        emailAttributeNames.add("email");
        emailAttributeNames.add("emailAddress");
        columnsToAttributes.put("email", emailAttributeNames);
        columnsToAttributes.put("shirt_color", "dressShirtColor");
        columnsToAttributes.put("locations", "locations");
        impl.setResultAttributeMapping(columnsToAttributes);

        var attribs = impl.getPerson("awp9", IPersonAttributeDaoFilter.alwaysChoose()).getAttributes();
        assertEquals(Util.list("andrew.petro@yale.edu"), attribs.get("email"));
        assertEquals(Util.list("andrew.petro@yale.edu"), attribs.get("emailAddress"));
        assertEquals(Util.list("blue"), attribs.get("dressShirtColor"));
        assertNull(attribs.get("shirt_color"));
        assertEquals(Util.list("Andrew"), attribs.get("firstName"));
    }

    /**
     * Test for a query with a single attribute
     */
    public void testNullColumnName() {
        var impl = new SingleRowJdbcPersonAttributeDao(testDataSource, "SELECT name, email FROM user_table WHERE netid = {0}");
        impl.setQueryAttributeMapping(Collections.singletonMap("uid", null));

        impl.setUsernameAttributeProvider(new SimpleUsernameAttributeProvider("uid"));

        final Map<String, Object> columnsToAttributes = new HashMap<>();
        columnsToAttributes.put("name", "firstName");

        columnsToAttributes.put("email", "emailAddress");
        impl.setResultAttributeMapping(columnsToAttributes);

        var attribs = impl.getPerson("awp9", IPersonAttributeDaoFilter.alwaysChoose()).getAttributes();
        assertEquals(Util.list("andrew.petro@yale.edu"), attribs.get("emailAddress"));
        assertEquals(Util.list("Andrew"), attribs.get("firstName"));
    }

    /**
     * Test for a query with a single attribute
     */
    public void testSetNullAttributeMapping() {
        var impl = new SingleRowJdbcPersonAttributeDao(testDataSource, "SELECT name, email, shirt_color FROM user_table WHERE {0}");
        impl.setQueryAttributeMapping(Collections.singletonMap("uid", "netid"));

        impl.setUsernameAttributeProvider(new SimpleUsernameAttributeProvider("uid"));

        final Map<String, Object> columnsToAttributes = new HashMap<>();
        columnsToAttributes.put("name", "firstName");

        final Set<String> emailAttributeNames = new HashSet<>();
        emailAttributeNames.add("email");
        emailAttributeNames.add("emailAddress");
        columnsToAttributes.put("email", emailAttributeNames);
        columnsToAttributes.put("shirt_color", null);
        impl.setResultAttributeMapping(columnsToAttributes);

        var attribs = impl.getPerson("awp9", IPersonAttributeDaoFilter.alwaysChoose()).getAttributes();
        assertEquals(Util.list("andrew.petro@yale.edu"), attribs.get("email"));
        assertEquals(Util.list("andrew.petro@yale.edu"), attribs.get("emailAddress"));
        assertEquals(Util.list("blue"), attribs.get("shirt_color"));
        assertEquals(Util.list("Andrew"), attribs.get("firstName"));
    }

    /**
     * Test for a query with a single attribute
     */
    public void testSetNullAttributeName() {
        var impl = new SingleRowJdbcPersonAttributeDao(testDataSource, "SELECT name, email, shirt_color FROM user_table WHERE {0}");
        impl.setQueryAttributeMapping(Collections.singletonMap("uid", "netid"));

        final Map<String, Object> columnsToAttributes = new HashMap<>();
        columnsToAttributes.put("", "dressShirtColor");

        try {
            impl.setResultAttributeMapping(columnsToAttributes);
            fail("IllegalArgumentException if the ColumnsToAttributes Map has an empty Key");
        } catch (final IllegalArgumentException iae) {
            //expected
        }
    }

    /**
     * Test for a query with a null value attribute
     */
    public void testNullAttrQuery() {
        var impl = new SingleRowJdbcPersonAttributeDao(testDataSource, "SELECT name, email, shirt_color FROM user_table WHERE {0}");
        impl.setQueryAttributeMapping(Collections.singletonMap("uid", "netid"));

        impl.setUsernameAttributeProvider(new SimpleUsernameAttributeProvider("uid"));

        final Map<String, Object> columnsToAttributes = new HashMap<>();
        columnsToAttributes.put("name", "firstName");
        columnsToAttributes.put("shirt_color", "dressShirtColor");
        impl.setResultAttributeMapping(columnsToAttributes);

        var attribs = impl.getPerson("susan", IPersonAttributeDaoFilter.alwaysChoose()).getAttributes();
        assertNull(attribs.get("dressShirtColor"));
        assertEquals(Util.list("Susan"), attribs.get("firstName"));
    }

    /**
     * Test case for a query that needs multiple attributes to complete and
     * more attributes than are needed to complete are passed to it.
     */
    public void testMultiAttrQuery() {
        final Map<String, String> queryAttributeMapping = new LinkedHashMap<>();
        queryAttributeMapping.put("uid", "netid");
        queryAttributeMapping.put("shirtColor", "shirt_color");

        var impl = new SingleRowJdbcPersonAttributeDao(testDataSource, "SELECT name, email FROM user_table WHERE {0}");
        impl.setQueryAttributeMapping(queryAttributeMapping);


        final Map<String, Object> columnsToAttributes = new HashMap<>();
        columnsToAttributes.put("name", "firstName");

        final Set<String> emailAttributeNames = new HashSet<>();
        emailAttributeNames.add("email");
        emailAttributeNames.add("emailAddress");
        columnsToAttributes.put("email", emailAttributeNames);
        impl.setResultAttributeMapping(columnsToAttributes);

        final Map<String, List<Object>> queryMap = new HashMap<>();
        queryMap.put("uid", Util.list("awp9"));
        queryMap.put("shirtColor", Util.list("blue"));
        queryMap.put("Name", Util.list("John"));

        var attribsSet = impl.getPeopleWithMultivaluedAttributes(queryMap, IPersonAttributeDaoFilter.alwaysChoose());
        assertEquals(1, attribsSet.size());
        var result = attribsSet.iterator().next();
        assertNotNull(result.getAttributes());
        assertEquals(Util.list("andrew.petro@yale.edu"), result.getAttributes().get("email"));
        assertEquals(Util.list("andrew.petro@yale.edu"), result.getAttributes().get("emailAddress"));
        assertEquals(Util.list("Andrew"), result.getAttributes().get("firstName"));
    }

    /**
     * A query that needs mulitple attributes to complete but the needed
     * attributes aren't passed to it.
     */
    public void testInsufficientAttrQuery() {
        final Map<String, String> queryAttributeMapping = new LinkedHashMap<>();
        queryAttributeMapping.put("uid", "netid");
        queryAttributeMapping.put("shirtColor", "shirt_color");

        var impl = new SingleRowJdbcPersonAttributeDao(testDataSource, "SELECT name, email FROM user_table WHERE {0}");
        impl.setQueryAttributeMapping(queryAttributeMapping);
        impl.setRequireAllQueryAttributes(true);

        final Map<String, Object> columnsToAttributes = new HashMap<>();
        columnsToAttributes.put("name", "firstName");

        final Set<String> emailAttributeNames = new HashSet<>();
        emailAttributeNames.add("email");
        emailAttributeNames.add("emailAddress");
        columnsToAttributes.put("email", emailAttributeNames);
        impl.setResultAttributeMapping(columnsToAttributes);

        final Map<String, List<Object>> queryMap = new HashMap<>();
        queryMap.put("uid", Util.list("awp9"));
        queryMap.put("Name", Util.list("John"));

        var attribsSet = impl.getPeopleWithMultivaluedAttributes(queryMap, IPersonAttributeDaoFilter.alwaysChoose());
        assertNull(attribsSet);
    }

    /**
     * Test for a query with a single attribute
     */
    public void testMultiPersonQuery() {
        var impl = new SingleRowJdbcPersonAttributeDao(testDataSource, "SELECT netid, name, email FROM user_table WHERE {0}");
        impl.setQueryAttributeMapping(Collections.singletonMap("shirt", "shirt_color"));
        impl.setUsernameAttributeProvider(new SimpleUsernameAttributeProvider("uid"));

        final Map<String, Object> columnsToAttributes = new HashMap<>();
        columnsToAttributes.put("netid", "uid");
        columnsToAttributes.put("name", "firstName");

        final Set<String> emailAttributeNames = new HashSet<>();
        emailAttributeNames.add("email");
        emailAttributeNames.add("emailAddress");
        columnsToAttributes.put("email", emailAttributeNames);

        impl.setResultAttributeMapping(columnsToAttributes);

        final Map<String, List<Object>> queryMap = new HashMap<>();
        queryMap.put("shirt", Util.list("blue"));

        try {
            impl.getPeopleWithMultivaluedAttributes(queryMap, IPersonAttributeDaoFilter.alwaysChoose());
        } catch (final IncorrectResultSizeDataAccessException irsdae) {
            // good, exception thrown for multiple results
            return;
        }

        fail("JdbcPersonAttributeDao should have thrown IncorrectResultSizeDataAccessException for multiple results");
    }

    public void testProperties() {
        var impl = new SingleRowJdbcPersonAttributeDao(testDataSource, "SELECT netid, name, email FROM user_table WHERE {0}");

        impl.setQueryAttributeMapping(Collections.singletonMap("shirt", "shirt_color"));
        assertEquals(Collections.singletonMap("shirt", Collections.singleton("shirt_color")), impl.getQueryAttributeMapping());

        final Map<String, Object> columnsToAttributes = new HashMap<>();
        columnsToAttributes.put("netid", "uid");
        columnsToAttributes.put("name", "firstName");

        final Map<String, Set<String>> expectedColumnsToAttributes = new HashMap<>();
        expectedColumnsToAttributes.put("netid", Collections.singleton("uid"));
        expectedColumnsToAttributes.put("name", Collections.singleton("firstName"));

        impl.setResultAttributeMapping(columnsToAttributes);
        assertEquals(expectedColumnsToAttributes, impl.getResultAttributeMapping());
    }

    @Override
    protected AbstractDefaultAttributePersonAttributeDao getAbstractDefaultQueryPersonAttributeDao() {
        var queryAttr = "shirt";
        final List<String> queryAttrList = new LinkedList<>();
        queryAttrList.add(queryAttr);

        // shirt_color = ?

        var impl = new SingleRowJdbcPersonAttributeDao(this.testDataSource, "SELECT netid, name, email FROM user_table WHERE {0}");
        impl.setQueryAttributeMapping(Collections.singletonMap("shirt", "shirt_color"));

        return impl;
    }

}
