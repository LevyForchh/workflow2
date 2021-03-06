/**
 * Autogenerated by Jack
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 */
package com.liveramp.databases.workflow_db.query;

import java.util.Collection;

import com.rapleaf.jack.queries.AbstractDeleteBuilder;
import com.rapleaf.jack.queries.where_operators.IWhereOperator;
import com.rapleaf.jack.queries.where_operators.JackMatchers;
import com.rapleaf.jack.queries.WhereConstraint;
import com.liveramp.databases.workflow_db.iface.IDashboardApplicationPersistence;
import com.liveramp.databases.workflow_db.models.DashboardApplication;


public class DashboardApplicationDeleteBuilder extends AbstractDeleteBuilder<DashboardApplication> {

  public DashboardApplicationDeleteBuilder(IDashboardApplicationPersistence caller) {
    super(caller);
  }

  public DashboardApplicationDeleteBuilder id(Long value) {
    addId(value);
    return this;
  }

  public DashboardApplicationDeleteBuilder idIn(Collection<Long> values) {
    addIds(values);
    return this;
  }

  public DashboardApplicationDeleteBuilder dashboardId(Integer value) {
    addWhereConstraint(new WhereConstraint<Integer>(DashboardApplication._Fields.dashboard_id, JackMatchers.equalTo(value)));
    return this;
  }

  public DashboardApplicationDeleteBuilder whereDashboardId(IWhereOperator<Integer> operator) {
    addWhereConstraint(new WhereConstraint<Integer>(DashboardApplication._Fields.dashboard_id, operator));
    return this;
  }

  public DashboardApplicationDeleteBuilder applicationId(Integer value) {
    addWhereConstraint(new WhereConstraint<Integer>(DashboardApplication._Fields.application_id, JackMatchers.equalTo(value)));
    return this;
  }

  public DashboardApplicationDeleteBuilder whereApplicationId(IWhereOperator<Integer> operator) {
    addWhereConstraint(new WhereConstraint<Integer>(DashboardApplication._Fields.application_id, operator));
    return this;
  }
}
