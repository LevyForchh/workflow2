
/**
 * Autogenerated by Jack
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 */
package com.liveramp.databases.workflow_db.impl;

import java.sql.SQLRecoverableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.sql.Timestamp;

import com.rapleaf.jack.AbstractDatabaseModel;
import com.rapleaf.jack.BaseDatabaseConnection;
import com.rapleaf.jack.queries.WhereConstraint;
import com.rapleaf.jack.queries.WhereClause;
import com.rapleaf.jack.util.JackUtility;
import com.liveramp.databases.workflow_db.iface.IStepDependencyPersistence;
import com.liveramp.databases.workflow_db.models.StepDependency;
import com.liveramp.databases.workflow_db.query.StepDependencyQueryBuilder;
import com.liveramp.databases.workflow_db.query.StepDependencyDeleteBuilder;

import com.liveramp.databases.workflow_db.IDatabases;

public class BaseStepDependencyPersistenceImpl extends AbstractDatabaseModel<StepDependency> implements IStepDependencyPersistence {
  private final IDatabases databases;

  public BaseStepDependencyPersistenceImpl(BaseDatabaseConnection conn, IDatabases databases) {
    super(conn, "step_dependencies", Arrays.<String>asList("step_attempt_id", "dependency_attempt_id"));
    this.databases = databases;
  }

  @Override
  public StepDependency create(Map<Enum, Object> fieldsMap) throws IOException {
    long step_attempt_id = (Long) fieldsMap.get(StepDependency._Fields.step_attempt_id);
    long dependency_attempt_id = (Long) fieldsMap.get(StepDependency._Fields.dependency_attempt_id);
    return create(step_attempt_id, dependency_attempt_id);
  }

  public StepDependency create(final long step_attempt_id, final long dependency_attempt_id) throws IOException {
    StatementCreator statementCreator = new StatementCreator() {
      private final List<String> nonNullFields = new ArrayList<>();
      private final List<AttrSetter> statementSetters = new ArrayList<>();

      {
        int index = 1;

        nonNullFields.add("step_attempt_id");
        int fieldIndex0 = index++;
        statementSetters.add(stmt -> stmt.setLong(fieldIndex0, step_attempt_id));

        nonNullFields.add("dependency_attempt_id");
        int fieldIndex1 = index++;
        statementSetters.add(stmt -> stmt.setLong(fieldIndex1, dependency_attempt_id));
      }

      @Override
      public String getStatement() {
        return getInsertStatement(nonNullFields);
      }

      @Override
      public void setStatement(PreparedStatement statement) throws SQLException {
        for (AttrSetter setter : statementSetters) {
          setter.set(statement);
        }
      }
    };

    long __id = realCreate(statementCreator);
    StepDependency newInst = new StepDependency(__id, step_attempt_id, dependency_attempt_id, databases);
    newInst.setCreated(true);
    cachedById.put(__id, newInst);
    clearForeignKeyCache();
    return newInst;
  }

  public StepDependency createDefaultInstance() throws IOException {
    return create(0L, 0L);
  }

  public List<StepDependency> find(Map<Enum, Object> fieldsMap) throws IOException {
    return find(null, fieldsMap);
  }

  public List<StepDependency> find(Collection<Long> ids, Map<Enum, Object> fieldsMap) throws IOException {
    List<StepDependency> foundList = new ArrayList<>();

    if (fieldsMap == null || fieldsMap.isEmpty()) {
      return foundList;
    }

    StringBuilder statementString = new StringBuilder();
    statementString.append("SELECT * FROM step_dependencies WHERE (");
    List<Object> nonNullValues = new ArrayList<>();
    List<StepDependency._Fields> nonNullValueFields = new ArrayList<>();

    Iterator<Map.Entry<Enum, Object>> iter = fieldsMap.entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<Enum, Object> entry = iter.next();
      Enum field = entry.getKey();
      Object value = entry.getValue();

      String queryValue = value != null ? " = ? " : " IS NULL";
      if (value != null) {
        nonNullValueFields.add((StepDependency._Fields) field);
        nonNullValues.add(value);
      }

      statementString.append(field).append(queryValue);
      if (iter.hasNext()) {
        statementString.append(" AND ");
      }
    }
    if (ids != null) statementString.append(" AND ").append(getIdSetCondition(ids));
    statementString.append(")");

    int retryCount = 0;
    PreparedStatement preparedStatement;

    while (true) {
      preparedStatement = getPreparedStatement(statementString.toString());

      for (int i = 0; i < nonNullValues.size(); i++) {
        StepDependency._Fields field = nonNullValueFields.get(i);
        try {
          switch (field) {
            case step_attempt_id:
              preparedStatement.setLong(i+1, (Long) nonNullValues.get(i));
              break;
            case dependency_attempt_id:
              preparedStatement.setLong(i+1, (Long) nonNullValues.get(i));
              break;
          }
        } catch (SQLException e) {
          throw new IOException(e);
        }
      }

      try {
        executeQuery(foundList, preparedStatement);
        return foundList;
      } catch (SQLRecoverableException e) {
        if (++retryCount > AbstractDatabaseModel.MAX_CONNECTION_RETRIES) {
          throw new IOException(e);
        }
      } catch (SQLException e) {
        throw new IOException(e);
      }
    }
  }

  @Override
  protected void setStatementParameters(PreparedStatement preparedStatement, WhereClause whereClause) throws IOException {
    int index = 0;
    for (WhereConstraint constraint : whereClause.getWhereConstraints()) {
      for (Object parameter : constraint.getParameters()) {
        if (parameter == null) {
          continue;
        }
        try {
          if (constraint.isId()) {
            preparedStatement.setLong(++index, (Long)parameter);
          } else {
            StepDependency._Fields field = (StepDependency._Fields)constraint.getField();
            switch (field) {
              case step_attempt_id:
                preparedStatement.setLong(++index, (Long) parameter);
                break;
              case dependency_attempt_id:
                preparedStatement.setLong(++index, (Long) parameter);
                break;
            }
          }
        } catch (SQLException e) {
          throw new IOException(e);
        }
      }
    }
  }

  @Override
  protected void setAttrs(StepDependency model, PreparedStatement stmt, boolean setNull) throws SQLException {
    int index = 1;
    {
      stmt.setLong(index++, model.getStepAttemptId());
    }
    {
      stmt.setLong(index++, model.getDependencyAttemptId());
    }
    stmt.setLong(index, model.getId());
  }

  @Override
  protected StepDependency instanceFromResultSet(ResultSet rs, Collection<Enum> selectedFields) throws SQLException {
    boolean allFields = selectedFields == null || selectedFields.isEmpty();
    long id = rs.getLong("id");
    return new StepDependency(id,
      allFields || selectedFields.contains(StepDependency._Fields.step_attempt_id) ? getLongOrNull(rs, "step_attempt_id") : 0L,
      allFields || selectedFields.contains(StepDependency._Fields.dependency_attempt_id) ? getLongOrNull(rs, "dependency_attempt_id") : 0L,
      databases
    );
  }

  public List<StepDependency> findByStepAttemptId(final long value) throws IOException {
    return find(Collections.<Enum, Object>singletonMap(StepDependency._Fields.step_attempt_id, value));
  }

  public List<StepDependency> findByDependencyAttemptId(final long value) throws IOException {
    return find(Collections.<Enum, Object>singletonMap(StepDependency._Fields.dependency_attempt_id, value));
  }

  public StepDependencyQueryBuilder query() {
    return new StepDependencyQueryBuilder(this);
  }

  public StepDependencyDeleteBuilder delete() {
    return new StepDependencyDeleteBuilder(this);
  }
}
