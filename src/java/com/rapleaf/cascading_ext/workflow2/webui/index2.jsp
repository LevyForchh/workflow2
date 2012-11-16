<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8"%>
<!DOCTYPE html>

<%@page import="com.rapleaf.cascading_ext.workflow2.*"%>
<%@page import="com.rapleaf.cascading_ext.workflow2.WorkflowDiagram.Vertex"%>
<%@page import="org.jgrapht.graph.*"%>
<%@page import="java.util.*"%>
<%@page import="com.rapleaf.support.DAGLayoutGenerator"%>
<%@page import="com.rapleaf.support.DAGLayoutGenerator.DAGLayout"%>
<%@page import="com.rapleaf.support.TimeHelper"%>
<%@page import="org.jgrapht.traverse.TopologicalOrderIterator"%>
<%@page import="org.jgrapht.DirectedGraph"%>
<%@page import="java.text.DateFormat"%>
<%@page import="java.text.SimpleDateFormat"%>
<%@page import="com.rapleaf.support.Rap"%>
<html>
<script type="text/javascript">
  function renderProgressBar(pctComplete) {
    if (pctComplete < 0 ) {
      return "<br />";
    }
    return "(" + pctComplete + "%)" +
            "<table class=\"progress_bar\">"
            + "<tr>"
            + "<td style=\"background-color: #8888ff; width: " + pctComplete + "%\"></td>"
            + "<td style=\"background-color: #aaffaa; width: " + (100 - pctComplete) + "%\"></td>"
            + "</tr>"
            +  "</table>";
  }

  function shortHumanReadableElapsedTime(start, end) {
    function format(s) {
      return s.length == 1 ? "0" + s : s;
    }
    var elapsed = end - start;
    var sec = Math.round((elapsed / 1000) % 60);
    var min = Math.round((elapsed / 1000 / 60) % 60);
    var hrs = Math.round((elapsed / 1000 / 60 / 60));

    return format(hrs.toString()) + ":" + format(min.toString()) + ":" + format(sec.toString());
  }
</script>

<%
  WorkflowRunner wfr = (WorkflowRunner)getServletContext().getAttribute("workflowRunner");
  WorkflowDiagram wfd;
  if (session.isNew()) {
    wfd = new WorkflowDiagram(wfr);
    session.setAttribute("workflowDiagram", wfd);
  } else {
    wfd = (WorkflowDiagram)session.getAttribute("workflowDiagram");
  }
%>

<head>
  <meta charset="utf-8" />
  <title>Workflow <%= wfr.getWorkflowName() %></title>
  <link href="css/bootstrap.min.css" rel="stylesheet" media="screen">

  <style type="text/css">
    body {
      font-size: 10pt
    }

    div.shutdown-notice {
      font-weight: bold;
      color: red;
    }

    .shutdown-notice-message {
      font-weight: bold;
      color: black;
    }

    td.status-column {
      text-align: center;
    }

    div.node-inner-div {
      display: table-cell;
      text-align: center;
      vertical-align: middle;
    }

    div.all-nodes {
      border: 1px solid black;
      font-size: 9pt;
      position: absolute;
    }

    div.<%= StepStatus.WAITING.name().toLowerCase() %> {
      background-color: #dddddd;
    }

    div.<%= StepStatus.RUNNING.name().toLowerCase() %> {
      background-color: #ddffdd;
    }

    div.<%= StepStatus.SKIPPED.name().toLowerCase() %> {
      background-color: #ffffdd;
    }

    div.<%= StepStatus.COMPLETED.name().toLowerCase() %> {
      background-color: #ddddff;
    }

    div.<%= StepStatus.FAILED.name().toLowerCase() %> {
      background-color: #ffdddd;
    }

    div.datastore {
      border-style: dashed;
      /*background-color: #ffcc88;*/
    }

    div.legend-entry {
      width: 50px;
      height: 30px;
      font-size: 9pt;
      text-align: center;
      vertical-align: middle;
      display: table-cell;
      padding: 3px;
    }

    label.collapse-node {
      position: absolute;
      top: 0;
      left: 1px;
      font-weight: bold;
      cursor: pointer;
    }
    label.expand-node {
      position: absolute;
      top: 0;
      right: 0;
      font-weight: bold;
      cursor: pointer;
    }

    table.progress_bar {
      border: 1px solid #333333;
      height: 12px;
      border-collapse: collapse;
      margin: auto;
      width: 100px;
    }
    table.progress_bar td {
      padding: 0;
      margin: 0;
    }

    #detail {
      border: 1px solid #333333;
      border-collapse: collapse;
    }
    #detail td {
      border: 1px solid #888888;
      border-left-width: 0;
    }
    #detail td.ec {
      border-right-width: 0;
      font-weight: bold;
    }
    #detail td.ec label {
      cursor: pointer;
    }

  </style>

  <script src="js/raphael-min.js" type="text/javascript" charset="utf-8"></script>
  <script src="js/diagrams2.js" type="text/javascript" charset="utf-8"></script>
  <script src="js/graph.js" type="text/javascript" charset="utf-8"></script>
  <script src="js/workflow_diagram.js" type="text/javascript" charset="utf-8"></script>
  <script src="js/dag_layout.js" type="text/javascript" charset="utf-8"></script>
  <script src="js/jquery.min.js"></script>
  <script type="text/javascript">

  function updateView() {
    renderDiagram("canvas", wfd);
    updateTable();
  }

  <%= wfd.getJSWorkflowDefinition(true) %>
  var wfd = new Wfd(workflowSteps, workflowDatastores);
  var graph = wfd.getDiagramGraph();
  var diagramNodes = getDiagramNodes(graph);

  window.onload = function () {
    $("#datastores").click(function() {
      if (wfd.includeDatastores == true) {
        wfd.includeDatastores = false;
        $("#datastores").html("Show Datastores");
      } else {
        wfd.includeDatastores = true;
        $("#datastores").html("Hide Datastores");
      }
      updateView();
    });

    $("#expand-all").click(function() {
      wfd.expandAll();
      updateView();
    });

    $("#collapse-all").click(function() {
      wfd.collapseAll();
      updateView();
    });
    updateView();
  };
  </script>
</head>
<body>

<h2><%= wfr.getWorkflowName() %> </h2>

<form method="GET" name="diagram_options" id="diagram_options">
  <%
    List<String> isolated = wfd.getIsolated();
    if (!isolated.isEmpty()){
      String previous = isolated.remove(0);
      for (String current : isolated) { %>
  <input type="submit" value="<%= previous %>" style="display:none;"
         name="remove_isolation" id ="remove_isolation_<%=current%>" onclick="this.value=<%= current %>" />
  <label for="remove_isolation_<%=current%>" style="color:blue;" ><%= previous %></label>
  <span style="padding: 0 8px 0 8px;">&gt;</span>
  <%
      previous = current;
    }
  %>
  <%= previous %>
  <%
    }
  %>
  <br/>
  <div id="canvas" style="border:1px solid black; position:relative; overflow:auto; width:100%"></div>

  <div id="legend" style="float:right">
    <%
      for (StepStatus status : StepStatus.values()) {
        String pretty_name = status.name().toLowerCase();
    %>
    <div class="legend-entry <%= status.name().toLowerCase() %>"><%= pretty_name %></div>
    <%
      }
    %>
    <div class="legend-entry datastore">datastore</div>
  </div>

  <a id="expand-all" class="btn btn-info btn-small">Expand All</a>
  <a id="collapse-all" class="btn btn-info btn-small">Collapse All</a>
  <a id="datastores" class="btn btn-info btn-small">Show Datastores</a>
</form>
<h4>Shutdown Controls</h4>

<%
  if (wfr.isShutdownPending()) {
%>
<div class='shutdown-notice'>
  <p>
    Workflow shutdown has been requested with the following message:
  <p class="shutdown-notice-message">
    "<%= wfr.getReasonForShutdownRequest() %>"
  </p>
  </p>
  <p>
    Currently running components will be allowed to complete before exiting.
  </p>
</div>
<%} else {%>
<form action="/request_shutdown.jsp" method=post>
  <p>
    <label for="shutdown-reason">Reason for shutdown:</label>
  </p>
  <p>
    <textarea id="shutdown-reason" name="reason" rows="10" cols="70" style="width:50%"></textarea>
  </p>
  <input type="submit" value="Request Workflow Shutdown"/>
</form>
<%
  }
%>

<h4>Workflow Detail</h4>
<table id="detail"></table>
  <script type="text/javascript">
    function updateTable() {
      var tableHtml = "<tr><th>&nbsp;</th><th>&nbsp;</th><th>Token</th><th>Name</th><th>Status</th><th>Job Tracker</th><th>Messages</th></tr>"
      for (i in wfd.workflowDef) {
        var step = wfd.workflowDef[i];
        if (!wfd.shouldBeIncluded(step.id)) continue;
        var collapseLabel = wfd.isCollapsable(step.id) ? "<label onclick=\"$('#collapse_" + wfd.getLongNameForStep(step).replace(/\s/g, '-') + "_label').click()\">&ndash;</label>" : "";
        var expandLabel = wfd.isExpandable(step.id) ? "<label onclick=\"$('#expand_" + wfd.getLongNameForStep(step).replace(/\s/g, '-') + "_label').click()\">+</label>" : "";

        tableHtml += "<tr>";
        tableHtml += "<td class='ec'>" + collapseLabel + "</td>";
        tableHtml += "<td class='ec'>" + expandLabel + "</td>";
        tableHtml += "<td>" + step.name + "</td>";
        tableHtml += "<td>" + step.java_class.split('.').pop() + "</td>";
        tableHtml += "<td>" + step.status;
        if (step.status == "running") {
          tableHtml += renderProgressBar(step.pctComplete);
          tableHtml += "Started at " + new Date(step.startTimestamp);
        } else if (step.status == "completed") {
          tableHtml += renderProgressBar(100);
          tableHtml += "Started at " + new Date(step.startTimestamp).toString();
          tableHtml += "<br>Ended at " + new Date(step.endTimestamp).toString();
          tableHtml += "<br>Took " + shortHumanReadableElapsedTime(step.startTimestamp, step.endTimestamp);
        }

        tableHtml += "</td>";
        tableHtml += "<td></td>";
        tableHtml += "<td></td>";
        tableHtml += "</tr>";

      }
      $("#detail").html(tableHtml);
    }
  </script>

</body>
</html>
