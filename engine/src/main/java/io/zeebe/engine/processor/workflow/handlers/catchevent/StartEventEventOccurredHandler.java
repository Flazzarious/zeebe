/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.catchevent;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class StartEventEventOccurredHandler<T extends ExecutableCatchEventElement>
    extends EventOccurredHandler<T> {
  private static final String NO_WORKFLOW_FOUND_MESSAGE =
      "Expected to create an instance of workflow with key '{}', but no such workflow was found";
  private static final String NO_TRIGGERED_EVENT_MESSAGE = "No triggered event for workflow '{}'";

  private final WorkflowInstanceRecord record = new WorkflowInstanceRecord();
  private final WorkflowState state;
  private final MessageState messageState;
  private final KeyGenerator keyGenerator;

  public StartEventEventOccurredHandler(final ZeebeState zeebeState) {
    this(null, zeebeState);
  }

  public StartEventEventOccurredHandler(
      final WorkflowInstanceIntent nextState, final ZeebeState zeebeState) {
    super(nextState);
    this.state = zeebeState.getWorkflowState();
    this.messageState = zeebeState.getMessageState();
    keyGenerator = zeebeState.getKeyGenerator();
  }

  @Override
  protected boolean handleState(final BpmnStepContext<T> context) {
    final WorkflowInstanceRecord event = context.getValue();
    final long workflowKey = event.getWorkflowKey();
    final DeployedWorkflow workflow = state.getWorkflowByKey(workflowKey);
    final long workflowInstanceKey = keyGenerator.nextKey();

    // this should never happen because workflows are never deleted.
    if (workflow == null) {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.error(NO_WORKFLOW_FOUND_MESSAGE, workflowKey);
      return false;
    }

    final EventTrigger triggeredEvent = getTriggeredEvent(context, workflowKey);
    if (triggeredEvent == null) {
      Loggers.WORKFLOW_PROCESSOR_LOGGER.error(NO_TRIGGERED_EVENT_MESSAGE, workflowKey);
      return false;
    }

    activateContainer(context, workflow, workflowInstanceKey);
    final WorkflowInstanceRecord record =
        getEventRecord(context, triggeredEvent, BpmnElementType.START_EVENT)
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setVersion(workflow.getVersion())
            .setBpmnProcessId(workflow.getBpmnProcessId())
            .setFlowScopeKey(workflowInstanceKey);

    deferEvent(context, workflowKey, workflowInstanceKey, record, triggeredEvent);

    // should mark particular message as already correlated for this instance
    if (context.getElement().isMessage()) {
      final long messageKey = triggeredEvent.getEventKey();
      messageState.putMessageCorrelation(messageKey, workflowInstanceKey);
    }

    return true;
  }

  @Override
  protected boolean shouldHandleState(final BpmnStepContext<T> context) {
    return true;
  }

  /*
   Returns the event subprocess's element id for the start event. If the start event doesn't belong
   to an event subprocess, return null.
  */
  private DirectBuffer getSubprocessId(BpmnStepContext<T> context, DeployedWorkflow workflow) {
    return workflow.getWorkflow().getEvents().stream()
        .filter(
            s -> BufferUtil.contentsEqual(s.getEventId(), context.getValue().getElementIdBuffer()))
        .map(ExecutableFlowElement::getId)
        .findFirst()
        .orElse(null);
  }

  private void activateContainer(
      final BpmnStepContext<T> context,
      final DeployedWorkflow workflow,
      final long workflowInstanceKey) {
    final ExecutableCatchEventElement startEvent =
        workflow
            .getWorkflow()
            .getElementById(context.getValue().getElementId(), ExecutableCatchEventElement.class);
    final DirectBuffer subprocessId = startEvent.getEventSubProcess();

    if (subprocessId != null) {
      record.setElementId(subprocessId).setBpmnElementType(BpmnElementType.SUB_PROCESS);
    } else {
      record
          .setElementId(workflow.getWorkflow().getId())
          .setBpmnElementType(BpmnElementType.PROCESS);
    }

    record
        .setBpmnProcessId(workflow.getBpmnProcessId())
        .setWorkflowKey(workflow.getKey())
        .setVersion(workflow.getVersion())
        .setWorkflowInstanceKey(workflowInstanceKey);

    context
        .getOutput()
        .appendFollowUpEvent(
            workflowInstanceKey, WorkflowInstanceIntent.ELEMENT_ACTIVATING, record);
  }
}
