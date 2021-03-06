/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.callactivity;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCallActivity;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementActivatingHandler;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.ErrorType;

public class CallActivityActivatingHandler
    extends ActivityElementActivatingHandler<ExecutableCallActivity> {

  private final KeyGenerator keyGenerator;

  private final WorkflowInstanceRecord childInstanceRecord = new WorkflowInstanceRecord();

  public CallActivityActivatingHandler(
      CatchEventSubscriber catchEventSubscriber, KeyGenerator keyGenerator) {
    super(null, catchEventSubscriber);
    this.keyGenerator = keyGenerator;
  }

  @Override
  protected boolean handleState(BpmnStepContext<ExecutableCallActivity> context) {
    super.handleState(context);

    final DeployedWorkflow workflow = getCalledWorkflow(context);
    if (workflow == null) {
      return false;
    }

    transitionTo(context, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final var childWorkflowInstanceKey = createInstance(workflow, context);

    final var callActivityInstanceKey = context.getKey();
    copyVariables(callActivityInstanceKey, childWorkflowInstanceKey, workflow, context);

    return true;
  }

  private DeployedWorkflow getCalledWorkflow(
      final BpmnStepContext<ExecutableCallActivity> context) {

    final var processId = context.getElement().getCalledElementProcessId();
    final var workflow = context.getStateDb().getLatestWorkflowVersionByProcessId(processId);

    if (workflow == null) {
      context.raiseIncident(
          ErrorType.CALLED_ELEMENT_ERROR,
          String.format(
              "Expected workflow with BPMN process id '%s' to be deployed, but not found.",
              bufferAsString(processId)));
      return null;
    }

    final var noneStartEvent = workflow.getWorkflow().getNoneStartEvent();

    if (noneStartEvent == null) {
      context.raiseIncident(
          ErrorType.CALLED_ELEMENT_ERROR,
          String.format(
              "Expected workflow with BPMN process id '%s' to have a none start event, but not found.",
              bufferAsString(processId)));
      return null;
    }

    return workflow;
  }

  private long createInstance(
      final DeployedWorkflow workflow, final BpmnStepContext<ExecutableCallActivity> context) {

    final var parentWorkflowInstanceKey = context.getValue().getWorkflowInstanceKey();
    final var parentElementInstanceKey = context.getKey();

    final var workflowInstanceKey = keyGenerator.nextKey();

    childInstanceRecord.reset();
    childInstanceRecord
        .setBpmnProcessId(workflow.getBpmnProcessId())
        .setVersion(workflow.getVersion())
        .setWorkflowKey(workflow.getKey())
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setParentWorkflowInstanceKey(parentWorkflowInstanceKey)
        .setParentElementInstanceKey(parentElementInstanceKey);

    context
        .getOutput()
        .appendFollowUpEvent(
            workflowInstanceKey,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            childInstanceRecord,
            workflow.getWorkflow());

    return workflowInstanceKey;
  }

  private void copyVariables(
      final long source,
      final long target,
      final DeployedWorkflow targetWorkflow,
      final BpmnStepContext<ExecutableCallActivity> context) {

    final var state = context.getElementInstanceState().getVariablesState();

    final var variables = state.getVariablesAsDocument(source);
    state.setVariablesFromDocument(target, targetWorkflow.getKey(), variables);
  }
}
