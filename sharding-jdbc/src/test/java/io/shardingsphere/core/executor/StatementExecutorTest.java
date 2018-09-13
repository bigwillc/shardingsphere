/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.core.executor;

import io.shardingsphere.core.constant.ConnectionMode;
import io.shardingsphere.core.event.ShardingEventType;
import io.shardingsphere.core.executor.sql.execute.threadlocal.ExecutorExceptionHandler;
import io.shardingsphere.core.jdbc.core.connection.ShardingConnection;
import io.shardingsphere.core.merger.QueryResult;
import io.shardingsphere.core.rewrite.SQLBuilder;
import io.shardingsphere.core.routing.RouteUnit;
import org.junit.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class StatementExecutorTest extends AbstractBaseExecutorTest {

    private static final String DQL_SQL = "SELECT * FROM table_x";
    
    private static final String DML_SQL = "DELETE FROM table_x";
    
    private static final ShardingConnection CONNECTION = Mockito.mock(ShardingConnection.class);
    
    @Override
    public void setUp() {
        when(CONNECTION.getShardingDataSource().getShardingContext().getExecuteEngine()).thenReturn(mock(ShardingExecuteEngine.class));
    }
    
    @Test
    public void assertNoStatement() throws SQLException {
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertFalse(actual.execute());
        assertThat(actual.executeUpdate(), is(0));
        assertThat(actual.executeQuery().size(), is(0));
    }
    
    @Test
    public void assertExecuteQueryForSingleStatementSuccess() throws SQLException {
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        QueryResult queryResult = mock(QueryResult.class);
        when(statement.executeQuery(DQL_SQL)).thenReturn(resultSet);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertThat(actual.executeQuery(), is(Collections.singletonList(queryResult)));
        verify(statement).executeQuery(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteQueryForMultipleStatementsSuccess() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        ResultSet resultSet1 = mock(ResultSet.class);
        ResultSet resultSet2 = mock(ResultSet.class);
        QueryResult queryResult1 = mock(QueryResult.class);
        QueryResult queryResult2 = mock(QueryResult.class);
        when(statement1.executeQuery(DQL_SQL)).thenReturn(resultSet1);
        when(statement1.getConnection()).thenReturn(mock(Connection.class));
        when(statement2.executeQuery(DQL_SQL)).thenReturn(resultSet2);
        when(statement2.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        List<QueryResult> actualResultSets = actual.executeQuery();
        assertThat(actualResultSets, hasItem(queryResult1));
        assertThat(actualResultSets, hasItem(queryResult2));
        verify(statement1).executeQuery(DQL_SQL);
        verify(statement2).executeQuery(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DQL_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteQueryForSingleStatementFailure() throws SQLException {
        Statement statement = mock(Statement.class);
        SQLException exp = new SQLException();
        when(statement.executeQuery(DQL_SQL)).thenThrow(exp);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertThat(actual.executeQuery(), is(Collections.singletonList((QueryResult) null)));
        verify(statement).executeQuery(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
        verify(getEventCaller()).verifyException(exp);
    }
    
    @Test
    public void assertExecuteQueryForMultipleStatementsFailure() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        SQLException exp = new SQLException();
        when(statement1.executeQuery(DQL_SQL)).thenThrow(exp);
        when(statement2.executeQuery(DQL_SQL)).thenThrow(exp);
        when(statement1.getConnection()).thenReturn(mock(Connection.class));
        when(statement2.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        List<QueryResult> actualResultSets = actual.executeQuery();
        assertThat(actualResultSets, is(Arrays.asList((QueryResult) null, null)));
        verify(statement1).executeQuery(DQL_SQL);
        verify(statement2).executeQuery(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DQL_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
        verify(getEventCaller(), times(2)).verifyException(exp);
    }
    
    @Test
    public void assertExecuteUpdateForSingleStatementSuccess() throws SQLException {
        Statement statement = mock(Statement.class);
        when(statement.executeUpdate(DML_SQL)).thenReturn(10);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertThat(actual.executeUpdate(), is(10));
        verify(statement).executeUpdate(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteUpdateForMultipleStatementsSuccess() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        when(statement1.executeUpdate(DML_SQL)).thenReturn(10);
        when(statement2.executeUpdate(DML_SQL)).thenReturn(20);
        when(statement1.getConnection()).thenReturn(mock(Connection.class));
        when(statement2.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertThat(actual.executeUpdate(), is(30));
        verify(statement1).executeUpdate(DML_SQL);
        verify(statement2).executeUpdate(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteUpdateForSingleStatementFailure() throws SQLException {
        Statement statement = mock(Statement.class);
        SQLException exp = new SQLException();
        when(statement.executeUpdate(DML_SQL)).thenThrow(exp);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertThat(actual.executeUpdate(), is(0));
        verify(statement).executeUpdate(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
        verify(getEventCaller()).verifyException(exp);
    }
    
    @Test
    public void assertExecuteUpdateForMultipleStatementsFailure() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        SQLException exp = new SQLException();
        when(statement1.executeUpdate(DML_SQL)).thenThrow(exp);
        when(statement2.executeUpdate(DML_SQL)).thenThrow(exp);
        when(statement1.getConnection()).thenReturn(mock(Connection.class));
        when(statement2.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertThat(actual.executeUpdate(), is(0));
        verify(statement1).executeUpdate(DML_SQL);
        verify(statement2).executeUpdate(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
        verify(getEventCaller(), times(2)).verifyException(exp);
    }
    
    @Test
    public void assertExecuteUpdateWithAutoGeneratedKeys() throws SQLException {
        Statement statement = mock(Statement.class);
        when(statement.executeUpdate(DML_SQL, Statement.NO_GENERATED_KEYS)).thenReturn(10);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertThat(actual.executeUpdate(Statement.NO_GENERATED_KEYS), is(10));
        verify(statement).executeUpdate(DML_SQL, Statement.NO_GENERATED_KEYS);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteUpdateWithColumnIndexes() throws SQLException {
        Statement statement = mock(Statement.class);
        when(statement.executeUpdate(DML_SQL, new int[] {1})).thenReturn(10);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertThat(actual.executeUpdate(new int[] {1}), is(10));
        verify(statement).executeUpdate(DML_SQL, new int[] {1});
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteUpdateWithColumnNames() throws SQLException {
        Statement statement = mock(Statement.class);
        when(statement.executeUpdate(DML_SQL, new String[] {"col"})).thenReturn(10);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertThat(actual.executeUpdate(new String[] {"col"}), is(10));
        verify(statement).executeUpdate(DML_SQL, new String[] {"col"});
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteForSingleStatementSuccessWithDML() throws SQLException {
        Statement statement = mock(Statement.class);
        when(statement.execute(DML_SQL)).thenReturn(false);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertFalse(actual.execute());
        verify(statement).execute(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteForMultipleStatementsSuccessWithDML() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        when(statement1.execute(DML_SQL)).thenReturn(false);
        when(statement2.execute(DML_SQL)).thenReturn(false);
        when(statement1.getConnection()).thenReturn(mock(Connection.class));
        when(statement2.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertFalse(actual.execute());
        verify(statement1).execute(DML_SQL);
        verify(statement2).execute(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteForSingleStatementFailureWithDML() throws SQLException {
        Statement statement = mock(Statement.class);
        SQLException exp = new SQLException();
        when(statement.execute(DML_SQL)).thenThrow(exp);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertFalse(actual.execute());
        verify(statement).execute(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
        verify(getEventCaller()).verifyException(exp);
    }
    
    @Test
    public void assertExecuteForMultipleStatementsFailureWithDML() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        SQLException exp = new SQLException();
        when(statement1.execute(DML_SQL)).thenThrow(exp);
        when(statement2.execute(DML_SQL)).thenThrow(exp);
        when(statement1.getConnection()).thenReturn(mock(Connection.class));
        when(statement2.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertFalse(actual.execute());
        verify(statement1).execute(DML_SQL);
        verify(statement2).execute(DML_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
        verify(getEventCaller(), times(2)).verifyException(exp);
    }
    
    @Test
    public void assertExecuteForSingleStatementWithDQL() throws SQLException {
        Statement statement = mock(Statement.class);
        when(statement.execute(DQL_SQL)).thenReturn(true);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertTrue(actual.execute());
        verify(statement).execute(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteForMultipleStatements() throws SQLException {
        Statement statement1 = mock(Statement.class);
        Statement statement2 = mock(Statement.class);
        when(statement1.execute(DQL_SQL)).thenReturn(true);
        when(statement2.execute(DQL_SQL)).thenReturn(true);
        when(statement1.getConnection()).thenReturn(mock(Connection.class));
        when(statement2.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertTrue(actual.execute());
        verify(statement1).execute(DQL_SQL);
        verify(statement2).execute(DQL_SQL);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifyDataSource("ds_1");
        verify(getEventCaller(), times(4)).verifySQL(DQL_SQL);
        verify(getEventCaller(), times(4)).verifyParameters(Collections.emptyList());
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller(), times(2)).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteWithAutoGeneratedKeys() throws SQLException {
        Statement statement = mock(Statement.class);
        when(statement.execute(DML_SQL, Statement.NO_GENERATED_KEYS)).thenReturn(false);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertFalse(actual.execute(Statement.NO_GENERATED_KEYS));
        verify(statement).execute(DML_SQL, Statement.NO_GENERATED_KEYS);
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteWithColumnIndexes() throws SQLException {
        Statement statement = mock(Statement.class);
        when(statement.execute(DML_SQL, new int[] {1})).thenReturn(false);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertFalse(actual.execute(new int[] {1}));
        verify(statement).execute(DML_SQL, new int[] {1});
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertExecuteWithColumnNames() throws SQLException {
        Statement statement = mock(Statement.class);
        when(statement.execute(DML_SQL, new String[] {"col"})).thenReturn(false);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        assertFalse(actual.execute(new String[] {"col"}));
        verify(statement).execute(DML_SQL, new String[] {"col"});
        verify(getEventCaller(), times(2)).verifyDataSource("ds_0");
        verify(getEventCaller(), times(2)).verifySQL(DML_SQL);
        verify(getEventCaller(), times(2)).verifyParameters(Collections.emptyList());
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_SUCCESS);
        verify(getEventCaller(), times(0)).verifyException(null);
    }
    
    @Test
    public void assertOverallExceptionFailure() throws SQLException {
        ExecutorExceptionHandler.setExceptionThrown(true);
        Statement statement = mock(Statement.class);
        SQLException exp = new SQLException();
        when(statement.execute(DML_SQL)).thenThrow(exp);
        when(statement.getConnection()).thenReturn(mock(Connection.class));
        StatementExecutor actual = new StatementExecutor(1, 1, 1, CONNECTION);
        try {
            assertFalse(actual.execute());
        } catch (final SQLException ignore) {
        }
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.BEFORE_EXECUTE);
        verify(getEventCaller()).verifyEventExecutionType(ShardingEventType.EXECUTE_FAILURE);
    }
    
    private Collection<StatementExecuteUnit> createStatementExecuteUnits(final String sql, final Statement statement, final String dataSource) throws SQLException {
        Collection<StatementExecuteUnit> result = new LinkedList<>();
        SQLBuilder sqlBuilder = new SQLBuilder();
        sqlBuilder.appendLiterals(sql);
        result.add(new StatementExecuteUnit(new RouteUnit(dataSource, sqlBuilder.toSQL(null, Collections.<String, String>emptyMap(), null, null)), statement, ConnectionMode.MEMORY_STRICTLY));
        return result;
    }
}
